// Backs the Discover/Search screens: loads studios, activities, and Google Places results,
// ranks them for the current user, and keeps a lightweight in-memory recommendation profile
// (style/studio/teacher scores) that gets nudged every time the user searches, opens, or saves
// something. This is a singleton object rather than a per-screen instance because that profile
// and the loaded item caches need to survive navigating between Discover, Search, and the map.
package com.ana.theflow.data.repository

import android.content.Context
import android.location.Location
import android.util.Log
import com.ana.theflow.BuildConfig
import com.ana.theflow.data.model.discovery.DiscoveryItem
import com.ana.theflow.data.recommendation.DiscoverRankingStrategy
import com.ana.theflow.data.recommendation.GeoPoint
import com.ana.theflow.data.recommendation.LocationSourceResolver
import com.ana.theflow.data.recommendation.RecommendationContext
import com.ana.theflow.data.recommendation.RecommendationFeatureExtractor
import com.ana.theflow.data.recommendation.RecommendationNormalizer
import com.ana.theflow.data.recommendation.RecommendationProfile
import com.ana.theflow.data.recommendation.RecommendationScoreExplanation
import com.ana.theflow.data.recommendation.RecommendationSurface
import com.ana.theflow.data.session.RecommendationPreferenceCache
import com.ana.theflow.utilities.Constants
import com.ana.theflow.utilities.CityOptions
import com.ana.theflow.utilities.StudioDiscoveryUtils
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions

object DiscoveryRepository {

    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()
    private val activityTrackingRepository = ActivityTrackingRepository()
    private val activityRepository = ActivityRepository()
    private var firebaseItems: List<DiscoveryItem> = emptyList()
    private var activityItems: List<DiscoveryItem> = emptyList()
    private var externalItems: List<DiscoveryItem> = emptyList()
    // Items + fetch timestamp per cache key (query+city+styles+rounded-location+radius, built by
    // the caller) - a real cache, not just a display-scoping map: loadExternalStudios() checks
    // this before calling the paid Google Places Text Search API, so panning back to an
    // already-searched area within EXTERNAL_STALE_AFTER_MS reuses the stored result instead of
    // billing another call.
    private val externalItemsByCacheKey = mutableMapOf<String, Pair<List<DiscoveryItem>, Long>>()
    private var activeExternalCacheKey: String = "default"
    private var externalStatusMessage: String = ""
    private var scopedUserId: String = ""

    var preferredStyles: MutableSet<String> = mutableSetOf()
    var preferredLevel: String = ""
    var preferredLocation: String = ""

    private val styleScores = mutableMapOf<String, Double>()
    private val studioScores = mutableMapOf<String, Double>()
    private val teacherScores = mutableMapOf<String, Double>()
    private val savedItemIds = mutableSetOf<String>()
    private var lastReason = "Based on your dance preferences"
    private val userRepository = UserRepository()

    // One cached result list + fetch timestamp per lazy-loaded Discover section, deliberately
    // the same "process-lifetime var on this singleton object" mechanism already used above for
    // firebaseItems/activityItems/externalItems - not a new caching layer. Cleared only by
    // resetForUser() (auth change), so scrolling a section out of view and back never refetches,
    // and re-entering the Discover screen within the same process reuses whatever was last loaded.
    private var studiosNearYouCache: List<DiscoveryItem> = emptyList()
    private var studiosNearYouFetchedAt: Long = 0L
    private var eventsThisWeekCache: List<DiscoveryItem> = emptyList()
    private var eventsThisWeekFetchedAt: Long = 0L
    private var teachersYouMayLikeCache: List<DiscoveryItem> = emptyList()
    private var teachersYouMayLikeFetchedAt: Long = 0L
    private const val SECTION_STALE_AFTER_MS = 5 * 60 * 1000L
    private const val EXTERNAL_STALE_AFTER_MS = 5 * 60 * 1000L

    // Clears every cached item list and score map and re-seeds them from this user's persisted
    // preferences. Called whenever the signed-in user changes, so one account's recommendation
    // state never bleeds into another's.
    fun resetForUser(uid: String = auth.currentUser?.uid.orEmpty()) {
        scopedUserId = uid
        firebaseItems = emptyList()
        activityItems = emptyList()
        externalItems = emptyList()
        externalItemsByCacheKey.clear()
        activeExternalCacheKey = "default"
        externalStatusMessage = ""
        // Pre-seed from last-known-good persisted values instead of leaving these blank - closes
        // the race where a location-dependent fetch (e.g. Discover's Studios Near You section)
        // can run before this session's own loadRecommendationProfile() call has resolved. See
        // RecommendationPreferenceCache for the full reasoning.
        val restored = RecommendationPreferenceCache.restore(uid)
        preferredStyles = restored?.styles.orEmpty().toMutableSet()
        preferredLevel = restored?.level.orEmpty()
        preferredLocation = restored?.location.orEmpty()
        styleScores.clear()
        studioScores.clear()
        teacherScores.clear()
        savedItemIds.clear()
        lastReason = "Based on your dance preferences"
        studiosNearYouCache = emptyList()
        studiosNearYouFetchedAt = 0L
        eventsThisWeekCache = emptyList()
        eventsThisWeekFetchedAt = 0L
        teachersYouMayLikeCache = emptyList()
        teachersYouMayLikeFetchedAt = 0L
    }

    // Called at the top of nearly every function here - if the signed-in user has changed since
    // the last call, wipe and reload everything scoped to the previous user first.
    private fun ensureScope() {
        val uid = auth.currentUser?.uid.orEmpty()
        if (scopedUserId != uid) resetForUser(uid)
    }

    // Loads user preferences into discovery recommendations.
    fun hydratePreferences(
        styles: List<String>,
        level: String,
        location: String,
        preferredStudios: List<String> = emptyList(),
        preferredTeachers: List<String> = emptyList(),
        preferredDancers: List<String> = emptyList()
    ) {
        ensureScope()
        if (styles.isNotEmpty()) preferredStyles = styles.map { RecommendationNormalizer.styleId(it) }.toMutableSet()
        if (level.isNotBlank()) preferredLevel = level
        CityOptions.normalizeOptionalCity(location)?.let { preferredLocation = it }
        preferredStyles.forEach { styleScores[it] = maxOf(styleScores[it] ?: 0.0, 3.0) }
        preferredStudios.forEach { studioScores[RecommendationNormalizer.id(it)] = maxOf(studioScores[RecommendationNormalizer.id(it)] ?: 0.0, 3.0) }
        preferredTeachers.forEach { teacherScores[RecommendationNormalizer.id(it)] = maxOf(teacherScores[RecommendationNormalizer.id(it)] ?: 0.0, 3.0) }
        preferredDancers.forEach { teacherScores[RecommendationNormalizer.id(it)] = maxOf(teacherScores[RecommendationNormalizer.id(it)] ?: 0.0, 3.0) }
        lastReason = "Based on your dance profile"
        RecommendationPreferenceCache.save(scopedUserId, preferredStyles, preferredLevel, preferredLocation)
    }

    // Same idea as hydratePreferences, but takes a full server-loaded RecommendationProfile and
    // replaces the local scores wholesale instead of merging onto them.
    fun hydrateProfile(profile: RecommendationProfile) {
        ensureScope()
        if (profile.userId.isNotBlank() && scopedUserId != profile.userId) resetForUser(profile.userId)
        preferredStyles = profile.danceStyles.map { RecommendationNormalizer.styleId(it) }.toMutableSet()
        preferredLevel = profile.danceLevel
        preferredLocation = profile.preferredRecommendationArea
        styleScores.clear()
        styleScores.putAll(profile.styleScores)
        studioScores.clear()
        studioScores.putAll(profile.studioScores)
        teacherScores.clear()
        teacherScores.putAll(profile.teacherScores)
        savedItemIds.addAll(profile.savedItemIds)
        lastReason = "Based on your recommendation profile"
        RecommendationPreferenceCache.save(scopedUserId, preferredStyles, preferredLevel, preferredLocation)
    }

    // Tracks a search action.
    fun trackSearch(style: String, location: String) {
        ensureScope()
        if (style.isNotBlank()) {
            val styleId = RecommendationNormalizer.styleId(style)
            styleScores[styleId] = (styleScores[styleId] ?: 0.0) + 2.0
            lastReason = "Because you searched for $style"
        }
        if (location.isNotBlank()) {
            lastReason = "Search location: ${CityOptions.displayNameFor(location)}"
        }
    }

    // Tracks a Discover search in local recommendations and Firebase activity history.
    fun trackDiscoverSearch(query: String, style: String, location: String) {
        trackSearch(style, location)

        val searchText = listOf(query, style, location)
            .filter { it.isNotBlank() }
            .joinToString(" / ")

        activityTrackingRepository.trackSearch(
            query = searchText.ifBlank { "empty_query" },
            danceStyles = listOf(style).filter { it.isNotBlank() },
            location = location
        )
    }

    // Tracks that a discovery item was opened and updates recommendations.
    fun trackOpenItem(item: DiscoveryItem) {
        ensureScope()
        val styleId = RecommendationNormalizer.styleId(item.style)
        val studioId = RecommendationNormalizer.id(item.studio)
        val teacherId = RecommendationNormalizer.id(item.teacher)
        styleScores[styleId] = (styleScores[styleId] ?: 0.0) + 2.0
        studioScores[studioId] = (studioScores[studioId] ?: 0.0) + 2.0
        teacherScores[teacherId] = (teacherScores[teacherId] ?: 0.0) + 1.0
        lastReason = "Because you viewed ${item.style} classes"

        activityTrackingRepository.trackOpenDiscoveryItem(
            itemId = item.id,
            itemName = item.title,
            targetType = targetTypeFor(item),
            danceStyles = listOf(item.style).filter { it.isNotBlank() },
            location = item.location,
            metadata = mapOf(
                "studio" to item.studio,
                "teacher" to item.teacher,
                "level" to item.level
            ).filterValues { it.isNotBlank() }
        )
    }

    // Updates local recommendation state when an item is saved.
    fun trackSave(item: DiscoveryItem) {
        ensureScope()
        savedItemIds.add(item.id)
        val styleId = RecommendationNormalizer.styleId(item.style)
        val studioId = RecommendationNormalizer.id(item.studio)
        styleScores[styleId] = (styleScores[styleId] ?: 0.0) + 4.0
        studioScores[studioId] = (studioScores[studioId] ?: 0.0) + 4.0
        lastReason = "Because you saved ${item.studio}"
    }

    // Checks whether an item is saved locally.
    fun isSaved(item: DiscoveryItem): Boolean = savedItemIds.contains(item.id)

    // Loads saved discovery item ids for the current user.
    fun loadSavedItems(
        onSuccess: () -> Unit,
        onFailure: (String) -> Unit
    ) {
        ensureScope()
        val uid = auth.currentUser?.uid
        if (uid == null) {
            onFailure("User is not logged in")
            return
        }

        db.collection(Constants.Collections.USERS)
            .document(uid)
            .collection("savedItems")
            .get()
            .addOnSuccessListener { snapshot ->
                savedItemIds.clear()
                snapshot.documents.forEach { document ->
                    val itemId = document.getString("itemId").orEmpty().ifBlank { document.id }
                    savedItemIds.add(itemId)
                }
                onSuccess()
            }
            .addOnFailureListener { error ->
                onFailure(error.message ?: "Failed to load saved items")
            }
    }

    // Loads full saved discovery items for the current user.
    fun loadSavedDiscoveryItems(
        onSuccess: (List<DiscoveryItem>) -> Unit,
        onFailure: (String) -> Unit
    ) {
        ensureScope()
        val uid = auth.currentUser?.uid
        if (uid == null) {
            onFailure("User is not logged in")
            return
        }

        db.collection(Constants.Collections.USERS)
            .document(uid)
            .collection("savedItems")
            .get()
            .addOnSuccessListener { snapshot ->
                savedItemIds.clear()
                val savedItems = snapshot.documents.mapNotNull { document ->
                    val itemId = document.getString("itemId").orEmpty().ifBlank { document.id }
                    savedItemIds.add(itemId)
                    DiscoveryItem(
                        id = itemId,
                        title = document.getString("title").orEmpty(),
                        studio = document.getString("studio").orEmpty(),
                        teacher = document.getString("teacher").orEmpty(),
                        style = document.getString("style").orEmpty(),
                        level = document.getString("level").orEmpty(),
                        location = document.getString("location").orEmpty(),
                        time = document.getString("time").orEmpty(),
                        type = document.getString("itemType").orEmpty().ifBlank { "Discovery item" },
                        source = document.getString("source").orEmpty().ifBlank { DiscoveryItem.SOURCE_INTERNAL },
                        googlePlaceId = document.getString("googlePlaceId").orEmpty(),
                        address = document.getString("address").orEmpty()
                    )
                }
                rememberItems(savedItems)
                onSuccess(savedItems)
            }
            .addOnFailureListener { error ->
                onFailure(error.message ?: "Failed to load saved items")
            }
    }

    // Saves a discovery item permanently for the current user and updates recommendations.
    fun saveItem(
        item: DiscoveryItem,
        onSuccess: () -> Unit,
        onFailure: (String) -> Unit
    ) {
        ensureScope()
        val uid = auth.currentUser?.uid
        if (uid == null) {
            onFailure("User is not logged in")
            return
        }

        val savedItem = mapOf(
            "itemId" to item.id,
            "itemType" to item.type.ifBlank { "Discovery item" },
            "title" to item.title,
            "studio" to item.studio,
            "teacher" to item.teacher,
            "style" to item.style,
            "level" to item.level,
            "location" to item.location,
            "time" to item.time,
            "source" to item.source,
            "googlePlaceId" to item.googlePlaceId,
            "address" to item.address,
            "savedAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )

        db.collection(Constants.Collections.USERS)
            .document(uid)
            .collection("savedItems")
            .document(item.id)
            .set(savedItem, SetOptions.merge())
            .addOnSuccessListener {
                trackSave(item)
                activityTrackingRepository.trackSaveItem(
                    targetType = targetTypeFor(item),
                    targetId = item.id,
                    targetName = item.title,
                    danceStyles = listOf(item.style).filter { it.isNotBlank() },
                    location = item.location,
                    interactionStrength = 1.0
                )
                onSuccess()
            }
            .addOnFailureListener { error ->
                onFailure(error.message ?: "Failed to save item")
            }
    }

    // Removes a saved discovery item for the current user.
    fun unsaveItem(
        item: DiscoveryItem,
        onSuccess: () -> Unit,
        onFailure: (String) -> Unit
    ) {
        ensureScope()
        val uid = auth.currentUser?.uid
        if (uid == null) {
            onFailure("User is not logged in")
            return
        }

        db.collection(Constants.Collections.USERS)
            .document(uid)
            .collection("savedItems")
            .document(item.id)
            .delete()
            .addOnSuccessListener {
                savedItemIds.remove(item.id)
                onSuccess()
            }
            .addOnFailureListener { error ->
                onFailure(error.message ?: "Failed to unsave item")
            }
    }

    // Returns discovery items ranked for the user.
    fun recommendedItems(): List<DiscoveryItem> {
        return recommendedItems(recommendationContext(RecommendationSurface.DISCOVER))
    }

    fun recommendedItems(context: RecommendationContext): List<DiscoveryItem> {
        ensureScope()
        val ranked = DiscoverRankingStrategy.rank(allItems(), context)
        logExplanations(ranked.take(8).map { DiscoverRankingStrategy.score(it, context) })
        return ranked
    }

    // Returns popular discovery items near the preferred location.
    fun popularNearYou(): List<DiscoveryItem> {
        return popularNearYou(recommendationContext(RecommendationSurface.DISCOVER))
    }

    fun popularNearYou(context: RecommendationContext): List<DiscoveryItem> {
        ensureScope()
        val resolved = LocationSourceResolver.resolve(context)
        val nearby = if (resolved.cityId.isBlank()) {
            emptyList()
        } else {
            allItems().filter { CityOptions.normalizeCityId(it.location) == resolved.cityId }
        }
        return nearby.ifEmpty { allItems().take(3) }
    }

    // --- Discover's Studios Near You / Events This Week / Teachers You May Like sections.
    // Each one is a deliberately simple proximity/relevance filter, not full recommendation
    // scoring, and each is fetched independently the first time its section actually scrolls
    // into view rather than upfront on Discover's initial load, so the screen doesn't pay for
    // three extra queries before the user has even scrolled down to see them.

    fun studiosNearYouCached(): List<DiscoveryItem> = studiosNearYouCache
    fun eventsThisWeekCached(): List<DiscoveryItem> = eventsThisWeekCache
    fun teachersYouMayLikeCached(): List<DiscoveryItem> = teachersYouMayLikeCache

    fun isStudiosNearYouStale(now: Long = System.currentTimeMillis()) = isSectionStale(studiosNearYouFetchedAt, now)
    fun isEventsThisWeekStale(now: Long = System.currentTimeMillis()) = isSectionStale(eventsThisWeekFetchedAt, now)
    fun isTeachersYouMayLikeStale(now: Long = System.currentTimeMillis()) = isSectionStale(teachersYouMayLikeFetchedAt, now)

    private fun isSectionStale(fetchedAt: Long, now: Long): Boolean {
        return fetchedAt == 0L || now - fetchedAt > SECTION_STALE_AFTER_MS
    }

    // Internal, platform-managed studios only (not a Google Places merge) sorted by real
    // distance from the given viewer point - deliberately not re-querying Google Places from a
    // scroll gesture, since that call costs API quota and the recommended/search surfaces
    // already cover it. viewerPoint is resolved by the caller (DiscoverFragment), which prefers
    // the device's real last-known location over the shared recommendation-profile location -
    // "near you" needs to reflect where the user actually is, not a saved preference used for
    // ranking the Recommended feed.
    fun studiosNearYou(
        viewerPoint: GeoPoint?,
        onSuccess: (List<DiscoveryItem>) -> Unit,
        onFailure: (String) -> Unit
    ) {
        ensureScope()
        fun computeAndCache() {
            val studios = firebaseItems
                .filter { it.type.equals("Studio", ignoreCase = true) }
                .map { item -> withDistance(item, viewerPoint) }
                .sortedBy { it.distanceMeters ?: Double.MAX_VALUE }
            studiosNearYouCache = studios
            studiosNearYouFetchedAt = System.currentTimeMillis()
            onSuccess(studios)
        }
        if (firebaseItems.isNotEmpty()) {
            computeAndCache()
        } else {
            loadApprovedStudios(
                onSuccess = { computeAndCache() },
                onFailure = { error -> onFailure(error) }
            )
        }
    }

    // Published activities starting within the next 7 days - a real server-side date range via
    // ActivityRepository.loadActivitiesThisWeek, not a client-side filter of the general feed.
    fun eventsThisWeek(
        onSuccess: (List<DiscoveryItem>) -> Unit,
        onFailure: (String) -> Unit
    ) {
        ensureScope()
        activityRepository.loadActivitiesThisWeek(
            onSuccess = { activities ->
                val items = activityRepository.toDiscoveryItems(activities)
                eventsThisWeekCache = items
                eventsThisWeekFetchedAt = System.currentTimeMillis()
                onSuccess(items)
            },
            onFailure = { error -> onFailure(error) }
        )
    }

    // Verified teachers/choreographers, biased toward the viewer's own dance styles. Reuses
    // UserRepository.searchUsers' existing "fetch a bounded batch, filter/sort client-side"
    // pattern (same one the studio teacher-picker already uses) instead of a new composite
    // Firestore index for role+verified filtering. "Where they teach" is a follow-up
    // whereArrayContains("teacherUids", uid) lookup per teacher, bounded to the small batch
    // actually shown - it's an extra read, but only for this lazy-loaded section.
    fun teachersYouMayLike(
        onSuccess: (List<DiscoveryItem>) -> Unit,
        onFailure: (String) -> Unit
    ) {
        ensureScope()
        val viewerUid = auth.currentUser?.uid.orEmpty()
        userRepository.searchUsers(
            query = "",
            dancersOnly = false,
            onSuccess = { users ->
                val teachers = users
                    .filter { (it.verifiedTeacher || it.verifiedChoreographer) && it.uid != viewerUid }
                    .sortedWith(
                        compareByDescending<com.ana.theflow.data.model.user.User> { teacher ->
                            teacher.danceStyles.any { style -> preferredStyles.contains(RecommendationNormalizer.styleId(style)) }
                        }.thenBy { "${it.firstName} ${it.lastName}" }
                    )
                    .take(10)
                attachTeachingStudios(teachers) { items ->
                    teachersYouMayLikeCache = items
                    teachersYouMayLikeFetchedAt = System.currentTimeMillis()
                    onSuccess(items)
                }
            },
            onFailure = { error -> onFailure(error) }
        )
    }

    // Looks up which studio (if any) each teacher currently teaches at, one query per teacher,
    // and turns the pair into a DiscoveryItem the Teachers You May Like card can render.
    private fun attachTeachingStudios(teachers: List<com.ana.theflow.data.model.user.User>, onDone: (List<DiscoveryItem>) -> Unit) {
        if (teachers.isEmpty()) {
            onDone(emptyList())
            return
        }
        var pending = teachers.size
        val results = arrayOfNulls<DiscoveryItem>(teachers.size)
        teachers.forEachIndexed { index, teacher ->
            db.collection(Constants.Collections.STUDIOS)
                .whereArrayContains("teacherUids", teacher.uid)
                .limit(1)
                .get()
                .addOnSuccessListener { snapshot ->
                    val studioName = snapshot.documents.firstOrNull()
                        ?.let { it.getString("displayName") ?: it.getString("name") }
                        .orEmpty()
                    results[index] = teacher.toTeacherDiscoveryItem(studioName)
                    pending -= 1
                    if (pending == 0) onDone(results.filterNotNull())
                }
                .addOnFailureListener {
                    results[index] = teacher.toTeacherDiscoveryItem("")
                    pending -= 1
                    if (pending == 0) onDone(results.filterNotNull())
                }
        }
    }

    // Stamps a straight-line distance from the viewer onto the item, if both have coordinates.
    private fun withDistance(item: DiscoveryItem, viewerPoint: GeoPoint?): DiscoveryItem {
        val lat = item.latitude
        val lng = item.longitude
        if (viewerPoint == null || lat == null || lng == null) return item
        val result = FloatArray(1)
        android.location.Location.distanceBetween(viewerPoint.latitude, viewerPoint.longitude, lat, lng, result)
        return item.copy(distanceMeters = result[0].toDouble())
    }

    // Wraps a teacher/choreographer user as a DiscoveryItem so it can sit alongside studio and
    // event cards in the same list.
    private fun com.ana.theflow.data.model.user.User.toTeacherDiscoveryItem(studioName: String): DiscoveryItem {
        return DiscoveryItem(
            id = uid,
            title = "$firstName $lastName".trim(),
            studio = studioName,
            teacher = "$firstName $lastName".trim(),
            style = danceStyles.firstOrNull().orEmpty().ifBlank { "Dance" },
            level = danceLevel,
            location = location,
            time = "",
            type = "Teacher",
            coverImageUrl = profileImageUrl,
            displayType = "teacher"
        )
    }

    // Packages up everything we currently know about this user's dance preferences and behavior
    // scores into a RecommendationProfile, the shape the ranking strategies actually consume.
    fun currentRecommendationProfile(): RecommendationProfile {
        ensureScope()
        val locationScores = preferredLocation.takeIf { it.isNotBlank() }
            ?.let { mapOf((CityOptions.normalizeCityId(it) ?: RecommendationNormalizer.id(it)) to 3.0) }
            .orEmpty()
        return RecommendationProfile(
            userId = scopedUserId,
            danceStyles = preferredStyles.toList(),
            danceLevel = preferredLevel,
            profileLocation = preferredLocation,
            preferredRecommendationArea = preferredLocation,
            styleScores = styleScores.toMap(),
            locationScores = locationScores,
            studioScores = studioScores.toMap(),
            teacherScores = teacherScores.toMap(),
            savedItemIds = savedItemIds.toSet()
        )
    }

    // Bundles the user's profile together with whatever location/filter info the calling screen
    // has on hand (device GPS, map camera position, active filters) into the single context
    // object every ranking strategy takes as input.
    fun recommendationContext(
        surface: RecommendationSurface,
        manualSelectedLocation: String = "",
        currentDeviceLocation: GeoPoint? = null,
        mapCameraLocation: GeoPoint? = null,
        selectedFilters: Map<String, String> = emptyMap(),
        followingIds: Set<String> = emptySet(),
        profileOverride: RecommendationProfile? = null
    ): RecommendationContext {
        ensureScope()
        val profile = profileOverride ?: currentRecommendationProfile()
        return RecommendationContext(
            userId = profile.userId.ifBlank { scopedUserId },
            surface = surface,
            profileLocation = profile.profileLocation,
            preferredRecommendationArea = profile.preferredRecommendationArea,
            currentDeviceLocation = currentDeviceLocation,
            manualSelectedLocation = CityOptions.normalizeOptionalCity(manualSelectedLocation).orEmpty(),
            mapCameraLocation = mapCameraLocation,
            selectedFilters = selectedFilters,
            danceStyles = profile.danceStyles,
            danceLevel = profile.danceLevel,
            followingIds = followingIds,
            recommendationProfile = profile
        )
    }

    // Returns Google Places studios that survived duplicate filtering.
    fun externalStudioItems(): List<DiscoveryItem> {
        return allItems().filter { it.source == DiscoveryItem.SOURCE_GOOGLE }
    }

    // Filters discovery items by search fields.
    fun search(
        style: String,
        level: String,
        location: String,
        teacher: String,
        studio: String,
        time: String
    ): List<DiscoveryItem> {
        ensureScope()
        trackSearch(style, location)
        val locationId = CityOptions.normalizeCityId(location)

        return allItems().filter { item ->
            item.matches(style, item.style) &&
                item.matches(level, item.level) &&
                (location.isBlank() || locationId == null && item.matches(location, item.location) || locationId != null && CityOptions.normalizeCityId(item.location) == locationId) &&
                item.matches(teacher, item.teacher) &&
                item.matches(studio, item.studio) &&
                item.matches(time, item.time)
        }
    }

    // Finds a discovery item by id.
    fun itemById(id: String): DiscoveryItem? {
        return allItems().firstOrNull { it.id == id }
    }

    // Adds items to the temporary in-app cache so saved items can open in Detail.
    fun rememberItems(items: List<DiscoveryItem>) {
        ensureScope()
        if (items.isEmpty()) return
        val existingById = firebaseItems.associateBy { it.id }.toMutableMap()
        items.forEach { item ->
            if (item.id.isNotBlank()) existingById[item.id] = item
        }
        firebaseItems = existingById.values.toList()
    }

    // Loads approved studio data from Firestore.
    fun loadApprovedStudios(
        onSuccess: (List<DiscoveryItem>) -> Unit,
        onFailure: (String) -> Unit
    ) {
        ensureScope()
        db.collection(Constants.Collections.STUDIOS)
            .get()
            .addOnSuccessListener { snapshot ->
                firebaseItems = snapshot.documents.mapNotNull { document ->
                    val status = document.getString("status").orEmpty()
                    val verified = document.getBoolean("verified") == true
                    val isApproved = status.equals(Constants.StudioStatus.APPROVED.name, ignoreCase = true)
                    if (!verified && !isApproved) return@mapNotNull null

                    val styles = (document.get("danceStyles") as? List<*>)
                        ?.mapNotNull { it as? String }
                        ?.filter { it.isNotBlank() }
                        .orEmpty()
                    val studioName = document.firstNonBlankString("displayName", "name")
                    if (studioName.isBlank()) return@mapNotNull null

                    val branchName = document.firstNonBlankString("branchName")
                    val city = CityOptions.normalizeOptionalCity(document.firstNonBlankString("city", "location")).orEmpty()
                        .ifBlank { document.firstNonBlankString("city", "location") }
                    val googlePlaceId = document.firstNonBlankString("googlePlaceId")
                    val title = listOf(studioName, branchName)
                        .filter { it.isNotBlank() }
                        .joinToString(" - ")
                    // Studios created before coordinates were captured (or never re-saved since)
                    // would otherwise have no lat/lng and stay invisible on the map forever -
                    // approximate from the city here at read time so every existing studio shows
                    // up immediately, without needing a data migration or a manager to re-save.
                    val cityFallback = CityOptions.cityFor(city)
                    val latitude = document.getDouble("latitude") ?: cityFallback?.latitude
                    val longitude = document.getDouble("longitude") ?: cityFallback?.longitude

                    DiscoveryItem(
                        createdAtMillis = document.getTimestamp("createdAt")?.toDate()?.time ?: 0L,
                        followersCount = document.getLong("followersCount") ?: 0L,
                        id = document.id,
                        title = title,
                        studio = studioName,
                        teacher = "Studio",
                        style = styles.firstOrNull().orEmpty().ifBlank { "Dance" },
                        level = "All levels",
                        location = city,
                        time = document.firstNonBlankString("openingHours", "time").ifBlank { "Contact studio" },
                        type = "Studio",
                        latitude = latitude,
                        longitude = longitude,
                        claimStatus = document.firstNonBlankString("claimStatus"),
                        ownerUid = document.firstNonBlankString("ownerUid"),
                        source = DiscoveryItem.SOURCE_INTERNAL,
                        googlePlaceId = googlePlaceId,
                        address = document.firstNonBlankString("address"),
                        coverImageUrl = document.firstNonBlankString("coverImageUrl", "imageUrl", "photoUrl"),
                        displayType = "studio"
                    )
                }
                onSuccess(firebaseItems)
            }
            .addOnFailureListener { error ->
                onFailure(error.message ?: "Failed to load studios")
            }
    }

    // Loads published professional activities and adapts them to current Discover cards.
    fun loadPublishedActivities(
        onSuccess: (List<DiscoveryItem>) -> Unit,
        onFailure: (String) -> Unit
    ) {
        ensureScope()
        activityRepository.loadPublishedActivities(
            onSuccess = { activities ->
                activityItems = activityRepository.toDiscoveryItems(activities)
                onSuccess(activityItems)
            },
            onFailure = onFailure
        )
    }

    // Runs a Google Places text search for studios, unless we already have a fresh-enough
    // result cached under this exact cacheKey - the caller builds that key from query+city+
    // styles+rounded-location+radius, so panning the map back over an area you already searched
    // reuses the stored result instead of billing another Places API call.
    fun loadExternalStudios(
        context: Context,
        query: String = "",
        city: String = "",
        location: Location? = null,
        radiusMeters: Double? = null,
        usePreferredCityFallback: Boolean = true,
        cacheKey: String = "default",
        onSuccess: (List<DiscoveryItem>) -> Unit,
        onFailure: (String) -> Unit
    ) {
        ensureScope()
        activeExternalCacheKey = cacheKey
        val cached = externalItemsByCacheKey[cacheKey]
        if (cached != null && System.currentTimeMillis() - cached.second <= EXTERNAL_STALE_AFTER_MS) {
            externalItems = cached.first
            onSuccess(cached.first)
            return
        }
        GooglePlacesStudioDataSource(context).searchStudios(
            query = query,
            city = city.ifBlank { if (usePreferredCityFallback) preferredLocation else "" },
            location = location,
            radiusMeters = radiusMeters,
            onSuccess = { studios ->
                externalItems = studios
                externalItemsByCacheKey[cacheKey] = studios to System.currentTimeMillis()
                externalStatusMessage = if (studios.isEmpty()) {
                    "No Google studios found for this area yet."
                } else {
                    "Google Places results included."
                }
                onSuccess(studios)
            },
            onFailure = { error ->
                externalItems = emptyList()
                externalStatusMessage = error
                onFailure(error)
            }
        )
    }

    // Filters discovery items for the Discover screen.
    fun filterDiscoverItems(
        query: String,
        style: String,
        level: String,
        location: String
    ): List<DiscoveryItem> {
        val normalizedQuery = query.trim()
        val locationId = CityOptions.normalizeCityId(location)
        return allItems().filter { item ->
            item.matches(style, item.style) &&
                item.matchesLevel(level) &&
                (location.isBlank() || locationId == null && item.matches(location, item.location) || locationId != null && CityOptions.normalizeCityId(item.location) == locationId) &&
                (
                    normalizedQuery.isBlank() ||
                        item.title.contains(normalizedQuery, ignoreCase = true) ||
                        item.studio.contains(normalizedQuery, ignoreCase = true) ||
                        item.teacher.contains(normalizedQuery, ignoreCase = true) ||
                        item.style.contains(normalizedQuery, ignoreCase = true) ||
                        item.level.contains(normalizedQuery, ignoreCase = true) ||
                        item.location.contains(normalizedQuery, ignoreCase = true) ||
                        item.time.contains(normalizedQuery, ignoreCase = true) ||
                        item.type.contains(normalizedQuery, ignoreCase = true)
                    )
        }
    }

    // Returns a single, concrete, plain-language reason for this specific recommendation - the
    // strongest signal that actually drove it (e.g. "Because you like Hip Hop", "Because
    // you're in Tel Aviv"), not a raw internal score dump or one generic sentence shared across
    // every card. It calls the real DiscoverRankingStrategy.score() function to find the actual
    // winning component rather than keeping its own separate copy of the scoring weights -
    // weights only ever live in one place, so the displayed "why" can never disagree with what
    // actually drove the card's rank. It then just fills in the concrete value (the real style,
    // city, studio, or teacher name) that the component's generic label doesn't carry on its own.
    fun explanationFor(item: DiscoveryItem): String {
        val context = recommendationContext(RecommendationSurface.DISCOVER)
        val profile = context.recommendationProfile
        val resolved = LocationSourceResolver.resolve(context)
        val explanation = DiscoverRankingStrategy.score(item, context, resolved)
        val topLabel = explanation.components.filter { it.score > 0.0 }.maxByOrNull { it.score }?.label
            ?: return "New for you"

        val locationId = RecommendationFeatureExtractor.normalizeLocation(item.location.ifBlank { item.address })
        return when (topLabel) {
            "Explicit style preference" -> "Because you like ${item.style}"
            "Learned style" -> "Because you often engage with ${item.style}"
            "Studio behavior" -> "Because you like ${item.studio}"
            "Teacher behavior" -> "Because you follow dancers like ${item.teacher}"
            "Proximity" -> {
                val cityName = resolved.displayName.ifBlank { CityOptions.displayNameFor(resolved.cityId) }
                if (cityName.isNotBlank()) "Because you're in $cityName" else "Because it's near you"
            }
            "Learned location" -> "Because you're interested in ${CityOptions.displayNameFor(locationId)}"
            "Level match" -> "Because it matches your ${profile.danceLevel} level"
            "Popularity" -> "Popular right now"
            "Freshness" -> "New for you"
            else -> "New for you"
        }
    }

    // Builds a short summary of recommendation behavior.
    fun behaviorSummary(): String {
        val topStyle = styleScores.maxByOrNull { it.value }?.key ?: preferredStyles.firstOrNull() ?: "Not set"
        val topStudio = studioScores.maxByOrNull { it.value }?.key ?: "No studio yet"
        return "Top style: $topStyle\nTop studio: $topStudio\nLocation: $preferredLocation" +
            if (externalStatusMessage.isBlank()) "" else "\n$externalStatusMessage"
    }

    // Loads the current user recommendation profile.
    fun loadRecommendationProfile(
        onSuccess: (Map<String, Any>) -> Unit,
        onFailure: (String) -> Unit
    ) {
        val uid = auth.currentUser?.uid
        if (uid == null) {
            onFailure("User is not logged in")
            return
        }

        db.collection(Constants.Collections.USERS)
            .document(uid)
            .collection("recommendationProfile")
            .document("main")
            .get()
            .addOnSuccessListener { document ->
                onSuccess(document.data.orEmpty())
            }
            .addOnFailureListener { error ->
                onFailure(error.message ?: "Failed to load recommendation profile")
            }
    }

    // Checks whether a value matches a search query.
    private fun DiscoveryItem.matches(query: String, value: String): Boolean {
        return query.isBlank() || value.contains(query, ignoreCase = true)
    }

    // Checks whether an item matches the selected level filter.
    private fun DiscoveryItem.matchesLevel(query: String): Boolean {
        if (query.isBlank()) return true
        return level.equals(query, ignoreCase = true) ||
            level.equals("All levels", ignoreCase = true)
    }

    // Debug-only logging of how the top items got their scores, so ranking behavior can be
    // sanity-checked from Logcat during development. Compiled out of release builds.
    private fun logExplanations(explanations: List<RecommendationScoreExplanation>) {
        if (!BuildConfig.DEBUG) return
        explanations.forEach { explanation ->
            Log.d(
                "RecommendationDebug",
                buildString {
                    append("${explanation.rankingStrategy}: ${explanation.itemType} ${explanation.itemId} score=${explanation.finalScore}")
                    append(" source=${explanation.resolvedLocationSource}")
                    if (explanation.appliedFilters.isNotEmpty()) append(" filters=${explanation.appliedFilters}")
                    if (explanation.reasons.isNotEmpty()) append(" reasons=${explanation.reasons.joinToString("; ")}")
                }
            )
        }
    }

    // Returns loaded Firestore and external discovery items.
    private fun allItems(): List<DiscoveryItem> {
        val internal = firebaseItems + activityItems
        val scopedExternalItems = externalItemsByCacheKey[activeExternalCacheKey]?.first ?: externalItems
        if (scopedExternalItems.isEmpty()) return internal
        return StudioDiscoveryUtils.mergeInternalAndExternal(internal, scopedExternalItems)
    }

    // Maps a discovery item to the activity target type stored in Firebase.
    private fun targetTypeFor(item: DiscoveryItem): String {
        return when (item.type.lowercase()) {
            "studio" -> ActivityTrackingRepository.TargetTypes.STUDIO
            "class" -> ActivityTrackingRepository.TargetTypes.CLASS
            "workshop" -> ActivityTrackingRepository.TargetTypes.WORKSHOP
            "audition" -> ActivityTrackingRepository.TargetTypes.AUDITION
            "event" -> ActivityTrackingRepository.TargetTypes.EVENT
            else -> ActivityTrackingRepository.TargetTypes.DISCOVERY_ITEM
        }
    }

    // Returns the first non-empty string field from a document.
    private fun com.google.firebase.firestore.DocumentSnapshot.firstNonBlankString(
        vararg fields: String
    ): String {
        return fields.firstNotNullOfOrNull { field ->
            getString(field)?.takeIf { it.isNotBlank() }
        }.orEmpty()
    }
}
