package com.ana.theflow.data.repository

import com.ana.theflow.data.model.studio.StudioRequest
import com.ana.theflow.utilities.Constants
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query

// Handles user-submitted requests to create a new studio or claim an existing one. Any signed-in
// dancer may submit either request - studio permissions only ever come from an admin approving one.
class StudioRequestRepository {

    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()

    // Requests a brand-new studio business account.
    fun submitCreateRequest(
        displayName: String,
        city: String,
        address: String,
        bio: String,
        danceStyles: List<String>,
        websiteUrl: String,
        contactPhone: String,
        contactEmail: String,
        socialLinks: Map<String, String>,
        justification: String,
        onSuccess: (String) -> Unit,
        onFailure: (String) -> Unit
    ) {
        val currentUser = auth.currentUser
        val uid = currentUser?.uid
        if (uid.isNullOrBlank()) {
            onFailure("Please sign in before requesting a studio")
            return
        }
        if (displayName.isBlank() || city.isBlank()) {
            onFailure("Add a studio name and city")
            return
        }

        loadRequesterName(uid) { requesterName ->
            val requestRef = db.collection(Constants.Collections.STUDIO_REQUESTS).document()
            val request = mapOf(
                "requestId" to requestRef.id,
                "type" to StudioRequest.TYPE_CREATE,
                "status" to StudioRequest.STATUS_PENDING,
                "requesterUid" to uid,
                "requesterName" to requesterName,
                "requesterEmail" to currentUser.email.orEmpty(),
                "justification" to justification.trim(),
                "verificationDetails" to "",
                "adminNote" to "",
                "reviewedByUid" to "",
                "studioId" to "",
                "studioName" to "",
                "googlePlaceId" to "",
                "externalSource" to "",
                "address" to "",
                "draftDisplayName" to displayName.trim(),
                "draftCity" to city.trim(),
                "draftAddress" to address.trim(),
                "draftBio" to bio.trim(),
                "draftDanceStyles" to danceStyles.filter { it.isNotBlank() },
                "draftWebsiteUrl" to websiteUrl.trim(),
                "draftContactPhone" to contactPhone.trim(),
                "draftContactEmail" to contactEmail.trim(),
                "draftSocialLinks" to socialLinks.filterValues { it.isNotBlank() },
                "resultStudioId" to "",
                "createdAt" to FieldValue.serverTimestamp(),
                "reviewedAt" to null
            )
            requestRef.set(request)
                .addOnSuccessListener { onSuccess(requestRef.id) }
                .addOnFailureListener { error -> onFailure(error.message ?: "Failed to submit request") }
        }
    }

    // Requests ownership of an existing (internal or Google-sourced) studio listing.
    fun submitClaimRequest(
        studioId: String,
        studioName: String,
        googlePlaceId: String = "",
        address: String = "",
        justification: String,
        verificationDetails: String,
        onSuccess: (String) -> Unit,
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

        loadRequesterName(uid) { requesterName ->
            val isExternalClaim = googlePlaceId.isNotBlank() && studioId.startsWith("google_")
            if (isExternalClaim) {
                ensureNoPendingClaim(studioId = studioId, googlePlaceId = googlePlaceId,
                    onSuccess = {
                        createClaimRequest(
                            studioId = studioId, studioName = studioName, requesterUid = uid,
                            requesterEmail = currentUser.email.orEmpty(), requesterName = requesterName,
                            googlePlaceId = googlePlaceId, address = address,
                            justification = justification.trim(), verificationDetails = verificationDetails.trim(),
                            onSuccess = onSuccess, onFailure = onFailure
                        )
                    },
                    onFailure = onFailure
                )
                return@loadRequesterName
            }

            db.collection(Constants.Collections.STUDIOS).document(studioId).get()
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

                    ensureNoPendingClaim(studioId = studioId, googlePlaceId = googlePlaceId,
                        onSuccess = {
                            createClaimRequest(
                                studioId = studioId, studioName = studioName, requesterUid = uid,
                                requesterEmail = currentUser.email.orEmpty(), requesterName = requesterName,
                                googlePlaceId = googlePlaceId, address = address,
                                justification = justification.trim(), verificationDetails = verificationDetails.trim(),
                                onSuccess = onSuccess, onFailure = onFailure
                            )
                        },
                        onFailure = onFailure
                    )
                }
                .addOnFailureListener { error -> onFailure(error.message ?: "Failed to load studio") }
        }
    }

    // Loads requests the signed-in user has submitted.
    fun loadMyRequests(onSuccess: (List<StudioRequest>) -> Unit, onFailure: (String) -> Unit) {
        val uid = auth.currentUser?.uid
        if (uid == null) {
            onFailure("User is not logged in")
            return
        }
        db.collection(Constants.Collections.STUDIO_REQUESTS)
            .whereEqualTo("requesterUid", uid)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .get()
            .addOnSuccessListener { snapshot ->
                onSuccess(snapshot.documents.mapNotNull { document ->
                    document.toObject(StudioRequest::class.java)
                        ?.copy(requestId = document.id, sourceCollection = StudioRequest.SOURCE_STUDIO_REQUESTS)
                })
            }
            .addOnFailureListener { error -> onFailure(error.message ?: "Failed to load your requests") }
    }

    // Checks whether a studio already has a pending claim request.
    fun loadPendingRequestForStudio(
        studioId: String,
        onSuccess: (StudioRequest?) -> Unit,
        onFailure: (String) -> Unit
    ) {
        db.collection(Constants.Collections.STUDIO_REQUESTS)
            .whereEqualTo("studioId", studioId)
            .whereEqualTo("status", StudioRequest.STATUS_PENDING)
            .limit(1)
            .get()
            .addOnSuccessListener { snapshot ->
                val document = snapshot.documents.firstOrNull()
                onSuccess(
                    document?.toObject(StudioRequest::class.java)
                        ?.copy(requestId = document.id, sourceCollection = StudioRequest.SOURCE_STUDIO_REQUESTS)
                )
            }
            .addOnFailureListener { error -> onFailure(error.message ?: "Failed to check pending requests") }
    }

    private fun ensureNoPendingClaim(
        studioId: String,
        googlePlaceId: String = "",
        onSuccess: () -> Unit,
        onFailure: (String) -> Unit
    ) {
        val field = if (googlePlaceId.isBlank()) "studioId" else "googlePlaceId"
        val value = if (googlePlaceId.isBlank()) studioId else googlePlaceId
        db.collection(Constants.Collections.STUDIO_REQUESTS)
            .whereEqualTo(field, value)
            .whereEqualTo("status", StudioRequest.STATUS_PENDING)
            .limit(1)
            .get()
            .addOnSuccessListener { snapshot ->
                if (snapshot.isEmpty) onSuccess() else onFailure("This studio already has a pending claim")
            }
            .addOnFailureListener { error -> onFailure(error.message ?: "Failed to check pending claims") }
    }

    private fun createClaimRequest(
        studioId: String,
        studioName: String,
        requesterUid: String,
        requesterEmail: String,
        requesterName: String,
        googlePlaceId: String,
        address: String,
        justification: String,
        verificationDetails: String,
        onSuccess: (String) -> Unit,
        onFailure: (String) -> Unit
    ) {
        val requestRef = db.collection(Constants.Collections.STUDIO_REQUESTS).document()
        val request = hashMapOf(
            "requestId" to requestRef.id,
            "type" to StudioRequest.TYPE_CLAIM,
            "status" to StudioRequest.STATUS_PENDING,
            "requesterUid" to requesterUid,
            "requesterEmail" to requesterEmail,
            "requesterName" to requesterName,
            "studioId" to studioId,
            "studioName" to studioName,
            "googlePlaceId" to googlePlaceId,
            "externalSource" to if (googlePlaceId.isBlank()) "" else "google",
            "address" to address,
            "justification" to justification,
            "verificationDetails" to verificationDetails,
            "adminNote" to "",
            "reviewedByUid" to "",
            "resultStudioId" to "",
            "createdAt" to FieldValue.serverTimestamp(),
            "reviewedAt" to null
        )

        val isExternalClaim = googlePlaceId.isNotBlank() && studioId.startsWith("google_")
        if (isExternalClaim) {
            requestRef.set(request)
                .addOnSuccessListener { onSuccess(requestRef.id) }
                .addOnFailureListener { error -> onFailure(error.message ?: "Failed to submit claim") }
            return
        }

        db.runBatch { batch ->
            batch.set(requestRef, request)
            batch.update(
                db.collection(Constants.Collections.STUDIOS).document(studioId),
                mapOf(
                    "claimStatus" to "PENDING",
                    "claimUpdatedAt" to Timestamp.now()
                )
            )
        }
            .addOnSuccessListener { onSuccess(requestRef.id) }
            .addOnFailureListener { error -> onFailure(error.message ?: "Failed to submit claim") }
    }

    private fun loadRequesterName(uid: String, onLoaded: (String) -> Unit) {
        db.collection(Constants.Collections.USERS).document(uid).get()
            .addOnSuccessListener { document ->
                val name = listOf(
                    document.getString("firstName").orEmpty(),
                    document.getString("lastName").orEmpty()
                ).filter { it.isNotBlank() }.joinToString(" ")
                onLoaded(name)
            }
            .addOnFailureListener { onLoaded("") }
    }
}
