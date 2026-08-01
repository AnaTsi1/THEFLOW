// Unified model for anything shown in Discover/Search: an internal THE FLOW studio or activity,
// or an external Google Places result. One shape covers both sources so cards, maps, and the
// recommendation engine can treat them interchangeably.
package com.ana.theflow.data.model.discovery

// One thing you can discover - a studio, class, workshop, or event - whether it's ours or came from Google Places.
data class DiscoveryItem(
    val id: String,
    val title: String,
    val studio: String,
    val teacher: String,
    val style: String,
    val level: String,
    val location: String,
    val time: String,
    val type: String,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val claimStatus: String = "",
    val ownerUid: String = "",
    val source: String = SOURCE_INTERNAL,
    val googlePlaceId: String = "",
    val address: String = "",
    val distanceMeters: Double? = null,
    val rating: Double? = null,
    val ratingCount: Int? = null,
    val openNowLabel: String = "",
    val phoneNumber: String = "",
    val websiteUrl: String = "",
    val googleMapsUrl: String = "",
    val attributionHtml: String = "",
    val coverImageUrl: String = "",
    val priceText: String = "",
    val dateTimeText: String = "",
    val displayType: String = "",
    // Pre-formatted day-of-month/month-abbreviation for the "Events This Week" card's large
    // date badge - computed once from the real activity Date at the data layer instead of
    // re-parsing dateTimeText's Date.toString() output in the renderer.
    val eventDayOfMonth: String = "",
    val eventMonthAbbrev: String = "",
    // Popularity/freshness signals the recommendation engine uses to tell a brand-new, unfollowed
    // studio apart from a well-established, popular one of the same style/city.
    val createdAtMillis: Long = 0L,
    val followersCount: Long = 0L
) {
    companion object {
        const val SOURCE_INTERNAL = "internal"
        const val SOURCE_GOOGLE = "google"
    }
}
