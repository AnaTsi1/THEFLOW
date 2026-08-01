// Legacy Firestore model for a studio claim request, kept only so existing documents in the old
// studioClaims collection can still be read and migrated into the unified StudioRequest shape.
package com.ana.theflow.data.model.studio

// An old-style studio-claim record. New claims go through StudioRequest instead.
data class StudioClaim(
    val id: String = "",
    val studioId: String = "",
    val studioName: String = "",
    val requesterUid: String = "",
    val requesterEmail: String = "",
    val requesterName: String = "",
    val googlePlaceId: String = "",
    val externalSource: String = "",
    val address: String = "",
    val justification: String = "",
    val verificationDetails: String = "",
    val status: String = "PENDING",
    val reviewedByUid: String = "",
    val adminNote: String = ""
)
