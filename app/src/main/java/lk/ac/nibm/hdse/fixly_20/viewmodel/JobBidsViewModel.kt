package lk.ac.nibm.hdse.fixly_20.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import lk.ac.nibm.hdse.fixly_20.data.repository.BidRepository
import lk.ac.nibm.hdse.fixly_20.model.Bid

data class JobBidsUiState(
    val isLoading: Boolean = true,
    val bids: List<Bid> = emptyList(),
    val recommendedBidId: String? = null,
    val isSelecting: Boolean = false,
    val selectionDone: Boolean = false,
    val errorMessage: String? = null
)

class JobBidsViewModel(
    private val jobId: String,
    private val bidRepository: BidRepository = BidRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(JobBidsUiState())
    val uiState: StateFlow<JobBidsUiState> = _uiState

    init {
        viewModelScope.launch {
            bidRepository.observeBidsForJob(jobId).collect { bids ->
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    bids = bids,
                    recommendedBidId = computeRecommended(bids)
                )
            }
        }
    }

    /**
     * Simple weighted score: lower price is better, higher provider rating is better.
     * Price carries more weight (60%) since it's the most consistently available signal
     * right now; rating weight (40%) grows in importance once reviews are implemented.
     * This only produces a suggestion — the customer always makes the final pick.
     */
    private fun computeRecommended(bids: List<Bid>): String? {
        if (bids.isEmpty()) return null

        val minPrice = bids.minOf { it.price }
        val maxPrice = bids.maxOf { it.price }
        val priceRange = (maxPrice - minPrice).takeIf { it > 0 } ?: 1.0

        return bids.maxByOrNull { bid ->
            val normalizedPriceScore = 1.0 - ((bid.price - minPrice) / priceRange) // lower price = higher score
            val ratingScore = bid.providerRating / 5.0
            (normalizedPriceScore * 0.6) + (ratingScore * 0.4)
        }?.bidId
    }

    fun selectBid(bidId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSelecting = true, errorMessage = null)

            bidRepository.selectBid(jobId, bidId)
                .onSuccess {
                    _uiState.value = _uiState.value.copy(isSelecting = false, selectionDone = true)
                }
                .onFailure { exception ->
                    _uiState.value = _uiState.value.copy(
                        isSelecting = false,
                        errorMessage = exception.localizedMessage ?: "Failed to select bid"
                    )
                }
        }
    }
}
