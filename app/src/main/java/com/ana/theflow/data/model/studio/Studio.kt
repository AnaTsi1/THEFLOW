package com.ana.theflow.data.model.studio

import com.ana.theflow.utilities.Constants
import com.google.firebase.Timestamp

// A Studio is a business account: never authenticates directly, managed by one or more Users.
data class Studio(
    val id: String = "",
    val displayName: String = "",
    val searchName: String = "",
    val handle: String = "",
    val address: String = "",
    val city: String = "",
    val location: String = "",
    val latitude: Double? = null,
    val longitude: Double? = null,
    val ownerUid: String = "",
    val managerUids: List<String> = emptyList(),
    val verified: Boolean = false,
    val bio: String = "",
    val danceStyles: List<String> = emptyList(),
    val profileImageUrl: String = "",
    val coverImageUrl: String = "",
    val socialLinks: Map<String, String> = emptyMap(),
    val websiteUrl: String = "",
    val contactPhone: String = "",
    val contactEmail: String = "",
    val openingHours: String = "",
    val teacherUids: List<String> = emptyList(),
    val teacherProfiles: List<Map<String, Any>> = emptyList(),
    val followersCount: Long = 0,
    val postsCount: Long = 0,
    val status: String = Constants.StudioStatus.PENDING.name,
    val googlePlaceId: String = "",
    val externalSource: String = "",
    val claimStatus: String = "",
    val claimUpdatedAt: Timestamp? = null,
    val createdByUid: String = "",
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null
) {
    // socialLinks and teacherProfiles are loosely-typed Firestore maps (not their own model
    // classes) - these are the one shared set of keys every reader/writer of those maps uses,
    // so the shape stays consistent between the profile view and the edit screen.
    companion object {
        const val SOCIAL_INSTAGRAM = "instagram"
        const val SOCIAL_TIKTOK = "tiktok"
        const val SOCIAL_YOUTUBE = "youtube"

        const val TEACHER_KEY_UID = "uid"
        const val TEACHER_KEY_NAME = "name"
        const val TEACHER_KEY_HEADLINE = "headline"
        const val TEACHER_KEY_PHOTO = "profileImageUrl"
    }
}
