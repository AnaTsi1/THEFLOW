package com.ana.theflow.data.recommendation

object RecommendationNormalizer {
    fun id(value: String): String {
        return value.trim()
            .lowercase()
            .replace(Regex("[׳’'`´]"), "")
            .replace(Regex("[^\\p{L}\\p{N}]+"), "_")
            .trim('_')
            .ifBlank { "unknown" }
    }

    fun styleId(value: String): String {
        return when (id(value)) {
            "hip_hop", "hiphop" -> "hip_hop"
            else -> id(value)
        }
    }

    fun levelId(value: String): String {
        return when (id(value)) {
            "open_level", "all_levels", "all_level" -> "open_level"
            else -> id(value)
        }
    }

    fun contentTypeId(value: String): String = id(value)

    fun creatorTypeId(value: String): String = id(value)
}
