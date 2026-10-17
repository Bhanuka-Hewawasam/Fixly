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

    Scaffold(
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
                        AcceptedJobCard(job = job, onClick = { onOpenJob(job.jobId) })
                    }
                }
            }
        }
    }
}

@Composable
private fun AcceptedJobCard(job: JobPost, onClick: () -> Unit) {
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
        }
    }
}
