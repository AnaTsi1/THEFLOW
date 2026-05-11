package com.ana.theflow.ui.detail

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.ana.theflow.MainActivity
import com.ana.theflow.databinding.FragmentDetailBinding
import com.ana.theflow.prototype.PrototypeItem
import com.ana.theflow.prototype.RecommendationEngine

class DetailFragment : Fragment() {

    private var _binding: FragmentDetailBinding? = null
    private val binding get() = _binding!!
    private var item: PrototypeItem? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val itemId = requireArguments().getString(ARG_ITEM_ID).orEmpty()
        item = RecommendationEngine.itemById(itemId)
        item?.let { selected ->
            RecommendationEngine.trackOpen(selected)
            render(selected)
        }
    }

    private fun render(selected: PrototypeItem) {
        binding.detailLBLTitle.text = selected.title
        binding.detailLBLMeta.text =
            "${selected.studio} • ${selected.teacher}\n${selected.style} • ${selected.level} • ${selected.location}"
        binding.detailLBLSchedule.text =
            "Schedule\n${selected.time}\n\nPrototype behavior: opening this screen increases scores for ${selected.style}, ${selected.studio}, and ${selected.teacher}."

        binding.detailBTNSave.setOnClickListener {
            RecommendationEngine.trackSave(selected)
            binding.detailBTNSave.text = "Saved"
        }
        binding.detailBTNFollow.setOnClickListener {
            RecommendationEngine.trackSave(selected)
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
}
