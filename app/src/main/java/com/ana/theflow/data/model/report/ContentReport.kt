// Firestore model for moderation reports submitted by users.
package com.ana.theflow.data.model.report

import com.google.firebase.Timestamp

// Represents one report that an admin can review and resolve.
data class ContentReport(
    val reportId: String = "",
    val reporterId: String = "",
    val targetType: String = "",
    val targetId: String = "",
    val targetOwnerId: String = "",
    val postId: String = "",
    val commentId: String = "",
    val reason: String = "",
    val status: String = "open",
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null
)
