package com.ana.theflow.data.model.professional

import com.ana.theflow.utilities.Constants
import com.google.firebase.Timestamp

data class ProfessionalApplication(
    val applicationId: String = "",
    val applicantUid: String = "",
    val applicationType: String = Constants.ProfessionalApplicationType.VERIFIED_TEACHER.firestoreValue,
    val requestedDisplayName: String = "",
    val documents: List<String> = emptyList(),
    val status: String = "pending",
    val createdAt: Timestamp? = null,
    val reviewedAt: Timestamp? = null,
    val adminNotes: String = ""
)
