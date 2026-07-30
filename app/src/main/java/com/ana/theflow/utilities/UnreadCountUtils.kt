package com.ana.theflow.utilities

object UnreadCountUtils {
    fun incrementForRecipients(
        participantIds: List<String>,
        senderId: String,
        currentUnreadCounts: Map<String, Long>
    ): Map<String, Long> {
        return participantIds.associateWith { uid ->
            if (uid == senderId) 0L else (currentUnreadCounts[uid] ?: 0L) + 1L
        }
    }

    fun totalFor(uid: String, conversations: List<Map<String, Long>>): Long {
        return conversations.sumOf { it[uid] ?: 0L }
    }
}
