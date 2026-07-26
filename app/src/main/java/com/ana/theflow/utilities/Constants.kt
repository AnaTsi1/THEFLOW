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
