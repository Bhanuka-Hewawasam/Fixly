package lk.ac.nibm.hdse.fixly_20.ui.screens

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.firebase.firestore.GeoPoint
import lk.ac.nibm.hdse.fixly_20.model.JobPost
import lk.ac.nibm.hdse.fixly_20.viewmodel.AvailableJobsViewModel

private val radiusOptionsKm = listOf(10, 25, 50, 100)

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("MissingPermission") // permission is checked in requestLocation() before any location call
@Composable
fun AvailableJobsScreen(
    onBack: () -> Unit,
    viewModel: AvailableJobsViewModel = viewModel()
) {
    val context = LocalContext.current
    val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }

    val uiState by viewModel.uiState.collectAsState()
    var selectedJob by remember { mutableStateOf<JobPost?>(null) }
    var isLocating by remember { mutableStateOf(false) }
    var locationFailed by remember { mutableStateOf(false) }

    // Gets a fresh GPS fix for the provider; the ViewModel then filters jobs around it
    fun fetchLocation() {
        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        if (!hasPermission) {
            isLocating = false
            locationFailed = true
            return
        }
        isLocating = true
        locationFailed = false
        fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
            .addOnSuccessListener { location ->
                isLocating = false
                if (location != null) {
                    viewModel.setProviderLocation(GeoPoint(location.latitude, location.longitude))
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

    // Ask for location as soon as the screen opens
    LaunchedEffect(Unit) { requestLocation() }

    LaunchedEffect(uiState.bidSubmittedJobId) {
        if (uiState.bidSubmittedJobId != null) {
            selectedJob = null
            viewModel.clearBidSubmitted()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Jobs Near You") },
                navigationIcon = { TextButton(onClick = onBack) { Text("Back") } }
            )
        }
    ) { padding ->
        when {
            // --- Step A: we don't know where the provider is yet ---
            uiState.providerLocation == null -> {
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
                                    "We need your location to show jobs near you.",
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Button(onClick = { requestLocation() }) { Text("Enable Location") }
                        }
                    }
                }
            }

            // --- Step B: location known, jobs still loading ---
            uiState.isLoading -> {
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }

            // --- Step C: radius chips + nearby jobs ---
            else -> {
                Column(modifier = Modifier.fillMaxSize().padding(padding)) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Within:", style = MaterialTheme.typography.bodySmall)
                        radiusOptionsKm.forEach { km ->
                            FilterChip(
                                selected = uiState.radiusKm == km,
                                onClick = { viewModel.setRadius(km) },
                                label = { Text("$km km") }
                            )
                        }
                    }

                    if (uiState.visibleJobs.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize().padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "No jobs within ${uiState.radiusKm} km right now. Try a bigger radius.",
                                textAlign = TextAlign.Center
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(uiState.visibleJobs) { item ->
                                JobCard(
                                    job = item.job,
                                    distanceKm = item.distanceKm,
                                    onPlaceBid = { selectedJob = item.job }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    selectedJob?.let { job ->
        BidDialog(
            job = job,
            isSubmitting = uiState.isSubmittingBid,
            errorMessage = uiState.errorMessage,
            onDismiss = { selectedJob = null },
            onSubmit = { price, message -> viewModel.submitBid(job.jobId, price, message) }
        )
    }

}

@Composable
private fun JobCard(job: JobPost, distanceKm: Double, onPlaceBid: () -> Unit) {
    val distanceText = String.format(java.util.Locale.getDefault(), "%.1f", distanceKm)

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(job.category, style = MaterialTheme.typography.titleMedium)
                if (job.isEmergency) {
                    AssistChip(onClick = {}, label = { Text("URGENT") })
                }
            }

            Spacer(modifier = Modifier.height(6.dp))
            Text(job.description, style = MaterialTheme.typography.bodyMedium)

            Spacer(modifier = Modifier.height(8.dp))
            Text("📍 ${job.address} • $distanceText km away", style = MaterialTheme.typography.bodySmall)
            Text("🗓 ${job.preferredDate} ${job.preferredTime}", style = MaterialTheme.typography.bodySmall)
            Text("Posted by ${job.customerName}", style = MaterialTheme.typography.bodySmall)

            Spacer(modifier = Modifier.height(12.dp))
            Button(onClick = onPlaceBid, modifier = Modifier.fillMaxWidth()) {
                Text("Place Bid")
            }
        }
    }
}

@Composable
private fun BidDialog(
    job: JobPost,
    isSubmitting: Boolean,
    errorMessage: String?,
    onDismiss: () -> Unit,
    onSubmit: (price: Double, message: String) -> Unit
) {
    var price by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Bid on: ${job.category}") },
        text = {
            Column {
                OutlinedTextField(
                    value = price,
                    onValueChange = { price = it },
                    label = { Text("Your Price (Rs.)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = message,
                    onValueChange = { message = it },
                    label = { Text("Message (optional)") },
                    modifier = Modifier.fillMaxWidth()
                )
                errorMessage?.let {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val priceValue = price.toDoubleOrNull()
                    if (priceValue != null) onSubmit(priceValue, message)
                },
                enabled = !isSubmitting && price.toDoubleOrNull() != null
            ) {
                if (isSubmitting) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                } else {
                    Text("Submit Bid")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
