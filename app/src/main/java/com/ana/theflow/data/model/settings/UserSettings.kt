// Firestore models for a user's notification and messaging preferences, edited from Settings.
package com.ana.theflow.data.model.settings

// Which kinds of activity should actually trigger a notification for this user.
data class NotificationSettings(
    val allNotificationsEnabled: Boolean = true,
    val likes: Boolean = true,
    val comments: Boolean = true,
    val newFollowers: Boolean = true,
    val privateMessages: Boolean = true,
    val eventRecommendations: Boolean = true,
    val registeredEventUpdates: Boolean = true,
    val professionalApplicationUpdates: Boolean = true
)

// Who's allowed to message this user, and how messages should be shown to them.
data class MessageSettings(
    val messageNotificationsEnabled: Boolean = true,
    val showMessagePreviews: Boolean = true,
    val receiveMessagesFrom: String = RECEIVE_EVERYONE,
    val readReceipts: Boolean = true,
    val emojiSuggestions: Boolean = true
) {
    companion object {
        const val RECEIVE_EVERYONE = "everyone"
        const val RECEIVE_FOLLOWING = "following"
        const val RECEIVE_NOBODY = "nobody"
    }
}
