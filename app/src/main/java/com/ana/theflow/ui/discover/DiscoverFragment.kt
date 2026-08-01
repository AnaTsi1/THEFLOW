package com.ana.theflow.ui.discover

import android.location.Location
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.ana.theflow.MainActivity
import com.ana.theflow.R
import com.ana.theflow.data.model.discovery.DiscoveryItem
import com.ana.theflow.data.recommendation.GeoPoint
import com.ana.theflow.data.recommendation.LocationSourceResolver
import com.ana.theflow.data.recommendation.RecommendationSurface
import com.ana.theflow.data.repository.DiscoveryRepository
import com.ana.theflow.data.repository.UserRepository
import com.ana.theflow.databinding.FragmentDiscoverBinding
import com.ana.theflow.ui.common.DiscoveryCardRenderer
import com.ana.theflow.ui.common.ResponsiveLayout
import com.ana.theflow.ui.common.UiText
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
    private val viewModel: DiscoverViewModel by activityViewModels()
    private val userRepository = UserRepository()
    private var googleMap: GoogleMap? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentDiscoverBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val mapFragment = childFragmentManager.findFragmentById(R.id.discover_MAP_results) as? SupportMapFragment
        mapFragment?.getMapAsync(this)
        binding.discoverLAYSearchBox.setOnClickListener {
            (requireActivity() as MainActivity).openSearch()
        }
        binding.discoverBTNSearchArea.setOnClickListener {
            (requireActivity() as MainActivity).openSearch(mapMode = true)
        }
        ResponsiveLayout.constrainToReadableWidth(binding.discoverLAYContent)
        ResponsiveLayout.ensureTouchTarget(binding.discoverBTNSearchArea)
        if (viewModel.hasUsableCache()) {
            render()
            binding.discoverSCROLLRoot.post { binding.discoverSCROLLRoot.scrollTo(0, viewModel.scrollY) }
            if (viewModel.isStale()) loadData(background = true)
        } else {
            renderLoading()
            loadData(background = false)
        }
    }

    private fun loadData(background: Boolean) {
        if (viewModel.isRefreshing) return
        viewModel.isRefreshing = true
        if (!background) renderLoading()
        userRepository.loadRecommendationProfile(
            onSuccess = { profile ->
                viewModel.recommendationProfile = profile
                DiscoveryRepository.hydrateProfile(profile)
                if (_binding != null && viewModel.hasUsableCache()) render()
                loadDiscoveryContent()
            },
            onFailure = {
                viewModel.lastError = it
                if (_binding != null && viewModel.hasUsableCache()) render()
                loadDiscoveryContent()
            }
        )
    }

    private fun loadDiscoveryContent() {
        DiscoveryRepository.loadSavedItems(
            onSuccess = { if (_binding != null) render() },
            onFailure = { if (_binding != null) render() }
        )
        DiscoveryRepository.loadApprovedStudios(
            onSuccess = {
                viewModel.loadedInternal = true
                if (_binding != null) {
                    render()
                    loadActivities()
                    loadExternalStudios()
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
                    render()
                    loadExternalStudios()
                }
            }
        )
    }

    private fun loadActivities() {
        DiscoveryRepository.loadPublishedActivities(
            onSuccess = {
                viewModel.markLoaded()
                if (_binding != null) render()
            },
            onFailure = {
                viewModel.lastError = it
                viewModel.markLoaded()
                if (_binding != null) render()
            }
        )
    }

    private fun loadExternalStudios() {
        if (viewModel.requestedExternal && !viewModel.isStale()) return
        viewModel.requestedExternal = true
        val context = DiscoveryRepository.recommendationContext(
            surface = RecommendationSurface.DISCOVER,
            profileOverride = viewModel.recommendationProfile
        )
        val resolved = LocationSourceResolver.resolve(context)
        DiscoveryRepository.loadExternalStudios(
            context = requireContext(),
            query = "dance studio",
            city = resolved.displayName.ifBlank { "Israel" },
            location = resolved.point?.toLocation(),
            usePreferredCityFallback = false,
            cacheKey = "discover:${viewModel.recommendationProfile.userId}:${resolved.source}:${resolved.cityId}",
            onSuccess = {
                viewModel.loadedExternal = true
                viewModel.markLoaded()
                if (_binding != null) render()
            },
            onFailure = { error ->
                viewModel.loadedExternal = true
                viewModel.lastError = error
                viewModel.markLoaded()
                if (_binding != null) render()
            }
        )
    }

    private fun renderLoading() {
        binding.discoverPRGSearching.visibility = View.VISIBLE
        binding.discoverLBLRecommendedTitle.visibility = View.GONE
        binding.discoverLBLPopularTitle.visibility = View.GONE
        binding.discoverLBLSearchResultsTitle.visibility = View.GONE
        binding.discoverLBLMapTitle.visibility = View.GONE
        binding.discoverLAYMap.visibility = View.GONE
        binding.discoverLBLGoogleAttribution.visibility = View.GONE
        binding.discoverLAYRecommended.removeAllViews()
        binding.discoverLAYPopular.removeAllViews()
        binding.discoverLAYSearchResults.removeAllViews()
        binding.discoverLAYRecommended.addView(loadingText("Loading recommendations..."))
        binding.discoverLAYPopular.addView(loadingText("Loading nearby flow..."))
        binding.discoverLAYSearchResults.addView(loadingText("Loading studios..."))
    }

    private fun render() {
        if (!viewModel.loadedInternal) {
            renderLoading()
            return
        }
        binding.discoverPRGSearching.visibility = if (viewModel.isRefreshing && viewModel.hasUsableCache()) View.VISIBLE else View.GONE
        binding.discoverLAYRecommended.removeAllViews()
        binding.discoverLAYPopular.removeAllViews()
        binding.discoverLAYSearchResults.removeAllViews()

        val shown = mutableSetOf<String>()
        val context = DiscoveryRepository.recommendationContext(
            surface = RecommendationSurface.DISCOVER,
            profileOverride = viewModel.recommendationProfile
        )
        val recommended = DiscoveryRepository.recommendedItems(context)
            .filterNotGoogle()
            .unique()
            .takeUnique(shown, 8)
        val upcoming = DiscoveryRepository.recommendedItems(context)
            .filterNotGoogle()
            .filter { it.isActivity() && it.time.isUserFacing() }
            .unique()
            .takeUnique(shown, 4)
        val near = DiscoveryRepository.popularNearYou(context)
            .filterNotGoogle()
            .unique()
            .takeUnique(shown, 4)
        val studios = DiscoveryRepository.externalStudioItems()
            .unique()
            .take(8)

        renderSection(binding.discoverLAYRecommended, recommended, horizontal = true)
        binding.discoverLBLRecommendedTitle.visibility = if (recommended.isEmpty()) View.GONE else View.VISIBLE

        val middle = upcoming.ifEmpty { near }
        binding.discoverLBLPopularTitle.text = if (upcoming.isNotEmpty()) "Upcoming Events" else "Near You"
        renderSection(binding.discoverLAYPopular, middle, horizontal = false)
        binding.discoverLBLPopularTitle.visibility = if (middle.isEmpty()) View.GONE else View.VISIBLE

        if (!viewModel.loadedExternal) {
            binding.discoverLAYSearchResults.addView(loadingText("Loading studios..."))
            binding.discoverLBLSearchResultsTitle.visibility = View.GONE
        } else {
            renderSection(binding.discoverLAYSearchResults, studios, horizontal = true)
            binding.discoverLBLSearchResultsTitle.visibility = if (studios.isEmpty()) View.GONE else View.VISIBLE
        }
        binding.discoverLBLGoogleAttribution.visibility = if (studios.isNotEmpty()) View.VISIBLE else View.GONE

        if (recommended.isEmpty() && middle.isEmpty() && studios.isEmpty() && viewModel.loadedInternal) {
            binding.discoverLBLRecommendedTitle.visibility = View.VISIBLE
            binding.discoverLAYRecommended.addView(emptyText("No personalized recommendations yet. Save studios, follow dancers, or search styles to shape Discover."))
        }
        val mapPreviewItems = (near + studios).unique().take(12)
        val hasMapPreview = mapPreviewItems.any { it.latitude != null && it.longitude != null }
        binding.discoverLBLMapTitle.visibility = if (hasMapPreview) View.VISIBLE else View.GONE
        binding.discoverLAYMap.visibility = if (hasMapPreview) View.VISIBLE else View.GONE
        if (hasMapPreview) renderMapPreview(mapPreviewItems)
    }

    private fun renderSection(parent: LinearLayout, items: List<DiscoveryItem>, horizontal: Boolean) {
        items.forEach { item ->
            val wrapper = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    if (horizontal) 280.dp() else LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    rightMargin = if (horizontal) 10.dp() else 0
                }
            }
            DiscoveryCardRenderer.addItemCard(
                parent = wrapper,
                item = item,
                explanation = DiscoveryRepository.explanationFor(item),
                onOpen = { openDiscoveryItem(it) },
                onSave = { saveItem(it) },
                cardStyle = DiscoveryCardRenderer.CardStyle.DISCOVER_LIGHT
            )
            parent.addView(wrapper)
        }
    }

    // Internal studio listings open their business profile; everything else keeps the
    // existing Discover detail screen.
    private fun openDiscoveryItem(item: DiscoveryItem) {
        val activity = requireActivity() as MainActivity
        if (item.source != DiscoveryItem.SOURCE_GOOGLE && item.type.equals("Studio", ignoreCase = true)) {
            activity.openStudioProfile(item.id)
        } else {
            activity.openDetail(item)
        }
    }

    private fun saveItem(item: DiscoveryItem) {
        DiscoveryRepository.saveItem(
            item = item,
            onSuccess = {
                if (_binding != null) {
                    Toast.makeText(requireContext(), R.string.discover_saved, Toast.LENGTH_SHORT).show()
                    render()
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
            gravity = Gravity.CENTER_VERTICAL
            setTextColor(requireContext().getColor(R.color.discover_text_secondary))
            textSize = 14f
            setBackgroundResource(R.drawable.bg_discover_card)
            setPadding(14.dp(), 14.dp(), 14.dp(), 14.dp())
            layoutParams = LinearLayout.LayoutParams(280.dp(), LinearLayout.LayoutParams.WRAP_CONTENT)
        }
    }

    private fun loadingText(message: String): View {
        return emptyText(message).apply {
            alpha = 0.72f
        }
    }

    override fun onMapReady(map: GoogleMap) {
        googleMap = map.apply {
            uiSettings.isMapToolbarEnabled = false
            uiSettings.isZoomControlsEnabled = false
            setOnMapClickListener { (requireActivity() as MainActivity).openSearch(mapMode = true) }
            setOnMarkerClickListener {
                (requireActivity() as MainActivity).openSearch(mapMode = true)
                true
            }
        }
        renderMapPreview(DiscoveryRepository.popularNearYou().take(8))
    }

    private fun renderMapPreview(items: List<DiscoveryItem>) {
        val map = googleMap ?: return
        map.clear()
        val mapped = items.mapNotNull { item ->
            val lat = item.latitude ?: return@mapNotNull null
            val lng = item.longitude ?: return@mapNotNull null
            item to LatLng(lat, lng)
        }
        if (mapped.isEmpty()) {
            binding.discoverLBLMapTitle.visibility = View.GONE
            binding.discoverLAYMap.visibility = View.GONE
            map.moveCamera(CameraUpdateFactory.newLatLngZoom(DEFAULT_MAP_CENTER, DEFAULT_MAP_ZOOM))
            return
        }
        binding.discoverLBLMapTitle.visibility = View.VISIBLE
        binding.discoverLAYMap.visibility = View.VISIBLE
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
        binding.discoverLAYMap.post {
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

    private fun List<DiscoveryItem>.takeUnique(seen: MutableSet<String>, limit: Int): List<DiscoveryItem> {
        val out = mutableListOf<DiscoveryItem>()
        forEach { item ->
            if (out.size >= limit) return@forEach
            if (seen.add(item.key())) out.add(item)
        }
        return out
    }

    private fun DiscoveryItem.key(): String {
        return if (googlePlaceId.isNotBlank()) "google:$googlePlaceId" else "internal:$id"
    }

    private fun DiscoveryItem.isActivity(): Boolean {
        val value = displayType.ifBlank { type }.lowercase()
        return value == "class" || value == "workshop" || value == "event"
    }

    private fun String.isUserFacing(): Boolean {
        if (isBlank()) return false
        val lower = lowercase()
        return lower != "operational" && lower != "check availability" && lower != "schedule pending"
    }

    private fun GeoPoint.toLocation(): Location {
        return Location("discover_resolved_location").apply {
            latitude = this@toLocation.latitude
            longitude = this@toLocation.longitude
        }
    }

    override fun onDestroyView() {
        viewModel.scrollY = binding.discoverSCROLLRoot.scrollY
        googleMap = null
        _binding = null
        super.onDestroyView()
    }

    companion object {
        private val DEFAULT_MAP_CENTER = LatLng(32.0853, 34.7818)
        private const val DEFAULT_MAP_ZOOM = 10.5f
    }
}

private fun Int.dp(): Int {
    return (this * android.content.res.Resources.getSystem().displayMetrics.density).toInt()
}
