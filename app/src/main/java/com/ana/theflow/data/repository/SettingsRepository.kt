package com.ana.theflow.data.repository

import com.ana.theflow.data.model.settings.MessageSettings
import com.ana.theflow.data.model.settings.NotificationSettings
import com.ana.theflow.utilities.Constants
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions

class SettingsRepository {

    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()

    fun loadSettings(
        uid: String = auth.currentUser?.uid.orEmpty(),
        onSuccess: (NotificationSettings, MessageSettings) -> Unit,
        onFailure: (String) -> Unit
    ) {
        if (uid.isBlank()) {
            onFailure("User is not logged in")
            return
        }

        db.collection(Constants.Collections.USERS)
            .document(uid)
            .get()
            .addOnSuccessListener { document ->
                onSuccess(
                    document.notificationSettings(),
                    document.messageSettings()
                )
            }
            .addOnFailureListener { error ->
                onFailure(error.message ?: "Failed to load settings")
            }
    }

    fun saveNotificationSettings(
        settings: NotificationSettings,
        onSuccess: () -> Unit,
        onFailure: (String) -> Unit
    ) {
        val uid = auth.currentUser?.uid
        if (uid == null) {
            onFailure("User is not logged in")
            return
        }
        db.collection(Constants.Collections.USERS)
            .document(uid)
            .set(mapOf("notificationSettings" to notificationSettingsMap(settings)), SetOptions.merge())
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { error -> onFailure(error.message ?: "Failed to save notification settings") }
    }

    fun saveMessageSettings(
        settings: MessageSettings,
        onSuccess: () -> Unit,
        onFailure: (String) -> Unit
    ) {
        val uid = auth.currentUser?.uid
        if (uid == null) {
            onFailure("User is not logged in")
            return
        }
        db.collection(Constants.Collections.USERS)
            .document(uid)
            .set(mapOf("messageSettings" to messageSettingsMap(settings)), SetOptions.merge())
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { error -> onFailure(error.message ?: "Failed to save message settings") }
    }

    companion object {
        fun DocumentSnapshot.notificationSettings(): NotificationSettings {
            val map = get("notificationSettings") as? Map<*, *> ?: emptyMap<Any, Any>()
            return NotificationSettings(
                allNotificationsEnabled = map.bool("allNotificationsEnabled", true),
                likes = map.bool("likes", true),
                comments = map.bool("comments", true),
                newFollowers = map.bool("newFollowers", true),
                privateMessages = map.bool("privateMessages", true),
                eventRecommendations = map.bool("eventRecommendations", true),
                registeredEventUpdates = map.bool("registeredEventUpdates", true),
                professionalApplicationUpdates = map.bool("professionalApplicationUpdates", true)
            )
        }

        fun DocumentSnapshot.messageSettings(): MessageSettings {
            val map = get("messageSettings") as? Map<*, *> ?: emptyMap<Any, Any>()
            return MessageSettings(
                messageNotificationsEnabled = map.bool("messageNotificationsEnabled", true),
                showMessagePreviews = map.bool("showMessagePreviews", true),
                receiveMessagesFrom = map.string("receiveMessagesFrom", MessageSettings.RECEIVE_EVERYONE),
                readReceipts = map.bool("readReceipts", true),
                emojiSuggestions = map.bool("emojiSuggestions", true)
            )
        }

        fun notificationSettingsMap(settings: NotificationSettings): Map<String, Any> {
            return mapOf(
                "allNotificationsEnabled" to settings.allNotificationsEnabled,
                "likes" to settings.likes,
                "comments" to settings.comments,
                "newFollowers" to settings.newFollowers,
                "privateMessages" to settings.privateMessages,
                "eventRecommendations" to settings.eventRecommendations,
                "registeredEventUpdates" to settings.registeredEventUpdates,
                "professionalApplicationUpdates" to settings.professionalApplicationUpdates
            )
        }

        fun messageSettingsMap(settings: MessageSettings): Map<String, Any> {
            return mapOf(
                "messageNotificationsEnabled" to settings.messageNotificationsEnabled,
                "showMessagePreviews" to settings.showMessagePreviews,
                "receiveMessagesFrom" to settings.receiveMessagesFrom,
                "readReceipts" to settings.readReceipts,
                "emojiSuggestions" to settings.emojiSuggestions
            )
        }

        private fun Map<*, *>.bool(key: String, default: Boolean): Boolean {
            return this[key] as? Boolean ?: default
        }

        private fun Map<*, *>.string(key: String, default: String): String {
            return (this[key] as? String)?.ifBlank { default } ?: default
        }
    }
}
