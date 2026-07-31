package com.ana.theflow.data.recommendation

import com.ana.theflow.utilities.CityOptions

object LocationSourceResolver {
    private val israelFallback = GeoPoint(31.5, 34.75)

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
