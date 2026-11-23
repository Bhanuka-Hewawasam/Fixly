package lk.ac.nibm.hdse.fixly_20.ui.screens

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.rememberCameraPositionState
import lk.ac.nibm.hdse.fixly_20.ui.components.BottomNavItem
import lk.ac.nibm.hdse.fixly_20.ui.components.FixlyBottomNavBar
import lk.ac.nibm.hdse.fixly_20.data.repository.ServiceReviewRepository
import lk.ac.nibm.hdse.fixly_20.data.repository.ProviderRatingRepository
import lk.ac.nibm.hdse.fixly_20.model.ProviderRatingAggregate
import lk.ac.nibm.hdse.fixly_20.model.ServiceReview
import lk.ac.nibm.hdse.fixly_20.model.ServiceRating
import lk.ac.nibm.hdse.fixly_20.ui.theme.ThemeState
import lk.ac.nibm.hdse.fixly_20.viewmodel.ProviderAvailabilityViewModel
import java.util.Locale
import kotlinx.coroutines.flow.flowOf
import lk.ac.nibm.hdse.fixly_20.util.ServiceCategories

private val providerServiceCategories = ServiceCategories.canonicalList

private val sriLankaMapCenter = LatLng(7.8731, 80.7718)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    activeRole: String,
    onBack: () -> Unit,
    onNavigate: (String) -> Unit,
    viewModel: ProviderAvailabilityViewModel = viewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val isDarkMode by ThemeState.isDarkMode.collectAsState()
    val reviewRepository = remember { ServiceReviewRepository() }
    val ratingRepository = remember { ProviderRatingRepository() }
    val providerId = uiState.user?.uid
    val reviewsFlow = remember(providerId) {
        providerId?.let(reviewRepository::observeProviderReviews)
            ?: flowOf(Result.success(emptyList<ServiceReview>()))
    }
    val reviewResult by reviewsFlow.collectAsState(initial = Result.success(emptyList<ServiceReview>()))
    val ratingFlow = remember(providerId) {
        providerId?.let(ratingRepository::observe)
            ?: flowOf(Result.success<ProviderRatingAggregate?>(null))
    }
    val ratingResult by ratingFlow.collectAsState(
        initial = Result.success<ProviderRatingAggregate?>(null)
    )
    val providerReviews = reviewResult.getOrNull().orEmpty()
    var selectedReviewCategory by remember(providerId) { mutableStateOf("All") }
    var showSettingsSheet by remember { mutableStateOf(false) }
    var showAccountDetailsDialog by remember { mutableStateOf(false) }
    var showPrivacyDialog by remember { mutableStateOf(false) }

    var editName by remember { mutableStateOf("") }
    var editPhone by remember { mutableStateOf("") }
    var isEditingAccountDetails by remember { mutableStateOf(false) }

    var isBecomingProvider by remember { mutableStateOf(false) }
    var becomePhone by remember { mutableStateOf("") }
    var becomeAddress by remember { mutableStateOf("") }
    var becomeCity by remember { mutableStateOf("") }
    var becomeLatitude by remember { mutableStateOf("") }
    var becomeLongitude by remember { mutableStateOf("") }
    var becomeRadius by remember { mutableStateOf(20f) }
    var becomeCategories by remember { mutableStateOf(setOf<String>()) }

    val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }
    var address by remember { mutableStateOf("") }
    var city by remember { mutableStateOf("") }
    var latitude by remember { mutableStateOf("") }
    var longitude by remember { mutableStateOf("") }
    var selectedRadius by remember { mutableStateOf(20f) }
    var selectedCategories by remember { mutableStateOf(setOf<String>()) }
    var availableForEmergency by remember { mutableStateOf(false) }
    var isGettingLocation by remember { mutableStateOf(false) }
    var locationError by remember { mutableStateOf<String?>(null) }

    val certificatePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.uploadCertificate(context, uri)
        }
    }

    LaunchedEffect(uiState.user) {
        uiState.user?.let { user ->
            if (!isEditingAccountDetails) {
                editName = user.name
                editPhone = user.phoneNumber
            }
            if (becomePhone.isBlank()) {
                becomePhone = user.phoneNumber
            }
            address = user.serviceAddress
            city = user.serviceCity
            latitude = user.serviceLatitude?.toString().orEmpty()
            longitude = user.serviceLongitude?.toString().orEmpty()
            selectedRadius = user.serviceRadiusKm.coerceIn(1, 100).toFloat()
            selectedCategories = user.serviceCategories.map { ServiceCategories.normalize(it) }.toSet()
            availableForEmergency = user.availableForEmergency
        }
    }

    fun updateMapLocation(point: LatLng) {
        latitude = point.latitude.toString()
        longitude = point.longitude.toString()
        locationError = null
    }

    fun updateBecomeMapLocation(point: LatLng) {
        becomeLatitude = point.latitude.toString()
        becomeLongitude = point.longitude.toString()
        locationError = null
    }

    fun useCurrentLocationOnce(isBecome: Boolean = false) {
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        if (!locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) &&
            !locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        ) {
            locationError = "Turn on Location services, then try again."
            return
        }
        val priority = if (
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        ) Priority.PRIORITY_HIGH_ACCURACY else Priority.PRIORITY_BALANCED_POWER_ACCURACY
        isGettingLocation = true
        fusedLocationClient.getCurrentLocation(priority, null)
            .addOnSuccessListener { location ->
                isGettingLocation = false
                if (location == null) {
                    locationError = "Couldn't get a location. Choose the service point on the map."
                } else {
                    if (isBecome) updateBecomeMapLocation(LatLng(location.latitude, location.longitude))
                    else updateMapLocation(LatLng(location.latitude, location.longitude))
                }
            }
            .addOnFailureListener {
                isGettingLocation = false
                locationError = "Couldn't get a location. Choose the service point on the map."
            }
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.values.any { it }) useCurrentLocationOnce()
        else locationError = "Allow location permission to use your location once, or select the point on the map."
    }

    val mapPoint = remember(latitude, longitude) {
        val lat = latitude.toDoubleOrNull()
        val lng = longitude.toDoubleOrNull()
        if (lat != null && lng != null && lat in -90.0..90.0 && lng in -180.0..180.0) {
            LatLng(lat, lng)
        } else sriLankaMapCenter
    }

    val becomeMapPoint = remember(becomeLatitude, becomeLongitude) {
        val lat = becomeLatitude.toDoubleOrNull()
        val lng = becomeLongitude.toDoubleOrNull()
        if (lat != null && lng != null && lat in -90.0..90.0 && lng in -180.0..180.0) {
            LatLng(lat, lng)
        } else sriLankaMapCenter
    }

    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(mapPoint, if (latitude.isBlank()) 7f else 14f)
    }
    LaunchedEffect(mapPoint) { cameraPositionState.position = CameraPosition.fromLatLngZoom(mapPoint, 14f) }

    val userName = uiState.user?.name.orEmpty().ifBlank { "Your name" }
    val userInitials = userName.split(" ").let { parts ->
        if (parts.size >= 2) "${parts[0].firstOrNull() ?: 'S'}${parts[1].firstOrNull() ?: 'B'}"
        else "SB"
    }.uppercase()

    val isProvider = uiState.user?.roles?.contains("provider") == true

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Profile", fontWeight = FontWeight.Bold, fontSize = 20.sp) },
                navigationIcon = {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.padding(start = 12.dp).size(40.dp)
                    ) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", modifier = Modifier.size(20.dp))
                        }
                    }
                },
                actions = {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.padding(end = 16.dp).size(40.dp)
                    ) {
                        IconButton(onClick = { showSettingsSheet = true }) {
                            Icon(Icons.Outlined.Settings, contentDescription = "Settings", modifier = Modifier.size(20.dp))
                        }
                    }
                }
            )
        },
        bottomBar = {
            FixlyBottomNavBar(
                currentRoute = BottomNavItem.Profile.route,
                activeRole = activeRole,
                onNavigate = onNavigate
            )
        }
    ) { padding ->
        when {
            uiState.isLoading -> Box(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator() }

            else -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                // USER PROFILE CARD
                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            // Avatar with initials and verified badge
                            Box(
                                contentAlignment = Alignment.BottomEnd,
                                modifier = Modifier.size(64.dp)
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(CircleShape)
                                        .background(
                                            brush = Brush.linearGradient(
                                                colors = listOf(Color(0xFF2563EB), Color(0xFF4F46E5))
                                            )
                                        )
                                ) {
                                    Text(
                                        text = userInitials,
                                        style = MaterialTheme.typography.titleLarge.copy(
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold
                                        )
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .size(20.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF10B981)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Verified",
                                        tint = Color.White,
                                        modifier = Modifier.size(12.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(16.dp))

                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = userName,
                                    style = MaterialTheme.typography.titleLarge.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 18.sp
                                    )
                                )
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Phone,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        text = uiState.user?.phoneNumber.orEmpty().ifBlank { "Add your phone number" },
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            fontSize = 13.sp
                                        )
                                    )
                                }
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Email,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        text = uiState.user?.email.orEmpty().ifBlank { "No email available" },
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            fontSize = 12.5.sp
                                        )
                                    )
                                }
                            }
                        }

                        // User Stats Summary Strip
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = Color(0xFFF8FAFC),
                            border = BorderStroke(1.dp, Color(0xFFF1F5F9)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 12.dp),
                                horizontalArrangement = Arrangement.SpaceEvenly,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                StatItem(value = "0", label = "Jobs Posted")
                                VerticalDivider(modifier = Modifier.height(28.dp), color = Color(0xFFE2E8F0))
                                StatItem(value = "${providerReviews.size}", label = "Reviews")
                                VerticalDivider(modifier = Modifier.height(28.dp), color = Color(0xFFE2E8F0))
                                StatItem(value = "★ 5.0", label = "Rating")
                            }
                        }
                    }
                }

                // BECOME A SERVICE PROVIDER CARD (If not already a provider)
                if (!isProvider) {
                    Card(
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                        border = BorderStroke(1.dp, Color(0xFF2563EB)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { isBecomingProvider = !isBecomingProvider }
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text("Become a Service Provider", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary))
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text("Offer your services on Fixly and accept local customer jobs.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                TextButton(onClick = { isBecomingProvider = !isBecomingProvider }) {
                                    Text(if (isBecomingProvider) "Cancel" else "Get Started")
                                }
                            }

                            if (isBecomingProvider) {
                                OutlinedTextField(
                                    value = becomePhone,
                                    onValueChange = { becomePhone = it },
                                    label = { Text("Phone Number * (10 digits starting with 0 or +94 format)") },
                                    singleLine = true,
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier.fillMaxWidth()
                                )

                                Text("Certificate (Optional)", fontWeight = FontWeight.SemiBold)
                                if (!uiState.user?.certificateUrl.isNullOrBlank()) {
                                    OutlinedButton(
                                        onClick = {
                                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(uiState.user?.certificateUrl)))
                                        },
                                        shape = RoundedCornerShape(14.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text("View Uploaded Certificate")
                                    }
                                }
                                OutlinedButton(
                                    onClick = { certificatePicker.launch("image/*") },
                                    enabled = !uiState.isUploadingCertificate,
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    if (uiState.isUploadingCertificate) {
                                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                    } else {
                                        Text(if (uiState.user?.certificateUrl.isNullOrBlank()) "Upload Certificate" else "Re-upload Certificate")
                                    }
                                }

                                OutlinedTextField(
                                    value = becomeAddress,
                                    onValueChange = { becomeAddress = it },
                                    label = { Text("Service address (optional)") },
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true
                                )
                                OutlinedTextField(
                                    value = becomeCity,
                                    onValueChange = { becomeCity = it },
                                    label = { Text("City or area (optional)") },
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedButton(
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(14.dp),
                                        enabled = !isGettingLocation,
                                        onClick = { useCurrentLocationOnce(true) }
                                    ) {
                                        Text("Use current location once")
                                    }
                                }

                                GoogleMap(
                                    modifier = Modifier.fillMaxWidth().height(200.dp).clip(RoundedCornerShape(16.dp)),
                                    cameraPositionState = rememberCameraPositionState {
                                        position = CameraPosition.fromLatLngZoom(becomeMapPoint, 14f)
                                    },
                                    onMapClick = ::updateBecomeMapLocation
                                ) {
                                    Marker(
                                        state = MarkerState(position = becomeMapPoint),
                                        title = "Service Location"
                                    )
                                }

                                Text("Service radius: ${becomeRadius.toInt()} km", fontWeight = FontWeight.SemiBold)
                                Slider(
                                    value = becomeRadius,
                                    onValueChange = { becomeRadius = it },
                                    valueRange = 1f..100f,
                                    steps = 98
                                )

                                Text("Service categories * (At least 1 required)", fontWeight = FontWeight.SemiBold)
                                providerServiceCategories.chunked(2).forEach { rowCategories ->
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        rowCategories.forEach { category ->
                                            val normalizedCat = ServiceCategories.normalize(category)
                                            FilterChip(
                                                selected = normalizedCat in becomeCategories.map { ServiceCategories.normalize(it) },
                                                onClick = {
                                                    becomeCategories = if (normalizedCat in becomeCategories.map { ServiceCategories.normalize(it) }) {
                                                        becomeCategories.filter { ServiceCategories.normalize(it) != normalizedCat }.toSet()
                                                    } else becomeCategories + normalizedCat
                                                },
                                                label = { Text(category) }
                                            )
                                        }
                                    }
                                }
                                if (becomeCategories.isEmpty()) {
                                    Text("Please select at least one service category.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                                }

                                uiState.errorMessage?.let {
                                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                                }
                                uiState.successMessage?.let {
                                    Text(it, color = Color(0xFF10B981), style = MaterialTheme.typography.bodySmall)
                                }

                                Button(
                                    onClick = {
                                        viewModel.becomeServiceProvider(
                                            phoneNumber = becomePhone,
                                            serviceAddress = becomeAddress,
                                            serviceCity = becomeCity,
                                            latitude = becomeLatitude,
                                            longitude = becomeLongitude,
                                            serviceRadiusKm = becomeRadius.toInt(),
                                            serviceCategories = becomeCategories.toList(),
                                            certificateUrl = uiState.user?.certificateUrl
                                        )
                                    },
                                    enabled = !uiState.isUpdating && becomeCategories.isNotEmpty(),
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier.fillMaxWidth().height(48.dp)
                                ) {
                                    if (uiState.isUpdating) {
                                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                    } else {
                                        Text("Save & Become a Provider")
                                    }
                                }

                                if (uiState.saved) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Button(
                                        onClick = { onNavigate("profile/provider") },
                                        shape = RoundedCornerShape(14.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                                        modifier = Modifier.fillMaxWidth().height(48.dp)
                                    ) {
                                        Text("Switch to Provider Dashboard")
                                    }
                                }
                            }
                        }
                    }
                }

                if (isProvider) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                        border = BorderStroke(1.dp, Color(0xFFE2E8F0))
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Text("Service Location & Profile", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            Text(
                                "This is the provider’s registered service area and certificate details.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Text("Certificate", fontWeight = FontWeight.SemiBold)
                            if (!uiState.user?.certificateUrl.isNullOrBlank()) {
                                OutlinedButton(
                                    onClick = {
                                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(uiState.user?.certificateUrl)))
                                    },
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("View Uploaded Certificate")
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                            }
                            OutlinedButton(
                                onClick = { certificatePicker.launch("image/*") },
                                enabled = !uiState.isUploadingCertificate,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                if (uiState.isUploadingCertificate) {
                                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                } else {
                                    Text(if (uiState.user?.certificateUrl.isNullOrBlank()) "Upload Certificate" else "Re-upload Certificate")
                                }
                            }

                            OutlinedTextField(
                                value = address,
                                onValueChange = { address = it },
                                label = { Text("Service address (optional)") },
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )
                            OutlinedTextField(
                                value = city,
                                onValueChange = { city = it },
                                label = { Text("City or area (optional)") },
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(14.dp),
                                    enabled = !isGettingLocation,
                                    onClick = { useCurrentLocationOnce(false) }
                                ) {
                                    Text(if (isGettingLocation) "Getting location…" else "Use current location once")
                                }
                            }
                            Text("Or tap the map to select your registered service point.")
                            GoogleMap(
                                modifier = Modifier.fillMaxWidth().height(230.dp).clip(RoundedCornerShape(16.dp)),
                                cameraPositionState = cameraPositionState,
                                onMapClick = ::updateMapLocation
                            ) {
                                Marker(
                                    state = MarkerState(position = mapPoint),
                                    title = "Service Location",
                                    snippet = "Registered provider service point"
                                )
                            }
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(
                                    imageVector = Icons.Outlined.LocationOn,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    if (latitude.isBlank() || longitude.isBlank()) "Select a service point on the map"
                                    else "Latitude ${String.format(Locale.US, "%.6f", latitude.toDouble())}, " +
                                        "Longitude ${String.format(Locale.US, "%.6f", longitude.toDouble())}",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            locationError?.let { Text(it, color = MaterialTheme.colorScheme.error) }

                            Text("Service radius: ${selectedRadius.toInt()} km", fontWeight = FontWeight.SemiBold)
                            Slider(
                                value = selectedRadius,
                                onValueChange = { selectedRadius = it },
                                valueRange = 1f..100f,
                                steps = 98
                            )
                            Text("Service categories * (At least 1 required)", fontWeight = FontWeight.SemiBold)
                            providerServiceCategories.chunked(2).forEach { rowCategories ->
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    rowCategories.forEach { category ->
                                        val normalizedCat = ServiceCategories.normalize(category)
                                        FilterChip(
                                            selected = normalizedCat in selectedCategories.map { ServiceCategories.normalize(it) },
                                            onClick = {
                                                selectedCategories = if (normalizedCat in selectedCategories.map { ServiceCategories.normalize(it) }) {
                                                    selectedCategories.filter { ServiceCategories.normalize(it) != normalizedCat }.toSet()
                                                } else selectedCategories + normalizedCat
                                            },
                                            label = { Text(category) }
                                        )
                                    }
                                }
                            }
                            if (selectedCategories.isEmpty()) {
                                Text("Please select at least one service category.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text("Emergency Availability", fontWeight = FontWeight.Bold)
                                    Text(
                                        if (availableForEmergency) {
                                            "After saving, your registered service location stays visible after logout. Sign in to respond to customer requests."
                                        } else {
                                            "After saving, your service location will be hidden from emergency search."
                                        },
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                                Switch(
                                    checked = availableForEmergency,
                                    onCheckedChange = { availableForEmergency = it }
                                )
                            }
                            uiState.errorMessage?.let {
                                Text(it, color = MaterialTheme.colorScheme.error)
                            }
                            uiState.successMessage?.let {
                                Text(it, color = Color(0xFF10B981))
                            }
                            if (uiState.saved) {
                                Text("Service profile saved.", color = MaterialTheme.colorScheme.primary)
                            }
                            Button(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(14.dp),
                                enabled = !uiState.isUpdating && !isGettingLocation && selectedCategories.isNotEmpty(),
                                onClick = {
                                    viewModel.saveServiceProfile(
                                        serviceAddress = address,
                                        serviceCity = city,
                                        latitude = latitude,
                                        longitude = longitude,
                                        serviceRadiusKm = selectedRadius.toInt(),
                                        serviceCategories = selectedCategories.toList(),
                                        availableForEmergency = availableForEmergency
                                    )
                                }
                            ) {
                                if (uiState.isUpdating) CircularProgressIndicator()
                                else Text("Save Service Profile")
                            }
                        }
                    }
                }

                // SERVICE REVIEWS SECTION
                ProviderReviewsSection(
                    reviews = providerReviews,
                    categories = (
                        uiState.user?.serviceCategories.orEmpty() +
                            providerReviews.map { it.serviceCategory }
                        ).distinct(),
                    offeredCategories = uiState.user?.serviceCategories.orEmpty(),
                    serviceRatings = ratingResult.getOrNull()?.serviceRatings
                        ?: uiState.user?.serviceRatings.orEmpty(),
                    selectedCategory = selectedReviewCategory,
                    onCategorySelected = { selectedReviewCategory = it },
                    errorMessage = reviewResult.exceptionOrNull()?.localizedMessage
                        ?: ratingResult.exceptionOrNull()?.localizedMessage
                )

                // ACCOUNT QUICK LINKS SECTION
                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(vertical = 8.dp)
                    ) {
                        QuickLinkItem(
                            icon = Icons.Outlined.Person,
                            title = "Account Details",
                            onClick = { showAccountDetailsDialog = true }
                        )
                        HorizontalDivider(color = Color(0xFFF1F5F9), modifier = Modifier.padding(horizontal = 20.dp))
                        QuickLinkItem(
                            icon = Icons.Outlined.Security,
                            title = "Privacy & Security",
                            onClick = { showPrivacyDialog = true }
                        )
                    }
                }
            }
        }

        // ACCOUNT DETAILS BOTTOM SHEET
        if (showAccountDetailsDialog) {
            ModalBottomSheet(
                onDismissRequest = {
                    showAccountDetailsDialog = false
                    isEditingAccountDetails = false
                },
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
                            text = if (isEditingAccountDetails) "Edit Personal Information" else "Personal Information",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        )
                        TextButton(onClick = {
                            if (!isEditingAccountDetails) {
                                editName = uiState.user?.name.orEmpty()
                                editPhone = uiState.user?.phoneNumber.orEmpty()
                                isEditingAccountDetails = true
                            } else {
                                isEditingAccountDetails = false
                            }
                        }) {
                            Text(
                                text = if (isEditingAccountDetails) "Cancel" else "Edit Profile",
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF2563EB)
                            )
                        }
                    }

                    HorizontalDivider(color = Color(0xFFE2E8F0))

                    if (isEditingAccountDetails) {
                        OutlinedTextField(
                            value = editName,
                            onValueChange = { editName = it },
                            label = { Text("Full Name *") },
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = editPhone,
                            onValueChange = { editPhone = it },
                            label = { Text("Phone Number *") },
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        )

                        uiState.errorMessage?.let {
                            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                        }
                        uiState.successMessage?.let {
                            Text(it, color = Color(0xFF10B981), style = MaterialTheme.typography.bodySmall)
                        }

                        Button(
                            onClick = {
                                viewModel.updateBasicProfile(editName, editPhone)
                                isEditingAccountDetails = false
                            },
                            enabled = !uiState.isUpdatingProfile,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth().height(48.dp)
                        ) {
                            if (uiState.isUpdatingProfile) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            } else {
                                Text("Save Changes", fontWeight = FontWeight.Bold)
                            }
                        }
                    } else {
                        // Name Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Person,
                                contentDescription = null,
                                tint = Color(0xFF2563EB),
                                modifier = Modifier.size(20.dp)
                            )
                            Column {
                                Text(
                                    text = "Name",
                                    style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFF64748B), fontSize = 11.5.sp)
                                )
                                Text(
                                    text = uiState.user?.name.orEmpty().ifBlank { "Not provided" },
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold, fontSize = 14.5.sp, color = Color(0xFF1E293B))
                                )
                            }
                        }

                        // Phone Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Phone,
                                contentDescription = null,
                                tint = Color(0xFF2563EB),
                                modifier = Modifier.size(20.dp)
                            )
                            Column {
                                Text(
                                    text = "Phone",
                                    style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFF64748B), fontSize = 11.5.sp)
                                )
                                Text(
                                    text = uiState.user?.phoneNumber.orEmpty().ifBlank { "Not provided" },
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold, fontSize = 14.5.sp, color = Color(0xFF1E293B))
                                )
                            }
                        }

                        // Email Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Email,
                                contentDescription = null,
                                tint = Color(0xFF2563EB),
                                modifier = Modifier.size(20.dp)
                            )
                            Column {
                                Text(
                                    text = "Email",
                                    style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFF64748B), fontSize = 11.5.sp)
                                )
                                Text(
                                    text = "${uiState.user?.email.orEmpty().ifBlank { "Not available" }} (Cannot be changed)",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold, fontSize = 14.5.sp, color = Color(0xFF1E293B))
                                )
                            }
                        }

                        uiState.successMessage?.let {
                            Text(it, color = Color(0xFF10B981), style = MaterialTheme.typography.bodySmall)
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }

        // PRIVACY & SECURITY BOTTOM SHEET
        if (showPrivacyDialog) {
            ModalBottomSheet(
                onDismissRequest = { showPrivacyDialog = false },
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
                            text = "Privacy & Security",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        )
                        TextButton(onClick = { showPrivacyDialog = false }) {
                            Text("Close")
                        }
                    }

                    HorizontalDivider(color = Color(0xFFE2E8F0))

                    Text(
                        text = "Your account uses Firebase Authentication. Location and service-request data are handled according to the app's Firebase security rules and privacy settings. Avoid sharing sensitive information in public service descriptions.",
                        style = MaterialTheme.typography.bodyMedium.copy(color = Color(0xFF475569))
                    )

                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }

        // SETTINGS BOTTOM SHEET
        if (showSettingsSheet) {
            ModalBottomSheet(
                onDismissRequest = { showSettingsSheet = false },
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "⚙️ Settings",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                        )
                        TextButton(onClick = { showSettingsSheet = false }) {
                            Text("Close")
                        }
                    }

                    HorizontalDivider(color = Color(0xFFE2E8F0))

                    // Dark Mode Toggle Row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.DarkMode,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(22.dp)
                            )
                            Text(
                                text = "Dark Mode",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                            )
                        }
                        Switch(
                            checked = isDarkMode,
                            onCheckedChange = { ThemeState.setDarkMode(context, it) }
                        )
                    }

                    // Notifications Setting Row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Notifications,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(22.dp)
                            )
                            Text(
                                text = "Push Notifications",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                            )
                        }
                        Switch(
                            checked = true,
                            onCheckedChange = {}
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = Color(0xFFF8FAFC),
                        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "Fixly App Version",
                                style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFF64748B), fontWeight = FontWeight.Bold)
                            )
                            Text(
                                text = "Development build",
                                style = MaterialTheme.typography.titleSmall.copy(color = Color(0xFF1E293B), fontWeight = FontWeight.Bold)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }
}

@Composable
private fun StatItem(value: String, label: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1E293B),
                fontSize = 16.sp
            )
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall.copy(
                color = Color(0xFF64748B),
                fontSize = 11.5.sp
            )
        )
    }
}

@Composable
private fun QuickLinkItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        color = Color.Transparent,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFFEFF6FF)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = Color(0xFF2563EB),
                        modifier = Modifier.size(20.dp)
                    )
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp,
                        color = Color(0xFF1E293B)
                    )
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = Color(0xFF94A3B8),
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun ProviderReviewsSection(
    reviews: List<ServiceReview>,
    categories: List<String>,
    offeredCategories: List<String>,
    serviceRatings: Map<String, ServiceRating>,
    selectedCategory: String,
    onCategorySelected: (String) -> Unit,
    errorMessage: String?
) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Service Reviews",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = Color(0xFF1E293B)
                    )
                )
                Text(
                    text = "${reviews.size} Reviews",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = Color(0xFF64748B),
                        fontWeight = FontWeight.Medium
                    )
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                (listOf("All") + categories + "Other categories").distinct().forEach { category ->
                    FilterChip(
                        selected = selectedCategory == category,
                        onClick = { onCategorySelected(category) },
                        shape = RoundedCornerShape(12.dp),
                        label = {
                            val rating = serviceRatings[category]
                            Text(
                                if (rating != null && rating.reviewCount > 0) {
                                    "$category · ${String.format(Locale.US, "%.1f", rating.averageRating)} (${rating.reviewCount})"
                                } else category
                            )
                        }
                    )
                }
            }

            errorMessage?.let {
                Text("Couldn't load reviews: $it", color = MaterialTheme.colorScheme.error)
            }

            val filteredReviews = when (selectedCategory) {
                "All" -> reviews
                "Other categories" -> reviews.filter { it.serviceCategory !in offeredCategories }
                else -> reviews.filter { it.serviceCategory == selectedCategory }
            }

            if (filteredReviews.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxWidth().height(100.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No reviews for this category yet.",
                        style = MaterialTheme.typography.bodyMedium.copy(color = Color(0xFF64748B))
                    )
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    filteredReviews.forEach { review ->
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = Color(0xFFF8FAFC),
                            border = BorderStroke(1.dp, Color(0xFFF1F5F9)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = review.serviceCategory,
                                        style = MaterialTheme.typography.titleSmall.copy(
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF2563EB)
                                        )
                                    )
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.Star,
                                            contentDescription = null,
                                            tint = Color(0xFFF59E0B),
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "${review.rating}.0",
                                            style = MaterialTheme.typography.titleSmall.copy(
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFF1E293B)
                                            )
                                        )
                                    }
                                }
                                if (review.comment.isNotBlank()) {
                                    Text(
                                        text = review.comment,
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            color = Color(0xFF334155),
                                            fontSize = 14.sp
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
