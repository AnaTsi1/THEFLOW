// The one place every user action (like, save, follow, search, hide...) gets recorded, both as a
// raw activity event and as an update to the user's learned recommendation profile.
package com.ana.theflow.data.repository

import android.util.Log
import com.ana.theflow.data.model.post.Post
import com.ana.theflow.data.recommendation.RecommendationSurface
import com.ana.theflow.data.recommendation.PopularitySignals
import com.ana.theflow.data.recommendation.RecommendationFeatureExtractor
import com.ana.theflow.data.recommendation.RecommendationFeatures
import com.ana.theflow.data.recommendation.RecommendationFirestorePaths
import com.ana.theflow.data.recommendation.RecommendationProfileUpdatePlanner
import com.ana.theflow.data.recommendation.RecommendationScoreMath
import com.ana.theflow.data.recommendation.RecommendationSignalType
import com.ana.theflow.data.recommendation.RecommendationNormalizer
import com.ana.theflow.utilities.Constants
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions

class ActivityTrackingRepository {

    private companion object {
        const val MAX_USER_ACTIVITY_EVENTS = 50L
        const val TAG = "RecommendationTrackingDebug"
    }

    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()

    // The core tracking function everything else calls into. Writes a raw activity event, then
    // updates the user's learned recommendation profile, then prunes old events so this doesn't
    // grow forever.
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
        val signal = RecommendationSignalType.fromWireName(eventType)
        val baseWeight = signal.baseWeight
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
                    targetId = targetId,
                    danceStyles = danceStyles,
                    location = location,
                    metadata = eventMetadata,
                    signal = signal,
                    interactionStrength = normalizedStrength,
                    onFailure = onFailure
                )
                pruneOldActivityEvents(uid)
            }
            .addOnFailureListener { error ->
                // Most call sites don't pass an onFailure handler, since a failed
                // behavioral-learning write shouldn't ever bother the user - but we still log it
                // so a broken learning signal is at least visible somewhere.
                Log.e(TAG, "activity event write failed uid=$uid eventType=$eventType targetId=$targetId", error)
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

    // Tracks that a post was commented on.
    fun trackPostCommented(post: Post, interactionStrength: Double = 1.0) {
        trackEvent(
            eventType = EventTypes.COMMENT_POST,
            targetType = TargetTypes.POST,
            targetId = post.postId,
            targetName = post.authorName,
            metadata = post.interactionMetadata(),
            interactionStrength = interactionStrength
        )
    }

    // Tracks that a post was shared.
    fun trackPostShared(post: Post, interactionStrength: Double = 1.0) {
        trackEvent(
            eventType = EventTypes.SHARE_POST,
            targetType = TargetTypes.POST,
            targetId = post.postId,
            targetName = post.authorName,
            metadata = post.interactionMetadata(),
            interactionStrength = interactionStrength
        )
    }

    // Tracks that a post was hidden.
    fun trackPostHidden(post: Post) {
        trackEvent(
            eventType = EventTypes.HIDE,
            targetType = TargetTypes.POST,
            targetId = post.postId,
            targetName = post.authorName,
            metadata = post.interactionMetadata()
        )
    }

    // Tracks registering or un-registering for an event.
    fun trackEventRegistration(post: Post, registered: Boolean) {
        trackEvent(
            eventType = if (registered) EventTypes.REGISTER_EVENT else EventTypes.CANCEL_REGISTRATION,
            targetType = TargetTypes.EVENT,
            targetId = post.postId,
            targetName = post.activityType.ifBlank { post.text.take(80) },
            danceStyles = listOf(post.activityType).filter { it.isNotBlank() },
            location = post.activityLocation,
            metadata = post.interactionMetadata()
        )
    }

    // Batch-records that a set of posts was shown to the user (an impression), capped at 20 at a
    // time, and marks them as "seen" on the profile so they don't feel repetitive later.
    fun trackPostImpressions(posts: List<Post>, surface: RecommendationSurface) {
        val uid = auth.currentUser?.uid ?: return
        val visiblePosts = posts.distinctBy { it.postId }.filter { it.postId.isNotBlank() }.take(20)
        if (visiblePosts.isEmpty()) return

        val batch = db.batch()
        val profilePath = RecommendationFirestorePaths.profile(uid)
        visiblePosts.forEach { post ->
            val impressionRef = db.collection(RecommendationFirestorePaths.impressions(uid))
                .document("${surface.name.lowercase()}_${scoreKey(post.postId)}")
            batch.set(
                impressionRef,
                mapOf(
                    "itemId" to post.postId,
                    "userId" to uid,
                    "surface" to surface.name,
                    "shownAt" to FieldValue.serverTimestamp(),
                    "impressionCount" to FieldValue.increment(1),
                    "opened" to false,
                    "interacted" to false
                ),
                SetOptions.merge()
            )
        }
        batch.set(
            db.document(profilePath),
            mapOf(
                "seenItemIds" to FieldValue.arrayUnion(*visiblePosts.map { it.postId }.toTypedArray()),
                "updatedAt" to FieldValue.serverTimestamp()
            ),
            SetOptions.merge()
        )
        batch.commit()
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

    // Tracks that a discovery item (studio/activity) was shared.
    fun trackShareItem(
        targetType: String,
        targetId: String,
        targetName: String = "",
        danceStyles: List<String> = emptyList(),
        location: String = ""
    ) {
        trackEvent(
            eventType = EventTypes.SHARE_ITEM,
            targetType = targetType,
            targetId = targetId,
            targetName = targetName,
            danceStyles = danceStyles,
            location = location
        )
    }

    // Tracks unfollowing a user.
    fun trackUnfollowUser(targetUserId: String, targetName: String = "") {
        trackEvent(
            eventType = EventTypes.UNFOLLOW_USER,
            targetType = TargetTypes.USER,
            targetId = targetUserId,
            targetName = targetName
        )
    }

    // Turns one tracked event into real score changes on the user's recommendation profile -
    // works out which dimensions move (via the signal planner), applies them as increments, and
    // deduplicates certain signal types so the same action can't be counted twice.
    private fun updateRecommendationProfile(
        uid: String,
        eventType: String,
        targetType: String,
        targetId: String,
        danceStyles: List<String>,
        location: String,
        metadata: Map<String, String>,
        signal: RecommendationSignalType,
        interactionStrength: Double,
        onFailure: (String) -> Unit
    ) {
        val features = buildFeatures(
            targetId = targetId,
            targetType = targetType,
            danceStyles = danceStyles,
            location = location,
            metadata = metadata
        )
        val plan = RecommendationProfileUpdatePlanner.plan(signal, features, interactionStrength)
        if (plan.increments.isEmpty() && plan.arrayUnions.isEmpty() && plan.arrayRemoves.isEmpty()) return
        val now = System.currentTimeMillis()
        val updates = mutableMapOf<String, Any>(
            "updatedAt" to FieldValue.serverTimestamp(),
            "lastUpdatedMillis" to now
        )
        plan.increments.forEach { (path, amount) ->
            updates[path] = FieldValue.increment(amount)
        }
        plan.arrayUnions.forEach { (path, values) ->
            if (values.isNotEmpty()) updates[path] = FieldValue.arrayUnion(*values.toTypedArray())
        }
        plan.arrayRemoves.forEach { (path, values) ->
            if (values.isNotEmpty()) updates[path] = FieldValue.arrayRemove(*values.toTypedArray())
        }
        plan.increments.forEach { (path, amount) ->
            val key = path.replace(".", "_")
            updates["scoreMetadata.$key.score"] = FieldValue.increment(amount)
            updates["scoreMetadata.$key.lastUpdatedMillis"] = now
            updates["scoreMetadata.$key.interactionCount"] = FieldValue.increment(1)
            if (amount >= 0.0) {
                updates["scoreMetadata.$key.positiveCount"] = FieldValue.increment(1)
            } else {
                updates["scoreMetadata.$key.negativeCount"] = FieldValue.increment(1)
            }
        }

        val profileRef = db.document(RecommendationFirestorePaths.profile(uid))
        // set(map, SetOptions.merge()) treats a key like "styleScores.hip_hop" as one literal
        // field name with a dot in it, not a nested path the way update() would - so we convert
        // our dotted keys into a real nested map ourselves first, otherwise scores would pile up
        // under weird flat field names instead of inside a real styleScores/locationScores map.
        val nestedUpdates = updates.toNestedFirestoreMap()

        if (RecommendationProfileUpdatePlanner.shouldDedupe(signal) && plan.dedupeKey.isNotBlank()) {
            val dedupeRef = db.document(RecommendationFirestorePaths.signalDedupe(uid, plan.dedupeKey))
            db.runTransaction { transaction ->
                val existing = transaction.get(dedupeRef)
                if (!existing.exists()) {
                    transaction.set(dedupeRef, mapOf("createdAt" to FieldValue.serverTimestamp(), "eventType" to eventType, "targetId" to targetId))
                    transaction.set(profileRef, nestedUpdates, SetOptions.merge())
                }
            }.addOnFailureListener { error ->
                Log.e(TAG, "recommendation profile update (dedup path) failed uid=$uid eventType=$eventType targetId=$targetId", error)
                onFailure(error.message ?: "Failed to update recommendation profile")
            }
            return
        }

        profileRef
            .set(nestedUpdates, SetOptions.merge())
            .addOnFailureListener { error ->
                Log.e(TAG, "recommendation profile update failed uid=$uid eventType=$eventType targetId=$targetId", error)
                onFailure(error.message ?: "Failed to update recommendation profile")
            }
    }

    // Turns a flat map like {"styleScores.hip_hop": 5} into a real nested map like
    // {"styleScores": {"hip_hop": 5}}, so Firestore actually treats it as nested data.
    private fun Map<String, Any>.toNestedFirestoreMap(): Map<String, Any> {
        val root = mutableMapOf<String, Any>()
        for ((path, value) in this) {
            val parts = path.split(".")
            var current = root
            for (i in 0 until parts.size - 1) {
                @Suppress("UNCHECKED_CAST")
                current = current.getOrPut(parts[i]) { mutableMapOf<String, Any>() } as MutableMap<String, Any>
            }
            current[parts.last()] = value
        }
        return root
    }

    // Keeps only the newest activity events for a user so raw behavior data does not grow forever.
    private fun pruneOldActivityEvents(uid: String) {
        db.collection(Constants.Collections.USER_ACTIVITY_EVENTS)
            .whereEqualTo("userId", uid)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .get()
            .addOnSuccessListener { snapshot ->
                val oldEvents = snapshot.documents.drop(MAX_USER_ACTIVITY_EVENTS.toInt())
                if (oldEvents.isEmpty()) return@addOnSuccessListener

                val batch = db.batch()
                oldEvents.forEach { document ->
                    batch.delete(document.reference)
                }
                batch.commit()
                    .addOnFailureListener { error -> Log.w(TAG, "prune delete batch failed uid=$uid", error) }
            }
            .addOnFailureListener { error -> Log.w(TAG, "prune query failed uid=$uid", error) }
    }

    // Clamps interaction strength to a safe range.
    private fun normalizedInteractionStrength(interactionStrength: Double): Double {
        if (interactionStrength.isNaN()) return 1.0
        return interactionStrength.coerceIn(0.0, 1.0)
    }

    // Converts text into a safe recommendation score key.
    private fun scoreKey(value: String): String {
        return RecommendationNormalizer.id(value)
    }

    // All the raw event-type strings we write to Firestore.
    object EventTypes {
        const val VIEW_PROFILE = "view_profile"
        const val VIEW_POST = "view_post"
        const val OPEN_POST = "open_post"
        const val LIKE_POST = "like_post"
        const val COMMENT_POST = "comment_post"
        const val SHARE_POST = "share_post"
        const val SHARE_ITEM = "share_item"
        const val CREATE_POST = "create_post"
        const val OPEN_DISCOVERY_ITEM = "open_discovery_item"
        const val SEARCH = "search"
        const val SAVE_ITEM = "save_item"
        const val FOLLOW_USER = "follow_user"
        const val UNFOLLOW_USER = "unfollow_user"
        const val REGISTER_EVENT = "register_event"
        const val CANCEL_REGISTRATION = "cancel_registration"
        const val HIDE = "hide"
    }

    // All the target-type strings (what kind of thing an event happened to).
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
            "activityType" to activityType,
            "activityLocation" to activityLocation,
            "activityLevel" to activityLevel,
            "collaborationStyle" to collaborationStyle,
            "collaborationLocation" to collaborationLocation,
            "postType" to postType,
            "mediaType" to mediaType,
            "text" to text.take(120)
        ).filterValues { it.isNotBlank() }
    }

    // Builds the normalized features (style, location, creator, etc.) an event's target has, so
    // the signal planner can route the score update to the right dimensions.
    private fun buildFeatures(
        targetId: String,
        targetType: String,
        danceStyles: List<String>,
        location: String,
        metadata: Map<String, String>
    ): RecommendationFeatures {
        val styleIds = (
            danceStyles.map { RecommendationNormalizer.styleId(it) } +
                listOf(metadata["activityType"].orEmpty(), metadata["collaborationStyle"].orEmpty(), metadata["text"].orEmpty())
                    .flatMap { extractKnownStyles(it) }
            ).filter { it.isNotBlank() && it != "unknown" }.toSet()
        val contentType = metadata["postType"].orEmpty().ifBlank { targetType }
        return RecommendationFeatures(
            itemId = targetId,
            itemType = targetType,
            styleIds = styleIds,
            locationId = RecommendationFeatureExtractor.normalizeLocation(location.ifBlank { metadata["activityLocation"].orEmpty().ifBlank { metadata["collaborationLocation"].orEmpty() } }),
            teacherId = RecommendationNormalizer.id(metadata["teacher"].orEmpty()),
            studioId = RecommendationNormalizer.id(metadata["studio"].orEmpty()),
            creatorId = RecommendationNormalizer.id(metadata["authorId"].orEmpty().ifBlank { targetId.takeIf { targetType == TargetTypes.USER }.orEmpty() }),
            creatorTypeId = RecommendationNormalizer.creatorTypeId(metadata["authorType"].orEmpty().ifBlank { targetType }),
            contentTypeId = RecommendationNormalizer.contentTypeId(contentType),
            levelId = RecommendationNormalizer.levelId(metadata["activityLevel"].orEmpty()),
            mediaTypeId = RecommendationNormalizer.contentTypeId(metadata["mediaType"].orEmpty()),
            popularitySignals = PopularitySignals()
        )
    }

    // Scans free text for mentions of a style we know about, used when there's no explicit style field.
    private fun extractKnownStyles(text: String): List<String> {
        val lower = text.lowercase()
        return listOf("Hip Hop", "Heels", "Contemporary", "Ballet", "Jazz", "Salsa", "Bachata")
            .filter { lower.contains(it.lowercase()) }
            .map { RecommendationNormalizer.styleId(it) }
    }
}
