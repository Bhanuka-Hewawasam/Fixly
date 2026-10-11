package lk.ac.nibm.hdse.fixly_20.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.GeoPoint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import lk.ac.nibm.hdse.fixly_20.data.repository.EmergencyRepository
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

    private fun applyFilters(state: EmergencyUiState): EmergencyUiState {
        val myLocation = state.customerLocation ?: return state.copy(results = emptyList())
        val myUid = firebaseAuth.currentUser?.uid
        val now = System.currentTimeMillis()

        val results = state.allProviders.mapNotNull { provider ->
            if (provider.uid == myUid) return@mapNotNull null // don't show yourself to yourself

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
