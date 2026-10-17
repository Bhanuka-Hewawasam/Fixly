package lk.ac.nibm.hdse.fixly_20.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.firestore.GeoPoint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import lk.ac.nibm.hdse.fixly_20.data.repository.BidRepository
import lk.ac.nibm.hdse.fixly_20.data.repository.JobRepository
import lk.ac.nibm.hdse.fixly_20.data.repository.UserRepository
import lk.ac.nibm.hdse.fixly_20.model.JobPost
import lk.ac.nibm.hdse.fixly_20.util.DistanceUtils

data class JobWithDistance(
    val job: JobPost,
    val distanceKm: Double
)

data class AvailableJobsUiState(
    val isLoading: Boolean = true,
    val allJobs: List<JobPost> = emptyList(),          // open jobs matching the provider's categories
    val providerLocation: GeoPoint? = null,            // provider's current GPS position
    val radiusKm: Int = 25,                            // how far the provider is willing to travel
    val visibleJobs: List<JobWithDistance> = emptyList(), // allJobs after the distance filter, nearest first
    val errorMessage: String? = null,
    val isSubmittingBid: Boolean = false,
    val bidSubmittedJobId: String? = null
)

class AvailableJobsViewModel(
    private val jobRepository: JobRepository = JobRepository(),
    private val bidRepository: BidRepository = BidRepository(),
    private val userRepository: UserRepository = UserRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(AvailableJobsUiState())
    val uiState: StateFlow<AvailableJobsUiState> = _uiState

    init {
        viewModelScope.launch {
            // 1) Category filter happens in Firestore (provider only sees their own trades)
            val myCategories = userRepository.getCurrentUserProfile().getOrNull()?.serviceCategories ?: emptyList()

            jobRepository.observeOpenJobs(myCategories).collect { jobs ->
                _uiState.update { applyDistanceFilter(it.copy(isLoading = false, allJobs = jobs)) }
            }
        }
    }

    fun setProviderLocation(location: GeoPoint) {
        _uiState.update { applyDistanceFilter(it.copy(providerLocation = location)) }
    }

    fun setRadius(km: Int) {
        _uiState.update { applyDistanceFilter(it.copy(radiusKm = km)) }
    }

    /**
     * 2) Distance filter happens on the device: Firestore can't do "within X km" queries natively,
     * so we compute each job's distance from the provider and keep only the ones inside the radius.
     * Emergency jobs float to the top, then nearest first.
     */
    private fun applyDistanceFilter(state: AvailableJobsUiState): AvailableJobsUiState {
        val myLocation = state.providerLocation ?: return state.copy(visibleJobs = emptyList())

        val visible = state.allJobs
            .mapNotNull { job ->
                val jobLocation = job.location ?: return@mapNotNull null // no GPS on the job = can't judge distance
                val distance = DistanceUtils.distanceKm(
                    myLocation.latitude, myLocation.longitude,
                    jobLocation.latitude, jobLocation.longitude
                )
                if (distance <= state.radiusKm) JobWithDistance(job, distance) else null
            }
            .sortedWith(
                compareByDescending<JobWithDistance> { it.job.isEmergency }
                    .thenBy { it.distanceKm }
            )

        return state.copy(visibleJobs = visible)
    }

    fun submitBid(
        jobId: String,
        price: Double,
        message: String,
        estimatedArrivalMinutes: Int?
    ) {
        if (_uiState.value.isSubmittingBid) return
        if (!price.isFinite() || price <= 0.0) {
            _uiState.update { it.copy(errorMessage = "Enter a price greater than zero.") }
            return
        }
        if (estimatedArrivalMinutes != null && estimatedArrivalMinutes !in 1..1440) {
            _uiState.update { it.copy(errorMessage = "ETA must be between 1 and 1440 minutes.") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSubmittingBid = true, errorMessage = null) }

            val currentState = _uiState.value
            val jobLocation = currentState.allJobs.firstOrNull { it.jobId == jobId }?.location
            val providerLocation = currentState.providerLocation
            if (jobLocation == null || providerLocation == null) {
                _uiState.update {
                    it.copy(
                        isSubmittingBid = false,
                        errorMessage = "Could not calculate the job distance. Refresh your location and try again."
                    )
                }
                return@launch
            }
            val distanceKm = DistanceUtils.distanceKm(
                providerLocation.latitude,
                providerLocation.longitude,
                jobLocation.latitude,
                jobLocation.longitude
            )
            val providerName = userRepository.getCurrentUserProfile().getOrNull()?.name ?: "Provider"

            bidRepository.submitBid(
                jobId,
                providerName,
                price,
                message,
                distanceKm,
                estimatedArrivalMinutes
            )
                .onSuccess {
                    _uiState.update { it.copy(isSubmittingBid = false, bidSubmittedJobId = jobId) }
                }
                .onFailure { exception ->
                    _uiState.update {
                        it.copy(
                            isSubmittingBid = false,
                            errorMessage = exception.localizedMessage ?: "Failed to submit bid"
                        )
                    }
                }
        }
    }

    fun clearBidSubmitted() {
        _uiState.update { it.copy(bidSubmittedJobId = null) }
    }
}
