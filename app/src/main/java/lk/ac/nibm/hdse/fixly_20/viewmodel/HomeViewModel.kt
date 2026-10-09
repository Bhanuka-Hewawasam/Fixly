package lk.ac.nibm.hdse.fixly_20.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import lk.ac.nibm.hdse.fixly_20.data.repository.AuthRepository
import lk.ac.nibm.hdse.fixly_20.data.repository.UserRepository
import lk.ac.nibm.hdse.fixly_20.model.User

data class HomeUiState(
    val isLoading: Boolean = true,
    val user: User? = null,
    val activeRole: String = "customer", // which tab/view is currently shown
    val errorMessage: String? = null
)

class HomeViewModel(
    private val userRepository: UserRepository = UserRepository(),
    private val authRepository: AuthRepository = AuthRepository()
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
                }
                .onFailure { exception ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = exception.localizedMessage ?: "Could not load profile"
                    )
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

    fun logout() {
        authRepository.logout()
    }
}
