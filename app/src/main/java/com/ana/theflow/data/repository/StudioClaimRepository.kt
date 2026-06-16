package com.ana.theflow.data.repository

import com.ana.theflow.utilities.Constants
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore

class StudioClaimRepository {

    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()

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

    fun submitClaim(
        studioId: String,
        studioName: String,
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

        if (studioId.isBlank()) {
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

    private fun ensureNoPendingClaim(
        studioId: String,
        onSuccess: () -> Unit,
        onFailure: (String) -> Unit
    ) {
        db.collection(Constants.Collections.STUDIO_CLAIMS)
            .whereEqualTo("studioId", studioId)
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

    private fun createClaim(
        studioId: String,
        studioName: String,
        requesterUid: String,
        requesterEmail: String,
        requesterName: String,
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
            "justification" to justification,
            "verificationDetails" to verificationDetails,
            "status" to "PENDING",
            "createdAt" to FieldValue.serverTimestamp(),
            "reviewedAt" to null,
            "reviewedByUid" to "",
            "adminNote" to ""
        )

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

    private fun String.isStudioManagerRole(): Boolean {
        return equals(Constants.UserRole.STUDIO_MANAGER.name, ignoreCase = true) ||
            equals(Constants.UserRole.STUDIO_MANAGER.firestoreValue, ignoreCase = true)
    }

    data class StudioClaimState(
        val claimStatus: String = "",
        val ownerUid: String = ""
    )
}
