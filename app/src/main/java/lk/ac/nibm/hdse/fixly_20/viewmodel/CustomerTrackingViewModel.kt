
package lk.ac.nibm.hdse.fixly_20.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import lk.ac.nibm.hdse.fixly_20.data.repository.JobCompletionRepository
import lk.ac.nibm.hdse.fixly_20.data.repository.JobRepository
import lk.ac.nibm.hdse.fixly_20.model.JobPost

data class CustomerTrackingUiState(
    val job: JobPost? = null,
    val isSubmitting: Boolean = false,
    val errorMessage: String? = null
)

class CustomerTrackingViewModel(
    private val jobId: String,
    jobRepository: JobRepository = JobRepository(),
    private val completionRepository: JobCompletionRepository = JobCompletionRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(CustomerTrackingUiState())
    val uiState: StateFlow<CustomerTrackingUiState> = _uiState

    init {
        viewModelScope.launch {
            jobRepository.observeJob(jobId).collect { job ->
                _uiState.value = _uiState.value.copy(job = job)
            }
        }
    }

    /** Customer approves the finished work and rates the provider (1–5 stars). */
    fun confirmWithReview(rating: Int, comment: String) {
        val job = _uiState.value.job ?: return
        if (job.customerConfirmed) return // already reviewed — never count a job's stars twice

        if (rating !in 1..5) {
            _uiState.value = _uiState.value.copy(errorMessage = "Please tap the stars to rate the provider")
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSubmitting = true, errorMessage = null)

            completionRepository.confirmWithReview(job, rating, comment.trim())
                .onSuccess {
                    _uiState.value = _uiState.value.copy(isSubmitting = false)
                }
                .onFailure {
                    _uiState.value = _uiState.value.copy(
                        isSubmitting = false,
                        errorMessage = "Couldn't submit your review. Please try again."
                    )
                }
        }
    }

    /** Customer isn't satisfied: job returns to the provider as "In Progress". */
    fun sendBackToProvider() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSubmitting = true, errorMessage = null)

            completionRepository.sendBackToProvider(jobId)
                .onSuccess {
                    _uiState.value = _uiState.value.copy(isSubmitting = false)
                }
                .onFailure {
                    _uiState.value = _uiState.value.copy(
                        isSubmitting = false,
                        errorMessage = "Couldn't send the job back. Please try again."
                    )
                }
        }
    }
}

class CustomerTrackingViewModelFactory(private val jobId: String) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return CustomerTrackingViewModel(jobId) as T
    }
}
