package com.ana.theflow.data.model.studio

data class StudioClaim(
    val id: String = "",
    val studioId: String = "",
    val studioName: String = "",
    val requesterUid: String = "",
    val requesterEmail: String = "",
    val requesterName: String = "",
    val justification: String = "",
    val verificationDetails: String = "",
    val status: String = "PENDING",
    val reviewedByUid: String = "",
    val adminNote: String = ""
)
