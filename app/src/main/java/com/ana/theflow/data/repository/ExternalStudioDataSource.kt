// Contract for looking up studios from a source outside THE FLOW's own database (currently
// implemented by GooglePlacesStudioDataSource).
package com.ana.theflow.data.repository

import android.location.Location
import com.ana.theflow.data.model.discovery.DiscoveryItem

// A source of studio results that isn't THE FLOW's own database - right now that's just Google Places.
interface ExternalStudioDataSource {
    // Searches for studios matching the query/city near a location, within a radius if given.
    fun searchStudios(
        query: String,
        city: String,
        location: Location?,
        // Meters from `location` to search within. Pass a radius based on the map's current zoom
        // level when you have one; falls back to a sensible default otherwise.
        radiusMeters: Double? = null,
        onSuccess: (List<DiscoveryItem>) -> Unit,
        onFailure: (String) -> Unit
    )
}
