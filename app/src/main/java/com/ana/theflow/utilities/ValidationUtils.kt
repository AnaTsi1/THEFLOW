package com.ana.theflow.utilities

object ValidationUtils {

    fun isEmailValid(email: String): Boolean {
        return email.isNotBlank() && android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()
    }

    fun isPasswordValid(password: String): Boolean {
        return password.length >= MIN_PASSWORD_LENGTH
    }

    private const val MIN_PASSWORD_LENGTH = 6
}
