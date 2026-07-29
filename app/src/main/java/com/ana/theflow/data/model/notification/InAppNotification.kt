package com.ana.theflow.data.model.notification

import com.google.firebase.Timestamp

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
    val title: String = "",
    val message: String = "",
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
    }
}
