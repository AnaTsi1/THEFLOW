package com.ana.theflow.ui.discover

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.ana.theflow.MainActivity
import com.ana.theflow.data.model.discovery.DiscoveryItem
import com.ana.theflow.data.repository.DiscoveryRepository
import com.ana.theflow.databinding.FragmentDiscoverBinding
import com.ana.theflow.ui.common.DiscoveryCardRenderer

class DiscoverFragment : Fragment() {

    private var _binding: FragmentDiscoverBinding? = null
    private val binding get() = _binding!!

    // Creates and returns the fragment view.
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentDiscoverBinding.inflate(inflater, container, false)
        return binding.root
    }

    // Connects the screen UI after the view is ready.
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.discoverEDTSearch.setOnEditorActionListener { textView, _, _ ->
            DiscoveryRepository.trackSearch(textView.text.toString(), "")
            render()
            false
        }
        binding.discoverLBLExplanation.text = "Loading studios..."
        render()
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
                if (_binding != null) render()
            },
            onFailure = { error ->
                if (_binding != null) {
                    binding.discoverLBLExplanation.text = error
                    render()
                }
            }
        )
    }

    // Draws the screen content from current data.
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

    // Clears the fragment binding when the view is destroyed.
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
