package lk.ac.nibm.hdse.fixly_20.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import lk.ac.nibm.hdse.fixly_20.model.JobPost
import lk.ac.nibm.hdse.fixly_20.viewmodel.AcceptedJobsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AcceptedJobsScreen(
    onBack: () -> Unit,
    onOpenJob: (jobId: String) -> Unit,
    viewModel: AcceptedJobsViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var jobToCancel by remember { mutableStateOf<JobPost?>(null) }
    var cancelReason by remember { mutableStateOf("") }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.cancellationSuccessJobId) {
        if (uiState.cancellationSuccessJobId != null) {
            jobToCancel = null
            cancelReason = ""
            snackbarHostState.showSnackbar("Booking cancelled. The job is open for bids again.")
            viewModel.clearCancellationState()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("My Accepted Jobs") },
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
                    Text("Jobs you accept or win through bids will show up here.")
                }
            }
            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(uiState.jobs) { job ->
                        AcceptedJobCard(
                            job = job,
                            onClick = { onOpenJob(job.jobId) },
                            onCancel = {
                                viewModel.clearCancellationState()
                                cancelReason = ""
                                jobToCancel = job
                            },
                            isCancelling = uiState.cancellingJobId == job.jobId
                        )
                    }
                }
            }
        }
    }

    jobToCancel?.let { job ->
        AlertDialog(
            onDismissRequest = {
                if (uiState.cancellingJobId == null) {
                    jobToCancel = null
                    viewModel.clearCancellationState()
                }
            },
            title = { Text("Cancel booking?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("This booking will be reopened for bids. Your bid will be withdrawn and you cannot bid on this job again.")
                    OutlinedTextField(
                        value = cancelReason,
                        onValueChange = { cancelReason = it },
                        label = { Text("Reason (optional)") },
                        enabled = uiState.cancellingJobId == null,
                        modifier = Modifier.fillMaxWidth()
                    )
                    uiState.cancellationError?.let {
                        Text(it, color = MaterialTheme.colorScheme.error)
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.cancelJobAsProvider(job.jobId, cancelReason) },
                    enabled = uiState.cancellingJobId == null
                ) {
                    if (uiState.cancellingJobId == job.jobId) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        Text("Confirm cancellation")
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        jobToCancel = null
                        viewModel.clearCancellationState()
                    },
                    enabled = uiState.cancellingJobId == null
                ) { Text("Keep booking") }
            }
        )
    }
}

@Composable
private fun AcceptedJobCard(
    job: JobPost,
    onClick: () -> Unit,
    onCancel: () -> Unit,
    isCancelling: Boolean
) {
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
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
            Spacer(modifier = Modifier.height(6.dp))
            Text("📍 ${job.address}", style = MaterialTheme.typography.bodySmall)
            Text("Customer: ${job.customerName}", style = MaterialTheme.typography.bodySmall)
            if (!job.isEmergency && job.status == "Accepted") {
                OutlinedButton(
                    onClick = onCancel,
                    enabled = !isCancelling,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (isCancelling) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        Text("Cancel booking")
                    }
                }
            }
        }
    }
}
