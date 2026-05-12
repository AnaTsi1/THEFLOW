package com.ana.theflow.ui.detail

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.ana.theflow.MainActivity
import com.ana.theflow.data.model.discovery.DiscoveryItem
import com.ana.theflow.data.repository.ActivityTrackingRepository
import com.ana.theflow.data.repository.DiscoveryRepository
import com.ana.theflow.databinding.FragmentDetailBinding

class DetailFragment : Fragment() {

    private var _binding: FragmentDetailBinding? = null
    private val binding get() = _binding!!
    private var item: DiscoveryItem? = null
    private val activityTrackingRepository = ActivityTrackingRepository()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

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
        binding.detailBTNBack.setOnClickListener {
            (requireActivity() as MainActivity).closeDetail()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val ARG_ITEM_ID = "ARG_ITEM_ID"

        fun newInstance(itemId: String): DetailFragment {
            return DetailFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_ITEM_ID, itemId)
                }
            }
        }
    }

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
