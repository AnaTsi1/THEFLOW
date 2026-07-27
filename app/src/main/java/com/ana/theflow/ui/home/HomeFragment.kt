// Home feed screen that shows recommended and followed posts with social interactions.
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
import com.ana.theflow.data.model.post.PostComment
import com.ana.theflow.data.model.user.User
import com.ana.theflow.data.repository.ActivityTrackingRepository
import com.ana.theflow.data.repository.AuthRepository
import com.ana.theflow.data.repository.PostRepository
import com.ana.theflow.data.repository.ReportRepository
import com.ana.theflow.data.repository.UserRepository
import com.ana.theflow.databinding.FragmentHomeBinding
import com.ana.theflow.ui.common.PostCardRenderer

// Hosts the main social feed and delegates post persistence to repositories.
class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    private val postRepository = PostRepository()
    private val authRepository = AuthRepository()
    private val userRepository = UserRepository()
    private val activityTrackingRepository = ActivityTrackingRepository()
    private val reportRepository = ReportRepository()
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
        binding.homeTABForYou.setOnClickListener {
            selectTab(isForYou = true)
        }
        binding.homeTABFollowing.setOnClickListener {
            selectTab(isForYou = false)
        }
        binding.homeBOXForYou.setOnClickListener {
            selectTab(isForYou = true)
        }
        binding.homeBOXFollowing.setOnClickListener {
            selectTab(isForYou = false)
        }
        binding.homeBTNCreatePost.setOnClickListener {
            (requireActivity() as MainActivity).openProfile()
        }
        binding.homeSWIPERefresh.setOnRefreshListener {
            loadFeed()
        }
        binding.homeSWIPERefresh.setColorSchemeResources(R.color.neon_purple)
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
        binding.homeTABForYou.setTextColor(
            requireContext().getColor(if (isForYou) R.color.text_primary else R.color.text_secondary)
        )
        binding.homeTABFollowing.setTextColor(
            requireContext().getColor(if (isForYou) R.color.text_secondary else R.color.text_primary)
        )
        binding.homeINDForYou.visibility = if (isForYou) View.VISIBLE else View.INVISIBLE
        binding.homeINDFollowing.visibility = if (isForYou) View.INVISIBLE else View.VISIBLE
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
            binding.homeSWIPERefresh.isRefreshing = false
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
            binding.homeSWIPERefresh.isRefreshing = false
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
                loadEngagementState(post, comments)
            },
            onFailure = {
                if (_binding == null) return@loadComments
                loadEngagementState(post, emptyList())
            }
        )
    }

    // Loads per-user engagement state before rendering a post card.
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

    // Loads event registration state for activity posts before rendering.
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
                addFeedPostCard(post, comments, isLiked, isSaved, isRegistered)
            },
            onFailure = {
                if (_binding == null) return@isEventRegisteredByCurrentUser
                addFeedPostCard(post, comments, isLiked, isSaved, isEventRegistered = false)
            }
        )
    }

    // Adds the fully prepared post card to the feed.
    private fun addFeedPostCard(
        post: Post,
        comments: List<PostComment>,
        isLiked: Boolean,
        isSaved: Boolean,
        isEventRegistered: Boolean
    ) {
        PostCardRenderer.addPostCard(
            parent = binding.homeLAYPosts,
            post = post,
            comments = comments,
            isLiked = isLiked,
            isSaved = isSaved,
            isEventRegistered = isEventRegistered,
            currentUserId = authRepository.getCurrentUserUid().orEmpty(),
            onOpen = { activityTrackingRepository.trackPostOpened(it) },
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
            },
            cardStyle = PostCardRenderer.CardStyle.FLOW_LIGHT
        )
    }

    // Toggles the current user's like on one comment.
    private fun toggleCommentLike(comment: PostComment) {
        postRepository.toggleCommentLike(
            postId = comment.postId,
            commentId = comment.commentId,
            onSuccess = { loadFeed() },
            onFailure = { error ->
                Toast.makeText(requireContext(), error, Toast.LENGTH_SHORT).show()
            }
        )
    }

    // Adds a reply to one comment.
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
            onSuccess = { loadFeed() },
            onFailure = { error ->
                Toast.makeText(requireContext(), error, Toast.LENGTH_SHORT).show()
            }
        )
    }

    // Reports a post for moderation review.
    private fun reportPost(post: Post) {
        reportRepository.reportContent(
            targetType = ReportRepository.TargetTypes.POST,
            targetId = post.postId,
            targetOwnerId = post.authorId,
            postId = post.postId,
            reason = "Needs review",
            onSuccess = {
                Toast.makeText(requireContext(), R.string.report_sent, Toast.LENGTH_SHORT).show()
            },
            onFailure = { error ->
                Toast.makeText(requireContext(), error, Toast.LENGTH_SHORT).show()
            }
        )
    }

    // Reports a comment without changing the comment itself.
    private fun reportComment(comment: PostComment, post: Post) {
        reportRepository.reportContent(
            targetType = ReportRepository.TargetTypes.COMMENT,
            targetId = comment.commentId,
            targetOwnerId = comment.authorId,
            postId = post.postId,
            commentId = comment.commentId,
            reason = "Needs review",
            onSuccess = {
                Toast.makeText(requireContext(), R.string.report_sent, Toast.LENGTH_SHORT).show()
            },
            onFailure = { error ->
                Toast.makeText(requireContext(), error, Toast.LENGTH_SHORT).show()
            }
        )
    }

    // Toggles registration on an event post and refreshes the feed.
    private fun toggleEventRegistration(post: Post) {
        postRepository.toggleEventRegistration(
            post = post,
            onSuccess = {
                Toast.makeText(requireContext(), R.string.post_event_registered, Toast.LENGTH_SHORT).show()
                loadFeed()
            },
            onFailure = { error ->
                Toast.makeText(requireContext(), error, Toast.LENGTH_SHORT).show()
            }
        )
    }

    // Updates one owned comment and refreshes the feed card state.
    private fun updateComment(comment: PostComment, text: String) {
        postRepository.updateComment(
            postId = comment.postId,
            commentId = comment.commentId,
            text = text,
            onSuccess = { loadFeed() },
            onFailure = { error ->
                Toast.makeText(requireContext(), error, Toast.LENGTH_SHORT).show()
            }
        )
    }

    // Deletes one owned comment and refreshes the feed card state.
    private fun deleteComment(comment: PostComment) {
        postRepository.deleteComment(
            postId = comment.postId,
            commentId = comment.commentId,
            onSuccess = { loadFeed() },
            onFailure = { error ->
                Toast.makeText(requireContext(), error, Toast.LENGTH_SHORT).show()
            }
        )
    }

    // Hides a post from the current user's feed.
    private fun hidePost(post: Post) {
        postRepository.hidePostForCurrentUser(
            post = post,
            onSuccess = {
                Toast.makeText(requireContext(), R.string.post_hidden, Toast.LENGTH_SHORT).show()
                loadFeed()
            },
            onFailure = { error ->
                Toast.makeText(requireContext(), error, Toast.LENGTH_SHORT).show()
            }
        )
    }

    // Toggles saved state and tracks positive saves for recommendations.
    private fun toggleSave(post: Post) {
        postRepository.toggleSave(
            post = post,
            onSuccess = { isSaved ->
                if (isSaved) activityTrackingRepository.trackPostSaved(post)
                loadFeed()
            },
            onFailure = { error ->
                Toast.makeText(requireContext(), error, Toast.LENGTH_SHORT).show()
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
            FeedTab.FOR_YOU -> "Your feed is quiet right now. Create a post from your profile or check back soon."
            FeedTab.FOLLOWING -> "Follow dancers, teachers, and studios to build a more personal feed."
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
