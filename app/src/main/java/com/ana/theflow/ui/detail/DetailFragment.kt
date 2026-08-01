package com.ana.theflow.ui.detail

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.PopupMenu
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.ana.theflow.MainActivity
import com.ana.theflow.R
import com.ana.theflow.data.model.discovery.DiscoveryItem
import com.ana.theflow.data.repository.ActivityTrackingRepository
import com.ana.theflow.data.repository.DiscoveryRepository
import com.ana.theflow.data.repository.StudioClaimRepository
import com.ana.theflow.databinding.FragmentDetailBinding
import com.ana.theflow.ui.common.UiText

class DetailFragment : Fragment() {

    private var _binding: FragmentDetailBinding? = null
    private val binding get() = _binding!!
    private var item: DiscoveryItem? = null
    private val activityTrackingRepository = ActivityTrackingRepository()
    private val studioClaimRepository = StudioClaimRepository()

    // Creates and returns the fragment view.
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    // Connects the screen UI after the view is ready.
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val itemId = requireArguments().getString(ARG_ITEM_ID).orEmpty()
        item = DiscoveryRepository.itemById(itemId)
        item?.let { selected ->
            render(selected)
        }
    }

    // Draws the screen content from current data.
    private fun render(selected: DiscoveryItem) {
        binding.detailIMGHeader.text = selected.displayType.ifBlank { selected.type }.uppercase()
        binding.detailLBLTitle.text = selected.title
        binding.detailLBLMeta.text =
            "${selected.studio} / ${selected.teacher}\n${selected.style} / ${selected.level} / ${selected.location}"
        binding.detailLBLSchedule.text = detailBody(selected)
        configureExternalActions(selected)
        binding.detailBTNSave.text = if (DiscoveryRepository.isSaved(selected)) "Saved" else "Save"
        binding.detailBTNSave.isEnabled = !DiscoveryRepository.isSaved(selected)

        binding.detailBTNSave.setOnClickListener {
            binding.detailBTNSave.isEnabled = false
            binding.detailBTNSave.text = "Saving..."
            DiscoveryRepository.saveItem(
                item = selected,
                onSuccess = {
                    if (_binding == null) return@saveItem
                    binding.detailBTNSave.text = "Saved"
                    Toast.makeText(requireContext(), "Saved", Toast.LENGTH_SHORT).show()
                },
                onFailure = { error ->
                    if (_binding == null) return@saveItem
                    binding.detailBTNSave.isEnabled = true
                    binding.detailBTNSave.text = "Save"
                    Toast.makeText(requireContext(), UiText.friendlyError(error, "We could not save this item."), Toast.LENGTH_LONG).show()
                }
            )
        }
        // Following isn't meaningful for an external Google Places result - it isn't an entity
        // that actually exists in THE FLOW to follow. Only shown for internal items.
        binding.detailBTNFollow.visibility = if (selected.source == DiscoveryItem.SOURCE_GOOGLE) View.GONE else View.VISIBLE
        binding.detailBTNFollow.setOnClickListener {
            DiscoveryRepository.trackSave(selected)
            activityTrackingRepository.trackFollowUser(
                targetUserId = selected.teacher,
                targetName = selected.teacher
            )
            binding.detailBTNFollow.text = "Following"
        }

        configureClaimButton(selected)
        refreshClaimButtonState(selected)

        binding.detailBTNBack.setOnClickListener {
            (requireActivity() as MainActivity).closeDetail()
        }
    }

    // Configures the studio claim button state.
    private fun configureClaimButton(selected: DiscoveryItem) {
        configureClaimButton(
            itemType = selected.type,
            claimStatus = selected.claimStatus,
            ownerUid = selected.ownerUid,
            onClaim = { showClaimStudioDialog(selected) }
        )
    }

    // Configures the studio claim button state.
    private fun configureClaimButton(
        itemType: String,
        claimStatus: String,
        ownerUid: String,
        onClaim: () -> Unit
    ) {
        if (!itemType.equals("Studio", ignoreCase = true)) {
            binding.detailBTNClaimStudio.visibility = View.GONE
            return
        }

        binding.detailBTNClaimStudio.visibility = View.VISIBLE
        when {
            ownerUid.isNotBlank() ||
                claimStatus.equals("CLAIMED", ignoreCase = true) -> {
                binding.detailBTNClaimStudio.isEnabled = false
                binding.detailBTNClaimStudio.text = "Studio Claimed"
                binding.detailBTNClaimStudio.setOnClickListener(null)
            }

            claimStatus.equals("PENDING", ignoreCase = true) -> {
                binding.detailBTNClaimStudio.isEnabled = false
                binding.detailBTNClaimStudio.text = "Claim Pending"
                binding.detailBTNClaimStudio.setOnClickListener(null)
            }

            else -> {
                binding.detailBTNClaimStudio.isEnabled = true
                binding.detailBTNClaimStudio.text = getString(R.string.detail_claim_studio)
                binding.detailBTNClaimStudio.setOnClickListener { onClaim() }
            }
        }
    }

    // Refreshes studio claim state from Firestore.
    private fun refreshClaimButtonState(selected: DiscoveryItem) {
        if (!selected.type.equals("Studio", ignoreCase = true)) return

        studioClaimRepository.loadStudioClaimState(
            studioId = selected.id,
            onSuccess = { state ->
                if (_binding == null) return@loadStudioClaimState
                configureClaimButton(
                    itemType = selected.type,
                    claimStatus = state.claimStatus,
                    ownerUid = state.ownerUid,
                    onClaim = { showClaimStudioDialog(selected) }
                )
            },
            onFailure = {
                if (_binding == null) return@loadStudioClaimState
                configureClaimButton(selected)
            }
        )
    }

    // Opens the studio claim request screen for this item.
    private fun showClaimStudioDialog(selected: DiscoveryItem) {
        (requireActivity() as MainActivity).openStudioRequest(
            mode = "claim",
            studioId = selected.id,
            studioName = selected.studio,
            googlePlaceId = selected.googlePlaceId,
            address = selected.address,
            latitude = selected.latitude,
            longitude = selected.longitude,
            coverImageUrl = selected.coverImageUrl
        )
    }

    // Navigate/Call/Website/Maps are all secondary, situational actions (only as many as the
    // Google Place actually has data for) - showing each as its own full-size button crowded
    // this card next to the one action that actually matters here (Claim Studio). Consolidated
    // into a single overflow menu, same PopupMenu pattern PostCardRenderer already uses for its
    // own "more options" affordance.
    private fun configureExternalActions(selected: DiscoveryItem) {
        val isExternal = selected.source == DiscoveryItem.SOURCE_GOOGLE
        binding.detailLBLAttribution.visibility = if (isExternal) View.VISIBLE else View.GONE
        if (!isExternal) {
            binding.detailLAYExternalActions.visibility = View.GONE
            return
        }

        val actions = listOfNotNull(
            (selected.latitude != null && selected.longitude != null).takeIf { it }?.let {
                getString(R.string.detail_navigate) to { openUri("google.navigation:q=${selected.latitude},${selected.longitude}") }
            },
            selected.phoneNumber.takeIf { it.isNotBlank() }?.let {
                getString(R.string.detail_call_studio) to { openUri("tel:${selected.phoneNumber}") }
            },
            selected.websiteUrl.takeIf { it.isNotBlank() }?.let {
                getString(R.string.detail_open_website) to { openUri(selected.websiteUrl) }
            },
            selected.googleMapsUrl.takeIf { it.isNotBlank() }?.let {
                getString(R.string.detail_open_google_maps) to { openUri(selected.googleMapsUrl) }
            }
        )
        binding.detailLAYExternalActions.visibility = if (actions.isEmpty()) View.GONE else View.VISIBLE
        binding.detailBTNMoreActions.setOnClickListener { anchor -> showMoreActionsMenu(anchor, actions) }
    }

    private fun showMoreActionsMenu(anchor: View, actions: List<Pair<String, () -> Unit>>) {
        PopupMenu(anchor.context, anchor).apply {
            actions.forEach { (label, _) -> menu.add(label) }
            setOnMenuItemClickListener { item ->
                val action = actions.firstOrNull { it.first == item.title.toString() } ?: return@setOnMenuItemClickListener false
                action.second()
                true
            }
            show()
        }
    }

    private fun detailBody(selected: DiscoveryItem): String {
        if (selected.source != DiscoveryItem.SOURCE_GOOGLE) {
            return listOf(
                selected.time.takeIf { it.isNotBlank() && !it.equals("Schedule pending", ignoreCase = true) }?.let { "Schedule: $it" },
                selected.address.takeIf { it.isNotBlank() }?.let { "Address: $it" },
                selected.priceText.takeIf { it.isNotBlank() }?.let { "Price: $it" }
            ).filterNotNull().joinToString("\n").ifBlank { "Details will appear here when the organizer adds them." }
        }
        val ratingText = selected.rating?.let { rating ->
            val count = selected.ratingCount?.let { " ($it)" }.orEmpty()
            "Rating: ${"%.1f".format(rating)}$count"
        }
        val distanceText = selected.distanceMeters?.let { meters ->
            if (meters >= 1000) "Distance: ${"%.1f".format(meters / 1000.0)} km" else "Distance: ${meters.toInt()} m"
        }
        return listOfNotNull(
            selected.address.takeIf { it.isNotBlank() }?.let { "Address: $it" },
            ratingText,
            distanceText,
            selected.time.takeIf { it.isNotBlank() }?.let { "Status: $it" },
            selected.phoneNumber.takeIf { it.isNotBlank() }?.let { "Phone: $it" },
            selected.websiteUrl.takeIf { it.isNotBlank() }?.let { "Website: $it" },
            getString(R.string.detail_external_info_missing)
        ).joinToString("\n")
    }

    private fun openUri(uri: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uri))
        runCatching { startActivity(intent) }
            .onFailure {
                Toast.makeText(requireContext(), "No app can open this action", Toast.LENGTH_SHORT).show()
            }
    }

    // Clears the fragment binding when the view is destroyed.
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val ARG_ITEM_ID = "ARG_ITEM_ID"

        // Handles n ew in st an ce.
        fun newInstance(itemId: String): DetailFragment {
            return DetailFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_ITEM_ID, itemId)
                }
            }
        }
    }

}
