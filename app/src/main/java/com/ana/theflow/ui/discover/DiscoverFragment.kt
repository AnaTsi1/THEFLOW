package com.ana.theflow.ui.discover

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.ana.theflow.MainActivity
import com.ana.theflow.data.model.discovery.DiscoveryItem
import com.ana.theflow.data.repository.DiscoveryRepository
import com.ana.theflow.databinding.FragmentDiscoverBinding
import com.ana.theflow.ui.common.DiscoveryCardRenderer
import com.ana.theflow.utilities.CityOptions

class DiscoverFragment : Fragment() {

    private var _binding: FragmentDiscoverBinding? = null
    private val binding get() = _binding!!
    private var isFiltering = false
    private var isSearching = false

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
        configureFilterInputActions()
        binding.discoverBTNApplyFilters.setOnClickListener {
            applyFilters()
        }
        binding.discoverBTNClearFilters.setOnClickListener {
            clearFilters()
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
