// Repository for Firestore-backed dance activities used by Discover and map search.
package com.ana.theflow.data.repository

import com.ana.theflow.data.model.activity.ActivityLocation
import com.ana.theflow.data.model.activity.DanceActivity
import com.ana.theflow.data.model.discovery.DiscoveryItem
import com.ana.theflow.utilities.Constants
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions

// Centralizes activity reads and writes so Discover does not write Firestore data directly.
class ActivityRepository {

    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()

    // Creates a professional activity after verifying the signed-in user's role and badges.
    fun createActivity(
        activity: DanceActivity,
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
            .addOnSuccessListener { userDocument ->
                if (!userDocument.isApprovedProfessional()) {
                    onFailure("Only approved professionals can create activities")
                    return@addOnSuccessListener
                }

                val docRef = db.collection(Constants.Collections.ACTIVITIES).document()
                val activityToSave = activity.copy(
                    id = docRef.id,
                    creatorUid = uid
                ).toFirestoreMap() + mapOf(
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )

                docRef.set(activityToSave, SetOptions.merge())
                    .addOnSuccessListener { onSuccess(docRef.id) }
                    .addOnFailureListener { error ->
                        onFailure(error.message ?: "Failed to create activity")
                    }
            }
            .addOnFailureListener { error ->
                onFailure(error.message ?: "Failed to load user permissions")
            }
    }

    // Loads published activities for recommendations and search.
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

    // Searches loaded activities locally so internal results can render before external Google results.
    fun filterActivities(
        activities: List<DanceActivity>,
        query: String,
        style: String,
        level: String,
        city: String
    ): List<DanceActivity> {
        val text = query.trim()
        return activities.filter { activity ->
            activity.status == DanceActivity.STATUS_PUBLISHED &&
                (style.isBlank() || activity.styles.any { it.contains(style, ignoreCase = true) }) &&
                (level.isBlank() || activity.levels.any { it.contains(level, ignoreCase = true) }) &&
                (city.isBlank() || activity.location.city.equals(city, ignoreCase = true)) &&
                (
                    text.isBlank() ||
                        activity.title.contains(text, ignoreCase = true) ||
                        activity.description.contains(text, ignoreCase = true) ||
                        activity.instructorName.contains(text, ignoreCase = true) ||
                        activity.hostStudioName.contains(text, ignoreCase = true) ||
                        activity.location.name.contains(text, ignoreCase = true) ||
                        activity.location.address.contains(text, ignoreCase = true)
                    )
        }
    }

    // Converts activities into the current Discover card model during the migration.
    fun toDiscoveryItems(activities: List<DanceActivity>): List<DiscoveryItem> {
        return activities.map { activity ->
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
                displayType = activity.activityType
            )
        }
    }

    // Checks whether a user document represents an approved professional creator.
    private fun com.google.firebase.firestore.DocumentSnapshot.isApprovedProfessional(): Boolean {
        val role = getString("role").orEmpty()
        val managedStudioIds = (get("managedStudioIds") as? List<*>).orEmpty()
        return role.equals(Constants.UserRole.STUDIO_MANAGER.firestoreValue, ignoreCase = true) ||
            role.equals(Constants.UserRole.ADMIN.firestoreValue, ignoreCase = true) ||
            getBoolean("verifiedTeacher") == true ||
            getBoolean("verifiedChoreographer") == true ||
            managedStudioIds.isNotEmpty()
    }

    // Converts the model into a Firestore-friendly map with nested objects.
    private fun DanceActivity.toFirestoreMap(): Map<String, Any?> {
        return mapOf(
            "id" to id,
            "creatorUid" to creatorUid,
            "instructorUid" to instructorUid,
            "instructorName" to instructorName,
            "hostStudioId" to hostStudioId,
            "hostStudioName" to hostStudioName,
            "googlePlaceId" to googlePlaceId,
            "title" to title,
            "description" to description,
            "activityType" to activityType,
            "styles" to styles,
            "levels" to levels,
            "startAt" to startAt,
            "endAt" to endAt,
            "timezone" to timezone,
            "recurrence" to recurrence,
            "price" to price,
            "registration" to registration,
            "location" to location,
            "mediaUrls" to mediaUrls,
            "ratingSummary" to ratingSummary,
            "status" to status
        )
    }

    companion object {
        private const val MAX_DISCOVERY_ACTIVITIES = 80L
    }
}
