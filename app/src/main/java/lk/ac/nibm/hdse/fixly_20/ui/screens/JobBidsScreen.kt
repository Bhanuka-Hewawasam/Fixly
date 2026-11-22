package lk.ac.nibm.hdse.fixly_20.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import lk.ac.nibm.hdse.fixly_20.model.Bid
import lk.ac.nibm.hdse.fixly_20.ui.components.formatRating
import lk.ac.nibm.hdse.fixly_20.viewmodel.BidSortOption
import lk.ac.nibm.hdse.fixly_20.viewmodel.JobBidsViewModel
import lk.ac.nibm.hdse.fixly_20.viewmodel.JobBidsViewModelFactory

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JobBidsScreen(
    jobId: String,
    onBack: () -> Unit,
    onBidSelected: () -> Unit
) {
    val viewModel: JobBidsViewModel = viewModel(factory = JobBidsViewModelFactory(jobId))
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(uiState.selectionDone) {
        if (uiState.selectionDone) onBidSelected()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Bids Received", fontWeight = FontWeight.Bold, fontSize = 20.sp) },
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
            uiState.isLoading || !uiState.hasLoadedJob -> {
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }

            uiState.job?.status == "Cancelled" -> {
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    Text("This job was cancelled. Its bids can no longer be accepted.")
                }
            }

            uiState.job?.status != "Open" -> {
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    Text("This job is no longer open for bid selection.")
                }
            }

            uiState.bids.isEmpty() -> {
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    Text("No bids yet. Check back soon!")
                }
            }

            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentPadding = PaddingValues(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilterChip(
                                selected = uiState.sortOption == BidSortOption.RECOMMENDED,
                                onClick = { viewModel.setSortOption(BidSortOption.RECOMMENDED) },
                                shape = RoundedCornerShape(12.dp),
                                label = { Text("Recommended", fontWeight = FontWeight.Bold) }
                            )
                            FilterChip(
                                selected = uiState.sortOption == BidSortOption.LOWEST_PRICE,
                                onClick = { viewModel.setSortOption(BidSortOption.LOWEST_PRICE) },
                                shape = RoundedCornerShape(12.dp),
                                label = { Text("Lowest price", fontWeight = FontWeight.Bold) }
                            )
                            FilterChip(
                                selected = uiState.sortOption == BidSortOption.TOP_RATED,
                                onClick = { viewModel.setSortOption(BidSortOption.TOP_RATED) },
                                shape = RoundedCornerShape(12.dp),
                                label = { Text("Top rated", fontWeight = FontWeight.Bold) }
                            )
                            FilterChip(
                                selected = uiState.sortOption == BidSortOption.NEAREST,
                                onClick = { viewModel.setSortOption(BidSortOption.NEAREST) },
                                shape = RoundedCornerShape(12.dp),
                                label = { Text("Nearest", fontWeight = FontWeight.Bold) }
                            )
                            FilterChip(
                                selected = uiState.certificateAttachedOnly,
                                onClick = {
                                    viewModel.setCertificateAttachedOnly(!uiState.certificateAttachedOnly)
                                },
                                shape = RoundedCornerShape(12.dp),
                                label = { Text("Certificate attached only", fontWeight = FontWeight.Bold) }
                            )
                        }
                    }
                    uiState.errorMessage?.let { error ->
                        item {
                            Text(error, color = MaterialTheme.colorScheme.error)
                        }
                    }
                    if (uiState.visibleBids.isEmpty()) {
                        item {
                            Text("No bids have an attached certificate.")
                        }
                    }
                    items(uiState.visibleBids) { bid ->
                        BidCard(
                            bid = bid,
                            isRecommended = bid.bidId == uiState.recommendedBidId,
                            isSelecting = uiState.isSelecting,
                            canSelect = uiState.job?.status == "Open",
                            onSelect = { viewModel.selectBid(bid.bidId) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BidCard(
    bid: Bid,
    isRecommended: Boolean,
    isSelecting: Boolean,
    canSelect: Boolean,
    onSelect: () -> Unit
) {
    val context = LocalContext.current
    val hasCertificate = !bid.certificateUrl.isNullOrBlank()
    val ratingAvg = if (bid.ratingCount == 0 && bid.providerReviewCount > 0) {
        bid.providerRating
    } else {
        bid.ratingAvg
    }
    val ratingCount = if (bid.ratingCount == 0) bid.providerReviewCount else bid.ratingCount

    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header: Name + Subtitle / Rating + Recommended Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = bid.providerName.ifBlank { "Provider" },
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp,
                            color = Color(0xFF1E293B)
                        )
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = if (ratingCount > 0) formatRating(ratingAvg, ratingCount) else "New provider · no reviews yet",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = Color(0xFF64748B),
                            fontSize = 12.5.sp
                        )
                    )
                }

                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (isRecommended) {
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = Color(0xFFFEF3C7)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                            ) {
                                Text(text = "⭐", fontSize = 11.sp)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Recommended",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFFD97706),
                                        fontSize = 11.5.sp
                                    )
                                )
                            }
                        }
                    }
                    if (hasCertificate) {
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = Color(0xFFEFF6FF)
                        ) {
                            Text(
                                text = "Certificate attached",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF2563EB),
                                    fontSize = 11.5.sp
                                ),
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                            )
                        }
                    }
                }
            }

            // Price & Details
            Text(
                text = "Rs. ${"%.1f".format(bid.price)}",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.ExtraBold,
                    color = Color(0xFF1E293B),
                    fontSize = 24.sp
                )
            )

            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                bid.distanceKm?.let { dist ->
                    Text(
                        text = "Distance: ${String.format(java.util.Locale.getDefault(), "%.1f", dist)} km",
                        style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFF64748B), fontSize = 13.sp)
                    )
                }
                Text(
                    text = bid.estimatedArrivalMinutes?.let { "Arrives in ~$it min" } ?: "ETA: Not provided",
                    style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFF64748B), fontSize = 13.sp)
                )
            }

            if (bid.message.isNotBlank()) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = Color(0xFFF8FAFC),
                    border = BorderStroke(1.dp, Color(0xFFF1F5F9)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = bid.message,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = Color(0xFF475569),
                            fontSize = 13.5.sp
                        ),
                        modifier = Modifier.padding(14.dp)
                    )
                }
            }

            bid.certificateUrl?.takeIf { it.isNotBlank() }?.let { certificateUrl ->
                OutlinedButton(
                    onClick = {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(certificateUrl)))
                    },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("View certificate")
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Button(
                onClick = onSelect,
                enabled = !isSelecting && canSelect,
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) {
                if (isSelecting) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = Color.White)
                } else {
                    Text(
                        text = "Select This Provider",
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            fontSize = 14.sp
                        )
                    )
                }
            }
        }
    }
}
