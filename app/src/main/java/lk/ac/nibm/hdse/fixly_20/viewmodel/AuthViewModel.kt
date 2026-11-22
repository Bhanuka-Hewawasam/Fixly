package lk.ac.nibm.hdse.fixly_20.viewmodel

import android.content.Context
import android.net.Uri
import android.os.SystemClock
import android.util.Patterns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import lk.ac.nibm.hdse.fixly_20.data.repository.AuthRepository

data class AuthUiState(
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val isLoginSuccessful: Boolean = false,
    val isRegisterSuccessful: Boolean = false,
    val needsProfileCompletion: Boolean = false, // true = new Google user, must pick role/category
    val isProfileCompletionSuccessful: Boolean = false,
    val isPasswordResetLoading: Boolean = false,
    val passwordResetMessage: String? = null,
    val passwordResetError: String? = null,
    val passwordResetCooldownUntil: Long = 0L
)

class AuthViewModel(
    private val repository: AuthRepository = AuthRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState

    fun login(email: String, password: String) {
        if (email.isBlank() || password.isBlank()) {
            _uiState.value = _uiState.value.copy(errorMessage = "Email and password can't be empty")
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            repository.login(email, password)
                .onSuccess {
                    _uiState.value = _uiState.value.copy(isLoading = false, isLoginSuccessful = true)
                }
                .onFailure { exception ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = exception.localizedMessage ?: "Login failed. Please try again."
                    )
                }
        }
    }

    fun sendPasswordResetEmail(email: String) {
        val normalizedEmail = email.trim()
        if (normalizedEmail.isBlank() || !Patterns.EMAIL_ADDRESS.matcher(normalizedEmail).matches()) {
            _uiState.value = _uiState.value.copy(
                passwordResetMessage = null,
                passwordResetError = "Enter a valid email address"
            )
            return
        }

        if (_uiState.value.isPasswordResetLoading ||
            SystemClock.elapsedRealtime() < _uiState.value.passwordResetCooldownUntil
        ) return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isPasswordResetLoading = true,
                passwordResetMessage = null,
                passwordResetError = null
            )
            repository.sendPasswordResetEmail(normalizedEmail)
                .onSuccess {
                    _uiState.value = _uiState.value.copy(
                        isPasswordResetLoading = false,
                        passwordResetMessage = "If an account exists for this email, a reset link has been sent.",
                        passwordResetError = null,
                        passwordResetCooldownUntil = SystemClock.elapsedRealtime() + 30_000L
                    )
                }
                .onFailure {
                    _uiState.value = _uiState.value.copy(
                        isPasswordResetLoading = false,
                        passwordResetMessage = null,
                        passwordResetError = "Check your internet connection and try again"
                    )
                }
        }
    }

    fun clearPasswordResetResult() {
        _uiState.value = _uiState.value.copy(
            passwordResetMessage = null,
            passwordResetError = null
        )
    }

    fun register(
        context: Context,
        name: String,
        email: String,
        phoneNumber: String,
        password: String,
        confirmPassword: String,
        roles: List<String>,
        serviceCategories: List<String>,
        certificateUri: Uri?
    ) {
        when {
            name.isBlank() || email.isBlank() || phoneNumber.isBlank() || password.isBlank() -> {
                _uiState.value = _uiState.value.copy(errorMessage = "Please fill in all fields")
                return
            }
            phoneNumber.length != 10 -> {
                _uiState.value = _uiState.value.copy(errorMessage = "Phone number must be exactly 10 digits")
                return
            }
            password != confirmPassword -> {
                _uiState.value = _uiState.value.copy(errorMessage = "Passwords don't match")
                return
            }
            roles.isEmpty() -> {
                _uiState.value = _uiState.value.copy(errorMessage = "Select at least one role")
                return
            }
            roles.contains("provider") && serviceCategories.isEmpty() -> {
                _uiState.value = _uiState.value.copy(errorMessage = "Select at least one service category")
                return
            }
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            repository.register(context, name, email, phoneNumber, password, roles, serviceCategories, certificateUri)
                .onSuccess {
                    _uiState.value = _uiState.value.copy(isLoading = false, isRegisterSuccessful = true)
                }
                .onFailure { exception ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = exception.localizedMessage ?: "Registration failed. Please try again."
                    )
                }
        }
    }

    /** Called after Google's own sign-in UI returns an ID token. */
    fun signInWithGoogle(idToken: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)

            repository.signInWithGoogle(idToken)
                .onSuccess { isNewUser ->
                    _uiState.value = if (isNewUser) {
                        _uiState.value.copy(isLoading = false, needsProfileCompletion = true)
                    } else {
                        _uiState.value.copy(isLoading = false, isLoginSuccessful = true)
                    }
                }
                .onFailure { exception ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = exception.localizedMessage ?: "Google sign-in failed"
                    )
                }
        }
    }

    /** Called from the Complete Profile screen for a first-time Google user. */
    fun completeGoogleProfile(
        context: Context,
        phoneNumber: String,
        roles: List<String>,
        serviceCategories: List<String>,
        certificateUri: Uri?
    ) {
        if (phoneNumber.isBlank()) {
            _uiState.value = _uiState.value.copy(errorMessage = "Please enter your phone number")
            return
        }
        if (phoneNumber.length != 10) {
            _uiState.value = _uiState.value.copy(errorMessage = "Phone number must be exactly 10 digits")
            return
        }
        if (roles.contains("provider") && serviceCategories.isEmpty()) {
            _uiState.value = _uiState.value.copy(errorMessage = "Select at least one service category")
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            repository.completeGoogleProfile(context, phoneNumber, roles, serviceCategories, certificateUri)
                .onSuccess {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        isProfileCompletionSuccessful = true
                    )
                }
                .onFailure { exception ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = exception.localizedMessage ?: "Could not save profile"
                    )
                }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }
}
