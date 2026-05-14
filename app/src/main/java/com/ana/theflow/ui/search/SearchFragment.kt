package com.ana.theflow.ui.search

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.ana.theflow.R
import com.ana.theflow.MainActivity
import com.ana.theflow.data.model.discovery.DiscoveryItem
import com.ana.theflow.data.repository.ActivityTrackingRepository
import com.ana.theflow.data.repository.DiscoveryRepository
import com.ana.theflow.databinding.FragmentSearchBinding
import com.ana.theflow.ui.common.DiscoveryCardRenderer
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
    private val activityTrackingRepository = ActivityTrackingRepository()
    private var googleMap: GoogleMap? = null
    private var currentResults: List<DiscoveryItem> = emptyList()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentSearchBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val mapFragment = childFragmentManager.findFragmentById(R.id.search_MAP_results) as? SupportMapFragment
        mapFragment?.getMapAsync(this)

        binding.searchBTNQuery.setOnClickListener {
            runSimpleSearch()
        }
        binding.searchBTNAdvancedToggle.setOnClickListener {
            toggleSection(binding.searchLAYAdvanced)
        }
        binding.searchBTNManual.setOnClickListener {
            runManualSearch()
        }
        binding.searchBTNMapFilter.setOnClickListener {
            toggleSection(binding.searchLAYMapFilters)
        }
        binding.searchBTNMapApply.setOnClickListener {
            runMapFilter()
        }
        renderResults(
            items = DiscoveryRepository.recommendedItems(),
            label = "Recommended from your dance profile",
            title = "Recommended search"
        )
    }

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
        renderMapMarkers(currentResults)
    }

    private fun runManualSearch() {
        val query = binding.searchEDTQuery.text.toString()
        val style = binding.searchEDTStyle.text.toString()
        val location = binding.searchEDTLocation.text.toString()
        val results = DiscoveryRepository.search(
            style = style,
            level = binding.searchEDTLevel.text.toString(),
            location = location,
            teacher = binding.searchEDTTeacher.text.toString(),
            studio = binding.searchEDTStudio.text.toString(),
            time = binding.searchEDTTime.text.toString()
        ).filterByFreeText(query)
        activityTrackingRepository.trackSearch(
            query = listOf(
                query,
                style,
                binding.searchEDTLevel.text.toString(),
                location,
                binding.searchEDTTeacher.text.toString(),
                binding.searchEDTStudio.text.toString(),
                binding.searchEDTTime.text.toString()
            ).filter { it.isNotBlank() }.joinToString(" / "),
            danceStyles = listOf(style).filter { it.isNotBlank() },
            location = location
        )
        renderResults(results, "Search results", "Search results")
    }

    private fun runSimpleSearch() {
        val query = binding.searchEDTQuery.text.toString()
        val results = DiscoveryRepository.seedItems.filterByFreeText(query)

        activityTrackingRepository.trackSearch(
            query = query,
            danceStyles = emptyList(),
            location = ""
        )

        renderResults(
            items = results,
            label = if (query.isBlank()) "Recommended from your dance profile" else "Search results",
            title = if (query.isBlank()) "Recommended search" else "Search results"
        )
    }

    private fun runMapFilter() {
        val style = binding.searchEDTMapStyle.text.toString()
        val level = binding.searchEDTMapLevel.text.toString()
        val location = binding.searchEDTMapLocation.text.toString()
        val results = DiscoveryRepository.search(
            style = style,
            level = level,
            location = location,
            teacher = "",
            studio = "",
            time = ""
        )

        activityTrackingRepository.trackSearch(
            query = listOf(style, level, location).filter { it.isNotBlank() }.joinToString(" / "),
            danceStyles = listOf(style).filter { it.isNotBlank() },
            location = location
        )

        renderResults(results, "Map filtered results", "Search results")
    }

    private fun renderResults(items: List<DiscoveryItem>, label: String, title: String) {
        currentResults = items
        binding.searchLBLRecommendationsTitle.text = title
        binding.searchLBLResultSummary.text = "$label / ${items.size} results"
        binding.searchLAYResults.removeAllViews()
        renderMapMarkers(items)
        items.forEach { item ->
            DiscoveryCardRenderer.addItemCard(
                parent = binding.searchLAYResults,
                item = item,
                explanation = DiscoveryRepository.explanationFor(item),
                onOpen = { (requireActivity() as MainActivity).openDetail(it) },
                onSave = {
                    DiscoveryRepository.trackSave(it)
                    activityTrackingRepository.trackSaveItem(
                        targetType = ActivityTrackingRepository.TargetTypes.DISCOVERY_ITEM,
                        targetId = it.id,
                        targetName = it.title,
                        danceStyles = listOf(it.style),
                        location = it.location
                    )
                }
            )
        }
    }

    private fun toggleSection(section: View) {
        section.visibility = if (section.visibility == View.VISIBLE) View.GONE else View.VISIBLE
    }

    private fun List<DiscoveryItem>.filterByFreeText(query: String): List<DiscoveryItem> {
        val normalizedQuery = query.trim()
        if (normalizedQuery.isBlank()) return this

        return filter { item ->
            item.studio.contains(normalizedQuery, ignoreCase = true) ||
                item.teacher.contains(normalizedQuery, ignoreCase = true) ||
                item.title.contains(normalizedQuery, ignoreCase = true)
        }
    }

    private fun renderMapMarkers(items: List<DiscoveryItem>) {
        val map = googleMap ?: return
        map.clear()

        val mappedItems = items.mapNotNull { item ->
            item.toLatLng()?.let { position -> item to position }
        }
        if (mappedItems.isEmpty()) {
            map.moveCamera(CameraUpdateFactory.newLatLngZoom(DEFAULT_MAP_CENTER, DEFAULT_MAP_ZOOM))
            return
        }

        val boundsBuilder = LatLngBounds.Builder()
        mappedItems.forEach { (item, position) ->
            boundsBuilder.include(position)
            map.addMarker(
                MarkerOptions()
                    .position(position)
                    .title(item.title)
                    .snippet("${item.studio} / ${item.style} / ${item.time}")
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_MAGENTA))
            )?.tag = item
        }

        binding.searchMAPResults.post {
            if (mappedItems.size == 1) {
                map.animateCamera(CameraUpdateFactory.newLatLngZoom(mappedItems.first().second, DEFAULT_MAP_ZOOM))
            } else {
                map.animateCamera(CameraUpdateFactory.newLatLngBounds(boundsBuilder.build(), MAP_BOUNDS_PADDING))
            }
        }
    }

    private fun DiscoveryItem.toLatLng(): LatLng? {
        val latitude = latitude ?: return null
        val longitude = longitude ?: return null
        return LatLng(latitude, longitude)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        googleMap = null
        _binding = null
    }

    private companion object {
        private val DEFAULT_MAP_CENTER = LatLng(32.0853, 34.7818)
        private const val DEFAULT_MAP_ZOOM = 11f
        private const val MAP_BOUNDS_PADDING = 80
    }
}
