package lk.ac.nibm.hdse.fixly_20.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import lk.ac.nibm.hdse.fixly_20.model.JobPost
import lk.ac.nibm.hdse.fixly_20.ui.components.BottomNavItem
import lk.ac.nibm.hdse.fixly_20.ui.components.FixlyBottomNavBar
import lk.ac.nibm.hdse.fixly_20.ui.components.getCategoryVectorIcon
import lk.ac.nibm.hdse.fixly_20.viewmodel.MyBookingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyBookingsScreen(
    onBack: () -> Unit,
    onOpenJobBids: (jobId: String) -> Unit,
    onTrackProvider: (jobId: String) -> Unit,
    onHome: () -> Unit = {},
    onEmergency: () -> Unit = {},
    onProfile: (String) -> Unit = {},
    viewModel: MyBookingsViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val activeCount = uiState.jobs.count { it.status in listOf("Open", "Accepted", "In Progress", "Assigned") }
    var jobToCancel by remember { mutableStateOf<JobPost?>(null) }
    var cancelReason by remember { mutableStateOf("") }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.cancellationSuccessJobId) {
        if (uiState.cancellationSuccessJobId != null) {
            jobToCancel = null
            cancelReason = ""
            snackbarHostState.showSnackbar("Job cancelled successfully.")
            viewModel.clearCancellationState()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("My Bookings", fontWeight = FontWeight.Bold, fontSize = 20.sp) },
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
                },
                actions = {
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.padding(end = 16.dp)
                    ) {
                        Text(
                            text = "$activeCount Active",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                fontSize = 12.sp
                            ),
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }
            )
        },
        bottomBar = {
            FixlyBottomNavBar(
                currentRoute = BottomNavItem.History.route,
                activeRole = "customer",
                onNavigate = { route ->
                    when (route) {
                        "home" -> onHome()
                        "emergency_sos" -> onEmergency()
                        "profile" -> onProfile("customer")
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
                    modifier = Modifier.fillMaxSize().padding(padding).padding(28.dp),
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
                                    imageVector = Icons.Outlined.EventNote,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(34.dp)
                                )
                            }
                        }
                        Text(
                            text = "No bookings yet",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Your service requests and their progress will appear here. Start by finding the help you need.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        Button(
                            onClick = onHome,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.padding(top = 4.dp)
                        ) {
                            Text("Explore services")
                        }
                    }
                }
            }
            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentPadding = PaddingValues(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(uiState.jobs) { job ->
                        BookingCard(
                            job = job,
                            onViewBids = { onOpenJobBids(job.jobId) },
                            onTrack = { onTrackProvider(job.jobId) },
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
            title = { Text("Cancel job?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        if (job.status == "Accepted") {
                            "This will cancel the booking with ${job.assignedProviderName ?: "the provider"}."
                        } else {
                            "This will permanently cancel your open job request."
                        }
                    )
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
                    onClick = { viewModel.cancelJobAsCustomer(job.jobId, cancelReason) },
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
                ) { Text("Keep job") }
            }
        )
    }
}

@Composable
private fun BookingCard(
    job: JobPost,
    onViewBids: () -> Unit,
    onTrack: () -> Unit,
    onCancel: () -> Unit,
    isCancelling: Boolean
) {
    val context = LocalContext.current
    val categoryIcon = getCategoryVectorIcon(job.category)

    val isProgress = job.status in listOf("Accepted", "Assigned", "In Progress")
    val isOpen = job.status == "Open"

    val statusContainerColor = when {
        isOpen -> MaterialTheme.colorScheme.tertiaryContainer
        isProgress -> MaterialTheme.colorScheme.secondaryContainer
        else -> MaterialTheme.colorScheme.primaryContainer
    }
    val statusTextColor = when {
        isOpen -> MaterialTheme.colorScheme.onTertiaryContainer
        isProgress -> MaterialTheme.colorScheme.onSecondaryContainer
        else -> MaterialTheme.colorScheme.primary
    }
    val statusText = when {
        isOpen -> "Open for Bids"
        isProgress -> "In Progress"
        else -> job.status
    }

    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header Row: Icon + Category + Request ID + Status Pill
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = categoryIcon,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Column {
                        Text(
                            text = job.category,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Request ID: #${job.jobId.takeLast(6).uppercase()}",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 12.sp
                            )
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(50),
                    color = statusContainerColor
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(statusTextColor)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = statusText,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = statusTextColor,
                                fontSize = 11.5.sp
                            )
                        )
                    }
                }
            }

            // Description Quote Box
            if (job.description.isNotBlank()) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "\"${job.description}\"",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                            color = Color(0xFF475569),
                            fontSize = 13.5.sp
                        ),
                        modifier = Modifier.padding(14.dp)
                    )
                }
            }

            // Assigned Provider or Bids Info
            if (!job.isEmergency && job.assignedProviderId != null &&
                job.status in listOf("Accepted", "In Progress", "Completed")
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFF1F5F9)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Person,
                                contentDescription = null,
                                tint = Color(0xFF64748B),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "Assigned Provider",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = Color(0xFF64748B),
                                    fontSize = 11.5.sp
                                )
                            )
                            Text(
                                text = job.assignedProviderName ?: "Provider",
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = Color(0xFF1E293B)
                                )
                            )
                        }
                    }

                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = if ((job.acceptedProviderReviewCount ?: 0) > 0) {
                                "★ ${"%.1f".format(job.acceptedProviderRating ?: 0.0)}"
                            } else {
                                "No reviews yet"
                            },
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = Color(0xFF64748B),
                                fontSize = 12.sp
                            )
                        )
                        job.acceptedBidPrice?.let {
                            Text(
                                text = "Rs. ${"%.2f".format(it)}",
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF2563EB),
                                    fontSize = 13.5.sp
                                )
                            )
                        }
                    }
                }
            } else if (isOpen) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = Color(0xFFF8FAFC),
                    border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Handyman,
                                contentDescription = null,
                                tint = Color(0xFF2563EB),
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "Providers Bidding",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF1E293B),
                                    fontSize = 13.sp
                                )
                            )
                        }
                        Text(
                            text = "Active bids available",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = Color(0xFF2563EB),
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 12.5.sp
                            )
                        )
                    }
                }
            }

            job.providerCertificateUrl?.takeIf { it.isNotBlank() }?.let { certificateUrl ->
                OutlinedButton(
                    onClick = {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(certificateUrl)))
                    },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("View Provider Certificate")
                }
            }
            job.providerPhone?.takeIf { it.isNotBlank() }?.let { phone ->
                Button(
                    onClick = {
                        context.startActivity(
                            Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", phone, null))
                        )
                    },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Call Provider")
                }
            }

            // Action Buttons
            when (job.status) {
                "Open" -> {
                    Button(
                        onClick = onViewBids,
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                        modifier = Modifier.fillMaxWidth().height(48.dp)
                    ) {
                        Icon(Icons.Outlined.Visibility, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("View Bids", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                    }
                }
                "Accepted", "Assigned", "In Progress" -> {
                    Button(
                        onClick = onTrack,
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                        modifier = Modifier.fillMaxWidth().height(48.dp)
                    ) {
                        Icon(Icons.Outlined.Navigation, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Track Provider", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                    }
                }
                "Completed" -> {
                    OutlinedButton(
                        onClick = onTrack,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth().height(48.dp)
                    ) {
                        Text("View Work Evidence", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                    }
                }
            }
            if (!job.isEmergency && job.status in listOf("Open", "Accepted")) {
                OutlinedButton(
                    onClick = onCancel,
                    enabled = !isCancelling,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (isCancelling) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        Text("Cancel job")
                    }
                }
            }
        }
    }
}
