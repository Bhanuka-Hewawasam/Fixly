package lk.ac.nibm.hdse.fixly_20.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.GeoPoint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import lk.ac.nibm.hdse.fixly_20.data.repository.EmergencyAlertRepository
import lk.ac.nibm.hdse.fixly_20.data.repository.EmergencyRepository
import lk.ac.nibm.hdse.fixly_20.data.repository.UserRepository
import lk.ac.nibm.hdse.fixly_20.model.User
import lk.ac.nibm.hdse.fixly_20.util.DistanceUtils
import lk.ac.nibm.hdse.fixly_20.util.EmergencyProviderMatcher

data class ProviderWithDistance(
    val provider: User,
    val distanceKm: Double,
    val minutesAgo: Long, // how long ago the provider last shared their location
    val isDemo: Boolean = false
)

data class EmergencyUiState(
    val isLoading: Boolean = true,
    val customerLocation: GeoPoint? = null,
    val selectedCategory: String = "Any Available Provider",
    val radiusKm: Int = 10,
    val allProviders: List<User> = emptyList(), // everyone who has "Available for emergencies" ON
    val results: List<ProviderWithDistance> = emptyList() // allProviders after category/distance/freshness filters
)

class EmergencyViewModel(
    private val emergencyRepository: EmergencyRepository = EmergencyRepository(),
    private val alertRepository: EmergencyAlertRepository = EmergencyAlertRepository(),
    private val userRepository: UserRepository = UserRepository(),
    private val firebaseAuth: FirebaseAuth = FirebaseAuth.getInstance()
) : ViewModel() {

    private val _uiState = MutableStateFlow(EmergencyUiState())
    val uiState: StateFlow<EmergencyUiState> = _uiState

    init {
        // Live stream: a provider switching ON/OFF appears/disappears here instantly
        viewModelScope.launch {
            emergencyRepository.observeAvailableProviders().collect { providers ->
                _uiState.update { applyFilters(it.copy(isLoading = false, allProviders = providers)) }
            }
        }
    }

    fun setCustomerLocation(location: GeoPoint) {
        _uiState.update { applyFilters(it.copy(customerLocation = location)) }
    }

    fun setCategory(category: String) {
        _uiState.update { applyFilters(it.copy(selectedCategory = category)) }
    }

    fun setRadius(km: Int) {
        _uiState.update { applyFilters(it.copy(radiusKm = km)) }
    }

    fun sendDirectRequest(
        providerId: String,
        distanceKm: Double,
        note: String,
        category: String,
        onResult: (Result<String>) -> Unit
    ) {
        viewModelScope.launch {
            val myLocation = _uiState.value.customerLocation
            if (myLocation == null) {
                onResult(Result.failure(IllegalStateException("Customer location not available")))
                return@launch
            }

            val profile = userRepository.getCurrentUserProfile().getOrNull()
            if (profile == null) {
                onResult(Result.failure(IllegalStateException("User profile not found")))
                return@launch
            }

            val result = alertRepository.sendDirectAlert(
                customerName = profile.name,
                customerPhone = profile.phoneNumber,
                category = category,
                note = note,
                location = myLocation,
                selectedProviderId = providerId,
                distanceKm = distanceKm
            )
            onResult(result)
        }
    }

    private fun applyFilters(state: EmergencyUiState): EmergencyUiState {
        val myLocation = state.customerLocation ?: return state.copy(results = emptyList())
        val matches = EmergencyProviderMatcher.findMatches(
            providers = state.allProviders,
            customerLocation = myLocation,
            category = state.selectedCategory,
            radiusKm = state.radiusKm,
            currentUid = firebaseAuth.currentUser?.uid,
            currentEmail = firebaseAuth.currentUser?.email
        )
        val results = matches.map { ProviderWithDistance(it.provider, it.distanceKm, it.minutesAgo) }

        return state.copy(results = results)
    }
}
