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
     * Weighted score: lower price is better (60%), higher provider rating is better (40%).
     * A provider with NO reviews yet gets a neutral rating score (0.5 = 2.5 stars) so a
     * brand-new provider isn't ranked below someone who has a 1-star record.
     * This only produces a suggestion — the customer always makes the final pick.
     */
    private fun computeRecommended(bids: List<Bid>): String? {
        if (bids.isEmpty()) return null

        val minPrice = bids.minOf { it.price }
        val maxPrice = bids.maxOf { it.price }
        val priceRange = (maxPrice - minPrice).takeIf { it > 0 } ?: 1.0

        return bids.maxByOrNull { bid ->
            val priceScore = 1.0 - ((bid.price - minPrice) / priceRange) // lower price = higher score
            val ratingScore = if (bid.providerReviewCount == 0) 0.5 else bid.providerRating / 5.0
            (priceScore * 0.6) + (ratingScore * 0.4)
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
