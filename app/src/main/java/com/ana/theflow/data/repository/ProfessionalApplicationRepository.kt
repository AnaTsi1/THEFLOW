package com.ana.theflow.data.repository

import com.ana.theflow.utilities.Constants
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore

class ProfessionalApplicationRepository {

    private val db = FirebaseFirestore.getInstance()

    fun submitApplication(
        applicantUid: String,
        applicationType: Constants.ProfessionalApplicationType,
        requestedDisplayName: String,
        documentUrls: List<String> = emptyList(),
        onSuccess: (String) -> Unit,
        onFailure: (String) -> Unit
    ) {
        if (applicantUid.isBlank()) {
            onFailure("Missing user id")
            return
        }

        val docRef = db.collection(Constants.Collections.PROFESSIONAL_APPLICATIONS).document()
        val application = mapOf(
            "applicationId" to docRef.id,
            "applicantUid" to applicantUid,
            "applicationType" to applicationType.firestoreValue,
            "requestedDisplayName" to requestedDisplayName.trim(),
            "documents" to documentUrls,
            "status" to "pending",
            "createdAt" to FieldValue.serverTimestamp(),
            "reviewedAt" to null,
            "adminNotes" to ""
        )

        docRef.set(application)
            .addOnSuccessListener { onSuccess(docRef.id) }
            .addOnFailureListener { error ->
                onFailure(error.message ?: "Failed to submit application")
            }
    }
}
