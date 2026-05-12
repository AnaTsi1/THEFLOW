package com.ana.theflow.data.repository

import com.ana.theflow.utilities.Constants
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions

class ActivityTrackingRepository {

    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()

    fun trackEvent(
        eventType: String,
        targetType: String,
        targetId: String,
        targetName: String = "",
        danceStyles: List<String> = emptyList(),
        location: String = "",
        metadata: Map<String, String> = emptyMap(),
        onFailure: (String) -> Unit = {}
    ) {
        val uid = auth.currentUser?.uid ?: return
        if (eventType.isBlank() || targetType.isBlank()) return

        val weight = weightFor(eventType)
        val docRef = db.collection(Constants.Collections.USER_ACTIVITY_EVENTS).document()
        val event = mapOf(
            "eventId" to docRef.id,
            "userId" to uid,
            "eventType" to eventType,
            "targetType" to targetType,
            "targetId" to targetId,
            "targetName" to targetName,
            "danceStyles" to danceStyles,
            "location" to location,
            "metadata" to metadata,
            "weight" to weight,
            "createdAt" to FieldValue.serverTimestamp()
        )

        docRef.set(event)
            .addOnSuccessListener {
                updateRecommendationProfile(
                    uid = uid,
                    eventType = eventType,
                    targetType = targetType,
                    danceStyles = danceStyles,
                    location = location,
                    metadata = metadata,
                    weight = weight,
                    onFailure = onFailure
                )
            }
            .addOnFailureListener { error ->
                onFailure(error.message ?: "Failed to track activity")
            }
    }

    fun trackViewProfile(targetUserId: String, targetName: String = "", danceStyles: List<String> = emptyList(), location: String = "") {
        trackEvent(
            eventType = EventTypes.VIEW_PROFILE,
            targetType = TargetTypes.USER,
            targetId = targetUserId,
            targetName = targetName,
            danceStyles = danceStyles,
            location = location
        )
    }

    fun trackViewPost(postId: String, authorName: String = "", authorType: String = "") {
        trackEvent(
            eventType = EventTypes.VIEW_POST,
            targetType = TargetTypes.POST,
            targetId = postId,
            targetName = authorName,
            metadata = mapOf("authorType" to authorType)
        )
    }

    fun trackCreatePost(postId: String) {
        trackEvent(
            eventType = EventTypes.CREATE_POST,
            targetType = TargetTypes.POST,
            targetId = postId
        )
    }

    fun trackOpenDiscoveryItem(
        itemId: String,
        itemName: String,
        targetType: String,
        danceStyles: List<String>,
        location: String,
        metadata: Map<String, String> = emptyMap()
    ) {
        trackEvent(
            eventType = EventTypes.OPEN_DISCOVERY_ITEM,
            targetType = targetType,
            targetId = itemId,
            targetName = itemName,
            danceStyles = danceStyles,
            location = location,
            metadata = metadata
        )
    }

    fun trackSearch(query: String, danceStyles: List<String> = emptyList(), location: String = "") {
        trackEvent(
            eventType = EventTypes.SEARCH,
            targetType = TargetTypes.SEARCH_QUERY,
            targetId = query.ifBlank { "empty_query" },
            targetName = query,
            danceStyles = danceStyles,
            location = location
        )
    }

    fun trackSaveItem(targetType: String, targetId: String, targetName: String = "", danceStyles: List<String> = emptyList(), location: String = "") {
        trackEvent(
            eventType = EventTypes.SAVE_ITEM,
            targetType = targetType,
            targetId = targetId,
            targetName = targetName,
            danceStyles = danceStyles,
            location = location
        )
    }

    fun trackFollowUser(targetUserId: String, targetName: String = "") {
        trackEvent(
            eventType = EventTypes.FOLLOW_USER,
            targetType = TargetTypes.USER,
            targetId = targetUserId,
            targetName = targetName
        )
    }

    private fun updateRecommendationProfile(
        uid: String,
        eventType: String,
        targetType: String,
        danceStyles: List<String>,
        location: String,
        metadata: Map<String, String>,
        weight: Int,
        onFailure: (String) -> Unit
    ) {
        val updates = mutableMapOf<String, Any>(
            "targetTypeScores.${scoreKey(targetType)}" to FieldValue.increment(weight.toLong()),
            "updatedAt" to FieldValue.serverTimestamp()
        )

        danceStyles.forEach { style ->
            if (style.isNotBlank()) {
                updates["styleScores.${scoreKey(style)}"] = FieldValue.increment(weight.toLong())
            }
        }

        if (location.isNotBlank()) {
            updates["locationScores.${scoreKey(location)}"] = FieldValue.increment(weight.toLong())
        }

        metadata["teacher"]?.takeIf { it.isNotBlank() }?.let { teacher ->
            updates["teacherScores.${scoreKey(teacher)}"] = FieldValue.increment(weight.toLong())
        }

        metadata["studio"]?.takeIf { it.isNotBlank() }?.let { studio ->
            updates["studioScores.${scoreKey(studio)}"] = FieldValue.increment(weight.toLong())
        }

        metadata["authorType"]?.takeIf { eventType == EventTypes.VIEW_POST && it.isNotBlank() }?.let { authorType ->
            updates["targetTypeScores.${scoreKey(authorType)}"] = FieldValue.increment(weight.toLong())
        }

        db.collection(Constants.Collections.USERS)
            .document(uid)
            .collection("recommendationProfile")
            .document("main")
            .set(updates, SetOptions.merge())
            .addOnFailureListener { error ->
                onFailure(error.message ?: "Failed to update recommendation profile")
            }
    }

    private fun weightFor(eventType: String): Int {
        return when (eventType) {
            EventTypes.VIEW_POST -> 1
            EventTypes.VIEW_PROFILE -> 2
            EventTypes.SEARCH -> 3
            EventTypes.OPEN_DISCOVERY_ITEM -> 4
            EventTypes.SAVE_ITEM -> 5
            EventTypes.FOLLOW_USER -> 8
            EventTypes.CREATE_POST -> 2
            else -> 1
        }
    }

    private fun scoreKey(value: String): String {
        return value.trim()
            .ifBlank { "unknown" }
            .replace(Regex("[^A-Za-z0-9_-]"), "_")
    }

    object EventTypes {
        const val VIEW_PROFILE = "view_profile"
        const val VIEW_POST = "view_post"
        const val CREATE_POST = "create_post"
        const val OPEN_DISCOVERY_ITEM = "open_discovery_item"
        const val SEARCH = "search"
        const val SAVE_ITEM = "save_item"
        const val FOLLOW_USER = "follow_user"
    }

    object TargetTypes {
        const val USER = "user"
        const val POST = "post"
        const val STUDIO = "studio"
        const val TEACHER = "teacher"
        const val CHOREOGRAPHER = "choreographer"
        const val CLASS = "class"
        const val WORKSHOP = "workshop"
        const val AUDITION = "audition"
        const val EVENT = "event"
        const val DISCOVERY_ITEM = "discovery_item"
        const val SEARCH_QUERY = "search_query"
    }
}
