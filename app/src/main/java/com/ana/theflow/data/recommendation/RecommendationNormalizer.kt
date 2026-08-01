// Cleans up free-text values (a style name, a level, a city) into one consistent key, so the
// recommendation engine doesn't end up treating "Hip Hop" and "hip-hop" as two different things.
package com.ana.theflow.data.recommendation

object RecommendationNormalizer {
    // Lowercases, strips punctuation, and collapses whitespace into underscores. Falls back to
    // "unknown" if there's nothing usable left.
    fun id(value: String): String {
        return value.trim()
            .lowercase()
            .replace(Regex("[׳’'`´]"), "")
            .replace(Regex("[^\\p{L}\\p{N}]+"), "_")
            .trim('_')
            .ifBlank { "unknown" }
    }

    // Same as id(), but also merges known spelling variants like "hiphop" into "hip_hop".
    fun styleId(value: String): String {
        return when (id(value)) {
            "hip_hop", "hiphop" -> "hip_hop"
            else -> id(value)
        }
    }

    // Same as id(), but merges "all levels"-type variants into one "open_level" key.
    fun levelId(value: String): String {
        return when (id(value)) {
            "open_level", "all_levels", "all_level" -> "open_level"
            else -> id(value)
        }
    }

    // Normalizes a content type key (post/event/class/etc).
    fun contentTypeId(value: String): String = id(value)

    // Normalizes a creator type key (dancer/teacher/studio/etc).
    fun creatorTypeId(value: String): String = id(value)
}
