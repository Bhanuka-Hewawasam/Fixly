package lk.ac.nibm.hdse.fixly_20.ui.screens

import android.net.Uri
import androidx.compose.animation.animateContentSize
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.SubcomposeAsyncImage
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.rememberCameraPositionState
import kotlinx.coroutines.delay
import lk.ac.nibm.hdse.fixly_20.BuildConfig
import lk.ac.nibm.hdse.fixly_20.data.repository.EmergencyAlertRepository
import lk.ac.nibm.hdse.fixly_20.ui.components.CategoryCard
import lk.ac.nibm.hdse.fixly_20.ui.components.EmergencyChip
import lk.ac.nibm.hdse.fixly_20.ui.components.FixlyBottomNavBar
import lk.ac.nibm.hdse.fixly_20.ui.components.FixlyTopBar
import lk.ac.nibm.hdse.fixly_20.ui.theme.ThemeState
import lk.ac.nibm.hdse.fixly_20.util.dialNumber
import lk.ac.nibm.hdse.fixly_20.util.openInMaps
import lk.ac.nibm.hdse.fixly_20.viewmodel.HomeViewModel
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onLogout: () -> Unit,
    onPostJob: () -> Unit,
    onViewBookings: () -> Unit,
    onViewAvailableJobs: () -> Unit,
    onViewAcceptedJobs: () -> Unit,
    onEmergency: () -> Unit,
    onProfile: (String) -> Unit,
    viewModel: HomeViewModel = viewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val isDarkMode by ThemeState.isDarkMode.collectAsState()

    var showProfileSheet by remember { mutableStateOf(false) }
    var showNotificationsSheet by remember { mutableStateOf(false) }
    var showClearConfirmDialog by remember { mutableStateOf(false) }
    var selectedNotification by remember { mutableStateOf<lk.ac.nibm.hdse.fixly_20.model.ProviderNotification?>(null) }
    val unreadCount = uiState.notifications.count { !it.isRead }

    val profilePhotoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.uploadProfilePicture(context, uri)
        }
    }

    Scaffold(
        topBar = {
            FixlyTopBar(
                title = "Fixly",
                userName = uiState.user?.name,
                profilePictureUrl = uiState.user?.profilePictureUrl,
                onAvatarClick = { showProfileSheet = true },
                unreadNotificationCount = if (uiState.activeRole == "provider") unreadCount else 0,
                onNotificationClick = if (uiState.activeRole == "provider") { { showNotificationsSheet = true } } else null
            )
        },
        bottomBar = {
            if (!uiState.isLoading && uiState.user != null) {
                FixlyBottomNavBar(
                    currentRoute = "home",
                    activeRole = uiState.activeRole,
                    onNavigate = { route ->
                        when (route) {
                            "my_bookings" -> onViewBookings()
                            "available_jobs" -> onViewAvailableJobs()
                            "accepted_jobs" -> onViewAcceptedJobs()
                            "emergency_sos" -> onEmergency()
                            "profile" -> onProfile(uiState.activeRole)
                        }
                    }
                )
            }
        }
    ) { padding ->

        when {
            uiState.isLoading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.primary,
                        strokeWidth = 3.dp
                    )
                }
            }

            uiState.errorMessage != null -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.ErrorOutline,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = uiState.errorMessage ?: "Failed to load dashboard",
                                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }

            else -> {
                val user = uiState.user

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    // DASHBOARD CONTENT BASED ON ACTIVE ROLE
                    if (uiState.activeRole == "customer") {
                        CustomerDashboardView(
                            userName = user?.name,
                            onPostJob = onPostJob,
                            onViewBookings = onViewBookings
                        )
                    } else {
                        ProviderDashboardView(
                            userName = user?.name,
                            isVerified = user?.isVerified ?: false,
                            onViewAvailableJobs = onViewAvailableJobs,
                            onViewAcceptedJobs = onViewAcceptedJobs
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                }
            }
        }

        // --- PERSISTENT MINIMIZED ACTIVE REQUEST CARD FOR CUSTOMER ON HOME ---
        val activeAlert = uiState.activeCustomerAlert
        if (uiState.activeRole == "customer" && activeAlert != null &&
            activeAlert.status in listOf("ACTIVE", "ACCEPTED")
        ) {
            val status = activeAlert.status
            val isAccepted = status == "ACCEPTED"
            val now = System.currentTimeMillis()
            val expiresAtTime = activeAlert.expiresAt?.time ?: ((activeAlert.createdAt?.time ?: now) + EmergencyAlertRepository.ALERT_LIFETIME_MS)
            val diffMs = expiresAtTime - now
            var remainingSeconds by remember(activeAlert.alertId, expiresAtTime) { mutableStateOf(maxOf(0L, diffMs / 1000)) }

            LaunchedEffect(expiresAtTime) {
                while (true) {
                    val currentDiff = expiresAtTime - System.currentTimeMillis()
                    remainingSeconds = maxOf(0L, currentDiff / 1000)
                    if (remainingSeconds <= 0L) break
                    delay(1000L)
                }
            }
            val minutes = remainingSeconds / 60
            val seconds = remainingSeconds % 60
            val countdownStr = String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)

            var showExpandedDialog by remember { mutableStateOf(false) }
            var showCancelConfirmation by remember(activeAlert.alertId) { mutableStateOf(false) }

            if (BuildConfig.DEBUG) {
                LaunchedEffect(activeAlert.alertId) {
                    Log.d("HomeScreen", "DEBUG: minimized card shown/hidden - isMinimized: true, alertId: ${activeAlert.alertId}")
                    Log.d("HomeScreen", "DEBUG: countdown recalculation - remaining: $remainingSeconds")
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                contentAlignment = Alignment.BottomCenter
            ) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = if (isAccepted) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer
                    ),
                    shape = RoundedCornerShape(20.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showExpandedDialog = true }
                ) {
                    Row(
                        modifier = Modifier
                            .padding(18.dp)
                            .fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (isAccepted) "✅ Provider accepted your request" else "🚨 Waiting for provider response",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = if (isAccepted) "Provider: ${activeAlert.acceptedProviderName ?: "Accepted"}" else "${activeAlert.category} • $countdownStr remaining",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = { showExpandedDialog = true },
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Open")
                            }
                            if (!isAccepted) {
                                OutlinedButton(
                                    onClick = { showCancelConfirmation = true },
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text("Cancel")
                                }
                            }
                        }
                    }
                }
            }

            if (showExpandedDialog) {
                AlertDialog(
                    onDismissRequest = { showExpandedDialog = false },
                    shape = RoundedCornerShape(24.dp),
                    title = {
                        Text(
                            text = if (isAccepted) "✅ Provider Accepted Your Request!" else "🚨 Emergency Request Pending"
                        )
                    },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (isAccepted) {
                                Text("Provider: ${activeAlert.acceptedProviderName ?: "Provider"}", style = MaterialTheme.typography.titleMedium)
                                Text("Phone: ${activeAlert.acceptedProviderPhone ?: "N/A"}")
                                Text("Services: ${activeAlert.category}")
                                Text("The selected provider is on the way.")
                            } else {
                                Text("Your request was sent to the selected provider.")
                                Text("The provider has been notified in their dashboard.")
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("Service Category: ${activeAlert.category}")
                                Text("Provider response time remaining: $countdownStr", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                            }
                        }
                    },
                    confirmButton = {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (isAccepted) {
                                if (!activeAlert.acceptedProviderPhone.isNullOrBlank()) {
                                    Button(
                                        onClick = { context.dialNumber(activeAlert.acceptedProviderPhone) },
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("📞 Call")
                                    }
                                }
                                if (activeAlert.location != null) {
                                    OutlinedButton(
                                        onClick = {
                                            context.openInMaps(activeAlert.location.latitude, activeAlert.location.longitude, activeAlert.acceptedProviderName ?: "Provider")
                                        },
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("🗺️ Maps")
                                    }
                                }
                            } else {
                                Button(
                                    onClick = {
                                        showCancelConfirmation = true
                                        showExpandedDialog = false
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Cancel Request")
                                }
                            }
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showExpandedDialog = false }) {
                            Text("Close")
                        }
                    }
                )
            }

            if (showCancelConfirmation) {
                AlertDialog(
                    onDismissRequest = { showCancelConfirmation = false },
                    shape = RoundedCornerShape(24.dp),
                    title = { Text("Cancel emergency request?") },
                    text = { Text("Nearby providers will no longer be able to accept this request.") },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                showCancelConfirmation = false
                                viewModel.cancelActiveAlert(activeAlert.alertId)
                                showExpandedDialog = false
                            }
                        ) { Text("Cancel request") }
                    },
                    dismissButton = {
                        TextButton(onClick = { showCancelConfirmation = false }) { Text("Keep request") }
                    }
                )
            }
        }

        // PROFILE MENU BOTTOM SHEET (Anchored via TopBar Avatar Click)
        if (showProfileSheet && uiState.user != null) {
            val user = uiState.user!!
            val initial = (user.name.firstOrNull() ?: 'S').uppercaseChar()
            val hasBothRoles = user.roles.contains("customer") && user.roles.contains("provider")

            ModalBottomSheet(
                onDismissRequest = { showProfileSheet = false },
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Profile Avatar with Photo Edit Badge
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(80.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary)
                            .clickable(
                                onClickLabel = "Change profile picture",
                                onClick = { profilePhotoPicker.launch("image/*") }
                            )
                    ) {
                        if (!user.profilePictureUrl.isNullOrBlank()) {
                            SubcomposeAsyncImage(
                                model = user.profilePictureUrl,
                                contentDescription = "Profile Photo of ${user.name}",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Text(
                                text = initial.toString(),
                                style = MaterialTheme.typography.headlineLarge.copy(
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                        }

                        // Camera Icon Badge
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surface),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.PhotoCamera,
                                contentDescription = "Change Photo",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    TextButton(
                        onClick = { profilePhotoPicker.launch("image/*") },
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.PhotoCamera,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (user.profilePictureUrl.isNullOrBlank()) "Add Photo" else "Change Photo",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // User Name & Email Header
                    Text(
                        text = user.name.ifBlank { "User" },
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp
                        )
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = user.email,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )

                    Spacer(modifier = Modifier.height(20.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                    Spacer(modifier = Modifier.height(16.dp))

                    // ROLE SWITCHER (IF USER HAS BOTH CUSTOMER AND PROVIDER ROLES)
                    if (hasBothRoles) {
                        val targetRole = if (uiState.activeRole == "customer") "provider" else "customer"
                        val switchLabel = if (uiState.activeRole == "customer") "Switch to Provider Dashboard" else "Switch to Customer Dashboard"

                        Card(
                            onClick = {
                                viewModel.switchRole(targetRole)
                                showProfileSheet = false
                            },
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                            ),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = if (targetRole == "provider") Icons.Outlined.WorkOutline else Icons.Outlined.Person,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Spacer(modifier = Modifier.width(14.dp))
                                    Text(
                                        text = switchLabel,
                                        style = MaterialTheme.typography.titleMedium.copy(
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 15.sp,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    )
                                }

                                Icon(
                                    imageVector = Icons.Outlined.SwapHoriz,
                                    contentDescription = "Switch Role",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                    Spacer(modifier = Modifier.height(12.dp))

                    // Dark Mode Toggle Row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 48.dp)
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Outlined.DarkMode,
                                contentDescription = "Dark Mode",
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(14.dp))
                            Text(
                                text = "Dark mode",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 15.sp
                                )
                            )
                        }

                        Switch(
                            checked = isDarkMode,
                            onCheckedChange = { ThemeState.setDarkMode(context, it) }
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Log Out Row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 48.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .clickable {
                                showProfileSheet = false
                                viewModel.logout(onComplete = onLogout)
                            }
                            .padding(vertical = 12.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                            contentDescription = "Log out",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(14.dp))
                        Text(
                            text = "Log out",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = MaterialTheme.colorScheme.error
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }

        // NOTIFICATIONS BOTTOM SHEET
        if (showNotificationsSheet) {
            ModalBottomSheet(
                onDismissRequest = { showNotificationsSheet = false },
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp)
                        .navigationBarsPadding(),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "🚨 Emergency Notifications",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            if (uiState.notifications.isNotEmpty()) {
                                TextButton(onClick = { showClearConfirmDialog = true }) {
                                    Text("Clear")
                                }
                            }
                            TextButton(onClick = { showNotificationsSheet = false }) {
                                Text("Close")
                            }
                        }
                    }

                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f)),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Open a request to review the customer's location and details before responding.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(14.dp)
                        )
                    }

                    if (uiState.notifications.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No emergency notifications yet.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 400.dp)
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            uiState.notifications.forEach { notif ->
                                Card(
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (notif.isRead) MaterialTheme.colorScheme.surfaceVariant
                                        else MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.25f)
                                    ),
                                    shape = RoundedCornerShape(16.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = notif.title.ifBlank { "Emergency Request" },
                                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                                            )
                                            if (!notif.isRead) {
                                                Badge { Text("NEW") }
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = notif.body,
                                            style = MaterialTheme.typography.bodyMedium
                                        )

                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text(
                                            text = "Service: ${notif.serviceType} • ${String.format(Locale.getDefault(), "%.1f", notif.distanceKm)} km away",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.primary
                                        )

                                        Spacer(modifier = Modifier.height(12.dp))
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            OutlinedButton(
                                                onClick = {
                                                    selectedNotification = notif
                                                    viewModel.loadEmergencyDetails(notif.alertId)
                                                    viewModel.markNotificationRead(notif.notificationId)
                                                },
                                                shape = RoundedCornerShape(12.dp),
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Text("View details")
                                            }
                                            OutlinedButton(
                                                onClick = {
                                                    viewModel.declineAlert(notif.notificationId, notif.alertId)
                                                },
                                                shape = RoundedCornerShape(12.dp),
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Text("Decline")
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        if (showClearConfirmDialog) {
            AlertDialog(
                onDismissRequest = { showClearConfirmDialog = false },
                shape = RoundedCornerShape(24.dp),
                title = { Text("Clear Notifications") },
                text = { Text("Are you sure you want to clear all read, accepted, declined, and closed notifications? Active unread requests will be kept.") },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.clearProviderNotifications()
                            showClearConfirmDialog = false
                        },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Clear")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showClearConfirmDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }

        selectedNotification?.let { notif ->
            val emergencyDetails = uiState.selectedEmergencyAlert
            val emergencyDetailsError = uiState.emergencyDetailsError
            AlertDialog(
                onDismissRequest = {
                    selectedNotification = null
                    viewModel.clearEmergencyDetails()
                },
                shape = RoundedCornerShape(24.dp),
                title = { Text(notif.title) },
                text = {
                    Column(
                        modifier = Modifier
                            .heightIn(max = 360.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        when {
                            uiState.isLoadingEmergencyDetails -> CircularProgressIndicator()
                            emergencyDetailsError != null -> Column {
                                Text(
                                    emergencyDetailsError,
                                    color = MaterialTheme.colorScheme.error
                                )
                                TextButton(onClick = { viewModel.loadEmergencyDetails(notif.alertId) }) {
                                    Text("Retry")
                                }
                            }
                            emergencyDetails != null -> {
                                val alert = emergencyDetails
                                Text("Customer: ${alert.customerName}", fontWeight = FontWeight.Bold)
                                Text("Category: ${alert.category}")
                                Text(
                                    "Distance: ${String.format(Locale.getDefault(), "%.1f", notif.distanceKm)} km away",
                                    style = MaterialTheme.typography.bodySmall
                                )
                                if (alert.note.isNotBlank()) {
                                    Spacer(Modifier.height(8.dp))
                                    Text("Note: ${alert.note}")
                                }
                                alert.location?.let { location ->
                                    val latLng = LatLng(location.latitude, location.longitude)
                                    val cameraState = rememberCameraPositionState {
                                        position = CameraPosition.fromLatLngZoom(latLng, 14f)
                                    }
                                    GoogleMap(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(150.dp)
                                            .clip(RoundedCornerShape(16.dp)),
                                        cameraPositionState = cameraState
                                    ) {
                                        Marker(
                                            state = MarkerState(position = latLng),
                                            title = "Emergency location"
                                        )
                                    }
                                    Spacer(Modifier.height(8.dp))
                                    OutlinedButton(
                                        onClick = {
                                            context.openInMaps(
                                                location.latitude,
                                                location.longitude,
                                                "Emergency location"
                                            )
                                        },
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) { Text("Open customer location in Maps") }
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (!emergencyDetails?.customerPhone.isNullOrBlank()) {
                            OutlinedButton(
                                onClick = { emergencyDetails?.customerPhone?.let(context::dialNumber) },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Call customer")
                            }
                        }
                        Button(
                            enabled = !uiState.isLoadingEmergencyDetails &&
                                uiState.acceptingAlertId != notif.alertId &&
                                uiState.selectedEmergencyAlert != null,
                            onClick = {
                                viewModel.acceptAlert(notif.alertId) { success, error ->
                                    if (success) {
                                        selectedNotification = null
                                        viewModel.clearEmergencyDetails()
                                    } else {
                                        viewModel.showEmergencyDetailsError(
                                            error ?: "This request could not be accepted."
                                        )
                                    }
                                }
                            },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            if (uiState.acceptingAlertId == notif.alertId) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Text("Accept request")
                            }
                        }
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            viewModel.declineAlert(notif.notificationId, notif.alertId)
                            selectedNotification = null
                            viewModel.clearEmergencyDetails()
                        }
                    ) { Text("Decline") }
                }
            )
        }
    }
}

@Composable
private fun DashboardGreeting(
    userName: String?,
    roleLabel: String,
    subtitle: String
) {
    val firstName = userName
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
        ?.substringBefore(" ")
        ?: "there"

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize()
            .padding(top = 2.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Text(
            text = roleLabel,
            style = MaterialTheme.typography.labelSmall.copy(
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.2.sp
            )
        )
        Text(
            text = "Hello, $firstName 👋",
            style = MaterialTheme.typography.headlineSmall.copy(
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.Bold
            )
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium.copy(
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 20.sp
            )
        )
    }
}

@Composable
private fun CustomerDashboardView(
    userName: String?,
    onPostJob: () -> Unit,
    onViewBookings: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    val categories = listOf(
        Triple("Plumbing", "Leaks, taps & pipes", "https://images.unsplash.com/photo-1607472586893-edb57bdc0e39?w=900&auto=format&fit=crop&q=85"),
        Triple("Electrical", "Wiring, lights & repairs", "https://images.unsplash.com/photo-1621905251918-48416bd8575a?w=900&auto=format&fit=crop&q=85"),
        Triple("Cleaning", "A fresher, cleaner home", "https://images.unsplash.com/photo-1581578731548-c64695cc6952?w=900&auto=format&fit=crop&q=85"),
        Triple("Home Repairs", "Fixes around your home", "https://images.unsplash.com/photo-1504307651254-35680f356dfd?w=900&auto=format&fit=crop&q=85")
    )
    val visibleCategories = categories.filter {
        it.first.contains(searchQuery.trim(), ignoreCase = true) ||
            it.second.contains(searchQuery.trim(), ignoreCase = true)
    }

    Column(verticalArrangement = Arrangement.spacedBy(22.dp)) {
        DashboardGreeting(
            userName = userName,
            roleLabel = "FIXLY  •  HOME SERVICES",
            subtitle = "A better way to care for your home."
        )

        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = RoundedCornerShape(20.dp),
            placeholder = { Text("What do you need help with?") },
            leadingIcon = {
                Icon(Icons.Outlined.Search, contentDescription = "Search services")
            },
            trailingIcon = {
                IconButton(onClick = onPostJob) {
                    Icon(Icons.Outlined.Tune, contentDescription = "Browse services")
                }
            },
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                focusedBorderColor = MaterialTheme.colorScheme.primary
            )
        )

        Card(
            shape = RoundedCornerShape(30.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF101B3C)),
            elevation = CardDefaults.cardElevation(defaultElevation = 5.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 250.dp)
            ) {
                SubcomposeAsyncImage(
                    model = "https://images.unsplash.com/photo-1621905251189-08b45d6a269e?w=1200&auto=format&fit=crop&q=90",
                    contentDescription = "Professional home service technician at work",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.horizontalGradient(
                                0f to Color(0xFF101B3C),
                                0.58f to Color(0xE6101B3C),
                                1f to Color(0x22101B3C)
                            )
                        )
                )
                Column(
                    modifier = Modifier
                        .fillMaxWidth(0.82f)
                        .padding(horizontal = 22.dp, vertical = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(13.dp)
                ) {
                    Surface(
                        color = Color.White.copy(alpha = 0.13f),
                        shape = RoundedCornerShape(50),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.18f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 11.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                Icons.Outlined.VerifiedUser,
                                contentDescription = null,
                                tint = Color(0xFFB7F34A),
                                modifier = Modifier.size(15.dp)
                            )
                            Text(
                                "TRUSTED LOCAL PROFESSIONALS",
                                color = Color.White,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.6.sp
                            )
                        }
                    }
                    Text(
                        "Your home.\nOur priority.",
                        color = Color.White,
                        fontSize = 31.sp,
                        lineHeight = 35.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        "Reliable help for everyday repairs and home services, all in one place.",
                        color = Color.White.copy(alpha = 0.86f),
                        style = MaterialTheme.typography.bodyMedium,
                        lineHeight = 21.sp
                    )
                    Button(
                        onClick = onPostJob,
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFB7F34A),
                            contentColor = Color(0xFF14213D)
                        ),
                        contentPadding = PaddingValues(horizontal = 17.dp, vertical = 13.dp)
                    ) {
                        Text("Post a service request", fontWeight = FontWeight.ExtraBold)
                        Spacer(Modifier.width(8.dp))
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null)
                    }
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(13.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        "What needs fixing?",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        "Choose a service to get started",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                TextButton(onClick = onPostJob) {
                    Text("See all", fontWeight = FontWeight.Bold)
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, null, modifier = Modifier.size(16.dp))
                }
            }

            if (visibleCategories.isEmpty()) {
                Text(
                    "No matching service. Try plumbing, electrical or cleaning.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(vertical = 12.dp)
                )
            } else {
                visibleCategories.chunked(2).forEach { rowItems ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        rowItems.forEach { item ->
                            ServiceImageCard(
                                title = item.first,
                                subtitle = item.second,
                                imageUrl = item.third,
                                onClick = onPostJob,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        if (rowItems.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
        }

        Card(
            onClick = onViewBookings,
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(18.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .clip(RoundedCornerShape(17.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Outlined.History,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(26.dp)
                    )
                }
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Your bookings", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold)
                    Text(
                        "Track requests, offers and service progress",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = MaterialTheme.colorScheme.primary)
            }
        }

        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF17264B)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(19.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(13.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.White.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Outlined.Shield, null, tint = Color(0xFFB7F34A), modifier = Modifier.size(25.dp))
                }
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Book with more confidence", color = Color.White, fontWeight = FontWeight.Bold)
                    Text(
                        "Find service professionals and manage your requests through Fixly.",
                        color = Color.White.copy(alpha = 0.76f),
                        style = MaterialTheme.typography.bodySmall,
                        lineHeight = 18.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun ServiceImageCard(
    title: String,
    subtitle: String,
    imageUrl: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick,
        modifier = modifier.height(178.dp),
        shape = RoundedCornerShape(22.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            SubcomposeAsyncImage(
                model = imageUrl,
                contentDescription = title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                loading = {
                    Box(
                        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                error = {
                    Box(
                        Modifier.fillMaxSize().background(
                            Brush.linearGradient(
                                listOf(Color(0xFFEDE6FF), Color(0xFFDCD0FF))
                            )
                        ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "FIXLY",
                            color = Color(0xFF5131B5),
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 2.sp
                        )
                    }
                }
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0f to Color.Transparent,
                            0.38f to Color(0x33071125),
                            1f to Color(0xE6071125)
                        )
                    )
            )
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(
                    title,
                    color = Color.White,
                    fontWeight = FontWeight.ExtraBold,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    subtitle,
                    color = Color.White.copy(alpha = 0.86f),
                    style = MaterialTheme.typography.labelSmall,
                    lineHeight = 15.sp
                )
            }
        }
    }
}

@Composable
private fun ProviderDashboardView(
    userName: String?,
    isVerified: Boolean,
    onViewAvailableJobs: () -> Unit,
    onViewAcceptedJobs: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(22.dp)) {
        DashboardGreeting(
            userName = userName,
            roleLabel = "FIXLY • PRO WORKSPACE",
            subtitle = "Your next great job could be one tap away."
        )

        if (!isVerified) {
            Card(
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(17.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(13.dp)
                ) {
                    Icon(Icons.Outlined.VerifiedUser, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text("Verification in progress", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.ExtraBold)
                        Text("Your certificate is under review. You can still browse jobs and submit bids.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        Card(
            shape = RoundedCornerShape(32.dp),
            colors = CardDefaults.cardColors(containerColor = Color.Transparent),
            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                modifier = Modifier.fillMaxWidth()
                    .background(Brush.linearGradient(listOf(Color(0xFF17153B), Color(0xFF45218E), Color(0xFF7044F5))))
                    .padding(24.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Surface(color = Color.White.copy(alpha = 0.14f), shape = RoundedCornerShape(50)) {
                        Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.WorkOutline, null, tint = Color(0xFFA7F3D0), modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(7.dp))
                            Text("YOUR NEXT OPPORTUNITY", color = Color.White, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp)
                        }
                    }
                    Text(
                        "Find work.\nShow your craft.",
                        style = MaterialTheme.typography.headlineLarge.copy(color = Color.White, fontWeight = FontWeight.ExtraBold, lineHeight = 39.sp, fontSize = 32.sp)
                    )
                    Text(
                        "Discover nearby service requests and send a competitive bid to customers.",
                        style = MaterialTheme.typography.bodyMedium.copy(color = Color.White.copy(alpha = 0.84f), lineHeight = 21.sp)
                    )
                    Button(
                        onClick = onViewAvailableJobs,
                        shape = RoundedCornerShape(17.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFA7F3D0), contentColor = Color(0xFF052E2B)),
                        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Outlined.Search, null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Explore available jobs", fontWeight = FontWeight.ExtraBold)
                        Spacer(Modifier.weight(1f))
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, null, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }

        Card(
            onClick = onViewAcceptedJobs,
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(18.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Box(
                    modifier = Modifier.size(52.dp).clip(RoundedCornerShape(17.dp)).background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Outlined.AssignmentTurnedIn, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(25.dp))
                }
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("Accepted jobs", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold)
                    Text("Manage the work you have taken on", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = MaterialTheme.colorScheme.primary)
            }
        }
        TrustVerificationBanner()
    }
}

@Composable
private fun TrustVerificationBanner(
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.Transparent
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    brush = Brush.horizontalGradient(
                        colors = listOf(
                            Color(0xFF21164F),
                            Color(0xFF120F2D)
                        )
                    )
                )
                .padding(18.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(
                            brush = Brush.linearGradient(
                                colors = listOf(Color(0xFF8B5CF6), Color(0xFF5B35D5))
                            )
                        )
                        .padding(2.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape)
                            .background(Color(0xFF21164F)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Security,
                            contentDescription = null,
                            tint = Color(0xFFC4B5FD),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "100% Trusted Service",
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.5.sp,
                                color = Color.White
                            )
                        )
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = Color(0xFFC4B5FD),
                            modifier = Modifier.size(15.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(2.dp))

                    Text(
                        text = "Background-verified professionals ready for all home maintenance & urgent fixes.",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 12.sp,
                            lineHeight = 16.sp,
                            color = Color.White.copy(alpha = 0.75f)
                        )
                    )
                }
            }
        }
    }
}
