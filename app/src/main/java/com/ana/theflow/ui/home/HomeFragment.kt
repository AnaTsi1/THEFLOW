package com.ana.theflow.ui.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import android.widget.Toast
import com.ana.theflow.MainActivity
import com.ana.theflow.R
import com.ana.theflow.data.repository.ActivityTrackingRepository
import com.ana.theflow.data.repository.PostRepository
import com.ana.theflow.databinding.FragmentHomeBinding
import com.ana.theflow.ui.common.PostCardRenderer

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    private val postRepository = PostRepository()
    private val activityTrackingRepository = ActivityTrackingRepository()

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
        loadFeed()
    }

    private fun selectTab(isForYou: Boolean) {
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
    }

    private fun loadFeed() {
        binding.homeProgress.visibility = View.VISIBLE
        binding.homeLBLMessage.visibility = View.GONE
        binding.homeLAYPosts.removeAllViews()

        postRepository.loadFeed(
            onSuccess = { posts ->
                binding.homeProgress.visibility = View.GONE
                binding.homeLBLMessage.visibility = if (posts.isEmpty()) View.VISIBLE else View.GONE
                posts.forEach { post ->
                    PostCardRenderer.addPostCard(
                        parent = binding.homeLAYPosts,
                        post = post,
                        onOpen = {
                            activityTrackingRepository.trackViewPost(
                                postId = it.postId,
                                authorName = it.authorName,
                                authorType = it.authorType
                            )
                        }
                    )
                }
            },
            onFailure = { error ->
                binding.homeProgress.visibility = View.GONE
                binding.homeLBLMessage.visibility = View.VISIBLE
                binding.homeLBLMessage.text = "Could not load posts"
                Toast.makeText(requireContext(), error, Toast.LENGTH_SHORT).show()
            }
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
