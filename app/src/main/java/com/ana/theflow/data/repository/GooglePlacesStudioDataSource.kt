// This is where we go out to Google Places and look up real dance studios, so search and
// discover aren't limited to studios that already have a profile on THE FLOW.
package com.ana.theflow.data.repository

import android.content.Context
import android.location.Location
import com.ana.theflow.BuildConfig
import com.ana.theflow.data.model.discovery.DiscoveryItem
import com.ana.theflow.utilities.CityOptions
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

    // Runs the actual Places search. We build a few different query variants (see
    // buildExternalStudioQueries) and fire them all at once, then merge whatever comes back and
    // filter out anything that isn't really a dance studio.
    override fun searchStudios(
        query: String,
        city: String,
        location: Location?,
        radiusMeters: Double?,
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
                        val radius = (radiusMeters ?: DEFAULT_SEARCH_RADIUS_METERS)
                            .coerceIn(MIN_SEARCH_RADIUS_METERS, MAX_SEARCH_RADIUS_METERS)
                        setLocationBias(
                            CircularBounds.newInstance(
                                LatLng(location.latitude, location.longitude),
                                radius
                            )
                        )
                    }
                }
                .build()

            client.searchByText(request)
                .addOnSuccessListener { response ->
                    if (completed) return@addOnSuccessListener
                    response.places
                        .mapNotNull { place -> place.toDiscoveryItem(location) }
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

    // Which fields we ask Google for on every search. Worth remembering that rating, phone, and
    // website all push this call into Google's pricier billing tier - they're included on purpose
    // because we actually show all three (rating badges everywhere, phone/website on the detail
    // screen), not because we grabbed everything without thinking about it.
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

    // Turns a raw Google Place result into our own DiscoveryItem shape, so the rest of the app can
    // treat a Google studio the same way it treats one of our own. Skips anything missing an id or
    // name since we can't really show a studio without those.
    private fun Place.toDiscoveryItem(userLocation: Location?): DiscoveryItem? {
        val placeId = id.orEmpty()
        val name = displayName.orEmpty()
        if (placeId.isBlank() || name.isBlank()) return null
        val placeLocation = location
        val address = formattedAddress.orEmpty()
        val inferredCity = inferCityFromAddress(address)
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
            location = inferredCity,
            time = businessStatus?.name?.replace('_', ' ') ?: "Check availability",
            type = "Studio",
            latitude = placeLocation?.latitude,
            longitude = placeLocation?.longitude,
            source = DiscoveryItem.SOURCE_GOOGLE,
            googlePlaceId = placeId,
            address = address,
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

    // Google doesn't give us a clean "city" field, just a formatted address, so we just check if
    // any of our known city names (or their aliases) show up in it.
    private fun inferCityFromAddress(address: String): String {
        if (address.isBlank()) return ""
        return CityOptions.cityOptions.firstOrNull { city ->
            address.contains(city.displayName, ignoreCase = true) ||
                city.aliases.any { alias -> address.contains(alias, ignoreCase = true) }
        }?.displayName.orEmpty()
    }

    // Turns whatever error code Places threw at us into something readable to show the user,
    // instead of a raw API error message.
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
        private const val DEFAULT_SEARCH_RADIUS_METERS = 30000.0
        // Places API (New) rejects a location bias radius outside this range.
        private const val MIN_SEARCH_RADIUS_METERS = 500.0
        private const val MAX_SEARCH_RADIUS_METERS = 50000.0
    }
}
