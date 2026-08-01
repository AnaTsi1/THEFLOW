// Persists the last-known resolved recommendation preferences (styles/level/location) across
// process restarts, keyed by uid - same SharedPreferences-backed pattern as ActiveAccountHolder.
//
// Why this exists: DiscoveryRepository (a process-lifetime singleton) keeps its own in-memory
// preferredLocation/preferredStyles/preferredLevel as the synchronous fallback used by anything
// that can't block on a Firestore round-trip (e.g. resolving "Studios Near You"'s viewer point
// while its RecyclerView row is already binding). On a cold app start those in-memory fields
// start blank and only get populated once loadRecommendationProfile()'s async read completes -
// if a location-dependent fetch fires before that finishes (entirely plausible: the Discover
// RecyclerView's lookahead binds rows well before layout settles), it silently falls back to a
// generic country-wide point instead of the user's real city, even though that real city is
// sitting in Firestore the whole time. Restoring last-known-good values here at init closes that
// window for every returning user - only a user's very first session ever has nothing to
// restore, and that session writes onboarding preferences synchronously anyway.
package com.ana.theflow.data.session

import android.content.Context
import android.content.SharedPreferences

object RecommendationPreferenceCache {
    private const val PREFS_NAME = "flow_recommendation_prefs"
    private const val KEY_STYLES_PREFIX = "styles_"
    private const val KEY_LEVEL_PREFIX = "level_"
    private const val KEY_LOCATION_PREFIX = "location_"

    private var prefs: SharedPreferences? = null

    // Opens the SharedPreferences file once for the app's lifetime - safe to call more than once,
    // later calls are no-ops.
    fun init(context: Context) {
        if (prefs != null) return
        prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    // Stashes the resolved preferences for this uid so the next cold start has something to
    // restore before Firestore answers.
    fun save(uid: String, styles: Collection<String>, level: String, location: String) {
        if (uid.isBlank()) return
        prefs?.edit()
            ?.putString(KEY_STYLES_PREFIX + uid, styles.joinToString(","))
            ?.putString(KEY_LEVEL_PREFIX + uid, level)
            ?.putString(KEY_LOCATION_PREFIX + uid, location)
            ?.apply()
    }

    data class Restored(val styles: List<String>, val level: String, val location: String)

    // Reads back whatever was last saved for this uid, or null if there's genuinely nothing
    // stored yet (as opposed to values that just happen to be blank).
    fun restore(uid: String): Restored? {
        if (uid.isBlank()) return null
        val store = prefs ?: return null
        val location = store.getString(KEY_LOCATION_PREFIX + uid, "").orEmpty()
        val level = store.getString(KEY_LEVEL_PREFIX + uid, "").orEmpty()
        val styles = store.getString(KEY_STYLES_PREFIX + uid, "").orEmpty()
            .split(",")
            .map { it.trim() }
            .filter { it.isNotBlank() }
        if (location.isBlank() && level.isBlank() && styles.isEmpty()) return null
        return Restored(styles, level, location)
    }
}
