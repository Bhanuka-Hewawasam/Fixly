package lk.ac.nibm.hdse.fixly_20.ui.screens

import android.Manifest
import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.BackHandler
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Send
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
import coil.compose.AsyncImage
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.firebase.firestore.GeoPoint
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.rememberCameraPositionState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import lk.ac.nibm.hdse.fixly_20.data.repository.EmergencyAlertRepository
import lk.ac.nibm.hdse.fixly_20.model.EmergencyAlert
import lk.ac.nibm.hdse.fixly_20.model.EmergencyAlertPolicy
import lk.ac.nibm.hdse.fixly_20.model.User
import lk.ac.nibm.hdse.fixly_20.ui.components.BottomNavItem
import lk.ac.nibm.hdse.fixly_20.ui.components.FixlyBottomNavBar
import lk.ac.nibm.hdse.fixly_20.ui.components.formatRating
import lk.ac.nibm.hdse.fixly_20.util.EmergencyProviderMatcher
import lk.ac.nibm.hdse.fixly_20.util.openInMaps
import lk.ac.nibm.hdse.fixly_20.viewmodel.EmergencyViewModel
import lk.ac.nibm.hdse.fixly_20.viewmodel.ProviderWithDistance
import java.text.SimpleDateFormat
import java.util.Locale

private val categoryFilters = listOf(
    "Vehicle Mechanic",
    "Electrician",
    "Plumber",
    "AC Technician",
    "Computer Technician",
    "Other"
)

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("MissingPermission") // permission is checked in requestLocation() before any location call
@Composable
fun EmergencyScreen(
    onBack: () -> Unit,
    onPostUrgentJob: () -> Unit,
    viewModel: EmergencyViewModel = viewModel(),
    onNavigate: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val isDebugBuild = (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
    val coroutineScope = rememberCoroutineScope()
    val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }
    val alertRepository = remember { EmergencyAlertRepository() }

    val uiState by viewModel.uiState.collectAsState()
    val myActiveAlertFlow by alertRepository.observeMyActiveAlert().collectAsState(initial = null)

    var isLocating by remember { mutableStateOf(false) }
    var locationFailed by remember { mutableStateOf(false) }
    var selectedProvider by remember { mutableStateOf<ProviderWithDistance?>(null) }
    var showDemoProviders by remember { mutableStateOf(false) }

    // Request help dialog & active request states
    var requestingProvider by remember { mutableStateOf<ProviderWithDistance?>(null) }
    var requestNote by remember { mutableStateOf("") }
    var activeRequestId by remember { mutableStateOf<String?>(null) }
    var activeAlert by remember { mutableStateOf<EmergencyAlert?>(null) }
    var selectedProviderPhone by remember { mutableStateOf<String?>(null) }
    var providerContactError by remember { mutableStateOf<String?>(null) }
    var requestErrorMessage by remember { mutableStateOf<String?>(null) }
    var isSendingRequest by remember { mutableStateOf(false) }
    var isMinimized by remember { mutableStateOf(false) }
    var showCancelConfirmation by remember { mutableStateOf(false) }

    // Automatically bind to existing active alert if customer returns to Emergency
    LaunchedEffect(myActiveAlertFlow) {
        val latestAlert = myActiveAlertFlow
        if (latestAlert != null && EmergencyAlertPolicy.isLiveForCustomer(latestAlert.status) &&
            activeRequestId == null
        ) {
            activeRequestId = latestAlert.alertId
            isMinimized = false
        } else if (latestAlert != null && !EmergencyAlertPolicy.isLiveForCustomer(latestAlert.status) &&
            activeRequestId == latestAlert.alertId
        ) {
            activeRequestId = null
            activeAlert = null
        }
    }

    LaunchedEffect(activeRequestId) {
        if (activeRequestId != null) {
            alertRepository.observeAlert(activeRequestId!!).collect { alert ->
                activeAlert = alert
                if (alert != null && !EmergencyAlertPolicy.isLiveForCustomer(alert.status)) {
                    activeRequestId = null
                }
            }
        } else {
            activeAlert = null
            isMinimized = false
        }
    }

    LaunchedEffect(activeAlert?.alertId, activeAlert?.selectedProviderId, activeAlert?.status) {
        selectedProviderPhone = null
        providerContactError = null
        val alert = activeAlert
        val providerId = alert?.selectedProviderId
        if (alert != null && providerId != null && alert.status in listOf("ACTIVE", "ACCEPTED")) {
            alertRepository.getEmergencyProviderPhone(alert.alertId, providerId)
                .onSuccess { selectedProviderPhone = it }
                .onFailure { providerContactError = it.localizedMessage ?: "Couldn't load provider contact details." }
        }
    }

    val customerLatLng = uiState.customerLocation?.let { LatLng(it.latitude, it.longitude) }
    val demoProviderMatches = remember(uiState.customerLocation, uiState.selectedCategory, uiState.radiusKm) {
        val customerLocation = uiState.customerLocation
        if (customerLocation == null) {
            emptyList()
        } else {
            val sampleProviders = listOf(
                User(
                    uid = "demo-preview-mechanic",
                    name = "Sample Vehicle Mechanic",
                    phoneNumber = "077 000 0001",
                    roles = listOf("provider"),
                    serviceCategories = listOf("Vehicle Mechanic"),
                    ratingSum = 46.0,
                    ratingCount = 10,
                    isVerified = true,
                    availableForEmergency = true,
                    serviceAddress = "Sample service point",
                    serviceCity = "Nearby",
                    serviceLatitude = customerLocation.latitude + 0.003,
                    serviceLongitude = customerLocation.longitude + 0.002,
                    serviceRadiusKm = 20
                ),
                User(
                    uid = "demo-preview-electrician",
                    name = "Sample Electrician",
                    phoneNumber = "077 000 0002",
                    roles = listOf("provider"),
                    serviceCategories = listOf("Electrician"),
                    ratingSum = 43.0,
                    ratingCount = 9,
                    availableForEmergency = true,
                    serviceAddress = "Sample service point",
                    serviceCity = "Nearby",
                    serviceLatitude = customerLocation.latitude - 0.006,
                    serviceLongitude = customerLocation.longitude + 0.004,
                    serviceRadiusKm = 20
                ),
                User(
                    uid = "demo-preview-plumber",
                    name = "Sample Plumber",
                    phoneNumber = "077 000 0003",
                    roles = listOf("provider"),
                    serviceCategories = listOf("Plumber"),
                    ratingSum = 48.0,
                    ratingCount = 12,
                    isVerified = true,
                    availableForEmergency = true,
                    serviceAddress = "Sample service point",
                    serviceCity = "Nearby",
                    serviceLatitude = customerLocation.latitude + 0.014,
                    serviceLongitude = customerLocation.longitude - 0.008,
                    serviceRadiusKm = 20
                )
            )
            EmergencyProviderMatcher.findMatches(
                providers = sampleProviders,
                customerLocation = customerLocation,
                category = uiState.selectedCategory,
                radiusKm = uiState.radiusKm,
                currentUid = null,
                currentEmail = null
            ).map { ProviderWithDistance(it.provider, it.distanceKm, isDemo = true) }
        }
    }
    val displayedProviders = if (isDebugBuild && showDemoProviders) {
        (uiState.results + demoProviderMatches).sortedBy { it.distanceKm }
    } else {
        uiState.results
    }
    val cameraPositionState = rememberCameraPositionState {
        customerLatLng?.let { position = CameraPosition.fromLatLngZoom(it, 13f) }
    }

    // Update camera when customer location changes for the first time
    LaunchedEffect(customerLatLng) {
        customerLatLng?.let {
            cameraPositionState.position = CameraPosition.fromLatLngZoom(it, 13f)
        }
    }

    // Fresh GPS fix: this is "where the emergency is happening"
    fun fetchLocation() {
        isLocating = true
        locationFailed = false
        fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
            .addOnSuccessListener { location ->
                isLocating = false
                if (location != null) {
                    viewModel.setCustomerLocation(GeoPoint(location.latitude, location.longitude))
                } else {
                    locationFailed = true
                }
            }
            .addOnFailureListener {
                isLocating = false
                locationFailed = true
            }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) fetchLocation() else locationFailed = true
    }

    fun requestLocation() {
        val hasPermission = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (hasPermission) fetchLocation()
        else permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
    }

    LaunchedEffect(Unit) { requestLocation() }

    // Opens the phone's dialer with the number filled in (no CALL_PHONE permission needed)
    fun callProvider(phoneNumber: String) {
        try {
            context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(phoneNumber))))
        } catch (e: ActivityNotFoundException) {
            // Device has no dialer app
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(9.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFEF4444))
                        )
                        Text(
                            text = "Emergency",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 19.sp,
                                color = Color(0xFF0F172A)
                            )
                        )
                    }
                },
                navigationIcon = {
                    Surface(
                        shape = CircleShape,
                        color = Color(0xFFF1F5F9),
                        modifier = Modifier.padding(start = 12.dp).size(38.dp)
                    ) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color(0xFF334155), modifier = Modifier.size(18.dp))
                        }
                    }
                },
                actions = {
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = Color(0xFFFEE2E2),
                        border = BorderStroke(1.dp, Color(0xFFFECACA)),
                        modifier = Modifier.padding(end = 16.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.ElectricBolt,
                                contentDescription = null,
                                tint = Color(0xFFDC2626),
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = "24/7 Live",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFDC2626),
                                    fontSize = 11.5.sp
                                )
                            )
                        }
                    }
                }
            )
        },
        bottomBar = {
            FixlyBottomNavBar(
                currentRoute = BottomNavItem.Emergency.route,
                activeRole = "customer",
                onNavigate = onNavigate
            )
        }
    ) { padding ->
        when {
            // --- Step A: we need the customer's position first ---
            uiState.customerLocation == null -> {
                Box(
                    modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        if (isLocating) {
                            CircularProgressIndicator()
                            Spacer(modifier = Modifier.height(12.dp))
                            Text("Finding your location...")
                        } else {
                            Text(
                                text = if (locationFailed)
                                    "Couldn't get your location. Turn on GPS and allow location permission."
                                else
                                    "We need your location to find help near you.",
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Button(onClick = { requestLocation() }) { Text("Enable Location") }
                        }
                    }
                }
            }

            // --- Step B: filters + map + registered service locations ---
            else -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .verticalScroll(rememberScrollState())
                        .padding(bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {

                    // --- URGENT ALERT BANNER ---
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    brush = Brush.linearGradient(
                                        colors = listOf(Color(0xFFEF4444), Color(0xFFE11D48), Color(0xFFBE123C))
                                    )
                                )
                                .padding(18.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(14.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(42.dp)
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(Color.White.copy(alpha = 0.2f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.WarningAmber,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }

                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = "Urgent help, when you need it",
                                        style = MaterialTheme.typography.titleMedium.copy(
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White,
                                            fontSize = 16.sp
                                        )
                                    )

                                    Text(
                                        text = "We'll share your real-time location and instantly notify nearby available emergency providers.",
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            color = Color.White.copy(alpha = 0.9f),
                                            fontSize = 12.5.sp,
                                            lineHeight = 17.sp
                                        )
                                    )

                                    Spacer(modifier = Modifier.height(2.dp))

                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = Color.Black.copy(alpha = 0.2f)
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Outlined.LocationOn,
                                                contentDescription = null,
                                                tint = Color(0xFFFCD34D),
                                                modifier = Modifier.size(13.dp)
                                            )
                                            Spacer(modifier = Modifier.width(5.dp))
                                            Text(
                                                text = "Location verified on request",
                                                style = MaterialTheme.typography.labelSmall.copy(
                                                    fontWeight = FontWeight.Medium,
                                                    color = Color.White,
                                                    fontSize = 11.5.sp
                                                )
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // --- DEBUG ONLY ACTIONS FOR TESTING ---
                    if (isDebugBuild) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text("Test preview (debug build only)", style = MaterialTheme.typography.titleSmall)
                                Text(
                                    "Sample providers appear only on this device. Calls and emergency requests are disabled for samples.",
                                    style = MaterialTheme.typography.bodySmall
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                OutlinedButton(onClick = { showDemoProviders = !showDemoProviders }) {
                                    Text(if (showDemoProviders) "Hide sample providers" else "Show sample providers")
                                }
                            }
                        }
                    }

                    // --- CHOOSE THE HELP YOU NEED SECTION ---
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "CHOOSE HELP TYPE",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF0F172A),
                                    letterSpacing = 0.5.sp,
                                    fontSize = 12.sp
                                )
                            )
                            Text(
                                text = "Select 1 option",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = Color(0xFF94A3B8),
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 11.5.sp
                                )
                            )
                        }

                        // 2-Column Grid for categories
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            categoryFilters.chunked(2).forEach { rowItems ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    rowItems.forEach { cat ->
                                        val isSelected = uiState.selectedCategory == cat
                                        val icon = when (cat.lowercase().trim()) {
                                            "vehicle mechanic" -> Icons.Outlined.Build
                                            "electrician" -> Icons.Outlined.ElectricBolt
                                            "plumber" -> Icons.Outlined.WaterDrop
                                            "ac technician" -> Icons.Outlined.AcUnit
                                            "computer technician", "computer tech" -> Icons.Outlined.Computer
                                            else -> Icons.Outlined.MoreHoriz
                                        }

                                        Card(
                                            onClick = { viewModel.setCategory(cat) },
                                            shape = RoundedCornerShape(16.dp),
                                            colors = CardDefaults.cardColors(
                                                containerColor = MaterialTheme.colorScheme.surface
                                            ),
                                            border = BorderStroke(
                                                1.dp,
                                                if (isSelected) Color(0xFFEF4444) else Color(0xFFE2E8F0)
                                            ),
                                            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(12.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(34.dp)
                                                        .clip(RoundedCornerShape(10.dp))
                                                        .background(Color(0xFFF1F5F9)),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Icon(
                                                        imageVector = icon,
                                                        contentDescription = null,
                                                        tint = Color(0xFF475569),
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                }
                                                Text(
                                                    text = when (cat) {
                                                        "Computer Technician" -> "Computer Tech"
                                                        "Other" -> "Other Service"
                                                        else -> cat
                                                    },
                                                    style = MaterialTheme.typography.bodyMedium.copy(
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 12.5.sp,
                                                        color = Color(0xFF1E293B)
                                                    ),
                                                    maxLines = 1
                                                )
                                            }
                                        }
                                    }
                                    if (rowItems.size == 1) {
                                        Spacer(modifier = Modifier.weight(1f))
                                    }
                                }
                            }
                        }

                        // Any Available Provider (Full Width Highlighted Card below grid)
                        Card(
                            onClick = { viewModel.setCategory("Any Available Provider") },
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = Color(0xFFEF4444)
                            ),
                            elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 2.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(Color.White.copy(alpha = 0.2f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.Security,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                    Column {
                                        Text(
                                            text = "Any Available Provider",
                                            style = MaterialTheme.typography.titleMedium.copy(
                                                fontWeight = FontWeight.Bold,
                                                color = Color.White,
                                                fontSize = 14.5.sp
                                            )
                                        )
                                        Spacer(modifier = Modifier.height(1.dp))
                                        Text(
                                            text = "Fastest dispatch response",
                                            style = MaterialTheme.typography.bodySmall.copy(
                                                color = Color.White.copy(alpha = 0.9f),
                                                fontSize = 11.5.sp
                                            )
                                        )
                                    }
                                }
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }

                    // --- OPTIONAL NOTES BOX ("What happened?") ---
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Icon(
                                        imageVector = Icons.Outlined.ChatBubbleOutline,
                                        contentDescription = null,
                                        tint = Color(0xFF64748B),
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Text(
                                        text = "What happened?",
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, fontSize = 13.5.sp, color = Color(0xFF334155))
                                    )
                                    Text(
                                        text = "(optional)",
                                        style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFF94A3B8), fontSize = 11.5.sp)
                                    )
                                }
                                Text(
                                    text = "${requestNote.length}/500",
                                    style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFF94A3B8), fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold)
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedTextField(
                                value = requestNote,
                                onValueChange = { if (it.length <= 500) requestNote = it },
                                placeholder = {
                                    Text(
                                        text = "Describe your emergency situation briefly...",
                                        style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFF94A3B8), fontSize = 12.sp)
                                    )
                                },
                                shape = RoundedCornerShape(12.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Color(0xFFEF4444),
                                    unfocusedBorderColor = Color(0xFFE2E8F0),
                                    focusedContainerColor = Color(0xFFF8FAFC),
                                    unfocusedContainerColor = Color(0xFFF8FAFC)
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }

                    // --- ACTION BUTTONS & LOCATION ---
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = { requestLocation() },
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = Color(0xFFF8FAFC),
                                contentColor = Color(0xFF334155)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.MyLocation,
                                contentDescription = null,
                                tint = Color(0xFFEF4444),
                                modifier = Modifier.size(17.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (isLocating) "Getting location…" else "Get my current location",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            )
                        }

                        // Primary Send Emergency Request Button
                        Button(
                            onClick = {
                                if (displayedProviders.isNotEmpty()) {
                                    requestingProvider = displayedProviders.first()
                                } else {
                                    requestErrorMessage = "No providers available nearby."
                                }
                            },
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color.Transparent
                            ),
                            contentPadding = PaddingValues(0.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(
                                        brush = Brush.linearGradient(
                                            colors = listOf(Color(0xFFDC2626), Color(0xFFE11D48))
                                        ),
                                        shape = RoundedCornerShape(16.dp)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Outlined.Send,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Send Emergency Request",
                                        style = MaterialTheme.typography.titleSmall.copy(
                                            fontWeight = FontWeight.ExtraBold,
                                            color = Color.White,
                                            fontSize = 13.5.sp
                                        )
                                    )
                                }
                            }
                        }

                        // Privacy badge text
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Lock,
                                contentDescription = null,
                                tint = Color(0xFF94A3B8),
                                modifier = Modifier.size(11.dp)
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = "Your location is shared only to help providers reach you.",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = Color(0xFF94A3B8),
                                    fontSize = 11.sp
                                ),
                                textAlign = TextAlign.Center
                            )
                        }

                        OutlinedButton(
                            onClick = onPostUrgentJob,
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = MaterialTheme.colorScheme.surface,
                                contentColor = Color(0xFF475569)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                        ) {
                            Text(
                                text = "Browse nearby providers instead",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold, fontSize = 12.5.sp)
                            )
                        }
                    }

                    // --- Google Map & Provider Results ---
                    if (customerLatLng != null) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(220.dp)
                                .padding(horizontal = 20.dp, vertical = 4.dp)
                                .clip(RoundedCornerShape(20.dp))
                        ) {
                            GoogleMap(
                                modifier = Modifier.fillMaxSize(),
                                cameraPositionState = cameraPositionState
                            ) {
                                Marker(
                                    state = MarkerState(position = customerLatLng),
                                    title = "You are here"
                                )

                                displayedProviders.forEach { item ->
                                    val latitude = item.provider.serviceLatitude
                                    val longitude = item.provider.serviceLongitude
                                    if (latitude != null && longitude != null) {
                                        val pLatLng = LatLng(latitude, longitude)
                                        Marker(
                                            state = MarkerState(position = pLatLng),
                                            title = "Service Location: ${item.provider.name}",
                                            snippet = "${String.format(Locale.getDefault(), "%.1f", item.distanceKm)} km away",
                                            onClick = {
                                                selectedProvider = item
                                                false
                                            }
                                        )
                                    }
                                }
                            }
                            Surface(
                                modifier = Modifier.align(Alignment.TopStart).padding(12.dp),
                                shape = RoundedCornerShape(50),
                                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
                                tonalElevation = 3.dp
                            ) {
                                Text(
                                    "${displayedProviders.size} available",
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }

                    when {
                        uiState.isLoading -> {
                            Box(
                                modifier = Modifier.fillMaxWidth().height(150.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator()
                            }
                        }

                        displayedProviders.isEmpty() -> {
                            Box(
                                modifier = Modifier.fillMaxWidth().padding(24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        "No matching providers found within ${uiState.radiusKm} km. " +
                                            "Try another service or a wider search radius. Providers must have Emergency Availability enabled and a registered service location within their service radius.",
                                        textAlign = TextAlign.Center
                                    )
                                    Spacer(Modifier.height(12.dp))
                                    Button(onClick = onPostUrgentJob) { Text("Post an Urgent Job") }
                                }
                            }
                        }

                        else -> {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                displayedProviders.forEach { item ->
                                    ProviderCard(
                                        item = item,
                                        category = uiState.selectedCategory,
                                        isSelected = selectedProvider?.provider?.uid == item.provider.uid,
                                        onClick = {
                                            selectedProvider = item
                                            val latitude = item.provider.serviceLatitude
                                            val longitude = item.provider.serviceLongitude
                                            if (latitude != null && longitude != null) {
                                                val latLng = LatLng(latitude, longitude)
                                                cameraPositionState.position = CameraPosition.fromLatLngZoom(latLng, 15f)
                                            }
                                        },
                                        onRequestHelp = {
                                            if (activeRequestId != null) {
                                                requestErrorMessage = "You already have an active emergency request."
                                                isMinimized = false
                                            } else {
                                                requestingProvider = item
                                            }
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // --- REQUEST HELP NOTE DIALOG ---
    requestingProvider?.let { item ->
        AlertDialog(
            onDismissRequest = { requestingProvider = null },
            shape = RoundedCornerShape(24.dp),
            title = { Text("Request Help from ${item.provider.name}") },
            text = {
                Column {
                    Text(
                        "This request appears instantly in the provider dashboard while the provider is online. Phone push notifications require a backend push service.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = requestNote,
                        onValueChange = { requestNote = it },
                        label = { Text("What happened? (optional note)") },
                        placeholder = { Text("e.g. flat tyre, engine won't start") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    requestErrorMessage?.let { err ->
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(err, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        isSendingRequest = true
                        requestErrorMessage = null
                        viewModel.sendDirectRequest(
                            providerId = item.provider.uid,
                            distanceKm = item.distanceKm,
                            note = requestNote,
                            category = if (uiState.selectedCategory == "Any Available Provider") {
                                "Any"
                            } else uiState.selectedCategory
                        ) { result ->
                            isSendingRequest = false
                            result.onSuccess { alertId ->
                                activeRequestId = alertId
                                isMinimized = false
                                requestingProvider = null
                                requestNote = ""
                            }.onFailure { e ->
                                requestErrorMessage = e.localizedMessage ?: "Failed to send request"
                            }
                        }
                    },
                    shape = RoundedCornerShape(12.dp),
                    enabled = !isSendingRequest
                ) {
                    if (isSendingRequest) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    } else {
                        Text("Confirm Request Help")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { requestingProvider = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // --- ACTIVE REQUEST WAITING & STATUS VIEW ---
    activeRequestId?.let { reqId ->
        val alert = activeAlert
        val status = alert?.status ?: "ACTIVE"
        val isAccepted = status == "ACCEPTED"
        val isDeclined = status == "DECLINED"
        val isCancelled = status == "CANCELLED"
        val isClosed = status == "CLOSED"

        val now = System.currentTimeMillis()
        val expiresAtTime = alert?.expiresAt?.time ?: ((alert?.createdAt?.time ?: now) + EmergencyAlertRepository.ALERT_LIFETIME_MS)
        val diffMs = expiresAtTime - now
        val isExpired = status == "EXPIRED" || diffMs <= 0L

        // Timer calculation
        var remainingSeconds by remember(reqId, expiresAtTime) { mutableStateOf(maxOf(0L, diffMs / 1000)) }
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

        BackHandler(enabled = !isMinimized && (status == "ACTIVE" || status == "PENDING")) {
            isMinimized = true
        }

        if (isMinimized) {
            // Minimized Floating Status Card at the bottom
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
                    modifier = Modifier.fillMaxWidth().clickable { isMinimized = false }
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp).fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (isAccepted) "✅ Provider Accepted!" else "🚨 Waiting for provider",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = if (isAccepted) "Tap to view provider details" else "Provider response time remaining: $countdownStr",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        Button(
                            onClick = { isMinimized = false },
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Open")
                        }
                    }
                }
            }
        } else {
            // Expanded Modal Bottom Sheet view
            ModalBottomSheet(
                onDismissRequest = {
                    if (status != "ACTIVE" && status != "PENDING") {
                        activeRequestId = null
                    } else {
                        isMinimized = true
                    }
                },
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp)
                        .verticalScroll(rememberScrollState())
                        .navigationBarsPadding(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = when {
                                isAccepted -> "✅ Provider Accepted Your Request!"
                                isDeclined -> "⚠️ Request Declined"
                                isCancelled -> "⚠️ Request Cancelled"
                                isClosed -> "Request closed"
                                isExpired || remainingSeconds <= 0L -> "⚠️ Request Expired"
                                else -> "Waiting for provider response"
                            },
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            if (status == "ACTIVE" || status == "PENDING") {
                                TextButton(onClick = { isMinimized = true }) {
                                    Text("Minimize")
                                }
                            }
                            TextButton(onClick = { activeRequestId = null }) {
                                Text("Close")
                            }
                        }
                    }

                    if (!isAccepted && selectedProviderPhone != null) {
                        val selectedProvider = alert?.selectedProviderId
                            ?.let { providerId -> displayedProviders.firstOrNull { it.provider.uid == providerId }?.provider }
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    "Contact ${selectedProvider?.name ?: "your selected provider"}",
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text("Service: ${alert?.category ?: "Emergency assistance"}")
                                Text(
                                    selectedProviderPhone.orEmpty(),
                                    style = MaterialTheme.typography.bodyLarge
                                )
                                Button(
                                    onClick = { callProvider(selectedProviderPhone.orEmpty()) },
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Call provider")
                                }
                            }
                        }
                    } else if (alert?.selectedProviderId != null && providerContactError != null) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                providerContactError.orEmpty(),
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall
                            )
                            TextButton(
                                onClick = {
                                    coroutineScope.launch {
                                        alertRepository.getEmergencyProviderPhone(
                                            reqId,
                                            alert.selectedProviderId.orEmpty()
                                        ).onSuccess {
                                            selectedProviderPhone = it
                                            providerContactError = null
                                        }.onFailure {
                                            providerContactError = it.localizedMessage
                                                ?: "Couldn't load provider contact details."
                                        }
                                    }
                                }
                            ) { Text("Retry loading number") }
                        }
                    }

                    when {
                        isAccepted -> {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text(
                                        "Emergency",
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(50))
                                            .background(MaterialTheme.colorScheme.error)
                                            .padding(horizontal = 12.dp, vertical = 5.dp),
                                        color = MaterialTheme.colorScheme.onError,
                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                                    )
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Text(
                                        "Provider accepted your request",
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        "Help needed: ${alert?.category ?: "Emergency assistance"}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    if (!alert?.note.isNullOrBlank()) {
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text("Note: ${alert?.note}", style = MaterialTheme.typography.bodySmall)
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text("Provider: ${alert?.acceptedProviderName ?: "Provider"}", style = MaterialTheme.typography.titleMedium)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text("Phone: ${alert?.acceptedProviderPhone ?: "N/A"}")
                                    Text("Your provider is on the way.")
                                    Text("Services: ${alert?.category ?: "General"}")
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                if (!alert?.acceptedProviderPhone.isNullOrBlank()) {
                                    Button(
                                        onClick = { alert.acceptedProviderPhone.let { callProvider(it) } },
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("📞 Call Provider")
                                    }
                                }
                                if (alert?.location != null) {
                                    OutlinedButton(
                                        onClick = {
                                            context.openInMaps(alert.location!!.latitude, alert.location!!.longitude, alert.acceptedProviderName ?: "Provider")
                                        },
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("🗺️ Open in Maps")
                                    }
                                }
                            }
                            OutlinedButton(
                                onClick = { showCancelConfirmation = true },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) { Text("Close request") }
                            if (showCancelConfirmation) {
                                AlertDialog(
                                    onDismissRequest = { showCancelConfirmation = false },
                                    shape = RoundedCornerShape(24.dp),
                                    title = { Text("Close this request?") },
                                    text = { Text("This will mark the accepted emergency request as closed.") },
                                    confirmButton = {
                                        TextButton(
                                            onClick = {
                                                showCancelConfirmation = false
                                                coroutineScope.launch {
                                                    alertRepository.finishAcceptedAlert(reqId)
                                                        .onFailure { requestErrorMessage = it.localizedMessage }
                                                }
                                            }
                                        ) { Text("Close request") }
                                    },
                                    dismissButton = {
                                        TextButton(onClick = { showCancelConfirmation = false }) {
                                            Text("Keep request")
                                        }
                                    }
                                )
                            }
                        }
                        isDeclined -> {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "This provider declined the request.",
                                    modifier = Modifier.padding(16.dp),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = { activeRequestId = null },
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Choose Another Provider")
                                }
                                OutlinedButton(
                                    onClick = {
                                        activeRequestId = null
                                        onBack()
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Call Provider")
                                }
                            }
                        }
                        isCancelled -> {
                            Text("Request cancelled.")
                            Button(
                                onClick = { activeRequestId = null },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Return to Emergency Map")
                            }
                        }
                        isClosed -> {
                            Text("This emergency request is closed.")
                            Button(
                                onClick = { activeRequestId = null },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) { Text("Return to Emergency Map") }
                        }
                        isExpired || remainingSeconds <= 0L -> {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "This request expired because the provider did not respond.",
                                    modifier = Modifier.padding(16.dp),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                Button(
                                    onClick = { activeRequestId = null },
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Choose Another Provider")
                                }
                                OutlinedButton(
                                    onClick = {
                                        activeRequestId = null
                                        onBack()
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Browse Nearby Providers")
                                }
                                TextButton(
                                    onClick = {
                                        activeRequestId = null
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Cancel / Close")
                                }
                            }
                        }
                        else -> {
                            // ACTIVE / PENDING waiting state
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)),
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text("Waiting for provider response", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text("Your request was sent to the selected provider.")
                                    Text("The provider has been notified in their dashboard.")
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text("Service Category: ${alert?.category ?: uiState.selectedCategory}")
                                    val timeFormat = SimpleDateFormat("hh:mm a", Locale.getDefault())
                                    val createdAtStr = alert?.createdAt?.let { timeFormat.format(it) } ?: "Just now"
                                    Text("Request created at: $createdAtStr")
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text("Provider response time remaining: $countdownStr", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))
                            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator()
                            }
                            Spacer(modifier = Modifier.height(12.dp))

                            if (status == "ACTIVE") {
                                OutlinedButton(
                                    onClick = { showCancelConfirmation = true },
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) { Text("Cancel emergency request") }
                            } else if (isAccepted) {
                                Button(
                                    onClick = { showCancelConfirmation = true },
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) { Text("Close request") }
                            }

                            if (showCancelConfirmation) {
                                AlertDialog(
                                    onDismissRequest = { showCancelConfirmation = false },
                                    shape = RoundedCornerShape(24.dp),
                                    title = { Text(if (isAccepted) "Close this request?" else "Cancel emergency request?") },
                                    text = {
                                        Text(
                                            if (isAccepted) "This will mark the accepted request as closed."
                                            else "Nearby providers will no longer be able to accept it."
                                        )
                                    },
                                    confirmButton = {
                                        TextButton(
                                            onClick = {
                                                showCancelConfirmation = false
                                                coroutineScope.launch {
                                                    val result = if (isAccepted) {
                                                        alertRepository.finishAcceptedAlert(reqId)
                                                    } else {
                                                        alertRepository.closeAlert(reqId)
                                                    }
                                                    result.onFailure { requestErrorMessage = it.localizedMessage }
                                                }
                                            }
                                        ) { Text(if (isAccepted) "Close request" else "Cancel request") }
                                    },
                                    dismissButton = {
                                        TextButton(onClick = { showCancelConfirmation = false }) {
                                            Text("Keep request")
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProviderCard(
    item: ProviderWithDistance,
    category: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    onRequestHelp: () -> Unit
) {
    val provider = item.provider
    val categoryRating = provider.ratingForCategory(category)
    val distanceText = String.format(Locale.getDefault(), "%.1f", item.distanceKm)
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isSelected) 4.dp else 1.5.dp),
        border = BorderStroke(if (isSelected) 2.dp else 1.dp, if (isSelected) MaterialTheme.colorScheme.primary else Color(0xFFE2E8F0)),
        colors = if (isSelected) {
            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        } else {
            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        }
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier.size(52.dp).clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        provider.name.firstOrNull()?.uppercase() ?: "?",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    if (!provider.profilePictureUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = provider.profilePictureUrl,
                            contentDescription = "${provider.name} profile photo",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop
                        )
                    }
                }

                Spacer(Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(provider.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        if (categoryRating.reviewCount > 0) {
                            formatRating(categoryRating.averageRating, categoryRating.reviewCount)
                        } else if (category == "Any Available Provider" || category == "Any") {
                            "Overall ${formatRating(provider.averageRating, provider.ratingCount)}"
                        } else {
                            "No reviews for $category yet"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF64748B)
                    )
                }
                if (provider.isVerified) {
                    AssistChip(onClick = {}, label = { Text("Verified") })
                } else {
                    AssistChip(
                        onClick = {},
                        label = {
                            Text(
                                provider.verificationStatus
                                    .replace('_', ' ')
                                    .lowercase(Locale.getDefault())
                                    .replaceFirstChar { it.titlecase(Locale.getDefault()) }
                            )
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            Text(
                "Services: ${provider.serviceCategories.joinToString(", ")}",
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "Service Location",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                listOf(provider.serviceAddress, provider.serviceCity)
                    .filter { it.isNotBlank() }
                    .joinToString(", ")
                    .ifBlank { "Location selected on map" },
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFF475569)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "$distanceText km away • serves within ${provider.serviceRadiusKm} km",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF64748B)
            )
            Text(
                "This is the provider’s registered service area, not their live current location.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (isSelected) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    if (item.isDemo) {
                        "Sample preview only. Calls and requests are disabled."
                    } else {
                        "Send a request to this provider to reveal their contact number."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(14.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = onRequestHelp,
                    enabled = !item.isDemo,
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                ) {
                    Text(if (item.isDemo) "Sample only" else "Request help")
                }
                OutlinedButton(
                    onClick = onClick,
                    enabled = !isSelected,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                ) {
                    Text(if (isSelected) "Selected" else "View on map")
                }
            }
        }
    }
}
