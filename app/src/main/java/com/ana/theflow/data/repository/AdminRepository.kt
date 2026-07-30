package com.ana.theflow.data.repository

import com.ana.theflow.data.model.professional.ProfessionalApplication
import com.ana.theflow.data.model.notification.InAppNotification
import com.ana.theflow.data.model.report.ContentReport
import com.ana.theflow.data.model.studio.StudioClaim
import com.ana.theflow.utilities.Constants
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions

class AdminRepository {

    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()
    private val notificationRepository = NotificationRepository()

    // Loads pending studio claims and professional applications for admin review.
    fun loadPendingReviews(
        onSuccess: (AdminReviewData) -> Unit,
        onFailure: (String) -> Unit
    ) {
        ensureAdmin(
            onSuccess = {
                var claims: List<StudioClaim>? = null
                var applications: List<ProfessionalApplication>? = null
                var reports: List<ContentReport>? = null
                val warnings = mutableListOf<String>()

                fun finishIfReady() {
                    val loadedClaims = claims
                    val loadedApplications = applications
                    val loadedReports = reports
                    if (loadedClaims != null && loadedApplications != null && loadedReports != null) {
                        onSuccess(
                            AdminReviewData(
                                studioClaims = loadedClaims,
                                professionalApplications = loadedApplications,
                                contentReports = loadedReports,
                                warnings = warnings
                            )
                        )
                    }
                }

                db.collection(Constants.Collections.STUDIO_CLAIMS)
                    .whereEqualTo("status", "PENDING")
                    .get()
                    .addOnSuccessListener { claimSnapshot ->
                        claims = claimSnapshot.documents
                            .map { document ->
                                val claim = document.toObject(StudioClaim::class.java)
                                claim?.copy(id = document.id)
                            }
                            .filterNotNull()
                        finishIfReady()
                    }
                    .addOnFailureListener { error ->
                        warnings.add("Studio claims could not load: ${error.message ?: "permission problem"}")
                        claims = emptyList()
                        finishIfReady()
                    }

                db.collection(Constants.Collections.PROFESSIONAL_APPLICATIONS)
                    .whereEqualTo("status", "pending")
                    .get()
                    .addOnSuccessListener { applicationSnapshot ->
                        applications = applicationSnapshot.documents
                            .mapNotNull { document ->
                                document.toObject(ProfessionalApplication::class.java)
                                    ?.copy(applicationId = document.id)
                            }
                        finishIfReady()
                    }
                    .addOnFailureListener { error ->
                        warnings.add("Professional applications could not load: ${error.message ?: "permission problem"}")
                        applications = emptyList()
                        finishIfReady()
                    }

                db.collection(Constants.Collections.CONTENT_REPORTS)
                    .whereEqualTo("status", "open")
                    .get()
                    .addOnSuccessListener { reportSnapshot ->
                        reports = reportSnapshot.documents.mapNotNull { document ->
                            document.toObject(ContentReport::class.java)?.copy(reportId = document.id)
                        }.sortedByDescending { it.createdAt?.seconds ?: 0L }
                        finishIfReady()
                    }
                    .addOnFailureListener { error ->
                        warnings.add("Content reports could not load: ${error.message ?: "permission problem"}")
                        reports = emptyList()
                        finishIfReady()
                    }
            },
            onFailure = onFailure
        )
    }

    // Marks a content report as resolved after admin review.
    fun resolveContentReport(
        report: ContentReport,
        onSuccess: () -> Unit,
        onFailure: (String) -> Unit
    ) {
        ensureAdmin(
            onSuccess = {
                if (report.reportId.isBlank()) {
                    onFailure("Missing report id")
                    return@ensureAdmin
                }
                db.collection(Constants.Collections.CONTENT_REPORTS)
                    .document(report.reportId)
                    .update(
                        mapOf(
                            "status" to "resolved",
                            "resolvedAt" to FieldValue.serverTimestamp(),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                    .addOnSuccessListener { onSuccess() }
                    .addOnFailureListener { error ->
                        onFailure(error.message ?: "Failed to resolve report")
                    }
            },
            onFailure = onFailure
        )
    }

    // Approves a studio claim and links the studio to the requester.
    fun approveStudioClaim(
        claim: StudioClaim,
        onSuccess: () -> Unit,
        onFailure: (String) -> Unit
    ) {
        ensureAdmin(
            onSuccess = { adminUid ->
                if (claim.id.isBlank() || claim.studioId.isBlank() || claim.requesterUid.isBlank()) {
                    onFailure("Claim is missing required data")
                    return@ensureAdmin
                }

                val claimRef = db.collection(Constants.Collections.STUDIO_CLAIMS).document(claim.id)
                val isExternalClaim = claim.googlePlaceId.isNotBlank() && claim.studioId.startsWith("google_")
                val studioRef = if (isExternalClaim) {
                    db.collection(Constants.Collections.STUDIOS).document()
                } else {
                    db.collection(Constants.Collections.STUDIOS).document(claim.studioId)
                }
                val userRef = db.collection(Constants.Collections.USERS).document(claim.requesterUid)

                db.runBatch { batch ->
                    batch.update(
                        claimRef,
                        mapOf(
                            "status" to "APPROVED",
                            "reviewedAt" to FieldValue.serverTimestamp(),
                            "reviewedByUid" to adminUid
                        )
                    )
                    if (isExternalClaim) {
                        batch.set(
                            studioRef,
                            mapOf(
                                "id" to studioRef.id,
                                "displayName" to claim.studioName,
                                "address" to claim.address,
                                "city" to "",
                                "location" to claim.address,
                                "ownerUid" to claim.requesterUid,
                                "managerUids" to listOf(claim.requesterUid),
                                "googlePlaceId" to claim.googlePlaceId,
                                "externalSource" to "google",
                                "claimStatus" to "CLAIMED",
                                "claimUpdatedAt" to FieldValue.serverTimestamp(),
                                "status" to Constants.StudioStatus.APPROVED.name,
                                "verified" to true
                            ),
                            SetOptions.merge()
                        )
                        batch.set(
                            db.collection(Constants.Collections.EXTERNAL_STUDIOS).document(claim.googlePlaceId),
                            mapOf(
                                "googlePlaceId" to claim.googlePlaceId,
                                "source" to "google",
                                "claimedStudioId" to studioRef.id,
                                "claimStatus" to "CLAIMED",
                                "discoveredAt" to FieldValue.serverTimestamp(),
                                "updatedAt" to FieldValue.serverTimestamp()
                            ),
                            SetOptions.merge()
                        )
                    } else {
                        batch.set(
                            studioRef,
                            mapOf(
                                "ownerUid" to claim.requesterUid,
                                "managerUids" to FieldValue.arrayUnion(claim.requesterUid),
                                "claimStatus" to "CLAIMED",
                                "claimUpdatedAt" to Timestamp.now(),
                                "status" to Constants.StudioStatus.APPROVED.name,
                                "verified" to true
                            ),
                            SetOptions.merge()
                        )
                    }
                    val managedStudioId = studioRef.id
                    batch.set(
                        userRef,
                        mapOf(
                            "role" to Constants.UserRole.STUDIO_MANAGER.firestoreValue,
                            "managedStudioIds" to FieldValue.arrayUnion(managedStudioId),
                            "professionalBadges" to FieldValue.arrayUnion("Studio Manager")
                        ),
                        SetOptions.merge()
                    )
                }
                    .addOnSuccessListener {
                        onSuccess()
                    }
                    .addOnFailureListener { error ->
                        onFailure(error.message ?: "Failed to approve studio claim")
                    }
            },
            onFailure = onFailure
        )
    }

    // Rejects a studio claim and opens the studio for a future claim.
    fun rejectStudioClaim(
        claim: StudioClaim,
        onSuccess: () -> Unit,
        onFailure: (String) -> Unit
    ) {
        ensureAdmin(
            onSuccess = { adminUid ->
                if (claim.id.isBlank()) {
                    onFailure("Claim is missing required data")
                    return@ensureAdmin
                }

                val claimRef = db.collection(Constants.Collections.STUDIO_CLAIMS).document(claim.id)
                val isExternalClaim = claim.googlePlaceId.isNotBlank() && claim.studioId.startsWith("google_")
                val studioRef = db.collection(Constants.Collections.STUDIOS).document(claim.studioId)

                db.runBatch { batch ->
                    batch.update(
                        claimRef,
                        mapOf(
                            "status" to "REJECTED",
                            "reviewedAt" to FieldValue.serverTimestamp(),
                            "reviewedByUid" to adminUid
                        )
                    )
                    if (claim.studioId.isNotBlank() && !isExternalClaim) {
                        batch.set(
                            studioRef,
                            mapOf(
                                "claimStatus" to "UNCLAIMED",
                                "claimUpdatedAt" to Timestamp.now()
                            ),
                            SetOptions.merge()
                        )
                    }
                }
                    .addOnSuccessListener { onSuccess() }
                    .addOnFailureListener { error ->
                        onFailure(error.message ?: "Failed to reject studio claim")
                    }
            },
            onFailure = onFailure
        )
    }

    // Approves a professional application and updates the user's professional flags.
    fun approveProfessionalApplication(
        application: ProfessionalApplication,
        onSuccess: () -> Unit,
        onFailure: (String) -> Unit
    ) {
        ensureAdmin(
            onSuccess = { adminUid ->
                if (application.applicationId.isBlank() || application.applicantUid.isBlank()) {
                    onFailure("Application is missing required data")
                    return@ensureAdmin
                }

                val applicationRef = db.collection(Constants.Collections.PROFESSIONAL_APPLICATIONS)
                    .document(application.applicationId)
                val userRef = db.collection(Constants.Collections.USERS).document(application.applicantUid)
                val userUpdates = professionalApprovalUpdates(application.applicationType)

                db.runBatch { batch ->
                    batch.update(
                        applicationRef,
                        mapOf(
                            "status" to "approved",
                            "reviewedAt" to FieldValue.serverTimestamp(),
                            "reviewedByUid" to adminUid
                        )
                    )
                    batch.set(userRef, userUpdates, SetOptions.merge())
                }
                    .addOnSuccessListener {
                        notifyProfessionalApplication(application, adminUid, approved = true)
                        onSuccess()
                    }
                    .addOnFailureListener { error ->
                        onFailure(error.message ?: "Failed to approve application")
                    }
            },
            onFailure = onFailure
        )
    }

    // Rejects a professional application.
    fun rejectProfessionalApplication(
        application: ProfessionalApplication,
        onSuccess: () -> Unit,
        onFailure: (String) -> Unit
    ) {
        ensureAdmin(
            onSuccess = { adminUid ->
                if (application.applicationId.isBlank()) {
                    onFailure("Application is missing required data")
                    return@ensureAdmin
                }

                db.collection(Constants.Collections.PROFESSIONAL_APPLICATIONS)
                    .document(application.applicationId)
                    .update(
                        mapOf(
                            "status" to "rejected",
                            "reviewedAt" to FieldValue.serverTimestamp(),
                            "reviewedByUid" to adminUid
                        )
                    )
                    .addOnSuccessListener {
                        notifyProfessionalApplication(application, adminUid, approved = false)
                        onSuccess()
                    }
                    .addOnFailureListener { error ->
                        onFailure(error.message ?: "Failed to reject application")
                    }
            },
            onFailure = onFailure
        )
    }

    private fun ensureAdmin(
        onSuccess: (String) -> Unit,
        onFailure: (String) -> Unit
    ) {
        val uid = auth.currentUser?.uid
        if (uid.isNullOrBlank()) {
            onFailure("User is not logged in")
            return
        }

        db.collection(Constants.Collections.USERS)
            .document(uid)
            .get()
            .addOnSuccessListener { document ->
                val role = document.getString("role").orEmpty()
                if (role.isAdminRole()) {
                    onSuccess(uid)
                } else {
                    onFailure("Only admins can review requests")
                }
            }
            .addOnFailureListener { error ->
                onFailure(error.message ?: "Failed to verify admin permissions")
            }
    }

    private fun professionalApprovalUpdates(applicationType: String): Map<String, Any> {
        return when {
            applicationType.equals(Constants.ProfessionalApplicationType.VERIFIED_TEACHER.firestoreValue, ignoreCase = true) -> {
                mapOf(
                    "verifiedTeacher" to true,
                    "professionalBadges" to FieldValue.arrayUnion("Verified Teacher")
                )
            }
            applicationType.equals(Constants.ProfessionalApplicationType.CHOREOGRAPHER.firestoreValue, ignoreCase = true) -> {
                mapOf(
                    "verifiedChoreographer" to true,
                    "professionalBadges" to FieldValue.arrayUnion("Choreographer")
                )
            }
            else -> {
                mapOf(
                    "role" to Constants.UserRole.STUDIO_MANAGER.firestoreValue,
                    "professionalBadges" to FieldValue.arrayUnion("Studio / Dance School")
                )
            }
        }
    }

    private fun notifyProfessionalApplication(
        application: ProfessionalApplication,
        adminUid: String,
        approved: Boolean
    ) {
        val type = if (approved) {
            InAppNotification.Types.PROFESSIONAL_APPROVED
        } else {
            InAppNotification.Types.PROFESSIONAL_REJECTED
        }
        val statusText = if (approved) "approved" else "rejected"
        notificationRepository.createNotification(
            recipientUid = application.applicantUid,
            type = type,
            actorId = adminUid,
            applicationId = application.applicationId,
            title = "Application $statusText",
            message = "Your ${applicationTypeLabel(application.applicationType)} application was $statusText.",
            dedupeId = "professional_${application.applicationId}_$statusText"
        )
    }

    private fun applicationTypeLabel(type: String): String {
        return when {
            type.equals(Constants.ProfessionalApplicationType.VERIFIED_TEACHER.firestoreValue, ignoreCase = true) -> "Verified Teacher"
            type.equals(Constants.ProfessionalApplicationType.CHOREOGRAPHER.firestoreValue, ignoreCase = true) -> "Choreographer"
            else -> "Studio / Dance School"
        }
    }

    private fun String.isAdminRole(): Boolean {
        return equals(Constants.UserRole.ADMIN.name, ignoreCase = true) ||
            equals(Constants.UserRole.ADMIN.firestoreValue, ignoreCase = true)
    }

    data class AdminReviewData(
        val studioClaims: List<StudioClaim> = emptyList(),
        val professionalApplications: List<ProfessionalApplication> = emptyList(),
        val contentReports: List<ContentReport> = emptyList(),
        val warnings: List<String> = emptyList()
    )
}
