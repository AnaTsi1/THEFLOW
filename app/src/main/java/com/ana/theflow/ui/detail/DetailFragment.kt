package com.ana.theflow.ui.detail

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import com.ana.theflow.MainActivity
import com.ana.theflow.data.model.discovery.DiscoveryItem
import com.ana.theflow.data.repository.ActivityTrackingRepository
import com.ana.theflow.data.repository.DiscoveryRepository
import com.ana.theflow.data.repository.StudioClaimRepository
import com.ana.theflow.databinding.FragmentDetailBinding

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
            DiscoveryRepository.trackOpen(selected)
            activityTrackingRepository.trackOpenDiscoveryItem(
                itemId = selected.id,
                itemName = selected.title,
                targetType = targetTypeFor(selected),
                danceStyles = listOf(selected.style),
                location = selected.location,
                metadata = mapOf(
                    "studio" to selected.studio,
                    "teacher" to selected.teacher,
                    "level" to selected.level
                )
            )
            render(selected)
        }
    }

    // Draws the screen content from current data.
    private fun render(selected: DiscoveryItem) {
        binding.detailLBLTitle.text = selected.title
        binding.detailLBLMeta.text =
            "${selected.studio} / ${selected.teacher}\n${selected.style} / ${selected.level} / ${selected.location}"
        binding.detailLBLSchedule.text =
            "Schedule\n${selected.time}\n\nThis detail view is ready for Firestore-backed class and studio data."

        binding.detailBTNSave.setOnClickListener {
            DiscoveryRepository.trackSave(selected)
            activityTrackingRepository.trackSaveItem(
                targetType = targetTypeFor(selected),
                targetId = selected.id,
                targetName = selected.title,
                danceStyles = listOf(selected.style),
                location = selected.location
            )
            binding.detailBTNSave.text = "Saved"
        }
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
                binding.detailBTNClaimStudio.text = "Claim Studio"
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

    // Shows the dialog for claiming a studio.
    private fun showClaimStudioDialog(selected: DiscoveryItem) {
        val context = requireContext()
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 16, 48, 0)
        }

        val justificationInput = EditText(context).apply {
            hint = "Why is this studio yours?"
            minLines = 2
        }
        val verificationInput = EditText(context).apply {
            hint = "Verification details: phone, website, Instagram..."
            minLines = 2
        }

        container.addView(justificationInput)
        container.addView(verificationInput)

        AlertDialog.Builder(context)
            .setTitle("Claim ${selected.studio}")
            .setView(container)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Submit") { _, _ ->
                submitStudioClaim(
                    selected = selected,
                    justification = justificationInput.text.toString(),
                    verificationDetails = verificationInput.text.toString()
                )
            }
            .show()
    }

    // Sends the studio claim request.
    private fun submitStudioClaim(
        selected: DiscoveryItem,
        justification: String,
        verificationDetails: String
    ) {
        binding.detailBTNClaimStudio.isEnabled = false
        binding.detailBTNClaimStudio.text = "Submitting..."

        studioClaimRepository.submitClaim(
            studioId = selected.id,
            studioName = selected.studio,
            justification = justification,
            verificationDetails = verificationDetails,
            onSuccess = {
                if (_binding == null) return@submitClaim
                binding.detailBTNClaimStudio.text = "Claim Pending"
                binding.detailBTNClaimStudio.isEnabled = false
                Toast.makeText(requireContext(), "Claim request submitted for approval", Toast.LENGTH_LONG).show()
            },
            onFailure = { error ->
                if (_binding == null) return@submitClaim
                binding.detailBTNClaimStudio.isEnabled = true
                binding.detailBTNClaimStudio.text = "Claim Studio"
                Toast.makeText(requireContext(), error, Toast.LENGTH_LONG).show()
            }
        )
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

    // Maps a discovery item type to an activity target type.
    private fun targetTypeFor(item: DiscoveryItem): String {
        return when (item.type.lowercase()) {
            "class" -> ActivityTrackingRepository.TargetTypes.CLASS
            "workshop" -> ActivityTrackingRepository.TargetTypes.WORKSHOP
            "audition" -> ActivityTrackingRepository.TargetTypes.AUDITION
            "event" -> ActivityTrackingRepository.TargetTypes.EVENT
            else -> ActivityTrackingRepository.TargetTypes.DISCOVERY_ITEM
        }
    }
}
