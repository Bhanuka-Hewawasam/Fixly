package lk.ac.nibm.hdse.fixly_20.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import lk.ac.nibm.hdse.fixly_20.data.repository.BidRepository
import lk.ac.nibm.hdse.fixly_20.data.repository.JobRepository
import lk.ac.nibm.hdse.fixly_20.data.repository.UserRepository
import lk.ac.nibm.hdse.fixly_20.model.JobPost

data class AvailableJobsUiState(
    val isLoading: Boolean = true,
    val jobs: List<JobPost> = emptyList(),
    val errorMessage: String? = null,
    val isSubmittingBid: Boolean = false,
    val bidSubmittedJobId: String? = null // used to show a confirmation + close the dialog
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
            jobRepository.observeOpenJobs()
                .collect { jobs ->
                    _uiState.value = _uiState.value.copy(isLoading = false, jobs = jobs)
                }
        }
    }

    fun submitBid(jobId: String, price: Double, message: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSubmittingBid = true, errorMessage = null)

            val providerName = userRepository.getCurrentUserProfile().getOrNull()?.name ?: "Provider"

            bidRepository.submitBid(jobId, providerName, price, message)
                .onSuccess {
                    _uiState.value = _uiState.value.copy(
                        isSubmittingBid = false,
                        bidSubmittedJobId = jobId
                    )
                }
                .onFailure { exception ->
                    _uiState.value = _uiState.value.copy(
                        isSubmittingBid = false,
                        errorMessage = exception.localizedMessage ?: "Failed to submit bid"
                    )
                }
        }
    }

    fun clearBidSubmitted() {
        _uiState.value = _uiState.value.copy(bidSubmittedJobId = null)
    }
}
