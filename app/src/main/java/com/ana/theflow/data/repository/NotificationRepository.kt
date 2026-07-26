package com.ana.theflow.data.repository

import com.ana.theflow.data.model.notification.InAppNotification
import com.ana.theflow.utilities.Constants
import com.ana.theflow.utilities.NotificationPreferenceUtils
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions

class NotificationRepository {

    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()
    private val settingsRepository = SettingsRepository()

    fun listenToNotifications(
        onUpdate: (List<InAppNotification>) -> Unit,
        onError: (String) -> Unit
    ): ListenerRegistration? {
        val uid = auth.currentUser?.uid
        if (uid == null) {
            onError("User is not logged in")
            return null
        }

        return db.collection(Constants.Collections.USERS)
            .document(uid)
            .collection(Constants.Collections.NOTIFICATIONS)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(50)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    onError(error.message ?: "Failed to load notifications")
                    return@addSnapshotListener
                }
                onUpdate(snapshot?.documents?.mapNotNull { document ->
                    document.toObject(InAppNotification::class.java)?.copy(notificationId = document.id)
                }.orEmpty())
            }
    }

    fun listenToUnreadCount(
        onUpdate: (Long) -> Unit,
        onError: (String) -> Unit = {}
    ): ListenerRegistration? {
        val uid = auth.currentUser?.uid ?: return null
        return db.collection(Constants.Collections.USERS)
            .document(uid)
            .collection(Constants.Collections.NOTIFICATIONS)
            .whereEqualTo("isRead", false)
            .limit(99)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    onError(error.message ?: "Failed to load unread notifications")
                    return@addSnapshotListener
                }
                onUpdate(snapshot?.size()?.toLong() ?: 0L)
            }
    }

    fun createNotification(
        recipientUid: String,
        type: String,
        actorId: String,
        actorName: String = "",
        actorProfileImageUrl: String = "",
        postId: String = "",
        eventId: String = "",
        conversationId: String = "",
        applicationId: String = "",
        title: String,
        message: String,
        dedupeId: String = "",
        onComplete: () -> Unit = {}
    ) {
        if (recipientUid.isBlank() || actorId == recipientUid) {
            onComplete()
            return
        }

        settingsRepository.loadSettings(
            uid = recipientUid,
            onSuccess = { notificationSettings, messageSettings ->
                if (!NotificationPreferenceUtils.isTypeEnabled(type, notificationSettings)) {
                    onComplete()
                    return@loadSettings
                }
                if (type == InAppNotification.Types.PRIVATE_MESSAGE &&
                    !messageSettings.messageNotificationsEnabled
                ) {
                    onComplete()
                    return@loadSettings
                }

                val finalMessage = if (
                    type == InAppNotification.Types.PRIVATE_MESSAGE &&
                    !messageSettings.showMessagePreviews
                ) {
                    "New message"
                } else {
                    message
                }
                val notificationCollection = db.collection(Constants.Collections.USERS)
                    .document(recipientUid)
                    .collection(Constants.Collections.NOTIFICATIONS)
                val docRef = if (dedupeId.isBlank()) notificationCollection.document() else notificationCollection.document(dedupeId)
                val data = mapOf(
                    "notificationId" to docRef.id,
                    "type" to type,
                    "actorId" to actorId,
                    "actorName" to actorName,
                    "actorProfileImageUrl" to actorProfileImageUrl,
                    "postId" to postId,
                    "eventId" to eventId,
                    "conversationId" to conversationId,
                    "applicationId" to applicationId,
                    "title" to title,
                    "message" to finalMessage,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "isRead" to false
                )
                docRef.set(data, SetOptions.merge()).addOnCompleteListener { onComplete() }
            },
            onFailure = { onComplete() }
        )
    }

    fun markAsRead(notificationId: String, onFailure: (String) -> Unit = {}) {
        val uid = auth.currentUser?.uid ?: return
        db.collection(Constants.Collections.USERS)
            .document(uid)
            .collection(Constants.Collections.NOTIFICATIONS)
            .document(notificationId)
            .update("isRead", true)
            .addOnFailureListener { error -> onFailure(error.message ?: "Failed to update notification") }
    }

    fun markAllAsRead(onFailure: (String) -> Unit = {}) {
        val uid = auth.currentUser?.uid ?: return
        db.collection(Constants.Collections.USERS)
            .document(uid)
            .collection(Constants.Collections.NOTIFICATIONS)
            .whereEqualTo("isRead", false)
            .limit(50)
            .get()
            .addOnSuccessListener { snapshot ->
                val batch = db.batch()
                snapshot.documents.forEach { batch.update(it.reference, "isRead", true) }
                batch.commit().addOnFailureListener { error ->
                    onFailure(error.message ?: "Failed to update notifications")
                }
            }
            .addOnFailureListener { error -> onFailure(error.message ?: "Failed to load notifications") }
    }
}
