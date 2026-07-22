package com.ana.theflow.ui.profile

import android.graphics.Typeface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.ana.theflow.MainActivity
import com.ana.theflow.R
import com.ana.theflow.data.model.discovery.DiscoveryItem
import com.ana.theflow.data.repository.DiscoveryRepository
import com.ana.theflow.databinding.FragmentSavedItemsBinding

class SavedItemsFragment : Fragment() {

    private var _binding: FragmentSavedItemsBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentSavedItemsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.savedBTNBack.setOnClickListener {
            parentFragmentManager.popBackStack()
        }
        binding.savedBTNRefresh.setOnClickListener {
            loadSavedItems()
        }
        loadSavedItems()
    }

    private fun loadSavedItems() {
        setLoading(true)
        binding.savedLAYItems.removeAllViews()

        DiscoveryRepository.loadSavedDiscoveryItems(
            onSuccess = { items ->
                if (_binding == null) return@loadSavedDiscoveryItems
                setLoading(false)
                renderItems(items)
            },
            onFailure = { error ->
                if (_binding == null) return@loadSavedDiscoveryItems
                setLoading(false)
                binding.savedLBLMessage.text = error
            }
        )
    }

    private fun renderItems(items: List<DiscoveryItem>) {
        binding.savedLBLMessage.text = if (items.isEmpty()) {
            "No saved items yet."
        } else {
            "${items.size} saved item${if (items.size == 1) "" else "s"}"
        }

        if (items.isEmpty()) {
            binding.savedLAYItems.addView(emptyText("Save studios or classes from Discover and they will appear here."))
            return
        }

        items.forEach { item ->
            binding.savedLAYItems.addView(savedCard(item))
        }
    }

    private fun savedCard(item: DiscoveryItem): View {
        val context = requireContext()
        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(R.drawable.bg_card)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dp(12)
            }
        }

        card.addView(TextView(context).apply {
            text = item.title.ifBlank { item.studio.ifBlank { "Saved item" } }
            setTextColor(context.getColor(R.color.text_primary))
            textSize = 18f
            setTypeface(null, Typeface.BOLD)
        })

        card.addView(TextView(context).apply {
            text = listOf(
                item.type,
                item.style,
                item.level,
                item.location,
                item.time
            ).filter { it.isNotBlank() }.joinToString(" / ")
            setTextColor(context.getColor(R.color.text_secondary))
            textSize = 14f
            setPadding(0, dp(8), 0, 0)
        })

        val actions = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(12)
            }
        }

        actions.addView(Button(context).apply {
            text = "Open"
            setTextColor(context.getColor(R.color.text_primary))
            setTypeface(null, Typeface.BOLD)
            setBackgroundResource(R.drawable.bg_button_primary)
            layoutParams = LinearLayout.LayoutParams(0, dp(48), 1f)
            setOnClickListener {
                DiscoveryRepository.rememberItems(listOf(item))
                (requireActivity() as MainActivity).openDetail(item)
            }
        })

        actions.addView(Button(context).apply {
            text = "Unsave"
            setTextColor(context.getColor(R.color.text_primary))
            setBackgroundResource(R.drawable.bg_button_secondary)
            layoutParams = LinearLayout.LayoutParams(0, dp(48), 1f).apply {
                leftMargin = dp(10)
            }
            setOnClickListener {
                isEnabled = false
                DiscoveryRepository.unsaveItem(
                    item = item,
                    onSuccess = {
                        if (_binding == null) return@unsaveItem
                        Toast.makeText(requireContext(), "Removed from saved", Toast.LENGTH_SHORT).show()
                        loadSavedItems()
                    },
                    onFailure = { error ->
                        if (_binding == null) return@unsaveItem
                        isEnabled = true
                        Toast.makeText(requireContext(), error, Toast.LENGTH_LONG).show()
                    }
                )
            }
        })

        card.addView(actions)
        return card
    }

    private fun emptyText(message: String): TextView {
        return TextView(requireContext()).apply {
            text = message
            setTextColor(requireContext().getColor(R.color.text_muted))
            textSize = 14f
        }
    }

    private fun setLoading(isLoading: Boolean) {
        binding.savedProgress.visibility = if (isLoading) View.VISIBLE else View.GONE
        binding.savedBTNRefresh.isEnabled = !isLoading
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
