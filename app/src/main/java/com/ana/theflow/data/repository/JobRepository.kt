package com.ana.theflow.data.repository

import com.ana.theflow.data.model.jobs.DanceJob
import com.ana.theflow.data.model.jobs.JobApplication
import com.ana.theflow.data.model.notification.InAppNotification
import com.ana.theflow.data.model.user.User
import com.ana.theflow.utilities.Constants
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions

class JobRepository {

    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()
    private val userRepository = UserRepository()
    private val notificationRepository = NotificationRepository()

    fun loadActiveJobs(
        query: String = "",
        city: String = "",
        style: String = "",
        jobType: String = "",
        workType: String = "",
        onSuccess: (List<DanceJob>) -> Unit,
        onFailure: (String) -> Unit
    ) {
        db.collection(Constants.Collections.JOBS)
            .whereEqualTo("status", DanceJob.STATUS_ACTIVE)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(80)
            .get()
            .addOnSuccessListener { snapshot ->
                val normalized = query.trim()
                val jobs = snapshot.documents.mapNotNull { it.toJob() }
                    .filter { job ->
                        (normalized.isBlank() || job.matches(normalized)) &&
                            (city.isBlank() || job.city.equals(city, ignoreCase = true)) &&
                            (style.isBlank() || job.danceStyles.any { it.equals(style, ignoreCase = true) }) &&
                            (jobType.isBlank() || job.jobType.equals(jobType, ignoreCase = true)) &&
                            (workType.isBlank() || job.workType.equals(workType, ignoreCase = true))
                    }
                onSuccess(jobs)
            }
            .addOnFailureListener { error -> onFailure(error.message ?: "Failed to load jobs") }
    }

    fun loadJob(jobId: String, onSuccess: (DanceJob) -> Unit, onFailure: (String) -> Unit) {
        if (jobId.isBlank()) {
            onFailure("Missing job id")
            return
        }
        db.collection(Constants.Collections.JOBS).document(jobId).get()
            .addOnSuccessListener { document ->
                document.toJob()?.let(onSuccess) ?: onFailure("Job was not found")
            }
            .addOnFailureListener { error -> onFailure(error.message ?: "Failed to load job") }
    }

    fun canPublishJobs(onSuccess: (Boolean) -> Unit, onFailure: (String) -> Unit = {}) {
        val uid = auth.currentUser?.uid
        if (uid == null) {
            onSuccess(false)
            return
        }
        db.collection(Constants.Collections.USERS).document(uid).get()
            .addOnSuccessListener { document -> onSuccess(document.canPublishJobs()) }
            .addOnFailureListener { error -> onFailure(error.message ?: "Failed to load job permissions") }
    }

    fun createJob(
        title: String,
        employerName: String,
        city: String,
        danceStyles: List<String>,
        description: String,
        onSuccess: (String) -> Unit,
        onFailure: (String) -> Unit
    ) {
        val uid = auth.currentUser?.uid
        if (uid == null) {
            onFailure("User is not logged in")
            return
        }
        val cleanTitle = title.trim()
        if (cleanTitle.isBlank() || employerName.trim().isBlank() || city.trim().isBlank() || description.trim().isBlank()) {
            onFailure("Add a title, employer, city, and description")
            return
        }
        db.collection(Constants.Collections.USERS).document(uid).get()
            .addOnSuccessListener { userDocument ->
                if (!userDocument.canPublishJobs()) {
                    onFailure("Only verified choreographers or authorized studio managers can post jobs")
                    return@addOnSuccessListener
                }
                val managedStudioIds = ((userDocument.get("managedStudioIds") as? List<*>).orEmpty())
                    .mapNotNull { it as? String }
                    .filter { it.isNotBlank() }
                val verifiedChoreographer = userDocument.getBoolean("verifiedChoreographer") == true
                val studioId = if (verifiedChoreographer) "" else managedStudioIds.firstOrNull().orEmpty()
                val docRef = db.collection(Constants.Collections.JOBS).document()
                val job = mapOf(
                    "jobId" to docRef.id,
                    "title" to cleanTitle,
                    "employerName" to employerName.trim(),
                    "employerImageUrl" to userDocument.getString("profileImageUrl").orEmpty(),
                    "city" to city.trim(),
                    "location" to city.trim(),
                    "workType" to DanceJob.WORK_ON_SITE,
                    "jobType" to DanceJob.TYPE_FREELANCE,
                    "danceStyles" to danceStyles.filter { it.isNotBlank() }.ifEmpty { listOf("Dance") },
                    "experienceLevel" to "Open level",
                    "description" to description.trim(),
                    "requirements" to emptyList<String>(),
                    "paymentText" to "",
                    "contactMethod" to "",
                    "externalApplyUrl" to "",
                    "status" to DanceJob.STATUS_ACTIVE,
                    "creatorId" to uid,
                    "studioId" to studioId,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                docRef.set(job)
                    .addOnSuccessListener { onSuccess(docRef.id) }
                    .addOnFailureListener { error -> onFailure(error.message ?: "Failed to create job") }
            }
            .addOnFailureListener { error -> onFailure(error.message ?: "Failed to load job permissions") }
    }

    fun toggleSaveJob(job: DanceJob, onSuccess: (Boolean) -> Unit, onFailure: (String) -> Unit) {
        val uid = auth.currentUser?.uid
        if (uid == null) {
            onFailure("User is not logged in")
            return
        }
        if (job.jobId.isBlank()) {
            onFailure("Missing job id")
            return
        }
        val ref = db.collection(Constants.Collections.USERS)
            .document(uid)
            .collection(Constants.Collections.SAVED_JOBS)
            .document(job.jobId)
        ref.get().addOnSuccessListener { snapshot ->
            if (snapshot.exists()) {
                ref.delete()
                    .addOnSuccessListener { onSuccess(false) }
                    .addOnFailureListener { error -> onFailure(error.message ?: "Failed to unsave job") }
            } else {
                ref.set(job.savedSummary(), SetOptions.merge())
                    .addOnSuccessListener { onSuccess(true) }
                    .addOnFailureListener { error -> onFailure(error.message ?: "Failed to save job") }
            }
        }.addOnFailureListener { error -> onFailure(error.message ?: "Failed to load saved job state") }
    }

    fun isJobSaved(jobId: String, onSuccess: (Boolean) -> Unit, onFailure: (String) -> Unit = {}) {
        val uid = auth.currentUser?.uid
        if (uid == null || jobId.isBlank()) {
            onSuccess(false)
            return
        }
        db.collection(Constants.Collections.USERS).document(uid)
            .collection(Constants.Collections.SAVED_JOBS).document(jobId)
            .get()
            .addOnSuccessListener { onSuccess(it.exists()) }
            .addOnFailureListener { error -> onFailure(error.message ?: "Failed to load saved state") }
    }

    fun loadSavedJobs(onSuccess: (List<DanceJob>) -> Unit, onFailure: (String) -> Unit) {
        val uid = auth.currentUser?.uid
        if (uid == null) {
            onFailure("User is not logged in")
            return
        }
        db.collection(Constants.Collections.USERS).document(uid)
            .collection(Constants.Collections.SAVED_JOBS)
            .orderBy("savedAt", Query.Direction.DESCENDING)
            .limit(40)
            .get()
            .addOnSuccessListener { snapshot ->
                val ids = snapshot.documents.map { document ->
                    document.getString("jobId").orEmpty().ifBlank { document.id }
                }.filter { it.isNotBlank() }
                loadJobsByIds(ids, onSuccess, onFailure)
            }
            .addOnFailureListener { error -> onFailure(error.message ?: "Failed to load saved jobs") }
    }

    fun submitApplication(
        job: DanceJob,
        applicant: User,
        introduction: String,
        experience: String,
        portfolioUrl: String,
        onSuccess: () -> Unit,
        onFailure: (String) -> Unit
    ) {
        val uid = auth.currentUser?.uid
        if (uid == null) {
            onFailure("User is not logged in")
            return
        }
        if (applicant.uid.isNotBlank() && applicant.uid != uid) {
            onFailure("Applicant does not match the signed-in user")
            return
        }
        if (job.status != DanceJob.STATUS_ACTIVE) {
            onFailure("This job is not accepting applications")
            return
        }
        val cleanIntro = introduction.trim()
        if (cleanIntro.isBlank()) {
            onFailure("Add a short introduction")
            return
        }
        val applicationId = "${job.jobId}_$uid"
        val data = mapOf(
            "applicationId" to applicationId,
            "jobId" to job.jobId,
            "applicantId" to uid,
            "applicantName" to "${applicant.firstName} ${applicant.lastName}".trim().ifBlank { "Dancer" },
            "introduction" to cleanIntro,
            "experience" to experience.trim(),
            "portfolioUrl" to portfolioUrl.trim(),
            "status" to JobApplication.STATUS_SUBMITTED,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        db.collection(Constants.Collections.JOB_APPLICATIONS)
            .document(applicationId)
            .set(data, SetOptions.merge())
            .addOnSuccessListener {
                notificationRepository.createNotification(
                    recipientUid = job.creatorId,
                    type = InAppNotification.Types.JOB_APPLICATION_RECEIVED,
                    actorId = uid,
                    actorName = data["applicantName"] as String,
                    applicationId = applicationId,
                    title = "New job application",
                    message = "New application for ${job.title}.",
                    dedupeId = "job_application_$applicationId"
                )
                onSuccess()
            }
            .addOnFailureListener { error -> onFailure(error.message ?: "Failed to submit application") }
    }

    fun loadMyApplications(onSuccess: (List<JobApplication>) -> Unit, onFailure: (String) -> Unit) {
        val uid = auth.currentUser?.uid
        if (uid == null) {
            onFailure("User is not logged in")
            return
        }
        db.collection(Constants.Collections.JOB_APPLICATIONS)
            .whereEqualTo("applicantId", uid)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(40)
            .get()
            .addOnSuccessListener { snapshot -> onSuccess(snapshot.documents.mapNotNull { it.toApplication() }) }
            .addOnFailureListener { error -> onFailure(error.message ?: "Failed to load applications") }
    }

    fun loadMyListings(onSuccess: (List<DanceJob>) -> Unit, onFailure: (String) -> Unit) {
        val uid = auth.currentUser?.uid
        if (uid == null) {
            onFailure("User is not logged in")
            return
        }
        db.collection(Constants.Collections.JOBS)
            .whereEqualTo("creatorId", uid)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(40)
            .get()
            .addOnSuccessListener { snapshot -> onSuccess(snapshot.documents.mapNotNull { it.toJob() }) }
            .addOnFailureListener { error -> onFailure(error.message ?: "Failed to load listings") }
    }

    fun withdrawApplication(applicationId: String, onSuccess: () -> Unit, onFailure: (String) -> Unit) {
        if (applicationId.isBlank()) {
            onFailure("Missing application id")
            return
        }
        db.collection(Constants.Collections.JOB_APPLICATIONS)
            .document(applicationId)
            .update(
                mapOf(
                    "status" to JobApplication.STATUS_WITHDRAWN,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { error -> onFailure(error.message ?: "Failed to withdraw application") }
    }

    private fun loadJobsByIds(ids: List<String>, onSuccess: (List<DanceJob>) -> Unit, onFailure: (String) -> Unit) {
        val clean = ids.distinct().filter { it.isNotBlank() }
        if (clean.isEmpty()) {
            onSuccess(emptyList())
            return
        }
        val order = clean.withIndex().associate { it.value to it.index }
        val chunks = clean.chunked(10)
        val jobs = mutableListOf<DanceJob>()
        var pending = chunks.size
        var completed = false
        chunks.forEach { chunk ->
            db.collection(Constants.Collections.JOBS)
                .whereIn(FieldPath.documentId(), chunk)
                .get()
                .addOnSuccessListener { snapshot ->
                    if (completed) return@addOnSuccessListener
                    jobs.addAll(snapshot.documents.mapNotNull { it.toJob() })
                    pending -= 1
                    if (pending == 0) {
                        completed = true
                        onSuccess(jobs.sortedBy { order[it.jobId] ?: Int.MAX_VALUE })
                    }
                }
                .addOnFailureListener { error ->
                    if (!completed) {
                        completed = true
                        onFailure(error.message ?: "Failed to load jobs")
                    }
                }
        }
    }

    private fun DocumentSnapshot.toJob(): DanceJob? {
        return toObject(DanceJob::class.java)?.copy(jobId = id)
    }

    private fun DocumentSnapshot.toApplication(): JobApplication? {
        return toObject(JobApplication::class.java)?.copy(applicationId = id)
    }

    private fun DocumentSnapshot.canPublishJobs(): Boolean {
        val managedStudioIds = (get("managedStudioIds") as? List<*>).orEmpty()
        return getString("role").orEmpty().equals(Constants.UserRole.ADMIN.firestoreValue, ignoreCase = true) ||
            getBoolean("verifiedChoreographer") == true ||
            managedStudioIds.isNotEmpty()
    }

    private fun DanceJob.matches(query: String): Boolean {
        return listOf(title, employerName, city, location, description, paymentText, experienceLevel)
            .any { it.contains(query, ignoreCase = true) } ||
            requirements.any { it.contains(query, ignoreCase = true) } ||
            danceStyles.any { it.contains(query, ignoreCase = true) }
    }

    private fun DanceJob.savedSummary(): Map<String, Any> {
        return mapOf(
            "jobId" to jobId,
            "title" to title,
            "employerName" to employerName,
            "city" to city,
            "workType" to workType,
            "jobType" to jobType,
            "danceStyles" to danceStyles,
            "status" to status,
            "savedAt" to FieldValue.serverTimestamp()
        )
    }
}
