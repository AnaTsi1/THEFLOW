// Firestore models for the Jobs feature: a job listing posted by a studio business account, and
// an application a dancer submits against one.
package com.ana.theflow.data.model.jobs

import com.google.firebase.Timestamp

// A job/audition/role listing, posted by a studio business account.
data class DanceJob(
    val jobId: String = "",
    val title: String = "",
    val employerName: String = "",
    val employerImageUrl: String = "",
    val city: String = "",
    val location: String = "",
    val workType: String = WORK_ON_SITE,
    val jobType: String = TYPE_FREELANCE,
    val danceStyles: List<String> = emptyList(),
    val experienceLevel: String = "",
    val description: String = "",
    val requirements: List<String> = emptyList(),
    val paymentText: String = "",
    val deadlineAt: Timestamp? = null,
    val contactMethod: String = "",
    val externalApplyUrl: String = "",
    val status: String = STATUS_ACTIVE,
    val creatorId: String = "",
    val studioId: String = "",
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null
) {
    companion object {
        const val STATUS_ACTIVE = "active"
        const val STATUS_CLOSED = "closed"
        const val STATUS_FILLED = "filled"

        const val WORK_REMOTE = "remote"
        const val WORK_ON_SITE = "on_site"
        const val WORK_HYBRID = "hybrid"

        const val TYPE_FULL_TIME = "full_time"
        const val TYPE_PART_TIME = "part_time"
        const val TYPE_TEMPORARY = "temporary"
        const val TYPE_FREELANCE = "freelance"
        const val TYPE_ONE_TIME = "one_time"
    }
}

// One dancer's application to a specific job.
data class JobApplication(
    val applicationId: String = "",
    val jobId: String = "",
    val studioId: String = "",
    val applicantId: String = "",
    val applicantName: String = "",
    val introduction: String = "",
    val experience: String = "",
    val portfolioUrl: String = "",
    val status: String = STATUS_SUBMITTED,
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null
) {
    companion object {
        const val STATUS_SUBMITTED = "submitted"
        const val STATUS_VIEWED = "viewed"
        const val STATUS_CONTACTED = "contacted"
        const val STATUS_ACCEPTED = "accepted"
        const val STATUS_REJECTED = "rejected"
        const val STATUS_WITHDRAWN = "withdrawn"
    }
}
