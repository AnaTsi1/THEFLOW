package com.ana.theflow.utilities

object Constants {

    object Collections {
        const val USERS = "users"
        const val STUDIOS = "studios"
        const val STUDIO_REQUESTS = "studioRequests"
    }

    enum class UserRole {
        DANCER,
        STUDIO_OWNER,
        ADMIN
    }

    enum class StudioStatus {
        PENDING,
        APPROVED,
        REJECTED
    }
}
