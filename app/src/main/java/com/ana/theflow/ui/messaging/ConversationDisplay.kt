package com.ana.theflow.ui.messaging

import com.ana.theflow.data.model.account.ActiveAccount
import com.ana.theflow.data.model.messaging.Conversation
import com.ana.theflow.data.model.messaging.ConversationParticipant
import com.ana.theflow.data.model.messaging.Message
import com.ana.theflow.data.model.messaging.toPartyRef
import com.ana.theflow.utilities.Constants

// Resolves "who am I / who's the other side" for a conversation relative to whichever account is
// currently active, so screens never compare raw uids directly - the same conversation reads
// differently depending on whether a personal or a studio account is viewing it.
object ConversationDisplay {

    fun myPartyKey(account: ActiveAccount): String = account.toPartyRef().key

    // The other side of this (always two-party) conversation. Falls back to participantIds for
    // every conversation created before partyKeys existed - equivalent for user<->user threads.
    fun counterpartyKey(conversation: Conversation, account: ActiveAccount): String {
        val myKey = myPartyKey(account)
        val keys = conversation.partyKeys.ifEmpty { conversation.participantIds }
        return keys.firstOrNull { it != myKey }.orEmpty()
    }

    fun counterparty(conversation: Conversation, account: ActiveAccount): ConversationParticipant? {
        return conversation.participantInfo[counterpartyKey(conversation, account)]
    }

    // Whether this bubble belongs to the viewer's side of the conversation.
    fun isMine(message: Message, account: ActiveAccount): Boolean {
        val myKey = myPartyKey(account)
        val messageKey = message.senderPartyKey.ifBlank { message.senderId }
        if (messageKey == myKey) return true
        // Legacy fallback: every message sent before Phase 5 only has senderId, and only a
        // personal account could ever have sent it.
        return account is ActiveAccount.Personal && message.senderId == account.userUid
    }

    fun isStudioAuthoredMessage(message: Message): Boolean {
        val type = message.senderEntityType.ifBlank { Constants.EntityType.USER }
        return type.equals(Constants.EntityType.STUDIO, ignoreCase = true)
    }
}
