package lk.ac.nibm.hdse.fixly_20.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import lk.ac.nibm.hdse.fixly_20.data.repository.JobRepository
import lk.ac.nibm.hdse.fixly_20.model.JobPost

data class AcceptedJobsUiState(
    val isLoading: Boolean = true,
    val jobs: List<JobPost> = emptyList(),
    val cancellingJobId: String? = null,
    val cancellationSuccessJobId: String? = null,
    val cancellationError: String? = null
)

class AcceptedJobsViewModel(
    private val jobRepository: JobRepository = JobRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(AcceptedJobsUiState())
    val uiState: StateFlow<AcceptedJobsUiState> = _uiState

    init {
        viewModelScope.launch {
            jobRepository.observeProviderAssignedJobs().collect { jobs ->
                _uiState.value = _uiState.value.copy(isLoading = false, jobs = jobs)
            }
        }
    }

    fun cancelJobAsProvider(jobId: String, reason: String) {
        if (_uiState.value.cancellingJobId != null) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                cancellingJobId = jobId,
                cancellationSuccessJobId = null,
                cancellationError = null
            )
            jobRepository.cancelJobAsProvider(jobId, reason)
                .onSuccess {
                    _uiState.value = _uiState.value.copy(
                        cancellingJobId = null,
                        cancellationSuccessJobId = jobId
                    )
                }
                .onFailure { error ->
                    _uiState.value = _uiState.value.copy(
                        cancellingJobId = null,
                        cancellationError = error.localizedMessage ?: "Could not cancel this booking."
                    )
                }
        }
    }

    fun clearCancellationState() {
        _uiState.value = _uiState.value.copy(cancellationSuccessJobId = null, cancellationError = null)
    }
}
