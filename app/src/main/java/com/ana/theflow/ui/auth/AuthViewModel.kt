// Backs the login and register screens: validates input, talks to AuthRepository/UserRepository,
// and exposes loading/error state as LiveData for the fragments to observe.
package com.ana.theflow.ui.auth

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.ana.theflow.data.repository.AuthRepository
import com.ana.theflow.data.repository.UserRepository
import com.ana.theflow.utilities.ValidationUtils

class AuthViewModel : ViewModel() {

    private val authRepository = AuthRepository()
    private val userRepository = UserRepository()

    private val _uiState = MutableLiveData(AuthUiState())
    val uiState: LiveData<AuthUiState> get() = _uiState

    // Signs in a user with email and password. There is no role selector - permissions are
    // additive and derived from the stored account, never chosen at login.
    fun login(
        email: String,
        password: String,
        onSuccess: () -> Unit
    ) {
        if (!validateLogin(email, password)) return

        _uiState.value = AuthUiState(isLoading = true)

        authRepository.login(
            email = email,
            password = password,
            onSuccess = {
                _uiState.value = AuthUiState()
                onSuccess()
            },
            onFailure = { error ->
                _uiState.value = AuthUiState(errorMessage = error)
            }
        )
    }

    // Creates a new user account. Every new account is simply a regular dancer - professional
    // and studio-manager permissions are always granted later by an admin.
    fun register(
        firstName: String,
        lastName: String,
        email: String,
        password: String,
        onSuccess: () -> Unit
    ) {
        if (!validateRegister(firstName, lastName, email, password)) return

        _uiState.value = AuthUiState(isLoading = true)

        authRepository.register(
            email = email,
            password = password,
            onSuccess = {
                createUserProfile(
                    firstName = firstName,
                    lastName = lastName,
                    email = email,
                    onSuccess = onSuccess
                )
            },
            onFailure = { error ->
                _uiState.value = AuthUiState(errorMessage = error)
            }
        )
    }

    // Returns the signed-in user id.
    fun getCurrentUserUid(): String? {
        return authRepository.getCurrentUserUid()
    }

    // Loads the signed-in user profile.
    fun loadCurrentUser(
        onSuccess: (com.ana.theflow.data.model.user.User) -> Unit,
        onFailure: (String) -> Unit
    ) {
        val uid = authRepository.getCurrentUserUid()
        if (uid == null) {
            onFailure("User is not logged in")
            return
        }

        _uiState.value = AuthUiState(isLoading = true)
        userRepository.getUserByUid(
            uid = uid,
            onSuccess = { user ->
                _uiState.value = AuthUiState()
                onSuccess(user)
            },
            onFailure = { error ->
                _uiState.value = AuthUiState(errorMessage = error)
                onFailure(error)
            }
        )
    }

    // Clears the current authentication error.
    fun clearError() {
        _uiState.value = _uiState.value?.copy(errorMessage = null) ?: AuthUiState()
    }

    // Requests a password reset email. Reports the same message on success or failure so the
    // result never reveals whether the address belongs to a real account.
    fun sendPasswordReset(email: String, onResult: (String) -> Unit) {
        if (!ValidationUtils.isEmailValid(email)) {
            onResult("Please enter a valid email address")
            return
        }
        authRepository.sendPasswordReset(email) {
            onResult("If an account exists for this email, a password reset link has been sent.")
        }
    }

    // Creates a Firestore profile for a new user.
    private fun createUserProfile(
        firstName: String,
        lastName: String,
        email: String,
        onSuccess: () -> Unit
    ) {
        userRepository.createUserProfile(
            firstName = firstName,
            lastName = lastName,
            email = email,
            onSuccess = {
                _uiState.value = AuthUiState()
                onSuccess()
            },
            onFailure = { error ->
                authRepository.deleteCurrentUser()
                _uiState.value = AuthUiState(errorMessage = error)
            }
        )
    }

    // Checks that login input is valid.
    private fun validateLogin(email: String, password: String): Boolean {
        return when {
            !ValidationUtils.isEmailValid(email) -> {
                _uiState.value = AuthUiState(errorMessage = "Invalid email address")
                false
            }

            password.isBlank() -> {
                _uiState.value = AuthUiState(errorMessage = "Please enter a password")
                false
            }

            else -> true
        }
    }

    // Checks that registration input is valid.
    private fun validateRegister(
        firstName: String,
        lastName: String,
        email: String,
        password: String
    ): Boolean {
        return when {
            firstName.isBlank() || lastName.isBlank() -> {
                _uiState.value = AuthUiState(errorMessage = "Please enter first and last name")
                false
            }

            !ValidationUtils.isEmailValid(email) -> {
                _uiState.value = AuthUiState(errorMessage = "Invalid email address")
                false
            }

            !ValidationUtils.isPasswordValid(password) -> {
                _uiState.value = AuthUiState(errorMessage = "Password must contain at least 6 characters")
                false
            }

            else -> true
        }
    }
}

// What the login/register screens need to render: a spinner while a request is in flight, and
// an error message to show if the last attempt failed.
data class AuthUiState(
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)
