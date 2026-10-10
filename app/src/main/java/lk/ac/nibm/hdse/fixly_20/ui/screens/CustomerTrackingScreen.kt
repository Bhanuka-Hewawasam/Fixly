package lk.ac.nibm.hdse.fixly_20.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.rememberCameraPositionState
import lk.ac.nibm.hdse.fixly_20.model.JobPost
import lk.ac.nibm.hdse.fixly_20.ui.components.EvidencePhotoCard
import lk.ac.nibm.hdse.fixly_20.viewmodel.CustomerTrackingViewModel
import lk.ac.nibm.hdse.fixly_20.viewmodel.CustomerTrackingViewModelFactory

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomerTrackingScreen(
    jobId: String,
    onBack: () -> Unit
) {
    val viewModel: CustomerTrackingViewModel = viewModel(factory = CustomerTrackingViewModelFactory(jobId))
    val uiState by viewModel.uiState.collectAsState()
    val job = uiState.job

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (job?.status == "Completed") "Work Summary" else "Track Provider") },
                navigationIcon = { TextButton(onClick = onBack) { Text("Back") } }
            )
        }
    ) { padding ->
        when {
            job == null -> {
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            // Job is done: the map no longer matters, the proof of work does
            job.status == "Completed" -> CompletedSummary(job = job, paddingValues = padding)
            else -> LiveTrackingContent(job = job, paddingValues = padding)
        }
    }
}

@Composable
private fun CompletedSummary(job: JobPost, paddingValues: PaddingValues) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text(job.category, style = MaterialTheme.typography.headlineSmall)
        AssistChip(onClick = {}, label = { Text("✓ Completed") })

        Spacer(modifier = Modifier.height(8.dp))
        Text(job.description, style = MaterialTheme.typography.bodyMedium)

        Spacer(modifier = Modifier.height(20.dp))
        Text("Work Evidence", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(8.dp))

        EvidencePhotoCard(title = "Before", photoUrl = job.beforePhotoUrl)
        Spacer(modifier = Modifier.height(12.dp))
        EvidencePhotoCard(title = "After", photoUrl = job.afterPhotoUrl)

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun LiveTrackingContent(job: JobPost, paddingValues: PaddingValues) {
    val providerLatLng = job.providerLocation?.let { LatLng(it.latitude, it.longitude) }

    Column(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(job.category, style = MaterialTheme.typography.titleMedium)
            AssistChip(onClick = {}, label = { Text(job.status) })
        }

        if (providerLatLng == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Waiting for the provider to start sharing their location...")
            }
        } else {
            val cameraPositionState = rememberCameraPositionState {
                position = CameraPosition.fromLatLngZoom(providerLatLng, 14f)
            }

            // Re-center the camera whenever the provider's location updates
            LaunchedEffect(providerLatLng) {
                cameraPositionState.position = CameraPosition.fromLatLngZoom(providerLatLng, 15f)
            }

            GoogleMap(
                modifier = Modifier.fillMaxSize(),
                cameraPositionState = cameraPositionState
            ) {
                Marker(
                    state = MarkerState(position = providerLatLng),
                    title = "Provider's current location"
                )
            }
        }
    }
}
