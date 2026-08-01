package com.ana.theflow.data.recommendation

enum class RecommendationSignalType(val wireName: String, val baseWeight: Double) {
    IMPRESSION("impression", RecommendationSignalWeights.IMPRESSION),
    FAST_SKIP("fast_skip", RecommendationSignalWeights.FAST_SKIP),
    SHORT_VIEW("short_view", RecommendationSignalWeights.SHORT_VIEW),
    MEANINGFUL_VIEW("meaningful_view", RecommendationSignalWeights.MEANINGFUL_VIEW),
    NEAR_COMPLETE_VIEW("near_complete_view", RecommendationSignalWeights.NEAR_COMPLETE_VIEW),
    OPEN_POST("open_post", RecommendationSignalWeights.OPEN_POST),
    VIEW_PROFILE("view_profile", RecommendationSignalWeights.VIEW_PROFILE),
    LIKE("like", RecommendationSignalWeights.LIKE),
    COMMENT("comment", RecommendationSignalWeights.COMMENT),
    SAVE("save", RecommendationSignalWeights.SAVE),
    SHARE("share", RecommendationSignalWeights.SHARE),
    OPEN_EVENT("open_event", RecommendationSignalWeights.OPEN_EVENT),
    REGISTER_EVENT("register_event", RecommendationSignalWeights.REGISTER_EVENT),
    START_NAVIGATION("start_navigation", RecommendationSignalWeights.START_NAVIGATION),
    HIDE("hide", RecommendationSignalWeights.HIDE),
    NOT_INTERESTED("not_interested", RecommendationSignalWeights.NOT_INTERESTED),
    UNFOLLOW("unfollow", RecommendationSignalWeights.UNFOLLOW),
    CANCEL_REGISTRATION("cancel_registration", RecommendationSignalWeights.CANCEL_REGISTRATION),
    FOLLOW("follow", RecommendationSignalWeights.FOLLOW),
    SEARCH("search", RecommendationSignalWeights.SEARCH);

    companion object {
        fun fromWireName(value: String): RecommendationSignalType {
            return values().firstOrNull { it.wireName == value } ?: when (value) {
                "view_post" -> SHORT_VIEW
                "like_post" -> LIKE
                "comment_post" -> COMMENT
                "share_post" -> SHARE
                "share_item" -> SHARE
                "save_item" -> SAVE
                "open_discovery_item" -> OPEN_EVENT
                "follow_user" -> FOLLOW
                "unfollow_user" -> UNFOLLOW
                else -> IMPRESSION
            }
        }
    }
}

data class RecommendationProfileUpdatePlan(
    val increments: Map<String, Double> = emptyMap(),
    val arrayUnions: Map<String, List<String>> = emptyMap(),
    val arrayRemoves: Map<String, List<String>> = emptyMap(),
    val metadataKeys: Set<String> = emptySet(),
    val dedupeKey: String = ""
)

object RecommendationProfileUpdatePlanner {
    fun plan(
        signal: RecommendationSignalType,
        features: RecommendationFeatures,
        interactionStrength: Double = 1.0
    ): RecommendationProfileUpdatePlan {
        val weight = signal.baseWeight * interactionStrength.coerceIn(0.0, 1.0)
        val increments = linkedMapOf<String, Double>()
        val arrayUnions = linkedMapOf<String, List<String>>()
        val arrayRemoves = linkedMapOf<String, List<String>>()
        val metadataKeys = linkedSetOf<String>()

        fun add(path: String, key: String, value: Double) {
            if (key.isBlank() || key == "unknown" || value == 0.0) return
            val fullPath = "$path.$key"
            increments[fullPath] = (increments[fullPath] ?: 0.0) + RecommendationScoreMath.capped(value)
            metadataKeys.add("${path}_$key")
        }

        when (signal) {
            RecommendationSignalType.VIEW_PROFILE -> {
                add("creatorScores", features.creatorId, weight * RecommendationSignalWeights.OPEN_PROFILE_CREATOR_FACTOR)
                add("creatorTypeScores", features.creatorTypeId, weight * RecommendationSignalWeights.OPEN_PROFILE_CREATOR_TYPE_FACTOR)
                features.styleIds.forEach { add("styleScores", it, weight * RecommendationSignalWeights.OPEN_PROFILE_STYLE_FACTOR) }
                add("locationScores", features.locationId, weight * RecommendationSignalWeights.OPEN_PROFILE_LOCATION_FACTOR)
            }

            RecommendationSignalType.REGISTER_EVENT,
            RecommendationSignalType.START_NAVIGATION -> {
                features.styleIds.forEach { add("styleScores", it, weight * RecommendationSignalWeights.REGISTRATION_STYLE_FACTOR) }
                add("locationScores", features.locationId, weight * RecommendationSignalWeights.REGISTRATION_LOCATION_FACTOR)
                add("teacherScores", features.teacherId, weight * RecommendationSignalWeights.REGISTRATION_TEACHER_FACTOR)
                add("studioScores", features.studioId, weight * RecommendationSignalWeights.REGISTRATION_STUDIO_FACTOR)
                add("levelScores", features.levelId, weight * RecommendationSignalWeights.REGISTRATION_LEVEL_FACTOR)
                add("contentTypeScores", features.contentTypeId, weight * RecommendationSignalWeights.REGISTRATION_CONTENT_TYPE_FACTOR)
                add("creatorScores", features.creatorId, weight * RecommendationSignalWeights.CREATOR_FACTOR)
                if (signal == RecommendationSignalType.REGISTER_EVENT) arrayUnions["registeredEventIds"] = listOf(features.itemId).filter { it.isNotBlank() }
            }

            RecommendationSignalType.HIDE,
            RecommendationSignalType.NOT_INTERESTED -> {
                add("creatorScores", features.creatorId, weight)
                add("contentTypeScores", features.contentTypeId, weight * 0.4)
                arrayUnions["hiddenItemIds"] = listOf(features.itemId).filter { it.isNotBlank() }
            }

            RecommendationSignalType.UNFOLLOW -> {
                add("creatorScores", features.creatorId, weight)
                add("creatorTypeScores", features.creatorTypeId, weight * 0.6)
            }

            RecommendationSignalType.CANCEL_REGISTRATION -> {
                features.styleIds.forEach { add("styleScores", it, weight * 0.25) }
                add("locationScores", features.locationId, weight * 0.4)
                add("contentTypeScores", features.contentTypeId, weight * 0.5)
                arrayRemoves["registeredEventIds"] = listOf(features.itemId).filter { it.isNotBlank() }
            }

            else -> {
                features.styleIds.forEach { add("styleScores", it, weight * RecommendationSignalWeights.STYLE_FACTOR) }
                add("creatorScores", features.creatorId, weight * RecommendationSignalWeights.CREATOR_FACTOR)
                add("teacherScores", features.teacherId, weight * RecommendationSignalWeights.TEACHER_FACTOR)
                add("studioScores", features.studioId, weight * RecommendationSignalWeights.STUDIO_FACTOR)
                add("contentTypeScores", features.contentTypeId, weight * RecommendationSignalWeights.CONTENT_TYPE_FACTOR)
                add("targetTypeScores", features.contentTypeId, weight * RecommendationSignalWeights.CONTENT_TYPE_FACTOR)
                add("mediaTypeScores", features.mediaTypeId, weight * RecommendationSignalWeights.MEDIA_TYPE_FACTOR)
                add("locationScores", features.locationId, weight * RecommendationSignalWeights.LOCATION_FACTOR)
                add("levelScores", features.levelId, weight * RecommendationSignalWeights.LEVEL_FACTOR)
                add("creatorTypeScores", features.creatorTypeId, weight * RecommendationSignalWeights.CREATOR_TYPE_FACTOR)
            }
        }

        if (signal.baseWeight > 0.0) arrayUnions["interactedItemIds"] = listOf(features.itemId).filter { it.isNotBlank() }
        if (signal == RecommendationSignalType.IMPRESSION || signal == RecommendationSignalType.FAST_SKIP) {
            arrayUnions["seenItemIds"] = listOf(features.itemId).filter { it.isNotBlank() }
        }

        return RecommendationProfileUpdatePlan(
            increments = increments,
            arrayUnions = arrayUnions,
            arrayRemoves = arrayRemoves,
            metadataKeys = metadataKeys,
            dedupeKey = dedupeKey(signal, features)
        )
    }

    fun shouldDedupe(signal: RecommendationSignalType): Boolean {
        return signal in setOf(
            RecommendationSignalType.LIKE,
            RecommendationSignalType.SAVE,
            RecommendationSignalType.REGISTER_EVENT,
            RecommendationSignalType.CANCEL_REGISTRATION,
            RecommendationSignalType.HIDE,
            RecommendationSignalType.NOT_INTERESTED,
            RecommendationSignalType.FOLLOW,
            RecommendationSignalType.UNFOLLOW
        )
    }

    private fun dedupeKey(signal: RecommendationSignalType, features: RecommendationFeatures): String {
        return RecommendationNormalizer.id("${signal.wireName}_${features.itemId}")
    }
}
