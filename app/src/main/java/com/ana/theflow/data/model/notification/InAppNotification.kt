// Firestore model for one in-app notification, stored under either a user's or a studio's
// notifications subcollection depending on which account the activity was addressed to.
package com.ana.theflow.data.model.notification

import com.google.firebase.Timestamp

// One in-app notification - a like, follow, message, application update, whatever.
data class InAppNotification(
    val notificationId: String = "",
    val type: String = "",
    val actorId: String = "",
    val actorName: String = "",
    val actorProfileImageUrl: String = "",
    val postId: String = "",
    val eventId: String = "",
    val conversationId: String = "",
    val applicationId: String = "",
    // Set on studio_request_approved so the recipient can jump straight into the new business
    // account instead of hunting for it in the account switcher.
    val studioId: String = "",
    val title: String = "",
    val message: String = "",
    // Set only on a studio's notifications subcollection - which business account this belongs to.
    val targetEntityType: String = "",
    val targetEntityId: String = "",
    // Which manager cleared a studio notification - blank for a personal notification.
    val readByUid: String = "",
    val createdAt: Timestamp? = null,
    val isRead: Boolean = false
) {
    object Types {
        const val LIKE = "like"
        const val COMMENT = "comment"
        const val FOLLOW = "follow"
        const val PRIVATE_MESSAGE = "private_message"
        const val PROFESSIONAL_APPROVED = "professional_approved"
        const val PROFESSIONAL_REJECTED = "professional_rejected"
        const val EVENT_UPDATED = "event_updated"
        const val EVENT_RECOMMENDED = "event_recommended"
        const val JOB_RECOMMENDED = "job_recommended"
        const val JOB_APPLICATION_RECEIVED = "job_application_received"
        const val JOB_APPLICATION_UPDATED = "job_application_updated"
        const val STUDIO_FOLLOW = "studio_follow"
        const val STUDIO_MESSAGE = "studio_message"
        const val EVENT_REGISTRATION = "event_registration"
        const val STUDIO_POST_COMMENT = "studio_post_comment"
        const val STUDIO_POST_LIKE = "studio_post_like"
        const val PERMISSION_GRANTED = "permission_granted"
        const val PERMISSION_REVOKED = "permission_revoked"
        const val STUDIO_REQUEST_APPROVED = "studio_request_approved"
        const val STUDIO_REQUEST_REJECTED = "studio_request_rejected"
    }
}
