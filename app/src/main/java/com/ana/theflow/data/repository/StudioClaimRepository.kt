package com.ana.theflow.data.repository

import com.ana.theflow.utilities.Constants
import com.google.firebase.firestore.FirebaseFirestore

// Thin legacy facade kept only so existing UI call sites (DetailFragment) keep compiling.
// Actual request submission now goes through StudioRequestRepository; this class is
// removed entirely once callers are migrated to StudioRequestRepository directly (Phase 6).
class StudioClaimRepository {

    private val db = FirebaseFirestore.getInstance()
    private val studioRequestRepository = StudioRequestRepository()

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

    // Submits a claim request for a studio. Any signed-in dancer may request - permissions are
    // only ever granted by an admin approving the resulting request.
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

    data class StudioClaimState(
        val claimStatus: String = "",
        val ownerUid: String = ""
    )
}
