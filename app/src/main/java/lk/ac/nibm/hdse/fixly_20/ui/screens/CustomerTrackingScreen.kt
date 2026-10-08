package lk.ac.nibm.hdse.fixly_20.ui.screens

import androidx.compose.foundation.layout.*
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
                title = { Text("Track Provider") },
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

        val providerLatLng = job.providerLocation?.let { LatLng(it.latitude, it.longitude) }

        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
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
                    position = CameraPosition.fromLatLngZoom(providerLatLng, 15f)
                }

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
}
