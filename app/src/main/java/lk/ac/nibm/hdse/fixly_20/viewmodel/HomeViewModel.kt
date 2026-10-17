package lk.ac.nibm.hdse.fixly_20.viewmodel

import android.net.Uri
import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import lk.ac.nibm.hdse.fixly_20.BuildConfig
import lk.ac.nibm.hdse.fixly_20.data.repository.AuthRepository
import lk.ac.nibm.hdse.fixly_20.data.repository.EmergencyAlertRepository
import lk.ac.nibm.hdse.fixly_20.data.repository.UserRepository
import lk.ac.nibm.hdse.fixly_20.model.EmergencyAlert
import lk.ac.nibm.hdse.fixly_20.model.ProviderNotification
import lk.ac.nibm.hdse.fixly_20.model.User

data class HomeUiState(
    val isLoading: Boolean = true,
    val user: User? = null,
    val activeRole: String = "customer", // which tab/view is currently shown
    val notifications: List<ProviderNotification> = emptyList(),
    val activeCustomerAlert: EmergencyAlert? = null,
    val selectedEmergencyAlert: EmergencyAlert? = null,
    val isLoadingEmergencyDetails: Boolean = false,
    val emergencyDetailsError: String? = null,
    val acceptingAlertId: String? = null,
    val errorMessage: String? = null
)

class HomeViewModel(
    private val userRepository: UserRepository = UserRepository(),
    private val authRepository: AuthRepository = AuthRepository(),
    private val alertRepository: EmergencyAlertRepository = EmergencyAlertRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState

    init {
        loadUserProfile()
    }

    private fun loadUserProfile() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)

            userRepository.getCurrentUserProfile()
                .onSuccess { user ->
                    // Default the active tab to whichever role comes first (Customer preferred if both)
                    val defaultRole = if (user.roles.contains("customer")) "customer"
                                       else user.roles.firstOrNull() ?: "customer"
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        user = user,
                        activeRole = defaultRole
                    )
                    observeNotifications(user.uid)
                    observeCustomerActiveAlert()
                }
                .onFailure { exception ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = exception.localizedMessage ?: "Could not load profile"
                    )
                }
        }
    }

    private fun observeNotifications(providerId: String) {
        viewModelScope.launch {
            alertRepository.observeProviderNotifications(providerId).collect { notifs ->
                _uiState.value = _uiState.value.copy(notifications = notifs)
            }
        }
    }

    private fun observeCustomerActiveAlert() {
        viewModelScope.launch {
            alertRepository.observeMyActiveAlert().collect { alert ->
                _uiState.value = _uiState.value.copy(activeCustomerAlert = alert)
            }
        }
    }

    fun cancelActiveAlert(alertId: String) {
        viewModelScope.launch {
            if (BuildConfig.DEBUG) {
                Log.d("HomeViewModel", "DEBUG: request cancellation for alertId: $alertId")
            }
            alertRepository.closeAlert(alertId)
        }
    }

    fun markNotificationRead(notificationId: String) {
        viewModelScope.launch {
            alertRepository.markNotificationRead(notificationId)
        }
    }

    fun loadEmergencyDetails(alertId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                selectedEmergencyAlert = null,
                isLoadingEmergencyDetails = true,
                emergencyDetailsError = null
            )
            alertRepository.getAlert(alertId)
                .onSuccess { alert ->
                    _uiState.value = _uiState.value.copy(
                        selectedEmergencyAlert = alert,
                        isLoadingEmergencyDetails = false
                    )
                }
                .onFailure { error ->
                    _uiState.value = _uiState.value.copy(
                        isLoadingEmergencyDetails = false,
                        emergencyDetailsError = error.localizedMessage
                            ?: "Couldn't load the emergency details."
                    )
                }
        }
    }

    fun clearEmergencyDetails() {
        _uiState.value = _uiState.value.copy(
            selectedEmergencyAlert = null,
            isLoadingEmergencyDetails = false,
            emergencyDetailsError = null
        )
    }

    fun showEmergencyDetailsError(message: String) {
        _uiState.value = _uiState.value.copy(emergencyDetailsError = message)
    }

    fun acceptAlert(alertId: String, onResult: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            if (_uiState.value.acceptingAlertId != null) return@launch
            _uiState.value = _uiState.value.copy(acceptingAlertId = alertId)
            val user = _uiState.value.user
            if (user == null) {
                _uiState.value = _uiState.value.copy(acceptingAlertId = null)
                onResult(false, "User profile not loaded")
                return@launch
            }
            alertRepository.acceptAlert(alertId, user.uid)
                .onSuccess { onResult(true, null) }
                .onFailure { e -> onResult(false, e.localizedMessage) }
            _uiState.value = _uiState.value.copy(acceptingAlertId = null)
        }
    }

    fun declineAlert(notificationId: String, alertId: String) {
        viewModelScope.launch {
            val user = _uiState.value.user
            if (user != null) {
                alertRepository.declineAlert(alertId, user.uid, notificationId)
            }
        }
    }

    fun clearProviderNotifications() {
        viewModelScope.launch {
            val user = _uiState.value.user
            if (user != null) {
                alertRepository.clearClearedNotifications(user.uid)
            }
        }
    }

    fun uploadProfilePicture(context: Context, imageUri: Uri) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            userRepository.updateProfilePicture(context, imageUri)
                .onSuccess { newUrl ->
                    val updatedUser = _uiState.value.user?.copy(profilePictureUrl = newUrl)
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        user = updatedUser
                    )
                }
                .onFailure { exception ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = exception.localizedMessage ?: "Could not upload profile photo"
                    )
                }
        }
    }

    fun switchRole(role: String) {
        _uiState.value = _uiState.value.copy(activeRole = role)
    }

    fun logout(onComplete: () -> Unit, onError: (String) -> Unit = {}) {
        viewModelScope.launch {
            try {
                authRepository.logout()
                _uiState.value = HomeUiState(isLoading = true)
                onComplete()
            } catch (error: Exception) {
                Log.e("HomeViewModel", "Couldn't log out", error)
                val message = error.localizedMessage ?: "Couldn't log out."
                _uiState.value = _uiState.value.copy(errorMessage = message)
                onError(message)
            }
        }
    }
}
