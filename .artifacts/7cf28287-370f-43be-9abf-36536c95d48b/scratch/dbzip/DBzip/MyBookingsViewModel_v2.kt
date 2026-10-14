package lk.ac.nibm.hdse.fixly_20.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import lk.ac.nibm.hdse.fixly_20.data.repository.BookingsRepository
import lk.ac.nibm.hdse.fixly_20.model.JobPost

// Same shape as before, so MyBookingsScreen needs no changes
data class MyBookingsUiState(
    val isLoading: Boolean = true,
    val jobs: List<JobPost> = emptyList()
)

class MyBookingsViewModel(
    private val repository: BookingsRepository = BookingsRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(MyBookingsUiState())
    val uiState: StateFlow<MyBookingsUiState> = _uiState

    private var firstSyncDone = false

    init {
        // 1) The screen only ever reads the LOCAL database (instant, and works offline)
        viewModelScope.launch {
            repository.observeBookings().collect { jobs ->
                _uiState.update {
                    // Keep the spinner only while there's nothing to show AND we haven't heard from Firestore yet
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
}
