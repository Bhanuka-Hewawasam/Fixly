package lk.ac.nibm.hdse.fixly_20.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import lk.ac.nibm.hdse.fixly_20.data.repository.JobRepository
import lk.ac.nibm.hdse.fixly_20.model.JobPost

data class MyBookingsUiState(
    val isLoading: Boolean = true,
    val jobs: List<JobPost> = emptyList()
)

class MyBookingsViewModel(
    private val jobRepository: JobRepository = JobRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(MyBookingsUiState())
    val uiState: StateFlow<MyBookingsUiState> = _uiState

    init {
        viewModelScope.launch {
            jobRepository.observeCustomerJobs().collect { jobs ->
                _uiState.value = _uiState.value.copy(isLoading = false, jobs = jobs)
            }
        }
    }
}
