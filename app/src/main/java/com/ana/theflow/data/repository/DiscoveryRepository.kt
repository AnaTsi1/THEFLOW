package com.ana.theflow.data.repository

import com.ana.theflow.data.model.discovery.DiscoveryItem
import com.ana.theflow.utilities.Constants
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

object DiscoveryRepository {

    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()
    private var firebaseItems: List<DiscoveryItem> = emptyList()

    val seedItems = listOf(
        DiscoveryItem("1", "Hip Hop Foundations", "Beat Room", "Noa Levi", "Hip Hop", "Beginner", "Tel Aviv", "Today 18:00", "Class", 32.0718, 34.7792),
        DiscoveryItem("2", "Heels After Dark", "Studio Luna", "Maya Cohen", "Heels", "Intermediate", "Tel Aviv", "Today 20:30", "Class", 32.0645, 34.7710),
        DiscoveryItem("3", "Salsa Social Night", "Latin House", "Carlos M.", "Salsa", "Beginner", "Ramat Gan", "Fri 21:00", "Event", 32.0837, 34.8142),
        DiscoveryItem("4", "Contemporary Flow", "Move Hub", "Dana Shalev", "Contemporary", "Advanced", "Herzliya", "Wed 19:30", "Class", 32.1663, 34.8433),
        DiscoveryItem("5", "Afro Fusion Lab", "Studio Luna", "Ari Ben", "Afro", "Intermediate", "Tel Aviv", "Thu 20:00", "Workshop", 32.0645, 34.7710),
        DiscoveryItem("6", "Adult Ballet Basics", "North Stage", "Lior Dan", "Ballet", "Beginner", "Haifa", "Sun 17:00", "Class", 32.7940, 34.9896)
    )

    var preferredStyles: MutableSet<String> = mutableSetOf("Hip Hop", "Heels")
    var preferredLevel: String = "Intermediate"
    var preferredLocation: String = "Tel Aviv"

    private val styleScores = mutableMapOf<String, Int>()
    private val studioScores = mutableMapOf<String, Int>()
    private val teacherScores = mutableMapOf<String, Int>()
    private val savedItemIds = mutableSetOf<String>()
    private var lastReason = "Based on your dance preferences"

    fun hydratePreferences(styles: List<String>, level: String, location: String) {
        if (styles.isNotEmpty()) preferredStyles = styles.toMutableSet()
        if (level.isNotBlank()) preferredLevel = level
        if (location.isNotBlank()) preferredLocation = location
        preferredStyles.forEach { styleScores[it] = (styleScores[it] ?: 0) + 3 }
        lastReason = "Based on your dance profile"
    }

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

    fun trackOpen(item: DiscoveryItem) {
        styleScores[item.style] = (styleScores[item.style] ?: 0) + 2
        studioScores[item.studio] = (studioScores[item.studio] ?: 0) + 2
        teacherScores[item.teacher] = (teacherScores[item.teacher] ?: 0) + 1
        lastReason = "Because you viewed ${item.style} classes"
    }

    fun trackSave(item: DiscoveryItem) {
        savedItemIds.add(item.id)
        styleScores[item.style] = (styleScores[item.style] ?: 0) + 4
        studioScores[item.studio] = (studioScores[item.studio] ?: 0) + 4
        lastReason = "Because you saved ${item.studio}"
    }

    fun isSaved(item: DiscoveryItem): Boolean = savedItemIds.contains(item.id)

    fun recommendedItems(): List<DiscoveryItem> {
        return allItems().sortedByDescending { item -> scoreFor(item) }
    }

    fun popularNearYou(): List<DiscoveryItem> {
        return allItems()
            .filter { it.location.equals(preferredLocation, ignoreCase = true) }
            .ifEmpty { allItems().take(3) }
    }

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

    fun itemById(id: String): DiscoveryItem? {
        return allItems().firstOrNull { it.id == id }
    }

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
                        longitude = document.getDouble("longitude")
                    )
                }
                onSuccess(firebaseItems)
            }
            .addOnFailureListener { error ->
                onFailure(error.message ?: "Failed to load studios")
            }
    }

    fun explanationFor(item: DiscoveryItem): String {
        return when {
            (styleScores[item.style] ?: 0) > 0 -> "Because you viewed ${item.style} classes"
            (studioScores[item.studio] ?: 0) > 0 -> "Because you saved ${item.studio}"
            item.location == preferredLocation -> "Popular near $preferredLocation"
            else -> lastReason
        }
    }

    fun behaviorSummary(): String {
        val topStyle = styleScores.maxByOrNull { it.value }?.key ?: preferredStyles.firstOrNull() ?: "Not set"
        val topStudio = studioScores.maxByOrNull { it.value }?.key ?: "No studio yet"
        return "Top style: $topStyle\nTop studio: $topStudio\nLocation: $preferredLocation"
    }

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

    private fun scoreFor(item: DiscoveryItem): Int {
        var score = 0
        if (preferredStyles.contains(item.style)) score += 4
        if (item.level == preferredLevel) score += 2
        if (item.location == preferredLocation) score += 3
        score += styleScores[item.style] ?: 0
        score += studioScores[item.studio] ?: 0
        score += teacherScores[item.teacher] ?: 0
        if (savedItemIds.contains(item.id)) score += 5
        return score
    }

    private fun DiscoveryItem.matches(query: String, value: String): Boolean {
        return query.isBlank() || value.contains(query, ignoreCase = true)
    }

    private fun allItems(): List<DiscoveryItem> {
        return firebaseItems.ifEmpty { seedItems }
    }

    private fun com.google.firebase.firestore.DocumentSnapshot.firstNonBlankString(
        vararg fields: String
    ): String {
        return fields.firstNotNullOfOrNull { field ->
            getString(field)?.takeIf { it.isNotBlank() }
        }.orEmpty()
    }
}
