// Handles submitting and creating teacher/choreographer verification applications for admin review.
package com.ana.theflow.data.repository

import com.ana.theflow.utilities.Constants
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore

class ProfessionalApplicationRepository {

    private val db = FirebaseFirestore.getInstance()

    // Submits an application for review. If they already have a pending one of this same type,
    // we just reuse it instead of creating a new one - stops a resubmit or retry from cluttering
    // up the admin queue with duplicates.
    fun submitApplication(
        applicantUid: String,
        applicationType: Constants.ProfessionalApplicationType,
        requestedDisplayName: String,
        experienceDetails: String = "",
        documentUrls: List<String> = emptyList(),
        onSuccess: (String) -> Unit,
        onFailure: (String) -> Unit
    ) {
        if (applicantUid.isBlank()) {
            onFailure("Missing user id")
            return
        }

        db.collection(Constants.Collections.PROFESSIONAL_APPLICATIONS)
            .whereEqualTo("applicantUid", applicantUid)
            .whereEqualTo("applicationType", applicationType.firestoreValue)
            .whereEqualTo("status", "pending")
            .limit(1)
            .get()
            .addOnSuccessListener { snapshot ->
                val existingId = snapshot.documents.firstOrNull()?.id
                if (existingId != null) {
                    onSuccess(existingId)
                    return@addOnSuccessListener
                }
                createApplication(
                    applicantUid = applicantUid,
                    applicationType = applicationType,
                    requestedDisplayName = requestedDisplayName,
                    experienceDetails = experienceDetails,
                    documentUrls = documentUrls,
                    onSuccess = onSuccess,
                    onFailure = onFailure
                )
            }
            .addOnFailureListener { error ->
                onFailure(error.message ?: "Failed to submit application")
            }
    }

    // Actually creates the new pending application document.
    private fun createApplication(
        applicantUid: String,
        applicationType: Constants.ProfessionalApplicationType,
        requestedDisplayName: String,
        experienceDetails: String,
        documentUrls: List<String>,
        onSuccess: (String) -> Unit,
        onFailure: (String) -> Unit
    ) {
        val docRef = db.collection(Constants.Collections.PROFESSIONAL_APPLICATIONS).document()
        val application = mapOf(
            "applicationId" to docRef.id,
            "applicantUid" to applicantUid,
            "applicationType" to applicationType.firestoreValue,
            "requestedDisplayName" to requestedDisplayName.trim(),
            "experienceDetails" to experienceDetails.trim(),
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
