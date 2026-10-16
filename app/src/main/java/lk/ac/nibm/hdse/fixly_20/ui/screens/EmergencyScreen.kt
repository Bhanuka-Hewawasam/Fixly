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
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
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
import lk.ac.nibm.hdse.fixly_20.ui.components.formatRating
import lk.ac.nibm.hdse.fixly_20.util.openInMaps
import lk.ac.nibm.hdse.fixly_20.viewmodel.EmergencyViewModel
import lk.ac.nibm.hdse.fixly_20.viewmodel.ProviderWithDistance
import java.text.SimpleDateFormat
import java.util.Locale

private val categoryFilters = listOf(
    "Any Available Provider",
    "Vehicle Mechanic",
    "Electrician",
    "Plumber",
    "AC Technician",
    "Computer Technician",
    "Other"
)
private val radiusOptionsKm = listOf(5, 10, 25, 50)

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("MissingPermission") // permission is checked in requestLocation() before any location call
@Composable
fun EmergencyScreen(
    onBack: () -> Unit,
    onPostUrgentJob: () -> Unit,
    viewModel: EmergencyViewModel = viewModel()
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }
    val alertRepository = remember { EmergencyAlertRepository() }

    val uiState by viewModel.uiState.collectAsState()
    val myActiveAlertFlow by alertRepository.observeMyActiveAlert().collectAsState(initial = null)

    var isLocating by remember { mutableStateOf(false) }
    var locationFailed by remember { mutableStateOf(false) }
    var selectedProvider by remember { mutableStateOf<ProviderWithDistance?>(null) }
    var debugMsg by remember { mutableStateOf<String?>(null) }

    // Request help dialog & active request states
    var requestingProvider by remember { mutableStateOf<ProviderWithDistance?>(null) }
    var requestNote by remember { mutableStateOf("") }
    var activeRequestId by remember { mutableStateOf<String?>(null) }
    var activeAlert by remember { mutableStateOf<EmergencyAlert?>(null) }
    var requestErrorMessage by remember { mutableStateOf<String?>(null) }
    var isSendingRequest by remember { mutableStateOf(false) }
    var isMinimized by remember { mutableStateOf(false) }
    var showCancelConfirmation by remember { mutableStateOf(false) }

    // Automatically bind to existing active alert if customer returns to Emergency
    LaunchedEffect(myActiveAlertFlow) {
        if (myActiveAlertFlow != null && activeRequestId == null) {
            activeRequestId = myActiveAlertFlow!!.alertId
            isMinimized = false
        }
    }

    LaunchedEffect(activeRequestId) {
        if (activeRequestId != null) {
            alertRepository.observeAlert(activeRequestId!!).collect { alert ->
                activeAlert = alert
            }
        } else {
            activeAlert = null
            isMinimized = false
        }
    }

    val customerLatLng = uiState.customerLocation?.let { LatLng(it.latitude, it.longitude) }
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
                title = { Text("🚨 Emergency Help") },
                navigationIcon = { TextButton(onClick = onBack) { Text("Back") } }
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

            // --- Step B: filters + map + live results ---
            else -> {
                Column(modifier = Modifier.fillMaxSize().padding(padding)) {

                    Card(
                        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 8.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                    ) {
                        Text(
                            "Showing providers who are available right now within ${uiState.radiusKm} km of you, nearest first.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(12.dp)
                        )
                    }

                    // --- DEBUG ONLY ACTIONS FOR TESTING ---
                    val isDebug = (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
                    if (isDebug) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text("🛠️ Debug Actions (DEV ONLY)", style = MaterialTheme.typography.titleSmall)
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Button(
                                        onClick = { viewModel.seedDemoProviders { debugMsg = it } },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("Seed Demo Providers", style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
                                    }
                                    OutlinedButton(
                                        onClick = { viewModel.deleteDemoProviders { debugMsg = it } },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("Delete Demo Providers", style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
                                    }
                                }
                                debugMsg?.let { msg ->
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(msg, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSecondaryContainer)
                                }
                            }
                        }
                    }

                    Text(
                        "What do you need?",
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(start = 16.dp, top = 8.dp)
                    )
                    Row(
                        modifier = Modifier
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp, vertical = 2.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        categoryFilters.forEach { category ->
                            FilterChip(
                                selected = uiState.selectedCategory == category,
                                onClick = { viewModel.setCategory(category) },
                                label = { Text(category) }
                            )
                        }
                    }

                    Text(
                        "Search radius",
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(start = 16.dp, top = 2.dp)
                    )
                    Row(
                        modifier = Modifier
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp, vertical = 2.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        radiusOptionsKm.forEach { km ->
                            FilterChip(
                                selected = uiState.radiusKm == km,
                                onClick = { viewModel.setRadius(km) },
                                label = { Text("$km km") }
                            )
                        }
                    }

                    // --- Google Map ---
                    if (customerLatLng != null) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(220.dp)
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            GoogleMap(
                                modifier = Modifier.fillMaxSize(),
                                cameraPositionState = cameraPositionState
                            ) {
                                // Customer location marker
                                Marker(
                                    state = MarkerState(position = customerLatLng),
                                    title = "You are here"
                                )

                                // Provider markers
                                uiState.results.forEach { item ->
                                    val pLoc = item.provider.lastLocation
                                    if (pLoc != null) {
                                        val pLatLng = LatLng(pLoc.latitude, pLoc.longitude)
                                        Marker(
                                            state = MarkerState(position = pLatLng),
                                            title = item.provider.name,
                                            snippet = "${String.format(Locale.getDefault(), "%.1f", item.distanceKm)} km away",
                                            onClick = {
                                                selectedProvider = item
                                                false
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    when {
                        uiState.isLoading -> {
                            Box(
                                modifier = Modifier.fillMaxWidth().weight(1f),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator()
                            }
                        }

                        uiState.results.isEmpty() -> {
                            Box(
                                modifier = Modifier.fillMaxWidth().weight(1f).padding(24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        "No providers available within ${uiState.radiusKm} km right now. " +
                                            "Try a bigger radius, or post an urgent job so providers can respond to you.",
                                        textAlign = TextAlign.Center
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Button(onClick = onPostUrgentJob) { Text("Post an Urgent Job") }
                                }
                            }
                        }

                        else -> {
                            LazyColumn(
                                modifier = Modifier.fillMaxWidth().weight(1f),
                                contentPadding = PaddingValues(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                items(uiState.results) { item ->
                                    ProviderCard(
                                        item = item,
                                        isSelected = selectedProvider?.provider?.uid == item.provider.uid,
                                        onClick = {
                                            selectedProvider = item
                                            item.provider.lastLocation?.let { loc ->
                                                val latLng = LatLng(loc.latitude, loc.longitude)
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
                                        onCall = { callProvider(item.provider.phoneNumber) }
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
                            category = when (uiState.selectedCategory) {
                                "Any Available Provider" -> "Any"
                                "Electrician" -> "Electrical"
                                "Plumber" -> "Plumbing"
                                "AC Technician" -> "AC Repair"
                                else -> uiState.selectedCategory
                            }
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
                        Button(onClick = { isMinimized = false }) {
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
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
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

                    when {
                        isAccepted -> {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
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
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("🗺️ Open in Maps")
                                    }
                                }
                            }
                            OutlinedButton(
                                onClick = { showCancelConfirmation = true },
                                modifier = Modifier.fillMaxWidth()
                            ) { Text("Close request") }
                            if (showCancelConfirmation) {
                                AlertDialog(
                                    onDismissRequest = { showCancelConfirmation = false },
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
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Choose Another Provider")
                                }
                                OutlinedButton(
                                    onClick = {
                                        activeRequestId = null
                                        onBack()
                                    },
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
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Return to Emergency Map")
                            }
                        }
                        isClosed -> {
                            Text("This emergency request is closed.")
                            Button(
                                onClick = { activeRequestId = null },
                                modifier = Modifier.fillMaxWidth()
                            ) { Text("Return to Emergency Map") }
                        }
                        isExpired || remainingSeconds <= 0L -> {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
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
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Choose Another Provider")
                                }
                                OutlinedButton(
                                    onClick = {
                                        activeRequestId = null
                                        onBack()
                                    },
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
                                    modifier = Modifier.fillMaxWidth()
                                ) { Text("Cancel emergency request") }
                            } else if (isAccepted) {
                                Button(
                                    onClick = { showCancelConfirmation = true },
                                    modifier = Modifier.fillMaxWidth()
                                ) { Text("Close request") }
                            }

                            if (showCancelConfirmation) {
                                AlertDialog(
                                    onDismissRequest = { showCancelConfirmation = false },
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
    isSelected: Boolean,
    onClick: () -> Unit,
    onRequestHelp: () -> Unit,
    onCall: () -> Unit
) {
    val provider = item.provider
    val distanceText = String.format(Locale.getDefault(), "%.1f", item.distanceKm)
    val updatedText = when {
        item.minutesAgo < 1 -> "just now"
        item.minutesAgo < 60 -> "${item.minutesAgo} min ago"
        else -> "${item.minutesAgo / 60} h ago"
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = if (isSelected) {
            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        } else {
            CardDefaults.cardColors()
        }
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier.size(48.dp).clip(CircleShape)
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
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(provider.name, style = MaterialTheme.typography.titleMedium)
                    Text(
                        formatRating(provider.averageRating, provider.ratingCount),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                if (provider.isVerified) {
                    AssistChip(onClick = {}, label = { Text("Verified") })
                }
            }

            Spacer(modifier = Modifier.height(6.dp))
            Text(
                "Services: ${provider.serviceCategories.joinToString(", ")}",
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                "$distanceText km away • location updated $updatedText",
                style = MaterialTheme.typography.bodySmall
            )

            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onRequestHelp,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Send Emergency Request")
                }
                OutlinedButton(
                    onClick = onCall,
                    enabled = provider.phoneNumber.isNotBlank(),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        if (provider.phoneNumber.isBlank()) "Call after acceptance"
                        else "Call"
                    )
                }
            }
        }
    }
}
