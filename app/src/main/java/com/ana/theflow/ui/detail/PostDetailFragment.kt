// Detail screen for one post and its direct engagement actions.
package com.ana.theflow.ui.detail

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.ana.theflow.MainActivity
import com.ana.theflow.R
import com.ana.theflow.data.model.post.Post
import com.ana.theflow.data.model.post.PostComment
import com.ana.theflow.data.model.user.User
import com.ana.theflow.data.repository.ActivityTrackingRepository
import com.ana.theflow.data.repository.AuthRepository
import com.ana.theflow.data.repository.PostRepository
import com.ana.theflow.data.repository.ReportRepository
import com.ana.theflow.data.repository.UserRepository
import com.ana.theflow.databinding.FragmentPostDetailBinding
import com.ana.theflow.ui.common.PostCardRenderer

// Displays a single social post with comments, likes, saves, and media viewing.
class PostDetailFragment : Fragment() {

    private var _binding: FragmentPostDetailBinding? = null
    private val binding get() = _binding!!
    private val postRepository = PostRepository()
    private val authRepository = AuthRepository()
    private val userRepository = UserRepository()
    private val activityTrackingRepository = ActivityTrackingRepository()
    private val reportRepository = ReportRepository()
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
                loadEngagementState(post, comments)
            },
            onFailure = {
                if (_binding == null) return@loadComments
                loadEngagementState(post, emptyList())
            }
        )
    }

    // Loads viewer-specific like and saved state before drawing the detail card.
    private fun loadEngagementState(post: Post, comments: List<PostComment>) {
        postRepository.isPostLikedByCurrentUser(
            postId = post.postId,
            onSuccess = { isLiked ->
                if (_binding == null) return@isPostLikedByCurrentUser
                postRepository.isPostSavedByCurrentUser(
                    postId = post.postId,
                    onSuccess = { isSaved ->
                        if (_binding == null) return@isPostSavedByCurrentUser
                        loadEventRegistrationState(post, comments, isLiked, isSaved)
                    },
                    onFailure = {
                        if (_binding == null) return@isPostSavedByCurrentUser
                        loadEventRegistrationState(post, comments, isLiked, isSaved = false)
                    }
                )
            },
            onFailure = {
                if (_binding == null) return@isPostLikedByCurrentUser
                loadEventRegistrationState(post, comments, isLiked = false, isSaved = false)
            }
        )
    }

    // Loads registration state for dance activity posts before drawing the detail card.
    private fun loadEventRegistrationState(
        post: Post,
        comments: List<PostComment>,
        isLiked: Boolean,
        isSaved: Boolean
    ) {
        postRepository.isEventRegisteredByCurrentUser(
            post = post,
            onSuccess = { isRegistered ->
                if (_binding == null) return@isEventRegisteredByCurrentUser
                addPostCard(post, comments, isLiked, isSaved, isRegistered)
            },
            onFailure = {
                if (_binding == null) return@isEventRegisteredByCurrentUser
                addPostCard(post, comments, isLiked, isSaved, isEventRegistered = false)
            }
        )
    }

    // Replaces the detail content with one fully configured post card.
    private fun addPostCard(
        post: Post,
        comments: List<PostComment>,
        isLiked: Boolean,
        isSaved: Boolean,
        isEventRegistered: Boolean
    ) {
        binding.postDetailProgress.visibility = View.GONE
        binding.postDetailLAYContent.removeAllViews()
        PostCardRenderer.addPostCard(
            parent = binding.postDetailLAYContent,
            post = post,
            comments = comments,
            isLiked = isLiked,
            isSaved = isSaved,
            isEventRegistered = isEventRegistered,
            currentUserId = authRepository.getCurrentUserUid().orEmpty(),
            onLike = { toggleLike(it) },
            onSave = { toggleSave(it) },
            onComment = { targetPost, text -> addComment(targetPost, text) },
            onEditComment = { comment, text -> updateComment(comment, text) },
            onDeleteComment = { comment -> deleteComment(comment) },
            onLikeComment = { comment -> toggleCommentLike(comment) },
            onReplyComment = { comment, text -> addCommentReply(comment, text) },
            onReportComment = { comment -> reportComment(comment, post) },
            onEventRegister = { toggleEventRegistration(it) },
            onReport = { reportPost(it) },
            onHide = { hidePost(it) },
            onMediaOpen = { url, mediaType ->
                (requireActivity() as MainActivity).openMediaViewer(url, mediaType)
            },
            onAuthorOpen = { authorId ->
                (requireActivity() as MainActivity).openUserProfile(authorId)
            }
        )
    }

    // Toggles the current user's like on one comment.
    private fun toggleCommentLike(comment: PostComment) {
        postRepository.toggleCommentLike(
            postId = comment.postId,
            commentId = comment.commentId,
            onSuccess = { loadPost() },
            onFailure = { error -> Toast.makeText(requireContext(), error, Toast.LENGTH_SHORT).show() }
        )
    }

    // Adds a reply to one comment and reloads the post detail.
    private fun addCommentReply(comment: PostComment, text: String) {
        val user = currentUser
        if (user == null) {
            Toast.makeText(requireContext(), "User is not loaded yet", Toast.LENGTH_SHORT).show()
            return
        }
        postRepository.addCommentReply(
            postId = comment.postId,
            commentId = comment.commentId,
            author = user,
            text = text,
            onSuccess = { loadPost() },
            onFailure = { error -> Toast.makeText(requireContext(), error, Toast.LENGTH_SHORT).show() }
        )
    }

    // Reports this post for moderation review.
    private fun reportPost(post: Post) {
        reportRepository.reportContent(
            targetType = ReportRepository.TargetTypes.POST,
            targetId = post.postId,
            targetOwnerId = post.authorId,
            postId = post.postId,
            reason = "Needs review",
            onSuccess = { Toast.makeText(requireContext(), R.string.report_sent, Toast.LENGTH_SHORT).show() },
            onFailure = { error -> Toast.makeText(requireContext(), error, Toast.LENGTH_SHORT).show() }
        )
    }

    // Reports a comment on this post for moderation review.
    private fun reportComment(comment: PostComment, post: Post) {
        reportRepository.reportContent(
            targetType = ReportRepository.TargetTypes.COMMENT,
            targetId = comment.commentId,
            targetOwnerId = comment.authorId,
            postId = post.postId,
            commentId = comment.commentId,
            reason = "Needs review",
            onSuccess = { Toast.makeText(requireContext(), R.string.report_sent, Toast.LENGTH_SHORT).show() },
            onFailure = { error -> Toast.makeText(requireContext(), error, Toast.LENGTH_SHORT).show() }
        )
    }

    // Toggles registration for this event post and reloads the detail state.
    private fun toggleEventRegistration(post: Post) {
        postRepository.toggleEventRegistration(
            post = post,
            onSuccess = {
                Toast.makeText(requireContext(), R.string.post_event_registered, Toast.LENGTH_SHORT).show()
                loadPost()
            },
            onFailure = { error -> Toast.makeText(requireContext(), error, Toast.LENGTH_SHORT).show() }
        )
    }

    // Updates one owned comment and reloads the post detail.
    private fun updateComment(comment: PostComment, text: String) {
        postRepository.updateComment(
            postId = comment.postId,
            commentId = comment.commentId,
            text = text,
            onSuccess = { loadPost() },
            onFailure = { error -> Toast.makeText(requireContext(), error, Toast.LENGTH_SHORT).show() }
        )
    }

    // Deletes one owned comment and reloads the post detail.
    private fun deleteComment(comment: PostComment) {
        postRepository.deleteComment(
            postId = comment.postId,
            commentId = comment.commentId,
            onSuccess = { loadPost() },
            onFailure = { error -> Toast.makeText(requireContext(), error, Toast.LENGTH_SHORT).show() }
        )
    }

    // Hides this post for the signed-in user and returns to the previous screen.
    private fun hidePost(post: Post) {
        postRepository.hidePostForCurrentUser(
            post = post,
            onSuccess = {
                Toast.makeText(requireContext(), R.string.post_hidden, Toast.LENGTH_SHORT).show()
                parentFragmentManager.popBackStack()
            },
            onFailure = { error -> Toast.makeText(requireContext(), error, Toast.LENGTH_SHORT).show() }
        )
    }

    private fun toggleLike(post: Post) {
        postRepository.toggleLike(
            postId = post.postId,
            onSuccess = { loadPost() },
            onFailure = { error -> Toast.makeText(requireContext(), error, Toast.LENGTH_SHORT).show() }
        )
    }

    // Toggles saved state and records positive saves for personalization.
    private fun toggleSave(post: Post) {
        postRepository.toggleSave(
            post = post,
            onSuccess = { isSaved ->
                if (isSaved) activityTrackingRepository.trackPostSaved(post)
                loadPost()
            },
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
