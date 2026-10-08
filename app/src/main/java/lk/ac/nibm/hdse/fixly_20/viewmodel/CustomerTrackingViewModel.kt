package lk.ac.nibm.hdse.fixly_20.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import lk.ac.nibm.hdse.fixly_20.data.repository.JobRepository
import lk.ac.nibm.hdse.fixly_20.model.JobPost

data class CustomerTrackingUiState(
    val job: JobPost? = null
)

class CustomerTrackingViewModel(
    jobId: String,
    jobRepository: JobRepository = JobRepository()
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
}

class CustomerTrackingViewModelFactory(private val jobId: String) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return CustomerTrackingViewModel(jobId) as T
    }
}
