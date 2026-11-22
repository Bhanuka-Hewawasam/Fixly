package lk.ac.nibm.hdse.fixly_20.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.GeoPoint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import lk.ac.nibm.hdse.fixly_20.data.repository.JobRepository
import lk.ac.nibm.hdse.fixly_20.data.repository.UserRepository

data class PostJobUiState(
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val isPostSuccessful: Boolean = false,
    val capturedLocation: GeoPoint? = null,
    val locationAddress: String? = null,
    val isFetchingLocation: Boolean = false
)

class PostJobViewModel(
    private val jobRepository: JobRepository = JobRepository(),
    private val userRepository: UserRepository = UserRepository(),
    private val firebaseAuth: FirebaseAuth = FirebaseAuth.getInstance()
) : ViewModel() {

    private val _uiState = MutableStateFlow(PostJobUiState())
    val uiState: StateFlow<PostJobUiState> = _uiState

    fun setLocation(geoPoint: GeoPoint, address: String) {
        _uiState.value = _uiState.value.copy(
            capturedLocation = geoPoint,
            locationAddress = address,
            isFetchingLocation = false
        )
    }

    fun setFetchingLocation(isFetching: Boolean) {
        _uiState.value = _uiState.value.copy(isFetchingLocation = isFetching)
    }

    fun postJob(
        context: Context,
        category: String,
        description: String,
        preferredDate: String,
        preferredTime: String,
        isEmergency: Boolean,
        address: String,
        imageUris: List<Uri>
    ) {
        when {
            category.isBlank() -> {
                _uiState.value = _uiState.value.copy(errorMessage = "Please select a service category")
                return
            }
            description.isBlank() -> {
                _uiState.value = _uiState.value.copy(errorMessage = "Please describe the problem")
                return
            }
            _uiState.value.capturedLocation == null -> {
                _uiState.value = _uiState.value.copy(errorMessage = "Please choose a job location using current location or the map")
                return
            }
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)

            // We need the customer's name from their Firestore profile for display on bids
            val profileResult = userRepository.getCurrentUserProfile()
            val customerName = profileResult.getOrNull()?.name ?: "Customer"

            val result = jobRepository.postJob(
                context = context,
                customerName = customerName,
                category = category,
                description = description,
                preferredDate = preferredDate,
                preferredTime = preferredTime,
                isEmergency = isEmergency,
                location = _uiState.value.capturedLocation,
                address = address,
                imageUris = imageUris
            )

            result.onSuccess {
                _uiState.value = _uiState.value.copy(isLoading = false, isPostSuccessful = true)
            }.onFailure { exception ->
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = exception.localizedMessage ?: "Failed to post job"
                )
            }
        }
    }
}
