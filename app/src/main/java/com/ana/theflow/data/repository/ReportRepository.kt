// Repository for user-submitted safety reports on social content and profiles.
package com.ana.theflow.data.repository

import com.ana.theflow.utilities.Constants
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore

// Stores immutable moderation reports for later admin review.
class ReportRepository {

    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()

    // Reports a post, comment, event, or user without modifying the reported target.
    fun reportContent(
        targetType: String,
        targetId: String,
        reason: String,
        targetOwnerId: String = "",
        postId: String = "",
        commentId: String = "",
        onSuccess: () -> Unit,
        onFailure: (String) -> Unit
    ) {
        val uid = auth.currentUser?.uid
        if (uid == null) {
            onFailure("User is not logged in")
            return
        }
        if (targetType.isBlank() || targetId.isBlank()) {
            onFailure("Report target is missing")
            return
        }

        val reportId = "${targetType}_${targetId}_$uid".replace(Regex("[^A-Za-z0-9_-]"), "_")
        val report = mapOf(
            "reportId" to reportId,
            "reporterId" to uid,
            "targetType" to targetType,
            "targetId" to targetId,
            "targetOwnerId" to targetOwnerId,
            "postId" to postId,
            "commentId" to commentId,
            "reason" to reason.ifBlank { DEFAULT_REASON },
            "status" to STATUS_OPEN,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )

        db.collection(Constants.Collections.CONTENT_REPORTS)
            .document(reportId)
            .get()
            .addOnSuccessListener { snapshot ->
                if (snapshot.exists()) {
                    onSuccess()
                    return@addOnSuccessListener
                }
                db.collection(Constants.Collections.CONTENT_REPORTS)
                    .document(reportId)
                    .set(report)
                    .addOnSuccessListener { onSuccess() }
                    .addOnFailureListener { error ->
                        onFailure(error.message ?: "Failed to send report")
                    }
            }
            .addOnFailureListener { error ->
                onFailure(error.message ?: "Failed to send report")
            }
    }

    object TargetTypes {
        const val POST = "post"
        const val COMMENT = "comment"
        const val USER = "user"
        const val EVENT = "event"
    }

    private companion object {
        const val DEFAULT_REASON = "Needs review"
        const val STATUS_OPEN = "open"
    }
}
