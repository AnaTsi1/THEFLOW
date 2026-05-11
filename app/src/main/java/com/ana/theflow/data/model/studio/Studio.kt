package com.ana.theflow.data.model.studio

import com.ana.theflow.utilities.Constants

data class Studio(
    val id: String = "",
    val name: String = "",
    val address: String = "",
    val city: String = "",
    val danceStyles: List<String> = emptyList(),
    val phone: String = "",
    val ownerUid: String = "",
    val status: String = Constants.StudioStatus.PENDING.name
)
