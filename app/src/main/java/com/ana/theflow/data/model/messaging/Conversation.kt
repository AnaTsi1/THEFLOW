// Firestore model for a private conversation's metadata (participants, preview, unread counts).
// The actual chat messages live in a subcollection under the conversation document, see Message.kt.
package com.ana.theflow.data.model.messaging

import com.ana.theflow.utilities.Constants
import com.google.firebase.Timestamp

// Metadata for one conversation - could be two people chatting, or a person messaging into a studio's shared inbox.
data class Conversation(
    val conversationId: String = "",
    val participantIds: List<String> = emptyList(),
    // Set only for a user<->studio conversation (a business inbox thread); absent for user<->user.
    val studioId: String = "",
    // The two parties' keys (a uid, or "studio_<id>") - falls back to participantIds for every
    // conversation created before this field existed, since those are equivalent for user<->user.
    val partyKeys: List<String> = emptyList(),
    // Keyed by party key, not always by uid - a studio's entry uses its "studio_<id>" key.
    val participantInfo: Map<String, ConversationParticipant> = emptyMap(),
    val lastMessage: String = "",
    val lastMessageAt: Timestamp? = null,
    val lastSenderId: String = "",
    val lastSenderPartyKey: String = "",
    // Keyed by party key - lets every manager of a studio share one read state for its inbox.
    val unreadCounts: Map<String, Long> = emptyMap(),
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null
) {
    // True if this is someone messaging a studio's inbox, rather than a regular person-to-person chat.
    fun isStudioConversation(): Boolean = studioId.isNotBlank()
}

// Display info for one side of a conversation - keyed by party key in participantInfo above.
data class ConversationParticipant(
    // The party's entity id - a uid for a person, a studioId for a studio.
    val uid: String = "",
    val name: String = "",
    val profileImageUrl: String = "",
    val type: String = Constants.EntityType.USER
)
