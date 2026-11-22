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
import lk.ac.nibm.hdse.fixly_20.ui.components.StarRatingBar
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

            // Provider says it's done: customer reviews the evidence, then confirms or sends it back
            job.status == "Completed" -> CompletedContent(
                job = job,
                isSubmitting = uiState.isSubmitting,
                errorMessage = uiState.errorMessage,
                paddingValues = padding,
                onConfirm = viewModel::confirmWithReview,
                onSendBack = viewModel::sendBackToProvider
            )

            else -> LiveTrackingContent(job = job, paddingValues = padding)
        }
    }
}

@Composable
private fun CompletedContent(
    job: JobPost,
    isSubmitting: Boolean,
    errorMessage: String?,
    paddingValues: PaddingValues,
    onConfirm: (rating: Int, comment: String) -> Unit,
    onSendBack: () -> Unit
) {
    var rating by remember { mutableIntStateOf(0) }
    var comment by remember { mutableStateOf("") }
    var showSendBackDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text(job.category, style = MaterialTheme.typography.headlineSmall)
        AssistChip(
            onClick = {},
            label = { Text(if (job.customerConfirmed) "✓ Confirmed" else "Awaiting your confirmation") }
        )

        Spacer(modifier = Modifier.height(8.dp))
        Text(job.description, style = MaterialTheme.typography.bodyMedium)

        Spacer(modifier = Modifier.height(20.dp))
        Text("Work Evidence", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(8.dp))

        EvidencePhotoCard(title = "Before", photoUrl = job.beforePhotoUrl)
        Spacer(modifier = Modifier.height(12.dp))
        EvidencePhotoCard(title = "After", photoUrl = job.afterPhotoUrl)

        Spacer(modifier = Modifier.height(24.dp))

        if (job.customerConfirmed) {
            // ---------- Already reviewed: read-only ----------
            Text("${job.category} Review", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(4.dp))
            StarRatingBar(rating = job.rating ?: 0)

            if (!job.reviewComment.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(job.reviewComment, style = MaterialTheme.typography.bodyMedium)
            }
        } else {
            // ---------- Confirm + review form ----------
            Text(
                "Rate your ${job.category} experience with ${job.assignedProviderName ?: "your provider"}",
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                "Confirm the completed work and share an optional comment.",
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(modifier = Modifier.height(8.dp))

            StarRatingBar(rating = rating, onRatingChange = { rating = it })

            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = comment,
                onValueChange = { if (it.length <= 1000) comment = it },
                label = { Text("Write a review (optional)") },
                minLines = 3,
                modifier = Modifier.fillMaxWidth()
            )

            errorMessage?.let { message ->
                Spacer(modifier = Modifier.height(8.dp))
                Text(message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }

            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = { onConfirm(rating, comment) },
                enabled = !isSubmitting && rating > 0,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (isSubmitting) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    Text("Confirm & Submit Review")
                }
            }

            TextButton(
                onClick = { showSendBackDialog = true },
                enabled = !isSubmitting,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Not satisfied? Send back to provider")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }

    if (showSendBackDialog) {
        AlertDialog(
            onDismissRequest = { showSendBackDialog = false },
            title = { Text("Send back to provider?") },
            text = {
                Text("The provider will be asked to fix the work and upload a new 'after' photo.")
            },
            confirmButton = {
                Button(onClick = {
                    showSendBackDialog = false
                    onSendBack()
                }) {
                    Text("Send Back")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSendBackDialog = false }) { Text("Cancel") }
            }
        )
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
