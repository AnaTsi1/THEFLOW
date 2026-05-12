package com.ana.theflow.data.model.activity

import com.google.firebase.Timestamp

data class ActivityEvent(
    val eventId: String = "",
    val userId: String = "",
    val eventType: String = "",
    val targetType: String = "",
    val targetId: String = "",
    val targetName: String = "",
    val danceStyles: List<String> = emptyList(),
    val location: String = "",
    val metadata: Map<String, String> = emptyMap(),
    val weight: Int = 0,
    val createdAt: Timestamp? = null
)
