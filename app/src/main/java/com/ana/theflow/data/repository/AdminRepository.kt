// Every permission mutation in the app funnels through here: studio create/claim approval,
// professional verification, and direct grant/revoke of teacher/choreographer/manager status.
// Nothing outside this class (and Firestore rules) may write role/verified*/managedStudioIds.
package com.ana.theflow.data.repository

import com.ana.theflow.data.model.permission.PermissionGrant
import com.ana.theflow.data.model.professional.ProfessionalApplication
import com.ana.theflow.data.model.notification.InAppNotification
import com.ana.theflow.data.model.report.ContentReport
import com.ana.theflow.data.model.studio.Studio
import com.ana.theflow.data.model.studio.StudioClaim
import com.ana.theflow.data.model.studio.StudioRequest
import com.ana.theflow.data.model.user.User
import com.ana.theflow.utilities.CityOptions
import com.ana.theflow.utilities.Constants
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions

class AdminRepository {

    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()
    private val notificationRepository = NotificationRepository()

    // Loads pending studio requests (new + legacy), professional applications, and content
    // reports for admin review.
    fun loadPendingReviews(
        onSuccess: (AdminReviewData) -> Unit,
        onFailure: (String) -> Unit
    ) {
        ensureAdmin(
            onSuccess = {
                val newRequests = mutableListOf<StudioRequest>()
                val legacyRequests = mutableListOf<StudioRequest>()
                var applications: List<ProfessionalApplication> = emptyList()
                var reports: List<ContentReport> = emptyList()
                val failedSections = mutableSetOf<ReviewSection>()
                var pendingLoads = 4

                fun finishOne() {
                    pendingLoads -= 1
                    if (pendingLoads > 0) return
                    onSuccess(
                        AdminReviewData(
                            studioRequests = (newRequests + legacyRequests).sortedByDescending { it.createdAt?.seconds ?: 0L },
                            professionalApplications = applications,
                            contentReports = reports,
                            failedSections = failedSections
                        )
                    )
                }

                db.collection(Constants.Collections.STUDIO_REQUESTS)
                    .whereEqualTo("status", StudioRequest.STATUS_PENDING)
                    .get()
                    .addOnSuccessListener { snapshot ->
                        newRequests.addAll(snapshot.documents.mapNotNull { document ->
                            document.toObject(StudioRequest::class.java)
                                ?.copy(requestId = document.id, sourceCollection = StudioRequest.SOURCE_STUDIO_REQUESTS)
                        })
                        finishOne()
                    }
                    .addOnFailureListener {
                        failedSections.add(ReviewSection.STUDIO_REQUESTS)
                        finishOne()
                    }

                db.collection(Constants.Collections.STUDIO_CLAIMS)
                    .whereEqualTo("status", "PENDING")
                    .get()
                    .addOnSuccessListener { snapshot ->
                        legacyRequests.addAll(
                            snapshot.documents.mapNotNull { document ->
                                document.toObject(StudioClaim::class.java)?.copy(id = document.id)
                            }.map { StudioRequest.fromLegacyClaim(it) }
                        )
                        finishOne()
                    }
                    .addOnFailureListener {
                        failedSections.add(ReviewSection.STUDIO_REQUESTS)
                        finishOne()
                    }

                db.collection(Constants.Collections.PROFESSIONAL_APPLICATIONS)
                    .whereEqualTo("status", "pending")
                    .get()
                    .addOnSuccessListener { applicationSnapshot ->
                        applications = applicationSnapshot.documents.mapNotNull { document ->
                            document.toObject(ProfessionalApplication::class.java)
                                ?.copy(applicationId = document.id)
                        }.filterNot { isLegacyStudioApplication(it.applicationType) }
                        finishOne()
                    }
                    .addOnFailureListener {
                        failedSections.add(ReviewSection.PROFESSIONAL_APPLICATIONS)
                        finishOne()
                    }

                db.collection(Constants.Collections.CONTENT_REPORTS)
                    .whereEqualTo("status", "open")
                    .get()
                    .addOnSuccessListener { reportSnapshot ->
                        reports = reportSnapshot.documents.mapNotNull { document ->
                            document.toObject(ContentReport::class.java)?.copy(reportId = document.id)
                        }.sortedByDescending { it.createdAt?.seconds ?: 0L }
                        finishOne()
                    }
                    .addOnFailureListener {
                        failedSections.add(ReviewSection.CONTENT_REPORTS)
                        finishOne()
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
        updateContentReportStatus(report, "resolved", onSuccess, onFailure)
    }

    // Marks a content report as reviewed with no action taken against the content - the report
    // is closed out (removed from the pending "open" queue) instead of silently doing nothing and
    // resurfacing on every future review pass.
    fun dismissContentReport(
        report: ContentReport,
        onSuccess: () -> Unit,
        onFailure: (String) -> Unit
    ) {
        updateContentReportStatus(report, "dismissed", onSuccess, onFailure)
    }

    // Shared write path behind resolveContentReport/dismissContentReport - just flips the status
    // field and stamps who reviewed it and when.
    private fun updateContentReportStatus(
        report: ContentReport,
        status: String,
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
                            "status" to status,
                            "resolvedAt" to FieldValue.serverTimestamp(),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                    .addOnSuccessListener { onSuccess() }
                    .addOnFailureListener { error ->
                        onFailure(error.message ?: "Failed to update report")
                    }
            },
            onFailure = onFailure
        )
    }

    // Approves a studio create or claim request and grants the requester manager permissions.
    // Never writes `role` - studio permission is entirely represented by managedStudioIds.
    fun approveStudioRequest(
        request: StudioRequest,
        onSuccess: () -> Unit,
        onFailure: (String) -> Unit
    ) {
        ensureAdmin(
            onSuccess = { adminUid ->
                if (request.requestId.isBlank() || request.requesterUid.isBlank()) {
                    onFailure("Request is missing required data")
                    return@ensureAdmin
                }
                when (request.type) {
                    StudioRequest.TYPE_CREATE -> approveCreateRequest(request, adminUid, onSuccess, onFailure)
                    StudioRequest.TYPE_CLAIM -> approveClaimRequest(request, adminUid, onSuccess, onFailure)
                    else -> onFailure("Unknown request type")
                }
            },
            onFailure = onFailure
        )
    }

    // Rejects a studio create or claim request.
    fun rejectStudioRequest(
        request: StudioRequest,
        adminNote: String = "",
        onSuccess: () -> Unit,
        onFailure: (String) -> Unit
    ) {
        ensureAdmin(
            onSuccess = { adminUid ->
                if (request.requestId.isBlank()) {
                    onFailure("Request is missing required data")
                    return@ensureAdmin
                }
                val requestRef = requestDocRef(request)
                val isExternalClaim = request.type == StudioRequest.TYPE_CLAIM &&
                    request.googlePlaceId.isNotBlank() && request.studioId.startsWith("google_")

                db.runBatch { batch ->
                    batch.update(
                        requestRef,
                        mapOf(
                            "status" to StudioRequest.STATUS_REJECTED,
                            "reviewedAt" to FieldValue.serverTimestamp(),
                            "reviewedByUid" to adminUid,
                            "adminNote" to adminNote
                        )
                    )
                    if (request.type == StudioRequest.TYPE_CLAIM && request.studioId.isNotBlank() && !isExternalClaim) {
                        batch.set(
                            db.collection(Constants.Collections.STUDIOS).document(request.studioId),
                            mapOf("claimStatus" to "UNCLAIMED", "claimUpdatedAt" to Timestamp.now()),
                            SetOptions.merge()
                        )
                    }
                }
                    .addOnSuccessListener {
                        notifyStudioRequestDecision(request, adminUid, approved = false)
                        onSuccess()
                    }
                    .addOnFailureListener { error ->
                        onFailure(error.message ?: "Failed to reject request")
                    }
            },
            onFailure = onFailure
        )
    }

    // Approves a professional application. Studio access is deliberately excluded here -
    // it only ever comes from an approved studio request.
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
                val userUpdates = professionalApprovalUpdates(application.applicationType)
                if (userUpdates == null) {
                    onFailure("Studio access is granted through studio requests, not professional applications.")
                    return@ensureAdmin
                }

                val applicationRef = db.collection(Constants.Collections.PROFESSIONAL_APPLICATIONS)
                    .document(application.applicationId)
                val userRef = db.collection(Constants.Collections.USERS).document(application.applicantUid)

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

    // Grants or revokes the additive Verified Teacher / Choreographer permissions directly.
    fun grantTeacher(targetUid: String, note: String = "", onSuccess: () -> Unit, onFailure: (String) -> Unit) =
        setBooleanPermission(targetUid, "verifiedTeacher", true, PermissionGrant.Actions.GRANT_TEACHER, "Verified Teacher", note, onSuccess, onFailure)

    fun revokeTeacher(targetUid: String, note: String = "", onSuccess: () -> Unit, onFailure: (String) -> Unit) =
        setBooleanPermission(targetUid, "verifiedTeacher", false, PermissionGrant.Actions.REVOKE_TEACHER, "Verified Teacher", note, onSuccess, onFailure)

    fun grantChoreographer(targetUid: String, note: String = "", onSuccess: () -> Unit, onFailure: (String) -> Unit) =
        setBooleanPermission(targetUid, "verifiedChoreographer", true, PermissionGrant.Actions.GRANT_CHOREOGRAPHER, "Choreographer", note, onSuccess, onFailure)

    fun revokeChoreographer(targetUid: String, note: String = "", onSuccess: () -> Unit, onFailure: (String) -> Unit) =
        setBooleanPermission(targetUid, "verifiedChoreographer", false, PermissionGrant.Actions.REVOKE_CHOREOGRAPHER, "Choreographer", note, onSuccess, onFailure)

    // Adds a user as an additional manager of an existing studio.
    fun addStudioManager(studioId: String, targetUid: String, note: String = "", onSuccess: () -> Unit, onFailure: (String) -> Unit) {
        ensureAdmin(
            onSuccess = { adminUid ->
                if (studioId.isBlank() || targetUid.isBlank()) {
                    onFailure("Missing studio or user id")
                    return@ensureAdmin
                }
                db.collection(Constants.Collections.USERS).document(targetUid).get()
                    .addOnSuccessListener { userDocument ->
                        val targetName = fullNameFrom(userDocument)
                        val grantRef = db.collection(Constants.Collections.PERMISSION_GRANTS).document()
                        db.runBatch { batch ->
                            batch.set(
                                db.collection(Constants.Collections.STUDIOS).document(studioId),
                                mapOf("managerUids" to FieldValue.arrayUnion(targetUid)),
                                SetOptions.merge()
                            )
                            batch.set(
                                db.collection(Constants.Collections.USERS).document(targetUid),
                                mapOf(
                                    "managedStudioIds" to FieldValue.arrayUnion(studioId),
                                    "professionalBadges" to FieldValue.arrayUnion("Studio Manager")
                                ),
                                SetOptions.merge()
                            )
                            batch.set(grantRef, permissionGrantMap(grantRef.id, adminUid, targetUid, targetName, PermissionGrant.Actions.ADD_MANAGER, studioId, note))
                        }
                            .addOnSuccessListener {
                                notificationRepository.createNotification(
                                    recipientUid = targetUid,
                                    type = InAppNotification.Types.PERMISSION_GRANTED,
                                    actorId = adminUid,
                                    title = "Studio access granted",
                                    message = "You were added as a manager of a studio.",
                                    dedupeId = "manager_added_${studioId}_$targetUid"
                                )
                                onSuccess()
                            }
                            .addOnFailureListener { error -> onFailure(error.message ?: "Failed to add manager") }
                    }
                    .addOnFailureListener { error -> onFailure(error.message ?: "Failed to load user") }
            },
            onFailure = onFailure
        )
    }

    // Removes a manager from a studio. Refuses to remove the current owner - transfer first.
    fun removeStudioManager(studioId: String, targetUid: String, note: String = "", onSuccess: () -> Unit, onFailure: (String) -> Unit) {
        ensureAdmin(
            onSuccess = { adminUid ->
                if (studioId.isBlank() || targetUid.isBlank()) {
                    onFailure("Missing studio or user id")
                    return@ensureAdmin
                }
                db.collection(Constants.Collections.STUDIOS).document(studioId).get()
                    .addOnSuccessListener { studioDocument ->
                        if (studioDocument.getString("ownerUid") == targetUid) {
                            onFailure("Transfer ownership before removing this manager")
                            return@addOnSuccessListener
                        }
                        db.collection(Constants.Collections.USERS).document(targetUid).get()
                            .addOnSuccessListener { userDocument ->
                                val targetName = fullNameFrom(userDocument)
                                val grantRef = db.collection(Constants.Collections.PERMISSION_GRANTS).document()
                                db.runBatch { batch ->
                                    batch.set(
                                        db.collection(Constants.Collections.STUDIOS).document(studioId),
                                        mapOf("managerUids" to FieldValue.arrayRemove(targetUid)),
                                        SetOptions.merge()
                                    )
                                    batch.set(
                                        db.collection(Constants.Collections.USERS).document(targetUid),
                                        mapOf("managedStudioIds" to FieldValue.arrayRemove(studioId)),
                                        SetOptions.merge()
                                    )
                                    batch.set(grantRef, permissionGrantMap(grantRef.id, adminUid, targetUid, targetName, PermissionGrant.Actions.REMOVE_MANAGER, studioId, note))
                                }
                                    .addOnSuccessListener {
                                        notificationRepository.createNotification(
                                            recipientUid = targetUid,
                                            type = InAppNotification.Types.PERMISSION_REVOKED,
                                            actorId = adminUid,
                                            title = "Studio access removed",
                                            message = "You are no longer a manager of a studio.",
                                            dedupeId = "manager_removed_${studioId}_$targetUid"
                                        )
                                        onSuccess()
                                    }
                                    .addOnFailureListener { error -> onFailure(error.message ?: "Failed to remove manager") }
                            }
                            .addOnFailureListener { error -> onFailure(error.message ?: "Failed to load user") }
                    }
                    .addOnFailureListener { error -> onFailure(error.message ?: "Failed to load studio") }
            },
            onFailure = onFailure
        )
    }

    // Transfers studio ownership to another user, keeping them as a manager as well.
    fun transferStudioOwner(studioId: String, newOwnerUid: String, onSuccess: () -> Unit, onFailure: (String) -> Unit) {
        ensureAdmin(
            onSuccess = { adminUid ->
                if (studioId.isBlank() || newOwnerUid.isBlank()) {
                    onFailure("Missing studio or user id")
                    return@ensureAdmin
                }
                db.collection(Constants.Collections.USERS).document(newOwnerUid).get()
                    .addOnSuccessListener { userDocument ->
                        val targetName = fullNameFrom(userDocument)
                        val grantRef = db.collection(Constants.Collections.PERMISSION_GRANTS).document()
                        db.runBatch { batch ->
                            batch.set(
                                db.collection(Constants.Collections.STUDIOS).document(studioId),
                                mapOf(
                                    "ownerUid" to newOwnerUid,
                                    "managerUids" to FieldValue.arrayUnion(newOwnerUid)
                                ),
                                SetOptions.merge()
                            )
                            batch.set(
                                db.collection(Constants.Collections.USERS).document(newOwnerUid),
                                mapOf(
                                    "managedStudioIds" to FieldValue.arrayUnion(studioId),
                                    "professionalBadges" to FieldValue.arrayUnion("Studio Manager")
                                ),
                                SetOptions.merge()
                            )
                            batch.set(grantRef, permissionGrantMap(grantRef.id, adminUid, newOwnerUid, targetName, PermissionGrant.Actions.SET_OWNER, studioId, ""))
                        }
                            .addOnSuccessListener { onSuccess() }
                            .addOnFailureListener { error -> onFailure(error.message ?: "Failed to transfer ownership") }
                    }
                    .addOnFailureListener { error -> onFailure(error.message ?: "Failed to load user") }
            },
            onFailure = onFailure
        )
    }

    // Verifies or unverifies a studio's business badge.
    fun setStudioVerified(studioId: String, verified: Boolean, onSuccess: () -> Unit, onFailure: (String) -> Unit) {
        ensureAdmin(
            onSuccess = { adminUid ->
                if (studioId.isBlank()) {
                    onFailure("Missing studio id")
                    return@ensureAdmin
                }
                val grantRef = db.collection(Constants.Collections.PERMISSION_GRANTS).document()
                db.runBatch { batch ->
                    batch.set(
                        db.collection(Constants.Collections.STUDIOS).document(studioId),
                        mapOf("verified" to verified, "updatedAt" to FieldValue.serverTimestamp()),
                        SetOptions.merge()
                    )
                    batch.set(
                        grantRef,
                        permissionGrantMap(
                            grantRef.id, adminUid, "", "",
                            if (verified) PermissionGrant.Actions.VERIFY_STUDIO else PermissionGrant.Actions.UNVERIFY_STUDIO,
                            studioId, ""
                        )
                    )
                }
                    .addOnSuccessListener { onSuccess() }
                    .addOnFailureListener { error -> onFailure(error.message ?: "Failed to update studio") }
            },
            onFailure = onFailure
        )
    }

    // Loads a user plus every studio they currently manage, for the permissions editor.
    fun loadUserPermissions(uid: String, onSuccess: (User, List<Studio>) -> Unit, onFailure: (String) -> Unit) {
        ensureAdmin(
            onSuccess = {
                db.collection(Constants.Collections.USERS).document(uid).get()
                    .addOnSuccessListener { userDocument ->
                        val user = userDocument.toObject(User::class.java)?.copy(uid = userDocument.id)
                        if (user == null) {
                            onFailure("User was not found")
                            return@addOnSuccessListener
                        }
                        val ids = user.managedStudioIds.filter { it.isNotBlank() }
                        if (ids.isEmpty()) {
                            onSuccess(user, emptyList())
                            return@addOnSuccessListener
                        }
                        StudioRepository().loadStudiosByIds(
                            ids = ids,
                            onSuccess = { studios -> onSuccess(user, studios) },
                            onFailure = { onSuccess(user, emptyList()) }
                        )
                    }
                    .addOnFailureListener { error -> onFailure(error.message ?: "Failed to load user") }
            },
            onFailure = onFailure
        )
    }

    // Turns an approved "create new studio" request into a real studio document, owned and
    // managed by whoever requested it.
    private fun approveCreateRequest(
        request: StudioRequest,
        adminUid: String,
        onSuccess: () -> Unit,
        onFailure: (String) -> Unit
    ) {
        val requestRef = requestDocRef(request)
        val studioRef = db.collection(Constants.Collections.STUDIOS).document()
        val userRef = db.collection(Constants.Collections.USERS).document(request.requesterUid)
        val grantRef = db.collection(Constants.Collections.PERMISSION_GRANTS).document()
        // A studio created this way (as opposed to claiming a Google-sourced listing) has no
        // geocoded address, so it would otherwise never have coordinates and stay permanently
        // invisible on the map. The city it was created in already has known coordinates, so use
        // those as an approximate pin rather than leaving it with none at all.
        val cityCoordinates = CityOptions.cityFor(request.draftCity)

        db.runBatch { batch ->
            batch.update(
                requestRef,
                mapOf(
                    "status" to StudioRequest.STATUS_APPROVED,
                    "reviewedAt" to FieldValue.serverTimestamp(),
                    "reviewedByUid" to adminUid,
                    "resultStudioId" to studioRef.id
                )
            )
            batch.set(
                studioRef,
                mapOf(
                    "id" to studioRef.id,
                    "displayName" to request.draftDisplayName,
                    "searchName" to request.draftDisplayName.lowercase(),
                    "city" to request.draftCity,
                    "address" to request.draftAddress,
                    "location" to request.draftCity,
                    "latitude" to cityCoordinates?.latitude,
                    "longitude" to cityCoordinates?.longitude,
                    "bio" to request.draftBio,
                    "danceStyles" to request.draftDanceStyles,
                    "websiteUrl" to request.draftWebsiteUrl,
                    "contactPhone" to request.draftContactPhone,
                    "contactEmail" to request.draftContactEmail,
                    "socialLinks" to request.draftSocialLinks,
                    "ownerUid" to request.requesterUid,
                    "managerUids" to listOf(request.requesterUid),
                    "verified" to true,
                    "status" to Constants.StudioStatus.APPROVED.name,
                    "claimStatus" to "CLAIMED",
                    "claimUpdatedAt" to Timestamp.now(),
                    "createdByUid" to request.requesterUid,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            batch.set(
                userRef,
                mapOf(
                    "managedStudioIds" to FieldValue.arrayUnion(studioRef.id),
                    "professionalBadges" to FieldValue.arrayUnion("Studio Manager")
                ),
                SetOptions.merge()
            )
            batch.set(grantRef, permissionGrantMap(grantRef.id, adminUid, request.requesterUid, request.requesterName, PermissionGrant.Actions.APPROVE_STUDIO_REQUEST, studioRef.id, ""))
        }
            .addOnSuccessListener {
                notifyStudioRequestDecision(request, adminUid, approved = true, resultStudioId = studioRef.id)
                onSuccess()
            }
            .addOnFailureListener { error -> onFailure(error.message ?: "Failed to approve request") }
    }

    // Turns an approved claim request into ownership of a studio. Covers two cases: claiming a
    // studio that already exists in our own STUDIOS collection, and claiming a place that was
    // only ever a Google Places result until now, which needs a brand new studio document.
    private fun approveClaimRequest(
        request: StudioRequest,
        adminUid: String,
        onSuccess: () -> Unit,
        onFailure: (String) -> Unit
    ) {
        if (request.studioId.isBlank()) {
            onFailure("Claim is missing required data")
            return
        }

        val requestRef = requestDocRef(request)
        val isExternalClaim = request.googlePlaceId.isNotBlank() && request.studioId.startsWith("google_")
        val studioRef = if (isExternalClaim) {
            db.collection(Constants.Collections.STUDIOS).document()
        } else {
            db.collection(Constants.Collections.STUDIOS).document(request.studioId)
        }
        val userRef = db.collection(Constants.Collections.USERS).document(request.requesterUid)
        val grantRef = db.collection(Constants.Collections.PERMISSION_GRANTS).document()

        db.runBatch { batch ->
            batch.update(
                requestRef,
                mapOf(
                    "status" to StudioRequest.STATUS_APPROVED,
                    "reviewedAt" to FieldValue.serverTimestamp(),
                    "reviewedByUid" to adminUid,
                    "resultStudioId" to studioRef.id
                )
            )
            if (isExternalClaim) {
                // The request only ever carried the studio's name/address as bare text before -
                // an approved external claim would create a studio with no coordinates and no
                // city, silently undermining every distance-based ranking feature from then on.
                // latitude/longitude/coverImageUrl now come straight from the Google Places
                // result the requester was actually looking at; city is a best-effort guess from
                // the address text since Google's formatted address has no separate city field.
                val guessedCity = CityOptions.guessCityFromAddress(request.address)
                batch.set(
                    studioRef,
                    mapOf(
                        "id" to studioRef.id,
                        "displayName" to request.studioName,
                        "address" to request.address,
                        "city" to guessedCity?.displayName.orEmpty(),
                        "location" to (guessedCity?.displayName ?: request.address),
                        "latitude" to (request.latitude ?: guessedCity?.latitude),
                        "longitude" to (request.longitude ?: guessedCity?.longitude),
                        "coverImageUrl" to request.coverImageUrl,
                        "ownerUid" to request.requesterUid,
                        "managerUids" to listOf(request.requesterUid),
                        "googlePlaceId" to request.googlePlaceId,
                        "externalSource" to "google",
                        "claimStatus" to "CLAIMED",
                        "claimUpdatedAt" to Timestamp.now(),
                        "status" to Constants.StudioStatus.APPROVED.name,
                        "verified" to true
                    ),
                    SetOptions.merge()
                )
                batch.set(
                    db.collection(Constants.Collections.EXTERNAL_STUDIOS).document(request.googlePlaceId),
                    mapOf(
                        "googlePlaceId" to request.googlePlaceId,
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
                        "ownerUid" to request.requesterUid,
                        "managerUids" to FieldValue.arrayUnion(request.requesterUid),
                        "claimStatus" to "CLAIMED",
                        "claimUpdatedAt" to Timestamp.now(),
                        "status" to Constants.StudioStatus.APPROVED.name,
                        "verified" to true
                    ),
                    SetOptions.merge()
                )
            }
            batch.set(
                userRef,
                mapOf(
                    "managedStudioIds" to FieldValue.arrayUnion(studioRef.id),
                    "professionalBadges" to FieldValue.arrayUnion("Studio Manager")
                ),
                SetOptions.merge()
            )
            batch.set(grantRef, permissionGrantMap(grantRef.id, adminUid, request.requesterUid, request.requesterName, PermissionGrant.Actions.APPROVE_STUDIO_REQUEST, studioRef.id, ""))
        }
            .addOnSuccessListener {
                notifyStudioRequestDecision(request, adminUid, approved = true, resultStudioId = studioRef.id)
                onSuccess()
            }
            .addOnFailureListener { error -> onFailure(error.message ?: "Failed to approve studio claim") }
    }

    // Shared implementation behind the grant/revoke pairs above - flips the given boolean field
    // on the user, keeps their professionalBadges list in sync with it, and logs a permission
    // grant record so there's an audit trail of who granted what and when.
    private fun setBooleanPermission(
        targetUid: String,
        field: String,
        value: Boolean,
        action: String,
        badge: String,
        note: String,
        onSuccess: () -> Unit,
        onFailure: (String) -> Unit
    ) {
        ensureAdmin(
            onSuccess = { adminUid ->
                if (targetUid.isBlank()) {
                    onFailure("Missing user id")
                    return@ensureAdmin
                }
                db.collection(Constants.Collections.USERS).document(targetUid).get()
                    .addOnSuccessListener { userDocument ->
                        val targetName = fullNameFrom(userDocument)
                        val userUpdates = mutableMapOf<String, Any>(field to value)
                        userUpdates["professionalBadges"] = if (value) {
                            FieldValue.arrayUnion(badge)
                        } else {
                            FieldValue.arrayRemove(badge)
                        }
                        val grantRef = db.collection(Constants.Collections.PERMISSION_GRANTS).document()
                        db.runBatch { batch ->
                            batch.set(db.collection(Constants.Collections.USERS).document(targetUid), userUpdates, SetOptions.merge())
                            batch.set(grantRef, permissionGrantMap(grantRef.id, adminUid, targetUid, targetName, action, "", note))
                        }
                            .addOnSuccessListener {
                                notificationRepository.createNotification(
                                    recipientUid = targetUid,
                                    type = if (value) InAppNotification.Types.PERMISSION_GRANTED else InAppNotification.Types.PERMISSION_REVOKED,
                                    actorId = adminUid,
                                    title = if (value) "Permission granted" else "Permission updated",
                                    message = if (value) "You are now a $badge." else "Your $badge status was removed.",
                                    dedupeId = "${action}_$targetUid"
                                )
                                onSuccess()
                            }
                            .addOnFailureListener { error -> onFailure(error.message ?: "Failed to update permission") }
                    }
                    .addOnFailureListener { error -> onFailure(error.message ?: "Failed to load user") }
            },
            onFailure = onFailure
        )
    }

    // Studio requests can live in one of two collections depending on whether they came from the
    // newer flow or the old legacy claims flow, so this just points at whichever one actually owns
    // this request.
    private fun requestDocRef(request: StudioRequest) =
        if (request.sourceCollection == StudioRequest.SOURCE_LEGACY_STUDIO_CLAIMS) {
            db.collection(Constants.Collections.STUDIO_CLAIMS).document(request.requestId)
        } else {
            db.collection(Constants.Collections.STUDIO_REQUESTS).document(request.requestId)
        }

    // Tells the requester whether their studio create/claim request was approved or rejected.
    private fun notifyStudioRequestDecision(request: StudioRequest, adminUid: String, approved: Boolean, resultStudioId: String = "") {
        val title = if (request.type == StudioRequest.TYPE_CREATE) "Studio request" else "Studio claim"
        val statusText = if (approved) "approved" else "rejected"
        notificationRepository.createNotification(
            recipientUid = request.requesterUid,
            type = if (approved) InAppNotification.Types.STUDIO_REQUEST_APPROVED else InAppNotification.Types.STUDIO_REQUEST_REJECTED,
            actorId = adminUid,
            studioId = resultStudioId,
            title = "$title $statusText",
            message = "Your request for ${request.draftDisplayName.ifBlank { request.studioName }.ifBlank { "a studio" }} was $statusText.",
            dedupeId = "studio_request_${request.requestId}_$statusText"
        )
    }

    // Builds the record we write to PERMISSION_GRANTS every time an admin changes someone's
    // access, so there's a permanent log of who did what to whom.
    private fun permissionGrantMap(
        grantId: String,
        adminUid: String,
        targetUid: String,
        targetName: String,
        action: String,
        studioId: String,
        note: String
    ): Map<String, Any> {
        return mapOf(
            "grantId" to grantId,
            "adminUid" to adminUid,
            "targetUid" to targetUid,
            "targetName" to targetName,
            "action" to action,
            "studioId" to studioId,
            "note" to note,
            "createdAt" to FieldValue.serverTimestamp()
        )
    }

    // Pulls a display name out of a user document for notification/grant text, without blowing up
    // if first or last name happens to be missing.
    private fun fullNameFrom(document: DocumentSnapshot): String {
        return listOf(document.getString("firstName").orEmpty(), document.getString("lastName").orEmpty())
            .filter { it.isNotBlank() }
            .joinToString(" ")
    }

    // Gatekeeper for every function in this class - checks that whoever is signed in actually
    // has the admin role before letting any permission-changing code run.
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

    // Maps an application type to the fields it grants on approval. Returns null for the studio
    // application type, since studio access only ever comes through the studio request flow -
    // that keeps this function from accidentally handing out studio permissions from the wrong path.
    private fun professionalApprovalUpdates(applicationType: String): Map<String, Any>? {
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
            else -> null
        }
    }

    // Old professional applications could be filed with a "studio" type before studio requests
    // got their own dedicated flow. Those are filtered out of the pending applications list since
    // studio access is now handled entirely through studio requests instead.
    private fun isLegacyStudioApplication(applicationType: String): Boolean {
        @Suppress("DEPRECATION")
        return applicationType.equals(Constants.ProfessionalApplicationType.STUDIO.firestoreValue, ignoreCase = true)
    }

    // Tells the applicant whether their Verified Teacher / Choreographer application was
    // approved or rejected.
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

    // Turns the raw application type value into the human-readable label used in notification text.
    private fun applicationTypeLabel(type: String): String {
        return when {
            type.equals(Constants.ProfessionalApplicationType.VERIFIED_TEACHER.firestoreValue, ignoreCase = true) -> "Verified Teacher"
            type.equals(Constants.ProfessionalApplicationType.CHOREOGRAPHER.firestoreValue, ignoreCase = true) -> "Choreographer"
            else -> "Studio / Dance School"
        }
    }

    // Role is stored inconsistently across older and newer accounts (plain enum name vs. the
    // Firestore-facing value), so this checks both forms rather than assuming one.
    private fun String.isAdminRole(): Boolean {
        return equals(Constants.UserRole.ADMIN.name, ignoreCase = true) ||
            equals(Constants.UserRole.ADMIN.firestoreValue, ignoreCase = true)
    }

    // Everything the admin review screen needs in one bundle, plus which sections (if any)
    // failed to load.
    data class AdminReviewData(
        val studioRequests: List<StudioRequest> = emptyList(),
        val professionalApplications: List<ProfessionalApplication> = emptyList(),
        val contentReports: List<ContentReport> = emptyList(),
        // Which sections failed to load, so the UI can show a friendly per-section "couldn't
        // load, tap to retry" instead of a raw Firestore error string.
        val failedSections: Set<ReviewSection> = emptySet()
    )

    enum class ReviewSection {
        STUDIO_REQUESTS,
        PROFESSIONAL_APPLICATIONS,
        CONTENT_REPORTS
    }
}
