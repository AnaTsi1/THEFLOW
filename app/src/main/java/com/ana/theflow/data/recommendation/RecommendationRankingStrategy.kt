// The real ranking logic for every screen - Discover, Search, the map, Home "For You", and
// Following. Scores items against a user's profile, filters out anything that shouldn't show up
// at all, and re-ranks for variety. The admin insights screen calls these same functions directly
// instead of copying the logic, so what an admin sees there is exactly what a real user would get.
package com.ana.theflow.data.recommendation

import com.ana.theflow.data.model.discovery.DiscoveryItem
import com.ana.theflow.data.model.post.Post

// A strategy that orders a list of items or posts for one screen, using a user's profile and context.
interface RecommendationRankingStrategy<T> {
    val name: String

    // Returns the list re-ordered (and possibly filtered) for this context.
    fun rank(items: List<T>, context: RecommendationContext): List<T>
}

// Ranks Discover/Search/map results. This feed is mostly about location and skill level, with
// style as a secondary factor.
object DiscoverRankingStrategy : RecommendationRankingStrategy<DiscoveryItem> {
    override val name = "DiscoverRankingStrategy"

    // Drops anything that fails a hard rule (already registered, hidden, wrong city/style/level
    // filter) and sorts what's left by score.
    override fun rank(items: List<DiscoveryItem>, context: RecommendationContext): List<DiscoveryItem> {
        val resolved = LocationSourceResolver.resolve(context)
        val scored = items
            .filter { item -> hardFiltersPass(item, context, resolved) }
            .map { item -> item to score(item, context, resolved) }
            .sortedByDescending { it.second.finalScore }
        return RecommendationDiversity.rerankDiscovery(scored).map { it.first }
    }

    // Scores one item against a user's profile for Discover/Search/map. This is the opposite of
    // Home's feed (see ForYouRankingStrategy.score below) - Home is about content you personally
    // like no matter where it's from, while this one cares most about what's actually near you and
    // at your level right now. Distance uses a real tiered scale instead of a flat "same city"
    // bonus, so something far away can't beat a nearby result just by matching on everything else.
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
        val isDiscover = context.surface == RecommendationSurface.DISCOVER

        fun add(label: String, value: Double) {
            if (value != 0.0) components.add(RecommendationScoreComponent(label, value))
        }

        val styleExplicitWeight = if (isDiscover) 5.0 else 12.0
        val styleLearnedFactor = if (isDiscover) 0.5 else 1.2
        val levelWeight = if (isDiscover) 6.0 else 3.0

        add("Explicit style preference", features.styleIds.maxOfOrNull { if (profile.danceStyles.any { style -> RecommendationNormalizer.styleId(style) == it }) styleExplicitWeight else 0.0 } ?: 0.0)
        add("Learned style", features.styleIds.sumOf { profile.styleScores[it] ?: 0.0 } * styleLearnedFactor)
        add("Learned location", (profile.locationScores[features.locationId] ?: 0.0) * (if (isDiscover) 1.8 else 1.4))
        add("Teacher behavior", (profile.teacherScores[features.teacherId] ?: 0.0) * 1.1)
        add("Studio behavior", (profile.studioScores[features.studioId] ?: 0.0) * 1.2)
        add("Content type", (profile.contentTypeScores[features.contentTypeId] ?: profile.targetTypeScores[features.contentTypeId] ?: 0.0) * 0.8)
        add("Level match", levelScore(features.levelId, profile) * levelWeight)

        val proximityScore = if (!isDiscover) {
            if (resolved.cityId.isNotBlank() && features.locationId == resolved.cityId) 4.0 else 0.0
        } else {
            val distanceMeters = realDistanceMeters(item, resolved)
            when {
                distanceMeters != null -> RecommendationScoreMath.proximity(distanceMeters)
                resolved.cityId.isNotBlank() && features.locationId == resolved.cityId -> 14.0
                else -> 0.0
            }
        }
        add("Proximity", proximityScore)

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

    // Real distance between where the user is and where the item is, when we know both. Returns
    // null (not zero) if we can't actually compute it, so the caller can fall back to a rougher
    // same-city bonus instead of quietly treating an unknown distance as "right here."
    private fun realDistanceMeters(item: DiscoveryItem, resolved: ResolvedLocation): Double? {
        val point = resolved.point ?: return null
        val lat = item.latitude ?: return null
        val lng = item.longitude ?: return null
        return runCatching {
            val result = FloatArray(1)
            android.location.Location.distanceBetween(point.latitude, point.longitude, lat, lng, result)
            result[0].toDouble()
        }.getOrNull()
    }

    // Hard rules that remove an item from the results completely, rather than just lowering its score.
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

    // For events, freshness means "how soon does it start." For everything else, it means "how new is it."
    private fun freshnessForDiscovery(features: RecommendationFeatures): Double {
        return if (features.contentTypeId in eventTypeIds) {
            RecommendationScoreMath.eventFreshness(features.eventStartMillis)
        } else {
            RecommendationScoreMath.freshness(features.createdAtMillis)
        }
    }
}

// Ranks Search results the same way Discover does, just forced onto the Search surface so it
// picks up Search's own location rules.
object SearchRankingStrategy : RecommendationRankingStrategy<DiscoveryItem> {
    override val name = "SearchRankingStrategy"

    override fun rank(items: List<DiscoveryItem>, context: RecommendationContext): List<DiscoveryItem> {
        return DiscoverRankingStrategy.rank(items, context.copy(surface = RecommendationSurface.SEARCH))
    }
}

// Ranks map results the same way Search does, then drops anything without coordinates since we
// can't put a pin down for it.
object MapRankingStrategy : RecommendationRankingStrategy<DiscoveryItem> {
    override val name = "MapRankingStrategy"

    override fun rank(items: List<DiscoveryItem>, context: RecommendationContext): List<DiscoveryItem> {
        return SearchRankingStrategy.rank(items, context.copy(surface = RecommendationSurface.MAP))
            .filter { it.latitude != null && it.longitude != null }
    }
}

// Ranks Home's "For You" feed - mostly about style, with location only as a minor tiebreaker.
object ForYouRankingStrategy : RecommendationRankingStrategy<Post> {
    override val name = "ForYouRankingStrategy"

    // Scores everything, drops anything the user hid, sorts by score, then re-ranks for variety
    // so one author or style doesn't take over the whole feed.
    override fun rank(items: List<Post>, context: RecommendationContext): List<Post> {
        val scored = items
            .filter { it.postId !in context.recommendationProfile.hiddenItemIds }
            .map { post -> post to score(post, context) }
            .sortedByDescending { it.second.finalScore }
        return RecommendationDiversity.rerankPosts(scored, context).map { it.first }
    }

    // Scores one post against a user's profile - mostly their declared and learned style
    // preferences, with freshness, popularity, and level as smaller factors on top.
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
        // Location is just a small tiebreaker here, never the deciding factor - this is a feed of
        // people the user chose to follow, so where they happen to be matters a lot less than it
        // does on Discover.
        val preferred = RecommendationFeatureExtractor.normalizeLocation(context.preferredRecommendationArea)
        if (preferred.isNotBlank() && features.locationId == preferred) add("Preferred area", 1.5)
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

    // Decides if this post is worth showing as a "discover something new" pick, even without a
    // strong match to what the user already likes - keeps the feed from becoming an endless loop
    // of the exact same styles and creators.
    private fun isExploreCandidate(features: RecommendationFeatures, profile: RecommendationProfile): Boolean {
        val hasKnownStyle = features.styleIds.any { (profile.styleScores[it] ?: 0.0) > 0.0 || profile.danceStyles.any { style -> RecommendationNormalizer.styleId(style) == it } }
        val knownCreator = (profile.creatorScores[features.creatorId] ?: 0.0) > 0.0
        val knownContentType = (profile.contentTypeScores[features.contentTypeId] ?: 0.0) > 0.0
        return !hasKnownStyle || (!knownCreator && knownContentType)
    }
}

// Ranks the Following feed by plain recency - no personalization needed since it's already just
// the people the user chose to follow.
object FollowingRankingStrategy : RecommendationRankingStrategy<Post> {
    override val name = "FollowingRankingStrategy"

    override fun rank(items: List<Post>, context: RecommendationContext): List<Post> {
        return items
            .filter { context.followingIds.isEmpty() || it.authorId in context.followingIds }
            .filter { it.postId !in context.recommendationProfile.hiddenItemIds }
            .sortedByDescending { it.createdAt?.seconds ?: 0L }
    }
}

// Takes an already-scored, already-sorted list and shuffles it just enough that it doesn't feel
// repetitive - avoids stacking too many posts from the same creator or style back to back, and
// mixes in a few "explore" picks so the feed doesn't loop the same content forever.
private object RecommendationDiversity {
    // Re-orders scored posts for variety, mixing in explore posts up to a target ratio - a ratio
    // that grows once we actually have enough history on this user to trust it.
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

    // Same idea for Discover items, just without the explore-ratio mixing since Discover candidates don't have that label.
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

    // Walks through the already-sorted list and, at each step, grabs the highest-ranked item that
    // doesn't break the variety rules - no more than 2 of the last 10 from one creator, no 3 in a
    // row with the same style, no 2 in a row in the same category. If nothing fits all the rules,
    // it just takes the next-best item anyway rather than getting stuck.
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

// Combines a user's declared level with their learned level score, so matching their stated level
// always counts for something even before we've learned anything from their behavior.
private fun levelScore(levelId: String, profile: RecommendationProfile): Double {
    if (levelId.isBlank() || levelId == "unknown") return 0.0
    val explicit = if (profile.danceLevel.isNotBlank() && RecommendationNormalizer.levelId(profile.danceLevel) == levelId) 1.0 else 0.0
    return explicit + (profile.levelScores[levelId] ?: 0.0)
}

private val eventTypeIds = setOf("event", "class", "workshop", "dance_activity")

// Formats a score with a "+" in front when it's positive, so a score breakdown is easy to read at a glance.
private fun Double.signed(): String = if (this > 0) "+${"%.1f".format(this)}" else "%.1f".format(this)
