package lk.ac.nibm.hdse.fixly_20.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import lk.ac.nibm.hdse.fixly_20.model.JobPost
import lk.ac.nibm.hdse.fixly_20.viewmodel.AvailableJobsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AvailableJobsScreen(
    onBack: () -> Unit,
    viewModel: AvailableJobsViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var selectedJob by remember { mutableStateOf<JobPost?>(null) }

    // Show a quick confirmation and close the dialog once a bid is submitted
    LaunchedEffect(uiState.bidSubmittedJobId) {
        if (uiState.bidSubmittedJobId != null) {
            selectedJob = null
            viewModel.clearBidSubmitted()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Available Jobs") },
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
                    Text("No open jobs right now. Check back soon!")
                }
            }

            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(uiState.jobs) { job ->
                        JobCard(job = job, onPlaceBid = { selectedJob = job })
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
private fun JobCard(job: JobPost, onPlaceBid: () -> Unit) {
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
            Text("📍 ${job.address}", style = MaterialTheme.typography.bodySmall)
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
