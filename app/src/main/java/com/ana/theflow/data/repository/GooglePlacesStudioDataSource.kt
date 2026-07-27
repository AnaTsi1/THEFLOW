package com.ana.theflow.data.repository

import android.content.Context
import android.location.Location
import com.ana.theflow.BuildConfig
import com.ana.theflow.data.model.discovery.DiscoveryItem
import com.ana.theflow.utilities.StudioDiscoveryUtils
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.maps.model.LatLng
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.CircularBounds
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.api.net.PlacesClient
import com.google.android.libraries.places.api.net.SearchByTextRequest

class GooglePlacesStudioDataSource(
    context: Context
) : ExternalStudioDataSource {

    private val placesClient: PlacesClient? = if (
        BuildConfig.PLACES_API_KEY.isNotBlank() && Places.isInitialized()
    ) {
        Places.createClient(context.applicationContext)
    } else {
        null
    }

    override fun searchStudios(
        query: String,
        city: String,
        location: Location?,
        onSuccess: (List<DiscoveryItem>) -> Unit,
        onFailure: (String) -> Unit
    ) {
        val client = placesClient
        if (client == null) {
            onFailure("Google Places is not configured yet.")
            return
        }

        val queries = StudioDiscoveryUtils.buildExternalStudioQueries(query, city)
        if (queries.isEmpty()) {
            onSuccess(emptyList())
            return
        }

        val collected = linkedMapOf<String, DiscoveryItem>()
        var pending = queries.size
        var completed = false

        queries.forEach { textQuery ->
            val request = SearchByTextRequest.builder(textQuery, placeFields())
                .setMaxResultCount(8)
                .setRegionCode("IL")
                .apply {
                    if (location != null) {
                        setLocationBias(
                            CircularBounds.newInstance(
                                LatLng(location.latitude, location.longitude),
                                SEARCH_RADIUS_METERS
                            )
                        )
                    }
                }
                .build()

            client.searchByText(request)
                .addOnSuccessListener { response ->
                    if (completed) return@addOnSuccessListener
                    response.places
                        .mapNotNull { place -> place.toDiscoveryItem(city, location) }
                        .filter { StudioDiscoveryUtils.isRelevantDanceStudio(it) }
                        .forEach { item ->
                            collected.putIfAbsent(item.googlePlaceId, item)
                        }
                    pending -= 1
                    if (pending == 0) {
                        completed = true
                        onSuccess(collected.values.toList())
                    }
                }
                .addOnFailureListener { error ->
                    pending -= 1
                    if (pending == 0 && !completed) {
                        completed = true
                        if (collected.isNotEmpty()) {
                            onSuccess(collected.values.toList())
                        } else {
                            onFailure(userMessageFor(error))
                        }
                    }
                }
        }
    }

    private fun buildQueries(query: String, city: String): List<String> {
        val locationSuffix = city.trim().ifBlank { "near me" }
        val baseTerms = listOf(
            "dance studio",
            "dance school",
            "dancing school",
            "ballet school",
            "hip-hop studio",
            "dance academy",
            "סטודיו לריקוד",
            "בית ספר לריקוד",
            "סטודיו למחול",
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

    private fun placeFields(): List<Place.Field> {
        return listOf(
            Place.Field.ID,
            Place.Field.DISPLAY_NAME,
            Place.Field.FORMATTED_ADDRESS,
            Place.Field.LOCATION,
            Place.Field.RATING,
            Place.Field.USER_RATING_COUNT,
            Place.Field.BUSINESS_STATUS,
            Place.Field.INTERNATIONAL_PHONE_NUMBER,
            Place.Field.WEBSITE_URI,
            Place.Field.GOOGLE_MAPS_URI,
            Place.Field.PHOTO_METADATAS
        )
    }

    private fun Place.toDiscoveryItem(city: String, userLocation: Location?): DiscoveryItem? {
        val placeId = id.orEmpty()
        val name = displayName.orEmpty()
        if (placeId.isBlank() || name.isBlank()) return null
        val placeLocation = location
        val distance = if (userLocation != null && placeLocation != null) {
            FloatArray(1).also { result ->
                Location.distanceBetween(
                    userLocation.latitude,
                    userLocation.longitude,
                    placeLocation.latitude,
                    placeLocation.longitude,
                    result
                )
            }[0].toDouble()
        } else {
            null
        }

        return DiscoveryItem(
            id = "google_$placeId",
            title = name,
            studio = name,
            teacher = "Google Places",
            style = "Dance",
            level = "All levels",
            location = city.ifBlank { formattedAddress.orEmpty() },
            time = businessStatus?.name?.replace('_', ' ') ?: "Check availability",
            type = "Studio",
            latitude = placeLocation?.latitude,
            longitude = placeLocation?.longitude,
            source = DiscoveryItem.SOURCE_GOOGLE,
            googlePlaceId = placeId,
            address = formattedAddress.orEmpty(),
            distanceMeters = distance,
            rating = rating,
            ratingCount = userRatingCount,
            phoneNumber = internationalPhoneNumber.orEmpty(),
            websiteUrl = websiteUri?.toString().orEmpty(),
            googleMapsUrl = googleMapsUri?.toString().orEmpty(),
            attributionHtml = photoMetadatas?.firstOrNull()?.attributions.orEmpty(),
            displayType = "google_place"
        )
    }

    private fun DiscoveryItem.isRelevantDanceStudio(): Boolean {
        val haystack = listOf(title, studio, address, type)
            .joinToString(" ")
            .lowercase()
        val negativeTerms = listOf("restaurant", "bar", "night club", "club", "clothing", "shoes")
        if (negativeTerms.any { it in haystack }) return false
        val positiveTerms = listOf(
            "dance",
            "dancing",
            "ballet",
            "hip",
            "studio",
            "school",
            "academy",
            "מחול",
            "ריקוד",
            "ריקודים",
            "סטודיו"
        )
        if (positiveTerms.any { it in haystack }) return true
        return listOf("dance", "dancing", "ballet", "hip", "מחול", "ריקוד").any { it in haystack }
    }

    private fun userMessageFor(error: Exception): String {
        val status = (error as? ApiException)?.statusCode
        return when (status) {
            7 -> "Network unavailable. Showing THE FLOW studios for now."
            8, 10 -> "Google Places is not configured yet."
            9011, 9012 -> "Places quota is unavailable right now."
            else -> "External studio search is unavailable right now."
        }
    }

    companion object {
        private const val MAX_QUERY_COUNT = 8
        private const val SEARCH_RADIUS_METERS = 30000.0
    }
}
