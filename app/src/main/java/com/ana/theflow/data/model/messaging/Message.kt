package com.ana.theflow.data.model.messaging

import com.google.firebase.Timestamp

data class Message(
    val messageId: String = "",
    val senderId: String = "",
    val text: String = "",
    val sentAt: Timestamp? = null,
    val readBy: Map<String, Timestamp> = emptyMap(),
    val type: String = TYPE_TEXT
) {
    companion object {
        const val TYPE_TEXT = "text"
    }
}
