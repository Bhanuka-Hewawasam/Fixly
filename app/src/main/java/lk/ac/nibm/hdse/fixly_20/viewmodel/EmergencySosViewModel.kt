package lk.ac.nibm.hdse.fixly_20.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.firestore.GeoPoint
import com.google.firebase.firestore.FirebaseFirestoreException
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
    val customerLocation: GeoPoint? = null,
    val responses: List<EmergencyResponse> = emptyList(), // providers who said "I can help"
    val isSending: Boolean = false,
    val errorMessage: String? = null,
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
        if (_uiState.value.isSending) return
        viewModelScope.launch {
            _uiState.update { it.copy(isSending = true, errorMessage = null) }

            val profileResult = userRepository.getCurrentUserProfile()
            val profile = profileResult.getOrNull()
            if (profile == null) {
                _uiState.update {
                    it.copy(
                        isSending = false,
                        errorMessage = profileResult.exceptionOrNull()?.let(::friendlyError)
                            ?: "Couldn't load your profile. Please sign in and try again."
                    )
                }
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
                            errorMessage = friendlyError(exception)
                        )
                    }
                }
        }
    }

    fun closeAlert() {
        val alert = _uiState.value.myAlert ?: return
        viewModelScope.launch {
            val result = if (alert.status == "ACCEPTED") {
                alertRepository.finishAcceptedAlert(alert.alertId)
            } else {
                alertRepository.closeAlert(alert.alertId)
            }
            result.onFailure { error ->
                _uiState.update {
                    it.copy(errorMessage = error.localizedMessage ?: "Couldn't update the request.")
                }
            }
        }
    }

    fun showError(message: String) {
        _uiState.update { it.copy(errorMessage = message) }
    }

    fun setCustomerLocation(location: GeoPoint) {
        _uiState.update { it.copy(customerLocation = location, errorMessage = null) }
    }

    private fun friendlyError(error: Throwable): String = when (error) {
        is FirebaseNetworkException -> "No internet connection. Check your connection and try again."
        is FirebaseFirestoreException -> when (error.code) {
            FirebaseFirestoreException.Code.PERMISSION_DENIED ->
                "Your account isn't allowed to send this request. Sign in again or contact support."
            FirebaseFirestoreException.Code.UNAVAILABLE,
            FirebaseFirestoreException.Code.DEADLINE_EXCEEDED ->
                "The service is temporarily unavailable. Check your connection and try again."
            else -> "We couldn't save your emergency request. Please try again."
        }
        is FirebaseAuthException ->
            "Please sign in again before sending an emergency request."
        is IllegalStateException -> error.localizedMessage ?: "The emergency request couldn't be completed."
        else -> "We couldn't send your emergency request. Please try again."
    }
}
