// Firestore model for a dancer's application to become a verified teacher or choreographer.
package com.ana.theflow.data.model.professional

import com.ana.theflow.utilities.Constants
import com.google.firebase.Timestamp

// One application to get verified as a teacher or choreographer - an admin reviews these.
data class ProfessionalApplication(
    val applicationId: String = "",
    val applicantUid: String = "",
    val applicationType: String = Constants.ProfessionalApplicationType.VERIFIED_TEACHER.firestoreValue,
    val requestedDisplayName: String = "",
    val experienceDetails: String = "",
    val documents: List<String> = emptyList(),
    val status: String = "pending",
    val createdAt: Timestamp? = null,
    val reviewedAt: Timestamp? = null,
    val adminNotes: String = ""
)
