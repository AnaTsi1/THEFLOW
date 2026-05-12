package com.ana.theflow.ui.search

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.ana.theflow.MainActivity
import com.ana.theflow.data.model.discovery.DiscoveryItem
import com.ana.theflow.data.repository.ActivityTrackingRepository
import com.ana.theflow.data.repository.DiscoveryRepository
import com.ana.theflow.databinding.FragmentSearchBinding
import com.ana.theflow.ui.common.DiscoveryCardRenderer

class SearchFragment : Fragment() {

    private var _binding: FragmentSearchBinding? = null
    private val binding get() = _binding!!
    private val activityTrackingRepository = ActivityTrackingRepository()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentSearchBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.searchBTNManual.setOnClickListener {
            runManualSearch()
        }
        binding.searchBTNRecommended.setOnClickListener {
            renderResults(DiscoveryRepository.recommendedItems(), "Recommended from your dance profile")
        }
        renderResults(DiscoveryRepository.seedItems, "All discovery results")
    }

    private fun runManualSearch() {
        val style = binding.searchEDTStyle.text.toString()
        val location = binding.searchEDTLocation.text.toString()
        val results = DiscoveryRepository.search(
            style = style,
            level = binding.searchEDTLevel.text.toString(),
            location = location,
            teacher = binding.searchEDTTeacher.text.toString(),
            studio = binding.searchEDTStudio.text.toString(),
            time = binding.searchEDTTime.text.toString()
        )
        activityTrackingRepository.trackSearch(
            query = listOf(
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
        renderResults(results, "Search results")
    }

    private fun renderResults(items: List<DiscoveryItem>, label: String) {
        binding.searchLBLResultSummary.text = "$label / ${items.size} results"
        binding.searchLAYResults.removeAllViews()
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

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
