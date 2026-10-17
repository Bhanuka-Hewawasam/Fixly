package lk.ac.nibm.hdse.fixly_20.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import lk.ac.nibm.hdse.fixly_20.data.repository.BidRepository
import lk.ac.nibm.hdse.fixly_20.model.Bid

enum class BidSortOption {
    RECOMMENDED,
    LOWEST_PRICE,
    TOP_RATED,
    NEAREST
}

data class JobBidsUiState(
    val isLoading: Boolean = true,
    val bids: List<Bid> = emptyList(),
    val visibleBids: List<Bid> = emptyList(),
    val recommendedBidId: String? = null,
    val sortOption: BidSortOption = BidSortOption.RECOMMENDED,
    val certificateAttachedOnly: Boolean = false,
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
                _uiState.value = applyBidFilters(_uiState.value.copy(
                    isLoading = false,
                    bids = bids,
                    recommendedBidId = computeRecommended(bids)
                ))
            }
        }
    }

    fun setSortOption(sortOption: BidSortOption) {
        _uiState.value = applyBidFilters(_uiState.value.copy(sortOption = sortOption))
    }

    fun setCertificateAttachedOnly(enabled: Boolean) {
        _uiState.value = applyBidFilters(
            _uiState.value.copy(certificateAttachedOnly = enabled)
        )
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
            recommendationScore(bid, minPrice, priceRange)
        }?.bidId
    }

    private fun applyBidFilters(state: JobBidsUiState): JobBidsUiState {
        val filteredBids = state.bids.filter { bid ->
            !state.certificateAttachedOnly || !bid.certificateUrl.isNullOrBlank()
        }
        val minPrice = state.bids.minOfOrNull { it.price } ?: 0.0
        val priceRange = ((state.bids.maxOfOrNull { it.price } ?: minPrice) - minPrice)
            .takeIf { it > 0 } ?: 1.0
        val sortedBids = when (state.sortOption) {
            BidSortOption.RECOMMENDED ->
                filteredBids.sortedByDescending { recommendationScore(it, minPrice, priceRange) }
            BidSortOption.LOWEST_PRICE ->
                filteredBids.sortedBy { it.price }
            BidSortOption.TOP_RATED ->
                filteredBids.sortedWith(
                    compareByDescending<Bid> { ratingAverage(it) }
                        .thenByDescending { ratingCount(it) }
                )
            BidSortOption.NEAREST ->
                filteredBids.sortedBy { it.distanceKm ?: Double.POSITIVE_INFINITY }
        }
        return state.copy(visibleBids = sortedBids)
    }

    private fun recommendationScore(bid: Bid, minPrice: Double, priceRange: Double): Double {
        val priceScore = 1.0 - ((bid.price - minPrice) / priceRange)
        val ratingScore = if (ratingCount(bid) == 0) 0.5 else ratingAverage(bid) / 5.0
        return (priceScore * 0.6) + (ratingScore * 0.4)
    }

    private fun ratingAverage(bid: Bid): Double =
        if (bid.ratingCount == 0 && bid.providerReviewCount > 0) bid.providerRating else bid.ratingAvg

    private fun ratingCount(bid: Bid): Int =
        if (bid.ratingCount == 0) bid.providerReviewCount else bid.ratingCount

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
