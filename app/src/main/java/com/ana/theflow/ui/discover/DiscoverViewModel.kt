package com.ana.theflow.ui.discover

import androidx.lifecycle.ViewModel
import com.ana.theflow.data.recommendation.RecommendationProfile

class DiscoverViewModel : ViewModel() {
    var loadedInternal: Boolean = false
    var loadedExternal: Boolean = false
    var requestedExternal: Boolean = false
    var isRefreshing: Boolean = false
    var lastLoadedAtMillis: Long = 0L
    var scrollY: Int = 0
    var lastError: String = ""
    var recommendationProfile: RecommendationProfile = RecommendationProfile.empty()

    fun hasUsableCache(): Boolean {
        return loadedInternal
    }

    fun isStale(now: Long = System.currentTimeMillis()): Boolean {
        return lastLoadedAtMillis == 0L || now - lastLoadedAtMillis > STALE_AFTER_MS
    }

    fun markLoaded() {
        lastLoadedAtMillis = System.currentTimeMillis()
        isRefreshing = false
    }

    companion object {
        private const val STALE_AFTER_MS = 5 * 60 * 1000L
    }
}
