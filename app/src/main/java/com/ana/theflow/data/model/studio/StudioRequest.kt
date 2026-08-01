package com.ana.theflow.data.model.studio

import com.google.firebase.Timestamp

// A user-submitted request to either create a brand-new studio account or claim an existing one.
// Reviewed by an admin, who is the only actor able to turn it into real manager permissions.
data class StudioRequest(
    val requestId: String = "",
    val type: String = TYPE_CREATE,
    val status: String = STATUS_PENDING,
    val requesterUid: String = "",
    val requesterName: String = "",
    val requesterEmail: String = "",
    val justification: String = "",
    val verificationDetails: String = "",
    val adminNote: String = "",
    val reviewedByUid: String = "",
    val createdAt: Timestamp? = null,
    val reviewedAt: Timestamp? = null,

    // CLAIM only
    val studioId: String = "",
    val studioName: String = "",
    val googlePlaceId: String = "",
    val externalSource: String = "",
    val address: String = "",

    // CREATE only - the admin-reviewable draft profile
    val draftDisplayName: String = "",
    val draftCity: String = "",
    val draftAddress: String = "",
    val draftBio: String = "",
    val draftDanceStyles: List<String> = emptyList(),
    val draftWebsiteUrl: String = "",
    val draftContactPhone: String = "",
    val draftContactEmail: String = "",
    val draftSocialLinks: Map<String, String> = emptyMap(),

    // Set at approval time, for traceability
    val resultStudioId: String = "",

    // "studioClaims" when mapped from the legacy collection, "studioRequests" otherwise.
    // Drives which collection AdminRepository writes the review decision back to.
    val sourceCollection: String = ""
) {
    companion object {
        const val TYPE_CREATE = "CREATE"
        const val TYPE_CLAIM = "CLAIM"

        const val STATUS_PENDING = "PENDING"
        const val STATUS_APPROVED = "APPROVED"
        const val STATUS_REJECTED = "REJECTED"

        const val SOURCE_STUDIO_REQUESTS = "studioRequests"
        const val SOURCE_LEGACY_STUDIO_CLAIMS = "studioClaims"

        // Maps a legacy StudioClaim document into the unified StudioRequest shape so the
        // in-app admin queue can review both flows through a single screen while the old
        // collection drains.
        fun fromLegacyClaim(claim: StudioClaim): StudioRequest {
            return StudioRequest(
                requestId = claim.id,
                type = TYPE_CLAIM,
                status = claim.status,
                requesterUid = claim.requesterUid,
                requesterName = claim.requesterName,
                requesterEmail = claim.requesterEmail,
                justification = claim.justification,
                verificationDetails = claim.verificationDetails,
                adminNote = claim.adminNote,
                reviewedByUid = claim.reviewedByUid,
                studioId = claim.studioId,
                studioName = claim.studioName,
                googlePlaceId = claim.googlePlaceId,
                externalSource = claim.externalSource,
                address = claim.address,
                sourceCollection = SOURCE_LEGACY_STUDIO_CLAIMS
            )
        }
    }
}
