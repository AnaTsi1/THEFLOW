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
        latitude: Double? = null,
        longitude: Double? = null,
        coverImageUrl: String = "",
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
                createClaimRequest(
                    studioId = studioId, studioName = studioName, requesterUid = uid,
                    requesterEmail = currentUser.email.orEmpty(), requesterName = requesterName,
                    googlePlaceId = googlePlaceId, address = address,
                    latitude = latitude, longitude = longitude, coverImageUrl = coverImageUrl,
                    justification = justification.trim(), verificationDetails = verificationDetails.trim(),
                    onSuccess = onSuccess, onFailure = onFailure
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

                    createClaimRequest(
                        studioId = studioId, studioName = studioName, requesterUid = uid,
                        requesterEmail = currentUser.email.orEmpty(), requesterName = requesterName,
                        googlePlaceId = googlePlaceId, address = address,
                        latitude = latitude, longitude = longitude, coverImageUrl = coverImageUrl,
                        justification = justification.trim(), verificationDetails = verificationDetails.trim(),
                        onSuccess = onSuccess, onFailure = onFailure
                    )
                }
                .addOnFailureListener { error -> onFailure(error.message ?: "Failed to load studio") }
        }
    }

    // Loads whatever create/claim requests the signed-in user has submitted so far.
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

    // Actually writes the claim request. For a Google Places studio it's just one document, but
    // for a studio already in our database we also flip its claimStatus to PENDING in the same
    // batch, so the studio and the request stay in sync.
    private fun createClaimRequest(
        studioId: String,
        studioName: String,
        requesterUid: String,
        requesterEmail: String,
        requesterName: String,
        googlePlaceId: String,
        address: String,
        latitude: Double?,
        longitude: Double?,
        coverImageUrl: String,
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
            "latitude" to latitude,
            "longitude" to longitude,
            "coverImageUrl" to coverImageUrl,
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

    // Grabs the requester's name off their user doc, so the request has a readable name attached
    // instead of just a uid.
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
