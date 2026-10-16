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

data class ProviderWithDistance(
    val provider: User,
    val distanceKm: Double,
    val minutesAgo: Long // how long ago the provider last shared their location
)

data class EmergencyUiState(
    val isLoading: Boolean = true,
    val customerLocation: GeoPoint? = null,
    val selectedCategory: String = "Any",
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

    fun seedDemoProviders(onResult: (String) -> Unit) {
        viewModelScope.launch {
            alertRepository.seedDemoProviders()
                .onSuccess { onResult("Successfully seeded 3 demo SOS providers.") }
                .onFailure { onResult("Failed to seed: ${it.localizedMessage}") }
        }
    }

    fun deleteDemoProviders(onResult: (String) -> Unit) {
        viewModelScope.launch {
            alertRepository.deleteDemoProviders()
                .onSuccess { onResult("Successfully deleted demo SOS providers.") }
                .onFailure { onResult("Failed to delete: ${it.localizedMessage}") }
        }
    }

    private fun applyFilters(state: EmergencyUiState): EmergencyUiState {
        val myLocation = state.customerLocation ?: return state.copy(results = emptyList())
        val myUid = firebaseAuth.currentUser?.uid
        val myEmail = firebaseAuth.currentUser?.email
        val now = System.currentTimeMillis()

        val results = state.allProviders.mapNotNull { provider ->
            // Robust self-exclusion: customer must never see themselves as a provider
            if (myUid != null && provider.uid == myUid) return@mapNotNull null
            if (myEmail != null && provider.email.isNotBlank() && provider.email.equals(myEmail, ignoreCase = true)) {
                return@mapNotNull null
            }

            val providerLocation = provider.lastLocation ?: return@mapNotNull null
            val sharedAt = provider.lastLocationAt ?: return@mapNotNull null

            // Too old = the provider has probably moved (or forgot the switch is ON)
            val ageMs = maxOf(0L, now - sharedAt.time)
            if (ageMs > MAX_LOCATION_AGE_MS) return@mapNotNull null

            if (state.selectedCategory != "Any" && !provider.serviceCategories.contains(state.selectedCategory)) {
                return@mapNotNull null
            }

            val distance = DistanceUtils.distanceKm(
                myLocation.latitude, myLocation.longitude,
                providerLocation.latitude, providerLocation.longitude
            )
            if (distance > state.radiusKm) return@mapNotNull null

            ProviderWithDistance(provider, distance, ageMs / 60_000)
        }.sortedBy { it.distanceKm } // nearest first — in an emergency, closest helps fastest

        return state.copy(results = results)
    }

    private companion object {
        const val MAX_LOCATION_AGE_MS = 3L * 60 * 60 * 1000 // 3 hours
    }
}
