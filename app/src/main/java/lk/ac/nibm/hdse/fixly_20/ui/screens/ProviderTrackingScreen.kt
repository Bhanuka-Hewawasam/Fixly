package lk.ac.nibm.hdse.fixly_20.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.rememberCameraPositionState
import lk.ac.nibm.hdse.fixly_20.viewmodel.ProviderTrackingViewModel
import lk.ac.nibm.hdse.fixly_20.viewmodel.ProviderTrackingViewModelFactory

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProviderTrackingScreen(
    jobId: String,
    onBack: () -> Unit,
    onJobCompleted: () -> Unit
) {
    val context = LocalContext.current
    val viewModel: ProviderTrackingViewModel = viewModel(factory = ProviderTrackingViewModelFactory(jobId))
    val uiState by viewModel.uiState.collectAsState()
    val job = uiState.job

    var hasLocationPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasLocationPermission = isGranted
        if (isGranted) {
            viewModel.startSharingLocation(context)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Job Tracking & Navigation") },
                navigationIcon = { TextButton(onClick = onBack) { Text("Back") } }
            )
        }
    ) { padding ->
        if (job == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        val jobLatLng = job.location?.let { LatLng(it.latitude, it.longitude) } ?: LatLng(6.9271, 79.8612) // Default to Colombo

        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            // Status and Controls Header
            Card(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(job.category, style = MaterialTheme.typography.titleMedium)
                        AssistChip(onClick = {}, label = { Text(job.status) })
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text("📍 ${job.address}", style = MaterialTheme.typography.bodyMedium)
                    Text("Customer: ${job.customerName}", style = MaterialTheme.typography.bodySmall)

                    Spacer(modifier = Modifier.height(12.dp))

                    if (job.status == "Assigned") {
                        Button(
                            onClick = {
                                if (hasLocationPermission) {
                                    viewModel.startSharingLocation(context)
                                    viewModel.markInProgress()
                                } else {
                                    permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(if (uiState.isSharingLocation) "Sharing Live Location..." else "Start Job & Share Location")
                        }
                    } else if (job.status == "In Progress") {
                        Button(
                            onClick = {
                                viewModel.markCompleted()
                                onJobCompleted()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Mark Job as Completed")
                        }
                    } else if (job.status == "Completed") {
                        Text("✓ Job finished successfully", color = MaterialTheme.colorScheme.primary)
                    }
                }
            }

            // Map View
            val cameraPositionState = rememberCameraPositionState {
                position = CameraPosition.fromLatLngZoom(jobLatLng, 15f)
            }

            GoogleMap(
                modifier = Modifier.fillMaxSize(),
                cameraPositionState = cameraPositionState
            ) {
                Marker(
                    state = MarkerState(position = jobLatLng),
                    title = "Customer Location"
                )
            }
        }
    }
}
