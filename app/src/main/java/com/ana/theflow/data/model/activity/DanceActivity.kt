// Firestore model for professional dance classes, workshops, and events shown in Discover.
package com.ana.theflow.data.model.activity

import com.google.firebase.Timestamp

// Represents a published or draft activity that can be discovered, searched, and shown on the map.
data class DanceActivity(
    val id: String = "",
    val creatorUid: String = "",
    val instructorUid: String = "",
    val instructorName: String = "",
    val hostStudioId: String = "",
    val hostStudioName: String = "",
    val googlePlaceId: String = "",
    val title: String = "",
    val description: String = "",
    val activityType: String = TYPE_CLASS,
    val styles: List<String> = emptyList(),
    val levels: List<String> = emptyList(),
    val startAt: Timestamp? = null,
    val endAt: Timestamp? = null,
    val timezone: String = "Asia/Jerusalem",
    val recurrence: ActivityRecurrence = ActivityRecurrence(),
    val price: ActivityPrice = ActivityPrice(),
    val registration: ActivityRegistrationInfo = ActivityRegistrationInfo(),
    val location: ActivityLocation = ActivityLocation(),
    val mediaUrls: List<String> = emptyList(),
    val ratingSummary: RatingSummary = RatingSummary(),
    val status: String = STATUS_DRAFT,
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null
) {
    companion object {
        const val TYPE_CLASS = "class"
        const val TYPE_WORKSHOP = "workshop"
        const val TYPE_EVENT = "event"

        const val STATUS_DRAFT = "draft"
        const val STATUS_PUBLISHED = "published"
        const val STATUS_CANCELLED = "cancelled"
        const val STATUS_PAST = "past"
    }
}

// Describes recurring activity rules without requiring a scheduling engine in the first version.
data class ActivityRecurrence(
    val type: String = "none",
    val rule: String = ""
)

// Keeps pricing explicit so free activities display as "Free" instead of numeric zero.
data class ActivityPrice(
    val isFree: Boolean = false,
    val amount: Double? = null,
    val currency: String = "ILS",
    val displayText: String = ""
)

// Stores external registration/contact options. THE FLOW does not process payments in this version.
data class ActivityRegistrationInfo(
    val isOpen: Boolean = true,
    val externalUrl: String = "",
    val whatsapp: String = "",
    val phone: String = "",
    val email: String = "",
    val deadlineAt: Timestamp? = null,
    val capacity: Long? = null,
    val spotsRemaining: Long? = null,
    val showSpotsRemaining: Boolean = false
)

// Separates the activity location from the creator and optional host studio.
data class ActivityLocation(
    val type: String = TYPE_PUBLIC,
    val name: String = "",
    val address: String = "",
    val city: String = "",
    val latitude: Double? = null,
    val longitude: Double? = null,
    val visibility: String = VISIBILITY_PUBLIC
) {
    companion object {
        const val TYPE_PUBLIC = "public"
        const val TYPE_STUDIO = "studio"
        const val TYPE_ONLINE = "online"
        const val TYPE_VARIABLE = "variable"
        const val TYPE_AFTER_REGISTRATION = "after_registration"

        const val VISIBILITY_PUBLIC = "public"
        const val VISIBILITY_AFTER_REGISTRATION = "after_registration"
        const val VISIBILITY_PRIVATE = "private"
    }
}

// Aggregated internal rating data for studios and activities.
data class RatingSummary(
    val average: Double? = null,
    val count: Long = 0
)
