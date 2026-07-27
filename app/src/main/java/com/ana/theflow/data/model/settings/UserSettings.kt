

package com.ana.theflow.data.model.settings

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
