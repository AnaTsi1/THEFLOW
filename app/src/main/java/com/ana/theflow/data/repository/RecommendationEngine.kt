package com.ana.theflow.data.repository

import com.ana.theflow.data.model.discovery.DiscoveryItem
import com.ana.theflow.data.model.post.Post

data class RecommendationResult(
    val item: DiscoveryItem,
    val score: Int,
    val reasons: List<String>,
    val candidateReason: String
)

data class RecommendationCandidate(
    val item: DiscoveryItem,
    val candidateReason: String
)

data class PostRecommendationProfile(
    val targetTypeScores: Map<String, Int> = emptyMap()
)

object RecommendationEngine {

    fun rankPosts(
        posts: List<Post>,
        profile: PostRecommendationProfile
    ): List<Post> {
        return posts.sortedWith(
            compareByDescending<Post> { post ->
                profile.targetTypeScores[scoreKey(post.authorType)] ?: 0
            }.thenByDescending { post ->
                post.createdAt?.seconds ?: 0L
            }
        )
    }

    fun calculate(
        items: List<DiscoveryItem>,
        preferredStyles: Set<String>,
        preferredLevel: String,
        preferredLocation: String,
        savedItemIds: Set<String>,
        styleScores: Map<String, Int>,
        studioScores: Map<String, Int>,
        teacherScores: Map<String, Int>
    ): List<RecommendationResult> {
        return rankCandidates(
            candidates = generateCandidates(
                items = items,
                preferredStyles = preferredStyles,
                preferredLevel = preferredLevel,
                preferredLocation = preferredLocation,
                savedItemIds = savedItemIds,
                styleScores = styleScores,
                studioScores = studioScores,
                teacherScores = teacherScores
            ),
            preferredStyles = preferredStyles,
            preferredLevel = preferredLevel,
            preferredLocation = preferredLocation,
            savedItemIds = savedItemIds,
            styleScores = styleScores,
            studioScores = studioScores,
            teacherScores = teacherScores
        )
    }

    fun generateCandidates(
        items: List<DiscoveryItem>,
        preferredStyles: Set<String>,
        preferredLevel: String,
        preferredLocation: String,
        savedItemIds: Set<String>,
        styleScores: Map<String, Int>,
        studioScores: Map<String, Int>,
        teacherScores: Map<String, Int>
    ): List<RecommendationCandidate> {
        return items.mapNotNull { item ->
            val candidateReason = candidateReasonFor(
                item = item,
                preferredStyles = preferredStyles,
                preferredLevel = preferredLevel,
                preferredLocation = preferredLocation,
                savedItemIds = savedItemIds,
                styleScores = styleScores,
                studioScores = studioScores,
                teacherScores = teacherScores
            ) ?: return@mapNotNull null

            RecommendationCandidate(item = item, candidateReason = candidateReason)
        }
    }

    fun rankCandidates(
        candidates: List<RecommendationCandidate>,
        preferredStyles: Set<String>,
        preferredLevel: String,
        preferredLocation: String,
        savedItemIds: Set<String>,
        styleScores: Map<String, Int>,
        studioScores: Map<String, Int>,
        teacherScores: Map<String, Int>
    ): List<RecommendationResult> {
        return candidates
            .map { candidate ->
                val item = candidate.item
                val reasons = mutableListOf<String>()
                var score = 0

                score += addPreferredStyleScore(item, preferredStyles, reasons)
                score += addPreferredLevelScore(item, preferredLevel, reasons)
                score += addPreferredLocationScore(item, preferredLocation, reasons)
                score += addSavedItemScore(item, savedItemIds, reasons)
                score += addBehaviorScore(
                    label = item.style,
                    score = styleScores[item.style] ?: 0,
                    reason = "Because you viewed or opened ${item.style} classes",
                    reasons = reasons
                )
                score += addBehaviorScore(
                    label = item.studio,
                    score = studioScores[item.studio] ?: 0,
                    reason = "Because you saved or viewed ${item.studio}",
                    reasons = reasons
                )
                score += addBehaviorScore(
                    label = item.teacher,
                    score = teacherScores[item.teacher] ?: 0,
                    reason = "Because you showed interest in ${item.teacher}",
                    reasons = reasons
                )

                RecommendationResult(
                    item = item,
                    score = score,
                    reasons = reasons.ifEmpty { listOf(candidate.candidateReason) },
                    candidateReason = candidate.candidateReason
                )
            }
            .sortedByDescending { it.score }
    }

    private fun candidateReasonFor(
        item: DiscoveryItem,
        preferredStyles: Set<String>,
        preferredLevel: String,
        preferredLocation: String,
        savedItemIds: Set<String>,
        styleScores: Map<String, Int>,
        studioScores: Map<String, Int>,
        teacherScores: Map<String, Int>
    ): String? {
        return when {
            preferredStyles.any { it.equals(item.style, ignoreCase = true) } ->
                "Candidate because it matches your preferred style: ${item.style}"
            preferredLocation.isNotBlank() && item.location.equals(preferredLocation, ignoreCase = true) ->
                "Candidate because it is near $preferredLocation"
            preferredLevel.isNotBlank() && item.level.equals(preferredLevel, ignoreCase = true) ->
                "Candidate because it matches your preferred level: ${item.level}"
            savedItemIds.contains(item.id) ->
                "Candidate because you saved this item"
            (styleScores[item.style] ?: 0) > 0 ->
                "Candidate because of your ${item.style} activity"
            (studioScores[item.studio] ?: 0) > 0 ->
                "Candidate because of your ${item.studio} activity"
            (teacherScores[item.teacher] ?: 0) > 0 ->
                "Candidate because of your interest in ${item.teacher}"
            else -> null
        }
    }

    private fun addPreferredStyleScore(
        item: DiscoveryItem,
        preferredStyles: Set<String>,
        reasons: MutableList<String>
    ): Int {
        if (!preferredStyles.any { it.equals(item.style, ignoreCase = true) }) return 0
        reasons.add("Matches your preferred style: ${item.style}")
        return 4
    }

    private fun addPreferredLevelScore(
        item: DiscoveryItem,
        preferredLevel: String,
        reasons: MutableList<String>
    ): Int {
        if (preferredLevel.isBlank() || !item.level.equals(preferredLevel, ignoreCase = true)) return 0
        reasons.add("Fits your preferred level: ${item.level}")
        return 2
    }

    private fun addPreferredLocationScore(
        item: DiscoveryItem,
        preferredLocation: String,
        reasons: MutableList<String>
    ): Int {
        if (preferredLocation.isBlank() || !item.location.equals(preferredLocation, ignoreCase = true)) return 0
        reasons.add("Popular near $preferredLocation")
        return 3
    }

    private fun addSavedItemScore(
        item: DiscoveryItem,
        savedItemIds: Set<String>,
        reasons: MutableList<String>
    ): Int {
        if (!savedItemIds.contains(item.id)) return 0
        reasons.add("You saved this item")
        return 5
    }

    private fun addBehaviorScore(
        label: String,
        score: Int,
        reason: String,
        reasons: MutableList<String>
    ): Int {
        if (label.isBlank() || score <= 0) return 0
        reasons.add(reason)
        return score
    }

    fun scoreKey(value: String): String {
        return value.trim()
            .ifBlank { "unknown" }
            .replace(Regex("[^A-Za-z0-9_-]"), "_")
    }
}
