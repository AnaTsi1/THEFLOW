// A lightweight fallback ranker for feed posts, used when a user's full recommendation profile
// isn't available yet (e.g. it failed to load) so the feed still shows something reasonable
// instead of an unranked list.
package com.ana.theflow.data.repository

import com.ana.theflow.data.model.discovery.DiscoveryItem
import com.ana.theflow.data.model.post.Post
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// A simplified stand-in for a user's real recommendation profile - only this fallback ranker uses it.
data class PostRecommendationProfile(
    val targetTypeScores: Map<String, Int> = emptyMap()
)

object RecommendationEngine {

    // Sorts posts by a simple engagement score, then spreads things out so the same author or
    // post type doesn't end up clumped together.
    fun rankPosts(
        posts: List<Post>,
        profile: PostRecommendationProfile
    ): List<Post> {
        return diversifyPosts(posts.sortedWith(
            compareByDescending<Post> { post ->
                postRecommendationScore(post, profile)
            }.thenByDescending { post ->
                post.createdAt?.seconds ?: 0L
            }
        ))
    }

    // A basic score from likes/comments, plus a bonus for upcoming dance activities (and a
    // penalty if the activity's already happened), plus a small bump for having media attached.
    private fun postRecommendationScore(post: Post, profile: PostRecommendationProfile): Int {
        var score = profile.targetTypeScores[scoreKey(post.authorType)] ?: 0
        score += post.likesCount.toInt().coerceAtMost(40)
        score += (post.commentsCount * 2).toInt().coerceAtMost(40)
        if (post.postType == POST_TYPE_DANCE_ACTIVITY) {
            score += if (isPastEvent(post)) -80 else 18
            if (post.activityLevel.isNotBlank()) score += 4
            if (post.activityLocation.isNotBlank()) score += 4
        }
        if (post.mediaUrls.isNotEmpty() || post.mediaItems.any { it.url.isNotBlank() }) score += 6
        if (post.originalPostId.isNotBlank()) score -= 4
        return score
    }

    // Reshuffles a sorted list so you don't get two posts in a row from the same author or of the same type.
    private fun diversifyPosts(posts: List<Post>): List<Post> {
        val remaining = posts.toMutableList()
        val result = mutableListOf<Post>()
        while (remaining.isNotEmpty()) {
            val previousAuthor = result.lastOrNull()?.authorId
            val previousType = result.lastOrNull()?.postType
            val index = remaining.indexOfFirst { candidate ->
                candidate.authorId != previousAuthor && candidate.postType != previousType
            }.takeIf { it >= 0 } ?: 0
            result.add(remaining.removeAt(index))
        }
        return result
    }

    // True if this is a dance activity post and its date has already passed.
    private fun isPastEvent(post: Post): Boolean {
        if (post.postType != POST_TYPE_DANCE_ACTIVITY || post.activityDate.isBlank()) return false
        val parsed = listOf("yyyy-MM-dd", "dd/MM/yyyy", "MMM d, yyyy").firstNotNullOfOrNull { pattern ->
            runCatching { SimpleDateFormat(pattern, Locale.US).parse(post.activityDate.trim()) }.getOrNull()
        } ?: return false
        return parsed.before(Date(System.currentTimeMillis() - 24L * 60L * 60L * 1000L))
    }

    // Turns free text into a safe key - just letters, numbers, dashes, and underscores. Falls back to "unknown" if blank.
    fun scoreKey(value: String): String {
        return value.trim()
            .ifBlank { "unknown" }
            .replace(Regex("[^A-Za-z0-9_-]"), "_")
    }

    private const val POST_TYPE_DANCE_ACTIVITY = "dance_activity"
}
