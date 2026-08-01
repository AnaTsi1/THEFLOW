// Thin wrapper around a studio's claim status, used by screens that just need to know whether a
// studio is claimed/pending/available - actual claim submission is handled by StudioRequestRepository.
package com.ana.theflow.data.repository

import com.ana.theflow.utilities.Constants
import com.google.firebase.firestore.FirebaseFirestore

class StudioClaimRepository {

    private val db = FirebaseFirestore.getInstance()
    private val studioRequestRepository = StudioRequestRepository()

    // Loads a studio's current claim status and who owns it, if anyone.
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

    // Submits a claim request. Anyone signed in can request a claim, but an admin still has to approve it before they actually get any permissions.
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
        studioRequestRepository.submitClaimRequest(
            studioId = studioId,
            studioName = studioName,
            googlePlaceId = googlePlaceId,
            address = address,
            justification = justification,
            verificationDetails = verificationDetails,
            onSuccess = { onSuccess() },
            onFailure = onFailure
        )
    }

    // A studio's claim status and current owner, if it has one.
    data class StudioClaimState(
        val claimStatus: String = "",
        val ownerUid: String = ""
    )
}
