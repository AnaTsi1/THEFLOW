package com.ana.theflow.ui.search

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.ana.theflow.MainActivity
import com.ana.theflow.databinding.FragmentSearchBinding
import com.ana.theflow.prototype.PrototypeItem
import com.ana.theflow.prototype.RecommendationEngine
import com.ana.theflow.ui.common.PrototypeCardRenderer

class SearchFragment : Fragment() {

    private var _binding: FragmentSearchBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentSearchBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.searchBTNManual.setOnClickListener {
            runManualSearch()
        }
        binding.searchBTNRecommended.setOnClickListener {
            renderResults(RecommendationEngine.recommendedItems(), "Recommended from your behavior")
        }
        renderResults(RecommendationEngine.allItems, "All prototype results")
    }

    private fun runManualSearch() {
        val results = RecommendationEngine.search(
            style = binding.searchEDTStyle.text.toString(),
            level = binding.searchEDTLevel.text.toString(),
            location = binding.searchEDTLocation.text.toString(),
            teacher = binding.searchEDTTeacher.text.toString(),
            studio = binding.searchEDTStudio.text.toString(),
            time = binding.searchEDTTime.text.toString()
        )
        renderResults(results, "Search results update your recommendation scores")
    }

    private fun renderResults(items: List<PrototypeItem>, label: String) {
        binding.searchLBLResultSummary.text = "$label • ${items.size} results"
        binding.searchLAYResults.removeAllViews()
        items.forEach { item ->
            PrototypeCardRenderer.addItemCard(
                parent = binding.searchLAYResults,
                item = item,
                explanation = RecommendationEngine.explanationFor(item),
                onOpen = { (requireActivity() as MainActivity).openDetail(it) },
                onSave = { RecommendationEngine.trackSave(it) }
            )
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
