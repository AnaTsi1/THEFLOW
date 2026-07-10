package com.ana.theflow.data.repository

import com.ana.theflow.data.model.post.Post
import com.ana.theflow.utilities.Constants
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions

class ActivityTrackingRepository {

    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()

    // Stores an activity event and updates recommendations.
    fun trackEvent(
        eventType: String,
        targetType: String,
        targetId: String,
        targetName: String = "",
        danceStyles: List<String> = emptyList(),
        location: String = "",
        metadata: Map<String, String> = emptyMap(),
        interactionStrength: Double = 1.0,
        onFailure: (String) -> Unit = {}
    ) {
        val uid = auth.currentUser?.uid ?: return
        if (eventType.isBlank() || targetType.isBlank()) return

        val normalizedStrength = normalizedInteractionStrength(interactionStrength)
        val baseWeight = weightFor(eventType)
        val finalWeight = baseWeight * normalizedStrength
        val eventMetadata = metadata + ("interactionStrength" to normalizedStrength.toString())
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
            "metadata" to eventMetadata,
            "weight" to finalWeight,
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
                    metadata = eventMetadata,
                    weight = finalWeight,
                    onFailure = onFailure
                )
            }
            .addOnFailureListener { error ->
                onFailure(error.message ?: "Failed to track activity")
            }
    }

    // Tracks that a user profile was viewed.
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

    // Tracks that a post was viewed.
    fun trackViewPost(
        postId: String,
        authorName: String = "",
        authorType: String = "",
        interactionStrength: Double = 1.0
    ) {
        trackEvent(
            eventType = EventTypes.VIEW_POST,
            targetType = TargetTypes.POST,
            targetId = postId,
            targetName = authorName,
            metadata = mapOf("authorType" to authorType),
            interactionStrength = interactionStrength
        )
    }

    // Tracks a post impression.
    fun trackPostViewed(post: Post, interactionStrength: Double = 1.0) {
        trackEvent(
            eventType = EventTypes.VIEW_POST,
            targetType = TargetTypes.POST,
            targetId = post.postId,
            targetName = post.authorName,
            metadata = post.interactionMetadata(),
            interactionStrength = interactionStrength
        )
    }

    // Tracks that a post was opened.
    fun trackPostOpened(post: Post, interactionStrength: Double = 1.0) {
        trackEvent(
            eventType = EventTypes.OPEN_POST,
            targetType = TargetTypes.POST,
            targetId = post.postId,
            targetName = post.authorName,
            metadata = post.interactionMetadata(),
            interactionStrength = interactionStrength
        )
    }

    // Tracks that a post was saved.
    fun trackPostSaved(post: Post, interactionStrength: Double = 1.0) {
        trackEvent(
            eventType = EventTypes.SAVE_ITEM,
            targetType = TargetTypes.POST,
            targetId = post.postId,
            targetName = post.authorName,
            metadata = post.interactionMetadata(),
            interactionStrength = interactionStrength
        )
    }

    // Tracks that a post was liked.
    fun trackPostLiked(post: Post, interactionStrength: Double = 1.0) {
        trackEvent(
            eventType = EventTypes.LIKE_POST,
            targetType = TargetTypes.POST,
            targetId = post.postId,
            targetName = post.authorName,
            metadata = post.interactionMetadata(),
            interactionStrength = interactionStrength
        )
    }

    // Tracks that a post was created.
    fun trackCreatePost(
        postId: String,
        authorType: String = "",
        text: String = "",
        interactionStrength: Double = 1.0
    ) {
        trackEvent(
            eventType = EventTypes.CREATE_POST,
            targetType = TargetTypes.POST,
            targetId = postId,
            metadata = mapOf(
                "authorType" to authorType,
                "text" to text.take(120)
            ).filterValues { it.isNotBlank() },
            interactionStrength = interactionStrength
        )
    }

    // Tracks that a discovery item was opened.
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

    // Tracks a search action.
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

    // Tracks that an item was saved.
    fun trackSaveItem(
        targetType: String,
        targetId: String,
        targetName: String = "",
        danceStyles: List<String> = emptyList(),
        location: String = "",
        interactionStrength: Double = 1.0
    ) {
        trackEvent(
            eventType = EventTypes.SAVE_ITEM,
            targetType = targetType,
            targetId = targetId,
            targetName = targetName,
            danceStyles = danceStyles,
            location = location,
            interactionStrength = interactionStrength
        )
    }

    // Tracks that a user was followed.
    fun trackFollowUser(targetUserId: String, targetName: String = "") {
        trackEvent(
            eventType = EventTypes.FOLLOW_USER,
            targetType = TargetTypes.USER,
            targetId = targetUserId,
            targetName = targetName
        )
    }

    // Updates the user recommendation profile from an event.
    private fun updateRecommendationProfile(
        uid: String,
        eventType: String,
        targetType: String,
        danceStyles: List<String>,
        location: String,
        metadata: Map<String, String>,
        weight: Double,
        onFailure: (String) -> Unit
    ) {
        val updates = mutableMapOf<String, Any>(
            "targetTypeScores.${scoreKey(targetType)}" to FieldValue.increment(weight),
            "updatedAt" to FieldValue.serverTimestamp()
        )

        danceStyles.forEach { style ->
            if (style.isNotBlank()) {
                updates["styleScores.${scoreKey(style)}"] = FieldValue.increment(weight)
            }
        }

        if (location.isNotBlank()) {
            updates["locationScores.${scoreKey(location)}"] = FieldValue.increment(weight)
        }

        metadata["teacher"]?.takeIf { it.isNotBlank() }?.let { teacher ->
            updates["teacherScores.${scoreKey(teacher)}"] = FieldValue.increment(weight)
        }

        metadata["studio"]?.takeIf { it.isNotBlank() }?.let { studio ->
            updates["studioScores.${scoreKey(studio)}"] = FieldValue.increment(weight)
        }

        metadata["authorType"]?.takeIf { isPostInterestEvent(eventType) && it.isNotBlank() }?.let { authorType ->
            updates["targetTypeScores.${scoreKey(authorType)}"] = FieldValue.increment(weight)
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

    // Returns the recommendation weight for an event type.
    private fun weightFor(eventType: String): Int {
        return when (eventType) {
            EventTypes.VIEW_POST -> 1
            EventTypes.OPEN_POST -> 3
            EventTypes.LIKE_POST -> 4
            EventTypes.VIEW_PROFILE -> 2
            EventTypes.SEARCH -> 3
            EventTypes.OPEN_DISCOVERY_ITEM -> 4
            EventTypes.SAVE_ITEM -> 5
            EventTypes.FOLLOW_USER -> 8
            EventTypes.CREATE_POST -> 2
            else -> 1
        }
    }

    // Clamps interaction strength to a safe range.
    private fun normalizedInteractionStrength(interactionStrength: Double): Double {
        if (interactionStrength.isNaN()) return 1.0
        return interactionStrength.coerceIn(0.0, 1.0)
    }

    // Converts text into a safe recommendation score key.
    private fun scoreKey(value: String): String {
        return value.trim()
            .ifBlank { "unknown" }
            .replace(Regex("[^A-Za-z0-9_-]"), "_")
    }

    // Checks whether an event should affect post interests.
    private fun isPostInterestEvent(eventType: String): Boolean {
        return eventType == EventTypes.VIEW_POST ||
            eventType == EventTypes.OPEN_POST ||
            eventType == EventTypes.LIKE_POST ||
            eventType == EventTypes.SAVE_ITEM ||
            eventType == EventTypes.CREATE_POST
    }

    object EventTypes {
        const val VIEW_PROFILE = "view_profile"
        const val VIEW_POST = "view_post"
        const val OPEN_POST = "open_post"
        const val LIKE_POST = "like_post"
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

    // Builds metadata for post interaction tracking.
    private fun Post.interactionMetadata(): Map<String, String> {
        return mapOf(
            "authorId" to authorId,
            "authorType" to authorType,
            "text" to text.take(120)
        ).filterValues { it.isNotBlank() }
    }
}
