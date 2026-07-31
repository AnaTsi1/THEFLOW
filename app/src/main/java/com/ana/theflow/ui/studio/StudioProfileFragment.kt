package com.ana.theflow.ui.studio

import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
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
import com.ana.theflow.data.model.post.Post
import com.ana.theflow.data.model.studio.Studio
import com.ana.theflow.data.model.user.User
import com.ana.theflow.data.repository.AuthRepository
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
    private var studioListener: ListenerRegistration? = null
    private var currentUser: User? = null
    private var isFollowing: Boolean = false
    private var lastStudio: Studio? = null

    private val studioId: String get() = arguments?.getString(ARG_STUDIO_ID).orEmpty()
    private val isRootTab: Boolean get() = arguments?.getBoolean(ARG_AS_ROOT_TAB) == true

    fun isRootTabInstance(): Boolean = isRootTab

    private lateinit var root: LinearLayout
    private lateinit var content: LinearLayout
    private lateinit var messageLabel: TextView

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
                    leftMargin = if (isRootTab) 4.dp() else 12.dp()
                }
            })
        }
    }

    private fun loadCurrentUser() {
        val uid = authRepository.getCurrentUserUid() ?: return
        userRepository.getUserByUid(uid, onSuccess = { user -> currentUser = user }, onFailure = {})
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

    private fun render(studio: Studio) {
        lastStudio = studio
        content.removeAllViews()
        content.addView(header(studio))
        content.addView(actionRow(studio))
        content.addView(infoSection(studio))
        content.addView(placeholderSection("Jobs", "Job openings posted by this studio will appear here."))
        loadStudioContent(studio)
    }

    private fun loadStudioContent(studio: Studio) {
        postRepository.loadPostsByStudio(
            studioId = studio.id,
            onSuccess = { posts -> if (isAdded) renderStudioContent(posts) },
            onFailure = { if (isAdded) renderStudioContent(emptyList()) }
        )
    }

    private fun renderStudioContent(posts: List<Post>) {
        val (events, regularPosts) = posts.partition { it.postType == "dance_activity" }
        val studio = lastStudio
        val canEdit = studio != null && currentUser?.let { AccountPermissions.canEditStudio(it, studio) } == true

        content.addView(sectionTitle("Events & Classes"))
        if (events.isEmpty()) {
            content.addView(sectionMessage("No upcoming events or classes."))
        } else {
            events.forEach { post -> addStudioPostCard(post, canEdit) }
        }

        content.addView(sectionTitle("Posts"))
        if (regularPosts.isEmpty()) {
            content.addView(sectionMessage("No posts yet."))
        } else {
            regularPosts.forEach { post -> addStudioPostCard(post, canEdit) }
        }
    }

    private fun addStudioPostCard(post: Post, canEdit: Boolean) {
        PostCardRenderer.addPostCard(
            parent = content,
            post = post,
            canEdit = canEdit,
            currentUserId = authRepository.getCurrentUserUid().orEmpty(),
            onOpen = { (requireActivity() as MainActivity).openPost(it.postId) },
            onAuthorEntityOpen = { ref -> (requireActivity() as MainActivity).openAuthorEntity(ref) },
            cardStyle = PostCardRenderer.CardStyle.FLOW_LIGHT
        )
    }

    private fun sectionTitle(title: String): View {
        return TextView(requireContext()).apply {
            text = title
            setTextColor(context.getColor(R.color.flow_ink))
            textSize = 16f
            setTypeface(typeface, Typeface.BOLD)
            setPadding(0, 8.dp(), 0, 6.dp())
        }
    }

    private fun sectionMessage(message: String): View {
        return TextView(requireContext()).apply {
            text = message
            setTextColor(context.getColor(R.color.flow_text_secondary))
            textSize = 13f
            setPadding(0, 0, 0, 8.dp())
        }
    }

    private fun header(studio: Studio): View {
        val context = requireContext()
        return FrameLayout(context).apply {
            setBackgroundResource(R.drawable.bg_flow_card)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 176.dp()).apply { bottomMargin = 14.dp() }
            val cover = ImageView(context).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP
                setBackgroundResource(R.drawable.bg_flow_media)
                layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, 104.dp())
                if (studio.coverImageUrl.isNotBlank()) Glide.with(this@StudioProfileFragment).load(studio.coverImageUrl).centerCrop().into(this)
            }
            addView(cover)
            val logo = ImageView(context).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP
                setBackgroundResource(R.drawable.bg_avatar)
                layoutParams = FrameLayout.LayoutParams(74.dp(), 74.dp(), Gravity.START or Gravity.BOTTOM).apply {
                    leftMargin = 16.dp()
                    bottomMargin = 18.dp()
                }
                if (studio.profileImageUrl.isNotBlank()) Glide.with(this@StudioProfileFragment).load(studio.profileImageUrl).circleCrop().into(this)
            }
            addView(logo)
            addView(TextView(context).apply {
                text = studio.displayName.ifBlank { "Studio" } + if (studio.verified) "  ✓" else ""
                setTextColor(context.getColor(R.color.flow_ink))
                textSize = 18f
                setTypeface(typeface, Typeface.BOLD)
                layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.START or Gravity.BOTTOM).apply {
                    leftMargin = 104.dp()
                    bottomMargin = 44.dp()
                }
            })
            addView(TextView(context).apply {
                text = "Business account"
                setTextColor(context.getColor(R.color.flow_text_secondary))
                textSize = 13f
                layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.START or Gravity.BOTTOM).apply {
                    leftMargin = 104.dp()
                    bottomMargin = 24.dp()
                }
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
        } else {
            val followButton = actionButton(if (isFollowing) "Following" else "Follow", primary = true) {}
            followButton.setOnClickListener {
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
                Toast.makeText(requireContext(), "Messaging studios is coming soon.", Toast.LENGTH_SHORT).show()
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
            studio.danceStyles.takeIf { it.isNotEmpty() }?.joinToString(", ")
        )
        return placeholderSectionRaw("About", if (lines.isEmpty()) "No details added yet." else lines.joinToString("\n"))
    }

    private fun placeholderSection(title: String, message: String): View {
        return placeholderSectionRaw(title, message)
    }

    private fun placeholderSectionRaw(title: String, body: String): View {
        val context = requireContext()
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(R.drawable.bg_flow_card)
            setPadding(14.dp(), 14.dp(), 14.dp(), 14.dp())
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = 12.dp()
            }
            addView(TextView(context).apply {
                text = title
                setTextColor(context.getColor(R.color.flow_ink))
                textSize = 16f
                setTypeface(typeface, Typeface.BOLD)
            })
            addView(TextView(context).apply {
                text = body
                setTextColor(context.getColor(R.color.flow_text_secondary))
                textSize = 13f
                setLineSpacing(3.dp().toFloat(), 1f)
                setPadding(0, 8.dp(), 0, 0)
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
