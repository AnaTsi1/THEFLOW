package com.ana.theflow.data.recommendation

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
        fun empty(userId: String = "") = RecommendationProfile(userId = userId)
    }
}

data class RecommendationScoreMetadata(
    val score: Double = 0.0,
    val lastUpdatedMillis: Long = 0L,
    val interactionCount: Int = 0,
    val confidence: Double = 0.0,
    val positiveCount: Int = 0,
    val negativeCount: Int = 0
)

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

enum class RecommendationSurface {
    FOR_YOU,
    DISCOVER,
    SEARCH,
    MAP,
    FOLLOWING
}

enum class ResolvedLocationSource {
    NONE,
    MANUAL_SELECTION,
    MAP_CAMERA,
    DEVICE_LOCATION,
    PREFERRED_RECOMMENDATION_AREA,
    PROFILE_LOCATION,
    FALLBACK_COUNTRY
}

data class ResolvedLocation(
    val source: ResolvedLocationSource,
    val cityId: String = "",
    val displayName: String = "",
    val point: GeoPoint? = null,
    val hardFilter: Boolean = false,
    val useAsGooglePlacesBias: Boolean = false
)

data class RecommendationScoreComponent(
    val label: String,
    val score: Double
)

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

enum class RecommendationSelectionMode {
    EXPLOIT,
    EXPLORE
}
