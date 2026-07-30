package com.ana.theflow.utilities

object Constants {

    object Collections {
        const val USERS = "users"
        const val POSTS = "posts"
        const val STUDIOS = "studios"
        const val STUDIO_CLAIMS = "studioClaims"
        const val PROFESSIONAL_APPLICATIONS = "professionalApplications"
        const val STUDIO_APPLICATIONS = "studioApplications"
        const val USER_ACTIVITY_EVENTS = "userActivityEvents"
        const val EXTERNAL_STUDIOS = "externalStudios"
        const val CONVERSATIONS = "conversations"
        const val MESSAGES = "messages"
        const val NOTIFICATIONS = "notifications"
        const val CONTENT_REPORTS = "contentReports"
        const val ACCOUNT_DELETION_REQUESTS = "accountDeletionRequests"
        const val ACTIVITIES = "activities"
        const val JOBS = "jobs"
        const val JOB_APPLICATIONS = "jobApplications"
        const val SAVED_JOBS = "savedJobs"
        const val REVIEWS = "reviews"
    }

    enum class UserRole(val firestoreValue: String) {
        DANCER("dancer"),
        STUDIO_MANAGER("studio_manager"),
        ADMIN("admin")
    }

    enum class ProfessionalApplicationType(val firestoreValue: String) {
        VERIFIED_TEACHER("verified_teacher"),
        CHOREOGRAPHER("choreographer"),
        STUDIO("studio")
    }

    enum class StudioStatus {
        PENDING,
        APPROVED,
        REJECTED
    }
}
