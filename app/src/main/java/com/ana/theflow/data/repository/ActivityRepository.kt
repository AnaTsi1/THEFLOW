// Repository for Firestore-backed dance activities used by Discover and map search.
package com.ana.theflow.data.repository

import com.ana.theflow.data.model.activity.DanceActivity
import com.ana.theflow.data.model.discovery.DiscoveryItem
import com.ana.theflow.utilities.Constants
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import java.util.Date

// Centralizes activity reads so Discover does not read Firestore activity data directly.
class ActivityRepository {

    private val db = FirebaseFirestore.getInstance()

    // Grabs published dance activities (classes/workshops/events) for recommendations and search.
    fun loadPublishedActivities(
        onSuccess: (List<DanceActivity>) -> Unit,
        onFailure: (String) -> Unit
    ) {
        db.collection(Constants.Collections.ACTIVITIES)
            .whereEqualTo("status", DanceActivity.STATUS_PUBLISHED)
            .orderBy("startAt", Query.Direction.ASCENDING)
            .limit(MAX_DISCOVERY_ACTIVITIES)
            .get()
            .addOnSuccessListener { snapshot ->
                val activities = snapshot.documents.mapNotNull { document ->
                    document.toObject(DanceActivity::class.java)?.copy(id = document.id)
                }
                onSuccess(activities)
            }
            .addOnFailureListener { error ->
                onFailure(error.message ?: "Failed to load activities")
            }
    }

    // Same as above but only activities starting in the next 7 days, for the "Events This Week" section.
    fun loadActivitiesThisWeek(
        onSuccess: (List<DanceActivity>) -> Unit,
        onFailure: (String) -> Unit
    ) {
        val now = Timestamp.now()
        val weekFromNow = Timestamp(Date(now.toDate().time + WEEK_MILLIS))
        db.collection(Constants.Collections.ACTIVITIES)
            .whereEqualTo("status", DanceActivity.STATUS_PUBLISHED)
            .whereGreaterThanOrEqualTo("startAt", now)
            .whereLessThanOrEqualTo("startAt", weekFromNow)
            .orderBy("startAt", Query.Direction.ASCENDING)
            .limit(MAX_DISCOVERY_ACTIVITIES)
            .get()
            .addOnSuccessListener { snapshot ->
                val activities = snapshot.documents.mapNotNull { document ->
                    document.toObject(DanceActivity::class.java)?.copy(id = document.id)
                }
                onSuccess(activities)
            }
            .addOnFailureListener { error ->
                onFailure(error.message ?: "Failed to load this week's events")
            }
    }

    // Converts activities into the same DiscoveryItem shape studios and Google Places results
    // use, so they can all show up side by side in the same cards.
    fun toDiscoveryItems(activities: List<DanceActivity>): List<DiscoveryItem> {
        val dayFormat = java.text.SimpleDateFormat("d", java.util.Locale.US)
        val monthFormat = java.text.SimpleDateFormat("MMM", java.util.Locale.US)
        return activities.map { activity ->
            val startDate = activity.startAt?.toDate()
            DiscoveryItem(
                id = activity.id,
                title = activity.title,
                studio = activity.hostStudioName.ifBlank { activity.location.name },
                teacher = activity.instructorName,
                style = activity.styles.firstOrNull().orEmpty().ifBlank { "Dance" },
                level = activity.levels.firstOrNull().orEmpty().ifBlank { "All levels" },
                location = activity.location.city.ifBlank { activity.location.address },
                time = activity.startAt?.toDate()?.toString().orEmpty().ifBlank { "Schedule pending" },
                type = activity.activityType.replaceFirstChar { it.uppercase() },
                latitude = activity.location.latitude,
                longitude = activity.location.longitude,
                source = DiscoveryItem.SOURCE_INTERNAL,
                googlePlaceId = activity.googlePlaceId,
                address = activity.location.address,
                rating = activity.ratingSummary.average,
                ratingCount = activity.ratingSummary.count.toInt(),
                coverImageUrl = activity.mediaUrls.firstOrNull().orEmpty(),
                priceText = activity.price.displayText.ifBlank {
                    when {
                        activity.price.isFree -> "Free"
                        activity.price.amount != null -> "${activity.price.amount} ${activity.price.currency}"
                        else -> ""
                    }
                },
                dateTimeText = activity.startAt?.toDate()?.toString().orEmpty(),
                displayType = activity.activityType,
                eventDayOfMonth = startDate?.let { dayFormat.format(it) }.orEmpty(),
                eventMonthAbbrev = startDate?.let { monthFormat.format(it) }.orEmpty()
            )
        }
    }

    companion object {
        private const val MAX_DISCOVERY_ACTIVITIES = 80L
        private const val WEEK_MILLIS = 7L * 24 * 60 * 60 * 1000
    }
}
