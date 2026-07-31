package com.ana.theflow.data.recommendation

import com.ana.theflow.data.model.discovery.DiscoveryItem
import com.ana.theflow.data.model.post.Post

interface RecommendationRankingStrategy<T> {
    val name: String
    fun rank(items: List<T>, context: RecommendationContext): List<T>
}

object DiscoverRankingStrategy : RecommendationRankingStrategy<DiscoveryItem> {
    override val name = "DiscoverRankingStrategy"

    override fun rank(items: List<DiscoveryItem>, context: RecommendationContext): List<DiscoveryItem> {
        val resolved = LocationSourceResolver.resolve(context)
        val scored = items
            .filter { item -> hardFiltersPass(item, context, resolved) }
            .map { item -> item to score(item, context, resolved) }
            .sortedByDescending { it.second.finalScore }
        return RecommendationDiversity.rerankDiscovery(scored).map { it.first }
    }

    fun score(
        item: DiscoveryItem,
        context: RecommendationContext,
        resolved: ResolvedLocation = LocationSourceResolver.resolve(context)
    ): RecommendationScoreExplanation {
        val profile = context.recommendationProfile
        val features = RecommendationFeatureExtractor.fromDiscoveryItem(item)
        val components = mutableListOf<RecommendationScoreComponent>()
        val penalties = mutableListOf<String>()
        val filters = mutableListOf<String>()

        fun add(label: String, value: Double) {
            if (value != 0.0) components.add(RecommendationScoreComponent(label, value))
        }
        add("Explicit style preference", features.styleIds.maxOfOrNull { if (profile.danceStyles.any { style -> RecommendationNormalizer.styleId(style) == it }) 12.0 else 0.0 } ?: 0.0)
        add("Learned style", features.styleIds.sumOf { profile.styleScores[it] ?: 0.0 } * 1.2)
        add("Learned location", (profile.locationScores[features.locationId] ?: 0.0) * 1.4)
        add("Teacher behavior", (profile.teacherScores[features.teacherId] ?: 0.0) * 1.1)
        add("Studio behavior", (profile.studioScores[features.studioId] ?: 0.0) * 1.2)
        add("Content type", (profile.contentTypeScores[features.contentTypeId] ?: profile.targetTypeScores[features.contentTypeId] ?: 0.0) * 0.8)
        add("Level match", levelScore(features.levelId, profile) * 3.0)
        if (resolved.cityId.isNotBlank() && features.locationId == resolved.cityId) add("Resolved location", if (context.surface == RecommendationSurface.DISCOVER) 10.0 else 4.0)
        add("Freshness", freshnessForDiscovery(features))
        add("Popularity", RecommendationScoreMath.popularity(features.popularitySignals))

        if (item.id in profile.seenItemIds) {
            add("Seen item", -3.0)
            penalties.add("Already shown")
        }
        if (item.id in profile.savedItemIds) add("Saved item", -1.5)
        if (item.id in profile.registeredEventIds && features.contentTypeId in eventTypeIds) {
            add("Already registered", -10_000.0)
            penalties.add("Already registered")
        }

        return RecommendationScoreExplanation(
            itemId = item.id,
            itemType = features.itemType,
            finalScore = components.sumOf { it.score },
            source = item.source,
            rankingStrategy = name,
            resolvedLocationSource = resolved.source,
            surface = context.surface,
            appliedFilters = filters + if (resolved.hardFilter && resolved.displayName.isNotBlank()) listOf("Location: ${resolved.displayName}") else emptyList(),
            negativePenalties = penalties,
            wasSeen = item.id in profile.seenItemIds,
            components = components,
            reasons = components.map { "${it.label}: ${it.score.signed()}" } + penalties
        )
    }

    private fun hardFiltersPass(item: DiscoveryItem, context: RecommendationContext, resolved: ResolvedLocation): Boolean {
        val profile = context.recommendationProfile
        if (item.id in profile.hiddenItemIds) return false
        if (item.id in profile.registeredEventIds && RecommendationNormalizer.contentTypeId(item.displayType.ifBlank { item.type }) in eventTypeIds) return false
        val features = RecommendationFeatureExtractor.fromDiscoveryItem(item)
        if (features.eventStartMillis > 0L && RecommendationScoreMath.eventFreshness(features.eventStartMillis) <= -9_000.0) return false
        if (resolved.hardFilter && resolved.cityId.isNotBlank() && features.locationId != resolved.cityId && !RecommendationNormalizer.id(item.address).contains(resolved.cityId)) return false
        context.selectedFilters["style"]?.takeIf { it.isNotBlank() }?.let { filter ->
            if (features.styleIds.none { it == RecommendationNormalizer.styleId(filter) }) return false
        }
        context.selectedFilters["level"]?.takeIf { it.isNotBlank() }?.let { filter ->
            if (features.levelId != RecommendationNormalizer.levelId(filter) && features.levelId != "open_level") return false
        }
        return true
    }

    private fun freshnessForDiscovery(features: RecommendationFeatures): Double {
        return if (features.contentTypeId in eventTypeIds) {
            RecommendationScoreMath.eventFreshness(features.eventStartMillis)
        } else {
            RecommendationScoreMath.freshness(features.createdAtMillis)
        }
    }
}

object SearchRankingStrategy : RecommendationRankingStrategy<DiscoveryItem> {
    override val name = "SearchRankingStrategy"
    override fun rank(items: List<DiscoveryItem>, context: RecommendationContext): List<DiscoveryItem> {
        return DiscoverRankingStrategy.rank(items, context.copy(surface = RecommendationSurface.SEARCH))
    }
}

object MapRankingStrategy : RecommendationRankingStrategy<DiscoveryItem> {
    override val name = "MapRankingStrategy"
    override fun rank(items: List<DiscoveryItem>, context: RecommendationContext): List<DiscoveryItem> {
        return SearchRankingStrategy.rank(items, context.copy(surface = RecommendationSurface.MAP))
            .filter { it.latitude != null && it.longitude != null }
    }
}

object ForYouRankingStrategy : RecommendationRankingStrategy<Post> {
    override val name = "ForYouRankingStrategy"

    override fun rank(items: List<Post>, context: RecommendationContext): List<Post> {
        val scored = items
            .filter { it.postId !in context.recommendationProfile.hiddenItemIds }
            .map { post -> post to score(post, context) }
            .sortedByDescending { it.second.finalScore }
        return RecommendationDiversity.rerankPosts(scored, context).map { it.first }
    }

    fun score(post: Post, context: RecommendationContext): RecommendationScoreExplanation {
        val profile = context.recommendationProfile
        val features = RecommendationFeatureExtractor.fromPost(post)
        val components = mutableListOf<RecommendationScoreComponent>()
        val penalties = mutableListOf<String>()
        fun add(label: String, value: Double) {
            if (value != 0.0) components.add(RecommendationScoreComponent(label, value))
        }

        add("Explicit style preference", features.styleIds.maxOfOrNull { if (profile.danceStyles.any { style -> RecommendationNormalizer.styleId(style) == it }) 24.0 else 0.0 } ?: 0.0)
        add("Learned style", features.styleIds.sumOf { profile.styleScores[it] ?: 0.0 } * 1.6)
        add("Creator behavior", (profile.creatorScores[features.creatorId] ?: 0.0) * 1.4)
        add("Creator type", (profile.creatorTypeScores[features.creatorTypeId] ?: profile.targetTypeScores[features.creatorTypeId] ?: 0.0) * 0.8)
        add("Content type", (profile.contentTypeScores[features.contentTypeId] ?: 0.0) * 0.9)
        add("Media type", (profile.mediaTypeScores[features.mediaTypeId] ?: 0.0) * 0.7)
        add("Level match", levelScore(features.levelId, profile) * 2.0)
        add("Freshness", RecommendationScoreMath.freshness(features.createdAtMillis))
        add("Popularity", RecommendationScoreMath.popularity(features.popularitySignals))
        val preferred = RecommendationFeatureExtractor.normalizeLocation(context.preferredRecommendationArea)
        if (preferred.isNotBlank() && features.locationId == preferred) add("Preferred area", 4.0)
        if (post.postId in profile.seenItemIds) {
            add("Shown before", -5.0)
            penalties.add("Already shown")
        }
        if (post.postId in profile.savedItemIds) add("Saved before", -1.0)

        val enoughHistory = profile.scoreMetadata.values.sumOf { it.interactionCount } >= 10
        val explore = isExploreCandidate(features, profile)
        val mode = if (explore) RecommendationSelectionMode.EXPLORE else RecommendationSelectionMode.EXPLOIT
        if (explore) add("Explore candidate", if (enoughHistory) 2.0 else 4.0)

        return RecommendationScoreExplanation(
            itemId = post.postId,
            itemType = features.itemType,
            finalScore = components.sumOf { it.score },
            source = "posts",
            rankingStrategy = name,
            resolvedLocationSource = LocationSourceResolver.resolve(context).source,
            surface = context.surface,
            exploreOrExploit = mode,
            negativePenalties = penalties,
            wasSeen = post.postId in profile.seenItemIds,
            components = components,
            reasons = components.map { "${it.label}: ${it.score.signed()}" } + penalties
        )
    }

    private fun isExploreCandidate(features: RecommendationFeatures, profile: RecommendationProfile): Boolean {
        val hasKnownStyle = features.styleIds.any { (profile.styleScores[it] ?: 0.0) > 0.0 || profile.danceStyles.any { style -> RecommendationNormalizer.styleId(style) == it } }
        val knownCreator = (profile.creatorScores[features.creatorId] ?: 0.0) > 0.0
        val knownContentType = (profile.contentTypeScores[features.contentTypeId] ?: 0.0) > 0.0
        return !hasKnownStyle || (!knownCreator && knownContentType)
    }
}

object FollowingRankingStrategy : RecommendationRankingStrategy<Post> {
    override val name = "FollowingRankingStrategy"
    override fun rank(items: List<Post>, context: RecommendationContext): List<Post> {
        return items
            .filter { context.followingIds.isEmpty() || it.authorId in context.followingIds }
            .filter { it.postId !in context.recommendationProfile.hiddenItemIds }
            .sortedByDescending { it.createdAt?.seconds ?: 0L }
    }
}

private object RecommendationDiversity {
    fun rerankPosts(scored: List<Pair<Post, RecommendationScoreExplanation>>, context: RecommendationContext): List<Pair<Post, RecommendationScoreExplanation>> {
        val targetExploreRatio = if (context.recommendationProfile.scoreMetadata.values.sumOf { it.interactionCount } >= 10) 0.2 else 0.3
        return rerank(
            scored = scored,
            key = { it.first.postId },
            creator = { it.first.authorId },
            style = { RecommendationFeatureExtractor.fromPost(it.first).styleIds.firstOrNull().orEmpty() },
            category = { "" },
            isExplore = { it.second.exploreOrExploit == RecommendationSelectionMode.EXPLORE },
            targetExploreRatio = targetExploreRatio
        )
    }

    fun rerankDiscovery(scored: List<Pair<DiscoveryItem, RecommendationScoreExplanation>>): List<Pair<DiscoveryItem, RecommendationScoreExplanation>> {
        return rerank(
            scored = scored,
            key = { if (it.first.googlePlaceId.isNotBlank()) "google:${it.first.googlePlaceId}" else it.first.id },
            creator = { it.first.ownerUid.ifBlank { it.first.studio } },
            style = { RecommendationFeatureExtractor.fromDiscoveryItem(it.first).styleIds.firstOrNull().orEmpty() },
            category = { RecommendationFeatureExtractor.fromDiscoveryItem(it.first).contentTypeId },
            isExplore = { false },
            targetExploreRatio = 0.0
        )
    }

    private fun <T> rerank(
        scored: List<Pair<T, RecommendationScoreExplanation>>,
        key: (Pair<T, RecommendationScoreExplanation>) -> String,
        creator: (Pair<T, RecommendationScoreExplanation>) -> String,
        style: (Pair<T, RecommendationScoreExplanation>) -> String,
        category: (Pair<T, RecommendationScoreExplanation>) -> String,
        isExplore: (Pair<T, RecommendationScoreExplanation>) -> Boolean,
        targetExploreRatio: Double
    ): List<Pair<T, RecommendationScoreExplanation>> {
        val remaining = scored.distinctBy(key).toMutableList()
        val output = mutableListOf<Pair<T, RecommendationScoreExplanation>>()
        while (remaining.isNotEmpty()) {
            val exploreNeeded = targetExploreRatio > 0.0 && output.isNotEmpty() &&
                output.count(isExplore).toDouble() / output.size.toDouble() < targetExploreRatio &&
                remaining.any(isExplore)
            val index = remaining.indexOfFirst { candidate ->
                val creatorCount = output.takeLast(10).count { creator(it) == creator(candidate) && creator(candidate).isNotBlank() }
                val styleWindow = output.takeLast(3)
                val categoryWindow = output.takeLast(2)
                val sameStyleRun = styleWindow.size >= 3 && styleWindow.all { style(it).isNotBlank() && style(it) == style(candidate) }
                val sameCategoryRun = categoryWindow.size >= 2 && categoryWindow.all { category(it).isNotBlank() && category(it) == category(candidate) }
                creatorCount < 2 && !sameStyleRun && !sameCategoryRun && (!exploreNeeded || isExplore(candidate))
            }.takeIf { it >= 0 } ?: 0
            output.add(remaining.removeAt(index))
        }
        return output
    }
}

private fun levelScore(levelId: String, profile: RecommendationProfile): Double {
    if (levelId.isBlank() || levelId == "unknown") return 0.0
    val explicit = if (profile.danceLevel.isNotBlank() && RecommendationNormalizer.levelId(profile.danceLevel) == levelId) 1.0 else 0.0
    return explicit + (profile.levelScores[levelId] ?: 0.0)
}

private val eventTypeIds = setOf("event", "class", "workshop", "dance_activity")

private fun Double.signed(): String = if (this > 0) "+${"%.1f".format(this)}" else "%.1f".format(this)
