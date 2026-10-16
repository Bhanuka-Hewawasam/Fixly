package lk.ac.nibm.hdse.fixly_20.ui.screens

import android.net.Uri
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
import lk.ac.nibm.hdse.fixly_20.ui.components.FixlyPrimaryButton
import lk.ac.nibm.hdse.fixly_20.ui.components.FixlyTopBar
import lk.ac.nibm.hdse.fixly_20.ui.components.MechanicIllustrationPanel
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
                        shape = RoundedCornerShape(16.dp),
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
                        .padding(horizontal = 20.dp, vertical = 16.dp)
                ) {
                    // DASHBOARD CONTENT BASED ON ACTIVE ROLE
                    if (uiState.activeRole == "customer") {
                        CustomerDashboardView(
                            onPostJob = onPostJob,
                            onViewBookings = onViewBookings
                        )
                    } else {
                        ProviderDashboardView(
                            isVerified = user?.isVerified ?: false,
                            onViewAvailableJobs = onViewAvailableJobs,
                            onViewAcceptedJobs = onViewAcceptedJobs
                        )
                    }

                    Spacer(modifier = Modifier.height(20.dp))
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
                    elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showExpandedDialog = true }
                ) {
                    Row(
                        modifier = Modifier
                            .padding(16.dp)
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
                            Button(onClick = { showExpandedDialog = true }) {
                                Text("Open")
                            }
                            if (!isAccepted) {
                                OutlinedButton(
                                    onClick = { showCancelConfirmation = true }
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
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Profile Avatar with Photo Edit Badge
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(76.dp)
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
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surface),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.PhotoCamera,
                                contentDescription = "Change Photo",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(15.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

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
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                            ),
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
                                    Spacer(modifier = Modifier.width(12.dp))
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
                            .clip(RoundedCornerShape(14.dp))
                            .clickable {
                                showProfileSheet = false
                                viewModel.logout()
                                onLogout()
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
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp)
                        .navigationBarsPadding()
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
                                    Text("Clear Notifications")
                                }
                            }
                            TextButton(onClick = { showNotificationsSheet = false }) {
                                Text("Close")
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Open a request to review the customer's location and details before responding.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(12.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

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
                                        else MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f)
                                    ),
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
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                text = "Service: ${notif.serviceType} • ${String.format(Locale.getDefault(), "%.1f", notif.distanceKm)} km away",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }

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
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Text("View details")
                                            }
                                            OutlinedButton(
                                                onClick = {
                                                    viewModel.declineAlert(notif.notificationId, notif.alertId)
                                                },
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
                title = { Text("Clear Notifications") },
                text = { Text("Are you sure you want to clear all read, accepted, declined, and closed notifications? Active unread requests will be kept.") },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.clearProviderNotifications()
                            showClearConfirmDialog = false
                        },
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
private fun CustomerDashboardView(
    onPostJob: () -> Unit,
    onViewBookings: () -> Unit
) {
    Column {
        // --- COMPACT HERO CARD: "What do you need fixed?" ---
        ElevatedCard(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.elevatedCardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Outlined.Build,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(26.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "What do you need fixed?",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 19.sp
                            )
                        )
                    }
                    EmergencyChip()
                }

                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Post a job request to get competitive bids from qualified local service providers.",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 13.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )

                Spacer(modifier = Modifier.height(18.dp))

                FixlyPrimaryButton(
                    text = "Post a New Job",
                    icon = Icons.Outlined.Add,
                    onClick = onPostJob
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // --- SERVICE CATEGORIES ---
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "Popular Categories",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    color = Color(0xFF1E293B)
                )
            )

            TextButton(
                onClick = onPostJob,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = "See All",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.5.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        val categories = listOf("Plumbing", "Electrical", "Cleaning", "Other")
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            categories.forEach { category ->
                CategoryCard(
                    category = category,
                    isSelected = false,
                    onClick = onPostJob,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // --- MY BOOKINGS & BIDS ACTION CARD ---
        Card(
            onClick = onViewBookings,
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color.White
            ),
            border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 16.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFEFF6FF))
                    ) {
                        Icon(
                            imageVector = Icons.Default.History,
                            contentDescription = null,
                            tint = Color(0xFF2563EB),
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column {
                        Text(
                            text = "My Bookings & Bids",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = Color(0xFF1E293B)
                            )
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Track requests and review provider bids",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontWeight = FontWeight.Normal,
                                fontSize = 13.sp,
                                lineHeight = 17.sp,
                                color = Color(0xFF64748B)
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.width(10.dp))

                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFEFF6FF)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = "View Bookings",
                        tint = Color(0xFF2563EB),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // --- FRIENDLY MECHANIC ILLUSTRATION PANEL ---
        MechanicIllustrationPanel()
    }
}

@Composable
private fun ProviderDashboardView(
    isVerified: Boolean,
    onViewAvailableJobs: () -> Unit,
    onViewAcceptedJobs: () -> Unit
) {
    Column {
        // VERIFICATION ALERT (IF UNVERIFIED)
        if (!isVerified) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFFEFF6FF)
                ),
                border = BorderStroke(1.dp, Color(0xFFBFDBFE)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(16.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Info,
                        contentDescription = null,
                        tint = Color(0xFF2563EB),
                        modifier = Modifier.size(26.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Verification Pending",
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.5.sp,
                                color = Color(0xFF1E293B)
                            )
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Your certificate is under review. You can still browse jobs and submit bids.",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontSize = 12.5.sp,
                                lineHeight = 16.sp,
                                color = Color(0xFF64748B)
                            )
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(18.dp))
        }

        // AVAILABLE JOBS HERO CARD
        ElevatedCard(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.elevatedCardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Outlined.WorkOutline,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(26.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Browse Open Jobs",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 19.sp
                            )
                        )
                    }
                    EmergencyChip()
                }

                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Find home repair requests nearby, view job details, and place your price bids.",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 13.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )

                Spacer(modifier = Modifier.height(18.dp))

                FixlyPrimaryButton(
                    text = "Browse Available Jobs",
                    icon = Icons.Outlined.Search,
                    onClick = onViewAvailableJobs
                )
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // ACCEPTED JOBS CARD
        Card(
            onClick = onViewAcceptedJobs,
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color.White
            ),
            border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 16.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFEFF6FF))
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.TaskAlt,
                            contentDescription = null,
                            tint = Color(0xFF2563EB),
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column {
                        Text(
                            text = "My Accepted Jobs",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = Color(0xFF1E293B)
                            )
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "View jobs where your bid was selected",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontWeight = FontWeight.Normal,
                                fontSize = 13.sp,
                                lineHeight = 17.sp,
                                color = Color(0xFF64748B)
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.width(10.dp))

                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFEFF6FF)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = "View Accepted Jobs",
                        tint = Color(0xFF2563EB),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}
