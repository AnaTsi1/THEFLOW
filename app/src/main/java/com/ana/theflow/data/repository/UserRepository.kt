package com.ana.theflow.data.repository

import com.ana.theflow.data.model.user.User
import com.ana.theflow.utilities.Constants
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class UserRepository {

    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()

    fun createUserProfile(
        firstName: String,
        lastName: String,
        email: String,
        role: Constants.UserRole,
        onSuccess: () -> Unit,
        onFailure: (String) -> Unit
    ) {
        val uid = auth.currentUser?.uid
        if (uid == null) {
            onFailure("User is not logged in")
            return
        }

        val user = User(
            uid = uid,
            firstName = firstName,
            lastName = lastName,
            email = email,
            role = role.firestoreValue,
            onboardingCompleted = false
        )

        db.collection(Constants.Collections.USERS)
            .document(uid)
            .set(user)
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { error ->
                onFailure(error.message ?: "Failed to save user")
            }
    }

    fun getUserByUid(
        uid: String,
        onSuccess: (User) -> Unit,
        onFailure: (String) -> Unit
    ) {
        db.collection(Constants.Collections.USERS)
            .document(uid)
            .get()
            .addOnSuccessListener { document ->
                val user = document.toObject(User::class.java)
                if (user != null) {
                    onSuccess(user.copy(uid = document.id))
                } else {
                    onFailure("User not found")
                }
            }
            .addOnFailureListener { error ->
                onFailure(error.message ?: "Failed to load user")
            }
    }

    fun saveOnboardingPreferences(
        styles: List<String>,
        level: String,
        location: String,
        onSuccess: () -> Unit,
        onFailure: (String) -> Unit
    ) {
        val uid = auth.currentUser?.uid
        if (uid == null) {
            onFailure("User is not logged in")
            return
        }

        val updates = mapOf(
            "danceStyles" to styles,
            "danceLevel" to level,
            "location" to location,
            "onboardingCompleted" to true
        )

        db.collection(Constants.Collections.USERS)
            .document(uid)
            .update(updates)
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { error ->
                onFailure(error.message ?: "Failed to save onboarding")
            }
    }

    fun updateUserProfile(
        uid: String,
        firstName: String,
        lastName: String,
        birthDate: String,
        age: Int,
        headline: String,
        bio: String,
        location: String,
        danceStyles: List<String>,
        danceLevel: String,
        yearsOfExperience: String,
        studiosTrainedAt: List<String>,
        teachersLearnedFrom: List<String>,
        performancesCompetitions: List<String>,
        availability: String,
        instagramUrl: String,
        tiktokUrl: String,
        youtubeUrl: String,
        onSuccess: () -> Unit,
        onFailure: (String) -> Unit
    ) {
        if (uid.isBlank()) {
            onFailure("Missing user id")
            return
        }

        val updates = mapOf(
            "firstName" to firstName,
            "lastName" to lastName,
            "birthDate" to birthDate,
            "age" to age,
            "headline" to headline,
            "bio" to bio,
            "location" to location,
            "danceStyles" to danceStyles,
            "danceLevel" to danceLevel,
            "yearsOfExperience" to yearsOfExperience,
            "studiosTrainedAt" to studiosTrainedAt,
            "teachersLearnedFrom" to teachersLearnedFrom,
            "performancesCompetitions" to performancesCompetitions,
            "availability" to availability,
            "instagramUrl" to instagramUrl,
            "tiktokUrl" to tiktokUrl,
            "youtubeUrl" to youtubeUrl
        )

        db.collection(Constants.Collections.USERS)
            .document(uid)
            .update(updates)
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { error ->
                onFailure(error.message ?: "Failed to update profile")
            }
    }
}
