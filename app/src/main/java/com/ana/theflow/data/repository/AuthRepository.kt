// Handles sign-in, sign-up, sign-out, and password reset via Firebase Authentication.
package com.ana.theflow.data.repository

import com.ana.theflow.data.session.ActiveAccountHolder
import com.google.firebase.auth.FirebaseAuth

class AuthRepository {

    private val auth = FirebaseAuth.getInstance()

    // Signs in an existing user.
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

    // Creates a brand-new account.
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

    // Sends a password reset email. We always call onComplete the same way regardless of whether
    // the email actually matches an account, so someone can't use this to check if an address is registered.
    fun sendPasswordReset(email: String, onComplete: () -> Unit) {
        auth.sendPasswordResetEmail(email)
            .addOnCompleteListener { onComplete() }
    }

    // Signs out and also clears the cached recommendation data and active-account choice, so the next login starts fresh.
    fun logout() {
        DiscoveryRepository.resetForUser("")
        ActiveAccountHolder.clear()
        auth.signOut()
    }

    // Deletes the signed-in account.
    fun deleteCurrentUser() {
        auth.currentUser?.delete()
    }

    // Returns the signed-in user's uid, or null if nobody's signed in.
    fun getCurrentUserUid(): String? {
        return auth.currentUser?.uid
    }
}
