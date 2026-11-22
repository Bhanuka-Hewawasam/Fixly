package lk.ac.nibm.hdse.fixly_20.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import lk.ac.nibm.hdse.fixly_20.data.repository.BookingsRepository
import lk.ac.nibm.hdse.fixly_20.data.repository.JobRepository
import lk.ac.nibm.hdse.fixly_20.model.JobPost

data class MyBookingsUiState(
    val isLoading: Boolean = true,
    val jobs: List<JobPost> = emptyList(),
    val cancellingJobId: String? = null,
    val cancellationSuccessJobId: String? = null,
    val cancellationError: String? = null
)

class MyBookingsViewModel(
    private val repository: BookingsRepository = BookingsRepository(),
    private val jobRepository: JobRepository = JobRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(MyBookingsUiState())
    val uiState: StateFlow<MyBookingsUiState> = _uiState

    private var firstSyncDone = false

    init {
        // 1) The screen only ever reads the LOCAL database (instant, and works offline)
        viewModelScope.launch {
            repository.observeBookings().collect { jobs ->
                _uiState.update {
                    it.copy(jobs = jobs, isLoading = jobs.isEmpty() && !firstSyncDone)
                }
            }
        }

        // 2) In the background, Firestore keeps that local database fresh
        viewModelScope.launch {
            repository.syncFromFirestore {
                firstSyncDone = true
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    fun cancelJobAsCustomer(jobId: String, reason: String) {
        if (_uiState.value.cancellingJobId != null) return
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    cancellingJobId = jobId,
                    cancellationSuccessJobId = null,
                    cancellationError = null
                )
            }
            jobRepository.cancelJobAsCustomer(jobId, reason)
                .onSuccess {
                    _uiState.update {
                        it.copy(cancellingJobId = null, cancellationSuccessJobId = jobId)
                    }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            cancellingJobId = null,
                            cancellationError = error.localizedMessage ?: "Could not cancel this job."
                        )
                    }
                }
        }
    }

    fun clearCancellationState() {
        _uiState.update {
            it.copy(cancellationSuccessJobId = null, cancellationError = null)
        }
    }
}
