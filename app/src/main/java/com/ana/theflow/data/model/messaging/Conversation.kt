package com.ana.theflow.data.model.messaging

import com.google.firebase.Timestamp

data class Conversation(
    val conversationId: String = "",
    val participantIds: List<String> = emptyList(),
    val participantInfo: Map<String, ConversationParticipant> = emptyMap(),
    val lastMessage: String = "",
    val lastMessageAt: Timestamp? = null,
    val lastSenderId: String = "",
    val unreadCounts: Map<String, Long> = emptyMap(),
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null
)

data class ConversationParticipant(
    val uid: String = "",
    val name: String = "",
    val profileImageUrl: String = ""
)
