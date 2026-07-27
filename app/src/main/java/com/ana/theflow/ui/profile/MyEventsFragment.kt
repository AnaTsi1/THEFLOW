// Screen for events the signed-in user registered for.
package com.ana.theflow.ui.profile

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.ana.theflow.MainActivity
import com.ana.theflow.R
import com.ana.theflow.data.model.post.Post
import com.ana.theflow.data.repository.PostRepository
import com.ana.theflow.databinding.FragmentMyEventsBinding
import com.ana.theflow.ui.common.PostCardRenderer

// Displays registered event posts and lets the user open or unregister from them.
class MyEventsFragment : Fragment() {

    private var _binding: FragmentMyEventsBinding? = null
    private val binding get() = _binding!!
    private val postRepository = PostRepository()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentMyEventsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.myEventsBTNBack.setOnClickListener { parentFragmentManager.popBackStack() }
        loadEvents()
    }

    // Loads registered events from the current user's saved registration list.
    private fun loadEvents() {
        setLoading(true)
        binding.myEventsLAYEvents.removeAllViews()
        postRepository.loadRegisteredEventPosts(
            onSuccess = { posts ->
                if (_binding == null) return@loadRegisteredEventPosts
                setLoading(false)
                renderEvents(posts)
            },
            onFailure = { error ->
                if (_binding == null) return@loadRegisteredEventPosts
                setLoading(false)
                binding.myEventsLBLMessage.text = error
            }
        )
    }

    // Renders event cards or an empty state.
    private fun renderEvents(posts: List<Post>) {
        binding.myEventsLBLMessage.text = if (posts.isEmpty()) {
            getString(R.string.my_events_empty)
        } else {
            resources.getQuantityString(R.plurals.my_events_count, posts.size, posts.size)
        }
        posts.forEach { post ->
            PostCardRenderer.addPostCard(
                parent = binding.myEventsLAYEvents,
                post = post,
                isEventRegistered = true,
                onOpen = { (requireActivity() as MainActivity).openPost(it.postId) },
                onEventRegister = { unregister(it) },
                onMediaOpen = { url, mediaType ->
                    (requireActivity() as MainActivity).openMediaViewer(url, mediaType)
                },
                onAuthorOpen = { authorId ->
                    (requireActivity() as MainActivity).openUserProfile(authorId)
                }
            )
        }
    }

    // Unregisters from an event and refreshes the list.
    private fun unregister(post: Post) {
        postRepository.toggleEventRegistration(
            post = post,
            onSuccess = {
                if (_binding == null) return@toggleEventRegistration
                Toast.makeText(requireContext(), R.string.post_event_registered, Toast.LENGTH_SHORT).show()
                loadEvents()
            },
            onFailure = { error ->
                if (_binding == null) return@toggleEventRegistration
                Toast.makeText(requireContext(), error, Toast.LENGTH_SHORT).show()
            }
        )
    }

    private fun setLoading(isLoading: Boolean) {
        binding.myEventsProgress.visibility = if (isLoading) View.VISIBLE else View.GONE
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
