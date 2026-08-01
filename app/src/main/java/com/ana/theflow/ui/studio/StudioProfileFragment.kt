package com.ana.theflow.ui.studio

import android.graphics.Typeface
import android.os.Bundle
import android.text.TextUtils
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.ana.theflow.MainActivity
import com.ana.theflow.R
import com.ana.theflow.data.model.account.ActiveAccount
import com.ana.theflow.data.model.jobs.DanceJob
import com.ana.theflow.data.model.messaging.PartyRef
import com.ana.theflow.data.model.post.Post
import com.ana.theflow.data.model.studio.Studio
import com.ana.theflow.data.model.user.User
import com.ana.theflow.data.repository.AuthRepository
import com.ana.theflow.data.repository.JobRepository
import com.ana.theflow.data.repository.MessagingRepository
import com.ana.theflow.data.repository.PostRepository
import com.ana.theflow.data.repository.StudioRepository
import com.ana.theflow.data.repository.UserRepository
import com.ana.theflow.ui.common.PostCardRenderer
import com.ana.theflow.ui.common.ResponsiveLayout
import com.ana.theflow.ui.common.UiText
import com.ana.theflow.ui.settings.dp
import com.ana.theflow.utilities.AccountPermissions
import com.bumptech.glide.Glide
import com.google.firebase.firestore.ListenerRegistration

// A studio's business account profile - the "Instagram business profile" equivalent for a
// studio. Content tabs (posts/events/jobs) attribute to a studio starting Phase 3; for now
// this renders the business identity and the correct action row for the viewer.
class StudioProfileFragment : Fragment() {

    private val authRepository = AuthRepository()
    private val userRepository = UserRepository()
    private val studioRepository = StudioRepository()
    private val postRepository = PostRepository()
    private val jobRepository = JobRepository()
    private val messagingRepository = MessagingRepository()
    private var studioListener: ListenerRegistration? = null
    private var currentUser: User? = null
    private var isFollowing: Boolean = false
    private var lastStudio: Studio? = null
    private var lastJobs: List<DanceJob>? = null
    private var lastPosts: List<Post>? = null

    private val studioId: String get() = arguments?.getString(ARG_STUDIO_ID).orEmpty()
    private val isRootTab: Boolean get() = arguments?.getBoolean(ARG_AS_ROOT_TAB) == true

    fun isRootTabInstance(): Boolean = isRootTab

    private lateinit var root: LinearLayout
    private lateinit var content: LinearLayout
    private lateinit var messageLabel: TextView
    private lateinit var actionRowContainer: FrameLayout
    private lateinit var teachersContainer: LinearLayout
    private lateinit var jobsContainer: LinearLayout
    private lateinit var postsContainer: LinearLayout

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val context = requireContext()
        root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(R.color.flow_background)
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        }
        root.addView(topBar())
        val scroll = ScrollView(context).apply {
            isFillViewport = true
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
        }
        content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(18.dp(), 8.dp(), 18.dp(), 28.dp())
            ResponsiveLayout.constrainToReadableWidth(this)
        }
        messageLabel = TextView(context).apply {
            text = "Loading studio..."
            setTextColor(context.getColor(R.color.flow_text_secondary))
            textSize = 14f
            setPadding(0, 24.dp(), 0, 0)
        }
        content.addView(messageLabel)
        scroll.addView(content)
        root.addView(scroll)
        return root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        loadCurrentUser()
        listenToStudio()
    }

    private fun topBar(): View {
        val context = requireContext()
        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(10.dp(), 12.dp(), 14.dp(), 8.dp())
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 56.dp())
            if (!isRootTab) {
                addView(ImageButton(context).apply {
                    setImageResource(R.drawable.ic_arrow_back_24)
                    setColorFilter(context.getColor(R.color.flow_ink))
                    setBackgroundResource(R.drawable.bg_flow_icon_button)
                    layoutParams = LinearLayout.LayoutParams(44.dp(), 44.dp())
                    setOnClickListener { parentFragmentManager.popBackStack() }
                })
            }
            addView(TextView(context).apply {
                text = "Studio"
                setTextColor(context.getColor(R.color.flow_ink))
                textSize = 20f
                setTypeface(typeface, Typeface.BOLD)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginStart = if (isRootTab) 4.dp() else 12.dp()
                }
            })
        }
    }

    // currentUser and the studio doc load independently (a Firestore snapshot listener that
    // often resolves near-instantly from cache, vs. a real auth-gated user lookup). Sections
    // whose manager-only CTAs depend on currentUser are re-rendered from cached data here (no
    // extra network calls) so a manager who loads slightly late still ends up with the right view.
    private fun loadCurrentUser() {
        val uid = authRepository.getCurrentUserUid() ?: return
        userRepository.getUserByUid(
            uid = uid,
            onSuccess = { user ->
                currentUser = user
                if (!isAdded) return@getUserByUid
                lastStudio?.let { studio ->
                    renderActionRow(studio)
                    if (::teachersContainer.isInitialized) renderTeachersSection(studio)
                    if (::jobsContainer.isInitialized) lastJobs?.let { renderStudioJobs(it) }
                    if (::postsContainer.isInitialized) lastPosts?.let { renderStudioContent(it) }
                }
            },
            onFailure = {}
        )
    }

    private fun listenToStudio() {
        if (studioId.isBlank()) {
            messageLabel.text = "This studio is not available."
            return
        }
        studioRepository.isFollowingStudio(
            studioId = studioId,
            onSuccess = { following ->
                isFollowing = following
                if (isAdded) lastStudio?.let { render(it) }
            }
        )
        studioListener = studioRepository.listenToStudio(
            studioId = studioId,
            onUpdate = { studio -> if (isAdded) render(studio) },
            onError = { error -> if (isAdded) messageLabel.text = error }
        )
    }

    private fun canManage(studio: Studio): Boolean {
        return currentUser?.let { AccountPermissions.canEditStudio(it, studio) } == true
    }

    private fun render(studio: Studio) {
        lastStudio = studio
        content.removeAllViews()
        content.addView(header(studio))
        actionRowContainer = FrameLayout(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }
        content.addView(actionRowContainer)
        renderActionRow(studio)
        content.addView(infoSection(studio))
        teachersContainer = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }
        content.addView(teachersContainer)
        renderTeachersSection(studio)
        jobsContainer = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }
        content.addView(jobsContainer)
        postsContainer = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }
        content.addView(postsContainer)
        loadStudioContent(studio)
        loadStudioJobs(studio)
    }

    // Rebuilds only the action row (Edit vs. Follow/Message/Claim) - kept separate from render()
    // so refreshing it after currentUser finishes loading doesn't also re-fire the posts/jobs
    // network calls that render() triggers.
    private fun renderActionRow(studio: Studio) {
        if (!::actionRowContainer.isInitialized) return
        actionRowContainer.removeAllViews()
        actionRowContainer.addView(actionRow(studio))
    }

    private fun loadStudioJobs(studio: Studio) {
        jobRepository.loadListings(
            account = ActiveAccount.StudioAccount(userUid = authRepository.getCurrentUserUid().orEmpty(), studioId = studio.id),
            onSuccess = { jobs -> if (isAdded) { lastJobs = jobs; renderStudioJobs(jobs) } },
            onFailure = { if (isAdded) { lastJobs = emptyList(); renderStudioJobs(emptyList()) } }
        )
    }

    private fun renderStudioJobs(jobs: List<DanceJob>) {
        if (!::jobsContainer.isInitialized) return
        val studio = lastStudio ?: return
        jobsContainer.removeAllViews()
        val manager = canManage(studio)
        val body: View = if (jobs.isEmpty()) {
            if (manager) {
                emptyState(R.drawable.ic_work_24, "No open positions yet.", "Post a job") {
                    (requireActivity() as MainActivity).openJobCreation()
                }
            } else {
                emptyState(R.drawable.ic_work_24, "No open job postings right now.")
            }
        } else {
            LinearLayout(requireContext()).apply {
                orientation = LinearLayout.VERTICAL
                jobs.take(3).forEach { job -> addView(jobRow(job)) }
            }
        }
        jobsContainer.addView(sectionCard("Jobs", R.drawable.ic_work_24, body))
    }

    private fun jobRow(job: DanceJob): View {
        val context = requireContext()
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(R.drawable.bg_flow_card)
            setPadding(14.dp(), 12.dp(), 14.dp(), 12.dp())
            isClickable = true
            isFocusable = true
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = 8.dp()
            }
            setOnClickListener { (requireActivity() as MainActivity).openJobDetail(job.jobId) }
            addView(TextView(context).apply {
                text = job.title
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                setTextColor(context.getColor(R.color.flow_ink))
                textSize = 15f
                setTypeface(typeface, Typeface.BOLD)
            })
            addView(TextView(context).apply {
                text = job.workType.replace("_", " ").replaceFirstChar { it.uppercase() }
                setTextColor(context.getColor(R.color.flow_text_secondary))
                textSize = 12f
                setPadding(0, 3.dp(), 0, 0)
            })
        }
    }

    private fun loadStudioContent(studio: Studio) {
        postRepository.loadPostsByStudio(
            studioId = studio.id,
            onSuccess = { posts -> if (isAdded) { lastPosts = posts; renderStudioContent(posts) } },
            onFailure = { if (isAdded) { lastPosts = emptyList(); renderStudioContent(emptyList()) } }
        )
    }

    private fun renderStudioContent(posts: List<Post>) {
        if (!::postsContainer.isInitialized) return
        val studio = lastStudio ?: return
        postsContainer.removeAllViews()
        val manager = canManage(studio)
        val (events, regularPosts) = posts.partition { it.postType == "dance_activity" }

        val eventsBody: View = if (events.isEmpty()) {
            if (manager) {
                emptyState(R.drawable.ic_event_24, "No upcoming events or classes yet.", "Add an event or class") {
                    (requireActivity() as MainActivity).openEventCreation()
                }
            } else {
                emptyState(R.drawable.ic_event_24, "No upcoming events or classes.")
            }
        } else {
            LinearLayout(requireContext()).apply {
                orientation = LinearLayout.VERTICAL
                events.forEach { post -> addStudioPostCard(this, post, manager) }
            }
        }
        postsContainer.addView(sectionCard("Events & Classes", R.drawable.ic_event_24, eventsBody))

        val postsBody: View = if (regularPosts.isEmpty()) {
            if (manager) {
                emptyState(R.drawable.ic_image_24, "No posts yet.", "Share your first post") {
                    (requireActivity() as MainActivity).openPostCreation()
                }
            } else {
                emptyState(R.drawable.ic_image_24, "No posts yet.")
            }
        } else {
            LinearLayout(requireContext()).apply {
                orientation = LinearLayout.VERTICAL
                regularPosts.forEach { post -> addStudioPostCard(this, post, manager) }
            }
        }
        postsContainer.addView(sectionCard("Posts", R.drawable.ic_image_24, postsBody))
    }

    private fun addStudioPostCard(parent: LinearLayout, post: Post, canEdit: Boolean) {
        PostCardRenderer.addPostCard(
            parent = parent,
            post = post,
            canEdit = canEdit,
            currentUserId = authRepository.getCurrentUserUid().orEmpty(),
            onOpen = { (requireActivity() as MainActivity).openPost(it.postId) },
            onAuthorEntityOpen = { ref -> (requireActivity() as MainActivity).openAuthorEntity(ref) },
            cardStyle = PostCardRenderer.CardStyle.FLOW_LIGHT
        )
    }

    // Consistent card treatment for every content section: icon + title header, then whatever
    // body view the caller built (a list of rows, or an empty state) - so About/Teachers/Jobs/
    // Events & Classes/Posts all read as the same kind of block instead of one flat list.
    private fun sectionCard(title: String, iconRes: Int, body: View): View {
        val context = requireContext()
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(R.drawable.bg_flow_card)
            setPadding(14.dp(), 14.dp(), 14.dp(), 14.dp())
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = 14.dp()
            }
            addView(sectionHeader(title, iconRes))
            addView(body)
        }
    }

    private fun sectionHeader(title: String, iconRes: Int): View {
        val context = requireContext()
        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = 10.dp()
            }
            addView(ImageView(context).apply {
                setImageResource(iconRes)
                setColorFilter(context.getColor(R.color.flow_brand))
                layoutParams = LinearLayout.LayoutParams(20.dp(), 20.dp()).apply { marginEnd = 10.dp() }
            })
            addView(TextView(context).apply {
                text = title
                setTextColor(context.getColor(R.color.flow_ink))
                textSize = 16f
                setTypeface(typeface, Typeface.BOLD)
            })
        }
    }

    // A friendlier empty state than a flat gray sentence: a muted icon, softer copy, and - only
    // for a viewer who can actually act on it - a tappable CTA into the relevant creation flow.
    // Non-managers only ever see the icon + message, never a prompt to do something they can't.
    private fun emptyState(iconRes: Int, message: String, ctaLabel: String? = null, onCta: (() -> Unit)? = null): View {
        val context = requireContext()
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(0, 6.dp(), 0, 2.dp())
            addView(ImageView(context).apply {
                setImageResource(iconRes)
                setColorFilter(context.getColor(R.color.flow_text_muted))
                layoutParams = LinearLayout.LayoutParams(26.dp(), 26.dp()).apply { bottomMargin = 6.dp() }
            })
            addView(TextView(context).apply {
                text = message
                gravity = Gravity.CENTER
                setTextColor(context.getColor(R.color.flow_text_secondary))
                textSize = 13f
            })
            if (ctaLabel != null && onCta != null) {
                addView(TextView(context).apply {
                    text = ctaLabel
                    gravity = Gravity.CENTER
                    setTextColor(context.getColor(R.color.flow_brand))
                    textSize = 13f
                    setTypeface(typeface, Typeface.BOLD)
                    setPadding(0, 8.dp(), 0, 0)
                    isClickable = true
                    isFocusable = true
                    setOnClickListener { onCta() }
                })
            }
        }
    }

    private fun header(studio: Studio): View {
        val context = requireContext()
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(R.drawable.bg_flow_card)
            elevation = 2.dp().toFloat()
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = 14.dp()
            }

            // Cover + avatar live inside their own frame; the avatar deliberately breaks out of
            // the cover into the frame's own lower "collar" instead of sitting on top of any
            // text, and the identity block below is a separate, sequentially-stacked section -
            // so nothing can ever overlap the name/label the way the flat FrameLayout used to
            // (that older layout also mixed direction-aware gravity with raw left/right margins,
            // which is exactly what breaks under a right-to-left layout direction).
            addView(FrameLayout(context).apply {
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 168.dp())
                addView(ImageView(context).apply {
                    scaleType = ImageView.ScaleType.CENTER_CROP
                    setBackgroundResource(R.drawable.bg_flow_media)
                    layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, 124.dp())
                    if (studio.coverImageUrl.isNotBlank()) Glide.with(this@StudioProfileFragment).load(studio.coverImageUrl).centerCrop().into(this)
                })
                addView(View(context).apply {
                    setBackgroundResource(R.drawable.bg_event_cover_scrim)
                    layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, 56.dp(), Gravity.TOP).apply {
                        topMargin = 72.dp()
                    }
                })
                addView(FrameLayout(context).apply {
                    setBackgroundResource(R.drawable.bg_avatar_ring)
                    setPadding(4.dp(), 4.dp(), 4.dp(), 4.dp())
                    layoutParams = FrameLayout.LayoutParams(92.dp(), 92.dp(), Gravity.BOTTOM or Gravity.START).apply {
                        marginStart = 16.dp()
                    }
                    addView(ImageView(context).apply {
                        scaleType = ImageView.ScaleType.CENTER_CROP
                        setBackgroundResource(R.drawable.bg_avatar)
                        layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
                        if (studio.profileImageUrl.isNotBlank()) Glide.with(this@StudioProfileFragment).load(studio.profileImageUrl).circleCrop().into(this)
                    })
                })
            })

            addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(16.dp(), 10.dp(), 16.dp(), 14.dp())
                addView(LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    addView(TextView(context).apply {
                        text = studio.displayName.ifBlank { "Studio" }
                        setTextColor(context.getColor(R.color.flow_ink))
                        textSize = 19f
                        setTypeface(typeface, Typeface.BOLD)
                        maxLines = 1
                        ellipsize = TextUtils.TruncateAt.END
                        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                    })
                    if (studio.verified) {
                        addView(ImageView(context).apply {
                            setImageResource(R.drawable.ic_check_circle_24)
                            setColorFilter(context.getColor(R.color.flow_brand))
                            layoutParams = LinearLayout.LayoutParams(20.dp(), 20.dp()).apply { marginStart = 6.dp() }
                        })
                    }
                })
                addView(TextView(context).apply {
                    text = "Business account"
                    setTextColor(context.getColor(R.color.flow_text_secondary))
                    textSize = 13f
                    setPadding(0, 4.dp(), 0, 14.dp())
                })
                addView(View(context).apply {
                    setBackgroundColor(context.getColor(R.color.flow_border))
                    layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1.dp()).apply {
                        bottomMargin = 12.dp()
                    }
                })
                addView(statsRow(studio))
            })
        }
    }

    // A studio's own stat bar - the equivalent of a Facebook Page's "128 Followers" line, folded
    // into the header card so it reads as part of the business identity, not a floating aside.
    // followersCount is kept accurate by StudioRepository.toggleFollowStudio's own increment/
    // decrement, so this is a direct read of the live studio doc, no extra query needed.
    private fun statsRow(studio: Studio): View {
        val context = requireContext()
        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            addView(statCell(studio.postsCount, "Posts"))
            addView(View(context).apply {
                setBackgroundColor(context.getColor(R.color.flow_border))
                layoutParams = LinearLayout.LayoutParams(1.dp(), 30.dp())
            })
            addView(statCell(studio.followersCount, "Followers"))
        }
    }

    private fun statCell(count: Long, label: String): View {
        val context = requireContext()
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            addView(TextView(context).apply {
                text = count.toString()
                gravity = Gravity.CENTER
                setTextColor(context.getColor(R.color.flow_ink))
                textSize = 16f
                setTypeface(typeface, Typeface.BOLD)
            })
            addView(TextView(context).apply {
                text = label
                gravity = Gravity.CENTER
                setTextColor(context.getColor(R.color.flow_text_secondary))
                textSize = 12f
            })
        }
    }

    private fun actionRow(studio: Studio): View {
        val context = requireContext()
        val user = currentUser
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = 14.dp()
            }
        }
        val canEdit = user != null && AccountPermissions.canEditStudio(user, studio)
        val isClaimed = studio.ownerUid.isNotBlank() || studio.claimStatus.equals("CLAIMED", ignoreCase = true)

        if (canEdit) {
            row.addView(actionButton("Edit Business Profile", primary = true) {
                (requireActivity() as MainActivity).openEditStudioProfile(studio.id)
            })
            row.addView(actionButton("Analytics", primary = false) {
                (requireActivity() as MainActivity).openStudioAnalytics()
            })
        } else {
            val followButton = actionButton(if (isFollowing) "Following" else "Follow", primary = true) {}
            followButton.setOnClickListener {
                followButton.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                val viewer = user
                if (viewer == null) {
                    Toast.makeText(requireContext(), "Please sign in to follow studios.", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                followButton.isEnabled = false
                studioRepository.toggleFollowStudio(
                    studioId = studio.id,
                    viewer = viewer,
                    onSuccess = { nowFollowing ->
                        if (!isAdded) return@toggleFollowStudio
                        isFollowing = nowFollowing
                        followButton.isEnabled = true
                        followButton.text = if (nowFollowing) "Following" else "Follow"
                    },
                    onFailure = { error ->
                        if (!isAdded) return@toggleFollowStudio
                        followButton.isEnabled = true
                        Toast.makeText(requireContext(), UiText.friendlyError(error, "We could not update this follow."), Toast.LENGTH_SHORT).show()
                    }
                )
            }
            row.addView(followButton)
            row.addView(actionButton("Message", primary = false) {
                val viewerUid = user?.uid
                if (viewerUid.isNullOrBlank()) {
                    Toast.makeText(requireContext(), "Please sign in to message this studio.", Toast.LENGTH_SHORT).show()
                    return@actionButton
                }
                messagingRepository.resolveOrCreateConversation(
                    target = PartyRef.studio(studio.id),
                    from = ActiveAccount.Personal(viewerUid),
                    onSuccess = { conversationId -> (requireActivity() as MainActivity).openChat(conversationId) },
                    onFailure = { error ->
                        Toast.makeText(requireContext(), UiText.friendlyError(error, "We could not start this conversation."), Toast.LENGTH_SHORT).show()
                    }
                )
            })
            if (!isClaimed) {
                row.addView(actionButton("Claim Studio", primary = false) {
                    (requireActivity() as MainActivity).openStudioRequest(
                        mode = "claim",
                        studioId = studio.id,
                        studioName = studio.displayName,
                        googlePlaceId = studio.googlePlaceId,
                        address = studio.address
                    )
                })
            }
        }
        return row
    }

    private fun actionButton(label: String, primary: Boolean, onClick: () -> Unit): Button {
        return Button(requireContext()).apply {
            text = label
            isAllCaps = false
            setTextColor(context.getColor(if (primary) R.color.flow_surface else R.color.flow_brand))
            setBackgroundResource(if (primary) R.drawable.bg_flow_button_primary else R.drawable.bg_flow_button_secondary)
            layoutParams = LinearLayout.LayoutParams(0, 44.dp(), 1f).apply { rightMargin = 8.dp() }
            setOnClickListener { onClick() }
        }
    }

    private fun infoSection(studio: Studio): View {
        val lines = listOfNotNull(
            studio.bio.takeIf { it.isNotBlank() },
            listOf(studio.address, studio.city).filter { it.isNotBlank() }.joinToString(", ").takeIf { it.isNotBlank() },
            studio.contactPhone.takeIf { it.isNotBlank() }?.let { "Phone: $it" },
            studio.contactEmail.takeIf { it.isNotBlank() }?.let { "Email: $it" },
            studio.websiteUrl.takeIf { it.isNotBlank() },
            studio.openingHours.takeIf { it.isNotBlank() }?.let { "Hours: $it" },
            studio.danceStyles.takeIf { it.isNotEmpty() }?.joinToString(", "),
            studio.socialLinks[Studio.SOCIAL_INSTAGRAM]?.takeIf { it.isNotBlank() }?.let { "Instagram: $it" },
            studio.socialLinks[Studio.SOCIAL_TIKTOK]?.takeIf { it.isNotBlank() }?.let { "TikTok: $it" },
            studio.socialLinks[Studio.SOCIAL_YOUTUBE]?.takeIf { it.isNotBlank() }?.let { "YouTube: $it" }
        )
        val body = TextView(requireContext()).apply {
            text = if (lines.isEmpty()) "No details added yet." else lines.joinToString("\n")
            setTextColor(context.getColor(R.color.flow_text_secondary))
            textSize = 13f
            setLineSpacing(3.dp().toFloat(), 1f)
        }
        return sectionCard("About", R.drawable.ic_home_24, body)
    }

    // A studio's roster of teachers - denormalized name/photo snapshots in teacherProfiles so this
    // renders without an extra per-teacher user lookup, refreshed whenever EditStudioProfileFragment
    // calls StudioRepository.updateStudioTeachers.
    private fun renderTeachersSection(studio: Studio) {
        if (!::teachersContainer.isInitialized) return
        teachersContainer.removeAllViews()
        val manager = canManage(studio)
        val body: View = if (studio.teacherProfiles.isEmpty()) {
            if (manager) {
                emptyState(R.drawable.ic_profile_24, "No teachers yet.", "Add your first teacher") {
                    (requireActivity() as MainActivity).openEditStudioProfile(studio.id)
                }
            } else {
                emptyState(R.drawable.ic_profile_24, "No teachers listed yet.")
            }
        } else {
            LinearLayout(requireContext()).apply {
                orientation = LinearLayout.VERTICAL
                studio.teacherProfiles.forEach { profile -> addView(teacherRow(profile)) }
            }
        }
        teachersContainer.addView(sectionCard("Teachers", R.drawable.ic_profile_24, body))
    }

    private fun teacherRow(profile: Map<String, Any>): View {
        val context = requireContext()
        val uid = profile[Studio.TEACHER_KEY_UID] as? String ?: ""
        val name = profile[Studio.TEACHER_KEY_NAME] as? String ?: "Teacher"
        val headline = profile[Studio.TEACHER_KEY_HEADLINE] as? String ?: ""
        val photoUrl = profile[Studio.TEACHER_KEY_PHOTO] as? String ?: ""
        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundResource(R.drawable.bg_flow_card)
            setPadding(14.dp(), 10.dp(), 14.dp(), 10.dp())
            isClickable = uid.isNotBlank()
            isFocusable = uid.isNotBlank()
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = 8.dp()
            }
            if (uid.isNotBlank()) {
                setOnClickListener { (requireActivity() as MainActivity).openUserProfile(uid) }
            }
            addView(ImageView(context).apply {
                setBackgroundResource(R.drawable.bg_avatar)
                scaleType = ImageView.ScaleType.CENTER_CROP
                layoutParams = LinearLayout.LayoutParams(44.dp(), 44.dp()).apply { marginEnd = 12.dp() }
                if (photoUrl.isNotBlank()) Glide.with(this@StudioProfileFragment).load(photoUrl).circleCrop().into(this)
            })
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                addView(TextView(context).apply {
                    text = name
                    setTextColor(context.getColor(R.color.flow_ink))
                    textSize = 15f
                    setTypeface(typeface, Typeface.BOLD)
                })
                if (headline.isNotBlank()) {
                    addView(TextView(context).apply {
                        text = headline
                        setTextColor(context.getColor(R.color.flow_text_secondary))
                        textSize = 12f
                        setPadding(0, 2.dp(), 0, 0)
                    })
                }
            })
        }
    }

    override fun onDestroyView() {
        studioListener?.remove()
        studioListener = null
        super.onDestroyView()
    }

    companion object {
        private const val ARG_STUDIO_ID = "ARG_STUDIO_ID"
        private const val ARG_AS_ROOT_TAB = "ARG_AS_ROOT_TAB"

        fun newInstance(studioId: String, asRootTab: Boolean = false): StudioProfileFragment {
            return StudioProfileFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_STUDIO_ID, studioId)
                    putBoolean(ARG_AS_ROOT_TAB, asRootTab)
                }
            }
        }
    }
}
