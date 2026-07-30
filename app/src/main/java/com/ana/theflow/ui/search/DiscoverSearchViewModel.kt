package com.ana.theflow.ui.search

import androidx.lifecycle.ViewModel
import com.ana.theflow.data.model.discovery.DiscoveryItem
import com.ana.theflow.data.model.post.Post
import com.ana.theflow.data.model.user.User

class DiscoverSearchViewModel : ViewModel() {
    val state = SearchUiState()

    fun activeFilterCount(): Int {
        return listOf(
            if (state.filters.styles.isNotEmpty()) "styles" else state.filters.style,
            if (state.filters.levels.isNotEmpty()) "levels" else state.filters.level,
            if (state.filters.locations.isNotEmpty()) "locations" else state.filters.location,
            state.filters.distance,
            if (state.filters.dates.isNotEmpty()) "dates" else state.filters.date,
            if (state.filters.times.isNotEmpty()) "times" else state.filters.time,
            if (state.filters.contentTypes.isNotEmpty()) "types" else state.filters.contentType,
            state.filters.rating,
            state.filters.price,
            if (state.filters.freeOnly) "free" else "",
            if (state.filters.openNow) "open" else ""
        ).count { it.isNotBlank() }
    }
}

data class SearchUiState(
    var query: String = "",
    var selectedCategory: SearchCategory = SearchCategory.ALL,
    var viewMode: SearchViewMode = SearchViewMode.LIST,
    var filters: SearchFilters = SearchFilters(),
    var results: List<DiscoveryItem> = emptyList(),
    var peopleResults: List<User> = emptyList(),
    var postResults: List<Post> = emptyList(),
    var isLoading: Boolean = false,
    var error: String = "",
    var locationMessage: String = "",
    var selectedMarkerItem: DiscoveryItem? = null,
    var hasCenteredInitialMap: Boolean = false,
    var recentSearches: MutableList<String> = mutableListOf(),
    var hasLoadedRepositoryData: Boolean = false,
    var lastLoadedAtMillis: Long = 0L,
    var isBackgroundRefreshing: Boolean = false,
    var searchRequestId: Long = 0L,
    var scrollY: Int = 0,
    var mapLatitude: Double? = null,
    var mapLongitude: Double? = null,
    var mapZoom: Float? = null,
    var userMovedMap: Boolean = false
)

data class SearchFilters(
    var style: String = "",
    var styles: MutableSet<String> = mutableSetOf(),
    var level: String = "",
    var levels: MutableSet<String> = mutableSetOf(),
    var location: String = "",
    var locations: MutableSet<String> = mutableSetOf(),
    var distance: String = "",
    var date: String = "",
    var dates: MutableSet<String> = mutableSetOf(),
    var time: String = "",
    var times: MutableSet<String> = mutableSetOf(),
    var contentType: String = "",
    var contentTypes: MutableSet<String> = mutableSetOf(),
    var rating: String = "",
    var price: String = "",
    var freeOnly: Boolean = false,
    var openNow: Boolean = false
)

enum class SearchViewMode {
    LIST,
    MAP
}

enum class SearchCategory(val label: String) {
    ALL("All"),
    PEOPLE("People"),
    STUDIOS("Studios"),
    CLASSES("Classes"),
    EVENTS("Events")
}
