package com.ana.theflow.ui.search

import android.Manifest
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextUtils
import android.text.TextWatcher
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.ana.theflow.MainActivity
import com.ana.theflow.R
import com.ana.theflow.data.model.discovery.DiscoveryItem
import com.ana.theflow.data.model.post.Post
import com.ana.theflow.data.model.user.User
import com.ana.theflow.data.repository.ActivityTrackingRepository
import com.ana.theflow.data.repository.DiscoveryRepository
import com.ana.theflow.data.repository.PostRepository
import com.ana.theflow.data.repository.UserRepository
import com.ana.theflow.databinding.FragmentSearchBinding
import com.ana.theflow.ui.common.GooglePlacePhotoLoader
import com.ana.theflow.ui.common.PostCardRenderer
import com.ana.theflow.ui.common.ResponsiveLayout
import com.ana.theflow.ui.common.UiText
import com.ana.theflow.utilities.CityOptions
import com.bumptech.glide.Glide
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.MarkerOptions

class SearchFragment : Fragment(), OnMapReadyCallback {

    private var _binding: FragmentSearchBinding? = null
    private val binding get() = _binding!!
    private val viewModel: DiscoverSearchViewModel by viewModels()
    private val userRepository = UserRepository()
    private val postRepository = PostRepository()
    private val activityTrackingRepository = ActivityTrackingRepository()
    private val debounceHandler = Handler(Looper.getMainLooper())
    private var googleMap: GoogleMap? = null
    private var lastExternalSearchKey = ""
    private var lastRenderedQuery = ""
    private var mapCameraDirty = false
    private var mapSearchLocationOverride: Location? = null
    private var suppressQueryWatcher = false

    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        if (grants.values.any { it }) {
            centerOnCurrentLocation(force = true)
            loadSearchResults(forceExternal = true)
        } else {
            viewModel.state.locationMessage = "Location permission denied. Showing results by saved city when available."
            render()
            loadSearchResults(forceExternal = true)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (arguments?.getBoolean(ARG_MAP_MODE) == true) {
            viewModel.state.viewMode = SearchViewMode.MAP
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentSearchBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        (childFragmentManager.findFragmentById(R.id.search_MAP_results) as? SupportMapFragment)?.getMapAsync(this)
        setupControls()
        restoreQueryText()
        mapCameraDirty = viewModel.state.userMovedMap
        ResponsiveLayout.constrainToReadableWidth(
            binding.searchLAYTop,
            binding.searchSCROLLCategories,
            binding.searchLAYControls,
            binding.searchLBLResultSummary,
            binding.searchSCROLLResults,
            binding.searchLAYMap
        )
        ResponsiveLayout.ensureTouchTarget(binding.searchBTNBack, binding.searchBTNClear, binding.searchBTNCurrentLocation)
        setupCategoryChips()
        setupMapChips()
        if (viewModel.state.hasLoadedRepositoryData) {
            render()
            binding.searchSCROLLResults.post { binding.searchSCROLLResults.scrollTo(0, viewModel.state.scrollY) }
            if (isSearchDataStale()) loadInitialData(background = true)
        } else {
            loadInitialData(background = false)
        }
        render()
        if (viewModel.state.query.isBlank()) binding.searchEDTQuery.requestFocus()
    }

    private fun restoreQueryText() {
        suppressQueryWatcher = true
        binding.searchEDTQuery.setText(viewModel.state.query)
        binding.searchEDTQuery.setSelection(binding.searchEDTQuery.text?.length ?: 0)
        suppressQueryWatcher = false
    }

    private fun isSearchDataStale(): Boolean {
        val loadedAt = viewModel.state.lastLoadedAtMillis
        return loadedAt == 0L || System.currentTimeMillis() - loadedAt > SEARCH_STALE_AFTER_MS
    }

    private fun setupControls() {
        binding.searchBTNBack.setOnClickListener { parentFragmentManager.popBackStack() }
        binding.searchBTNClear.setOnClickListener {
            binding.searchEDTQuery.text?.clear()
            viewModel.state.query = ""
            scheduleSearch()
        }
        binding.searchBTNFilter.setOnClickListener { showFiltersSheet() }
        binding.searchCHIPList.setOnClickListener {
            viewModel.state.viewMode = SearchViewMode.LIST
            render()
        }
        binding.searchCHIPMap.setOnClickListener {
            viewModel.state.viewMode = SearchViewMode.MAP
            render()
        }
        binding.searchBTNSearchArea.setOnClickListener {
            mapSearchLocationOverride = googleMap?.cameraPosition?.target?.let { target ->
                Location("map_camera").apply {
                    latitude = target.latitude
                    longitude = target.longitude
                }
            }
            mapCameraDirty = false
            binding.searchBTNSearchArea.visibility = View.GONE
            loadSearchResults(forceExternal = true)
        }
        binding.searchBTNCurrentLocation.setOnClickListener { requestLocationIfNeeded() }
        binding.searchBTNClearMarkers.setOnClickListener {
            viewModel.state.filters = SearchFilters()
            setupMapChips()
            loadSearchResults(forceExternal = true)
            Toast.makeText(requireContext(), "Filters cleared", Toast.LENGTH_SHORT).show()
            render()
        }
        binding.searchEDTQuery.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                loadSearchResults(forceExternal = true)
                true
            } else {
                false
            }
        }
        binding.searchEDTQuery.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                if (suppressQueryWatcher) return
                viewModel.state.query = s?.toString().orEmpty()
                scheduleSearch()
            }
        })
    }

    private fun setupCategoryChips() {
        binding.searchLAYCategories.removeAllViews()
        SearchCategory.values().forEach { category ->
            binding.searchLAYCategories.addView(chip(category.label, selected = viewModel.state.selectedCategory == category) {
                viewModel.state.selectedCategory = category
                if (category == SearchCategory.PEOPLE) {
                    viewModel.state.viewMode = SearchViewMode.LIST
                    binding.searchBTNSearchArea.visibility = View.GONE
                    binding.searchLAYMarkerPreview.visibility = View.GONE
                }
                setupCategoryChips()
                loadSearchResults(forceExternal = category == SearchCategory.STUDIOS || category == SearchCategory.ALL)
            })
        }
    }

    private fun setupMapChips() {
        binding.searchLAYMapChips.removeAllViews()
        listOf("Style", "Level", "Location", "Distance", "Date", "Time", "Type", "More Filters").forEach { label ->
            binding.searchLAYMapChips.addView(chip(mapChipLabel(label), selected = isMapChipSelected(label)) { showFilterSelection(label) })
        }
    }

    private fun loadInitialData(background: Boolean) {
        if (viewModel.state.isBackgroundRefreshing) return
        viewModel.state.isBackgroundRefreshing = background
        viewModel.state.isLoading = !background || viewModel.state.results.isEmpty()
        render()
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
                if (viewModel.state.viewMode == SearchViewMode.MAP) requestInitialLocation()
                loadRepositoryData(background)
            },
            onFailure = {
                if (viewModel.state.viewMode == SearchViewMode.MAP) requestInitialLocation()
                loadRepositoryData(background)
            }
        )
    }

    private fun loadRepositoryData(background: Boolean) {
        DiscoveryRepository.loadSavedItems(onSuccess = {}, onFailure = {})
        DiscoveryRepository.loadApprovedStudios(
            onSuccess = {
                DiscoveryRepository.loadPublishedActivities(
                    onSuccess = {
                        viewModel.state.hasLoadedRepositoryData = true
                        viewModel.state.lastLoadedAtMillis = System.currentTimeMillis()
                        viewModel.state.isBackgroundRefreshing = false
                        loadSearchResults(forceExternal = true, keepContentVisible = background)
                    },
                    onFailure = {
                        viewModel.state.hasLoadedRepositoryData = true
                        viewModel.state.lastLoadedAtMillis = System.currentTimeMillis()
                        viewModel.state.isBackgroundRefreshing = false
                        loadSearchResults(forceExternal = true, keepContentVisible = background)
                    }
                )
            },
            onFailure = { error ->
                viewModel.state.error = error
                viewModel.state.hasLoadedRepositoryData = true
                viewModel.state.isBackgroundRefreshing = false
                loadSearchResults(forceExternal = true, keepContentVisible = background)
            }
        )
    }

    private fun scheduleSearch() {
        debounceHandler.removeCallbacksAndMessages(null)
        debounceHandler.postDelayed({ loadSearchResults(forceExternal = false) }, SEARCH_DEBOUNCE_MS)
    }

    private fun loadSearchResults(forceExternal: Boolean, keepContentVisible: Boolean = false) {
        if (_binding == null) return
        val state = viewModel.state
        val requestId = ++state.searchRequestId
        val filters = state.filters
        val query = state.query.trim()
        state.isLoading = !keepContentVisible && state.results.isEmpty() && state.peopleResults.isEmpty() && state.postResults.isEmpty()
        state.error = ""
        render()

        val internal = DiscoveryRepository.search(
            style = filters.primaryStyle().takeIf { filters.styles.size <= 1 }.orEmpty(),
            level = filters.level.takeIf { filters.levels.isEmpty() }.orEmpty(),
            location = (CityOptions.normalizeOptionalCity(filters.location) ?: filters.location).takeIf { filters.locations.isEmpty() }.orEmpty(),
            teacher = if (state.selectedCategory == SearchCategory.PEOPLE) query else "",
            studio = if (state.selectedCategory == SearchCategory.STUDIOS) query else "",
            time = listOf(filters.date.takeIf { filters.dates.isEmpty() }, filters.time.takeIf { filters.times.isEmpty() }).filterNotNull().filter { it.isNotBlank() }.joinToString(" ")
        )
            .filterByQuery(query)
            .filterByCategory(state.selectedCategory)
            .filterByExtraFilters(filters)
            .unique()

        state.results = internal
        state.peopleResults = emptyList()
        state.postResults = emptyList()
        val needsAsyncResults = query.isNotBlank() &&
            state.selectedCategory in listOf(SearchCategory.ALL, SearchCategory.PEOPLE, SearchCategory.EVENTS, SearchCategory.CLASSES)
        state.isLoading = needsAsyncResults && internal.isEmpty()
        if (query.isNotBlank() && query != lastRenderedQuery) {
            lastRenderedQuery = query
            state.recentSearches.remove(query)
            state.recentSearches.add(0, query)
            while (state.recentSearches.size > 5) state.recentSearches.removeLast()
            activityTrackingRepository.trackSearch(
                query = query,
                danceStyles = filters.selectedStyles(),
                location = filters.location
            )
        }
        render()
        loadPostAndUserResults(requestId)

        val shouldLoadExternal = forceExternal ||
            (state.selectedCategory == SearchCategory.ALL || state.selectedCategory == SearchCategory.STUDIOS) &&
            (state.viewMode == SearchViewMode.MAP || listOf(query, filters.location, filters.primaryStyle()).any { it.isNotBlank() })
        if (shouldLoadExternal) loadExternalResults(query, filters.location, requestId)
    }

    private fun loadExternalResults(query: String, city: String, requestId: Long) {
        val searchLocation = mapSearchLocationOverride ?: currentLocationIfAllowed() ?: if (viewModel.state.viewMode == SearchViewMode.MAP) fallbackMapCenter().toLocation() else null
        val locationKey = searchLocation?.let { "${"%.3f".format(it.latitude)},${"%.3f".format(it.longitude)}" }.orEmpty()
        val key = "${query.trim()}|${city.trim()}|${viewModel.state.filters.selectedStyles().joinToString(",")}|$locationKey"
        if (key == lastExternalSearchKey) return
        lastExternalSearchKey = key
        DiscoveryRepository.loadExternalStudios(
            context = requireContext(),
            query = listOf(query, viewModel.state.filters.primaryStyle()).filter { it.isNotBlank() }.joinToString(" "),
            city = city,
            location = searchLocation,
            usePreferredCityFallback = city.isNotBlank() || viewModel.state.viewMode != SearchViewMode.MAP,
            onSuccess = {
                if (_binding == null) return@loadExternalStudios
                if (requestId != viewModel.state.searchRequestId) return@loadExternalStudios
                viewModel.state.results = DiscoveryRepository.filterDiscoverItems(
                    query = query,
                    style = viewModel.state.filters.primaryStyle(),
                    level = viewModel.state.filters.level,
                    location = city
                ).filterByCategory(viewModel.state.selectedCategory).filterByExtraFilters(viewModel.state.filters).unique()
                    .mapCapableForMapIfNeeded()
                render()
            },
            onFailure = { error ->
                if (_binding == null) return@loadExternalStudios
                if (requestId != viewModel.state.searchRequestId) return@loadExternalStudios
                viewModel.state.error = error
                render()
            }
        )
    }

    private fun loadPostAndUserResults(requestId: Long) {
        val query = viewModel.state.query.trim()
        val category = viewModel.state.selectedCategory
        if (query.isBlank() && category != SearchCategory.PEOPLE) return
        if (category in listOf(SearchCategory.ALL, SearchCategory.EVENTS, SearchCategory.CLASSES)) {
            postRepository.searchPosts(
                query = query,
                onSuccess = { posts ->
                    if (_binding == null) return@searchPosts
                    if (requestId != viewModel.state.searchRequestId) return@searchPosts
                    viewModel.state.postResults = posts.filterBySearchCategory(viewModel.state.selectedCategory)
                    viewModel.state.isLoading = false
                    if (viewModel.state.viewMode == SearchViewMode.LIST) render()
                },
                onFailure = {
                    if (_binding == null) return@searchPosts
                    if (requestId != viewModel.state.searchRequestId) return@searchPosts
                    viewModel.state.isLoading = false
                    render()
                }
            )
        }
        if (category in listOf(SearchCategory.ALL, SearchCategory.PEOPLE)) {
            userRepository.searchUsers(
                query = query,
                dancersOnly = false,
                onSuccess = { users ->
                    if (_binding == null) return@searchUsers
                    if (requestId != viewModel.state.searchRequestId) return@searchUsers
                    viewModel.state.peopleResults = users.filterByPeopleCategory(viewModel.state.selectedCategory)
                    viewModel.state.isLoading = false
                    if (viewModel.state.viewMode == SearchViewMode.LIST) render()
                },
                onFailure = {
                    if (_binding == null) return@searchUsers
                    if (requestId != viewModel.state.searchRequestId) return@searchUsers
                    viewModel.state.isLoading = false
                    render()
                }
            )
        }
    }

    private fun render() {
        if (_binding == null) return
        val state = viewModel.state
        binding.searchPRGLoading.visibility = if (state.isLoading) View.VISIBLE else View.GONE
        binding.searchBTNFilter.text = activeFilterLabel()
        renderViewMode()
        binding.searchLBLResultSummary.text = summaryText()
        binding.searchLBLGoogleAttribution.visibility =
            if (state.results.any { it.source == DiscoveryItem.SOURCE_GOOGLE }) View.VISIBLE else View.GONE
        binding.searchLAYResults.removeAllViews()
        binding.searchLAYMarkerPreview.visibility = View.GONE
        if (state.viewMode == SearchViewMode.LIST) renderList()
        if (state.selectedCategory == SearchCategory.PEOPLE) {
            googleMap?.clear()
        } else {
            renderMapMarkers(state.results.mapCapableForMapIfNeeded())
        }
    }

    private fun renderViewMode() {
        val peopleMode = viewModel.state.selectedCategory == SearchCategory.PEOPLE
        if (peopleMode && viewModel.state.viewMode != SearchViewMode.LIST) {
            viewModel.state.viewMode = SearchViewMode.LIST
        }
        val listMode = viewModel.state.viewMode == SearchViewMode.LIST
        binding.searchSCROLLResults.visibility = if (listMode) View.VISIBLE else View.GONE
        binding.searchLAYMap.visibility = if (listMode) View.GONE else View.VISIBLE
        binding.searchCHIPList.visibility = if (peopleMode) View.GONE else View.VISIBLE
        binding.searchCHIPMap.visibility = if (peopleMode) View.GONE else View.VISIBLE
        binding.searchCHIPList.setBackgroundResource(if (listMode) R.drawable.bg_discover_segment_active else R.drawable.bg_discover_segment_inactive)
        binding.searchCHIPList.setTextColor(requireContext().getColor(if (listMode) R.color.white else R.color.discover_text_secondary))
        binding.searchCHIPMap.setBackgroundResource(if (!listMode) R.drawable.bg_discover_segment_active else R.drawable.bg_discover_segment_inactive)
        binding.searchCHIPMap.setTextColor(requireContext().getColor(if (!listMode) R.color.white else R.color.discover_text_secondary))
    }

    private fun renderList() {
        val results = viewModel.state.results
        if (viewModel.state.error.isNotBlank()) {
            binding.searchLAYResults.addView(messageCard(UiText.friendlyError(viewModel.state.error, "Search is unavailable right now.")))
        }
        if (viewModel.state.locationMessage.isNotBlank()) {
            binding.searchLAYResults.addView(messageCard(viewModel.state.locationMessage))
        }
        if (results.isEmpty() && viewModel.state.peopleResults.isEmpty() && viewModel.state.postResults.isEmpty() && !viewModel.state.isLoading) {
            renderEmptySearch()
            return
        }
        val studios = results.filter { it.isStudioLike() }
        val classes = results.filter { it.isType("class") || it.isType("workshop") }
        val events = results.filter { it.isType("event") }
        if (viewModel.state.peopleResults.isNotEmpty()) {
            binding.searchLAYResults.addView(sectionTitle("People"))
            viewModel.state.peopleResults.take(12).forEach { addUserRow(it) }
        }
        if (studios.isNotEmpty()) {
            binding.searchLAYResults.addView(sectionTitle("Studios"))
            studios.forEach { addCompactResultCard(it) }
        }
        if (classes.isNotEmpty()) {
            binding.searchLAYResults.addView(sectionTitle("Classes"))
            classes.forEach { addCompactResultCard(it) }
        }
        if (events.isNotEmpty()) {
            binding.searchLAYResults.addView(sectionTitle("Events"))
            events.forEach { addCompactResultCard(it) }
        }
        if (viewModel.state.postResults.isNotEmpty()) {
            binding.searchLAYResults.addView(sectionTitle("Posts and events"))
            viewModel.state.postResults.take(8).forEach { addPostCard(it) }
        }
    }

    private fun renderEmptySearch() {
        val state = viewModel.state
        if (state.query.isBlank() && state.activeFiltersBlank()) {
            binding.searchLAYResults.addView(sectionTitle("Recent searches"))
            if (state.recentSearches.isEmpty()) {
                binding.searchLAYResults.addView(messageCard("Start with a style, studio, teacher, event, or city."))
            } else {
                state.recentSearches.forEach { recent ->
                    binding.searchLAYResults.addView(chip(recent, selected = false) {
                        binding.searchEDTQuery.setText(recent)
                        binding.searchEDTQuery.setSelection(recent.length)
                    })
                }
            }
            binding.searchLAYResults.addView(sectionTitle("Suggested categories"))
            binding.searchLAYResults.addView(messageCard("Try Studios, Classes, Events, Teachers, Dancers, or Workshops."))
        } else {
            binding.searchLAYResults.addView(messageCard("No results match these filters.\nClear filters, increase distance, or search another area."))
            binding.searchLAYResults.addView(actionButton("Clear Filters") {
                viewModel.state.filters = SearchFilters()
                setupMapChips()
                loadSearchResults(forceExternal = true)
            })
        }
    }

    private fun addCompactResultCard(item: DiscoveryItem) {
        val context = requireContext()
        val card = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundResource(R.drawable.bg_discover_card)
            setPadding(10.dp(), 10.dp(), 10.dp(), 10.dp())
            isClickable = true
            isFocusable = true
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 10.dp() }
            setOnClickListener { showResultDetails(item) }
        }
        val imageWrap = FrameLayout(context).apply {
            setBackgroundResource(R.drawable.bg_flow_media)
            layoutParams = LinearLayout.LayoutParams(86.dp(), 86.dp()).apply { rightMargin = 12.dp() }
        }
        val image = ImageView(context).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
        }
        val placeholder = TextView(context).apply {
            text = item.typeLabel()
            gravity = Gravity.CENTER
            setTextColor(context.getColor(R.color.discover_purple))
            textSize = 11f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
        }
        imageWrap.addView(image)
        imageWrap.addView(placeholder)
        when {
            item.coverImageUrl.isNotBlank() -> {
                placeholder.visibility = View.GONE
                Glide.with(context).load(item.coverImageUrl).centerCrop().into(image)
            }
            item.source == DiscoveryItem.SOURCE_GOOGLE -> GooglePlacePhotoLoader.load(context, item.googlePlaceId, image, TextView(context))
        }
        card.addView(imageWrap)
        card.addView(LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            addView(TextView(context).apply {
                text = item.title
                maxLines = 2
                ellipsize = TextUtils.TruncateAt.END
                setTextColor(context.getColor(R.color.discover_ink))
                textSize = 16f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
            })
            addView(TextView(context).apply {
                text = item.compactMeta()
                maxLines = 2
                ellipsize = TextUtils.TruncateAt.END
                setTextColor(context.getColor(R.color.discover_text_secondary))
                textSize = 12f
                setPadding(0, 4.dp(), 0, 0)
            })
            addView(TextView(context).apply {
                text = if (item.source == DiscoveryItem.SOURCE_GOOGLE) "Google Places" else DiscoveryRepository.explanationFor(item).naturalReason()
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                setTextColor(context.getColor(if (item.source == DiscoveryItem.SOURCE_GOOGLE) R.color.discover_google else R.color.discover_purple))
                textSize = 12f
                setPadding(0, 6.dp(), 0, 0)
            })
        })
        card.addView(ImageButton(context).apply {
            contentDescription = getString(R.string.discover_save_item)
            setImageResource(R.drawable.ic_bookmark_24)
            setColorFilter(context.getColor(if (DiscoveryRepository.isSaved(item)) R.color.discover_purple else R.color.discover_text_muted))
            setBackgroundResource(R.drawable.bg_discover_icon_button)
            setOnClickListener {
                DiscoveryRepository.saveItem(
                    item = item,
                    onSuccess = {
                        if (_binding != null) {
                            Toast.makeText(requireContext(), R.string.discover_saved, Toast.LENGTH_SHORT).show()
                            render()
                        }
                    },
                    onFailure = { error -> if (_binding != null) Toast.makeText(requireContext(), UiText.friendlyError(error, "We could not save this item."), Toast.LENGTH_LONG).show() }
                )
            }
            layoutParams = LinearLayout.LayoutParams(44.dp(), 44.dp()).apply { leftMargin = 8.dp() }
        })
        binding.searchLAYResults.addView(card)
    }

    private fun showResultDetails(item: DiscoveryItem) {
        val context = requireContext()
        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(18.dp(), 18.dp(), 18.dp(), 14.dp())
            addView(TextView(context).apply {
                text = item.title
                setTextColor(context.getColor(R.color.discover_ink))
                textSize = 20f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
            })
            addView(TextView(context).apply {
                text = item.compactMeta()
                setTextColor(context.getColor(R.color.discover_text_secondary))
                textSize = 13f
                setPadding(0, 8.dp(), 0, 8.dp())
            })
        }
        val dialog = AlertDialog.Builder(context).setView(content).create()
        content.addView(actionButton("View Details") {
            dialog.dismiss()
            (requireActivity() as MainActivity).openDetail(item)
        })
        if (item.googleMapsUrl.isNotBlank()) {
            content.addView(actionButton("Open in Google Maps") {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(item.googleMapsUrl)))
            })
        }
        content.addView(actionButton("Save") {
            DiscoveryRepository.saveItem(item, onSuccess = { dialog.dismiss(); render() }, onFailure = {})
        })
        content.addView(actionButton("Share") { shareItem(item) })
        dialog.setOnShowListener { positionDialogBottom(dialog.window) }
        dialog.show()
    }

    private fun showFilterSelection(focus: String) {
        if (focus == "More Filters") {
            showFiltersSheet()
            return
        }
        val options = when (focus) {
            "Style" -> listOf("Hip Hop", "Heels", "Contemporary", "Ballet", "Jazz", "Salsa", "Bachata")
            "Level" -> listOf("Beginner", "Intermediate", "Advanced", "Open Level", "All levels")
            "Location" -> listOf("Current Location", "Saved City", "Tel Aviv", "Jerusalem", "Haifa", "Ramat Gan")
            "Distance" -> listOf("1 km", "3 km", "5 km", "10 km", "20 km")
            "Date" -> listOf("Today", "Tomorrow", "This Week", "This Weekend")
            "Time" -> listOf("Morning", "Afternoon", "Evening")
            "Type" -> listOf("Studio", "Class", "Event", "Workshop")
            else -> emptyList()
        }
        showOptionSheet(focus, options)
    }

    private fun showOptionSheet(filterName: String, options: List<String>) {
        val context = requireContext()
        lateinit var dialog: AlertDialog
        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(18.dp(), 18.dp(), 18.dp(), 14.dp())
            addView(TextView(context).apply {
                text = filterName
                setTextColor(context.getColor(R.color.discover_ink))
                textSize = 20f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
            })
        }
        val optionsContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 12.dp(), 0, 0)
        }
        fun renderOptions() {
            optionsContainer.removeAllViews()
            options.forEach { option ->
                optionsContainer.addView(optionRow(option, selected = isOptionSelected(filterName, option)) {
                    toggleOrApplyFilterValue(filterName, option)
                    setupMapChips()
                    renderOptions()
                })
            }
        }
        renderOptions()
        content.addView(optionsContainer)
        content.addView(LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 12.dp(), 0, 0)
            addView(actionButton("Clear Selection", weight = 1f) {
                applyFilterValue(filterName, "")
                setupMapChips()
                loadSearchResults(forceExternal = true)
                dialog.dismiss()
            })
            addView(actionButton("Apply", weight = 1f) {
                loadSearchResults(forceExternal = true)
                dialog.dismiss()
            })
        })
        dialog = AlertDialog.Builder(context).setView(ScrollView(context).apply { addView(content) }).create()
        dialog.setOnShowListener { positionDialogBottom(dialog.window) }
        dialog.show()
    }

    private fun showFiltersSheet(focus: String = "") {
        val context = requireContext()
        val filters = viewModel.state.filters
        lateinit var dialog: AlertDialog
        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(18.dp(), 18.dp(), 18.dp(), 14.dp())
            addView(TextView(context).apply {
                text = "Filters"
                setTextColor(context.getColor(R.color.discover_ink))
                textSize = 20f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
            })
        }
        val style = filterField("Dance style", filters.style)
        val level = filterField("Level", filters.level)
        val location = filterField("Location", filters.location)
        val distance = filterField("Distance radius", filters.distance)
        val date = filterField("Date", filters.date)
        val time = filterField("Time", filters.time)
        val type = filterField("Content type", filters.contentType)
        val rating = filterField("Rating", filters.rating)
        val price = filterField("Price range", filters.price)
        listOf(style, level, location, distance, date, time, type, rating, price).forEach { content.addView(it) }
        val freeOnly = CheckBox(context).apply { text = "Free only"; isChecked = filters.freeOnly; setTextColor(context.getColor(R.color.discover_ink)) }
        val openNow = CheckBox(context).apply { text = "Open now"; isChecked = filters.openNow; setTextColor(context.getColor(R.color.discover_ink)) }
        content.addView(freeOnly)
        content.addView(openNow)
        content.addView(LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 12.dp(), 0, 0)
            addView(actionButton("Clear All", weight = 1f) {
                viewModel.state.filters = SearchFilters()
                setupMapChips()
                loadSearchResults(forceExternal = true)
                dialog.dismiss()
            })
            addView(actionButton("Show Results", weight = 1f) {
                filters.style = style.text.toString().trim()
                if (filters.style.isNotBlank()) filters.styles.clear()
                filters.level = level.text.toString().trim()
                if (filters.level.isNotBlank()) filters.levels.clear()
                filters.location = location.text.toString().trim()
                if (filters.location.isNotBlank()) filters.locations.clear()
                filters.distance = distance.text.toString().trim()
                filters.date = date.text.toString().trim()
                if (filters.date.isNotBlank()) filters.dates.clear()
                filters.time = time.text.toString().trim()
                if (filters.time.isNotBlank()) filters.times.clear()
                filters.contentType = type.text.toString().trim()
                if (filters.contentType.isNotBlank()) filters.contentTypes.clear()
                filters.rating = rating.text.toString().trim()
                filters.price = price.text.toString().trim()
                filters.freeOnly = freeOnly.isChecked
                filters.openNow = openNow.isChecked
                setupMapChips()
                loadSearchResults(forceExternal = true)
                dialog.dismiss()
            })
        })
        dialog = AlertDialog.Builder(context).setView(ScrollView(context).apply { addView(content) }).create()
        dialog.setOnShowListener {
            positionDialogBottom(dialog.window)
            when (focus) {
                "Style" -> style.requestFocus()
                "Level" -> level.requestFocus()
                "Location" -> location.requestFocus()
                "Distance" -> distance.requestFocus()
                "Date" -> date.requestFocus()
                "Time" -> time.requestFocus()
                "Type" -> type.requestFocus()
            }
        }
        dialog.show()
    }

    private fun positionDialogBottom(window: Window?) {
        window ?: return
        window.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT)
        window.setGravity(Gravity.BOTTOM)
    }

    private fun filterField(hint: String, value: String): EditText {
        return EditText(requireContext()).apply {
            this.hint = hint
            setText(value)
            setTextColor(context.getColor(R.color.discover_ink))
            setHintTextColor(context.getColor(R.color.discover_text_muted))
            setBackgroundResource(R.drawable.bg_discover_input)
            setSingleLine(true)
            setPadding(14.dp(), 0, 14.dp(), 0)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 48.dp()).apply { topMargin = 10.dp() }
        }
    }

    override fun onMapReady(map: GoogleMap) {
        googleMap = map.apply {
            uiSettings.isZoomControlsEnabled = true
            uiSettings.isMapToolbarEnabled = true
            uiSettings.isMyLocationButtonEnabled = false
            if (hasLocationPermission()) {
                runCatching { isMyLocationEnabled = true }
            }
            setOnCameraMoveStartedListener { reason ->
                if (reason == GoogleMap.OnCameraMoveStartedListener.REASON_GESTURE) {
                    mapCameraDirty = true
                    viewModel.state.userMovedMap = true
                    binding.searchBTNSearchArea.visibility = View.VISIBLE
                }
            }
            setOnCameraIdleListener {
                cameraPosition?.let { camera ->
                    viewModel.state.mapLatitude = camera.target.latitude
                    viewModel.state.mapLongitude = camera.target.longitude
                    viewModel.state.mapZoom = camera.zoom
                }
            }
            setOnMarkerClickListener { marker ->
                (marker.tag as? DiscoveryItem)?.let { item ->
                    viewModel.state.selectedMarkerItem = item
                    renderMarkerPreview(item)
                }
                true
            }
        }
        restoreMapCamera()
        renderMapMarkers(viewModel.state.results.mapCapableForMapIfNeeded())
        if (viewModel.state.viewMode == SearchViewMode.MAP && !viewModel.state.hasCenteredInitialMap) {
            requestInitialLocation()
        }
    }

    private fun restoreMapCamera() {
        val lat = viewModel.state.mapLatitude ?: return
        val lng = viewModel.state.mapLongitude ?: return
        val zoom = viewModel.state.mapZoom ?: DEFAULT_MAP_ZOOM
        googleMap?.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(lat, lng), zoom))
        viewModel.state.hasCenteredInitialMap = true
    }

    private fun renderMapMarkers(items: List<DiscoveryItem>) {
        val map = googleMap ?: return
        map.clear()
        val mapped = items.mapNotNull { item ->
            val lat = item.latitude ?: return@mapNotNull null
            val lng = item.longitude ?: return@mapNotNull null
            item to LatLng(lat, lng)
        }
        if (mapped.isEmpty()) {
            if (!viewModel.state.hasCenteredInitialMap && !mapCameraDirty) {
                map.moveCamera(CameraUpdateFactory.newLatLngZoom(fallbackMapCenter(), DEFAULT_MAP_ZOOM))
                viewModel.state.hasCenteredInitialMap = true
            }
            if (viewModel.state.viewMode == SearchViewMode.MAP && !viewModel.state.isLoading) {
                binding.searchLAYMarkerPreview.visibility = View.GONE
            }
            return
        }
        val bounds = LatLngBounds.Builder()
        mapped.groupBy { "${"%.4f".format(it.second.latitude)}|${"%.4f".format(it.second.longitude)}" }
            .values
            .forEach { group ->
                val item = group.first().first
                val position = group.first().second
                bounds.include(position)
                map.addMarker(
                    MarkerOptions()
                        .position(position)
                        .title(if (group.size > 1) "${group.size} nearby results" else item.title)
                        .snippet(item.compactMeta())
                        .icon(BitmapDescriptorFactory.defaultMarker(markerHue(item)))
                )?.tag = item
            }
        binding.searchLAYMap.post {
            if (!viewModel.state.hasCenteredInitialMap && !mapCameraDirty) {
                if (mapped.size == 1) map.animateCamera(CameraUpdateFactory.newLatLngZoom(mapped.first().second, DEFAULT_MAP_ZOOM))
                else map.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds.build(), 90))
                viewModel.state.hasCenteredInitialMap = true
            }
        }
    }

    private fun renderMarkerPreview(item: DiscoveryItem) {
        binding.searchLAYMarkerPreview.removeAllViews()
        binding.searchLAYMarkerPreview.visibility = View.VISIBLE
        binding.searchLAYMarkerPreview.addView(TextView(requireContext()).apply {
            text = item.title
            setTextColor(requireContext().getColor(R.color.discover_ink))
            textSize = 16f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        })
        binding.searchLAYMarkerPreview.addView(TextView(requireContext()).apply {
            text = item.compactMeta()
            setTextColor(requireContext().getColor(R.color.discover_text_secondary))
            textSize = 12f
            setPadding(0, 4.dp(), 0, 8.dp())
        })
        binding.searchLAYMarkerPreview.setOnClickListener { showResultDetails(item) }
    }

    private fun requestLocationIfNeeded() {
        if (hasLocationPermission()) {
            centerOnCurrentLocation(force = true)
        } else {
            locationPermissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION))
        }
    }

    private fun requestInitialLocation() {
        if (viewModel.state.hasCenteredInitialMap) return
        if (hasLocationPermission()) {
            centerOnCurrentLocation(force = false)
        } else {
            viewModel.state.locationMessage = "Location is off. You can allow location access, select a city, or continue with results across Israel."
            googleMap?.moveCamera(CameraUpdateFactory.newLatLngZoom(fallbackMapCenter(), DEFAULT_MAP_ZOOM))
            viewModel.state.hasCenteredInitialMap = true
            render()
        }
    }

    @Suppress("MissingPermission")
    private fun centerOnCurrentLocation(force: Boolean) {
        val location = currentLocationIfAllowed()
        if (location == null) {
            viewModel.state.locationMessage = "Current location is unavailable. Showing saved-city results when available."
            if (!viewModel.state.hasCenteredInitialMap && !mapCameraDirty) {
                googleMap?.animateCamera(CameraUpdateFactory.newLatLngZoom(fallbackMapCenter(), DEFAULT_MAP_ZOOM))
                viewModel.state.hasCenteredInitialMap = true
            }
            render()
            return
        }
        runCatching { googleMap?.isMyLocationEnabled = true }
        if (force || !mapCameraDirty) {
            googleMap?.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(location.latitude, location.longitude), 12f))
            viewModel.state.hasCenteredInitialMap = true
            mapCameraDirty = false
            binding.searchBTNSearchArea.visibility = View.GONE
        }
    }

    private fun currentLocationIfAllowed(): Location? {
        if (!hasLocationPermission()) return null
        val locationManager = requireContext().getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        return listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
            .mapNotNull { provider ->
                runCatching {
                    if (locationManager?.isProviderEnabled(provider) == true) locationManager.getLastKnownLocation(provider) else null
                }.getOrNull()
            }
            .maxByOrNull { it.time }
    }

    private fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    }

    private fun addPostCard(post: Post) {
        PostCardRenderer.addPostCard(
            parent = binding.searchLAYResults,
            post = post,
            onOpen = { (requireActivity() as MainActivity).openPost(it.postId) },
            onMediaOpen = { url, mediaType -> (requireActivity() as MainActivity).openMediaViewer(url, mediaType) },
            onAuthorOpen = { authorId -> (requireActivity() as MainActivity).openUserProfile(authorId) },
            cardStyle = PostCardRenderer.CardStyle.FLOW_LIGHT
        )
    }

    private fun addUserRow(user: User) {
        val fullName = "${user.firstName} ${user.lastName}".trim().ifBlank { "Dancer" }
        binding.searchLAYResults.addView(LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundResource(R.drawable.bg_discover_card)
            setPadding(12.dp(), 12.dp(), 12.dp(), 12.dp())
            isClickable = true
            isFocusable = true
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = 10.dp() }
            setOnClickListener { (requireActivity() as MainActivity).openUserProfile(user.uid) }
            addView(ImageView(requireContext()).apply {
                setBackgroundResource(R.drawable.bg_avatar)
                scaleType = ImageView.ScaleType.CENTER_CROP
                layoutParams = LinearLayout.LayoutParams(52.dp(), 52.dp()).apply { rightMargin = 12.dp() }
                if (user.profileImageUrl.isNotBlank()) Glide.with(requireContext()).load(user.profileImageUrl).centerCrop().into(this)
            })
            addView(LinearLayout(requireContext()).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                addView(TextView(requireContext()).apply {
                    text = fullName
                    setTextColor(requireContext().getColor(R.color.discover_ink))
                    textSize = 16f
                    setTypeface(typeface, android.graphics.Typeface.BOLD)
                })
                addView(TextView(requireContext()).apply {
                    text = listOf(user.roleLabel(), user.danceStyles.joinToString(", "), user.location).filter { it.isNotBlank() }.joinToString(" / ")
                    setTextColor(requireContext().getColor(R.color.discover_text_secondary))
                    textSize = 13f
                    maxLines = 2
                    ellipsize = TextUtils.TruncateAt.END
                    setPadding(0, 5.dp(), 0, 0)
                })
            })
            addView(TextView(requireContext()).apply {
                text = "View"
                gravity = Gravity.CENTER
                setTextColor(requireContext().getColor(R.color.discover_purple))
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                setBackgroundResource(R.drawable.bg_discover_segment_inactive)
                layoutParams = LinearLayout.LayoutParams(64.dp(), 36.dp()).apply { leftMargin = 8.dp() }
            })
        })
    }

    private fun chip(label: String, selected: Boolean, onClick: () -> Unit): TextView {
        return TextView(requireContext()).apply {
            text = label
            gravity = Gravity.CENTER
            setBackgroundResource(if (selected) R.drawable.bg_discover_segment_active else R.drawable.bg_discover_segment_inactive)
            setTextColor(requireContext().getColor(if (selected) R.color.white else R.color.discover_text_secondary))
            setTypeface(typeface, if (selected) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
            isClickable = true
            isFocusable = true
            setPadding(14.dp(), 0, 14.dp(), 0)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, 36.dp()).apply { rightMargin = 8.dp() }
            setOnClickListener { onClick() }
        }
    }

    private fun optionRow(label: String, selected: Boolean, onClick: () -> Unit): TextView {
        return TextView(requireContext()).apply {
            text = if (selected) "[x] $label" else label
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundResource(if (selected) R.drawable.bg_discover_segment_active else R.drawable.bg_discover_segment_inactive)
            setTextColor(requireContext().getColor(if (selected) R.color.white else R.color.discover_ink))
            setTypeface(typeface, if (selected) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
            isClickable = true
            isFocusable = true
            minHeight = 46.dp()
            setPadding(14.dp(), 0, 14.dp(), 0)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 46.dp()).apply {
                bottomMargin = 8.dp()
            }
            setOnClickListener { onClick() }
        }
    }

    private fun actionButton(label: String, weight: Float = 0f, onClick: () -> Unit): Button {
        return Button(requireContext()).apply {
            text = label
            isAllCaps = false
            setTextColor(context.getColor(R.color.discover_purple))
            setBackgroundResource(R.drawable.bg_discover_segment_inactive)
            setOnClickListener { onClick() }
            layoutParams = LinearLayout.LayoutParams(
                if (weight > 0f) 0 else LinearLayout.LayoutParams.MATCH_PARENT,
                44.dp(),
                weight
            ).apply {
                topMargin = 8.dp()
                if (weight > 0f) rightMargin = 6.dp()
            }
        }
    }

    private fun sectionTitle(title: String): TextView {
        return TextView(requireContext()).apply {
            text = title
            setTextColor(requireContext().getColor(R.color.discover_ink))
            textSize = 17f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setPadding(0, 14.dp(), 0, 4.dp())
        }
    }

    private fun messageCard(message: String): TextView {
        return TextView(requireContext()).apply {
            text = message
            setTextColor(requireContext().getColor(R.color.discover_text_secondary))
            textSize = 14f
            setBackgroundResource(R.drawable.bg_discover_card)
            setPadding(14.dp(), 14.dp(), 14.dp(), 14.dp())
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = 10.dp() }
        }
    }

    private fun activeFilterLabel(): String {
        val count = viewModel.activeFilterCount()
        return if (count == 0) "Filters" else "Filters ($count)"
    }

    private fun mapChipLabel(label: String): String {
        val value = currentFilterValue(label)
        return when {
            label == "More Filters" -> activeFilterLabel()
            value.isBlank() -> label
            value.startsWith("$label (") -> value
            else -> "$label: $value"
        }
    }

    private fun isMapChipSelected(label: String): Boolean {
        return if (label == "More Filters") viewModel.activeFilterCount() > 0 else currentFilterValue(label).isNotBlank()
    }

    private fun currentFilterValue(label: String): String {
        val filters = viewModel.state.filters
        return when (label) {
            "Style" -> filters.styles.summaryOr(filters.style, "Style")
            "Level" -> filters.levels.summaryOr(filters.level, "Level")
            "Location" -> filters.locations.summaryOr(filters.location, "Location")
            "Distance" -> filters.distance
            "Date" -> filters.dates.summaryOr(filters.date, "Date")
            "Time" -> filters.times.summaryOr(filters.time, "Time")
            "Type" -> filters.contentTypes.summaryOr(filters.contentType, "Type")
            else -> ""
        }
    }

    private fun isOptionSelected(label: String, option: String): Boolean {
        val filters = viewModel.state.filters
        return when (label) {
            "Style" -> option in filters.styles || option.equals(filters.style, ignoreCase = true)
            "Level" -> option in filters.levels || option.equals(filters.level, ignoreCase = true)
            "Location" -> option in filters.locations || option.equals(filters.location, ignoreCase = true)
            "Date" -> option in filters.dates || option.equals(filters.date, ignoreCase = true)
            "Time" -> option in filters.times || option.equals(filters.time, ignoreCase = true)
            "Type" -> option in filters.contentTypes || option.equals(filters.contentType, ignoreCase = true)
            else -> option.equals(currentFilterValue(label), ignoreCase = true)
        }
    }

    private fun toggleOrApplyFilterValue(label: String, value: String) {
        val filters = viewModel.state.filters
        when (label) {
            "Style" -> {
                filters.style = ""
                if (!filters.styles.add(value)) filters.styles.remove(value)
            }
            "Level" -> {
                filters.level = ""
                if (!filters.levels.add(value)) filters.levels.remove(value)
            }
            "Location" -> {
                filters.location = ""
                if (!filters.locations.add(value)) filters.locations.remove(value)
            }
            "Date" -> {
                filters.date = ""
                if (!filters.dates.add(value)) filters.dates.remove(value)
            }
            "Time" -> {
                filters.time = ""
                if (!filters.times.add(value)) filters.times.remove(value)
            }
            "Type" -> {
                filters.contentType = ""
                if (!filters.contentTypes.add(value)) filters.contentTypes.remove(value)
            }
            else -> applyFilterValue(label, value)
        }
    }

    private fun applyFilterValue(label: String, value: String) {
        val filters = viewModel.state.filters
        val normalized = when (value) {
            "Saved City" -> DiscoveryRepository.preferredLocation
            "Current Location" -> {
                requestLocationIfNeeded()
                ""
            }
            else -> value
        }
        when (label) {
            "Style" -> {
                filters.styles.clear()
                filters.style = normalized
            }
            "Level" -> {
                filters.levels.clear()
                filters.level = normalized
            }
            "Location" -> {
                filters.locations.clear()
                filters.location = normalized
            }
            "Distance" -> filters.distance = normalized
            "Date" -> {
                filters.dates.clear()
                filters.date = normalized
            }
            "Time" -> {
                filters.times.clear()
                filters.time = normalized
            }
            "Type" -> {
                filters.contentTypes.clear()
                filters.contentType = normalized
            }
        }
    }

    private fun summaryText(): String {
        val results = viewModel.state.results.size + viewModel.state.peopleResults.size + viewModel.state.postResults.size
        val mode = if (viewModel.state.viewMode == SearchViewMode.MAP) "Map" else "List"
        return "$mode / $results results"
    }

    private fun renderError(message: String) {
        viewModel.state.error = message
        render()
    }

    private fun shareItem(item: DiscoveryItem) {
        val text = listOf(item.title, item.compactMeta()).filter { it.isNotBlank() }.joinToString("\n")
        startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }, "Share"))
    }

    private fun List<DiscoveryItem>.filterByQuery(query: String): List<DiscoveryItem> {
        if (query.isBlank()) return this
        return filter { item ->
            listOf(item.title, item.studio, item.teacher, item.style, item.level, item.location, item.address, item.type, item.displayType)
                .any { it.contains(query, ignoreCase = true) }
        }
    }

    private fun List<DiscoveryItem>.filterByCategory(category: SearchCategory): List<DiscoveryItem> {
        return when (category) {
            SearchCategory.ALL -> this
            SearchCategory.PEOPLE -> emptyList()
            SearchCategory.STUDIOS -> filter { it.isStudioLike() }
            SearchCategory.CLASSES -> filter { it.displayType.ifBlank { it.type }.equals("class", ignoreCase = true) }
            SearchCategory.EVENTS -> filter { it.displayType.ifBlank { it.type }.equals("event", ignoreCase = true) }
        }
    }

    private fun List<DiscoveryItem>.filterByExtraFilters(filters: SearchFilters): List<DiscoveryItem> {
        return filter { item ->
            (filters.selectedStyles().isEmpty() || filters.selectedStyles().any { item.style.contains(it, ignoreCase = true) }) &&
                (filters.selectedLevels().isEmpty() || filters.selectedLevels().any { item.level.contains(it, ignoreCase = true) || item.level.equals("All levels", ignoreCase = true) }) &&
                (filters.selectedLocations().isEmpty() || filters.selectedLocations().any { item.location.contains(it, ignoreCase = true) || item.address.contains(it, ignoreCase = true) }) &&
                (filters.selectedTypes().isEmpty() || filters.selectedTypes().any { item.isType(it) || (it.equals("Studio", ignoreCase = true) && item.isStudioLike()) }) &&
                (filters.rating.toDoubleOrNull() == null || (item.rating ?: 0.0) >= filters.rating.toDouble()) &&
                (!filters.freeOnly || item.priceText.contains("free", ignoreCase = true) || item.priceText == "0") &&
                (!filters.openNow || item.openNowLabel.contains("open", ignoreCase = true)) &&
                (filters.price.isBlank() || item.priceText.contains(filters.price, ignoreCase = true)) &&
                (filters.distance.distanceKmOrNull() == null || item.isWithinDistance(filters.distance.distanceKmOrNull()!!)) &&
                (filters.selectedDates().isEmpty() || filters.selectedDates().any { item.dateTimeText.contains(it, ignoreCase = true) || item.time.contains(it, ignoreCase = true) }) &&
                (filters.selectedTimes().isEmpty() || filters.selectedTimes().any { item.dateTimeText.contains(it, ignoreCase = true) || item.time.contains(it, ignoreCase = true) })
        }
    }

    private fun SearchFilters.selectedStyles(): List<String> {
        return styles.toList().ifEmpty { listOf(style).filter { it.isNotBlank() } }
    }

    private fun SearchFilters.primaryStyle(): String {
        return selectedStyles().firstOrNull().orEmpty()
    }

    private fun SearchFilters.selectedLevels(): List<String> {
        return levels.toList().ifEmpty { listOf(level).filter { it.isNotBlank() } }
    }

    private fun SearchFilters.selectedLocations(): List<String> {
        return locations.toList().ifEmpty { listOf(location).filter { it.isNotBlank() } }
    }

    private fun SearchFilters.selectedDates(): List<String> {
        return dates.toList().ifEmpty { listOf(date).filter { it.isNotBlank() } }
    }

    private fun SearchFilters.selectedTimes(): List<String> {
        return times.toList().ifEmpty { listOf(time).filter { it.isNotBlank() } }
    }

    private fun SearchFilters.selectedTypes(): List<String> {
        return contentTypes.toList().ifEmpty { listOf(contentType).filter { it.isNotBlank() } }
    }

    private fun Set<String>.summaryOr(fallback: String, label: String): String {
        return when {
            isEmpty() -> fallback
            size == 1 -> first()
            else -> "$label ($size)"
        }
    }

    private fun List<DiscoveryItem>.unique(): List<DiscoveryItem> {
        val seen = mutableSetOf<String>()
        return filter { seen.add(if (it.googlePlaceId.isNotBlank()) "google:${it.googlePlaceId}" else "internal:${it.id}") }
    }

    private fun List<Post>.filterBySearchCategory(category: SearchCategory): List<Post> {
        return when (category) {
            SearchCategory.EVENTS -> filter { it.postType == "dance_activity" }
            SearchCategory.CLASSES -> filter { it.activityType.contains("class", ignoreCase = true) }
            SearchCategory.PEOPLE, SearchCategory.STUDIOS -> emptyList()
            else -> this
        }
    }

    private fun List<User>.filterByPeopleCategory(category: SearchCategory): List<User> {
        return when (category) {
            SearchCategory.PEOPLE, SearchCategory.ALL -> this
            else -> this
        }
    }

    private fun User.roleLabel(): String {
        return when {
            verifiedChoreographer -> "Choreographer"
            verifiedTeacher -> "Teacher"
            role.contains("studio", ignoreCase = true) -> "Studio"
            else -> "Dancer"
        }
    }

    private fun SearchUiState.activeFiltersBlank(): Boolean {
        return filters == SearchFilters()
    }

    private fun DiscoveryItem.isStudioLike(): Boolean {
        val value = displayType.ifBlank { type }.lowercase()
        return source == DiscoveryItem.SOURCE_GOOGLE || value == "studio" || type.equals("studio", ignoreCase = true)
    }

    private fun DiscoveryItem.isType(expected: String): Boolean {
        val value = displayType.ifBlank { type }
        return value.equals(expected, ignoreCase = true) ||
            (expected.equals("Workshop", ignoreCase = true) && value.equals("workshop", ignoreCase = true))
    }

    private fun List<DiscoveryItem>.mapCapableForMapIfNeeded(): List<DiscoveryItem> {
        if (viewModel.state.viewMode != SearchViewMode.MAP) return this
        return filter { item ->
            item.latitude != null && item.longitude != null &&
                (item.isStudioLike() || item.isType("class") || item.isType("event") || item.isType("workshop"))
        }
    }

    private fun DiscoveryItem.isWithinDistance(distanceKm: Double): Boolean {
        val explicitDistance = distanceMeters
        if (explicitDistance != null) return explicitDistance <= distanceKm * 1000.0
        val location = currentLocationIfAllowed() ?: return true
        val lat = latitude ?: return false
        val lng = longitude ?: return false
        val result = FloatArray(1)
        Location.distanceBetween(location.latitude, location.longitude, lat, lng, result)
        return result[0] <= distanceKm * 1000.0
    }

    private fun String.distanceKmOrNull(): Double? {
        return Regex("""(\d+(?:\.\d+)?)""").find(this)?.value?.toDoubleOrNull()
    }

    private fun fallbackMapCenter(): LatLng {
        return when (DiscoveryRepository.preferredLocation.lowercase()) {
            "jerusalem", "ירושלים" -> LatLng(31.7683, 35.2137)
            "haifa", "חיפה" -> LatLng(32.7940, 34.9896)
            "ramat gan", "רמת גן" -> LatLng(32.0684, 34.8248)
            else -> DEFAULT_MAP_CENTER
        }
    }

    private fun LatLng.toLocation(): Location {
        return Location("map_fallback").apply {
            latitude = this@toLocation.latitude
            longitude = this@toLocation.longitude
        }
    }

    private fun DiscoveryItem.typeLabel(): String {
        if (source == DiscoveryItem.SOURCE_GOOGLE) return "Google"
        return displayType.ifBlank { type }.ifBlank { "Place" }.replaceFirstChar { it.uppercase() }
    }

    private fun DiscoveryItem.compactMeta(): String {
        val ratingText = rating?.let { rating ->
            val count = ratingCount?.takeIf { it > 0 }?.let { " ($it)" }.orEmpty()
            "★ ${"%.1f".format(rating)}$count"
        }
        val distanceText = distanceMeters?.let { meters ->
            if (meters >= 1000) "${"%.1f".format(meters / 1000.0)} km" else "${meters.toInt()} m"
        }
        return listOfNotNull(
            typeLabel(),
            style.takeIf { it.isNotBlank() && !it.equals("Dance", ignoreCase = true) },
            level.takeIf { it.isNotBlank() },
            location.ifBlank { address }.takeIf { it.isNotBlank() },
            dateTimeText.takeIf { it.isNotBlank() } ?: time.takeIf { it.isNotBlank() && !it.equals("operational", ignoreCase = true) },
            priceText.takeIf { it.isNotBlank() },
            ratingText,
            distanceText
        ).distinct().joinToString(" / ")
    }

    private fun String.naturalReason(): String {
        val first = lineSequence().map { it.trim() }.firstOrNull { it.isNotBlank() }.orEmpty()
        return when {
            first.startsWith("Popular near", ignoreCase = true) -> first.replace("Popular near", "Popular near you")
            first.startsWith("Based on", ignoreCase = true) -> "Matches your dance profile"
            first.contains("level", ignoreCase = true) -> "Recommended for your level"
            first.isBlank() -> "Recommended by THE FLOW"
            else -> first
        }
    }

    private fun markerHue(item: DiscoveryItem): Float {
        if (item.source == DiscoveryItem.SOURCE_GOOGLE) return BitmapDescriptorFactory.HUE_AZURE
        return when (item.displayType.ifBlank { item.type }.lowercase()) {
            "class" -> BitmapDescriptorFactory.HUE_VIOLET
            "workshop" -> BitmapDescriptorFactory.HUE_ORANGE
            "event" -> BitmapDescriptorFactory.HUE_ROSE
            else -> BitmapDescriptorFactory.HUE_MAGENTA
        }
    }

    override fun onDestroyView() {
        debounceHandler.removeCallbacksAndMessages(null)
        viewModel.state.scrollY = runCatching { binding.searchSCROLLResults.scrollY }.getOrDefault(0)
        googleMap?.cameraPosition?.let { camera ->
            viewModel.state.mapLatitude = camera.target.latitude
            viewModel.state.mapLongitude = camera.target.longitude
            viewModel.state.mapZoom = camera.zoom
        }
        googleMap = null
        _binding = null
        super.onDestroyView()
    }

    companion object {
        private const val ARG_MAP_MODE = "ARG_MAP_MODE"
        private const val SEARCH_DEBOUNCE_MS = 350L
        private const val SEARCH_STALE_AFTER_MS = 5 * 60 * 1000L
        private val DEFAULT_MAP_CENTER = LatLng(32.0853, 34.7818)
        private const val DEFAULT_MAP_ZOOM = 11f

        fun newInstance(mapMode: Boolean = false): SearchFragment {
            return SearchFragment().apply {
                arguments = Bundle().apply { putBoolean(ARG_MAP_MODE, mapMode) }
            }
        }
    }
}

private fun Int.dp(): Int {
    return (this * android.content.res.Resources.getSystem().displayMetrics.density).toInt()
}
