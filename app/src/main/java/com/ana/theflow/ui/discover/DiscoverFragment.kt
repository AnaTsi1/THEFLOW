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

class DiscoverFragment : Fragment() {

    private var _binding: FragmentDiscoverBinding? = null
    private val binding get() = _binding!!
    private val userRepository = UserRepository()
    private var lastExternalQuery: String = ""
    private var externalLoaded = false
    private var searchMode = SearchMode.USERS
    private var isFiltering = false
    private var isSearching = false

    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val granted = grants.values.any { it }
        if (!granted && _binding != null) {
            Toast.makeText(requireContext(), R.string.discover_location_permission_denied, Toast.LENGTH_SHORT).show()
        }
        loadExternalStudios(currentLocationIfAllowed())
    }

    // Creates and returns the fragment view.
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentDiscoverBinding.inflate(inflater, container, false)
        return binding.root
    }

    // Connects the screen UI after the view is ready.
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        configureDanceStyleSelector()
        configureLevelSelector()
        CityOptions.configureCitySelector(requireContext(), binding.discoverEDTLocation)
        setupSearch()
        setupSearchTypeChips()
        configureFilterInputActions()
        binding.discoverBTNApplyFilters.setOnClickListener {
            applyFilters()
        }
        binding.discoverBTNClearFilters.setOnClickListener {
            clearFilters()
        }
        binding.discoverLBLExplanation.text = getString(R.string.discover_loading_studios)
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
                    requestLocationThenLoadExternal()
                }
            },
            onFailure = { error ->
                if (_binding != null) {
                    binding.discoverLBLExplanation.text = error
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
        binding.discoverLBLExplanation.text = DiscoveryRepository.behaviorSummary()
        binding.discoverLAYRecommended.removeAllViews()
        binding.discoverLAYPopular.removeAllViews()

        if (isSearching) {
            binding.discoverPRGSearching.visibility = View.VISIBLE
            binding.discoverLBLRecommendedTitle.text = "Searching..."
            binding.discoverLBLPopularTitle.visibility = View.GONE
            binding.discoverLAYPopular.visibility = View.GONE
            return
        }

        binding.discoverPRGSearching.visibility = View.GONE

        if (isFiltering) {
            val results = DiscoveryRepository.filterDiscoverItems(
                query = binding.discoverEDTSearch.text.toString(),
                style = binding.discoverEDTStyle.text.toString(),
                level = binding.discoverEDTLevel.text.toString(),
                location = selectedOptionalCity(binding.discoverEDTLocation.text.toString())
            )
            binding.discoverLBLRecommendedTitle.text = "Search Results"
            binding.discoverLBLPopularTitle.visibility = View.GONE
            binding.discoverLAYPopular.visibility = View.GONE

            if (results.isEmpty()) {
                binding.discoverLAYRecommended.addView(emptyText("No matching items found."))
            } else {
                results.forEach { item ->
                    addCard(binding.discoverLAYRecommended, item)
                }
            }
            return
        }

        binding.discoverLBLRecommendedTitle.text = "Recommended for You"
        binding.discoverLBLPopularTitle.visibility = View.VISIBLE
        binding.discoverLAYPopular.visibility = View.VISIBLE

        DiscoveryRepository.recommendedItems().take(4).forEach { item ->
            addCard(binding.discoverLAYRecommended, item)
        }

        DiscoveryRepository.popularNearYou().take(3).forEach { item ->
            addCard(binding.discoverLAYPopular, item)
        }
        binding.discoverLBLGoogleAttribution.visibility =
            if (DiscoveryRepository.recommendedItems().any { it.source == DiscoveryItem.SOURCE_GOOGLE }) {
                View.VISIBLE
            } else {
                View.GONE
            }
    }

    private fun requestLocationThenLoadExternal() {
        if (externalLoaded) return
        externalLoaded = true
        if (hasLocationPermission()) {
            loadExternalStudios(currentLocationIfAllowed())
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
            searchMode = SearchMode.USERS
            renderSearchTypeChips()
            runDiscoverSearch(loadExternal = false)
        }
        binding.discoverCHIPStudios.setOnClickListener {
            searchMode = SearchMode.STUDIOS
            renderSearchTypeChips()
            runDiscoverSearch(loadExternal = true)
        }
        binding.discoverCHIPDancers.setOnClickListener {
            searchMode = SearchMode.DANCERS
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
        binding.discoverLAYSearchResults.removeAllViews()
        if (!hasSearch) return

        when (searchMode) {
            SearchMode.USERS,
            SearchMode.DANCERS -> searchUsers(query, location, style, level, searchMode == SearchMode.DANCERS)
            SearchMode.STUDIOS -> {
                val results = DiscoveryRepository.search(
                    style = style,
                    level = level,
                    location = location,
                    teacher = "",
                    studio = query,
                    time = time
                ).filterByFreeText(query)
                renderStudioSearchResults(results)
                if (loadExternal) {
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

    private fun searchUsers(query: String, location: String, style: String, level: String, dancersOnly: Boolean) {
        userRepository.searchUsers(
            query = query,
            dancersOnly = dancersOnly,
            onSuccess = { users ->
                if (_binding == null) return@searchUsers
                val filtered = users.filter { user ->
                    (location.isBlank() || user.location.equals(location, ignoreCase = true)) &&
                        (style.isBlank() || user.danceStyles.any { it.contains(style, ignoreCase = true) }) &&
                        (level.isBlank() || user.danceLevel.contains(level, ignoreCase = true))
                }
                renderUserSearchResults(filtered)
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
                    if (searchMode == SearchMode.STUDIOS) runDiscoverSearch(loadExternal = false)
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
            binding.discoverCHIPUsers to SearchMode.USERS,
            binding.discoverCHIPStudios to SearchMode.STUDIOS,
            binding.discoverCHIPDancers to SearchMode.DANCERS
        ).forEach { (chip, mode) ->
            val selected = searchMode == mode
            chip.setBackgroundResource(if (selected) R.drawable.bg_button_primary_selected else R.drawable.bg_button_secondary)
            chip.setTextColor(requireContext().getColor(if (selected) R.color.text_primary else R.color.text_secondary))
        }
    }

    private fun renderStudioSearchResults(items: List<DiscoveryItem>) {
        binding.discoverLAYSearchResults.removeAllViews()
        if (items.isEmpty()) {
            renderSearchMessage("No studios found")
            return
        }
        items.take(12).forEach { item -> addCard(binding.discoverLAYSearchResults, item) }
    }

    private fun renderUserSearchResults(users: List<User>) {
        binding.discoverLAYSearchResults.removeAllViews()
        if (users.isEmpty()) {
            renderSearchMessage("No users found")
            return
        }
        users.take(20).forEach { user ->
            binding.discoverLAYSearchResults.addView(userRow(user))
        }
    }

    private fun renderSearchMessage(message: String) {
        binding.discoverLAYSearchResults.removeAllViews()
        binding.discoverLAYSearchResults.addView(TextView(requireContext()).apply {
            text = message
            setTextColor(requireContext().getColor(R.color.text_secondary))
            textSize = 13f
            setBackgroundResource(R.drawable.bg_card)
            setPadding(14.dp(), 14.dp(), 14.dp(), 14.dp())
        })
    }

    private fun userRow(user: User): View {
        val fullName = "${user.firstName} ${user.lastName}".trim().ifBlank { "Dancer" }
        return LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundResource(R.drawable.bg_post_card)
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
                setTextColor(requireContext().getColor(R.color.text_primary))
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
                setTextColor(requireContext().getColor(R.color.text_secondary))
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
                            Toast.makeText(requireContext(), "Saved", Toast.LENGTH_SHORT).show()
                            render()
                        }
                    },
                    onFailure = { error ->
                        if (_binding != null) {
                            Toast.makeText(requireContext(), error, Toast.LENGTH_LONG).show()
                        }
                    }
                )
            }
        )
    }

    // Checks whether the user entered any Discover search filters.
    private fun hasActiveFilters(): Boolean {
        return binding.discoverEDTSearch.text.toString().isNotBlank() ||
            binding.discoverEDTStyle.text.toString().isNotBlank() ||
            binding.discoverEDTLevel.text.toString().isNotBlank() ||
            binding.discoverEDTLocation.text.toString().isNotBlank()
    }

    // Returns a normalized city filter or raw text when the city is not in the dropdown.
    private fun selectedOptionalCity(value: String): String {
        return CityOptions.normalizeOptionalCity(value) ?: value.trim()
    }

    // Creates a simple message row for empty search results.
    private fun emptyText(message: String): android.widget.TextView {
        return android.widget.TextView(requireContext()).apply {
            text = message
            setTextColor(requireContext().getColor(com.ana.theflow.R.color.text_muted))
            textSize = 14f
            setPadding(0, 18, 0, 0)
        }
    }

    // Clears the fragment binding when the view is destroyed.
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private enum class SearchMode {
        USERS,
        STUDIOS,
        DANCERS
    }

    private companion object {
        private const val SEARCH_FEEDBACK_DELAY_MS = 250L
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
}

private fun Int.dp(): Int {
    return (this * android.content.res.Resources.getSystem().displayMetrics.density).toInt()
}
