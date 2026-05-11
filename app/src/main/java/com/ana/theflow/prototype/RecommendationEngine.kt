package com.ana.theflow.prototype

object RecommendationEngine {

    val allItems = listOf(
        PrototypeItem("1", "Hip Hop Foundations", "Beat Room", "Noa Levi", "Hip Hop", "Beginner", "Tel Aviv", "Today 18:00", "Class"),
        PrototypeItem("2", "Heels After Dark", "Studio Luna", "Maya Cohen", "Heels", "Intermediate", "Tel Aviv", "Today 20:30", "Class"),
        PrototypeItem("3", "Salsa Social Night", "Latin House", "Carlos M.", "Salsa", "Beginner", "Ramat Gan", "Fri 21:00", "Event"),
        PrototypeItem("4", "Contemporary Flow", "Move Hub", "Dana Shalev", "Contemporary", "Advanced", "Herzliya", "Wed 19:30", "Class"),
        PrototypeItem("5", "Afro Fusion Lab", "Studio Luna", "Ari Ben", "Afro", "Intermediate", "Tel Aviv", "Thu 20:00", "Workshop"),
        PrototypeItem("6", "Adult Ballet Basics", "North Stage", "Lior Dan", "Ballet", "Beginner", "Haifa", "Sun 17:00", "Class")
    )

    var preferredStyles: MutableSet<String> = mutableSetOf("Hip Hop", "Heels")
    var preferredLevel: String = "Intermediate"
    var preferredLocation: String = "Tel Aviv"

    private val styleScores = mutableMapOf<String, Int>()
    private val studioScores = mutableMapOf<String, Int>()
    private val teacherScores = mutableMapOf<String, Int>()
    private val savedItemIds = mutableSetOf<String>()
    private var lastReason = "Based on your dance preferences"

    fun savePreferences(styles: Set<String>, level: String, location: String) {
        preferredStyles = styles.toMutableSet()
        preferredLevel = level
        preferredLocation = location
        styles.forEach { styleScores[it] = (styleScores[it] ?: 0) + 3 }
        lastReason = "Based on your onboarding preferences"
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

    fun trackOpen(item: PrototypeItem) {
        styleScores[item.style] = (styleScores[item.style] ?: 0) + 2
        studioScores[item.studio] = (studioScores[item.studio] ?: 0) + 2
        teacherScores[item.teacher] = (teacherScores[item.teacher] ?: 0) + 1
        lastReason = "Because you viewed ${item.style} classes"
    }

    fun trackSave(item: PrototypeItem) {
        savedItemIds.add(item.id)
        styleScores[item.style] = (styleScores[item.style] ?: 0) + 4
        studioScores[item.studio] = (studioScores[item.studio] ?: 0) + 4
        lastReason = "Because you saved ${item.studio}"
    }

    fun isSaved(item: PrototypeItem): Boolean = savedItemIds.contains(item.id)

    fun recommendedItems(): List<PrototypeItem> {
        return allItems.sortedByDescending { item ->
            scoreFor(item)
        }
    }

    fun popularNearYou(): List<PrototypeItem> {
        return allItems
            .filter { it.location.equals(preferredLocation, ignoreCase = true) }
            .ifEmpty { allItems.take(3) }
    }

    fun search(
        style: String,
        level: String,
        location: String,
        teacher: String,
        studio: String,
        time: String
    ): List<PrototypeItem> {
        trackSearch(style, location)

        return allItems.filter { item ->
            item.matches(style, item.style) &&
                item.matches(level, item.level) &&
                item.matches(location, item.location) &&
                item.matches(teacher, item.teacher) &&
                item.matches(studio, item.studio) &&
                item.matches(time, item.time)
        }
    }

    fun itemById(id: String): PrototypeItem? {
        return allItems.firstOrNull { it.id == id }
    }

    fun explanationFor(item: PrototypeItem): String {
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

    // Prototype scoring: simple local weights that simulate a smart recommendation engine.
    private fun scoreFor(item: PrototypeItem): Int {
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

    private fun PrototypeItem.matches(query: String, value: String): Boolean {
        return query.isBlank() || value.contains(query, ignoreCase = true)
    }
}
