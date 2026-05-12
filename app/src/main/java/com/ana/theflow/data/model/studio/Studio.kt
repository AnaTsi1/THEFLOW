package com.ana.theflow.data.model.studio

import com.ana.theflow.utilities.Constants

data class Studio(
    val id: String = "",
    val displayName: String = "",
    val address: String = "",
    val city: String = "",
    val ownerUid: String = "",
    val managerUids: List<String> = emptyList(),
    val verified: Boolean = false,
    val bio: String = "",
    val location: String = "",
    val danceStyles: List<String> = emptyList(),
    val profileImageUrl: String = "",
    val coverImageUrl: String = "",
    val socialLinks: Map<String, String> = emptyMap(),
    val status: String = Constants.StudioStatus.PENDING.name
)
