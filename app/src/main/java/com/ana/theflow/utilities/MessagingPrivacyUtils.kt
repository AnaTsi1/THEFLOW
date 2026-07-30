package com.ana.theflow.utilities

import com.ana.theflow.data.model.settings.MessageSettings

object MessagingPrivacyUtils {
    fun canStartConversation(
        settings: MessageSettings,
        senderIsFollowedByRecipient: Boolean
    ): Boolean {
        return when (settings.receiveMessagesFrom) {
            MessageSettings.RECEIVE_NOBODY -> false
            MessageSettings.RECEIVE_FOLLOWING -> senderIsFollowedByRecipient
            else -> true
        }
    }

    fun blockedReason(settings: MessageSettings): String {
        return when (settings.receiveMessagesFrom) {
            MessageSettings.RECEIVE_NOBODY -> "This dancer is not receiving new messages right now."
            MessageSettings.RECEIVE_FOLLOWING -> "This dancer only receives new messages from people they follow."
            else -> "Messaging is not available right now."
        }
    }
}
