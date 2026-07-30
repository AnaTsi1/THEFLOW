package com.ana.theflow.data.repository

import android.content.Context
import android.location.Location
import com.ana.theflow.data.model.discovery.DiscoveryItem
import com.ana.theflow.utilities.Constants
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
    private var externalStatusMessage: String = ""

    var preferredStyles: MutableSet<String> = mutableSetOf()
    var preferredLevel: String = ""
    var preferredLocation: String = ""

    private val styleScores = mutableMapOf<String, Int>()
    private val studioScores = mutableMapOf<String, Int>()
    private val teacherScores = mutableMapOf<String, Int>()
    private val savedItemIds = mutableSetOf<String>()
    private var lastReason = "Based on your dance preferences"

    // Loads user preferences into discovery recommendations.
    fun hydratePreferences(
        styles: List<String>,
        level: String,
        location: String,
        preferredStudios: List<String> = emptyList(),
        preferredTeachers: List<String> = emptyList(),
        preferredDancers: List<String> = emptyList()
    ) {
        if (styles.isNotEmpty()) preferredStyles = styles.toMutableSet()
        if (level.isNotBlank()) preferredLevel = level
        if (location.isNotBlank()) preferredLocation = location
        preferredStyles.forEach { styleScores[it] = (styleScores[it] ?: 0) + 3 }
        preferredStudios.forEach { studioScores[it] = (studioScores[it] ?: 0) + 3 }
        preferredTeachers.forEach { teacherScores[it] = (teacherScores[it] ?: 0) + 3 }
        preferredDancers.forEach { teacherScores[it] = (teacherScores[it] ?: 0) + 3 }
        lastReason = "Based on your dance profile"
    }

    // Tracks a search action.
    fun trackSearch(style: String, location: String) {
        if (style.isNotBlank()) {
            styleScores[style] = (styleScores[style] ?: 0) + 2
            lastReason = "Because you searched for $style"
        }
        if (location.isNotBlank()) {
            preferredLocation = location
            lastReason = "Popular near $location"
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
        styleScores[item.style] = (styleScores[item.style] ?: 0) + 2
        studioScores[item.studio] = (studioScores[item.studio] ?: 0) + 2
        teacherScores[item.teacher] = (teacherScores[item.teacher] ?: 0) + 1
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
        savedItemIds.add(item.id)
        styleScores[item.style] = (styleScores[item.style] ?: 0) + 4
        studioScores[item.studio] = (studioScores[item.studio] ?: 0) + 4
        lastReason = "Because you saved ${item.studio}"
    }

    // Checks whether an item is saved locally.
    fun isSaved(item: DiscoveryItem): Boolean = savedItemIds.contains(item.id)

    // Loads saved discovery item ids for the current user.
    fun loadSavedItems(
        onSuccess: () -> Unit,
        onFailure: (String) -> Unit
    ) {
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
        val candidates = RecommendationEngine.generateCandidates(
            items = allItems(),
            preferredStyles = preferredStyles,
            preferredLevel = preferredLevel,
            preferredLocation = preferredLocation,
            savedItemIds = savedItemIds,
            styleScores = styleScores,
            studioScores = studioScores,
            teacherScores = teacherScores
        )
        return RecommendationEngine.rankCandidates(
            candidates = candidates,
            preferredStyles = preferredStyles,
            preferredLevel = preferredLevel,
            preferredLocation = preferredLocation,
            savedItemIds = savedItemIds,
            styleScores = styleScores,
            studioScores = studioScores,
            teacherScores = teacherScores
        ).map { it.item }
    }

    // Returns ranked discovery results with explanations.
    fun recommendationResults(): List<RecommendationResult> {
        val candidates = RecommendationEngine.generateCandidates(
            items = allItems(),
            preferredStyles = preferredStyles,
            preferredLevel = preferredLevel,
            preferredLocation = preferredLocation,
            savedItemIds = savedItemIds,
            styleScores = styleScores,
            studioScores = studioScores,
            teacherScores = teacherScores
        )
        return RecommendationEngine.rankCandidates(
            candidates = candidates,
            preferredStyles = preferredStyles,
            preferredLevel = preferredLevel,
            preferredLocation = preferredLocation,
            savedItemIds = savedItemIds,
            styleScores = styleScores,
            studioScores = studioScores,
            teacherScores = teacherScores
        )
    }

    // Returns popular discovery items near the preferred location.
    fun popularNearYou(): List<DiscoveryItem> {
        return allItems()
            .filter { it.location.equals(preferredLocation, ignoreCase = true) }
            .ifEmpty { allItems().take(3) }
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
        trackSearch(style, location)

        return allItems().filter { item ->
            item.matches(style, item.style) &&
                item.matches(level, item.level) &&
                item.matches(location, item.location) &&
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
                    val city = document.firstNonBlankString("city", "location")
                    val googlePlaceId = document.firstNonBlankString("googlePlaceId")
                    val title = listOf(studioName, branchName)
                        .filter { it.isNotBlank() }
                        .joinToString(" - ")

                    DiscoveryItem(
                        id = document.id,
                        title = title,
                        studio = studioName,
                        teacher = "Studio",
                        style = styles.firstOrNull().orEmpty().ifBlank { "Dance" },
                        level = "All levels",
                        location = city,
                        time = document.firstNonBlankString("openingHours", "time").ifBlank { "Contact studio" },
                        type = "Studio",
                        latitude = document.getDouble("latitude"),
                        longitude = document.getDouble("longitude"),
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
        activityRepository.loadPublishedActivities(
            onSuccess = { activities ->
                activityItems = activityRepository.toDiscoveryItems(activities)
                onSuccess(activityItems)
            },
            onFailure = onFailure
        )
    }

    fun loadExternalStudios(
        context: Context,
        query: String = "",
        city: String = "",
        location: Location? = null,
        usePreferredCityFallback: Boolean = true,
        onSuccess: (List<DiscoveryItem>) -> Unit,
        onFailure: (String) -> Unit
    ) {
        GooglePlacesStudioDataSource(context).searchStudios(
            query = query,
            city = city.ifBlank { if (usePreferredCityFallback) preferredLocation else "" },
            location = location,
            onSuccess = { studios ->
                externalItems = studios
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
        return allItems().filter { item ->
            item.matches(style, item.style) &&
                item.matchesLevel(level) &&
                item.matches(location, item.location) &&
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

    // Returns the recommendation explanation for an item.
    fun explanationFor(item: DiscoveryItem): String {
        return recommendationResults()
            .firstOrNull { it.item.id == item.id }
            ?.reasons
            ?.joinToString(separator = "\n")
            ?: lastReason
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

    // Returns the recommendation score for an item.
    private fun scoreFor(item: DiscoveryItem): Int {
        return recommendationResults()
            .firstOrNull { it.item.id == item.id }
            ?.score
            ?: 0
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

    // Returns loaded Firestore and external discovery items.
    private fun allItems(): List<DiscoveryItem> {
        val internal = firebaseItems + activityItems
        if (externalItems.isEmpty()) return internal
        return StudioDiscoveryUtils.mergeInternalAndExternal(internal, externalItems)
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
