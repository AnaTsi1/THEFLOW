// Repository for user profiles, onboarding preferences, and social follow state.
package com.ana.theflow.data.repository

import com.ana.theflow.data.model.notification.InAppNotification
import com.ana.theflow.data.model.user.User
import com.ana.theflow.data.recommendation.RecommendationNormalizer
import com.ana.theflow.data.recommendation.RecommendationProfile
import com.ana.theflow.data.recommendation.RecommendationScoreMetadata
import com.ana.theflow.data.recommendation.RecommendationScoreMath
import com.ana.theflow.utilities.CityOptions
import com.ana.theflow.utilities.Constants
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions

// Keeps screens isolated from Firestore document layout for user-owned data.
class UserRepository {

    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()
    private val notificationRepository = NotificationRepository()

    // Creates a Firestore profile for a new user. Every new account is simply a dancer -
    // professional and studio-manager permissions are always granted later by an admin.
    fun createUserProfile(
        firstName: String,
        lastName: String,
        email: String,
        onSuccess: () -> Unit,
        onFailure: (String) -> Unit
    ) {
        val uid = auth.currentUser?.uid
        if (uid == null) {
            onFailure("User is not logged in")
            return
        }

        val user = User(
            uid = uid,
            firstName = firstName,
            lastName = lastName,
            email = email,
            role = Constants.UserRole.DANCER.firestoreValue,
            onboardingCompleted = false
        )

        db.collection(Constants.Collections.USERS)
            .document(uid)
            .set(user)
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { error ->
                onFailure(error.message ?: "Failed to save user")
            }
    }

    // Loads a user profile by uid.
    fun getUserByUid(
        uid: String,
        onSuccess: (User) -> Unit,
        onFailure: (String) -> Unit
    ) {
        db.collection(Constants.Collections.USERS)
            .document(uid)
            .get()
            .addOnSuccessListener { document ->
                val user = document.toObject(User::class.java)
                if (user != null) {
                    onSuccess(user.copy(uid = document.id))
                } else {
                    onFailure("User not found")
                }
            }
            .addOnFailureListener { error ->
                onFailure(error.message ?: "Failed to load user")
            }
    }

    // Saves onboarding preferences.
    fun saveOnboardingPreferences(
        styles: List<String>,
        level: String,
        location: String,
        preferredStudios: List<String> = emptyList(),
        preferredTeachers: List<String> = emptyList(),
        preferredDancers: List<String> = emptyList(),
        onSuccess: () -> Unit,
        onFailure: (String) -> Unit
    ) {
        val uid = auth.currentUser?.uid
        if (uid == null) {
            onFailure("User is not logged in")
            return
        }

        val normalizedLocation = CityOptions.normalizeCity(location)
        val updates = mapOf(
            "onboardingCompleted" to true
        )

        val recommendationUpdates = mapOf(
            "preferredStyles" to styles,
            "preferredLevel" to level,
            "preferredLocation" to normalizedLocation,
            "preferredStudios" to preferredStudios,
            "preferredTeachers" to preferredTeachers,
            "preferredDancers" to preferredDancers,
            "updatedAt" to FieldValue.serverTimestamp()
        )

        db.runBatch { batch ->
            batch.update(
                db.collection(Constants.Collections.USERS).document(uid),
                updates
            )
            batch.set(
                db.collection(Constants.Collections.USERS)
                    .document(uid)
                    .collection("recommendationProfile")
                    .document("main"),
                recommendationUpdates,
                SetOptions.merge()
            )
        }
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { error ->
                onFailure(error.message ?: "Failed to save onboarding")
            }
    }

    // Grabs a bounded batch of users and filters/sorts them client-side by name, headline, dance
    // styles, or location - dancersOnly narrows results to plain dancers (no verified badge),
    // which is what the studio teacher-picker and similar "add a real dancer" flows need.
    fun searchUsers(
        query: String,
        dancersOnly: Boolean,
        onSuccess: (List<User>) -> Unit,
        onFailure: (String) -> Unit
    ) {
        val normalizedQuery = query.trim()
        db.collection(Constants.Collections.USERS)
            .limit(80)
            .get()
            .addOnSuccessListener { snapshot ->
                val users = snapshot.documents.mapNotNull { document ->
                    document.toObject(User::class.java)?.copy(uid = document.id)
                }.filter { user ->
                    val isDancer = user.role.equals(Constants.UserRole.DANCER.firestoreValue, ignoreCase = true) &&
                        !user.verifiedTeacher &&
                        !user.verifiedChoreographer
                    (!dancersOnly || isDancer) &&
                        (
                            normalizedQuery.isBlank() ||
                                "${user.firstName} ${user.lastName}".contains(normalizedQuery, ignoreCase = true) ||
                                user.headline.contains(normalizedQuery, ignoreCase = true) ||
                                user.danceStyles.any { it.contains(normalizedQuery, ignoreCase = true) } ||
                                user.location.contains(normalizedQuery, ignoreCase = true)
                            )
                }.sortedWith(
                    compareByDescending<User> { it.verifiedTeacher || it.verifiedChoreographer }
                        .thenBy { "${it.firstName} ${it.lastName}" }
                )
                onSuccess(users)
            }
            .addOnFailureListener { error ->
                onFailure(error.message ?: "Failed to search users")
            }
    }

    // Loads private feed preferences for the current user.
    fun loadPreferenceSettings(
        onSuccess: (PreferenceSettings) -> Unit,
        onFailure: (String) -> Unit
    ) {
        val uid = auth.currentUser?.uid
        if (uid == null) {
            onFailure("User is not logged in")
            return
        }

        val profileRef = db.collection(Constants.Collections.USERS)
            .document(uid)
            .collection("recommendationProfile")
            .document("main")

        profileRef.get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    onSuccess(
                        PreferenceSettings(
                            styles = stringList(document.get("preferredStyles")),
                            level = document.getString("preferredLevel").orEmpty(),
                            location = document.getString("preferredLocation").orEmpty(),
                            preferredStudios = stringList(document.get("preferredStudios")),
                            preferredTeachers = stringList(document.get("preferredTeachers")),
                            preferredDancers = stringList(document.get("preferredDancers"))
                        )
                    )
                    return@addOnSuccessListener
                }

                getUserByUid(
                    uid = uid,
                    onSuccess = { user ->
                        onSuccess(
                            PreferenceSettings(
                                styles = user.danceStyles,
                                level = user.danceLevel,
                                location = user.location
                            )
                        )
                    },
                    onFailure = onFailure
                )
            }
            .addOnFailureListener { error ->
                onFailure(error.message ?: "Failed to load preferences")
            }
    }

    // The user doc and the recommendationProfile subdoc both only need `uid` - neither depends on
    // the other's data - so they're read in parallel instead of one after another.
    fun loadRecommendationProfile(
        onSuccess: (RecommendationProfile) -> Unit,
        onFailure: (String) -> Unit
    ) {
        val uid = auth.currentUser?.uid
        if (uid == null) {
            onFailure("User is not logged in")
            return
        }
        loadRecommendationProfileFor(
            uid = uid,
            onSuccess = { _, profile -> onSuccess(profile) },
            onFailure = onFailure
        )
    }

    // Same as loadRecommendationProfile, but for an arbitrary uid rather than only the signed-in
    // user - used by the admin recommendation-insights screen to inspect any user's real learned
    // profile without needing to sign in as them. Also hands back the User doc, since the admin
    // preview needs both (danceStyles/danceLevel/location for the fallback chain, plus display).
    fun loadRecommendationProfileFor(
        uid: String,
        onSuccess: (User, RecommendationProfile) -> Unit,
        onFailure: (String) -> Unit
    ) {
        val userRef = db.collection(Constants.Collections.USERS).document(uid)
        var user: User? = null
        var profileDocument: DocumentSnapshot? = null
        var failed = false
        var pending = 2

        fun finishIfReady() {
            if (failed) return
            pending -= 1
            if (pending > 0) return
            val loadedUser = user
            if (loadedUser == null) {
                onFailure("User not found")
                return
            }
            onSuccess(loadedUser, profileDocument!!.toRecommendationProfile(uid, loadedUser))
        }

        userRef.get()
            .addOnSuccessListener { userDocument ->
                user = userDocument.toObject(User::class.java)?.copy(uid = userDocument.id)
                finishIfReady()
            }
            .addOnFailureListener { error ->
                if (!failed) {
                    failed = true
                    onFailure(error.message ?: "Failed to load user profile")
                }
            }

        userRef.collection("recommendationProfile")
            .document("main")
            .get()
            .addOnSuccessListener { document ->
                profileDocument = document
                finishIfReady()
            }
            .addOnFailureListener { error ->
                if (!failed) {
                    failed = true
                    onFailure(error.message ?: "Failed to load recommendation profile")
                }
            }
    }

    // Updates recommendation preferences for the current user.
    fun updatePreferenceSettings(
        styles: List<String>,
        level: String,
        location: String,
        preferredStudios: List<String>,
        preferredTeachers: List<String>,
        preferredDancers: List<String>,
        onSuccess: () -> Unit,
        onFailure: (String) -> Unit
    ) {
        val uid = auth.currentUser?.uid
        if (uid == null) {
            onFailure("User is not logged in")
            return
        }

        val normalizedLocation = CityOptions.normalizeCity(location)
        val recommendationUpdates = mapOf(
            "preferredStyles" to styles,
            "preferredLevel" to level,
            "preferredLocation" to normalizedLocation,
            "preferredStudios" to preferredStudios,
            "preferredTeachers" to preferredTeachers,
            "preferredDancers" to preferredDancers,
            "updatedAt" to FieldValue.serverTimestamp()
        )

        db.collection(Constants.Collections.USERS)
            .document(uid)
            .collection("recommendationProfile")
            .document("main")
            .set(recommendationUpdates, SetOptions.merge())
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { error ->
                onFailure(error.message ?: "Failed to update preferences")
            }
    }

    // Generic partial update for arbitrary user fields - just passes the given map straight to
    // Firestore. Used by screens that only need to touch one or two fields (like a profile photo
    // change) rather than the whole profile-edit form below.
    fun updateUserFields(
        uid: String,
        updates: Map<String, Any>,
        onSuccess: () -> Unit,
        onFailure: (String) -> Unit
    ) {
        if (uid.isBlank()) {
            onFailure("Missing user id")
            return
        }
        if (updates.isEmpty()) {
            onSuccess()
            return
        }

        db.collection(Constants.Collections.USERS)
            .document(uid)
            .update(updates)
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { error ->
                onFailure(error.message ?: "Failed to update profile")
            }
    }

    // Saves the full set of fields from the profile-edit screen in one write.
    fun updateUserProfile(
        uid: String,
        firstName: String,
        lastName: String,
        birthDate: String,
        age: Int,
        headline: String,
        bio: String,
        professionalBackground: String,
        skills: List<String>,
        yearsOfExperience: String,
        studiosTrainedAt: List<String>,
        teachersLearnedFrom: List<String>,
        performancesCompetitions: List<String>,
        availability: String,
        instagramUrl: String,
        tiktokUrl: String,
        youtubeUrl: String,
        onSuccess: () -> Unit,
        onFailure: (String) -> Unit
    ) {
        if (uid.isBlank()) {
            onFailure("Missing user id")
            return
        }

        val updates = mapOf(
            "firstName" to firstName,
            "lastName" to lastName,
            "birthDate" to birthDate,
            "age" to age,
            "headline" to headline,
            "bio" to bio,
            "professionalBackground" to professionalBackground,
            "skills" to skills,
            "yearsOfExperience" to yearsOfExperience,
            "studiosTrainedAt" to studiosTrainedAt,
            "teachersLearnedFrom" to teachersLearnedFrom,
            "performancesCompetitions" to performancesCompetitions,
            "availability" to availability,
            "instagramUrl" to instagramUrl,
            "tiktokUrl" to tiktokUrl,
            "youtubeUrl" to youtubeUrl
        )

        db.collection(Constants.Collections.USERS)
            .document(uid)
            .update(updates)
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { error ->
                onFailure(error.message ?: "Failed to update profile")
            }
    }

    // Checks if the signed-in user currently follows a target user.
    fun isFollowingUser(
        targetUser: User,
        onSuccess: (Boolean) -> Unit,
        onFailure: (String) -> Unit = {}
    ) {
        val uid = auth.currentUser?.uid
        if (uid == null || targetUser.uid.isBlank() || uid == targetUser.uid) {
            onSuccess(false)
            return
        }

        db.collection(Constants.Collections.USERS)
            .document(uid)
            .collection(followingCollectionFor(targetUser))
            .document(targetUser.uid)
            .get()
            .addOnSuccessListener { document -> onSuccess(document.exists()) }
            .addOnFailureListener { error ->
                onFailure(error.message ?: "Failed to load follow state")
            }
    }

    // Toggles follow state for a target user and keeps both sides of the relationship in sync.
    fun toggleFollowUser(
        targetUser: User,
        onSuccess: (Boolean) -> Unit,
        onFailure: (String) -> Unit
    ) {
        val uid = auth.currentUser?.uid
        if (uid == null) {
            onFailure("User is not logged in")
            return
        }
        if (targetUser.uid.isBlank() || targetUser.uid == uid) {
            onFailure("Profile cannot be followed")
            return
        }

        getUserByUid(
            uid = uid,
            onSuccess = { viewer ->
                toggleFollowDocuments(
                    viewer = viewer,
                    targetUser = targetUser,
                    onSuccess = onSuccess,
                    onFailure = onFailure
                )
            },
            onFailure = onFailure
        )
    }

    // Loads users following a profile.
    fun loadFollowers(
        uid: String,
        onSuccess: (List<User>) -> Unit,
        onFailure: (String) -> Unit
    ) {
        loadUsersFromRelationshipCollection(
            ownerUid = uid,
            collectionName = FOLLOWERS_COLLECTION,
            idFields = listOf("followerId", "userId"),
            onSuccess = onSuccess,
            onFailure = onFailure
        )
    }

    // Loads users followed by a profile across the existing typed following collections.
    fun loadFollowing(
        uid: String,
        onSuccess: (List<User>) -> Unit,
        onFailure: (String) -> Unit
    ) {
        val collectionNames = listOf(
            FOLLOWING_DANCERS_COLLECTION,
            FOLLOWING_TEACHERS_COLLECTION,
            FOLLOWING_STUDIOS_COLLECTION
        )
        var pendingLoads = collectionNames.size
        val userIds = linkedSetOf<String>()
        var completed = false

        collectionNames.forEach { collectionName ->
            db.collection(Constants.Collections.USERS)
                .document(uid)
                .collection(collectionName)
                .get()
                .addOnSuccessListener { snapshot ->
                    if (completed) return@addOnSuccessListener
                    snapshot.documents.forEach { document ->
                        val targetId = document.getString("targetId")
                            ?: document.getString("userId")
                            ?: document.id
                        if (targetId.isNotBlank()) userIds.add(targetId)
                    }
                    pendingLoads -= 1
                    if (pendingLoads == 0) {
                        completed = true
                        loadUsersByIds(userIds.toList(), onSuccess, onFailure)
                    }
                }
                .addOnFailureListener { error ->
                    if (completed) return@addOnFailureListener
                    completed = true
                    onFailure(error.message ?: "Failed to load following")
                }
        }
    }

    // Loads follower and following counts for profile display.
    fun loadFollowCounts(
        uid: String,
        onSuccess: (followers: Int, following: Int) -> Unit,
        onFailure: (String) -> Unit = {}
    ) {
        if (uid.isBlank()) {
            onSuccess(0, 0)
            return
        }

        var followersCount = 0
        var followingCount = 0
        var pendingLoads = 4
        var completed = false

        fun finishOne() {
            if (completed) return
            pendingLoads -= 1
            if (pendingLoads == 0) {
                completed = true
                onSuccess(followersCount, followingCount)
            }
        }

        fun fail(message: String) {
            if (completed) return
            completed = true
            onFailure(message)
        }

        db.collection(Constants.Collections.USERS).document(uid).collection(FOLLOWERS_COLLECTION)
            .get()
            .addOnSuccessListener { snapshot ->
                followersCount = snapshot.size()
                finishOne()
            }
            .addOnFailureListener { error -> fail(error.message ?: "Failed to load followers") }

        listOf(FOLLOWING_DANCERS_COLLECTION, FOLLOWING_TEACHERS_COLLECTION, FOLLOWING_STUDIOS_COLLECTION)
            .forEach { collectionName ->
                db.collection(Constants.Collections.USERS).document(uid).collection(collectionName)
                    .get()
                    .addOnSuccessListener { snapshot ->
                        followingCount += snapshot.size()
                        finishOne()
                    }
                    .addOnFailureListener { error -> fail(error.message ?: "Failed to load following") }
            }
    }

    // Checks whether the signed-in user blocked a target profile.
    fun isUserBlocked(
        targetUid: String,
        onSuccess: (Boolean) -> Unit,
        onFailure: (String) -> Unit = {}
    ) {
        val uid = auth.currentUser?.uid
        if (uid == null || targetUid.isBlank() || targetUid == uid) {
            onSuccess(false)
            return
        }

        db.collection(Constants.Collections.USERS)
            .document(uid)
            .collection(BLOCKED_USERS_COLLECTION)
            .document(targetUid)
            .get()
            .addOnSuccessListener { document -> onSuccess(document.exists()) }
            .addOnFailureListener { error ->
                onFailure(error.message ?: "Failed to load block state")
            }
    }

    // Toggles a block relationship and removes follow relationships in both directions when blocking.
    fun toggleBlockUser(
        targetUser: User,
        onSuccess: (Boolean) -> Unit,
        onFailure: (String) -> Unit
    ) {
        val uid = auth.currentUser?.uid
        if (uid == null) {
            onFailure("User is not logged in")
            return
        }
        if (targetUser.uid.isBlank() || targetUser.uid == uid) {
            onFailure("Profile cannot be blocked")
            return
        }

        val blockedRef = db.collection(Constants.Collections.USERS)
            .document(uid)
            .collection(BLOCKED_USERS_COLLECTION)
            .document(targetUser.uid)

        blockedRef.get()
            .addOnSuccessListener { snapshot ->
                val isBlocked = snapshot.exists()
                val batch = db.batch()
                if (isBlocked) {
                    batch.delete(blockedRef)
                } else {
                    batch.set(
                        blockedRef,
                        mapOf(
                            "userId" to targetUser.uid,
                            "targetName" to targetUser.fullName(),
                            "profileImageUrl" to targetUser.profileImageUrl,
                            "createdAt" to FieldValue.serverTimestamp()
                        )
                    )
                    deleteFollowEdges(batch, uid, targetUser)
                }
                batch.commit()
                    .addOnSuccessListener { onSuccess(!isBlocked) }
                    .addOnFailureListener { error ->
                        onFailure(error.message ?: "Failed to update block")
                    }
            }
            .addOnFailureListener { error ->
                onFailure(error.message ?: "Failed to load block state")
            }
    }

    // Loads ids blocked by the signed-in user for feed and comment filtering.
    fun loadBlockedUserIds(
        onSuccess: (Set<String>) -> Unit,
        onFailure: (String) -> Unit = {}
    ) {
        val uid = auth.currentUser?.uid
        if (uid == null) {
            onSuccess(emptySet())
            return
        }

        db.collection(Constants.Collections.USERS)
            .document(uid)
            .collection(BLOCKED_USERS_COLLECTION)
            .get()
            .addOnSuccessListener { snapshot ->
                onSuccess(
                    snapshot.documents.map { document ->
                        document.getString("userId").orEmpty().ifBlank { document.id }
                    }.filter { it.isNotBlank() }.toSet()
                )
            }
            .addOnFailureListener { error ->
                onFailure(error.message ?: "Failed to load blocked users")
            }
    }

    // Creates an auditable account deletion request for backend/admin processing.
    fun requestAccountDeletion(
        onSuccess: () -> Unit,
        onFailure: (String) -> Unit
    ) {
        val uid = auth.currentUser?.uid
        if (uid == null) {
            onFailure("User is not logged in")
            return
        }

        db.collection(Constants.Collections.ACCOUNT_DELETION_REQUESTS)
            .document(uid)
            .set(
                mapOf(
                    "uid" to uid,
                    "email" to (auth.currentUser?.email ?: ""),
                    "status" to "requested",
                    "requestedAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                ),
                SetOptions.merge()
            )
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { error ->
                onFailure(error.message ?: "Failed to request account deletion")
            }
    }

    // Writes or removes follow documents after current follow state has been read.
    private fun toggleFollowDocuments(
        viewer: User,
        targetUser: User,
        onSuccess: (Boolean) -> Unit,
        onFailure: (String) -> Unit
    ) {
        val followingRef = db.collection(Constants.Collections.USERS)
            .document(viewer.uid)
            .collection(followingCollectionFor(targetUser))
            .document(targetUser.uid)
        val followerRef = db.collection(Constants.Collections.USERS)
            .document(targetUser.uid)
            .collection(FOLLOWERS_COLLECTION)
            .document(viewer.uid)

        followingRef.get()
            .addOnSuccessListener { snapshot ->
                val isFollowing = snapshot.exists()
                val batch = db.batch()
                if (isFollowing) {
                    batch.delete(followingRef)
                    batch.delete(followerRef)
                } else {
                    batch.set(followingRef, targetUser.followingSummary())
                    batch.set(followerRef, viewer.followerSummary())
                }
                batch.commit()
                    .addOnSuccessListener {
                        if (!isFollowing) createFollowNotification(viewer, targetUser)
                        onSuccess(!isFollowing)
                    }
                    .addOnFailureListener { error ->
                        onFailure(error.message ?: "Failed to update follow")
                    }
            }
            .addOnFailureListener { error ->
                onFailure(error.message ?: "Failed to load follow state")
            }
    }

    // Converts Firestore list values into a list of strings.
    private fun stringList(value: Any?): List<String> {
        return (value as? List<*>)
            ?.mapNotNull { it as? String }
            ?.filter { it.isNotBlank() }
            .orEmpty()
    }

    // Turns the raw recommendationProfile document into the RecommendationProfile object the
    // ranking strategies actually work with, falling back to the user's own onboarding
    // styles/level/location wherever the learned profile doesn't have a value yet.
    private fun DocumentSnapshot.toRecommendationProfile(uid: String, user: User?): RecommendationProfile {
        val preferredStyles = stringList(get("preferredStyles"))
        val preferredLevel = getString("preferredLevel").orEmpty()
        val preferredLocation = CityOptions.normalizeOptionalCity(getString("preferredLocation").orEmpty()).orEmpty()
        val profileLocation = CityOptions.normalizeOptionalCity(user?.location.orEmpty()).orEmpty().ifBlank { user?.location.orEmpty() }
        val metadata = scoreMetadataMap()
        return RecommendationProfile(
            userId = uid,
            danceStyles = preferredStyles.ifEmpty { user?.danceStyles.orEmpty() },
            danceLevel = preferredLevel.ifBlank { user?.danceLevel.orEmpty() },
            profileLocation = profileLocation,
            preferredRecommendationArea = preferredLocation.ifBlank { profileLocation },
            styleScores = effectiveScoreMap("styleScores", ::normalizeStyleKey, metadata),
            locationScores = effectiveScoreMap("locationScores", ::normalizeLocationKey, metadata),
            studioScores = effectiveScoreMap("studioScores", RecommendationNormalizer::id, metadata),
            teacherScores = effectiveScoreMap("teacherScores", RecommendationNormalizer::id, metadata),
            creatorScores = effectiveScoreMap("creatorScores", RecommendationNormalizer::id, metadata),
            creatorTypeScores = effectiveScoreMap("creatorTypeScores", RecommendationNormalizer::creatorTypeId, metadata),
            contentTypeScores = effectiveScoreMap("contentTypeScores", RecommendationNormalizer::contentTypeId, metadata),
            targetTypeScores = effectiveScoreMap("targetTypeScores", RecommendationNormalizer::contentTypeId, metadata),
            levelScores = effectiveScoreMap("levelScores", RecommendationNormalizer::levelId, metadata),
            mediaTypeScores = effectiveScoreMap("mediaTypeScores", RecommendationNormalizer::contentTypeId, metadata),
            scoreMetadata = metadata,
            savedItemIds = stringList(get("savedItemIds")).toSet(),
            interactedItemIds = stringList(get("interactedItemIds")).toSet(),
            hiddenItemIds = stringList(get("hiddenItemIds")).toSet(),
            registeredEventIds = stringList(get("registeredEventIds")).toSet(),
            seenItemIds = stringList(get("seenItemIds")).toSet()
        )
    }

    // scoreMetadata.$key.score and the raw field it shadows (e.g. styleScores.hip_hop) are two
    // parallel accumulators the write path increments by the same amount on every event - the
    // only real difference is that the metadata copy gets time-decay applied at read time. So
    // metadata's decayed value is meant to REPLACE the raw aggregate for a given key, not add to
    // it (they represent the same underlying total, one decayed, one not).
    //
    // scoreMetadata keys are stored verbatim, never normalized at read time, so some accounts have
    // a case-variant metadata key sitting alongside the correctly normalized one (e.g.
    // "styleScores_Hip_Hop" next to "styleScores_hip_hop") - two entries for what's really the same
    // signal, just split by casing. So this normalizes the key first and SUMS same-normalized-key
    // entries together rather than picking one, since both are genuine partial totals of the same
    // underlying score and neither should be thrown away.
    private fun DocumentSnapshot.effectiveScoreMap(
        field: String,
        normalizeKey: (String) -> String,
        metadata: Map<String, RecommendationScoreMetadata>
    ): Map<String, Double> {
        val scores = normalizedScoreMap(field, normalizeKey).toMutableMap()
        val prefix = "${field}_"
        val decayedByNormalizedKey = mutableMapOf<String, Double>()
        metadata.forEach { (metadataKey, metadataValue) ->
            if (!metadataKey.startsWith(prefix)) return@forEach
            val key = normalizeKey(metadataKey.removePrefix(prefix))
            if (key.isBlank() || key == "unknown") return@forEach
            val decayed = RecommendationScoreMath.decayed(metadataValue.score, metadataValue.lastUpdatedMillis)
            decayedByNormalizedKey[key] = (decayedByNormalizedKey[key] ?: 0.0) + decayed
        }
        decayedByNormalizedKey.forEach { (key, decayed) -> scores[key] = decayed }
        return scores
    }

    private fun DocumentSnapshot.normalizedScoreMap(
        field: String,
        normalizeKey: (String) -> String
    ): Map<String, Double> {
        val value = get(field) as? Map<*, *> ?: return emptyMap()
        // Some accounts have case-variant duplicates for the same underlying key sitting in the
        // raw Firestore map (e.g. both "Hip_Hop" and "hip_hop" as separate fields). A plain
        // toMap() would keep only the last value it sees for a given normalized key and silently
        // throw away the other one, corrupting the learned score the ranking engine reads. Summing
        // every raw key that normalizes to the same value is what keeps both real contributions.
        val merged = LinkedHashMap<String, Double>()
        value.forEach { (key, score) ->
            val rawKey = key as? String ?: return@forEach
            val number = score as? Number ?: (score as? Map<*, *>)?.get("score") as? Number ?: return@forEach
            val normalized = normalizeKey(rawKey).takeIf { it.isNotBlank() && it != "unknown" } ?: return@forEach
            merged[normalized] = (merged[normalized] ?: 0.0) + number.toDouble()
        }
        return merged
    }

    // Reads the raw scoreMetadata map off the document into typed RecommendationScoreMetadata
    // objects, one per signal key.
    private fun DocumentSnapshot.scoreMetadataMap(): Map<String, RecommendationScoreMetadata> {
        val value = get("scoreMetadata") as? Map<*, *> ?: return emptyMap()
        return value.mapNotNull { (key, rawMetadata) ->
            val rawKey = key as? String ?: return@mapNotNull null
            val metadata = rawMetadata as? Map<*, *> ?: return@mapNotNull null
            rawKey to RecommendationScoreMetadata(
                score = (metadata["score"] as? Number)?.toDouble() ?: 0.0,
                lastUpdatedMillis = (metadata["lastUpdatedMillis"] as? Number)?.toLong() ?: 0L,
                interactionCount = (metadata["interactionCount"] as? Number)?.toInt() ?: 0,
                confidence = (metadata["confidence"] as? Number)?.toDouble() ?: 0.0,
                positiveCount = (metadata["positiveCount"] as? Number)?.toInt() ?: 0,
                negativeCount = (metadata["negativeCount"] as? Number)?.toInt() ?: 0
            )
        }.toMap()
    }

    private fun normalizeStyleKey(value: String): String = RecommendationNormalizer.styleId(value)

    private fun normalizeLocationKey(value: String): String {
        return CityOptions.normalizeCityId(value) ?: RecommendationNormalizer.id(value)
    }

    // Chooses the existing follow collection used by the following feed.
    // A manager is still a person - following them follows the dancer/teacher, never
    // "followingStudios" (that edge is for following the studio business account itself, see
    // StudioRepository.toggleFollowStudio).
    private fun followingCollectionFor(user: User): String {
        return if (user.verifiedTeacher || user.verifiedChoreographer) "followingTeachers" else "followingDancers"
    }

    // Builds a compact target document for the current user's following collection.
    private fun User.followingSummary(): Map<String, Any> {
        return mapOf(
            "targetId" to uid,
            "userId" to uid,
            "targetName" to fullName(),
            "targetRole" to role,
            "profileImageUrl" to profileImageUrl,
            "createdAt" to FieldValue.serverTimestamp()
        )
    }

    // Builds a compact viewer document for the target user's followers collection.
    private fun User.followerSummary(): Map<String, Any> {
        return mapOf(
            "followerId" to uid,
            "userId" to uid,
            "followerName" to fullName(),
            "role" to role,
            "profileImageUrl" to profileImageUrl,
            "createdAt" to FieldValue.serverTimestamp()
        )
    }

    // Sends a deduplicated in-app notification for a new follow.
    private fun createFollowNotification(viewer: User, targetUser: User) {
        notificationRepository.createNotification(
            recipientUid = targetUser.uid,
            type = InAppNotification.Types.FOLLOW,
            actorId = viewer.uid,
            actorName = viewer.fullName(),
            actorProfileImageUrl = viewer.profileImageUrl,
            title = "New follower",
            message = "${viewer.fullName()} started following you.",
            dedupeId = "follow_${targetUser.uid}_${viewer.uid}"
        )
    }

    private fun User.fullName(): String {
        return "${firstName} ${lastName}".trim().ifBlank { "Dancer" }
    }

    // Removes follow documents related to a newly blocked user.
    private fun deleteFollowEdges(
        batch: com.google.firebase.firestore.WriteBatch,
        viewerUid: String,
        targetUser: User
    ) {
        val targetUid = targetUser.uid
        batch.delete(
            db.collection(Constants.Collections.USERS)
                .document(viewerUid)
                .collection(followingCollectionFor(targetUser))
                .document(targetUid)
        )
        batch.delete(
            db.collection(Constants.Collections.USERS)
                .document(targetUid)
                .collection(FOLLOWERS_COLLECTION)
                .document(viewerUid)
        )
        listOf(FOLLOWING_DANCERS_COLLECTION, FOLLOWING_TEACHERS_COLLECTION, FOLLOWING_STUDIOS_COLLECTION)
            .forEach { collectionName ->
                batch.delete(
                    db.collection(Constants.Collections.USERS)
                        .document(targetUid)
                        .collection(collectionName)
                        .document(viewerUid)
                )
            }
        batch.delete(
            db.collection(Constants.Collections.USERS)
                .document(viewerUid)
                .collection(FOLLOWERS_COLLECTION)
                .document(targetUid)
        )
    }

    // Resolves user documents from relationship ids.
    private fun loadUsersFromRelationshipCollection(
        ownerUid: String,
        collectionName: String,
        idFields: List<String>,
        onSuccess: (List<User>) -> Unit,
        onFailure: (String) -> Unit
    ) {
        if (ownerUid.isBlank()) {
            onSuccess(emptyList())
            return
        }

        db.collection(Constants.Collections.USERS)
            .document(ownerUid)
            .collection(collectionName)
            .get()
            .addOnSuccessListener { snapshot ->
                val userIds = snapshot.documents.mapNotNull { document ->
                    idFields.firstNotNullOfOrNull { field -> document.getString(field)?.takeIf { it.isNotBlank() } }
                        ?: document.id
                }.filter { it.isNotBlank() }
                loadUsersByIds(userIds, onSuccess, onFailure)
            }
            .addOnFailureListener { error ->
                onFailure(error.message ?: "Failed to load users")
            }
    }

    // Loads individual users by id and returns them in the source order.
    private fun loadUsersByIds(
        userIds: List<String>,
        onSuccess: (List<User>) -> Unit,
        onFailure: (String) -> Unit
    ) {
        val ids = userIds.distinct().filter { it.isNotBlank() }
        if (ids.isEmpty()) {
            onSuccess(emptyList())
            return
        }

        val order = ids.withIndex().associate { it.value to it.index }
        var pendingLoads = ids.size
        val users = mutableListOf<User>()
        var completed = false
        ids.forEach { uid ->
            getUserByUid(
                uid = uid,
                onSuccess = { user ->
                    if (!completed) {
                        users.add(user)
                        pendingLoads -= 1
                        if (pendingLoads == 0) {
                            completed = true
                            onSuccess(users.sortedBy { order[it.uid] ?: Int.MAX_VALUE })
                        }
                    }
                },
                onFailure = {
                    // Skip ids that no longer resolve (e.g. a deleted account) instead of failing the whole list.
                    if (!completed) {
                        pendingLoads -= 1
                        if (pendingLoads == 0) {
                            completed = true
                            onSuccess(users.sortedBy { order[it.uid] ?: Int.MAX_VALUE })
                        }
                    }
                }
            )
        }
    }

    data class PreferenceSettings(
        val styles: List<String> = emptyList(),
        val level: String = "",
        val location: String = "",
        val preferredStudios: List<String> = emptyList(),
        val preferredTeachers: List<String> = emptyList(),
        val preferredDancers: List<String> = emptyList()
    )

    private companion object {
        const val FOLLOWERS_COLLECTION = "followers"
        const val BLOCKED_USERS_COLLECTION = "blockedUsers"
        const val FOLLOWING_DANCERS_COLLECTION = "followingDancers"
        const val FOLLOWING_TEACHERS_COLLECTION = "followingTeachers"
        const val FOLLOWING_STUDIOS_COLLECTION = "followingStudios"
    }
}
