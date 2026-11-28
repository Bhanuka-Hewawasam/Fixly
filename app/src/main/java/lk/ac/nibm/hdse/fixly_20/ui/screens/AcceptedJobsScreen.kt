package lk.ac.nibm.hdse.fixly_20.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.WorkOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                ),
                title = { Text("My Accepted Jobs", fontWeight = FontWeight.Bold, fontSize = 20.sp) },
                navigationIcon = {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.padding(start = 12.dp).size(40.dp)
                    ) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", modifier = Modifier.size(20.dp))
                        }
                    }
                }
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
                Box(
                    Modifier.fillMaxSize().padding(padding).padding(28.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(76.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Outlined.WorkOutline,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(34.dp)
                                )
                            }
                        }
                        Text(
                            "No accepted jobs yet",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            "Jobs you accept or win through bids will appear here, ready for you to manage.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            }
            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    "Your active work",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    "Keep track of the jobs you've accepted",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Surface(
                                shape = RoundedCornerShape(50),
                                color = MaterialTheme.colorScheme.primaryContainer
                            ) {
                                Text(
                                    "${uiState.jobs.size} ACTIVE",
                                    modifier = Modifier.padding(horizontal = 11.dp, vertical = 7.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                    }
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
            shape = RoundedCornerShape(24.dp),
            title = { Text("Cancel booking?", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("This booking will be reopened for bids. Your bid will be withdrawn and you cannot bid on this job again.")
                    OutlinedTextField(
                        value = cancelReason,
                        onValueChange = { cancelReason = it },
                        label = { Text("Reason (optional)") },
                        shape = RoundedCornerShape(14.dp),
                        enabled = uiState.cancellingJobId == null,
                        modifier = Modifier.fillMaxWidth()
                    )
                    uiState.cancellationError?.let {
                        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.cancelJobAsProvider(job.jobId, cancelReason) },
                    shape = RoundedCornerShape(14.dp),
                    enabled = uiState.cancellingJobId == null,
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                ) {
                    if (uiState.cancellingJobId == job.jobId) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = Color.White)
                    } else {
                        Text("Confirm cancellation", fontWeight = FontWeight.Bold)
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        jobToCancel = null
                        viewModel.clearCancellationState()
                    },
                    enabled = uiState.cancellingJobId == null,
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Keep booking", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary) }
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
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = job.category,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                )

                Surface(
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.tertiaryContainer
                ) {
                    Text(
                        text = job.status,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.tertiary,
                            fontSize = 11.5.sp
                        ),
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                    )
                }
            }

            if (job.description.isNotBlank()) {
                Text(
                    text = job.description,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 14.sp
                    ),
                    maxLines = 2
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                if (job.address.isNotBlank()) {
                    Text(
                        text = "📍 ${job.address}",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.5.sp
                        ),
                        maxLines = 1
                    )
                }
                Text(
                    text = "Customer: ${job.customerName.ifBlank { "Customer" }}",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.5.sp
                    )
                )
            }

            if (!job.isEmergency && job.status == "Accepted") {
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedButton(
                    onClick = onCancel,
                    enabled = !isCancelling,
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        contentColor = MaterialTheme.colorScheme.primary
                    ),
                    modifier = Modifier.fillMaxWidth().height(46.dp)
                ) {
                    if (isCancelling) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        Text(
                            text = "Cancel booking",
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.5.sp
                            )
                        )
                    }
                }
            }
        }
    }
}
