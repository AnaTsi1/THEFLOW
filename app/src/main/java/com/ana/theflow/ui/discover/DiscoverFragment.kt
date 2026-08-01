package com.ana.theflow.ui.discover

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.ana.theflow.MainActivity
import com.ana.theflow.R
import com.ana.theflow.data.model.account.ActiveAccount
import com.ana.theflow.data.model.discovery.DiscoveryItem
import com.ana.theflow.data.recommendation.GeoPoint
import com.ana.theflow.data.recommendation.LocationSourceResolver
import com.ana.theflow.data.recommendation.RecommendationContext
import com.ana.theflow.data.recommendation.RecommendationSurface
import com.ana.theflow.data.repository.DiscoveryRepository
import com.ana.theflow.data.repository.UserRepository
import com.ana.theflow.data.session.ActiveAccountHolder
import com.ana.theflow.databinding.FragmentDiscoverBinding
import com.ana.theflow.databinding.ItemDiscoverMapBinding
import com.ana.theflow.databinding.ItemDiscoverSectionBinding
import com.ana.theflow.ui.common.DiscoveryCardRenderer
import com.ana.theflow.ui.common.ResponsiveLayout
import com.ana.theflow.ui.common.UiText
import com.ana.theflow.utilities.DeviceLocationProvider
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.MapView
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.MarkerOptions

class DiscoverFragment : Fragment(), OnMapReadyCallback {

    private var _binding: FragmentDiscoverBinding? = null
    private val binding get() = _binding!!
    private val viewModel: DiscoverViewModel by activityViewModels()
    private val userRepository = UserRepository()
    private var googleMap: GoogleMap? = null
    private var mapView: MapView? = null
    private var mapRowBinding: ItemDiscoverMapBinding? = null
    private var sectionsAdapter: DiscoverSectionsAdapter? = null
    private val locationProvider = DeviceLocationProvider(this)
    private var hasResumedOnce = false
    private var sectionsInitialized = false
    private val activeAccountListener: (ActiveAccount) -> Unit = {
        if (_binding != null) {
            applyAccountModeVisibility()
            if (!isStudioAccountActive()) initializeSectionsIfNeeded()
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentDiscoverBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.discoverLAYSearchBox.setOnClickListener {
            (requireActivity() as MainActivity).openSearch()
        }
        binding.discoverSWIPERefresh.setColorSchemeResources(R.color.discover_purple)
        binding.discoverSWIPERefresh.setOnRefreshListener {
            onPullToRefresh()
        }
        ResponsiveLayout.constrainToReadableWidth(
            binding.discoverLBLTitle,
            binding.discoverLBLExplanation,
            binding.discoverLAYSearchBox,
            binding.discoverRCYSections
        )
        ActiveAccountHolder.addListener(activeAccountListener)
        applyAccountModeVisibility()
        if (!isStudioAccountActive()) initializeSectionsIfNeeded()
    }

    private fun isStudioAccountActive(): Boolean = ActiveAccountHolder.current() is ActiveAccount.StudioAccount

    // A studio/business account has nothing personal to recommend - Discover collapses to just
    // search (the same search screen personal accounts use, unchanged) rather than showing
    // Recommended/Studios/Events/Teachers sections built from a personal recommendation profile
    // that doesn't apply to whoever the account is currently posting as. This was flagged as a
    // known gap in an earlier session ("DiscoverFragment has zero active-account awareness") and
    // never actually implemented - it isn't something this session's recommendation-engine work
    // regressed, it simply didn't exist yet.
    private fun applyAccountModeVisibility() {
        val studioActive = isStudioAccountActive()
        val recommendationViews = listOf(
            binding.discoverPRGSearching,
            binding.discoverLBLLoading,
            binding.discoverSWIPERefresh
        )
        recommendationViews.forEach { it.visibility = if (studioActive) View.GONE else View.VISIBLE }
        binding.discoverLBLExplanation.text = if (studioActive) {
            "Search for studios, dancers, teachers, and events."
        } else {
            "Find classes, events, studios, and dancers that fit your flow."
        }
    }

    private fun initializeSectionsIfNeeded() {
        if (sectionsInitialized) return
        sectionsInitialized = true
        setUpSections()

        if (viewModel.hasUsableCache()) {
            refreshRecommended()
            if (viewModel.isStale()) loadInitial(background = true)
        } else {
            renderLoadingRecommended()
            loadInitial(background = false)
        }
    }

    // Only the Recommended section's own dependencies (approved studios, published activities,
    // the recommendation profile, saved items) load on screen open - this is the entire
    // "section 1 only on initial open" requirement. Studios/Events/Teachers fetch nothing until
    // their row scrolls near the viewport (see setUpSections' lookahead layout manager).
    private fun loadInitial(background: Boolean) {
        if (viewModel.isRefreshing) return
        viewModel.isRefreshing = true
        if (!background) renderLoadingRecommended()

        userRepository.loadRecommendationProfile(
            onSuccess = { profile ->
                viewModel.recommendationProfile = profile
                DiscoveryRepository.hydrateProfile(profile)
                if (_binding != null) refreshRecommended()
            },
            onFailure = {
                viewModel.lastError = it
                if (_binding != null) refreshRecommended()
            }
        )

        DiscoveryRepository.loadSavedItems(
            onSuccess = { if (_binding != null) refreshRecommended() },
            onFailure = { if (_binding != null) refreshRecommended() }
        )

        DiscoveryRepository.loadApprovedStudios(
            onSuccess = {
                viewModel.loadedInternal = true
                if (_binding != null) {
                    refreshRecommended()
                    refreshMapPreview()
                }
            },
            onFailure = { error ->
                viewModel.loadedInternal = true
                viewModel.lastError = error
                viewModel.isRefreshing = false
                if (_binding != null) {
                    if (!viewModel.hasUsableCache()) {
                        Toast.makeText(requireContext(), UiText.friendlyError(error, "Discover is unavailable right now."), Toast.LENGTH_SHORT).show()
                    }
                    refreshRecommended()
                }
            }
        )

        DiscoveryRepository.loadPublishedActivities(
            onSuccess = {
                viewModel.markLoaded()
                if (_binding != null) refreshRecommended()
            },
            onFailure = {
                viewModel.lastError = it
                viewModel.markLoaded()
                if (_binding != null) refreshRecommended()
            }
        )
    }

    // A LinearLayoutManager whose extra-layout-space is forced out to LOOKAHEAD_PX in both
    // directions, so RecyclerView actually creates/binds a section's row (and this fragment
    // starts its fetch, in bindSection below) once it's within that many pixels of the visible
    // viewport - not only once it's already on screen. This is the "start the fetch slightly
    // ahead" requirement; no separate scroll-position polling is needed on top of it.
    private fun setUpSections() {
        val layoutManager = object : LinearLayoutManager(requireContext(), VERTICAL, false) {
            override fun calculateExtraLayoutSpace(state: RecyclerView.State, extraLayoutSpace: IntArray) {
                super.calculateExtraLayoutSpace(state, extraLayoutSpace)
                extraLayoutSpace[0] = maxOf(extraLayoutSpace[0], LOOKAHEAD_PX)
                extraLayoutSpace[1] = maxOf(extraLayoutSpace[1], LOOKAHEAD_PX)
            }
        }
        val adapter = DiscoverSectionsAdapter(
            onBindSection = { section, sectionBinding -> bindSection(section, sectionBinding) },
            onCreateMapRow = { mapBinding -> createMapRow(mapBinding) },
            onBindMapRow = { mapBinding -> bindMapRow(mapBinding) }
        )
        sectionsAdapter = adapter
        binding.discoverRCYSections.layoutManager = layoutManager
        binding.discoverRCYSections.adapter = adapter
        // +1 for the map row - cached alongside the 4 sections so its ViewHolder (and the MapView
        // it owns) is never torn down and recreated by scrolling within one screen visit; the
        // MapView's lifecycle is forwarded from this fragment's own lifecycle instead (see
        // onResume/onPause/onDestroyView), matching how many-instances-of-a-MapView-in-a-list
        // apps handle it, simplified here since there's only ever one map row.
        binding.discoverRCYSections.setItemViewCacheSize(DiscoverSectionType.values().size + 1)
    }

    // Called exactly once, when the map row's ViewHolder is first created - MapView requires
    // onCreate() before getMapAsync() will do anything.
    private fun createMapRow(mapBinding: ItemDiscoverMapBinding) {
        mapRowBinding = mapBinding
        val map = mapBinding.discoverMAPViewMap
        mapView = map
        map.onCreate(null)
        map.getMapAsync(this)
        ResponsiveLayout.constrainToReadableWidth(
            mapBinding.discoverMAPLBLTitle,
            mapBinding.discoverMAPLAYMap,
            mapBinding.discoverMAPLBLAttribution
        )
    }

    // Called on every bind pass for the map row (normally just once, but re-runs safely if the
    // RecyclerView ever has to recreate this row's ViewHolder).
    private fun bindMapRow(mapBinding: ItemDiscoverMapBinding) {
        mapRowBinding = mapBinding
        mapBinding.discoverMAPBTNSearchArea.setOnClickListener {
            (requireActivity() as MainActivity).openSearch(mapMode = true)
        }
        mapBinding.discoverMAPLBLAttribution.visibility = View.VISIBLE
        refreshMapPreview()
    }

    private fun bindSection(section: DiscoverSectionType, sectionBinding: ItemDiscoverSectionBinding) {
        renderSectionContent(section, sectionBinding.sectionLAYContent)
        if (section != DiscoverSectionType.RECOMMENDED && viewModel.triggeredSections.add(section)) {
            loadSection(section)
        }
    }

    private fun loadSection(section: DiscoverSectionType) {
        viewModel.loadingSections.add(section)
        viewModel.sectionErrors.remove(section)
        refreshSection(section)

        fun done(error: String?) {
            viewModel.loadingSections.remove(section)
            if (error != null) viewModel.sectionErrors[section] = error
            if (_binding != null) refreshSection(section)
        }

        when (section) {
            DiscoverSectionType.RECOMMENDED -> Unit
            DiscoverSectionType.STUDIOS -> resolveProximityPointAsync { point ->
                if (_binding == null) return@resolveProximityPointAsync
                DiscoveryRepository.studiosNearYou(
                    viewerPoint = point,
                    onSuccess = { done(null); if (_binding != null) refreshMapPreview() },
                    onFailure = { error -> done(error) }
                )
            }
            DiscoverSectionType.EVENTS -> DiscoveryRepository.eventsThisWeek(
                onSuccess = { done(null) },
                onFailure = { error -> done(error) }
            )
            DiscoverSectionType.TEACHERS -> DiscoveryRepository.teachersYouMayLike(
                onSuccess = { done(null) },
                onFailure = { error -> done(error) }
            )
        }
    }

    // Re-fetches Recommended plus every section that has already loaded at least once (untouched
    // sections don't need forcing - they'll fetch fresh the first time they scroll into view
    // regardless). The swipe spinner dismisses right away since each section already shows its
    // own skeleton/loading state while its refetch is in flight.
    private fun onPullToRefresh() {
        loadInitial(background = true)
        viewModel.triggeredSections.toList().forEach { section -> loadSection(section) }
        binding.discoverSWIPERefresh.postDelayed({
            if (_binding != null) binding.discoverSWIPERefresh.isRefreshing = false
        }, 400L)
    }

    // viewModel.recommendationProfile is typed non-null (defaults to RecommendationProfile.empty()
    // until loadInitial()'s profile fetch actually completes), so passing it unconditionally as
    // profileOverride would always win over DiscoveryRepository's own singleton fallback - even
    // during the exact window that fallback exists to cover. Only hand it over once it's real.
    private fun discoverContext(): RecommendationContext {
        return DiscoveryRepository.recommendationContext(
            surface = RecommendationSurface.DISCOVER,
            profileOverride = viewModel.recommendationProfile.takeIf { viewModel.loadedInternal }
        )
    }

    // "Studios Near You" needs where the user actually IS right now, not the location used to
    // rank the Recommended feed - LocationSourceResolver deliberately prefers a saved
    // preferred/profile city over device GPS for that surface (a traveling user's whole feed
    // shouldn't shift). This now runs a real permission request (DeviceLocationProvider) instead
    // of only ever checking a permission nothing in the app actually asked for, and actively
    // requests a fresh fix (bounded by a timeout) rather than only ever reading whatever another
    // app happened to cache - both were silently dead in practice before this. Falls back to the
    // shared resolver's point (preferred area / profile city / Israel-center) only if GPS is
    // denied or genuinely unavailable.
    private fun resolveProximityPointAsync(onResult: (GeoPoint?) -> Unit) {
        locationProvider.currentLocation { location ->
            if (_binding == null) return@currentLocation
            val point = location?.let { GeoPoint(it.latitude, it.longitude) }
                ?: LocationSourceResolver.resolve(discoverContext()).point
            onResult(point)
        }
    }

    private fun refreshRecommended() {
        if (!viewModel.loadedInternal) {
            renderLoadingRecommended()
            return
        }
        binding.discoverPRGSearching.visibility = if (viewModel.isRefreshing && viewModel.hasUsableCache()) View.VISIBLE else View.GONE
        binding.discoverLBLLoading.visibility = View.GONE
        refreshSection(DiscoverSectionType.RECOMMENDED)
    }

    private fun renderLoadingRecommended() {
        binding.discoverPRGSearching.visibility = View.VISIBLE
        binding.discoverLBLLoading.visibility = View.VISIBLE
    }

    // notifyItemChanged must never run synchronously from inside a RecyclerView bind pass - and
    // it easily can here: loadSection() calls this immediately (to show the loading skeleton)
    // from within bindSection(), which IS a bind pass, and DiscoveryRepository's cached-data
    // fast paths (studiosNearYou/eventsThisWeek) can also call their onSuccess back
    // synchronously. Either way, calling notifyItemChanged() mid-layout throws
    // "Cannot call this method while RecyclerView is computing a layout or scrolling". Posting
    // defers it past whatever call stack triggered it, every time, regardless of the cause.
    private fun refreshSection(section: DiscoverSectionType) {
        val recyclerView = _binding?.discoverRCYSections ?: return
        recyclerView.post {
            if (_binding != null) sectionsAdapter?.notifyItemChanged(section.ordinal)
        }
    }

    // Every section render pass - skeleton appearing, skeleton being replaced by real cards, an
    // error message taking their place - fades the container in rather than swapping content
    // instantly. Each of the 4 sections fetches independently and genuinely does finish at
    // different times (that's the point of the lazy-load design, not a bug to fix away), but a
    // consistent gentle fade on every transition is what makes that difference in timing read as
    // "one screen settling in" instead of "pieces popping in randomly."
    private fun renderSectionContent(section: DiscoverSectionType, container: LinearLayout) {
        if (!isAdded) return
        container.animate().cancel()
        container.alpha = 0f
        container.removeAllViews()
        val items = itemsFor(section)
        val isLoading = if (section == DiscoverSectionType.RECOMMENDED) !viewModel.loadedInternal else section in viewModel.loadingSections

        if (items.isEmpty() && isLoading) {
            val width = cardSizeFor(section).first
            repeat(3) { DiscoveryCardRenderer.addSkeletonCard(container, width, skeletonMediaHeightFor(section)) }
            fadeIn(container)
            return
        }
        if (items.isEmpty()) {
            val error = if (section == DiscoverSectionType.RECOMMENDED) viewModel.lastError else viewModel.sectionErrors[section]
            val message = error?.takeIf { it.isNotBlank() }?.let { UiText.friendlyError(it, emptyMessageFor(section)) } ?: emptyMessageFor(section)
            container.addView(emptyText(message))
            fadeIn(container)
            return
        }

        val (width, height) = cardSizeFor(section)
        when (section) {
            DiscoverSectionType.RECOMMENDED -> items.forEach { item ->
                addWrapped(container, width, height) { wrapper ->
                    DiscoveryCardRenderer.addItemCard(
                        parent = wrapper,
                        item = item,
                        explanation = DiscoveryRepository.explanationFor(item),
                        onOpen = ::openDiscoveryItem,
                        onSave = ::saveItem,
                        cardStyle = DiscoveryCardRenderer.CardStyle.DISCOVER_LIGHT
                    )
                }
            }
            DiscoverSectionType.STUDIOS -> items.forEach { item ->
                addWrapped(container, width, height) { wrapper ->
                    DiscoveryCardRenderer.addItemCard(
                        parent = wrapper,
                        item = item,
                        explanation = "",
                        onOpen = ::openDiscoveryItem,
                        onSave = ::saveItem,
                        cardStyle = DiscoveryCardRenderer.CardStyle.DISCOVER_LIGHT
                    )
                }
            }
            DiscoverSectionType.EVENTS -> items.forEach { item ->
                addWrapped(container, width, height) { wrapper ->
                    DiscoveryCardRenderer.addEventCard(wrapper, item, ::openDiscoveryItem, ::openDiscoveryItem)
                }
            }
            DiscoverSectionType.TEACHERS -> items.forEach { item ->
                addWrapped(container, width, height) { wrapper ->
                    DiscoveryCardRenderer.addTeacherCard(wrapper, item, ::openDiscoveryItem, ::followPerson)
                }
            }
        }
        fadeIn(container)
    }

    private fun fadeIn(container: View) {
        container.animate()
            .alpha(1f)
            .setDuration(220L)
            .setStartDelay(20L)
            .start()
    }

    // Every card in a section gets the SAME fixed width and height, regardless of how much
    // content it has - the wrapper fixes the slot size, and each card renderer fills it exactly
    // (MATCH_PARENT) rather than wrapping to its own content, so a studio with a longer name or
    // more listed styles can't make its card taller than its neighbors. Cards still truncate
    // individual text fields with maxLines/ellipsize; heights below are sized generously enough
    // for each section's realistic worst-case content so that truncation - not clipping - is
    // what handles overflow in practice.
    private fun cardSizeFor(section: DiscoverSectionType): Pair<Int, Int> {
        return when (section) {
            DiscoverSectionType.RECOMMENDED -> 300.dp() to 460.dp()
            DiscoverSectionType.STUDIOS -> 230.dp() to 420.dp()
            DiscoverSectionType.EVENTS -> 260.dp() to 310.dp()
            DiscoverSectionType.TEACHERS -> 190.dp() to 220.dp()
        }
    }

    private fun addWrapped(parent: LinearLayout, width: Int, height: Int, build: (LinearLayout) -> Unit) {
        val wrapper = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(width, height).apply {
                marginEnd = 10.dp()
            }
        }
        build(wrapper)
        parent.addView(wrapper)
    }

    private fun itemsFor(section: DiscoverSectionType): List<DiscoveryItem> {
        return when (section) {
            DiscoverSectionType.RECOMMENDED -> DiscoveryRepository.recommendedItems(discoverContext())
                .filterNotGoogle()
                .unique()
                .take(8)
            DiscoverSectionType.STUDIOS -> DiscoveryRepository.studiosNearYouCached()
            DiscoverSectionType.EVENTS -> DiscoveryRepository.eventsThisWeekCached()
            DiscoverSectionType.TEACHERS -> DiscoveryRepository.teachersYouMayLikeCached()
        }
    }

    private fun skeletonMediaHeightFor(section: DiscoverSectionType): Int {
        return when (section) {
            DiscoverSectionType.RECOMMENDED -> 176.dp()
            DiscoverSectionType.STUDIOS -> 176.dp()
            DiscoverSectionType.EVENTS -> 156.dp()
            DiscoverSectionType.TEACHERS -> 90.dp()
        }
    }

    private fun emptyMessageFor(section: DiscoverSectionType): String {
        return when (section) {
            DiscoverSectionType.RECOMMENDED -> "No personalized recommendations yet. Save studios, follow dancers, or search styles to shape Discover."
            DiscoverSectionType.STUDIOS -> "No studios found near you yet."
            DiscoverSectionType.EVENTS -> "No events happening this week."
            DiscoverSectionType.TEACHERS -> "No matching teachers yet."
        }
    }

    // Internal studio listings and teacher cards open their real profile; everything else keeps
    // the existing Discover detail screen. The Events card's Register button and its card tap
    // deliberately both land here too - there's no separate RSVP flow anywhere in the app yet,
    // so a dedicated "register" write isn't fabricated, it just opens the same detail screen the
    // tap already does.
    private fun openDiscoveryItem(item: DiscoveryItem) {
        val activity = requireActivity() as MainActivity
        when {
            item.source != DiscoveryItem.SOURCE_GOOGLE && item.type.equals("Studio", ignoreCase = true) -> activity.openStudioProfile(item.id)
            item.displayType.equals("teacher", ignoreCase = true) -> activity.openUserProfile(item.id)
            else -> activity.openDetail(item)
        }
    }

    private fun followPerson(item: DiscoveryItem) {
        userRepository.getUserByUid(
            uid = item.id,
            onSuccess = { user ->
                userRepository.toggleFollowUser(
                    targetUser = user,
                    onSuccess = { nowFollowing ->
                        if (_binding != null) {
                            val label = if (nowFollowing) "Following ${item.title}" else "Unfollowed ${item.title}"
                            Toast.makeText(requireContext(), label, Toast.LENGTH_SHORT).show()
                        }
                    },
                    onFailure = { error ->
                        if (_binding != null) Toast.makeText(requireContext(), UiText.friendlyError(error, "We could not update this follow."), Toast.LENGTH_SHORT).show()
                    }
                )
            },
            onFailure = { error ->
                if (_binding != null) Toast.makeText(requireContext(), UiText.friendlyError(error, "We could not update this follow."), Toast.LENGTH_SHORT).show()
            }
        )
    }

    private fun saveItem(item: DiscoveryItem) {
        DiscoveryRepository.saveItem(
            item = item,
            onSuccess = {
                if (_binding != null) {
                    Toast.makeText(requireContext(), R.string.discover_saved, Toast.LENGTH_SHORT).show()
                    refreshSection(DiscoverSectionType.RECOMMENDED)
                    refreshSection(DiscoverSectionType.STUDIOS)
                }
            },
            onFailure = { error ->
                if (_binding != null) Toast.makeText(requireContext(), error, Toast.LENGTH_LONG).show()
            }
        )
    }

    private fun emptyText(message: String): View {
        return TextView(requireContext()).apply {
            text = message
            setTextColor(requireContext().getColor(R.color.discover_text_secondary))
            textSize = 14f
            setBackgroundResource(R.drawable.bg_discover_card)
            setPadding(14.dp(), 14.dp(), 14.dp(), 14.dp())
            layoutParams = LinearLayout.LayoutParams(280.dp(), LinearLayout.LayoutParams.WRAP_CONTENT)
        }
    }

    override fun onMapReady(map: GoogleMap) {
        googleMap = map.apply {
            uiSettings.isMapToolbarEnabled = false
            uiSettings.isZoomControlsEnabled = false
            // This preview has never let you actually pan/zoom in place - both the map tap and
            // marker tap already just navigate to the full search-map screen (below). Now that
            // the preview lives inside a vertically-scrolling list, disabling its own pan/zoom
            // gestures also means a scroll gesture that starts over the map keeps scrolling the
            // page instead of fighting with the map for the touch, which matters here in a way
            // it didn't when this panel sat in a non-scrolling area.
            uiSettings.isScrollGesturesEnabled = false
            uiSettings.isZoomGesturesEnabled = false
            uiSettings.isTiltGesturesEnabled = false
            uiSettings.isRotateGesturesEnabled = false
            setOnMapClickListener { (requireActivity() as MainActivity).openSearch(mapMode = true) }
            setOnMarkerClickListener {
                (requireActivity() as MainActivity).openSearch(mapMode = true)
                true
            }
        }
        refreshMapPreview()
    }

    // Fed by whichever studio data has loaded so far (Recommended's approved-studios read, plus
    // Studios Near You once that section has been triggered) - the map is a secondary "what's
    // around" preview rather than a data-driven section like the other 4, so it just repaints
    // opportunistically as either source updates rather than having its own load state.
    private fun refreshMapPreview() {
        val map = googleMap ?: return
        val recommendedWithLocation = DiscoveryRepository.recommendedItems(discoverContext()).filterNotGoogle()
        val items = (recommendedWithLocation + DiscoveryRepository.studiosNearYouCached())
            .unique()
            .take(12)
        renderMapPreview(map, items)
    }

    private fun renderMapPreview(map: GoogleMap, items: List<DiscoveryItem>) {
        val mapBinding = mapRowBinding ?: return
        map.clear()
        val mapped = items.mapNotNull { item ->
            val lat = item.latitude ?: return@mapNotNull null
            val lng = item.longitude ?: return@mapNotNull null
            item to LatLng(lat, lng)
        }
        if (mapped.isEmpty()) {
            mapBinding.discoverMAPLBLTitle.visibility = View.GONE
            mapBinding.discoverMAPLAYMap.visibility = View.GONE
            map.moveCamera(CameraUpdateFactory.newLatLngZoom(DEFAULT_MAP_CENTER, DEFAULT_MAP_ZOOM))
            return
        }
        mapBinding.discoverMAPLBLTitle.visibility = View.VISIBLE
        mapBinding.discoverMAPLAYMap.visibility = View.VISIBLE
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
                        .title(if (group.size > 1) "${group.size} places" else item.title)
                        .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_MAGENTA))
                )
            }
        mapBinding.discoverMAPLAYMap.post {
            if (mapped.size == 1) {
                map.moveCamera(CameraUpdateFactory.newLatLngZoom(mapped.first().second, DEFAULT_MAP_ZOOM))
            } else {
                map.moveCamera(CameraUpdateFactory.newLatLngBounds(bounds.build(), 70))
            }
        }
    }

    private fun List<DiscoveryItem>.filterNotGoogle(): List<DiscoveryItem> {
        return filter { it.source != DiscoveryItem.SOURCE_GOOGLE }
    }

    private fun List<DiscoveryItem>.unique(): List<DiscoveryItem> {
        val seen = mutableSetOf<String>()
        return filter { seen.add(it.key()) }
    }

    private fun DiscoveryItem.key(): String {
        return if (googlePlaceId.isNotBlank()) "google:$googlePlaceId" else "internal:$id"
    }

    // Self-healing staleness check: the tab-based bottom nav in this app shows/hides fragments
    // rather than recreating them, so onResume (not onViewCreated) is what fires every time the
    // user comes back to this tab. Guarded against the very first resume immediately following
    // onViewCreated, which would otherwise race the initial load already in flight and double-fetch.
    // This is what actually wires isStudiosNearYouStale()/isEventsThisWeekStale()/
    // isTeachersYouMayLikeStale() up to anything - they existed but nothing ever called them
    // before, so a bad first snapshot (e.g. resolved before permission/profile data was ready)
    // used to stick around for the rest of the app session with no way to self-correct short of
    // a manual pull-to-refresh or a full restart.
    override fun onResume() {
        super.onResume()
        mapView?.onResume()
        if (_binding == null) return
        applyAccountModeVisibility()
        if (isStudioAccountActive()) return
        initializeSectionsIfNeeded()
        if (!hasResumedOnce) {
            hasResumedOnce = true
            return
        }
        refreshStaleSectionsIfNeeded()
    }

    private fun refreshStaleSectionsIfNeeded() {
        if (viewModel.isStale()) loadInitial(background = true)
        if (DiscoverSectionType.STUDIOS in viewModel.triggeredSections && DiscoveryRepository.isStudiosNearYouStale()) {
            loadSection(DiscoverSectionType.STUDIOS)
        }
        if (DiscoverSectionType.EVENTS in viewModel.triggeredSections && DiscoveryRepository.isEventsThisWeekStale()) {
            loadSection(DiscoverSectionType.EVENTS)
        }
        if (DiscoverSectionType.TEACHERS in viewModel.triggeredSections && DiscoveryRepository.isTeachersYouMayLikeStale()) {
            loadSection(DiscoverSectionType.TEACHERS)
        }
    }

    override fun onPause() {
        mapView?.onPause()
        super.onPause()
    }

    override fun onLowMemory() {
        super.onLowMemory()
        mapView?.onLowMemory()
    }

    override fun onDestroyView() {
        ActiveAccountHolder.removeListener(activeAccountListener)
        mapView?.onDestroy()
        mapView = null
        mapRowBinding = null
        googleMap = null
        sectionsAdapter = null
        hasResumedOnce = false
        sectionsInitialized = false
        _binding = null
        super.onDestroyView()
    }

    companion object {
        private val DEFAULT_MAP_CENTER = LatLng(32.0853, 34.7818)
        private const val DEFAULT_MAP_ZOOM = 10.5f
        private const val LOOKAHEAD_PX = 700
    }
}

private fun Int.dp(): Int {
    return (this * android.content.res.Resources.getSystem().displayMetrics.density).toInt()
}
