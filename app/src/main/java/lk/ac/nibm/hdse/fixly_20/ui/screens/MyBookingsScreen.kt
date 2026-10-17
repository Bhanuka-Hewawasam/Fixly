package lk.ac.nibm.hdse.fixly_20.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import lk.ac.nibm.hdse.fixly_20.model.JobPost
import lk.ac.nibm.hdse.fixly_20.viewmodel.MyBookingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyBookingsScreen(
    onBack: () -> Unit,
    onOpenJobBids: (jobId: String) -> Unit,
    onTrackProvider: (jobId: String) -> Unit,
    viewModel: MyBookingsViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("My Bookings") },
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
            uiState.jobs.isEmpty() -> {
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    Text("You haven't posted any jobs yet.")
                }
            }
            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(uiState.jobs) { job ->
                        BookingCard(
                            job = job,
                            onViewBids = { onOpenJobBids(job.jobId) },
                            onTrack = { onTrackProvider(job.jobId) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BookingCard(
    job: JobPost,
    onViewBids: () -> Unit,
    onTrack: () -> Unit
) {
    val context = LocalContext.current

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(job.category, style = MaterialTheme.typography.titleMedium)
                AssistChip(onClick = {}, label = { Text(job.status) })
            }
            if (job.isEmergency) {
                Text(
                    "Emergency request",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.labelMedium
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(job.description, style = MaterialTheme.typography.bodyMedium, maxLines = 2)
            Spacer(modifier = Modifier.height(12.dp))

            if (!job.isEmergency && job.assignedProviderId != null &&
                job.status in listOf("Accepted", "In Progress", "Completed")
            ) {
                Text(
                    job.assignedProviderName ?: "Provider",
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    if ((job.acceptedProviderReviewCount ?: 0) > 0) {
                        "Rating: ${"%.1f".format(job.acceptedProviderRating ?: 0.0)} " +
                            "(${job.acceptedProviderReviewCount} reviews)"
                    } else {
                        "No reviews yet"
                    },
                    style = MaterialTheme.typography.bodyMedium
                )
                job.acceptedBidPrice?.let {
                    Text("Accepted price: Rs. ${"%.2f".format(it)}", style = MaterialTheme.typography.bodyMedium)
                }
                job.providerCertificateUrl?.takeIf { it.isNotBlank() }?.let { certificateUrl ->
                    OutlinedButton(
                        onClick = {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(certificateUrl)))
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("View Provider Certificate")
                    }
                }
                if (!job.providerPhone.isNullOrBlank()) {
                    Button(
                        onClick = {
                            context.startActivity(
                                Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", job.providerPhone, null))
                            )
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Call Provider")
                    }
                } else {
                    Text(
                        "Provider hasn't added a phone number",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }

            when (job.status) {
                "Open" -> {
                    Button(onClick = onViewBids, modifier = Modifier.fillMaxWidth()) {
                        Text("View Bids")
                    }
                }
                "Accepted", "Assigned", "In Progress" -> {
                    Button(onClick = onTrack, modifier = Modifier.fillMaxWidth()) {
                        Text("Track Provider")
                    }
                }
                "Completed" -> {
                    // Opens the same screen, which shows the before/after photos for completed jobs
                    OutlinedButton(onClick = onTrack, modifier = Modifier.fillMaxWidth()) {
                        Text("View Work Evidence")
                    }
                }
            }
        }
    }
}
