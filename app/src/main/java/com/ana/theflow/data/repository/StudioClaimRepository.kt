package com.ana.theflow.data.repository

import com.ana.theflow.utilities.Constants
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore

class StudioClaimRepository {

    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()

    // Loads the claim status for a studio.
    fun loadStudioClaimState(
        studioId: String,
        onSuccess: (StudioClaimState) -> Unit,
        onFailure: (String) -> Unit
    ) {
        if (studioId.isBlank()) {
            onFailure("Missing studio id")
            return
        }

        db.collection(Constants.Collections.STUDIOS)
            .document(studioId)
            .get()
            .addOnSuccessListener { document ->
                if (!document.exists()) {
                    onFailure("Studio was not found")
                    return@addOnSuccessListener
                }

                onSuccess(
                    StudioClaimState(
                        claimStatus = document.getString("claimStatus").orEmpty(),
                        ownerUid = document.getString("ownerUid").orEmpty()
                    )
                )
            }
            .addOnFailureListener { error ->
                onFailure(error.message ?: "Failed to load studio claim state")
            }
    }

    // Submits a claim request for a studio.
    fun submitClaim(
        studioId: String,
        studioName: String,
        googlePlaceId: String = "",
        address: String = "",
        justification: String,
        verificationDetails: String,
        onSuccess: () -> Unit,
        onFailure: (String) -> Unit
    ) {
        val currentUser = auth.currentUser
        val uid = currentUser?.uid
        if (uid.isNullOrBlank()) {
            onFailure("Please sign in before claiming a studio")
            return
        }

        if (studioId.isBlank() && googlePlaceId.isBlank()) {
            onFailure("Missing studio id")
            return
        }

        if (justification.isBlank() && verificationDetails.isBlank()) {
            onFailure("Please add a short explanation or verification detail")
            return
        }

        val userRef = db.collection(Constants.Collections.USERS).document(uid)
        val studioRef = db.collection(Constants.Collections.STUDIOS).document(studioId)

        userRef.get()
            .addOnSuccessListener { userDocument ->
                val role = userDocument.getString("role").orEmpty()
                if (!role.isStudioManagerRole()) {
                    onFailure("Only studio managers can claim studios")
                    return@addOnSuccessListener
                }

                if (googlePlaceId.isNotBlank() && studioId.startsWith("google_")) {
                    ensureNoPendingClaim(
                        studioId = studioId,
                        googlePlaceId = googlePlaceId,
                        onSuccess = {
                            createClaim(
                                studioId = studioId,
                                studioName = studioName,
                                requesterUid = uid,
                                requesterEmail = currentUser.email.orEmpty(),
                                requesterName = listOf(
                                    userDocument.getString("firstName").orEmpty(),
                                    userDocument.getString("lastName").orEmpty()
                                ).filter { it.isNotBlank() }.joinToString(" "),
                                googlePlaceId = googlePlaceId,
                                address = address,
                                justification = justification.trim(),
                                verificationDetails = verificationDetails.trim(),
                                onSuccess = onSuccess,
                                onFailure = onFailure
                            )
                        },
                        onFailure = onFailure
                    )
                    return@addOnSuccessListener
                }

                studioRef.get()
                    .addOnSuccessListener { studioDocument ->
                        if (!studioDocument.exists()) {
                            onFailure("Studio was not found")
                            return@addOnSuccessListener
                        }

                        val status = studioDocument.getString("status").orEmpty()
                        val claimStatus = studioDocument.getString("claimStatus").orEmpty()
                        val ownerUid = studioDocument.getString("ownerUid").orEmpty()
                        val isApproved = status.equals(Constants.StudioStatus.APPROVED.name, ignoreCase = true) ||
                            studioDocument.getBoolean("verified") == true

                        if (!isApproved) {
                            onFailure("Only approved studios can be claimed")
                            return@addOnSuccessListener
                        }

                        if (ownerUid.isNotBlank() || claimStatus.equals("CLAIMED", ignoreCase = true)) {
                            onFailure("This studio is already claimed")
                            return@addOnSuccessListener
                        }

                        if (claimStatus.equals("PENDING", ignoreCase = true)) {
                            onFailure("This studio already has a pending claim")
                            return@addOnSuccessListener
                        }

                        ensureNoPendingClaim(
                            studioId = studioId,
                            googlePlaceId = googlePlaceId,
                            onSuccess = {
                                createClaim(
                                    studioId = studioId,
                                    studioName = studioName,
                                    requesterUid = uid,
                                    requesterEmail = currentUser.email.orEmpty(),
                                    requesterName = listOf(
                                        userDocument.getString("firstName").orEmpty(),
                                        userDocument.getString("lastName").orEmpty()
                                    ).filter { it.isNotBlank() }.joinToString(" "),
                                    googlePlaceId = googlePlaceId,
                                    address = address,
                                    justification = justification.trim(),
                                    verificationDetails = verificationDetails.trim(),
                                    onSuccess = onSuccess,
                                    onFailure = onFailure
                                )
                            },
                            onFailure = onFailure
                        )
                    }
                    .addOnFailureListener { error ->
                        onFailure(error.message ?: "Failed to load studio")
                    }
            }
            .addOnFailureListener { error ->
                onFailure(error.message ?: "Failed to load user profile")
            }
    }

    // Checks that the studio has no pending claim.
    private fun ensureNoPendingClaim(
        studioId: String,
        googlePlaceId: String = "",
        onSuccess: () -> Unit,
        onFailure: (String) -> Unit
    ) {
        val field = if (googlePlaceId.isBlank()) "studioId" else "googlePlaceId"
        val value = if (googlePlaceId.isBlank()) studioId else googlePlaceId
        db.collection(Constants.Collections.STUDIO_CLAIMS)
            .whereEqualTo(field, value)
            .whereEqualTo("status", "PENDING")
            .limit(1)
            .get()
            .addOnSuccessListener { snapshot ->
                if (snapshot.isEmpty) {
                    onSuccess()
                } else {
                    onFailure("This studio already has a pending claim")
                }
            }
            .addOnFailureListener { error ->
                onFailure(error.message ?: "Failed to check pending claims")
            }
    }

    // Creates a studio claim and marks the studio as pending.
    private fun createClaim(
        studioId: String,
        studioName: String,
        requesterUid: String,
        requesterEmail: String,
        requesterName: String,
        googlePlaceId: String,
        address: String,
        justification: String,
        verificationDetails: String,
        onSuccess: () -> Unit,
        onFailure: (String) -> Unit
    ) {
        val claimRef = db.collection(Constants.Collections.STUDIO_CLAIMS).document()
        val claim = hashMapOf(
            "id" to claimRef.id,
            "studioId" to studioId,
            "studioName" to studioName,
            "requesterUid" to requesterUid,
            "requesterEmail" to requesterEmail,
            "requesterName" to requesterName,
            "googlePlaceId" to googlePlaceId,
            "externalSource" to if (googlePlaceId.isBlank()) "" else "google",
            "address" to address,
            "justification" to justification,
            "verificationDetails" to verificationDetails,
            "status" to "PENDING",
            "createdAt" to FieldValue.serverTimestamp(),
            "reviewedAt" to null,
            "reviewedByUid" to "",
            "adminNote" to ""
        )

        if (googlePlaceId.isNotBlank() && studioId.startsWith("google_")) {
            claimRef.set(claim)
                .addOnSuccessListener { onSuccess() }
                .addOnFailureListener { error ->
                    onFailure(error.message ?: "Failed to submit claim")
                }
            return
        }

        db.runBatch { batch ->
            batch.set(claimRef, claim)
            batch.update(
                db.collection(Constants.Collections.STUDIOS).document(studioId),
                mapOf(
                    "claimStatus" to "PENDING",
                    "claimUpdatedAt" to Timestamp.now()
                )
            )
        }
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { error ->
                onFailure(error.message ?: "Failed to submit claim")
            }
    }

    // Checks whether a role value is a studio manager role.
    private fun String.isStudioManagerRole(): Boolean {
        return equals(Constants.UserRole.STUDIO_MANAGER.name, ignoreCase = true) ||
            equals(Constants.UserRole.STUDIO_MANAGER.firestoreValue, ignoreCase = true)
    }

    data class StudioClaimState(
        val claimStatus: String = "",
        val ownerUid: String = ""
    )
}
