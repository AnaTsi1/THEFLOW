// Firestore model for one chat message, stored under its parent Conversation document.
package com.ana.theflow.data.model.messaging

import com.google.firebase.Timestamp

// One message within a conversation.
data class Message(
    val messageId: String = "",
    // The real signed-in person who sent this, even if they sent it as a studio - this is what
    // the Firestore rules actually check against, so it always points to a real account.
    val senderId: String = "",
    // Might be blank on older messages - fall back to senderId if so.
    val senderPartyKey: String = "",
    val senderEntityType: String = "",
    val senderEntityId: String = "",
    // The acting person's display name, shown only to other managers of the sending studio
    // (e.g. "via Dima") - the recipient never sees who specifically typed it.
    val actorName: String = "",
    val text: String = "",
    val sentAt: Timestamp? = null,
    val readBy: Map<String, Timestamp> = emptyMap(),
    val type: String = TYPE_TEXT
) {
    companion object {
        const val TYPE_TEXT = "text"
    }
}
