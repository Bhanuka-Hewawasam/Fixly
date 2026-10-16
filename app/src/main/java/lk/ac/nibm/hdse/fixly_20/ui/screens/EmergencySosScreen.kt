package lk.ac.nibm.hdse.fixly_20.ui.screens

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.location.LocationManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.firebase.firestore.GeoPoint
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.rememberCameraPositionState
import lk.ac.nibm.hdse.fixly_20.model.EmergencyAlert
import lk.ac.nibm.hdse.fixly_20.model.EmergencyLocationPolicy
import lk.ac.nibm.hdse.fixly_20.model.EmergencyResponse
import lk.ac.nibm.hdse.fixly_20.ui.components.formatRating
import lk.ac.nibm.hdse.fixly_20.util.dialNumber
import lk.ac.nibm.hdse.fixly_20.util.openInMaps
import lk.ac.nibm.hdse.fixly_20.viewmodel.EmergencySosViewModel

private val sosCategories = listOf(
    "Vehicle Mechanic",
    "Electrician",
    "Plumber",
    "AC Technician",
    "Computer Technician",
    "Other",
    "Any Available Provider"
)

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("MissingPermission") // permission is checked in onSendClicked() before any location call
@Composable
fun EmergencySosScreen(
    onBack: () -> Unit,
    onBrowseProviders: () -> Unit,
    viewModel: EmergencySosViewModel = viewModel()
) {
    val context = LocalContext.current
    val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }

    val uiState by viewModel.uiState.collectAsState()
    var dismissedAlertId by remember { mutableStateOf<String?>(null) }
    val myAlert = uiState.myAlert?.takeIf { it.alertId != dismissedAlertId }

    var note by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("Any Available Provider") }
    var isLocating by remember { mutableStateOf(false) }
    var locationDisabled by remember { mutableStateOf(false) }
    var sendAfterLocation by remember { mutableStateOf(false) }

    fun fetchCurrentLocation() {
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val isLocationEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
            locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        if (!EmergencyLocationPolicy.canReadCurrentLocation(hasPermission, isLocationEnabled)) {
            locationDisabled = !isLocationEnabled
            viewModel.showError(
                if (!hasPermission) "Location permission is needed to send an emergency alert."
                else "Turn on Location services to share your current position."
            )
            return
        }

        locationDisabled = false
        isLocating = true
        fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
            .addOnSuccessListener { location ->
                isLocating = false
                if (location != null) {
                    val point = GeoPoint(location.latitude, location.longitude)
                    viewModel.setCustomerLocation(point)
                    if (sendAfterLocation) {
                        val serviceCategory = when (category) {
                            "Any Available Provider" -> "Any"
                            "Electrician" -> "Electrical"
                            "Plumber" -> "Plumbing"
                            "AC Technician" -> "AC Repair"
                            else -> category
                        }
                        viewModel.sendAlert(point, note, serviceCategory)
                    }
                    sendAfterLocation = false
                } else {
                    viewModel.showError("Couldn't get a current location fix. Check GPS and try again.")
                }
            }
            .addOnFailureListener {
                isLocating = false
                viewModel.showError("Location could not be read. Check your connection and GPS, then retry.")
            }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) fetchCurrentLocation()
        else viewModel.showError("Location permission is needed to send an emergency alert.")
    }

    fun requestCurrentLocation(sendAlertAfterFix: Boolean) {
        sendAfterLocation = sendAlertAfterFix
        val hasPermission = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (hasPermission) fetchCurrentLocation()
        else permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("🚨 Emergency") },
                navigationIcon = { TextButton(onClick = onBack) { Text("Back") } }
            )
        }
    ) { padding ->
        when {
            uiState.isLoading -> {
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }

            myAlert != null -> WaitingContent(
                alert = myAlert,
                responses = uiState.responses,
                paddingValues = padding,
                onCall = { phone -> context.dialNumber(phone) },
                onOpenMaps = { lat, lon, label -> context.openInMaps(lat, lon, label) },
                onClose = viewModel::closeAlert,
                onBrowseProviders = onBrowseProviders,
                onNewRequest = { dismissedAlertId = myAlert.alertId },
                errorMessage = uiState.errorMessage
            )

            else -> SendAlertContent(
                paddingValues = padding,
                isBusy = isLocating || uiState.isSending,
                note = note,
                onNoteChange = { note = it.take(500) },
                category = category,
                onCategoryChange = { category = it },
                errorMessage = uiState.errorMessage,
                customerLocation = uiState.customerLocation,
                locationDisabled = locationDisabled,
                isLocating = isLocating,
                onFindLocation = { requestCurrentLocation(false) },
                onOpenLocationSettings = {
                    context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                },
                onSend = { requestCurrentLocation(true) },
                onBrowseProviders = onBrowseProviders
            )
        }
    }
}

@Composable
private fun SendAlertContent(
    paddingValues: PaddingValues,
    isBusy: Boolean,
    note: String,
    onNoteChange: (String) -> Unit,
    category: String,
    onCategoryChange: (String) -> Unit,
    errorMessage: String?,
    customerLocation: GeoPoint?,
    locationDisabled: Boolean,
    isLocating: Boolean,
    onFindLocation: () -> Unit,
    onOpenLocationSettings: () -> Unit,
    onSend: () -> Unit,
    onBrowseProviders: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    "Urgent help, when you need it",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
                Text(
                    "We'll share your location and notify nearby available service providers.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (customerLocation == null) "Location will be checked when you send"
                        else "Current location ready",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
        }

        Text("Choose the help you need", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            sosCategories.chunked(2).forEach { rowCategories ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    rowCategories.forEach { option ->
                        val selected = category == option
                        Surface(
                            onClick = { onCategoryChange(option) },
                            shape = RoundedCornerShape(16.dp),
                            color = if (selected) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surface,
                            border = BorderStroke(
                                if (selected) 2.dp else 1.dp,
                                if (selected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.outlineVariant
                            ),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = option,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                                else MaterialTheme.colorScheme.onSurface,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 14.dp)
                            )
                        }
                    }
                    if (rowCategories.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }

        OutlinedTextField(
            value = note,
            onValueChange = onNoteChange,
            label = { Text("What happened? (optional)") },
            placeholder = { Text("Add a short note for the provider") },
            minLines = 2,
            maxLines = 4,
            supportingText = { Text("${note.length}/500") },
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedButton(
            onClick = onFindLocation,
            enabled = !isLocating,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                when {
                    isLocating -> "Getting your location…"
                    customerLocation != null -> "Refresh current location"
                    else -> "Get my current location"
                }
            )
        }

        customerLocation?.let { point ->
            val position = LatLng(point.latitude, point.longitude)
            val cameraState = rememberCameraPositionState {
                this.position = CameraPosition.fromLatLngZoom(position, 15f)
            }
            LaunchedEffect(position) {
                cameraState.position = CameraPosition.fromLatLngZoom(position, 15f)
            }
            Text("Your current location", style = MaterialTheme.typography.titleMedium)
            GoogleMap(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(190.dp)
                    .clip(RoundedCornerShape(20.dp)),
                cameraPositionState = cameraState
            ) {
                Marker(
                    state = MarkerState(position = position),
                    title = "Emergency location",
                    contentDescription = "Your emergency location"
                )
            }
        }

        if (locationDisabled) {
            OutlinedButton(
                onClick = onOpenLocationSettings,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Turn on Location services")
            }
        }

        errorMessage?.let {
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        }

        Button(
            onClick = onSend,
            enabled = !isBusy,
            shape = RoundedCornerShape(18.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
            modifier = Modifier.fillMaxWidth().height(62.dp)
        ) {
            if (isBusy) {
                CircularProgressIndicator(
                    modifier = Modifier.size(22.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onError
                )
                Spacer(Modifier.width(12.dp))
                Text("Finding your location and sending…", fontWeight = FontWeight.Bold)
            } else {
                Text("Send Emergency Request", fontWeight = FontWeight.Bold)
            }
        }
        Text(
            "Your location is shared only to help providers reach you.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedButton(onClick = onBrowseProviders, modifier = Modifier.fillMaxWidth()) {
            Text("Browse nearby providers instead")
        }
    }
}

@Composable
private fun WaitingContent(
    alert: EmergencyAlert,
    responses: List<EmergencyResponse>,
    paddingValues: PaddingValues,
    onCall: (String) -> Unit,
    onOpenMaps: (Double, Double, String) -> Unit,
    onClose: () -> Unit,
    onBrowseProviders: () -> Unit,
    onNewRequest: () -> Unit,
    errorMessage: String?
) {
    val isAccepted = alert.status == "ACCEPTED"
    val isActive = alert.status == "ACTIVE"
    val terminalStatus = alert.status in listOf("CANCELLED", "EXPIRED", "CLOSED")
    var showCloseConfirmation by remember(alert.alertId) { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        if (terminalStatus) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        when (alert.status) {
                            "CANCELLED" -> "Request cancelled"
                            "EXPIRED" -> "Request expired"
                            else -> "Request closed"
                        },
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text("This request is no longer active. You can send a new request if you still need help.")
                }
            }
        } else if (isAccepted) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "✅ Provider Accepted Your Request!",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Name: ${alert.acceptedProviderName ?: "Provider"}",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        "Phone: ${alert.acceptedProviderPhone ?: "N/A"}",
                        style = MaterialTheme.typography.bodyMedium
                    )

                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (!alert.acceptedProviderPhone.isNullOrBlank()) {
                            Button(
                                onClick = { onCall(alert.acceptedProviderPhone) },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("📞 Call Provider")
                            }
                        }
                        if (alert.location != null) {
                            OutlinedButton(
                                onClick = {
                                    onOpenMaps(alert.location.latitude, alert.location.longitude, alert.acceptedProviderName ?: "Provider")
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("🗺️ Open in Maps")
                            }
                        }
                    }
                }
            }
        } else {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "Searching for nearby providers…",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Your emergency request is active. Available providers matching your service will be notified.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                    if (alert.note.isNotBlank()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            "Your note: ${alert.note}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            Text("Provider responses (${responses.size})", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))

            if (responses.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxWidth().height(150.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Waiting for a provider to accept...", textAlign = TextAlign.Center)
                    }
                }
            } else {
                responses.forEach { response ->
                    ResponseCard(response = response, onCall = { onCall(response.providerPhone) })
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            TextButton(
                onClick = onBrowseProviders,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Browse nearby providers & call manually (Fallback)")
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
        errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }

        when {
            terminalStatus -> Button(onClick = onNewRequest, modifier = Modifier.fillMaxWidth()) {
                Text("Send another emergency request")
            }
            isActive -> OutlinedButton(
                onClick = { showCloseConfirmation = true },
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.fillMaxWidth()
            ) { Text("Cancel emergency request") }
            isAccepted -> OutlinedButton(
                onClick = { showCloseConfirmation = true },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Close request") }
        }
    }

    if (showCloseConfirmation) {
        AlertDialog(
            onDismissRequest = { showCloseConfirmation = false },
            title = { Text(if (isAccepted) "Close this request?" else "Cancel this emergency request?") },
            text = {
                Text(
                    if (isAccepted) "This will mark the accepted request as closed."
                    else "Nearby providers will no longer be able to accept this request."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showCloseConfirmation = false
                        onClose()
                    }
                ) { Text(if (isAccepted) "Close request" else "Cancel request") }
            },
            dismissButton = {
                TextButton(onClick = { showCloseConfirmation = false }) { Text("Keep request") }
            }
        )
    }
}

@Composable
private fun ResponseCard(response: EmergencyResponse, onCall: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(response.providerName, style = MaterialTheme.typography.titleMedium)
            Text(
                formatRating(response.providerRating, response.providerReviewCount),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            response.distanceKm?.let { distance ->
                Text(
                    "📍 ${String.format(java.util.Locale.getDefault(), "%.1f", distance)} km away — on the way",
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Spacer(modifier = Modifier.height(10.dp))
            Button(
                onClick = onCall,
                enabled = response.providerPhone.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    if (response.providerPhone.isBlank()) "No phone number"
                    else "📞 Call ${response.providerName}"
                )
            }
        }
    }
}
