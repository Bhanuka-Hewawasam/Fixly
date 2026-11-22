package lk.ac.nibm.hdse.fixly_20.viewmodel

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.google.android.gms.location.*
import com.google.firebase.firestore.GeoPoint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import lk.ac.nibm.hdse.fixly_20.data.repository.JobRepository
import lk.ac.nibm.hdse.fixly_20.model.JobPost

data class ProviderTrackingUiState(
    val job: JobPost? = null,
    val isSharingLocation: Boolean = false,
    val uploadingType: String? = null, // "before" or "after" while a photo upload is running
    val errorMessage: String? = null
)

class ProviderTrackingViewModel(
    private val jobId: String,
    private val jobRepository: JobRepository = JobRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProviderTrackingUiState())
    val uiState: StateFlow<ProviderTrackingUiState> = _uiState

    private var fusedLocationClient: FusedLocationProviderClient? = null
    private var locationCallback: LocationCallback? = null

    init {
        viewModelScope.launch {
            jobRepository.observeJob(jobId).collect { job ->
                _uiState.value = _uiState.value.copy(job = job)
            }
        }
    }

    @SuppressLint("MissingPermission") // permission is checked by the screen before calling this
    fun startSharingLocation(context: Context) {
        if (fusedLocationClient != null) return

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)

        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 10_000L).build()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val location = result.lastLocation ?: return
                viewModelScope.launch {
                    jobRepository.updateProviderLocation(
                        jobId,
                        GeoPoint(location.latitude, location.longitude)
                    )
                }
            }
        }

        fusedLocationClient?.requestLocationUpdates(request, locationCallback!!, null)
        _uiState.value = _uiState.value.copy(isSharingLocation = true)
    }

    fun stopSharingLocation() {
        locationCallback?.let { fusedLocationClient?.removeLocationUpdates(it) }
        fusedLocationClient = null
        locationCallback = null
        _uiState.value = _uiState.value.copy(isSharingLocation = false)
    }

    fun markInProgress() {
        viewModelScope.launch { jobRepository.updateJobStatus(jobId, "In Progress") }
    }

    /** Uploads a camera photo as work evidence. type is "before" or "after". */
    fun uploadEvidence(context: Context, photoUri: Uri, type: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(uploadingType = type, errorMessage = null)

            jobRepository.uploadEvidencePhoto(context, jobId, type, photoUri)
                .onSuccess {
                    _uiState.value = _uiState.value.copy(uploadingType = null)
                }
                .onFailure {
                    _uiState.value = _uiState.value.copy(
                        uploadingType = null,
                        errorMessage = "Photo upload failed. Check your connection and try again."
                    )
                }
        }
    }

    fun markCompleted() {
        // Proof of work is mandatory: no "after" photo, no completion
        if (_uiState.value.job?.afterPhotoUrl.isNullOrBlank()) {
            _uiState.value = _uiState.value.copy(
                errorMessage = "Take the 'after' photo first — it's your proof of completed work"
            )
            return
        }

        stopSharingLocation()
        viewModelScope.launch { jobRepository.updateJobStatus(jobId, "Completed") }
    }

    override fun onCleared() {
        super.onCleared()
        stopSharingLocation()
    }
}

class ProviderTrackingViewModelFactory(private val jobId: String) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return ProviderTrackingViewModel(jobId) as T
    }
}
