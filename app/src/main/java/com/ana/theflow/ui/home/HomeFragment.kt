package com.ana.theflow.ui.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import android.widget.Toast
import com.ana.theflow.MainActivity
import com.ana.theflow.R
import com.ana.theflow.data.model.post.Post
import com.ana.theflow.data.model.user.User
import com.ana.theflow.data.repository.ActivityTrackingRepository
import com.ana.theflow.data.repository.AuthRepository
import com.ana.theflow.data.repository.PostRepository
import com.ana.theflow.data.repository.UserRepository
import com.ana.theflow.databinding.FragmentHomeBinding
import com.ana.theflow.ui.common.PostCardRenderer

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    private val postRepository = PostRepository()
    private val authRepository = AuthRepository()
    private val userRepository = UserRepository()
    private val activityTrackingRepository = ActivityTrackingRepository()
    private var selectedFeed = FeedTab.FOR_YOU
    private var currentUser: User? = null

    // Creates and returns the fragment view.
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    // Connects the screen UI after the view is ready.
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.homeBTNSearch.setOnClickListener {
            (requireActivity() as MainActivity).openSearch()
        }
        binding.homeTABForYou.setOnClickListener {
            selectTab(isForYou = true)
        }
        binding.homeTABFollowing.setOnClickListener {
            selectTab(isForYou = false)
        }
        loadCurrentUser()
        selectTab(isForYou = true)
    }

    // Loads the signed-in user for comment attribution.
    private fun loadCurrentUser() {
        val uid = authRepository.getCurrentUserUid() ?: return
        userRepository.getUserByUid(
            uid = uid,
            onSuccess = { user -> currentUser = user },
            onFailure = {}
        )
    }

    // Selects a feed tab and reloads posts.
    private fun selectTab(isForYou: Boolean) {
        selectedFeed = if (isForYou) FeedTab.FOR_YOU else FeedTab.FOLLOWING
        binding.homeTABForYou.setBackgroundResource(
            if (isForYou) R.drawable.bg_button_primary else R.drawable.bg_button_secondary
        )
        binding.homeTABFollowing.setBackgroundResource(
            if (isForYou) R.drawable.bg_button_secondary else R.drawable.bg_button_primary
        )
        binding.homeTABForYou.setTextColor(
            requireContext().getColor(if (isForYou) R.color.text_primary else R.color.text_secondary)
        )
        binding.homeTABFollowing.setTextColor(
            requireContext().getColor(if (isForYou) R.color.text_secondary else R.color.text_primary)
        )
        binding.homeLBLSubtitle.text = if (isForYou) {
            "Professional posts personalized for your dancer profile"
        } else {
            "Posts from dancers, teachers, and studios you follow"
        }
        loadFeed()
    }

    // Loads posts for the current feed.
    private fun loadFeed() {
        val requestedFeed = selectedFeed
        binding.homeProgress.visibility = View.VISIBLE
        binding.homeLBLMessage.visibility = View.GONE
        binding.homeLAYPosts.removeAllViews()

        val onSuccess: (List<Post>) -> Unit = onSuccess@ { posts ->
            if (_binding == null) return@onSuccess
            if (selectedFeed != requestedFeed) return@onSuccess
            binding.homeProgress.visibility = View.GONE
            binding.homeLBLMessage.text = emptyMessageFor(requestedFeed)
            binding.homeLBLMessage.visibility = if (posts.isEmpty()) View.VISIBLE else View.GONE
            posts.forEach { post ->
                activityTrackingRepository.trackPostViewed(post)
                renderFeedPostCard(post)
            }
        }

        val onFailure: (String) -> Unit = onFailure@ { error ->
            if (_binding == null) return@onFailure
            if (selectedFeed != requestedFeed) return@onFailure
            binding.homeProgress.visibility = View.GONE
            binding.homeLBLMessage.visibility = View.VISIBLE
            binding.homeLBLMessage.text = "Could not load posts"
            Toast.makeText(requireContext(), error, Toast.LENGTH_SHORT).show()
        }

        when (requestedFeed) {
            FeedTab.FOR_YOU -> postRepository.loadForYouFeed(
                onSuccess = onSuccess,
                onFailure = onFailure
            )
            FeedTab.FOLLOWING -> postRepository.loadFollowingFeed(
                onSuccess = onSuccess,
                onFailure = onFailure
            )
        }
    }

    // Renders a feed post with like, comment, and media actions.
    private fun renderFeedPostCard(post: Post) {
        postRepository.loadComments(
            postId = post.postId,
            onSuccess = { comments ->
                if (_binding == null) return@loadComments
                postRepository.isPostLikedByCurrentUser(
                    postId = post.postId,
                    onSuccess = { isLiked ->
                        if (_binding == null) return@isPostLikedByCurrentUser
                        PostCardRenderer.addPostCard(
                            parent = binding.homeLAYPosts,
                            post = post,
                            comments = comments,
                            isLiked = isLiked,
                            onOpen = { activityTrackingRepository.trackPostOpened(it) },
                            onLike = { toggleLike(it) },
                            onComment = { targetPost, text -> addComment(targetPost, text) },
                            onMediaOpen = { url, mediaType ->
                                (requireActivity() as MainActivity).openMediaViewer(url, mediaType)
                            }
                        )
                    }
                )
            },
            onFailure = {
                if (_binding == null) return@loadComments
                PostCardRenderer.addPostCard(
                    parent = binding.homeLAYPosts,
                    post = post,
                    onOpen = { activityTrackingRepository.trackPostOpened(it) },
                    onLike = { toggleLike(it) },
                    onComment = { targetPost, text -> addComment(targetPost, text) },
                    onMediaOpen = { url, mediaType ->
                        (requireActivity() as MainActivity).openMediaViewer(url, mediaType)
                    }
                )
            }
        )
    }

    // Toggles a like and reloads the selected feed.
    private fun toggleLike(post: Post) {
        postRepository.toggleLike(
            postId = post.postId,
            onSuccess = { loadFeed() },
            onFailure = { error ->
                Toast.makeText(requireContext(), error, Toast.LENGTH_SHORT).show()
            }
        )
    }

    // Adds a comment and reloads the selected feed.
    private fun addComment(post: Post, text: String) {
        val user = currentUser
        if (user == null) {
            Toast.makeText(requireContext(), "User is not loaded yet", Toast.LENGTH_SHORT).show()
            return
        }
        postRepository.addComment(
            postId = post.postId,
            author = user,
            text = text,
            onSuccess = { loadFeed() },
            onFailure = { error ->
                Toast.makeText(requireContext(), error, Toast.LENGTH_SHORT).show()
            }
        )
    }

    // Returns the empty message for a feed tab.
    private fun emptyMessageFor(feedTab: FeedTab): String {
        return when (feedTab) {
            FeedTab.FOR_YOU -> "No posts yet. Create the first post from your profile."
            FeedTab.FOLLOWING -> "Follow dancers and studios to see their latest posts."
        }
    }

    private enum class FeedTab {
        FOR_YOU,
        FOLLOWING
    }

    // Clears the fragment binding when the view is destroyed.
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
