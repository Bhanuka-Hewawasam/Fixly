package lk.ac.nibm.hdse.fixly_20.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import lk.ac.nibm.hdse.fixly_20.model.Bid
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
            uiState.isLoading -> {
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
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
                    items(uiState.bids) { bid ->
                        BidCard(
                            bid = bid,
                            isRecommended = bid.bidId == uiState.recommendedBidId,
                            isSelecting = uiState.isSelecting,
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
    onSelect: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(bid.providerName, style = MaterialTheme.typography.titleMedium)
                if (isRecommended) {
                    AssistChip(onClick = {}, label = { Text("⭐ Recommended") })
                }
            }

            Spacer(modifier = Modifier.height(6.dp))
            Text("Rs. ${bid.price}", style = MaterialTheme.typography.headlineSmall)

            if (bid.message.isNotBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(bid.message, style = MaterialTheme.typography.bodyMedium)
            }

            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = onSelect,
                enabled = !isSelecting,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Select This Provider")
            }
        }
    }
}
