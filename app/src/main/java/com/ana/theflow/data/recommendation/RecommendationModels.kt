// The core shapes the recommendation engine passes around - a user's learned profile, the
// context one ranking request runs in, and the explanation attached to each ranked result.
package com.ana.theflow.data.recommendation

// Everything we know about one user for recommendation purposes - both what they told us
// directly (styles, level, city) and what we've learned from watching what they do.
data class RecommendationProfile(
    val userId: String = "",
    val danceStyles: List<String> = emptyList(),
    val danceLevel: String = "",
    val profileLocation: String = "",
    val preferredRecommendationArea: String = "",
    val styleScores: Map<String, Double> = emptyMap(),
    val locationScores: Map<String, Double> = emptyMap(),
    val studioScores: Map<String, Double> = emptyMap(),
    val teacherScores: Map<String, Double> = emptyMap(),
    val creatorScores: Map<String, Double> = emptyMap(),
    val creatorTypeScores: Map<String, Double> = emptyMap(),
    val contentTypeScores: Map<String, Double> = emptyMap(),
    val targetTypeScores: Map<String, Double> = emptyMap(),
    val levelScores: Map<String, Double> = emptyMap(),
    val mediaTypeScores: Map<String, Double> = emptyMap(),
    val scoreMetadata: Map<String, RecommendationScoreMetadata> = emptyMap(),
    val savedItemIds: Set<String> = emptySet(),
    val interactedItemIds: Set<String> = emptySet(),
    val hiddenItemIds: Set<String> = emptySet(),
    val registeredEventIds: Set<String> = emptySet(),
    val seenItemIds: Set<String> = emptySet()
) {
    companion object {
        // A blank profile with nothing learned yet - our safe default before the real one has loaded.
        fun empty(userId: String = "") = RecommendationProfile(userId = userId)
    }
}

// Extra info that rides along with one learned score (for a style, studio, teacher, etc) - when
// it last changed and how often.
data class RecommendationScoreMetadata(
    val score: Double = 0.0,
    val lastUpdatedMillis: Long = 0L,
    val interactionCount: Int = 0,
    val confidence: Double = 0.0,
    val positiveCount: Int = 0,
    val negativeCount: Int = 0
)

// Everything a ranking strategy needs to score items for one request - who's asking, where
// they are, and what filters are active.
data class RecommendationContext(
    val userId: String = "",
    val surface: RecommendationSurface,
    val profileLocation: String = "",
    val preferredRecommendationArea: String = "",
    val currentDeviceLocation: GeoPoint? = null,
    val manualSelectedLocation: String = "",
    val mapCameraLocation: GeoPoint? = null,
    val selectedFilters: Map<String, String> = emptyMap(),
    val danceStyles: List<String> = emptyList(),
    val danceLevel: String = "",
    val followingIds: Set<String> = emptySet(),
    val recommendationProfile: RecommendationProfile = RecommendationProfile.empty(userId)
)

// Which screen is asking for ranked results - Home, Discover, Search, the map, or Following.
// Each one has its own ranking priorities and its own rules for using location.
enum class RecommendationSurface {
    FOR_YOU,
    DISCOVER,
    SEARCH,
    MAP,
    FOLLOWING
}

// Which source LocationSourceResolver actually used to decide "where the user is" for a request.
enum class ResolvedLocationSource {
    NONE,
    MANUAL_SELECTION,
    MAP_CAMERA,
    DEVICE_LOCATION,
    PREFERRED_RECOMMENDATION_AREA,
    PROFILE_LOCATION,
    FALLBACK_COUNTRY
}

// The location LocationSourceResolver settled on for a request, plus how strictly it should be applied.
data class ResolvedLocation(
    val source: ResolvedLocationSource,
    val cityId: String = "",
    val displayName: String = "",
    val point: GeoPoint? = null,
    val hardFilter: Boolean = false,
    val useAsGooglePlacesBias: Boolean = false
)

// One named piece of an item's score - e.g. "Explicit style preference": +24.0.
data class RecommendationScoreComponent(
    val label: String,
    val score: Double
)

// The full breakdown of why one item landed where it did - used for the "why" text and the
// admin insights screen.
data class RecommendationScoreExplanation(
    val itemId: String,
    val itemType: String,
    val finalScore: Double,
    val source: String,
    val rankingStrategy: String,
    val resolvedLocationSource: ResolvedLocationSource,
    val surface: RecommendationSurface? = null,
    val exploreOrExploit: RecommendationSelectionMode = RecommendationSelectionMode.EXPLOIT,
    val appliedFilters: List<String> = emptyList(),
    val negativePenalties: List<String> = emptyList(),
    val diversityAdjustments: List<String> = emptyList(),
    val wasSeen: Boolean = false,
    val components: List<RecommendationScoreComponent> = emptyList(),
    val reasons: List<String> = emptyList()
)

// Whether an item was ranked normally (EXPLOIT) or deliberately surfaced to mix things up (EXPLORE).
enum class RecommendationSelectionMode {
    EXPLOIT,
    EXPLORE
}
