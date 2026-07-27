// Saved content screen for posts and discovery items kept by the signed-in user.
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
import com.ana.theflow.data.model.post.Post
import com.ana.theflow.data.repository.DiscoveryRepository
import com.ana.theflow.data.repository.PostRepository
import com.ana.theflow.databinding.FragmentSavedItemsBinding
import com.ana.theflow.ui.common.PostCardRenderer

// Combines saved social posts with saved studios/classes from Discover.
class SavedItemsFragment : Fragment() {

    private var _binding: FragmentSavedItemsBinding? = null
    private val binding get() = _binding!!
    private val postRepository = PostRepository()

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

        var savedPosts: List<Post> = emptyList()
        var savedDiscoveryItems: List<DiscoveryItem> = emptyList()
        var pendingLoads = 2
        var completed = false

        fun finishOne() {
            if (completed) return
            pendingLoads -= 1
            if (pendingLoads == 0) {
                completed = true
                setLoading(false)
                renderItems(savedPosts, savedDiscoveryItems)
            }
        }

        fun fail(message: String) {
            if (completed) return
            completed = true
            setLoading(false)
            binding.savedLBLMessage.text = message
        }

        postRepository.loadSavedPosts(
            onSuccess = { posts ->
                if (_binding == null) return@loadSavedPosts
                savedPosts = posts
                finishOne()
            },
            onFailure = { error ->
                if (_binding == null) return@loadSavedPosts
                fail(error)
            }
        )

        DiscoveryRepository.loadSavedDiscoveryItems(
            onSuccess = { items ->
                if (_binding == null) return@loadSavedDiscoveryItems
                savedDiscoveryItems = items
                finishOne()
            },
            onFailure = { error ->
                if (_binding == null) return@loadSavedDiscoveryItems
                fail(error)
            }
        )
    }

    // Renders saved posts first, then saved discovery items.
    private fun renderItems(savedPosts: List<Post>, discoveryItems: List<DiscoveryItem>) {
        val totalItems = savedPosts.size + discoveryItems.size
        binding.savedLBLMessage.text = if (totalItems == 0) {
            "No saved items yet."
        } else {
            "$totalItems saved item${if (totalItems == 1) "" else "s"}"
        }

        if (totalItems == 0) {
            binding.savedLAYItems.addView(emptyText("Save posts, studios, or classes and they will appear here."))
            return
        }

        if (savedPosts.isNotEmpty()) {
            binding.savedLAYItems.addView(sectionTitle("Saved Posts"))
            savedPosts.forEach { post -> addSavedPostCard(post) }
        }

        if (discoveryItems.isNotEmpty()) {
            binding.savedLAYItems.addView(sectionTitle("Saved Discover"))
        }
        discoveryItems.forEach { item ->
            binding.savedLAYItems.addView(savedCard(item))
        }
    }

    // Adds a saved post card with open, media, author, and unsave actions.
    private fun addSavedPostCard(post: Post) {
        PostCardRenderer.addPostCard(
            parent = binding.savedLAYItems,
            post = post,
            isSaved = true,
            onOpen = { (requireActivity() as MainActivity).openPost(it.postId) },
            onSave = { targetPost -> unsavePost(targetPost) },
            onMediaOpen = { url, mediaType ->
                (requireActivity() as MainActivity).openMediaViewer(url, mediaType)
            },
            onAuthorOpen = { authorId ->
                (requireActivity() as MainActivity).openUserProfile(authorId)
            }
        )
    }

    // Removes a saved post by toggling the current saved state off.
    private fun unsavePost(post: Post) {
        postRepository.toggleSave(
            post = post,
            onSuccess = {
                if (_binding == null) return@toggleSave
                Toast.makeText(requireContext(), "Removed from saved", Toast.LENGTH_SHORT).show()
                loadSavedItems()
            },
            onFailure = { error ->
                if (_binding == null) return@toggleSave
                Toast.makeText(requireContext(), error, Toast.LENGTH_LONG).show()
            }
        )
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

    private fun sectionTitle(title: String): TextView {
        return TextView(requireContext()).apply {
            text = title
            setTextColor(requireContext().getColor(R.color.text_primary))
            textSize = 18f
            setTypeface(null, Typeface.BOLD)
            setPadding(0, dp(10), 0, dp(6))
        }
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
