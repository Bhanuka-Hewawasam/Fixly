package lk.ac.nibm.hdse.fixly_20.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.firestore.GeoPoint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import lk.ac.nibm.hdse.fixly_20.data.repository.EmergencyRepository
import lk.ac.nibm.hdse.fixly_20.data.repository.UserRepository

data class ProviderAvailabilityUiState(
    val isAvailable: Boolean = false,
    val isUpdating: Boolean = false,
    val errorMessage: String? = null
)

class ProviderAvailabilityViewModel(
    private val userRepository: UserRepository = UserRepository(),
    private val emergencyRepository: EmergencyRepository = EmergencyRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProviderAvailabilityUiState())
    val uiState: StateFlow<ProviderAvailabilityUiState> = _uiState

    private var lastPublishedLocation: GeoPoint? = null
    private var lastPublishedAtMs: Long = 0L

    init {
        // Pick up the switch position from the last session
        viewModelScope.launch {
            val profile = userRepository.getCurrentUserProfile().getOrNull()
            _uiState.update { it.copy(isAvailable = profile?.emergencyAvailable ?: false) }
        }
    }

    fun setAvailable(available: Boolean, location: GeoPoint?) {
        if (available && location == null) {
            _uiState.update {
                it.copy(errorMessage = "Allow location access and turn on Location services to become available.")
            }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isUpdating = true, errorMessage = null) }

            emergencyRepository.setAvailability(available, location)
                .onSuccess {
                    if (available) {
                        lastPublishedLocation = location
                        lastPublishedAtMs = System.currentTimeMillis()
                    } else {
                        lastPublishedLocation = null
                        lastPublishedAtMs = 0L
                    }
                    _uiState.update { it.copy(isAvailable = available, isUpdating = false) }
                }
                .onFailure {
                    _uiState.update {
                        it.copy(isUpdating = false, errorMessage = "Couldn't update your availability. Try again.")
                    }
                }
        }
    }

    /** Keeps the shared position fresh while the switch is ON (called when the screen gets a new GPS fix). */
    fun refreshLocation(location: GeoPoint) {
        if (!_uiState.value.isAvailable ||
            (location == lastPublishedLocation &&
                System.currentTimeMillis() - lastPublishedAtMs < LOCATION_REFRESH_INTERVAL_MS)
        ) return

        viewModelScope.launch {
            emergencyRepository.setAvailability(true, location)
                .onSuccess {
                    lastPublishedLocation = location
                    lastPublishedAtMs = System.currentTimeMillis()
                }
        }
    }

    fun showNotificationPermissionWarning() {
        _uiState.update {
            it.copy(errorMessage = "Emergency availability is on, but notifications are disabled. Keep Fixly open to see requests.")
        }
    }

    private companion object {
        const val LOCATION_REFRESH_INTERVAL_MS = 60_000L
    }
}
