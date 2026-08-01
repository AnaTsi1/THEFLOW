// Decides "where is this user, for ranking purposes" for a given surface (Discover, Search, Map,
// Home, Following), trying manual/preferred/profile/device location sources in a per-surface
// priority order before falling back to a country-wide default.
package com.ana.theflow.data.recommendation

import com.ana.theflow.utilities.CityOptions

object LocationSourceResolver {
    private val israelFallback = GeoPoint(31.5, 34.75)

    // Figures out "where is this user" for whichever screen is asking. Each screen has its own
    // order of preference - Discover deliberately skips live GPS, so a traveling user's whole feed
    // doesn't reshuffle just because they left home, but Search and the map do use it.
    fun resolve(context: RecommendationContext): ResolvedLocation {
        val manual = cityLocation(context.manualSelectedLocation, ResolvedLocationSource.MANUAL_SELECTION, hardFilter = true)
        val preferred = cityLocation(context.preferredRecommendationArea, ResolvedLocationSource.PREFERRED_RECOMMENDATION_AREA)
        val profile = cityLocation(context.profileLocation, ResolvedLocationSource.PROFILE_LOCATION)

        return when (context.surface) {
            RecommendationSurface.SEARCH -> {
                manual ?: device(context, useAsBias = true) ?: preferred ?: profile ?: fallback()
            }
            RecommendationSurface.MAP -> {
                context.mapCameraLocation?.takeIf { it.isValid() }?.let {
                    return ResolvedLocation(
                        source = ResolvedLocationSource.MAP_CAMERA,
                        displayName = "Selected map area",
                        point = it,
                        hardFilter = true,
                        useAsGooglePlacesBias = true
                    )
                }
                manual ?: device(context, useAsBias = true) ?: preferred ?: profile ?: fallback()
            }
            RecommendationSurface.DISCOVER -> {
                manual ?: preferred ?: profile ?: fallback()
            }
            RecommendationSurface.FOR_YOU -> {
                preferred?.copy(hardFilter = false, useAsGooglePlacesBias = false)
                    ?: profile?.copy(hardFilter = false, useAsGooglePlacesBias = false)
                    ?: ResolvedLocation(ResolvedLocationSource.NONE)
            }
            RecommendationSurface.FOLLOWING -> ResolvedLocation(ResolvedLocationSource.NONE)
        }
    }

    // Tries to match a free-text city name to one of our known cities. Returns null if it doesn't match anything.
    private fun cityLocation(value: String, source: ResolvedLocationSource, hardFilter: Boolean = false): ResolvedLocation? {
        val city = CityOptions.cityFor(value) ?: return null
        return ResolvedLocation(
            source = source,
            cityId = city.id,
            displayName = city.displayName,
            point = GeoPoint(city.latitude, city.longitude),
            hardFilter = hardFilter,
            useAsGooglePlacesBias = hardFilter || source == ResolvedLocationSource.PREFERRED_RECOMMENDATION_AREA
        )
    }

    // Grabs the device's current GPS location, if we have one and it looks valid.
    private fun device(context: RecommendationContext, useAsBias: Boolean): ResolvedLocation? {
        val point = context.currentDeviceLocation?.takeIf { it.isValid() } ?: return null
        return ResolvedLocation(
            source = ResolvedLocationSource.DEVICE_LOCATION,
            displayName = "Current location",
            point = point,
            hardFilter = context.surface == RecommendationSurface.MAP,
            useAsGooglePlacesBias = useAsBias
        )
    }

    // Last resort if nothing else worked - just default to Israel, and never treat it as a hard filter.
    private fun fallback(): ResolvedLocation {
        return ResolvedLocation(
            source = ResolvedLocationSource.FALLBACK_COUNTRY,
            displayName = "Israel",
            point = israelFallback,
            hardFilter = false,
            useAsGooglePlacesBias = false
        )
    }
}
