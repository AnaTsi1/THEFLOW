package com.ana.theflow.data.recommendation

import com.ana.theflow.data.model.discovery.DiscoveryItem
import com.ana.theflow.data.model.post.Post
import com.ana.theflow.utilities.CityOptions
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.min

data class RecommendationFeatures(
    val itemId: String = "",
    val itemType: String = "",
    val styleIds: Set<String> = emptySet(),
    val locationId: String = "",
    val teacherId: String = "",
    val studioId: String = "",
    val creatorId: String = "",
    val creatorTypeId: String = "",
    val contentTypeId: String = "",
    val levelId: String = "",
    val mediaTypeId: String = "",
    val createdAtMillis: Long = 0L,
    val eventStartMillis: Long = 0L,
    val popularitySignals: PopularitySignals = PopularitySignals(),
    val hasCoordinates: Boolean = false
)

data class PopularitySignals(
    val likes: Long = 0,
    val comments: Long = 0,
    val saves: Long = 0,
    val registrations: Long = 0,
    val shares: Long = 0
)

object RecommendationFeatureExtractor {
    fun fromPost(post: Post): RecommendationFeatures {
        val styleValues = listOf(
            post.activityType,
            post.activityDescription,
            post.collaborationStyle,
            post.text
        )
        val contentType = post.postType.ifBlank {
            when {
                post.activityType.isNotBlank() -> "event"
                post.collaborationLookingFor.isNotBlank() -> "collaboration"
                else -> "post"
            }
        }
        return RecommendationFeatures(
            itemId = post.postId,
            itemType = contentType,
            styleIds = styleValues.flatMap { extractKnownStyles(it) }.toSet(),
            locationId = normalizeLocation(post.activityLocation.ifBlank { post.collaborationLocation }),
            creatorId = RecommendationNormalizer.id(post.authorId),
            creatorTypeId = RecommendationNormalizer.creatorTypeId(post.authorType),
            contentTypeId = RecommendationNormalizer.contentTypeId(contentType),
            levelId = RecommendationNormalizer.levelId(post.activityLevel),
            mediaTypeId = mediaType(post),
            createdAtMillis = post.createdAt?.toDate()?.time ?: 0L,
            eventStartMillis = parseDateMillis(post.activityDate),
            popularitySignals = PopularitySignals(
                likes = post.likesCount,
                comments = post.commentsCount,
                registrations = post.registrationsCount
            )
        )
    }

    fun fromDiscoveryItem(item: DiscoveryItem): RecommendationFeatures {
        return RecommendationFeatures(
            itemId = item.id,
            itemType = item.displayType.ifBlank { item.type },
            styleIds = setOf(RecommendationNormalizer.styleId(item.style)).filterNot { it == "unknown" }.toSet(),
            locationId = normalizeLocation(item.location.ifBlank { item.address }),
            teacherId = RecommendationNormalizer.id(item.teacher),
            studioId = RecommendationNormalizer.id(item.studio),
            creatorId = RecommendationNormalizer.id(item.ownerUid.ifBlank { item.studio.ifBlank { item.teacher } }),
            creatorTypeId = RecommendationNormalizer.creatorTypeId(item.displayType.ifBlank { item.type }),
            contentTypeId = RecommendationNormalizer.contentTypeId(item.displayType.ifBlank { item.type }),
            levelId = RecommendationNormalizer.levelId(item.level),
            mediaTypeId = if (item.coverImageUrl.isNotBlank()) "photo" else "",
            eventStartMillis = parseDateMillis(item.dateTimeText.ifBlank { item.time }),
            popularitySignals = PopularitySignals(
                registrations = item.ratingCount?.toLong() ?: 0L
            ),
            hasCoordinates = item.latitude != null && item.longitude != null
        )
    }

    fun normalizeLocation(value: String): String {
        return CityOptions.normalizeCityId(value) ?: RecommendationNormalizer.id(value).takeIf { it != "unknown" }.orEmpty()
    }

    private fun mediaType(post: Post): String {
        val explicit = post.mediaType.takeIf { it.isNotBlank() && it != "none" }
        if (explicit != null) return RecommendationNormalizer.contentTypeId(explicit)
        val mediaTypes = post.mediaItems.map { it.mediaType }.filter { it.isNotBlank() }
        if (mediaTypes.any { it.contains("video", ignoreCase = true) }) return "video"
        if (post.mediaUrls.isNotEmpty() || mediaTypes.any { it.contains("photo", ignoreCase = true) }) return "photo"
        return ""
    }

    private fun extractKnownStyles(text: String): List<String> {
        if (text.isBlank()) return emptyList()
        val haystack = text.lowercase()
        return listOf("Hip Hop", "Heels", "Contemporary", "Ballet", "Jazz", "Salsa", "Bachata")
            .filter { haystack.contains(it.lowercase()) }
            .map { RecommendationNormalizer.styleId(it) }
    }

    fun parseDateMillis(value: String): Long {
        val raw = value.trim()
        if (raw.isBlank()) return 0L
        return listOf("yyyy-MM-dd", "dd/MM/yyyy", "MMM d, yyyy", "MMMM d, yyyy").firstNotNullOfOrNull { pattern ->
            runCatching { SimpleDateFormat(pattern, Locale.US).parse(raw)?.time }.getOrNull()
        } ?: 0L
    }
}

object RecommendationScoreMath {
    fun decayed(score: Double, lastUpdatedMillis: Long, nowMillis: Long = System.currentTimeMillis()): Double {
        if (score == 0.0 || lastUpdatedMillis <= 0L) return score
        val ageDays = ((nowMillis - lastUpdatedMillis).coerceAtLeast(0L)).toDouble() / DAY_MS
        val factor = exp(-ageDays / 120.0).coerceIn(0.25, 1.0)
        return score * factor
    }

    fun confidence(interactionCount: Int): Double {
        if (interactionCount <= 0) return 0.0
        return min(1.0, ln(1.0 + interactionCount) / ln(11.0))
    }

    fun capped(score: Double): Double {
        return score.coerceIn(-RecommendationSignalWeights.MAX_DIMENSION_SCORE, RecommendationSignalWeights.MAX_DIMENSION_SCORE)
    }

    fun freshness(createdAtMillis: Long, nowMillis: Long = System.currentTimeMillis()): Double {
        if (createdAtMillis <= 0L) return 0.0
        val ageDays = ((nowMillis - createdAtMillis).coerceAtLeast(0L)).toDouble() / DAY_MS
        return when {
            ageDays <= 2.0 -> 10.0
            ageDays <= 7.0 -> 7.0
            ageDays <= 30.0 -> 4.0
            ageDays <= 120.0 -> 1.5
            else -> 0.0
        }
    }

    fun eventFreshness(eventStartMillis: Long, nowMillis: Long = System.currentTimeMillis()): Double {
        if (eventStartMillis <= 0L) return 0.0
        val daysUntil = (eventStartMillis - nowMillis).toDouble() / DAY_MS
        return when {
            daysUntil < -1.0 -> -10_000.0
            daysUntil <= 1.0 -> 12.0
            daysUntil <= 7.0 -> 10.0
            daysUntil <= 30.0 -> 6.0
            daysUntil <= 120.0 -> 2.0
            else -> 0.5
        }
    }

    fun popularity(signals: PopularitySignals): Double {
        val weighted = signals.likes + signals.comments * 2 + signals.saves * 3 + signals.registrations * 4 + signals.shares * 3
        return if (weighted <= 0) 0.0 else ln(1.0 + weighted.toDouble()) * 2.0
    }

    private const val DAY_MS = 24.0 * 60.0 * 60.0 * 1000.0
}
