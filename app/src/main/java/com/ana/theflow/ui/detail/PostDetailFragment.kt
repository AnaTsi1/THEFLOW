// Detail screen for one post and its direct engagement actions.
package com.ana.theflow.ui.detail

import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.bumptech.glide.Glide
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
import com.ana.theflow.ui.common.UiText
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
    private var detailPost: Post? = null
    private var detailComments: List<PostComment> = emptyList()
    private var detailLiked: Boolean = false
    private var detailSaved: Boolean = false
    private var detailRegistered: Boolean = false
    private var isRegistrationUpdating: Boolean = false

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
                binding.postDetailLBLMessage.text = UiText.friendlyError(error, "We could not load this post.")
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
        postRepository.loadLikeCount(
            postId = post.postId,
            onSuccess = { likeCount ->
                if (_binding == null) return@loadLikeCount
                loadEngagementStateWithCount(post.copy(likesCount = likeCount), comments)
            },
            onFailure = {
                if (_binding == null) return@loadLikeCount
                loadEngagementStateWithCount(post, comments)
            }
        )
    }

    private fun loadEngagementStateWithCount(post: Post, comments: List<PostComment>) {
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
        detailPost = post
        detailComments = comments
        detailLiked = isLiked
        detailSaved = isSaved
        detailRegistered = isEventRegistered
        if (post.postType == POST_TYPE_DANCE_ACTIVITY) {
            addEventDetail(post, comments, isLiked, isSaved, isEventRegistered)
            return
        }
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
            },
            onAuthorEntityOpen = { ref -> (requireActivity() as MainActivity).openAuthorEntity(ref) }
        )
    }

    private fun addEventDetail(
        post: Post,
        comments: List<PostComment>,
        isLiked: Boolean,
        isSaved: Boolean,
        isEventRegistered: Boolean
    ) {
        val context = requireContext()
        binding.postDetailLAYContent.addView(LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(R.drawable.bg_flow_card)
            setPadding(16.dp(), 16.dp(), 16.dp(), 16.dp())
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                topMargin = 8.dp()
                bottomMargin = 12.dp()
            }

            val imageUrl = post.mediaItems.firstOrNull { it.url.isNotBlank() }?.url ?: post.mediaUrls.firstOrNull().orEmpty()
            addView(FrameLayout(context).apply {
                setBackgroundResource(R.drawable.bg_flow_media)
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 230.dp()).apply {
                    bottomMargin = 14.dp()
                }
                if (imageUrl.isNotBlank()) {
                    addView(ImageView(context).apply {
                        scaleType = ImageView.ScaleType.CENTER_CROP
                        layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
                        Glide.with(context).load(imageUrl).centerCrop().into(this)
                    })
                } else {
                    addView(TextView(context).apply {
                        text = post.activityType.ifBlank { "Dance event" }
                        gravity = Gravity.CENTER
                        setTextColor(context.getColor(R.color.flow_brand))
                        textSize = 18f
                        setTypeface(typeface, android.graphics.Typeface.BOLD)
                        layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
                    })
                }
                if (post.activityDate.isNotBlank()) {
                    addView(TextView(context).apply {
                        text = post.activityDate
                        gravity = Gravity.CENTER
                        setTextColor(context.getColor(R.color.white))
                        textSize = 12f
                        setTypeface(typeface, android.graphics.Typeface.BOLD)
                        setBackgroundResource(R.drawable.bg_flow_button_primary)
                        layoutParams = FrameLayout.LayoutParams(94.dp(), 38.dp(), Gravity.TOP or Gravity.START).apply {
                            leftMargin = 12.dp()
                            topMargin = 12.dp()
                        }
                    })
                }
            })
            addView(TextView(context).apply {
                text = post.activityType.ifBlank { post.text.ifBlank { "Dance event" } }
                setTextColor(context.getColor(R.color.flow_ink))
                textSize = 26f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
            })
            addView(TextView(context).apply {
                text = eventScheduleLine(post)
                setTextColor(context.getColor(R.color.flow_text_secondary))
                textSize = 15f
                setPadding(0, 8.dp(), 0, 0)
            })
            addView(TextView(context).apply {
                text = post.activityLocation.ifBlank { "Location to be announced" }
                setTextColor(context.getColor(R.color.flow_text_secondary))
                textSize = 15f
                setPadding(0, 5.dp(), 0, 0)
            })
            addView(TextView(context).apply {
                text = eventRegistrationLabel(post, isEventRegistered)
                setTextColor(context.getColor(eventStateColor(post, isEventRegistered)))
                textSize = 13f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                setPadding(0, 10.dp(), 0, 0)
            })
        })

        addEventInfoSection(post)
        addLocationSection(post)
        addOrganizerSection(post)
        binding.postDetailLAYContent.addView(Button(context).apply {
            text = if (isRegistrationUpdating) "Updating..." else eventRegistrationLabel(post, isEventRegistered)
            isAllCaps = false
            setTextColor(context.getColor(if (isEventRegistered) R.color.flow_brand else R.color.white))
            setBackgroundResource(if (isEventRegistered) R.drawable.bg_flow_button_secondary else R.drawable.bg_flow_button_primary)
            isEnabled = !isRegistrationUpdating && canRegister(post, isEventRegistered)
            alpha = if (isEnabled) 1f else 0.58f
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 48.dp()).apply {
                topMargin = 12.dp()
                bottomMargin = 4.dp()
            }
            setOnClickListener { toggleEventRegistration(post) }
        })

        PostCardRenderer.addCommentThread(
            parent = binding.postDetailLAYContent,
            post = post,
            comments = comments,
            title = "Event discussion",
            currentUserId = authRepository.getCurrentUserUid().orEmpty(),
            onComment = { targetPost, text -> addComment(targetPost, text) },
            onEditComment = { comment, text -> updateComment(comment, text) },
            onDeleteComment = { comment -> deleteComment(comment) },
            onLikeComment = { comment -> toggleCommentLike(comment) },
            onReplyComment = { comment, text -> addCommentReply(comment, text) },
            onReportComment = { comment -> reportComment(comment, post) },
            onAuthorOpen = { authorId -> (requireActivity() as MainActivity).openUserProfile(authorId) },
            cardStyle = PostCardRenderer.CardStyle.FLOW_LIGHT
        )
    }

    private fun addEventInfoSection(post: Post) {
        val lines = listOfNotNull(
            "Schedule: ${eventScheduleLine(post)}",
            post.activityLocation.takeIf { it.isNotBlank() }?.let { "Venue: $it" },
            post.activityLevel.takeIf { it.isNotBlank() }?.let { "Level: ${it.cleanDisplayValue()}" },
            post.activityPrice.takeIf { it.isNotBlank() }?.let { "Price: $it" },
            registrationCountLine(post),
            post.activityDescription.ifBlank { post.text }.takeIf { it.isNotBlank() }
        )
        addEventSection("Main event information", lines)
    }

    private fun addLocationSection(post: Post) {
        val location = post.activityLocation.ifBlank { return }
        addEventSection("Location", listOf(location))
    }

    private fun addOrganizerSection(post: Post) {
        val context = requireContext()
        binding.postDetailLAYContent.addView(LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundResource(R.drawable.bg_flow_card)
            setPadding(14.dp(), 14.dp(), 14.dp(), 14.dp())
            isClickable = post.authorId.isNotBlank()
            isFocusable = isClickable
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                topMargin = 12.dp()
            }
            addView(ImageView(context).apply {
                setBackgroundResource(R.drawable.bg_avatar)
                contentDescription = post.authorName.ifBlank { "Organizer" }
                scaleType = ImageView.ScaleType.CENTER_CROP
                layoutParams = LinearLayout.LayoutParams(46.dp(), 46.dp()).apply { rightMargin = 12.dp() }
                if (post.authorProfileImageUrl.isNotBlank()) Glide.with(context).load(post.authorProfileImageUrl).circleCrop().into(this)
            })
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                addView(TextView(context).apply {
                    text = post.authorName.ifBlank { "THE FLOW creator" }
                    setTextColor(context.getColor(R.color.flow_ink))
                    textSize = 15f
                    setTypeface(typeface, android.graphics.Typeface.BOLD)
                })
                addView(TextView(context).apply {
                    text = post.authorType.cleanDisplayValue().ifBlank { "Organizer" }
                    setTextColor(context.getColor(R.color.flow_text_secondary))
                    textSize = 12f
                    setPadding(0, 2.dp(), 0, 0)
                })
            })
            addView(TextView(context).apply {
                text = "View"
                setTextColor(context.getColor(R.color.flow_brand))
                textSize = 13f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
            })
            setOnClickListener { if (post.authorId.isNotBlank()) (requireActivity() as MainActivity).openUserProfile(post.authorId) }
        })
    }

    private fun addEventSection(title: String, lines: List<String>) {
        if (lines.isEmpty()) return
        val context = requireContext()
        binding.postDetailLAYContent.addView(LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(R.drawable.bg_flow_card)
            setPadding(14.dp(), 14.dp(), 14.dp(), 14.dp())
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                topMargin = 12.dp()
            }
            addView(TextView(context).apply {
                text = title
                setTextColor(context.getColor(R.color.flow_ink))
                textSize = 17f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
            })
            addView(TextView(context).apply {
                text = lines.joinToString("\n")
                setTextColor(context.getColor(R.color.flow_text_secondary))
                textSize = 14f
                setLineSpacing(4.dp().toFloat(), 1f)
                setPadding(0, 8.dp(), 0, 0)
            })
        })
    }

    private fun registrationCountLine(post: Post): String? {
        return when {
            post.activityCapacity > 0 -> "${post.registrationsCount} / ${post.activityCapacity} registered"
            post.registrationsCount > 0 -> "${post.registrationsCount} registered"
            else -> null
        }
    }

    private fun eventScheduleLine(post: Post): String {
        return listOf(post.activityDate, post.activityTime)
            .filter { it.isNotBlank() }
            .joinToString(" at ")
            .ifBlank { "Date and time to be announced" }
    }

    private fun eventRegistrationLabel(post: Post, isRegistered: Boolean): String {
        return when {
            eventHasEnded(post) -> "Event ended"
            isRegistered -> getString(R.string.post_registered)
            post.activityCapacity > 0 && post.registrationsCount >= post.activityCapacity -> "Full"
            else -> getString(R.string.post_register)
        }
    }

    private fun eventStateColor(post: Post, isRegistered: Boolean): Int {
        return when {
            eventHasEnded(post) -> R.color.flow_text_muted
            isRegistered -> R.color.flow_success
            post.activityCapacity > 0 && post.registrationsCount >= post.activityCapacity -> R.color.flow_error
            else -> R.color.flow_brand
        }
    }

    private fun canRegister(post: Post, isRegistered: Boolean): Boolean {
        if (eventHasEnded(post)) return false
        if (isRegistered) return true
        return post.activityCapacity <= 0 || post.registrationsCount < post.activityCapacity
    }

    private fun eventHasEnded(post: Post): Boolean {
        val date = post.activityDate.trim()
        if (date.isBlank()) return false
        val parsed = listOf("yyyy-MM-dd", "dd/MM/yyyy", "MMM d, yyyy").firstNotNullOfOrNull { pattern ->
            runCatching { SimpleDateFormat(pattern, Locale.US).parse(date) }.getOrNull()
        } ?: return false
        return parsed.before(Date(System.currentTimeMillis() - 24L * 60L * 60L * 1000L))
    }

    // Toggles the current user's like on one comment.
    private fun toggleCommentLike(comment: PostComment) {
        postRepository.toggleCommentLike(
            postId = comment.postId,
            commentId = comment.commentId,
            onSuccess = {
                detailComments = detailComments.map { existing ->
                    if (existing.commentId == comment.commentId) {
                        val liked = !existing.isLikedByCurrentUser
                        existing.copy(
                            isLikedByCurrentUser = liked,
                            likesCount = (existing.likesCount + if (liked) 1 else -1).coerceAtLeast(0)
                        )
                    } else {
                        existing
                    }
                }
                renderCurrentDetail()
            },
            onFailure = { error -> Toast.makeText(requireContext(), UiText.friendlyError(error, "We could not update this comment."), Toast.LENGTH_SHORT).show() }
        )
    }

    // Adds a reply to one comment and reloads the post detail.
    private fun addCommentReply(comment: PostComment, text: String) {
        val user = currentUser
        if (user == null) {
            loadCurrentUserThen { loadedUser ->
                postRepository.addCommentReply(
                    postId = comment.postId,
                    commentId = comment.commentId,
                    author = loadedUser,
                    text = text,
                    onSuccess = { appendReplyLocally(comment, loadedUser, text) },
                    onFailure = { error ->
                        Toast.makeText(requireContext(), UiText.friendlyError(error, "We could not add this reply."), Toast.LENGTH_SHORT).show()
                    }
                )
            }
            return
        }
        postRepository.addCommentReply(
            postId = comment.postId,
            commentId = comment.commentId,
            author = user,
            text = text,
            onSuccess = { appendReplyLocally(comment, user, text) },
            onFailure = { error -> Toast.makeText(requireContext(), UiText.friendlyError(error, "We could not add this reply."), Toast.LENGTH_SHORT).show() }
        )
    }

    private fun appendReplyLocally(comment: PostComment, user: User, text: String) {
        val reply = com.ana.theflow.data.model.post.PostCommentReply(
            replyId = "local_${System.currentTimeMillis()}",
            postId = comment.postId,
            commentId = comment.commentId,
            authorId = user.uid,
            authorName = "${user.firstName} ${user.lastName}".trim().ifBlank { "Dancer" },
            authorProfileImageUrl = user.profileImageUrl,
            text = text,
            createdAt = com.google.firebase.Timestamp.now()
        )
        detailComments = detailComments.map { existing ->
            if (existing.commentId == comment.commentId) existing.copy(replies = existing.replies + reply) else existing
        }
        renderCurrentDetail()
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
            onFailure = { error -> Toast.makeText(requireContext(), UiText.friendlyError(error, "We could not send this report."), Toast.LENGTH_SHORT).show() }
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
            onFailure = { error -> Toast.makeText(requireContext(), UiText.friendlyError(error, "We could not send this report."), Toast.LENGTH_SHORT).show() }
        )
    }

    // Toggles registration for this event post and reloads the detail state.
    private fun toggleEventRegistration(post: Post) {
        if (isRegistrationUpdating || !canRegister(post, detailRegistered)) return
        isRegistrationUpdating = true
        renderCurrentDetail()
        postRepository.toggleEventRegistration(
            post = post,
            onSuccess = {
                isRegistrationUpdating = false
                Toast.makeText(requireContext(), R.string.post_event_registered, Toast.LENGTH_SHORT).show()
                val registered = !detailRegistered
                detailRegistered = registered
                detailPost = (detailPost ?: post).copy(
                    registrationsCount = ((detailPost ?: post).registrationsCount + if (registered) 1 else -1).coerceAtLeast(0)
                )
                renderCurrentDetail()
            },
            onFailure = { error ->
                isRegistrationUpdating = false
                renderCurrentDetail()
                Toast.makeText(requireContext(), UiText.friendlyError(error, "We could not update your registration."), Toast.LENGTH_SHORT).show()
            }
        )
    }

    // Updates one owned comment and reloads the post detail.
    private fun updateComment(comment: PostComment, text: String) {
        postRepository.updateComment(
            postId = comment.postId,
            commentId = comment.commentId,
            text = text,
            onSuccess = { loadPost() },
            onFailure = { error -> Toast.makeText(requireContext(), UiText.friendlyError(error, "We could not update this comment."), Toast.LENGTH_SHORT).show() }
        )
    }

    // Deletes one owned comment and reloads the post detail.
    private fun deleteComment(comment: PostComment) {
        postRepository.deleteComment(
            postId = comment.postId,
            commentId = comment.commentId,
            onSuccess = { loadPost() },
            onFailure = { error -> Toast.makeText(requireContext(), UiText.friendlyError(error, "We could not delete this comment."), Toast.LENGTH_SHORT).show() }
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
            onFailure = { error -> Toast.makeText(requireContext(), UiText.friendlyError(error, "We could not hide this post."), Toast.LENGTH_SHORT).show() }
        )
    }

    private fun toggleLike(post: Post) {
        postRepository.toggleLike(
            postId = post.postId,
            onSuccess = {
                detailLiked = !detailLiked
                detailPost = (detailPost ?: post).copy(
                    likesCount = ((detailPost ?: post).likesCount + if (detailLiked) 1 else -1).coerceAtLeast(0)
                )
                renderCurrentDetail()
            },
            onFailure = { error -> Toast.makeText(requireContext(), UiText.friendlyError(error, "We could not update this like."), Toast.LENGTH_SHORT).show() }
        )
    }

    // Toggles saved state and records positive saves for personalization.
    private fun toggleSave(post: Post) {
        postRepository.toggleSave(
            post = post,
            onSuccess = { isSaved ->
                detailSaved = isSaved
                renderCurrentDetail()
            },
            onFailure = { error -> Toast.makeText(requireContext(), UiText.friendlyError(error, "We could not update this save."), Toast.LENGTH_SHORT).show() }
        )
    }

    private fun addComment(post: Post, text: String) {
        val user = currentUser
        if (user == null) {
            loadCurrentUserThen { loadedUser ->
                postRepository.addComment(
                    postId = post.postId,
                    author = loadedUser,
                    text = text,
                    onSuccess = { appendCommentLocally(post, loadedUser, text) },
                    onFailure = { error -> Toast.makeText(requireContext(), UiText.friendlyError(error, "We could not add this comment."), Toast.LENGTH_SHORT).show() }
                )
            }
            return
        }
        postRepository.addComment(
            postId = post.postId,
            author = user,
            text = text,
            onSuccess = { appendCommentLocally(post, user, text) },
            onFailure = { error -> Toast.makeText(requireContext(), UiText.friendlyError(error, "We could not add this comment."), Toast.LENGTH_SHORT).show() }
        )
    }

    private fun appendCommentLocally(post: Post, user: User, text: String) {
        val comment = PostComment(
            commentId = "local_${System.currentTimeMillis()}",
            postId = post.postId,
            authorId = user.uid,
            authorName = "${user.firstName} ${user.lastName}".trim().ifBlank { "Dancer" },
            authorProfileImageUrl = user.profileImageUrl,
            text = text,
            createdAt = com.google.firebase.Timestamp.now()
        )
        detailComments = detailComments + comment
        detailPost = (detailPost ?: post).copy(commentsCount = ((detailPost ?: post).commentsCount + 1).coerceAtLeast(0))
        renderCurrentDetail()
    }

    private fun renderCurrentDetail() {
        val post = detailPost ?: return
        addPostCard(post, detailComments, detailLiked, detailSaved, detailRegistered)
    }

    private fun loadCurrentUserThen(action: (User) -> Unit) {
        val uid = authRepository.getCurrentUserUid()
        if (uid == null) {
            Toast.makeText(requireContext(), "User is not logged in", Toast.LENGTH_SHORT).show()
            return
        }
        userRepository.getUserByUid(
            uid = uid,
            onSuccess = { user ->
                if (_binding == null) return@getUserByUid
                currentUser = user
                action(user)
            },
            onFailure = { error ->
                if (_binding == null) return@getUserByUid
                Toast.makeText(requireContext(), UiText.friendlyError(error, "We could not load your profile."), Toast.LENGTH_SHORT).show()
            }
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val ARG_POST_ID = "ARG_POST_ID"
        private const val POST_TYPE_DANCE_ACTIVITY = "dance_activity"

        fun newInstance(postId: String): PostDetailFragment {
            return PostDetailFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_POST_ID, postId)
                }
            }
        }
    }
}

private fun Int.dp(): Int {
    return (this * android.content.res.Resources.getSystem().displayMetrics.density).toInt()
}

private fun String.cleanDisplayValue(): String {
    return trim()
        .replace('_', ' ')
        .replace('-', ' ')
        .split(" ")
        .filter { it.isNotBlank() }
        .joinToString(" ") { token ->
            token.lowercase(Locale.getDefault()).replaceFirstChar { char ->
                if (char.isLowerCase()) char.titlecase(Locale.getDefault()) else char.toString()
            }
        }
}
