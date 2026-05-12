package com.ana.theflow.ui.discover

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.ana.theflow.MainActivity
import com.ana.theflow.data.model.discovery.DiscoveryItem
import com.ana.theflow.data.repository.DiscoveryRepository
import com.ana.theflow.databinding.FragmentDiscoverBinding
import com.ana.theflow.ui.common.DiscoveryCardRenderer

class DiscoverFragment : Fragment() {

    private var _binding: FragmentDiscoverBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentDiscoverBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.discoverEDTSearch.setOnEditorActionListener { textView, _, _ ->
            DiscoveryRepository.trackSearch(textView.text.toString(), "")
            render()
            false
        }
        render()
    }

    private fun render() {
        binding.discoverLBLExplanation.text = DiscoveryRepository.behaviorSummary()
        binding.discoverLAYRecommended.removeAllViews()
        binding.discoverLAYPopular.removeAllViews()

        DiscoveryRepository.recommendedItems().take(4).forEach { item ->
            addCard(binding.discoverLAYRecommended, item)
        }

        DiscoveryRepository.popularNearYou().take(3).forEach { item ->
            addCard(binding.discoverLAYPopular, item)
        }
    }

    private fun addCard(parent: android.widget.LinearLayout, item: DiscoveryItem) {
        DiscoveryCardRenderer.addItemCard(
            parent = parent,
            item = item,
            explanation = DiscoveryRepository.explanationFor(item),
            onOpen = { (requireActivity() as MainActivity).openDetail(it) },
            onSave = {
                DiscoveryRepository.trackSave(it)
                render()
            }
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
