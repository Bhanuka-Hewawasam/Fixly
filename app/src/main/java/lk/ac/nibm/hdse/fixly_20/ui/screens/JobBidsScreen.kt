package lk.ac.nibm.hdse.fixly_20.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
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
                title = { Text("Bids Received") },
                navigationIcon = { TextButton(onClick = onBack) { Text("Back") } }
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
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilterChip(
                                selected = uiState.sortOption == BidSortOption.RECOMMENDED,
                                onClick = { viewModel.setSortOption(BidSortOption.RECOMMENDED) },
                                label = { Text("Recommended") }
                            )
                            FilterChip(
                                selected = uiState.sortOption == BidSortOption.LOWEST_PRICE,
                                onClick = { viewModel.setSortOption(BidSortOption.LOWEST_PRICE) },
                                label = { Text("Lowest price") }
                            )
                            FilterChip(
                                selected = uiState.sortOption == BidSortOption.TOP_RATED,
                                onClick = { viewModel.setSortOption(BidSortOption.TOP_RATED) },
                                label = { Text("Top rated") }
                            )
                            FilterChip(
                                selected = uiState.sortOption == BidSortOption.NEAREST,
                                onClick = { viewModel.setSortOption(BidSortOption.NEAREST) },
                                label = { Text("Nearest") }
                            )
                            FilterChip(
                                selected = uiState.certificateAttachedOnly,
                                onClick = {
                                    viewModel.setCertificateAttachedOnly(!uiState.certificateAttachedOnly)
                                },
                                label = { Text("Certificate attached only") }
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

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(bid.providerName, style = MaterialTheme.typography.titleMedium)
                    Text(
                        formatRating(ratingAvg, ratingCount),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    if (isRecommended) {
                        AssistChip(onClick = {}, label = { Text("⭐ Recommended") })
                    }
                    if (hasCertificate) {
                        AssistChip(onClick = {}, label = { Text("Certificate attached") })
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))
            Text("Rs. ${bid.price}", style = MaterialTheme.typography.headlineSmall)
            Text(
                bid.distanceKm?.let {
                    "Distance: ${String.format(java.util.Locale.getDefault(), "%.1f", it)} km"
                } ?: "Distance unavailable",
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                bid.estimatedArrivalMinutes?.let { "Arrives in ~$it min" } ?: "ETA: Not provided",
                style = MaterialTheme.typography.bodySmall
            )

            if (bid.message.isNotBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(bid.message, style = MaterialTheme.typography.bodyMedium)
            }

            bid.certificateUrl?.takeIf { it.isNotBlank() }?.let { certificateUrl ->
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(certificateUrl)))
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("View certificate")
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = onSelect,
                enabled = !isSelecting && canSelect,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Select This Provider")
            }
        }
    }
}
