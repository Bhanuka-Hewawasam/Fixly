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
    val jobs: List<JobPost> = emptyList()
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
}
