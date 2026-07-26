package com.ana.theflow.ui.detail

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.ana.theflow.MainActivity
import com.ana.theflow.data.model.post.Post
import com.ana.theflow.data.model.user.User
import com.ana.theflow.data.repository.AuthRepository
import com.ana.theflow.data.repository.PostRepository
import com.ana.theflow.data.repository.UserRepository
import com.ana.theflow.databinding.FragmentPostDetailBinding
import com.ana.theflow.ui.common.PostCardRenderer

class PostDetailFragment : Fragment() {

    private var _binding: FragmentPostDetailBinding? = null
    private val binding get() = _binding!!
    private val postRepository = PostRepository()
    private val authRepository = AuthRepository()
    private val userRepository = UserRepository()
    private var currentUser: User? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentPostDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.postDetailBTNBack.setOnClickListener { parentFragmentManager.popBackStack() }
        loadCurrentUser()
        loadPost()
    }

    private fun loadCurrentUser() {
        val uid = authRepository.getCurrentUserUid() ?: return
        userRepository.getUserByUid(uid, { currentUser = it }, {})
    }

    private fun loadPost() {
        val postId = requireArguments().getString(ARG_POST_ID).orEmpty()
        binding.postDetailProgress.visibility = View.VISIBLE
        binding.postDetailLBLMessage.visibility = View.GONE
        postRepository.loadPostById(
            postId = postId,
            onSuccess = { post ->
                if (_binding == null) return@loadPostById
                renderPost(post)
            },
            onFailure = { error ->
                if (_binding == null) return@loadPostById
                binding.postDetailProgress.visibility = View.GONE
                binding.postDetailLBLMessage.text = error
                binding.postDetailLBLMessage.visibility = View.VISIBLE
            }
        )
    }

    private fun renderPost(post: Post) {
        postRepository.loadComments(
            postId = post.postId,
            onSuccess = { comments ->
                if (_binding == null) return@loadComments
                postRepository.isPostLikedByCurrentUser(
                    postId = post.postId,
                    onSuccess = { isLiked ->
                        if (_binding == null) return@isPostLikedByCurrentUser
                        binding.postDetailProgress.visibility = View.GONE
                        binding.postDetailLAYContent.removeAllViews()
                        PostCardRenderer.addPostCard(
                            parent = binding.postDetailLAYContent,
                            post = post,
                            comments = comments,
                            isLiked = isLiked,
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
                binding.postDetailProgress.visibility = View.GONE
                binding.postDetailLAYContent.removeAllViews()
                PostCardRenderer.addPostCard(
                    parent = binding.postDetailLAYContent,
                    post = post,
                    onLike = { toggleLike(it) },
                    onComment = { targetPost, text -> addComment(targetPost, text) },
                    onMediaOpen = { url, mediaType ->
                        (requireActivity() as MainActivity).openMediaViewer(url, mediaType)
                    }
                )
            }
        )
    }

    private fun toggleLike(post: Post) {
        postRepository.toggleLike(
            postId = post.postId,
            onSuccess = { loadPost() },
            onFailure = { error -> Toast.makeText(requireContext(), error, Toast.LENGTH_SHORT).show() }
        )
    }

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
            onSuccess = { loadPost() },
            onFailure = { error -> Toast.makeText(requireContext(), error, Toast.LENGTH_SHORT).show() }
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val ARG_POST_ID = "ARG_POST_ID"

        fun newInstance(postId: String): PostDetailFragment {
            return PostDetailFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_POST_ID, postId)
                }
            }
        }
    }
}
