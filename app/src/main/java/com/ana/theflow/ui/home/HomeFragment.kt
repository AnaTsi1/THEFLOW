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
import com.ana.theflow.data.repository.ActivityTrackingRepository
import com.ana.theflow.data.repository.PostRepository
import com.ana.theflow.databinding.FragmentHomeBinding
import com.ana.theflow.ui.common.PostCardRenderer

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    private val postRepository = PostRepository()
    private val activityTrackingRepository = ActivityTrackingRepository()
    private var selectedFeed = FeedTab.FOR_YOU

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

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
        selectTab(isForYou = true)
    }

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
                PostCardRenderer.addPostCard(
                    parent = binding.homeLAYPosts,
                    post = post,
                    onOpen = {
                        activityTrackingRepository.trackPostOpened(it)
                    }
                )
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

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
