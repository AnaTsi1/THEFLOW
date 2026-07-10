package com.ana.theflow.utilities

object ValidationUtils {

    // Checks whether an email address looks valid.
    fun isEmailValid(email: String): Boolean {
        return email.isNotBlank() && android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()
    }

    // Checks whether a password meets the minimum length.
    fun isPasswordValid(password: String): Boolean {
        return password.length >= MIN_PASSWORD_LENGTH
    }

    private const val MIN_PASSWORD_LENGTH = 6
}
