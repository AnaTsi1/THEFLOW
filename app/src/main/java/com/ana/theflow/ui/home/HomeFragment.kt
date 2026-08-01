// Home feed screen that shows recommended and followed posts with social interactions.
package com.ana.theflow.ui.home

import android.graphics.Rect
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.view.GravityCompat
import com.ana.theflow.MainActivity
import com.ana.theflow.R
import com.ana.theflow.data.model.account.ActiveAccount
import com.ana.theflow.data.model.post.Post
import com.ana.theflow.data.model.post.PostComment
import com.ana.theflow.data.model.user.User
import com.ana.theflow.data.recommendation.RecommendationSurface
import com.ana.theflow.data.repository.ActivityTrackingRepository
import com.ana.theflow.data.repository.AuthRepository
import com.ana.theflow.data.repository.PostRepository
import com.ana.theflow.data.repository.ReportRepository
import com.ana.theflow.data.repository.UserRepository
import com.ana.theflow.data.session.ActiveAccountHolder
import com.ana.theflow.databinding.FragmentHomeBinding
import com.ana.theflow.ui.common.PostCardRenderer
import com.ana.theflow.ui.common.ResponsiveLayout
import com.ana.theflow.ui.common.UiText
import androidx.fragment.app.viewModels
import com.google.firebase.Timestamp

// Hosts the main social feed and delegates post persistence to repositories.
class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    private val postRepository = PostRepository()
    private val authRepository = AuthRepository()
    private val userRepository = UserRepository()
    private val activityTrackingRepository = ActivityTrackingRepository()
    private val reportRepository = ReportRepository()
    private val viewModel: HomeViewModel by viewModels()
    private var currentUser: User? = null
    private val impressionTrackedThisSession = mutableSetOf<String>()
    private val activeAccountListener: (ActiveAccount) -> Unit = {
        if (_binding != null) renderActiveAccountChip()
    }

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
            showCreationMenu()
        }
        binding.homeBTNMenu.setOnClickListener {
            binding.homeDRAWER.openDrawer(GravityCompat.START)
        }
        setupSideMenu()
        binding.homeLAYAccount.setOnClickListener {
            (requireActivity() as MainActivity).openAccountSwitcher()
        }
        renderActiveAccountChip()
        binding.homeSWIPERefresh.setOnRefreshListener {
            loadFeed(forceRefresh = true)
        }
        binding.homeSCROLLFeed.setOnScrollChangeListener { _, _, _, _, _ ->
            scheduleVisibleImpressionTracking()
        }
        binding.homeSWIPERefresh.setColorSchemeResources(R.color.neon_purple)
        ResponsiveLayout.constrainToReadableWidth(
            binding.homeLAYTop,
            binding.homeLAYTabs,
            binding.homeLBLMessage,
            binding.homeSWIPERefresh
        )
        ResponsiveLayout.ensureTouchTarget(binding.homeBTNCreatePost)
        ResponsiveLayout.ensureTouchTarget(binding.homeBTNMenu)
        ActiveAccountHolder.addListener(activeAccountListener)
        loadCurrentUser()
        renderSelectedTab()
        if (viewModel.hasCache()) {
            renderFeed()
            binding.homeSCROLLFeed.post { binding.homeSCROLLFeed.scrollTo(0, viewModel.scrollY()) }
            if (viewModel.isStale()) loadFeed(background = true)
        } else {
            selectTab(isForYou = viewModel.selectedFeed == HomeFeedTab.FOR_YOU)
        }
    }

    fun closeDrawerIfOpen(): Boolean {
        if (_binding == null) return false
        if (!binding.homeDRAWER.isDrawerOpen(GravityCompat.START)) return false
        binding.homeDRAWER.closeDrawer(GravityCompat.START)
        return true
    }

    private fun setupSideMenu() {
        val activity = requireActivity() as MainActivity
        fun openFromDrawer(selectedRow: View, action: () -> Unit) {
            listOf(
                binding.homeMENUSwitchAccount,
                binding.homeMENUEvents,
                binding.homeMENUJobs,
                binding.homeMENUSaved,
                binding.homeMENUSettings
            ).forEach { it.isSelected = it == selectedRow }
            binding.homeDRAWER.closeDrawer(GravityCompat.START)
            binding.root.postDelayed(action, 180L)
        }
        listOf(
            binding.homeMENUSwitchAccount,
            binding.homeMENUEvents,
            binding.homeMENUJobs,
            binding.homeMENUSaved,
            binding.homeMENUSettings
        ).forEach { ResponsiveLayout.ensureTouchTarget(it) }
        binding.homeMENUSwitchAccount.setOnClickListener { openFromDrawer(it) { activity.openAccountSwitcher() } }
        binding.homeMENUEvents.setOnClickListener { openFromDrawer(it) { activity.openEvents() } }
        binding.homeMENUJobs.setOnClickListener { openFromDrawer(it) { activity.openJobs() } }
        binding.homeMENUSaved.setOnClickListener { openFromDrawer(it) { activity.openSavedItems() } }
        binding.homeMENUSettings.setOnClickListener { openFromDrawer(it) { activity.openSettings() } }
    }

    // Reflects the currently active account ("Personal" or the active studio's name) in the chip.
    private fun renderActiveAccountChip() {
        val summary = (requireActivity() as MainActivity).activeAccountSummary()
        binding.homeLBLAccount.text = summary?.displayName?.takeIf { summary.subtitle != "Personal account" }
            ?.let { "Posting as: $it" }
            ?: getString(R.string.home_posting_as_personal)
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

    private fun showCreationMenu() {
        val context = requireContext()
        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(18.dp(), 18.dp(), 18.dp(), 8.dp())
            addView(TextView(context).apply {
                text = "What would you like to create?"
                setTextColor(context.getColor(R.color.flow_ink))
                textSize = 19f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
            })
        }
        val dialog = AlertDialog.Builder(context)
            .setView(content)
            .create()

        content.addView(creationChoice("Post", "Share a thought, media, music, feeling, or dance moment.") {
            dialog.dismiss()
            (requireActivity() as MainActivity).openPostCreation()
        })
        content.addView(creationChoice("Event", "Create a class, social, workshop, audition, or dance gathering.") {
            dialog.dismiss()
            (requireActivity() as MainActivity).openEventCreation()
        })
        content.addView(creationChoice("Collaboration", "Find dancers, teachers, creators, studios, or project partners.") {
            dialog.dismiss()
            (requireActivity() as MainActivity).openCollaborationCreation()
        })
        dialog.show()
    }

    private fun creationChoice(title: String, subtitle: String, onClick: () -> Unit): View {
        val context = requireContext()
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            setBackgroundResource(R.drawable.bg_flow_button_secondary)
            isClickable = true
            isFocusable = true
            setPadding(14.dp(), 10.dp(), 14.dp(), 10.dp())
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = 10.dp()
            }
            minimumHeight = 72.dp()
            addView(TextView(context).apply {
                text = title
                setTextColor(context.getColor(R.color.flow_brand))
                textSize = 15f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
            })
            addView(TextView(context).apply {
                text = subtitle
                setTextColor(context.getColor(R.color.flow_text_secondary))
                textSize = 12f
                setPadding(0, 3.dp(), 0, 0)
            })
            setOnClickListener { onClick() }
        }
    }

    // Selects a feed tab and reloads posts.
    private fun selectTab(isForYou: Boolean) {
        viewModel.saveScroll(binding.homeSCROLLFeed.scrollY)
        viewModel.selectedFeed = if (isForYou) HomeFeedTab.FOR_YOU else HomeFeedTab.FOLLOWING
        renderSelectedTab()
        if (viewModel.hasCache()) {
            renderFeed()
            binding.homeSCROLLFeed.post { binding.homeSCROLLFeed.scrollTo(0, viewModel.scrollY()) }
            if (viewModel.isStale()) loadFeed(background = true)
        } else {
            loadFeed()
        }
    }

    private fun renderSelectedTab() {
        val isForYou = viewModel.selectedFeed == HomeFeedTab.FOR_YOU
        binding.homeTABForYou.setTextColor(
            requireContext().getColor(if (isForYou) R.color.text_primary else R.color.text_secondary)
        )
        binding.homeTABFollowing.setTextColor(
            requireContext().getColor(if (isForYou) R.color.text_secondary else R.color.text_primary)
        )
        binding.homeINDForYou.visibility = if (isForYou) View.VISIBLE else View.INVISIBLE
        binding.homeINDFollowing.visibility = if (isForYou) View.INVISIBLE else View.VISIBLE
    }

    // Loads posts for the current feed.
    private fun loadFeed(forceRefresh: Boolean = false, background: Boolean = false) {
        val requestedFeed = viewModel.selectedFeed
        val requestId = viewModel.nextRequestId(requestedFeed)
        val hasCache = viewModel.items().isNotEmpty()
        viewModel.isLoading = !hasCache && !background
        viewModel.isRefreshing = forceRefresh || background
        binding.homeProgress.visibility = if (viewModel.isLoading) View.VISIBLE else View.GONE
        binding.homeLBLMessage.visibility = View.GONE
        if (!hasCache) binding.homeLAYPosts.removeAllViews()

        val onSuccess: (List<Post>) -> Unit = onSuccess@ { posts ->
            if (_binding == null) return@onSuccess
            if (viewModel.requestIdFor(requestedFeed) != requestId) return@onSuccess
            viewModel.setItems(requestedFeed, posts.distinctBy { it.postId }.map { HomeFeedItem(post = it) })
            if (viewModel.selectedFeed == requestedFeed) renderFeed()
            posts.distinctBy { it.postId }.forEach { post -> hydrateFeedPost(post, requestId, requestedFeed) }
        }

        val onFailure: (String) -> Unit = onFailure@ { error ->
            if (_binding == null) return@onFailure
            if (viewModel.requestIdFor(requestedFeed) != requestId) return@onFailure
            viewModel.isLoading = false
            viewModel.isRefreshing = false
            viewModel.error = error
            binding.homeProgress.visibility = View.GONE
            binding.homeSWIPERefresh.isRefreshing = false
            if (viewModel.items().isEmpty()) {
                binding.homeLBLMessage.visibility = View.VISIBLE
                binding.homeLBLMessage.text = UiText.friendlyError(error, "Could not load posts")
            } else {
                Toast.makeText(requireContext(), "Feed could not refresh", Toast.LENGTH_SHORT).show()
            }
        }

        when (requestedFeed) {
            HomeFeedTab.FOR_YOU -> postRepository.loadForYouFeed(
                onSuccess = onSuccess,
                onFailure = onFailure
            )
            HomeFeedTab.FOLLOWING -> postRepository.loadFollowingFeed(
                onSuccess = onSuccess,
                onFailure = onFailure
            )
        }
    }

    private fun renderFeed() {
        val previousScrollY = binding.homeSCROLLFeed.scrollY
        val hadRenderedContent = binding.homeLAYPosts.childCount > 0
        binding.homeProgress.visibility = if (viewModel.isLoading) View.VISIBLE else View.GONE
        binding.homeSWIPERefresh.isRefreshing = viewModel.isRefreshing && viewModel.items().isNotEmpty()
        binding.homeLAYPosts.removeAllViews()
        val items = viewModel.items()
        binding.homeLBLMessage.text = emptyMessageFor(viewModel.selectedFeed)
        binding.homeLBLMessage.visibility = if (items.isEmpty() && !viewModel.isLoading) View.VISIBLE else View.GONE
        if (items.isEmpty() && !viewModel.isLoading) addEmptyFeedAction()
        items.forEach { addFeedPostCard(it) }
        if (hadRenderedContent) {
            binding.homeSCROLLFeed.post { binding.homeSCROLLFeed.scrollTo(0, previousScrollY) }
        }
        scheduleVisibleImpressionTracking()
    }

    private fun scheduleVisibleImpressionTracking() {
        val binding = _binding ?: return
        binding.homeLAYPosts.removeCallbacks(trackVisibleImpressionsRunnable)
        binding.homeLAYPosts.postDelayed(trackVisibleImpressionsRunnable, IMPRESSION_VISIBLE_DELAY_MS)
    }

    private val trackVisibleImpressionsRunnable = Runnable {
        val binding = _binding ?: return@Runnable
        if (!isResumed || viewModel.selectedFeed != HomeFeedTab.FOR_YOU) return@Runnable
        val visiblePosts = mutableListOf<Post>()
        val viewport = Rect()
        binding.homeSCROLLFeed.getGlobalVisibleRect(viewport)
        for (index in 0 until binding.homeLAYPosts.childCount) {
            val child = binding.homeLAYPosts.getChildAt(index)
            val item = viewModel.items().getOrNull(index) ?: continue
            val key = "${viewModel.selectedFeed.name}:${item.post.postId}"
            if (key in impressionTrackedThisSession) continue
            if (isMeaningfullyVisible(child, viewport)) {
                impressionTrackedThisSession.add(key)
                visiblePosts.add(item.post)
            }
            if (visiblePosts.size >= MAX_IMPRESSIONS_PER_BATCH) break
        }
        if (visiblePosts.isNotEmpty()) {
            activityTrackingRepository.trackPostImpressions(visiblePosts, RecommendationSurface.FOR_YOU)
        }
    }

    private fun isMeaningfullyVisible(view: View, viewport: Rect): Boolean {
        if (!view.isShown || view.height <= 0) return false
        val rect = Rect()
        if (!view.getGlobalVisibleRect(rect)) return false
        val visibleHeight = rect.height().coerceAtMost(view.height)
        val visibleRatio = visibleHeight.toFloat() / view.height.toFloat()
        return visibleRatio >= MIN_VISIBLE_IMPRESSION_RATIO && Rect.intersects(rect, viewport)
    }

    private fun hydrateFeedPost(post: Post, requestId: Long, requestedFeed: HomeFeedTab) {
        postRepository.loadComments(
            postId = post.postId,
            onSuccess = { comments ->
                if (_binding == null) return@loadComments
                if (viewModel.requestIdFor(requestedFeed) != requestId) return@loadComments
                loadEngagementState(post, comments, requestId, requestedFeed)
            },
            onFailure = {
                if (_binding == null) return@loadComments
                if (viewModel.requestIdFor(requestedFeed) != requestId) return@loadComments
                loadEngagementState(post, emptyList(), requestId, requestedFeed)
            }
        )
    }

    // Loads per-user engagement state before rendering a post card.
    private fun loadEngagementState(post: Post, comments: List<PostComment>, requestId: Long, requestedFeed: HomeFeedTab) {
        postRepository.loadLikeCount(
            postId = post.postId,
            onSuccess = { likeCount ->
                if (_binding == null) return@loadLikeCount
                if (viewModel.requestIdFor(requestedFeed) != requestId) return@loadLikeCount
                loadEngagementStateWithCount(post.copy(likesCount = likeCount), comments, requestId, requestedFeed)
            },
            onFailure = {
                if (_binding == null) return@loadLikeCount
                if (viewModel.requestIdFor(requestedFeed) != requestId) return@loadLikeCount
                loadEngagementStateWithCount(post, comments, requestId, requestedFeed)
            }
        )
    }

    private fun loadEngagementStateWithCount(post: Post, comments: List<PostComment>, requestId: Long, requestedFeed: HomeFeedTab) {
        postRepository.isPostLikedByCurrentUser(
            postId = post.postId,
            onSuccess = { isLiked ->
                if (_binding == null) return@isPostLikedByCurrentUser
                if (viewModel.requestIdFor(requestedFeed) != requestId) return@isPostLikedByCurrentUser
                postRepository.isPostSavedByCurrentUser(
                    postId = post.postId,
                    onSuccess = { isSaved ->
                        if (_binding == null) return@isPostSavedByCurrentUser
                        if (viewModel.requestIdFor(requestedFeed) != requestId) return@isPostSavedByCurrentUser
                        loadEventRegistrationState(post, comments, isLiked, isSaved, requestId, requestedFeed)
                    },
                    onFailure = {
                        if (_binding == null) return@isPostSavedByCurrentUser
                        if (viewModel.requestIdFor(requestedFeed) != requestId) return@isPostSavedByCurrentUser
                        loadEventRegistrationState(post, comments, isLiked, isSaved = false, requestId, requestedFeed)
                    }
                )
            },
            onFailure = {
                if (_binding == null) return@isPostLikedByCurrentUser
                if (viewModel.requestIdFor(requestedFeed) != requestId) return@isPostLikedByCurrentUser
                loadEventRegistrationState(post, comments, isLiked = false, isSaved = false, requestId, requestedFeed)
            }
        )
    }

    // Loads event registration state for activity posts before rendering.
    private fun loadEventRegistrationState(
        post: Post,
        comments: List<PostComment>,
        isLiked: Boolean,
        isSaved: Boolean,
        requestId: Long,
        requestedFeed: HomeFeedTab
    ) {
        postRepository.isEventRegisteredByCurrentUser(
            post = post,
            onSuccess = { isRegistered ->
                if (_binding == null) return@isEventRegisteredByCurrentUser
                if (viewModel.requestIdFor(requestedFeed) != requestId) return@isEventRegisteredByCurrentUser
                viewModel.updateItem(post.postId) {
                    it.copy(post = post, comments = comments, isLiked = isLiked, isSaved = isSaved, isEventRegistered = isRegistered)
                }
                if (viewModel.selectedFeed == requestedFeed) renderFeed()
            },
            onFailure = {
                if (_binding == null) return@isEventRegisteredByCurrentUser
                if (viewModel.requestIdFor(requestedFeed) != requestId) return@isEventRegisteredByCurrentUser
                viewModel.updateItem(post.postId) {
                    it.copy(post = post, comments = comments, isLiked = isLiked, isSaved = isSaved, isEventRegistered = false)
                }
                if (viewModel.selectedFeed == requestedFeed) renderFeed()
            }
        )
    }

    // Adds the fully prepared post card to the feed.
    private fun addFeedPostCard(
        item: HomeFeedItem
    ) {
        PostCardRenderer.addPostCard(
            parent = binding.homeLAYPosts,
            post = item.post,
            comments = item.comments,
            isLiked = item.isLiked,
            isSaved = item.isSaved,
            isEventRegistered = item.isEventRegistered,
            currentUserId = authRepository.getCurrentUserUid().orEmpty(),
            onOpen = {
                activityTrackingRepository.trackPostOpened(it)
                (requireActivity() as MainActivity).openPost(it.postId)
            },
            onLike = { toggleLike(it) },
            onSave = { toggleSave(it) },
            onComment = { targetPost, text -> addComment(targetPost, text) },
            onRepost = { repost(it) },
            onEditComment = { comment, text -> updateComment(comment, text) },
            onDeleteComment = { comment -> deleteComment(comment) },
            onLikeComment = { comment -> toggleCommentLike(comment) },
            onReplyComment = { comment, text -> addCommentReply(comment, text) },
            onReportComment = { comment -> reportComment(comment, item.post) },
            onEventRegister = { toggleEventRegistration(it) },
            onReport = { reportPost(it) },
            onHide = { hidePost(it) },
            onMediaOpen = { url, mediaType ->
                (requireActivity() as MainActivity).openMediaViewer(url, mediaType)
            },
            onAuthorOpen = { authorId ->
                (requireActivity() as MainActivity).openUserProfile(authorId)
            },
            onAuthorEntityOpen = { ref -> (requireActivity() as MainActivity).openAuthorEntity(ref) },
            cardStyle = PostCardRenderer.CardStyle.FLOW_LIGHT
        )
    }

    // Toggles the current user's like on one comment.
    private fun toggleCommentLike(comment: PostComment) {
        postRepository.toggleCommentLike(
            postId = comment.postId,
            commentId = comment.commentId,
            onSuccess = {
                viewModel.updateItem(comment.postId) { item ->
                    item.copy(
                        comments = item.comments.map { existing ->
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
                    )
                }
                renderFeed()
            },
            onFailure = { error ->
                Toast.makeText(requireContext(), error, Toast.LENGTH_SHORT).show()
            }
        )
    }

    // Adds a reply to one comment.
    private fun addCommentReply(comment: PostComment, text: String) {
        val user = currentUser
        if (user == null) {
            loadCurrentUserThen { loadedUser ->
                postRepository.addCommentReply(
                    postId = comment.postId,
                    commentId = comment.commentId,
                    author = loadedUser,
                    text = text,
                    onSuccess = {
                        appendReplyLocally(comment, loadedUser, text)
                    },
                    onFailure = { error ->
                        Toast.makeText(requireContext(), error, Toast.LENGTH_SHORT).show()
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
            onSuccess = {
                appendReplyLocally(comment, user, text)
            },
            onFailure = { error ->
                Toast.makeText(requireContext(), error, Toast.LENGTH_SHORT).show()
            }
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
            createdAt = Timestamp.now()
        )
        viewModel.updateItem(comment.postId) { item ->
            item.copy(
                comments = item.comments.map { existing ->
                    if (existing.commentId == comment.commentId) existing.copy(replies = existing.replies + reply) else existing
                }
            )
        }
        renderFeed()
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
                viewModel.updateItem(post.postId) { item ->
                    val registered = !item.isEventRegistered
                    val countDelta = if (registered) 1 else -1
                    item.copy(
                        isEventRegistered = registered,
                        post = item.post.copy(registrationsCount = (item.post.registrationsCount + countDelta).coerceAtLeast(0))
                    )
                }
                renderFeed()
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
            onSuccess = {
                viewModel.updateItem(comment.postId) { item ->
                    item.copy(comments = item.comments.map { if (it.commentId == comment.commentId) it.copy(text = text) else it })
                }
                renderFeed()
            },
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
            onSuccess = {
                viewModel.updateItem(comment.postId) { item ->
                    item.copy(
                        post = item.post.copy(commentsCount = (item.post.commentsCount - 1).coerceAtLeast(0)),
                        comments = item.comments.filterNot { it.commentId == comment.commentId }
                    )
                }
                renderFeed()
            },
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
                viewModel.removeItem(post.postId)
                renderFeed()
            },
            onFailure = { error ->
                Toast.makeText(requireContext(), error, Toast.LENGTH_SHORT).show()
            }
        )
    }

    // Toggles saved state and tracks positive saves for recommendations.
    private fun toggleSave(post: Post) {
        val previous = viewModel.items().firstOrNull { it.post.postId == post.postId } ?: return
        viewModel.updateItem(post.postId) { it.copy(isSaved = !it.isSaved) }
        renderFeed()
        postRepository.toggleSave(
            post = post,
            onSuccess = { isSaved ->
                viewModel.updateItem(post.postId) { it.copy(isSaved = isSaved) }
                renderFeed()
            },
            onFailure = { error ->
                viewModel.updateItem(post.postId) { previous }
                renderFeed()
                Toast.makeText(requireContext(), error, Toast.LENGTH_SHORT).show()
            }
        )
    }

    private fun repost(post: Post) {
        val user = currentUser
        if (user == null) {
            loadCurrentUserThen { loadedUser -> createRepost(post, loadedUser) }
            return
        }
        createRepost(post, user)
    }

    private fun createRepost(post: Post, user: User) {
        postRepository.createRepost(
            originalPost = post,
            author = user,
            onSuccess = {
                Toast.makeText(requireContext(), "Post reposted", Toast.LENGTH_SHORT).show()
                viewModel.prependToCurrent(
                    HomeFeedItem(
                        post = Post(
                            postId = it,
                            authorId = user.uid,
                            authorName = "${user.firstName} ${user.lastName}".trim().ifBlank { "Dancer" },
                            authorProfileImageUrl = user.profileImageUrl,
                            authorType = user.role,
                            postType = "repost",
                            visibility = "public",
                            originalPostId = post.postId,
                            originalAuthorId = post.authorId,
                            originalAuthorName = post.authorName,
                            createdAt = Timestamp.now()
                        )
                    )
                )
                renderFeed()
            },
            onFailure = { error ->
                Toast.makeText(requireContext(), error, Toast.LENGTH_SHORT).show()
            }
        )
    }

    // Toggles a like and reloads the selected feed.
    private fun toggleLike(post: Post) {
        val previous = viewModel.items().firstOrNull { it.post.postId == post.postId } ?: return
        viewModel.updateItem(post.postId) { item ->
            val liked = !item.isLiked
            item.copy(
                isLiked = liked,
                post = item.post.copy(likesCount = (item.post.likesCount + if (liked) 1 else -1).coerceAtLeast(0))
            )
        }
        renderFeed()
        postRepository.toggleLike(
            postId = post.postId,
            onSuccess = { isLiked ->
                viewModel.updateItem(post.postId) { item ->
                    val delta = when {
                        isLiked && !previous.isLiked -> 1
                        !isLiked && previous.isLiked -> -1
                        else -> 0
                    }
                    item.copy(
                        isLiked = isLiked,
                        post = item.post.copy(likesCount = (previous.post.likesCount + delta).coerceAtLeast(0))
                    )
                }
                renderFeed()
            },
            onFailure = { error ->
                viewModel.updateItem(post.postId) { previous }
                renderFeed()
                Toast.makeText(requireContext(), error, Toast.LENGTH_SHORT).show()
            }
        )
    }

    // Adds a comment and reloads the selected feed.
    private fun addComment(post: Post, text: String) {
        val user = currentUser
        if (user == null) {
            loadCurrentUserThen { loadedUser ->
                postRepository.addComment(
                    postId = post.postId,
                    author = loadedUser,
                    text = text,
                    onSuccess = { appendCommentLocally(post, loadedUser, text) },
                    onFailure = { error ->
                        Toast.makeText(requireContext(), error, Toast.LENGTH_SHORT).show()
                    }
                )
            }
            return
        }
        postRepository.addComment(
            postId = post.postId,
            author = user,
            text = text,
            onSuccess = { appendCommentLocally(post, user, text) },
            onFailure = { error ->
                Toast.makeText(requireContext(), error, Toast.LENGTH_SHORT).show()
            }
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
            createdAt = Timestamp.now()
        )
        viewModel.updateItem(post.postId) { item ->
            item.copy(
                post = item.post.copy(commentsCount = item.post.commentsCount + 1),
                comments = (item.comments + comment).takeLast(3)
            )
        }
        renderFeed()
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
                Toast.makeText(requireContext(), error, Toast.LENGTH_SHORT).show()
            }
        )
    }

    // Returns the empty message for a feed tab.
    private fun emptyMessageFor(feedTab: HomeFeedTab): String {
        return when (feedTab) {
            HomeFeedTab.FOR_YOU -> "Your feed is quiet right now. Create a post from your profile or check back soon."
            HomeFeedTab.FOLLOWING -> "Follow dancers, teachers, and studios to build a more personal feed."
        }
    }

    private fun addEmptyFeedAction() {
        val context = requireContext()
        binding.homeLAYPosts.addView(Button(context).apply {
            text = if (viewModel.selectedFeed == HomeFeedTab.FOLLOWING) "Find people to follow" else "Explore Discover"
            isAllCaps = false
            setTextColor(context.getColor(R.color.flow_brand))
            setBackgroundResource(R.drawable.bg_flow_button_secondary)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 44.dp()).apply {
                topMargin = 12.dp()
            }
            setOnClickListener { (requireActivity() as MainActivity).openDiscover() }
        })
    }

    // Clears the fragment binding when the view is destroyed.
    override fun onDestroyView() {
        viewModel.saveScroll(binding.homeSCROLLFeed.scrollY)
        binding.homeLAYPosts.removeCallbacks(trackVisibleImpressionsRunnable)
        ActiveAccountHolder.removeListener(activeAccountListener)
        super.onDestroyView()
        _binding = null
    }

    private companion object {
        const val IMPRESSION_VISIBLE_DELAY_MS = 1200L
        const val MIN_VISIBLE_IMPRESSION_RATIO = 0.5f
        const val MAX_IMPRESSIONS_PER_BATCH = 8
    }
}

private fun Int.dp(): Int {
    return (this * android.content.res.Resources.getSystem().displayMetrics.density).toInt()
}
