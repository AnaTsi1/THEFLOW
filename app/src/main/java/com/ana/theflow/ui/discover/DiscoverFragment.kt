package com.ana.theflow.ui.discover

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.ana.theflow.MainActivity
import com.ana.theflow.R
import com.ana.theflow.data.model.discovery.DiscoveryItem
import com.ana.theflow.data.model.user.User
import com.ana.theflow.data.repository.DiscoveryRepository
import com.ana.theflow.data.repository.UserRepository
import com.ana.theflow.databinding.FragmentDiscoverBinding
import com.ana.theflow.ui.common.DiscoveryCardRenderer
import com.ana.theflow.utilities.CityOptions
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.MarkerOptions

class DiscoverFragment : Fragment(), OnMapReadyCallback {

    private var _binding: FragmentDiscoverBinding? = null
    private val binding get() = _binding!!
    private val userRepository = UserRepository()
    private var googleMap: GoogleMap? = null
    private var discoverMode = DiscoverMode.PERSONAL
    private var lastExternalQuery: String = ""
    private var externalLoaded = false
    private var mapWideStudiosLoaded = false
    private var searchMode = SearchMode.ALL
    private var mapRecommendedOnly = false
    private var isFiltering = false
    private var isSearching = false

    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val granted = grants.values.any { it }
        if (!granted && _binding != null) {
            Toast.makeText(requireContext(), R.string.discover_location_permission_denied, Toast.LENGTH_SHORT).show()
            loadWideMapStudios()
            return@registerForActivityResult
        }
        loadExternalStudios(currentLocationIfAllowed(), force = true)
    }

    // Creates and returns the fragment view.
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentDiscoverBinding.inflate(inflater, container, false)
        return binding.root
    }

    // Connects the screen UI after the view is ready.
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        discoverMode = DiscoverMode.fromArgument(arguments?.getString(ARG_MODE))
        val mapFragment = childFragmentManager.findFragmentById(R.id.discover_MAP_results) as? SupportMapFragment
        mapFragment?.getMapAsync(this)
        configureDanceStyleSelector()
        configureLevelSelector()
        CityOptions.configureCitySelector(requireContext(), binding.discoverEDTLocation)
        setupModeTabs()
        setupSearch()
        setupSearchTypeChips()
        configureFilterInputActions()
        binding.discoverBTNSearchArea.setOnClickListener {
            searchCurrentMapArea()
        }
        binding.discoverBTNMapRecommended.setOnClickListener {
            mapRecommendedOnly = !mapRecommendedOnly
            renderMapRecommendedFilter()
            renderMapMarkers(currentDiscoverItems())
        }
        binding.discoverBTNApplyFilters.setOnClickListener {
            applyFilters()
        }
        binding.discoverBTNClearFilters.setOnClickListener {
            clearFilters()
        }
        binding.discoverLBLExplanation.text = getString(R.string.discover_subtitle)
        if (discoverMode == DiscoverMode.MAP) loadWideMapStudios()
        render()
        loadUserPreferences()
        DiscoveryRepository.loadSavedItems(
            onSuccess = {
                if (_binding != null) render()
            },
            onFailure = { error ->
                if (_binding != null) {
                    Toast.makeText(requireContext(), error, Toast.LENGTH_SHORT).show()
                    render()
                }
            }
        )
        DiscoveryRepository.loadApprovedStudios(
            onSuccess = {
                if (_binding != null) {
                    render()
                    loadPublishedActivities()
                    requestLocationThenLoadExternal()
                }
            },
            onFailure = { error ->
                if (_binding != null) {
                    binding.discoverLBLExplanation.text = getString(R.string.discover_subtitle)
                    render()
                    requestLocationThenLoadExternal()
                }
            }
        )
    }

    // Connects the level field to the levels used by the app.
    private fun configureLevelSelector() {
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, LEVEL_OPTIONS)
        binding.discoverEDTLevel.setAdapter(adapter)
        binding.discoverEDTLevel.threshold = 0
        binding.discoverEDTLevel.setOnClickListener {
            binding.discoverEDTLevel.showDropDown()
        }
        binding.discoverEDTLevel.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) binding.discoverEDTLevel.showDropDown()
        }
    }

    // Connects the style field to known dance styles while still allowing custom text.
    private fun configureDanceStyleSelector() {
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, DANCE_STYLE_OPTIONS)
        binding.discoverEDTStyle.setAdapter(adapter)
        binding.discoverEDTStyle.threshold = 0
        binding.discoverEDTStyle.setOnClickListener {
            binding.discoverEDTStyle.showDropDown()
        }
        binding.discoverEDTStyle.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) binding.discoverEDTStyle.showDropDown()
        }
    }

    // Connects keyboard search actions to the Discover filters.
    private fun configureFilterInputActions() {
        val searchAction = android.widget.TextView.OnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                applyFilters()
                true
            } else {
                false
            }
        }

        binding.discoverEDTSearch.setOnEditorActionListener(searchAction)
        binding.discoverEDTStyle.setOnEditorActionListener(searchAction)
        binding.discoverEDTLevel.setOnEditorActionListener(searchAction)
        binding.discoverEDTLocation.setOnEditorActionListener(searchAction)
    }

    // Applies the Discover search and filter fields.
    private fun applyFilters() {
        val hasFilters = hasActiveFilters()
        if (!hasFilters) {
            isFiltering = false
            isSearching = false
            render()
            return
        }

        isSearching = true
        isFiltering = false
        binding.discoverBTNApplyFilters.isEnabled = false
        render()

        binding.root.postDelayed({
            if (_binding == null) return@postDelayed
            isSearching = false
            isFiltering = true
            binding.discoverBTNApplyFilters.isEnabled = true
            DiscoveryRepository.trackDiscoverSearch(
                query = binding.discoverEDTSearch.text.toString(),
                style = binding.discoverEDTStyle.text.toString(),
                location = selectedOptionalCity(binding.discoverEDTLocation.text.toString())
            )
            render()
        }, SEARCH_FEEDBACK_DELAY_MS)
    }

    // Clears all Discover filters and restores recommendations.
    private fun clearFilters() {
        binding.discoverEDTSearch.text?.clear()
        binding.discoverEDTStyle.text?.clear()
        binding.discoverEDTLevel.text?.clear()
        binding.discoverEDTLocation.text?.clear()
        isFiltering = false
        isSearching = false
        binding.discoverBTNApplyFilters.isEnabled = true
        render()
    }

    // Draws the screen content from current data.
    private fun render() {
        renderModeTabs()
        renderModeVisibility()
        binding.discoverLBLExplanation.text = getString(R.string.discover_subtitle)
        binding.discoverLAYRecommended.removeAllViews()
        binding.discoverLAYPopular.removeAllViews()
        binding.discoverLAYSearchResults.removeAllViews()

        if (discoverMode == DiscoverMode.MAP) {
            binding.discoverPRGSearching.visibility = View.GONE
            renderMapMarkers(currentDiscoverItems())
            return
        }

        if (discoverMode == DiscoverMode.SEARCH && !hasActiveSearchFields()) {
            binding.discoverPRGSearching.visibility = View.GONE
            binding.discoverLBLSearchResultsTitle.visibility = View.VISIBLE
            binding.discoverLBLSearchResultsTitle.text = getString(R.string.discover_tab_search)
            binding.discoverLAYSearchResults.addView(emptyText(getString(R.string.discover_search_empty_prompt)))
            return
        }

        if (isSearching) {
            binding.discoverPRGSearching.visibility = View.VISIBLE
            binding.discoverLBLRecommendedTitle.text = getString(R.string.discover_searching)
            binding.discoverLBLPopularTitle.visibility = View.GONE
            binding.discoverLAYPopular.visibility = View.GONE
            return
        }

        binding.discoverPRGSearching.visibility = View.GONE

        if (isFiltering) {
            runDiscoverSearch(loadExternal = false)
            return
        }

        renderRecommendedExperience()
    }

    private fun renderRecommendedExperience() {
        val shownKeys = mutableSetOf<String>()
        val recommended = DiscoveryRepository.recommendedItems()
            .filter { it.source != DiscoveryItem.SOURCE_GOOGLE }
            .uniqueForDiscover()
            .takeUnique(shownKeys, 4)
        val timely = DiscoveryRepository.recommendedItems()
            .filter { it.source != DiscoveryItem.SOURCE_GOOGLE && it.isActivity() && it.time.isUserFacingTime() }
            .uniqueForDiscover()
            .takeUnique(shownKeys, 3)
        val near = DiscoveryRepository.popularNearYou()
            .filter { it.source != DiscoveryItem.SOURCE_GOOGLE }
            .uniqueForDiscover()
            .takeUnique(shownKeys, 3)

        binding.discoverLBLRecommendedTitle.text = getString(R.string.discover_recommended_title)
        binding.discoverLBLRecommendedTitle.visibility = if (recommended.isEmpty()) View.GONE else View.VISIBLE
        binding.discoverLAYRecommended.visibility = if (recommended.isEmpty()) View.GONE else View.VISIBLE
        recommended.forEach { item ->
            addCard(binding.discoverLAYRecommended, item)
        }

        val secondary = timely.ifEmpty { near }
        binding.discoverLBLPopularTitle.text = getString(
            if (timely.isNotEmpty()) R.string.discover_this_week_title else R.string.discover_popular_title
        )
        binding.discoverLBLPopularTitle.visibility = if (secondary.isEmpty()) View.GONE else View.VISIBLE
        binding.discoverLAYPopular.visibility = if (secondary.isEmpty()) View.GONE else View.VISIBLE
        secondary.forEach { item ->
            addCard(binding.discoverLAYPopular, item)
        }

        renderGooglePlacesSection(shownKeys)

        if (recommended.isEmpty() && secondary.isEmpty() && DiscoveryRepository.externalStudioItems().isEmpty()) {
            binding.discoverLBLRecommendedTitle.visibility = View.VISIBLE
            binding.discoverLAYRecommended.visibility = View.VISIBLE
            binding.discoverLAYRecommended.addView(emptyText(getString(R.string.discover_search_empty)))
        }
    }

    private fun renderGooglePlacesSection(shownKeys: MutableSet<String> = mutableSetOf()) {
        if (discoverMode != DiscoverMode.PERSONAL || hasActiveSearchFields()) return

        val googleItems = DiscoveryRepository.externalStudioItems()
            .uniqueForDiscover()
            .filterNot { item -> shownKeys.contains(item.discoverKey()) }
            .take(6)
        binding.discoverLAYSearchResults.removeAllViews()
        binding.discoverLBLSearchResultsTitle.visibility = if (googleItems.isEmpty()) View.GONE else View.VISIBLE
        binding.discoverLBLSearchResultsTitle.text = getString(R.string.discover_more_places)
        googleItems.take(6).forEach { item ->
            addCard(binding.discoverLAYSearchResults, item)
        }
    }

    // Shows only the section that belongs to the selected Discover mode.
    private fun renderModeVisibility() {
        val isSearch = discoverMode == DiscoverMode.SEARCH
        val isMap = discoverMode == DiscoverMode.MAP
        val isPersonal = discoverMode == DiscoverMode.PERSONAL

        binding.discoverLAYMap.visibility = if (isMap) View.VISIBLE else View.GONE
        binding.discoverLAYSearchBox.visibility = View.VISIBLE
        binding.discoverLAYSearchTypes.visibility = if (isSearch) View.VISIBLE else View.GONE
        if (!isSearch && !isMap) binding.discoverLAYAdvanced.visibility = View.GONE
        binding.discoverLBLSearchResultsTitle.visibility = if (isSearch || isPersonal) View.VISIBLE else View.GONE
        binding.discoverLAYSearchResults.visibility = if (isSearch || isPersonal) View.VISIBLE else View.GONE
        binding.discoverLBLRecommendedTitle.visibility = if (isPersonal) View.VISIBLE else View.GONE
        binding.discoverLAYRecommended.visibility = if (isPersonal) View.VISIBLE else View.GONE
        binding.discoverLBLPopularTitle.visibility = if (isPersonal) View.VISIBLE else View.GONE
        binding.discoverLAYPopular.visibility = if (isPersonal) View.VISIBLE else View.GONE
        binding.discoverLBLGoogleAttribution.visibility = View.GONE
        renderMapRecommendedFilter()
    }

    private fun renderMapRecommendedFilter() {
        binding.discoverBTNMapRecommended.setBackgroundResource(
            if (mapRecommendedOnly) R.drawable.bg_discover_segment_active else R.drawable.bg_discover_segment_inactive
        )
        binding.discoverBTNMapRecommended.setTextColor(
            requireContext().getColor(if (mapRecommendedOnly) R.color.white else R.color.discover_purple)
        )
    }

    // Highlights the active Discover mode.
    private fun renderModeTabs() {
        listOf(
            binding.discoverTABForYou to DiscoverMode.PERSONAL,
            binding.discoverTABMap to DiscoverMode.MAP,
            binding.discoverTABSearch to DiscoverMode.SEARCH
        ).forEach { (tab, mode) ->
            val selected = discoverMode == mode
            tab.setBackgroundResource(if (selected) R.drawable.bg_discover_segment_active else R.drawable.bg_discover_segment_inactive)
            tab.setTextColor(requireContext().getColor(if (selected) R.color.white else R.color.discover_text_secondary))
        }
    }

    private fun requestLocationThenLoadExternal() {
        if (externalLoaded) return
        externalLoaded = true
        if (hasLocationPermission()) {
            loadExternalStudios(currentLocationIfAllowed())
        } else {
            loadExternalStudios(location = null, cityOverride = DiscoveryRepository.preferredLocation.ifBlank { "Israel" })
        }
    }

    private fun searchCurrentMapArea() {
        if (hasLocationPermission()) {
            loadExternalStudios(currentLocationIfAllowed(), force = true)
        } else {
            locationPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.ACCESS_FINE_LOCATION
                )
            )
        }
    }

    private fun setupSearch() {
        binding.discoverBTNAdvanced.setOnClickListener {
            binding.discoverLAYAdvanced.visibility =
                if (binding.discoverLAYAdvanced.visibility == View.VISIBLE) View.GONE else View.VISIBLE
        }
        val watcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                runDiscoverSearch(loadExternal = false)
            }
        }
        binding.discoverEDTSearch.addTextChangedListener(watcher)
        binding.discoverEDTLocation.addTextChangedListener(watcher)
        binding.discoverEDTStyle.addTextChangedListener(watcher)
        binding.discoverEDTLevel.addTextChangedListener(watcher)
        binding.discoverEDTTime.addTextChangedListener(watcher)
    }

    private fun setupSearchTypeChips() {
        binding.discoverCHIPUsers.setOnClickListener {
            searchMode = SearchMode.ALL
            renderSearchTypeChips()
            runDiscoverSearch(loadExternal = true)
        }
        binding.discoverCHIPActivities.setOnClickListener {
            searchMode = SearchMode.ACTIVITIES
            renderSearchTypeChips()
            runDiscoverSearch(loadExternal = false)
        }
        binding.discoverCHIPStudios.setOnClickListener {
            searchMode = SearchMode.STUDIOS
            renderSearchTypeChips()
            runDiscoverSearch(loadExternal = true)
        }
        binding.discoverCHIPDancers.setOnClickListener {
            searchMode = SearchMode.PEOPLE
            renderSearchTypeChips()
            runDiscoverSearch(loadExternal = false)
        }
        renderSearchTypeChips()
    }

    private fun runDiscoverSearch(loadExternal: Boolean) {
        val query = binding.discoverEDTSearch.text.toString().trim()
        val location = CityOptions.normalizeOptionalCity(binding.discoverEDTLocation.text.toString()).orEmpty()
        val style = binding.discoverEDTStyle.text.toString().trim()
        val level = binding.discoverEDTLevel.text.toString().trim()
        val time = binding.discoverEDTTime.text.toString().trim()
        val hasSearch = listOf(query, location, style, level, time).any { it.isNotBlank() }
        binding.discoverLBLSearchResultsTitle.visibility = if (hasSearch) View.VISIBLE else View.GONE
        binding.discoverLBLSearchResultsTitle.text = getString(R.string.discover_search_results)
        binding.discoverLAYSearchResults.removeAllViews()
        if (!hasSearch) {
            if (discoverMode == DiscoverMode.MAP) renderMapMarkers(currentDiscoverItems())
            return
        }

        if (discoverMode == DiscoverMode.MAP) {
            renderMapMarkers(currentDiscoverItems())
            return
        }

        when (searchMode) {
            SearchMode.PEOPLE -> searchUsers(query, location, style, level, append = false)
            SearchMode.ALL,
            SearchMode.ACTIVITIES,
            SearchMode.STUDIOS -> {
                val results = DiscoveryRepository.search(
                    style = style,
                    level = level,
                    location = location,
                    teacher = "",
                    studio = query,
                    time = time
                )
                    .filterByFreeText(query)
                    .filterBySearchMode()
                    .uniqueForDiscover()
                renderSearchItemResults(results, showEmptyMessage = searchMode != SearchMode.ALL)
                if (searchMode == SearchMode.ALL) {
                    searchUsers(query, location, style, level, append = true)
                }
                if (loadExternal || searchMode == SearchMode.STUDIOS || searchMode == SearchMode.ALL) {
                    loadExternalStudios(
                        location = currentLocationIfAllowed(),
                        queryOverride = listOf(query, style).filter { it.isNotBlank() }.joinToString(" "),
                        cityOverride = location,
                        force = true
                    )
                }
            }
        }
    }

    private fun searchUsers(query: String, location: String, style: String, level: String, append: Boolean) {
        userRepository.searchUsers(
            query = query,
            dancersOnly = false,
            onSuccess = { users ->
                if (_binding == null) return@searchUsers
                val filtered = users.filter { user ->
                    (location.isBlank() || user.location.equals(location, ignoreCase = true)) &&
                        (style.isBlank() || user.danceStyles.any { it.contains(style, ignoreCase = true) }) &&
                        (level.isBlank() || user.danceLevel.contains(level, ignoreCase = true))
                }
                renderUserSearchResults(filtered, append)
            },
            onFailure = { error ->
                if (_binding != null) renderSearchMessage(error)
            }
        )
    }

    private fun loadExternalStudios(
        location: Location?,
        queryOverride: String = binding.discoverEDTSearch.text.toString(),
        cityOverride: String = "",
        force: Boolean = false
    ) {
        val query = queryOverride
        val key = "$query|$cityOverride|${location?.latitude}|${location?.longitude}|${DiscoveryRepository.preferredLocation}"
        if (!force && key == lastExternalQuery) return
        lastExternalQuery = key
        DiscoveryRepository.loadExternalStudios(
            context = requireContext(),
            query = query,
            city = cityOverride,
            location = location,
            onSuccess = {
                if (_binding != null) {
                    render()
                    if (searchMode == SearchMode.STUDIOS || searchMode == SearchMode.ALL) runDiscoverSearch(loadExternal = false)
                }
            },
            onFailure = {
                if (_binding != null) {
                    Toast.makeText(requireContext(), R.string.discover_external_unavailable, Toast.LENGTH_SHORT).show()
                    render()
                }
            }
        )
    }

    private fun loadUserPreferences() {
        userRepository.loadPreferenceSettings(
            onSuccess = { settings ->
                DiscoveryRepository.hydratePreferences(
                    styles = settings.styles,
                    level = settings.level,
                    location = settings.location,
                    preferredStudios = settings.preferredStudios,
                    preferredTeachers = settings.preferredTeachers,
                    preferredDancers = settings.preferredDancers
                )
                if (_binding != null) render()
            },
            onFailure = {
                if (_binding != null) render()
            }
        )
    }

    private fun renderSearchTypeChips() {
        listOf(
            binding.discoverCHIPUsers to SearchMode.ALL,
            binding.discoverCHIPActivities to SearchMode.ACTIVITIES,
            binding.discoverCHIPStudios to SearchMode.STUDIOS,
            binding.discoverCHIPDancers to SearchMode.PEOPLE
        ).forEach { (chip, mode) ->
            val selected = searchMode == mode
            chip.setBackgroundResource(if (selected) R.drawable.bg_discover_segment_active else R.drawable.bg_discover_segment_inactive)
            chip.setTextColor(requireContext().getColor(if (selected) R.color.white else R.color.discover_text_secondary))
        }
    }

    private fun renderSearchItemResults(items: List<DiscoveryItem>, showEmptyMessage: Boolean = true) {
        binding.discoverLAYSearchResults.removeAllViews()
        if (items.isEmpty()) {
            if (showEmptyMessage) renderSearchMessage(getString(R.string.discover_search_empty))
            return
        }
        val internalItems = items.filter { it.source != DiscoveryItem.SOURCE_GOOGLE }.take(12)
        val googleItems = items.filter { it.source == DiscoveryItem.SOURCE_GOOGLE }.take(6)
        internalItems.forEach { item -> addCard(binding.discoverLAYSearchResults, item) }
        if (googleItems.isNotEmpty()) {
            binding.discoverLAYSearchResults.addView(sectionText(getString(R.string.discover_more_places)))
            googleItems.forEach { item -> addCard(binding.discoverLAYSearchResults, item) }
        }
    }

    private fun renderUserSearchResults(users: List<User>, append: Boolean) {
        if (!append) binding.discoverLAYSearchResults.removeAllViews()
        if (users.isEmpty()) {
            if (!append) renderSearchMessage(getString(R.string.discover_no_users))
            if (append && binding.discoverLAYSearchResults.childCount == 0) {
                renderSearchMessage(getString(R.string.discover_search_empty))
            }
            return
        }
        if (append) {
            binding.discoverLAYSearchResults.addView(sectionText(getString(R.string.discover_search_people)))
        }
        users.take(20).forEach { user ->
            binding.discoverLAYSearchResults.addView(userRow(user))
        }
    }

    private fun renderSearchMessage(message: String) {
        binding.discoverLAYSearchResults.removeAllViews()
        binding.discoverLAYSearchResults.addView(TextView(requireContext()).apply {
            text = message
            setTextColor(requireContext().getColor(R.color.discover_text_secondary))
            textSize = 13f
            setBackgroundResource(R.drawable.bg_discover_card)
            setPadding(14.dp(), 14.dp(), 14.dp(), 14.dp())
        })
    }

    private fun userRow(user: User): View {
        val fullName = "${user.firstName} ${user.lastName}".trim().ifBlank { getString(R.string.post_fallback_author) }
        return LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundResource(R.drawable.bg_discover_card)
            setPadding(14.dp(), 12.dp(), 14.dp(), 12.dp())
            isClickable = true
            isFocusable = true
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = 10.dp()
            }
            setOnClickListener {
                (requireActivity() as MainActivity).openUserProfile(user.uid)
            }
            addView(TextView(requireContext()).apply {
                text = fullName
                setTextColor(requireContext().getColor(R.color.discover_ink))
                textSize = 16f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
            })
            addView(TextView(requireContext()).apply {
                text = listOf(
                    user.headline,
                    user.danceStyles.joinToString(", "),
                    user.danceLevel,
                    user.location
                ).filter { it.isNotBlank() }.joinToString(" / ")
                setTextColor(requireContext().getColor(R.color.discover_text_secondary))
                textSize = 13f
                maxLines = 2
                setPadding(0, 5.dp(), 0, 0)
            })
        }
    }

    private fun List<DiscoveryItem>.filterByFreeText(query: String): List<DiscoveryItem> {
        if (query.isBlank()) return this
        return filter { item ->
            item.title.contains(query, ignoreCase = true) ||
                item.studio.contains(query, ignoreCase = true) ||
                item.teacher.contains(query, ignoreCase = true) ||
                item.address.contains(query, ignoreCase = true)
        }
    }

    private fun List<DiscoveryItem>.filterBySearchMode(): List<DiscoveryItem> {
        return when (searchMode) {
            SearchMode.ALL -> this
            SearchMode.ACTIVITIES -> filter { it.isActivity() }
            SearchMode.STUDIOS -> filter { it.isStudioLike() }
            SearchMode.PEOPLE -> emptyList()
        }
    }

    private fun List<DiscoveryItem>.uniqueForDiscover(): List<DiscoveryItem> {
        val seen = mutableSetOf<String>()
        return filter { item -> seen.add(item.discoverKey()) }
    }

    private fun List<DiscoveryItem>.takeUnique(seen: MutableSet<String>, limit: Int): List<DiscoveryItem> {
        val output = mutableListOf<DiscoveryItem>()
        forEach { item ->
            if (output.size >= limit) return@forEach
            if (seen.add(item.discoverKey())) output.add(item)
        }
        return output
    }

    private fun DiscoveryItem.discoverKey(): String {
        return if (googlePlaceId.isNotBlank()) {
            "google:$googlePlaceId"
        } else {
            "internal:$id"
        }
    }

    private fun DiscoveryItem.isActivity(): Boolean {
        val value = displayType.ifBlank { type }.lowercase()
        return value == "class" || value == "workshop" || value == "event"
    }

    private fun DiscoveryItem.isStudioLike(): Boolean {
        val value = displayType.ifBlank { type }.lowercase()
        return source == DiscoveryItem.SOURCE_GOOGLE || value == "studio"
    }

    private fun String.isUserFacingTime(): Boolean {
        if (isBlank()) return false
        val lower = lowercase()
        return lower != "operational" && lower != "check availability" && lower != "schedule pending"
    }

    private fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            requireContext(),
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
    }

    @Suppress("MissingPermission")
    private fun currentLocationIfAllowed(): Location? {
        if (!hasLocationPermission()) return null
        val locationManager = requireContext().getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        return listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
            .mapNotNull { provider ->
                runCatching {
                    if (locationManager?.isProviderEnabled(provider) == true) {
                        locationManager.getLastKnownLocation(provider)
                    } else {
                        null
                    }
                }.getOrNull()
            }
            .maxByOrNull { it.time }
    }

    // Adds one discovery card to a list.
    private fun addCard(parent: android.widget.LinearLayout, item: DiscoveryItem) {
        DiscoveryCardRenderer.addItemCard(
            parent = parent,
            item = item,
            explanation = DiscoveryRepository.explanationFor(item),
            onOpen = { (requireActivity() as MainActivity).openDetail(it) },
            onSave = {
                DiscoveryRepository.saveItem(
                    item = it,
                    onSuccess = {
                        if (_binding != null) {
                            Toast.makeText(requireContext(), R.string.discover_saved, Toast.LENGTH_SHORT).show()
                            render()
                        }
                    },
                    onFailure = { error ->
                        if (_binding != null) {
                            Toast.makeText(requireContext(), error, Toast.LENGTH_LONG).show()
                        }
                    }
                )
            },
            cardStyle = DiscoveryCardRenderer.CardStyle.DISCOVER_LIGHT
        )
    }

    private fun loadWideMapStudios() {
        if (mapWideStudiosLoaded) return
        mapWideStudiosLoaded = true
        loadExternalStudios(
            location = null,
            queryOverride = "dance studio",
            cityOverride = "Israel",
            force = true
        )
    }

    // Returns the currently relevant Discover items without tracking a new search event.
    private fun currentDiscoverItems(): List<DiscoveryItem> {
        val filterableMode = discoverMode == DiscoverMode.SEARCH || discoverMode == DiscoverMode.MAP
        val query = if (filterableMode) binding.discoverEDTSearch.text.toString() else ""
        val style = if (filterableMode) binding.discoverEDTStyle.text.toString() else ""
        val level = if (filterableMode) binding.discoverEDTLevel.text.toString() else ""
        val location = if (filterableMode) {
            selectedOptionalCity(binding.discoverEDTLocation.text.toString())
        } else {
            ""
        }
        val sourceItems = if (discoverMode == DiscoverMode.MAP && mapRecommendedOnly) {
            DiscoveryRepository.recommendedItems()
        } else {
            DiscoveryRepository.filterDiscoverItems(query, style, level, location)
        }
        return sourceItems
            .filter {
                !filterableMode ||
                    (
                        (query.isBlank() || it.matchesMapText(query)) &&
                            (style.isBlank() || it.style.contains(style, ignoreCase = true)) &&
                            (level.isBlank() || it.level.contains(level, ignoreCase = true) || it.level.equals("All levels", ignoreCase = true)) &&
                            (location.isBlank() || it.location.contains(location, ignoreCase = true) || it.address.contains(location, ignoreCase = true))
                        )
            }
            .filter { discoverMode != DiscoverMode.MAP || it.isActivity() || it.isStudioLike() }
            .uniqueForDiscover()
    }

    private fun DiscoveryItem.matchesMapText(query: String): Boolean {
        return title.contains(query, ignoreCase = true) ||
            studio.contains(query, ignoreCase = true) ||
            teacher.contains(query, ignoreCase = true) ||
            address.contains(query, ignoreCase = true) ||
            location.contains(query, ignoreCase = true) ||
            type.contains(query, ignoreCase = true)
    }

    // Draws grouped map markers for studios and professional activities.
    private fun renderMapMarkers(items: List<DiscoveryItem>) {
        val map = googleMap ?: return
        map.clear()

        val mappedItems = items.mapNotNull { item ->
            val lat = item.latitude ?: return@mapNotNull null
            val lng = item.longitude ?: return@mapNotNull null
            item to LatLng(lat, lng)
        }
        if (mappedItems.isEmpty()) {
            map.moveCamera(CameraUpdateFactory.newLatLngZoom(DEFAULT_MAP_CENTER, DEFAULT_MAP_ZOOM))
            return
        }

        val grouped = mappedItems.groupBy { (_, position) ->
            "${"%.4f".format(position.latitude)}|${"%.4f".format(position.longitude)}"
        }
        val boundsBuilder = LatLngBounds.Builder()
        grouped.values.forEach { group ->
            val representative = group.first().first
            val position = group.first().second
            boundsBuilder.include(position)
            val title = if (group.size == 1) {
                representative.title
            } else {
                getString(R.string.discover_grouped_map_title, group.size)
            }
            val snippet = if (group.size == 1) {
                listOf(representative.studio, representative.style, representative.time)
                    .filter { it.isNotBlank() }
                    .joinToString(" / ")
            } else {
                group.take(3).joinToString(" / ") { it.first.title }
            }
            map.addMarker(
                MarkerOptions()
                    .position(position)
                    .title(title)
                    .snippet(snippet)
                    .icon(BitmapDescriptorFactory.defaultMarker(markerHueFor(representative)))
            )?.tag = representative
        }

        binding.discoverLAYMap.post {
            if (grouped.size == 1) {
                map.animateCamera(CameraUpdateFactory.newLatLngZoom(mappedItems.first().second, DEFAULT_MAP_ZOOM))
            } else {
                map.animateCamera(CameraUpdateFactory.newLatLngBounds(boundsBuilder.build(), MAP_BOUNDS_PADDING))
            }
        }
    }

    // Chooses marker colors by result type and source.
    private fun markerHueFor(item: DiscoveryItem): Float {
        if (item.source == DiscoveryItem.SOURCE_GOOGLE) return BitmapDescriptorFactory.HUE_AZURE
        return when (item.type.lowercase()) {
            "class" -> BitmapDescriptorFactory.HUE_VIOLET
            "workshop" -> BitmapDescriptorFactory.HUE_ORANGE
            "event" -> BitmapDescriptorFactory.HUE_ROSE
            else -> BitmapDescriptorFactory.HUE_MAGENTA
        }
    }

    // Checks whether the user entered any Discover search filters.
    private fun hasActiveFilters(): Boolean {
        return binding.discoverEDTSearch.text.toString().isNotBlank() ||
            binding.discoverEDTStyle.text.toString().isNotBlank() ||
            binding.discoverEDTLevel.text.toString().isNotBlank() ||
            binding.discoverEDTLocation.text.toString().isNotBlank()
    }

    private fun hasActiveSearchFields(): Boolean {
        return hasActiveFilters() || binding.discoverEDTTime.text.toString().isNotBlank()
    }

    // Returns a normalized city filter or raw text when the city is not in the dropdown.
    private fun selectedOptionalCity(value: String): String {
        return CityOptions.normalizeOptionalCity(value) ?: value.trim()
    }

    // Creates a simple message row for empty search results.
    private fun emptyText(message: String): android.widget.TextView {
        return android.widget.TextView(requireContext()).apply {
            text = message
            setTextColor(requireContext().getColor(com.ana.theflow.R.color.discover_text_muted))
            textSize = 14f
            setPadding(0, 18, 0, 0)
        }
    }

    // Clears the fragment binding when the view is destroyed.
    override fun onDestroyView() {
        super.onDestroyView()
        googleMap = null
        _binding = null
    }

    private enum class DiscoverMode(val argument: String) {
        PERSONAL("personal"),
        MAP("map"),
        SEARCH("search");

        companion object {
            fun fromArgument(value: String?): DiscoverMode {
                return values().firstOrNull { it.argument == value } ?: PERSONAL
            }
        }
    }

    private fun sectionText(title: String): android.widget.TextView {
        return android.widget.TextView(requireContext()).apply {
            text = title
            setTextColor(requireContext().getColor(R.color.discover_ink))
            textSize = 16f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setPadding(0, 18.dp(), 0, 0)
        }
    }

    private enum class SearchMode {
        ALL,
        ACTIVITIES,
        STUDIOS,
        PEOPLE
    }

    companion object {
        private const val ARG_MODE = "ARG_MODE"
        private const val SEARCH_FEEDBACK_DELAY_MS = 250L
        private val DEFAULT_MAP_CENTER = LatLng(32.0853, 34.7818)
        private const val DEFAULT_MAP_ZOOM = 11f
        private const val MAP_BOUNDS_PADDING = 80

        // Opens Discover directly in search mode for old search entry points.
        fun newSearchInstance(): DiscoverFragment {
            return DiscoverFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_MODE, DiscoverMode.SEARCH.argument)
                }
            }
        }

        private val LEVEL_OPTIONS = listOf(
            "Beginner",
            "Intermediate",
            "Advanced",
            "All levels"
        )
        private val DANCE_STYLE_OPTIONS = listOf(
            "Hip Hop",
            "Contemporary",
            "Jazz",
            "Ballet",
            "Heels",
            "Salsa",
            "Afro",
            "Modern",
            "House",
            "Dancehall",
            "Latin",
            "Tap",
            "Ballroom",
            "Breakdance",
            "Waacking",
            "Popping",
            "Locking",
            "K-pop",
            "Commercial"
        )
    }

    // Configures the Google Map and routes marker taps to the existing detail screen.
    override fun onMapReady(map: GoogleMap) {
        googleMap = map.apply {
            uiSettings.isZoomControlsEnabled = true
            uiSettings.isMapToolbarEnabled = true
            setOnInfoWindowClickListener { marker ->
                (marker.tag as? DiscoveryItem)?.let { item ->
                    (requireActivity() as MainActivity).openDetail(item)
                }
            }
        }
        renderMapMarkers(currentDiscoverItems())
    }

    // Loads published professional activities into Discover without blocking studio results.
    private fun loadPublishedActivities() {
        DiscoveryRepository.loadPublishedActivities(
            onSuccess = {
                if (_binding != null) render()
            },
            onFailure = {
                if (_binding != null) render()
            }
        )
    }

    // Connects the high-level Discover modes.
    private fun setupModeTabs() {
        binding.discoverTABForYou.setOnClickListener {
            discoverMode = DiscoverMode.PERSONAL
            isFiltering = false
            render()
        }
        binding.discoverTABMap.setOnClickListener {
            discoverMode = DiscoverMode.MAP
            isFiltering = false
            loadWideMapStudios()
            render()
        }
        binding.discoverTABSearch.setOnClickListener {
            discoverMode = DiscoverMode.SEARCH
            render()
        }
    }
}

private fun Int.dp(): Int {
    return (this * android.content.res.Resources.getSystem().displayMetrics.density).toInt()
}
