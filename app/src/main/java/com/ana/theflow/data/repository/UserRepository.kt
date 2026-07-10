package com.ana.theflow.data.repository

import com.ana.theflow.data.model.user.User
import com.ana.theflow.utilities.CityOptions
import com.ana.theflow.utilities.Constants
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions

class UserRepository {

    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()

    // Creates a Firestore profile for a new user.
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

    // Loads a user profile by uid.
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

    // Saves onboarding preferences.
    fun saveOnboardingPreferences(
        styles: List<String>,
        level: String,
        location: String,
        preferredStudios: List<String> = emptyList(),
        preferredTeachers: List<String> = emptyList(),
        preferredDancers: List<String> = emptyList(),
        onSuccess: () -> Unit,
        onFailure: (String) -> Unit
    ) {
        val uid = auth.currentUser?.uid
        if (uid == null) {
            onFailure("User is not logged in")
            return
        }

        val normalizedLocation = CityOptions.normalizeCity(location)
        val updates = mapOf(
            "onboardingCompleted" to true
        )

        val recommendationUpdates = mapOf(
            "preferredStyles" to styles,
            "preferredLevel" to level,
            "preferredLocation" to normalizedLocation,
            "preferredStudios" to preferredStudios,
            "preferredTeachers" to preferredTeachers,
            "preferredDancers" to preferredDancers,
            "updatedAt" to FieldValue.serverTimestamp()
        )

        db.runBatch { batch ->
            batch.update(
                db.collection(Constants.Collections.USERS).document(uid),
                updates
            )
            batch.set(
                db.collection(Constants.Collections.USERS)
                    .document(uid)
                    .collection("recommendationProfile")
                    .document("main"),
                recommendationUpdates,
                SetOptions.merge()
            )
        }
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { error ->
                onFailure(error.message ?: "Failed to save onboarding")
            }
    }

    // Loads private feed preferences for the current user.
    fun loadPreferenceSettings(
        onSuccess: (PreferenceSettings) -> Unit,
        onFailure: (String) -> Unit
    ) {
        val uid = auth.currentUser?.uid
        if (uid == null) {
            onFailure("User is not logged in")
            return
        }

        val profileRef = db.collection(Constants.Collections.USERS)
            .document(uid)
            .collection("recommendationProfile")
            .document("main")

        profileRef.get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    onSuccess(
                        PreferenceSettings(
                            styles = stringList(document.get("preferredStyles")),
                            level = document.getString("preferredLevel").orEmpty(),
                            location = document.getString("preferredLocation").orEmpty(),
                            preferredStudios = stringList(document.get("preferredStudios")),
                            preferredTeachers = stringList(document.get("preferredTeachers")),
                            preferredDancers = stringList(document.get("preferredDancers"))
                        )
                    )
                    return@addOnSuccessListener
                }

                getUserByUid(
                    uid = uid,
                    onSuccess = { user ->
                        onSuccess(
                            PreferenceSettings(
                                styles = user.danceStyles,
                                level = user.danceLevel,
                                location = user.location
                            )
                        )
                    },
                    onFailure = onFailure
                )
            }
            .addOnFailureListener { error ->
                onFailure(error.message ?: "Failed to load preferences")
            }
    }

    // Updates recommendation preferences for the current user.
    fun updatePreferenceSettings(
        styles: List<String>,
        level: String,
        location: String,
        preferredStudios: List<String>,
        preferredTeachers: List<String>,
        preferredDancers: List<String>,
        onSuccess: () -> Unit,
        onFailure: (String) -> Unit
    ) {
        val uid = auth.currentUser?.uid
        if (uid == null) {
            onFailure("User is not logged in")
            return
        }

        val normalizedLocation = CityOptions.normalizeCity(location)
        val recommendationUpdates = mapOf(
            "preferredStyles" to styles,
            "preferredLevel" to level,
            "preferredLocation" to normalizedLocation,
            "preferredStudios" to preferredStudios,
            "preferredTeachers" to preferredTeachers,
            "preferredDancers" to preferredDancers,
            "updatedAt" to FieldValue.serverTimestamp()
        )

        db.collection(Constants.Collections.USERS)
            .document(uid)
            .collection("recommendationProfile")
            .document("main")
            .set(recommendationUpdates, SetOptions.merge())
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { error ->
                onFailure(error.message ?: "Failed to update preferences")
            }
    }

    // Updates profile fields for a user.
    fun updateUserProfile(
        uid: String,
        firstName: String,
        lastName: String,
        birthDate: String,
        age: Int,
        headline: String,
        bio: String,
        professionalBackground: String,
        skills: List<String>,
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
            "professionalBackground" to professionalBackground,
            "skills" to skills,
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

    // Converts Firestore list values into a list of strings.
    private fun stringList(value: Any?): List<String> {
        return (value as? List<*>)
            ?.mapNotNull { it as? String }
            ?.filter { it.isNotBlank() }
            .orEmpty()
    }

    data class PreferenceSettings(
        val styles: List<String> = emptyList(),
        val level: String = "",
        val location: String = "",
        val preferredStudios: List<String> = emptyList(),
        val preferredTeachers: List<String> = emptyList(),
        val preferredDancers: List<String> = emptyList()
    )
}
