package com.ana.theflow.utilities

import android.location.Location
import com.ana.theflow.data.model.discovery.DiscoveryItem

object StudioDiscoveryUtils {
    private const val DUPLICATE_DISTANCE_METERS = 120
    private const val MAX_QUERY_COUNT = 4

    fun buildExternalStudioQueries(query: String, city: String): List<String> {
        val locationSuffix = city.trim().ifBlank { "near me" }
        val baseTerms = listOf(
            "dance studio",
            "dance school",
            "dancing school",
            "ballet school",
            "hip-hop studio",
            "סטודיו לריקוד",
            "בית ספר לריקוד",
            "סטודיו למחול"
        )
        val cleanedQuery = query.trim()
        val searchTerms = if (cleanedQuery.isBlank()) baseTerms else listOf(cleanedQuery) + baseTerms
        return searchTerms
            .map { "$it $locationSuffix" }
            .distinct()
            .take(MAX_QUERY_COUNT)
    }

    fun mergeInternalAndExternal(
        internalItems: List<DiscoveryItem>,
        externalItems: List<DiscoveryItem>
    ): List<DiscoveryItem> {
        if (externalItems.isEmpty()) return internalItems
        val internalGoogleIds = internalItems.mapNotNull { it.googlePlaceId.takeIf(String::isNotBlank) }.toSet()
        val internalKeys = internalItems.map { duplicateKey(it) }.toSet()
        val filteredExternal = externalItems.filterNot { external ->
            external.googlePlaceId in internalGoogleIds ||
                duplicateKey(external) in internalKeys ||
                internalItems.any { internal -> looksLikeSameStudio(internal, external) }
        }
        return (internalItems + filteredExternal).sortedWith(
            compareByDescending<DiscoveryItem> { it.source == DiscoveryItem.SOURCE_INTERNAL }
                .thenByDescending { it.ownerUid.isNotBlank() || it.claimStatus.equals("CLAIMED", ignoreCase = true) }
                .thenBy { it.distanceMeters ?: Double.MAX_VALUE }
                .thenByDescending { if (it.source == DiscoveryItem.SOURCE_GOOGLE) it.rating ?: 0.0 else 10.0 }
        )
    }

    fun isRelevantDanceStudio(item: DiscoveryItem): Boolean {
        val haystack = listOf(item.title, item.studio, item.address, item.type)
            .joinToString(" ")
            .lowercase()
        val negativeTerms = listOf("restaurant", "bar", "night club", "club", "clothing", "shoes")
        if (negativeTerms.any { it in haystack }) return false
        return listOf("dance", "dancing", "ballet", "hip", "מחול", "ריקוד").any { it in haystack }
    }

    fun normalize(value: String): String {
        return value.lowercase()
            .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
            .trim()
    }

    private fun duplicateKey(item: DiscoveryItem): String {
        return "${normalize(item.studio)}|${normalize(item.address.ifBlank { item.location })}"
    }

    private fun looksLikeSameStudio(internal: DiscoveryItem, external: DiscoveryItem): Boolean {
        if (normalize(internal.studio) != normalize(external.studio)) return false
        val internalLat = internal.latitude
        val internalLng = internal.longitude
        val externalLat = external.latitude
        val externalLng = external.longitude
        if (internalLat != null && internalLng != null && externalLat != null && externalLng != null) {
            val distance = FloatArray(1)
            Location.distanceBetween(internalLat, internalLng, externalLat, externalLng, distance)
            return distance[0] <= DUPLICATE_DISTANCE_METERS
        }
        return normalize(internal.location).isNotBlank() &&
            normalize(internal.location) == normalize(external.location)
    }
}
