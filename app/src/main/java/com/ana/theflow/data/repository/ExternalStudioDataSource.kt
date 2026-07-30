package com.ana.theflow.data.repository

import android.location.Location
import com.ana.theflow.data.model.discovery.DiscoveryItem

interface ExternalStudioDataSource {
    fun searchStudios(
        query: String,
        city: String,
        location: Location?,
        onSuccess: (List<DiscoveryItem>) -> Unit,
        onFailure: (String) -> Unit
    )
}
