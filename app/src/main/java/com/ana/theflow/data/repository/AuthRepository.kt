package com.ana.theflow.data.repository

import com.google.firebase.auth.FirebaseAuth

class AuthRepository {

    private val auth = FirebaseAuth.getInstance()

    // Signs in a user with email and password.
    fun login(
        email: String,
        password: String,
        onSuccess: () -> Unit,
        onFailure: (String) -> Unit
    ) {
        auth.signInWithEmailAndPassword(email, password)
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { error ->
                onFailure(error.message ?: "Login failed")
            }
    }

    // Creates a new user account.
    fun register(
        email: String,
        password: String,
        onSuccess: () -> Unit,
        onFailure: (String) -> Unit
    ) {
        auth.createUserWithEmailAndPassword(email, password)
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { error ->
                onFailure(error.message ?: "Registration failed")
            }
    }

    // Signs out the current user and returns to login.
    fun logout() {
        auth.signOut()
    }

    // Deletes the currently signed-in auth user.
    fun deleteCurrentUser() {
        auth.currentUser?.delete()
    }

    // Returns the signed-in user id.
    fun getCurrentUserUid(): String? {
        return auth.currentUser?.uid
    }
}
