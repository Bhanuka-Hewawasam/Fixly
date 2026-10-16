package lk.ac.nibm.hdse.fixly_20.ui.screens

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.firebase.firestore.GeoPoint
import lk.ac.nibm.hdse.fixly_20.model.EmergencyAlert
import lk.ac.nibm.hdse.fixly_20.model.EmergencyResponse
import lk.ac.nibm.hdse.fixly_20.ui.components.formatRating
import lk.ac.nibm.hdse.fixly_20.util.dialNumber
import lk.ac.nibm.hdse.fixly_20.util.openInMaps
import lk.ac.nibm.hdse.fixly_20.viewmodel.EmergencySosViewModel

private val sosCategories = listOf("Any", "Plumbing", "Electrical", "Cleaning", "Other")

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
    val myAlert = uiState.myAlert

    var note by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("Any") }
    var isLocating by remember { mutableStateOf(false) }

    // Step 1 of the one tap: where are we right now?
    fun sendWithCurrentLocation() {
        isLocating = true
        fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
            .addOnSuccessListener { location ->
                isLocating = false
                if (location != null) {
                    viewModel.sendAlert(GeoPoint(location.latitude, location.longitude), note, category)
                } else {
                    viewModel.showError("Couldn't get your location. Turn on GPS and try again.")
                }
            }
            .addOnFailureListener {
                isLocating = false
                viewModel.showError("Couldn't get your location. Turn on GPS and try again.")
            }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) sendWithCurrentLocation()
        else viewModel.showError("Location permission is needed to send an emergency alert.")
    }

    fun onSendClicked() {
        val hasPermission = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (hasPermission) sendWithCurrentLocation()
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
                onBrowseProviders = onBrowseProviders
            )

            else -> SendAlertContent(
                paddingValues = padding,
                isBusy = isLocating || uiState.isSending,
                note = note,
                onNoteChange = { note = it },
                category = category,
                onCategoryChange = { category = it },
                errorMessage = uiState.errorMessage,
                uiState = uiState,
                viewModel = viewModel,
                onSend = { onSendClicked() },
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
    uiState: lk.ac.nibm.hdse.fixly_20.viewmodel.EmergencySosUiState,
    viewModel: EmergencySosViewModel,
    onSend: () -> Unit,
    onBrowseProviders: () -> Unit
) {
    val context = LocalContext.current
    val isDebug = (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            "Stuck on the road or need urgent help?",
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "Tap once. We use your location and notify available providers within 25 km — they can respond straight away.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(32.dp))

        // The one big button
        Button(
            onClick = onSend,
            enabled = !isBusy,
            shape = CircleShape,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.error,
                contentColor = MaterialTheme.colorScheme.onError
            ),
            modifier = Modifier.size(200.dp)
        ) {
            if (isBusy) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(32.dp),
                        strokeWidth = 3.dp,
                        color = MaterialTheme.colorScheme.onError
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Sending...", style = MaterialTheme.typography.titleMedium)
                }
            } else {
                Text(
                    "🚨\nSEND\nALERT",
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Center
                )
            }
        }

        errorMessage?.let {
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                it,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Optional extras — the alert works fine without them
        OutlinedTextField(
            value = note,
            onValueChange = onNoteChange,
            label = { Text("What happened? (optional)") },
            placeholder = { Text("e.g. flat tyre, engine won't start") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(12.dp))
        Text(
            "Who do you need? (optional)",
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.fillMaxWidth()
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            sosCategories.forEach { option ->
                FilterChip(
                    selected = category == option,
                    onClick = { onCategoryChange(option) },
                    label = { Text(option) }
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        TextButton(onClick = onBrowseProviders) {
            Text("Rather browse nearby providers yourself?")
        }

        // --- DEBUG ONLY ACTIONS FOR TESTING ---
        if (isDebug) {
            Spacer(modifier = Modifier.height(24.dp))
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("🛠️ Debug Actions (DEV ONLY)", style = MaterialTheme.typography.titleSmall)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = viewModel::seedDemoProviders,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Seed Demo Providers", style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
                        }
                        OutlinedButton(
                            onClick = viewModel::deleteDemoProviders,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Delete Demo Providers", style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
                        }
                    }
                    uiState.debugMessage?.let { msg ->
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(msg, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSecondaryContainer)
                    }
                }
            }
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
    onBrowseProviders: () -> Unit
) {
    val isAccepted = alert.status == "ACCEPTED"

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        if (isAccepted) {
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
                        "🚨 Searching for nearby providers within 25 km…",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "We are notifying available providers within 25 km of your location.",
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
            Text(
                "Providers responding (${responses.size})",
                style = MaterialTheme.typography.titleMedium
            )
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
        Button(
            onClick = onClose,
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (isAccepted) "Close Request" else "Cancel Emergency Request")
        }
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
