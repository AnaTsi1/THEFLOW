package com.ana.theflow.ui.discover

import androidx.lifecycle.ViewModel
import com.ana.theflow.data.recommendation.RecommendationProfile

class DiscoverViewModel : ViewModel() {
    var loadedInternal: Boolean = false
    var loadedExternal: Boolean = false
    var requestedExternal: Boolean = false
    var isRefreshing: Boolean = false
    var lastLoadedAtMillis: Long = 0L
    var lastError: String = ""
    var recommendationProfile: RecommendationProfile = RecommendationProfile.empty()

    // Lazy-loaded Discover sections (everything except Recommended, which uses the fields
    // above). Living on this activity-scoped ViewModel means switching away from the Discover
    // tab and back within the same app session never re-triggers a fetch for a section that's
    // already been loaded once - only a fresh process start clears this.
    val triggeredSections: MutableSet<DiscoverSectionType> = mutableSetOf()
    val loadingSections: MutableSet<DiscoverSectionType> = mutableSetOf()
    val sectionErrors: MutableMap<DiscoverSectionType, String> = mutableMapOf()

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
