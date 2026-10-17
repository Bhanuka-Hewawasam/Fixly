package lk.ac.nibm.hdse.fixly_20.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import lk.ac.nibm.hdse.fixly_20.data.repository.UserRepository
import lk.ac.nibm.hdse.fixly_20.model.User

data class ProviderAvailabilityUiState(
    val isLoading: Boolean = true,
    val isUpdating: Boolean = false,
    val errorMessage: String? = null,
    val saved: Boolean = false,
    val user: User? = null
)

class ProviderAvailabilityViewModel(
    private val userRepository: UserRepository = UserRepository()
) : ViewModel() {
    private val _uiState = MutableStateFlow(ProviderAvailabilityUiState())
    val uiState: StateFlow<ProviderAvailabilityUiState> = _uiState

    init {
        viewModelScope.launch {
            userRepository.getCurrentUserProfile()
                .onSuccess { user ->
                    _uiState.update {
                        it.copy(isLoading = false, user = user, errorMessage = null)
                    }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = error.localizedMessage ?: "Couldn't load your profile."
                        )
                    }
                }
        }
    }

    fun saveServiceProfile(
        serviceAddress: String,
        serviceCity: String,
        latitude: String,
        longitude: String,
        serviceRadiusKm: Int,
        serviceCategories: List<String>,
        availableForEmergency: Boolean
    ) {
        if (_uiState.value.isUpdating) return
        val parsedLatitude = latitude.toDoubleOrNull()
        val parsedLongitude = longitude.toDoubleOrNull()
        if (parsedLatitude == null || parsedLongitude == null) {
            _uiState.update {
                it.copy(errorMessage = "Choose your service location on the map or use your location once.")
            }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isUpdating = true, errorMessage = null, saved = false) }
            userRepository.saveProviderServiceProfile(
                serviceAddress = serviceAddress,
                serviceCity = serviceCity,
                serviceLatitude = parsedLatitude,
                serviceLongitude = parsedLongitude,
                serviceRadiusKm = serviceRadiusKm,
                serviceCategories = serviceCategories,
                availableForEmergency = availableForEmergency
            ).onSuccess {
                userRepository.getCurrentUserProfile()
                    .onSuccess { user ->
                        _uiState.update {
                            it.copy(user = user, isUpdating = false, saved = true)
                        }
                    }
                    .onFailure { error ->
                        _uiState.update {
                            it.copy(
                                isUpdating = false,
                                errorMessage = error.localizedMessage
                                    ?: "Saved the service profile, but couldn't refresh it."
                            )
                        }
                    }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isUpdating = false,
                        errorMessage = error.localizedMessage
                            ?: "Couldn't save your provider service profile."
                    )
                }
            }
        }
    }
}
