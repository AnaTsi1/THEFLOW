// Reads, writes, and marks read the in-app notifications for either a personal account or a
// studio's shared inbox.
package com.ana.theflow.data.repository

import com.ana.theflow.data.model.account.ActiveAccount
import com.ana.theflow.data.model.messaging.PartyRef
import com.ana.theflow.data.model.notification.InAppNotification
import com.ana.theflow.data.session.ActiveAccountHolder
import com.ana.theflow.utilities.Constants
import com.ana.theflow.utilities.NotificationPreferenceUtils
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions

class NotificationRepository {

    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()
    private val settingsRepository = SettingsRepository()

    // A studio's notifications live under the studio doc so every manager shares one read state;
    // a personal account keeps the original per-user subcollection.
    private fun notificationsRef(account: ActiveAccount): CollectionReference {
        return if (account is ActiveAccount.StudioAccount) {
            db.collection(Constants.Collections.STUDIOS).document(account.studioId).collection(Constants.Collections.NOTIFICATIONS)
        } else {
            db.collection(Constants.Collections.USERS).document(account.userUid).collection(Constants.Collections.NOTIFICATIONS)
        }
    }

    // Live-listens to the latest 50 notifications for whichever account is active, filtering out message-type ones (those live in the chat inbox instead).
    fun listenToNotifications(
        account: ActiveAccount = ActiveAccountHolder.current(),
        onUpdate: (List<InAppNotification>) -> Unit,
        onError: (String) -> Unit
    ): ListenerRegistration? {
        if (auth.currentUser?.uid == null) {
            onError("User is not logged in")
            return null
        }
        return notificationsRef(account)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(50)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    onError(error.message ?: "Failed to load notifications")
                    return@addSnapshotListener
                }
                onUpdate(snapshot?.documents?.mapNotNull { document ->
                    document.toObject(InAppNotification::class.java)?.copy(notificationId = document.id)
                }.orEmpty().excludingMessages())
            }
    }

    // Live-listens to the unread count for the notification bell badge.
    fun listenToUnreadCount(
        account: ActiveAccount = ActiveAccountHolder.current(),
        onUpdate: (Long) -> Unit,
        onError: (String) -> Unit = {}
    ): ListenerRegistration? {
        if (auth.currentUser?.uid == null) return null
        return notificationsRef(account)
            .whereEqualTo("isRead", false)
            .limit(99)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    onError(error.message ?: "Failed to load unread notifications")
                    return@addSnapshotListener
                }
                val unreadCount = snapshot?.documents.orEmpty().mapNotNull { document ->
                    document.toObject(InAppNotification::class.java)
                }.excludingMessages().size
                onUpdate(unreadCount.toLong())
            }
    }

    // Messages have their own inbox and unread badge via the chat icon, so we don't want them
    // showing up again in the general notifications feed.
    private fun List<InAppNotification>.excludingMessages(): List<InAppNotification> {
        return filterNot { it.type == InAppNotification.Types.PRIVATE_MESSAGE || it.type == InAppNotification.Types.STUDIO_MESSAGE }
    }

    // Shorthand for sending a notification to a regular person by uid - just wraps the PartyRef version below.
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
        studioId: String = "",
        title: String,
        message: String,
        dedupeId: String = "",
        onComplete: () -> Unit = {}
    ) {
        createNotification(
            recipient = PartyRef.user(recipientUid),
            type = type,
            actorId = actorId,
            actorName = actorName,
            actorProfileImageUrl = actorProfileImageUrl,
            postId = postId,
            eventId = eventId,
            conversationId = conversationId,
            applicationId = applicationId,
            studioId = studioId,
            title = title,
            message = message,
            dedupeId = dedupeId,
            onComplete = onComplete
        )
    }

    // Sends a notification to either a person or a studio. Skips it entirely if the actor is the
    // recipient (no "you liked your own post" notifications) and, for a person, checks their
    // notification settings first - a studio recipient always gets it, since there's no personal
    // settings doc to check for a business account.
    fun createNotification(
        recipient: PartyRef,
        type: String,
        actorId: String,
        actorName: String = "",
        actorProfileImageUrl: String = "",
        postId: String = "",
        eventId: String = "",
        conversationId: String = "",
        applicationId: String = "",
        studioId: String = "",
        title: String,
        message: String,
        dedupeId: String = "",
        onComplete: () -> Unit = {}
    ) {
        if (recipient.id.isBlank() || actorId == recipient.id) {
            onComplete()
            return
        }

        if (recipient.type == Constants.EntityType.STUDIO) {
            writeNotification(
                collection = db.collection(Constants.Collections.STUDIOS).document(recipient.id).collection(Constants.Collections.NOTIFICATIONS),
                type = type, actorId = actorId, actorName = actorName, actorProfileImageUrl = actorProfileImageUrl,
                postId = postId, eventId = eventId, conversationId = conversationId, applicationId = applicationId, studioId = studioId,
                title = title, message = message, dedupeId = dedupeId,
                targetEntityType = recipient.type, targetEntityId = recipient.id,
                onComplete = onComplete
            )
            return
        }

        settingsRepository.loadSettings(
            uid = recipient.id,
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
                writeNotification(
                    collection = db.collection(Constants.Collections.USERS).document(recipient.id).collection(Constants.Collections.NOTIFICATIONS),
                    type = type, actorId = actorId, actorName = actorName, actorProfileImageUrl = actorProfileImageUrl,
                    postId = postId, eventId = eventId, conversationId = conversationId, applicationId = applicationId, studioId = studioId,
                    title = title, message = finalMessage, dedupeId = dedupeId,
                    targetEntityType = "", targetEntityId = "",
                    onComplete = onComplete
                )
            },
            onFailure = { onComplete() }
        )
    }

    // Actually writes the notification document. If a dedupeId is given, we use it as the
    // document id so a repeated event (like liking then unliking then liking again) just
    // overwrites the same notification instead of spamming a bunch of duplicates.
    private fun writeNotification(
        collection: CollectionReference,
        type: String,
        actorId: String,
        actorName: String,
        actorProfileImageUrl: String,
        postId: String,
        eventId: String,
        conversationId: String,
        applicationId: String,
        studioId: String,
        title: String,
        message: String,
        dedupeId: String,
        targetEntityType: String,
        targetEntityId: String,
        onComplete: () -> Unit
    ) {
        val docRef = if (dedupeId.isBlank()) collection.document() else collection.document(dedupeId)
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
            "studioId" to studioId,
            "title" to title,
            "message" to message,
            "targetEntityType" to targetEntityType,
            "targetEntityId" to targetEntityId,
            "createdAt" to FieldValue.serverTimestamp(),
            "isRead" to false
        )
        docRef.set(data, SetOptions.merge()).addOnCompleteListener { onComplete() }
    }

    // Marks one notification as read. For a studio inbox we also record which manager cleared it.
    fun markAsRead(
        notificationId: String,
        account: ActiveAccount = ActiveAccountHolder.current(),
        onFailure: (String) -> Unit = {}
    ) {
        val uid = auth.currentUser?.uid ?: return
        val updates: Map<String, Any> = if (account is ActiveAccount.StudioAccount) {
            mapOf("isRead" to true, "readByUid" to uid)
        } else {
            mapOf("isRead" to true)
        }
        notificationsRef(account)
            .document(notificationId)
            .update(updates)
            .addOnFailureListener { error -> onFailure(error.message ?: "Failed to update notification") }
    }

    // Marks up to 50 unread notifications as read in one batch, for the "mark all as read" action.
    fun markAllAsRead(
        account: ActiveAccount = ActiveAccountHolder.current(),
        onFailure: (String) -> Unit = {}
    ) {
        val uid = auth.currentUser?.uid ?: return
        notificationsRef(account)
            .whereEqualTo("isRead", false)
            .limit(50)
            .get()
            .addOnSuccessListener { snapshot ->
                val batch = db.batch()
                val updates: Map<String, Any> = if (account is ActiveAccount.StudioAccount) {
                    mapOf("isRead" to true, "readByUid" to uid)
                } else {
                    mapOf("isRead" to true)
                }
                snapshot.documents.forEach { batch.update(it.reference, updates) }
                batch.commit().addOnFailureListener { error ->
                    onFailure(error.message ?: "Failed to update notifications")
                }
            }
            .addOnFailureListener { error -> onFailure(error.message ?: "Failed to load notifications") }
    }
}
