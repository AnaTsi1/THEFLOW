package com.ana.theflow.ui.auth

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.ana.theflow.data.repository.AuthRepository
import com.ana.theflow.data.repository.UserRepository
import com.ana.theflow.utilities.Constants
import com.ana.theflow.utilities.ValidationUtils

class AuthViewModel : ViewModel() {

    private val authRepository = AuthRepository()
    private val userRepository = UserRepository()

    private val _uiState = MutableLiveData(AuthUiState())
    val uiState: LiveData<AuthUiState> get() = _uiState

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
                    role = Constants.UserRole.DANCER,
                    onSuccess = onSuccess
                )
            },
            onFailure = { error ->
                _uiState.value = AuthUiState(errorMessage = error)
            }
        )
    }

    fun getCurrentUserUid(): String? {
        return authRepository.getCurrentUserUid()
    }

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

    fun clearError() {
        _uiState.value = _uiState.value?.copy(errorMessage = null) ?: AuthUiState()
    }

    private fun createUserProfile(
        firstName: String,
        lastName: String,
        email: String,
        role: Constants.UserRole,
        onSuccess: () -> Unit
    ) {
        userRepository.createUserProfile(
            firstName = firstName,
            lastName = lastName,
            email = email,
            role = role,
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

data class AuthUiState(
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)
