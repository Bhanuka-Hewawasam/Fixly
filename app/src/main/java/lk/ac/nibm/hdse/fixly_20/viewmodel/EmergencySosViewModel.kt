package lk.ac.nibm.hdse.fixly_20.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.firestore.GeoPoint
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import lk.ac.nibm.hdse.fixly_20.data.repository.EmergencyAlertRepository
import lk.ac.nibm.hdse.fixly_20.data.repository.UserRepository
import lk.ac.nibm.hdse.fixly_20.model.EmergencyAlert
import lk.ac.nibm.hdse.fixly_20.model.EmergencyResponse

data class EmergencySosUiState(
    val isLoading: Boolean = true,                    // waiting for the first alert snapshot
    val myAlert: EmergencyAlert? = null,              // the customer's live alert, if any
    val responses: List<EmergencyResponse> = emptyList(), // providers who said "I can help"
    val isSending: Boolean = false,
    val errorMessage: String? = null
)

class EmergencySosViewModel(
    private val alertRepository: EmergencyAlertRepository = EmergencyAlertRepository(),
    private val userRepository: UserRepository = UserRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(EmergencySosUiState())
    val uiState: StateFlow<EmergencySosUiState> = _uiState

    private var responsesJob: Job? = null
    private var observedAlertId: String? = null

    init {
        viewModelScope.launch {
            alertRepository.observeMyActiveAlert()
                .catch {
                    _uiState.update {
                        it.copy(isLoading = false, errorMessage = "Couldn't load your emergency status. Check your connection.")
                    }
                }
                .collect { alert ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            myAlert = alert,
                            responses = if (alert == null) emptyList() else it.responses
                        )
                    }

                    // (Re)start watching responses only when the alert itself changes
                    if (alert?.alertId != observedAlertId) {
                        observedAlertId = alert?.alertId
                        responsesJob?.cancel()
                        if (alert != null) {
                            responsesJob = launch {
                                alertRepository.observeResponses(alert.alertId)
                                    .catch { /* responses list simply stays as it was */ }
                                    .collect { list -> _uiState.update { it.copy(responses = list) } }
                            }
                        }
                    }
                }
        }
    }

    /** The one tap: saves the alert with the customer's GPS position. Providers' phones do the rest. */
    fun sendAlert(location: GeoPoint, note: String, category: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSending = true, errorMessage = null) }

            val profile = userRepository.getCurrentUserProfile().getOrNull()
            if (profile == null) {
                _uiState.update { it.copy(isSending = false, errorMessage = "Couldn't load your profile. Try again.") }
                return@launch
            }

            alertRepository.sendAlert(
                customerName = profile.name,
                customerPhone = profile.phoneNumber,
                category = category,
                note = note.trim(),
                location = location
            )
                .onSuccess {
                    // The live listener above flips the screen to the "alert is live" view
                    _uiState.update { it.copy(isSending = false) }
                }
                .onFailure { exception ->
                    _uiState.update {
                        it.copy(
                            isSending = false,
                            errorMessage = exception.localizedMessage ?: "Couldn't send the alert. Try again."
                        )
                    }
                }
        }
    }

    fun closeAlert() {
        val alertId = _uiState.value.myAlert?.alertId ?: return
        viewModelScope.launch { alertRepository.closeAlert(alertId) }
    }

    fun showError(message: String) {
        _uiState.update { it.copy(errorMessage = message) }
    }
}
