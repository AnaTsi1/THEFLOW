package com.ana.theflow.ui.events

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.ana.theflow.MainActivity
import com.ana.theflow.R
import com.ana.theflow.data.model.post.Post
import com.ana.theflow.data.model.user.User
import com.ana.theflow.data.repository.AuthRepository
import com.ana.theflow.data.repository.PostRepository
import com.ana.theflow.data.repository.UserRepository
import com.ana.theflow.ui.common.PostCardRenderer
import com.ana.theflow.ui.common.UiText
import com.ana.theflow.ui.settings.SettingsUi
import com.ana.theflow.ui.settings.dp

class EventsFragment : Fragment() {

    private val postRepository = PostRepository()
    private val authRepository = AuthRepository()
    private val userRepository = UserRepository()
    private lateinit var content: LinearLayout
    private lateinit var progress: ProgressBar
    private lateinit var message: TextView
    private lateinit var recommendedTab: Button
    private lateinit var myEventsTab: Button
    private var selectedTab = EventsTab.RECOMMENDED
    private var currentUser: User? = null
    private var recommendedEvents: List<Post> = emptyList()
    private var createdEvents: List<Post> = emptyList()
    private var registeredEvents: List<Post> = emptyList()
    private var pendingLoads = 0

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val root = SettingsUi.screen(this, getString(R.string.events_title))
        val scroll = SettingsUi.contentScroll(requireContext())
        content = SettingsUi.contentColumn(requireContext())
        progress = ProgressBar(requireContext()).apply {
            indeterminateTintList = android.content.res.ColorStateList.valueOf(requireContext().getColor(R.color.flow_brand))
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = 12.dp()
            }
        }
        message = SettingsUi.message(requireContext(), getString(R.string.events_loading))
        content.addView(tabRow())
        content.addView(progress)
        content.addView(message)
        scroll.addView(content)
        root.addView(scroll)
        return root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        loadCurrentUser()
        loadEvents()
    }

    override fun onResume() {
        super.onResume()
        if (::content.isInitialized) loadEvents()
    }

    private fun loadCurrentUser() {
        val uid = authRepository.getCurrentUserUid() ?: return
        userRepository.getUserByUid(
            uid = uid,
            onSuccess = { user -> currentUser = user },
            onFailure = {}
        )
    }

    private fun loadEvents() {
        pendingLoads = 3
        progress.visibility = View.VISIBLE
        message.text = getString(R.string.events_loading)
        postRepository.loadRecommendedEventPosts(
            onSuccess = {
                recommendedEvents = it
                finishOneLoad()
            },
            onFailure = {
                Toast.makeText(requireContext(), UiText.friendlyError(it, "We could not load recommended events."), Toast.LENGTH_SHORT).show()
                finishOneLoad()
            }
        )
        postRepository.loadRegisteredEventPosts(
            onSuccess = {
                registeredEvents = it
                finishOneLoad()
            },
            onFailure = {
                registeredEvents = emptyList()
                finishOneLoad()
            }
        )
        val uid = authRepository.getCurrentUserUid()
        if (uid == null) {
            createdEvents = emptyList()
            finishOneLoad()
        } else {
            postRepository.loadPostsByAuthor(
                authorId = uid,
                onSuccess = { posts ->
                    createdEvents = posts.filter { it.postType == POST_TYPE_DANCE_ACTIVITY }
                    finishOneLoad()
                },
                onFailure = {
                    createdEvents = emptyList()
                    finishOneLoad()
                }
            )
        }
    }

    private fun finishOneLoad() {
        if (!isAdded) return
        pendingLoads -= 1
        if (pendingLoads > 0) return
        render()
    }

    private fun render() {
        progress.visibility = View.GONE
        content.removeAllViews()
        content.addView(tabRow())
        content.addView(SettingsUi.row(requireContext(), "Create event", "Post a dance event from this account.", onClick = {
            (requireActivity() as MainActivity).openEventCreation()
        }))

        when (selectedTab) {
            EventsTab.RECOMMENDED -> {
                addSection(
                    title = getString(R.string.events_recommended),
                    posts = recommendedEvents.distinctBy { it.postId }.take(12),
                    emptyText = getString(R.string.events_upcoming_empty)
                )
            }
            EventsTab.MY_EVENTS -> {
                addSection("Created Events", createdEvents.distinctBy { it.postId }, "You have not created any events yet.")
                addSection(getString(R.string.events_registered), registeredEvents.distinctBy { it.postId }, "You haven’t registered for any events yet.")
                if (createdEvents.isEmpty() && registeredEvents.isEmpty()) addDiscoverEventsAction()
            }
        }
    }

    private fun tabRow(): View {
        val context = requireContext()
        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            recommendedTab = tabButton(getString(R.string.events_recommended), selectedTab == EventsTab.RECOMMENDED) {
                selectedTab = EventsTab.RECOMMENDED
                render()
            }
            myEventsTab = tabButton(getString(R.string.events_my_events), selectedTab == EventsTab.MY_EVENTS) {
                selectedTab = EventsTab.MY_EVENTS
                render()
            }
            addView(recommendedTab)
            addView(myEventsTab)
        }
    }

    private fun tabButton(textValue: String, selected: Boolean, onClick: () -> Unit): Button {
        return Button(requireContext()).apply {
            text = textValue
            isAllCaps = false
            setTextColor(context.getColor(if (selected) R.color.white else R.color.flow_brand))
            setBackgroundResource(if (selected) R.drawable.bg_flow_button_primary else R.drawable.bg_flow_button_secondary)
            layoutParams = LinearLayout.LayoutParams(0, 42.dp(), 1f).apply {
                rightMargin = 6.dp()
                bottomMargin = 12.dp()
            }
            setOnClickListener { onClick() }
        }
    }

    private fun addSection(title: String, posts: List<Post>, emptyText: String) {
        val context = requireContext()
        content.addView(TextView(context).apply {
            text = title
            setTextColor(context.getColor(R.color.flow_ink))
            textSize = 18f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setPadding(0, 10.dp(), 0, 2.dp())
        })
        if (posts.isEmpty()) {
            content.addView(SettingsUi.message(context, emptyText))
            return
        }
        posts.forEach { post ->
            PostCardRenderer.addPostCard(
                parent = content,
                post = post,
                isEventRegistered = registeredEvents.any { it.postId == post.postId },
                currentUserId = authRepository.getCurrentUserUid().orEmpty(),
                onOpen = { (requireActivity() as MainActivity).openPost(it.postId) },
                onEventRegister = { toggleEventRegistration(it) },
                onRepost = { shareEventToFeed(it) },
                onAuthorOpen = { (requireActivity() as MainActivity).openUserProfile(it) },
                onAuthorEntityOpen = { ref -> (requireActivity() as MainActivity).openAuthorEntity(ref) },
                cardStyle = PostCardRenderer.CardStyle.FLOW_LIGHT
            )
        }
    }

    private fun addDiscoverEventsAction() {
        content.addView(Button(requireContext()).apply {
            text = "Discover Events"
            isAllCaps = false
            setTextColor(context.getColor(R.color.white))
            setBackgroundResource(R.drawable.bg_flow_button_primary)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 44.dp()).apply {
                topMargin = 10.dp()
            }
            setOnClickListener { (requireActivity() as MainActivity).openDiscover() }
        })
    }

    private fun toggleEventRegistration(post: Post) {
        postRepository.toggleEventRegistration(
            post = post,
            onSuccess = { registered ->
                if (!isAdded) return@toggleEventRegistration
                Toast.makeText(requireContext(), if (registered) "Registered" else "Registration cancelled", Toast.LENGTH_SHORT).show()
                loadEvents()
            },
            onFailure = { error ->
                if (!isAdded) return@toggleEventRegistration
                Toast.makeText(requireContext(), UiText.friendlyError(error, "We could not update this registration."), Toast.LENGTH_SHORT).show()
            }
        )
    }

    private fun shareEventToFeed(post: Post) {
        val user = currentUser
        if (user == null) {
            Toast.makeText(requireContext(), "Your profile is still loading.", Toast.LENGTH_SHORT).show()
            loadCurrentUser()
            return
        }
        postRepository.createRepost(
            originalPost = post,
            author = user,
            onSuccess = {
                Toast.makeText(requireContext(), "Event shared to your feed", Toast.LENGTH_SHORT).show()
            },
            onFailure = { error ->
                Toast.makeText(requireContext(), UiText.friendlyError(error, "We could not share this event."), Toast.LENGTH_SHORT).show()
            }
        )
    }

    private enum class EventsTab {
        RECOMMENDED,
        MY_EVENTS
    }

    private companion object {
        const val POST_TYPE_DANCE_ACTIVITY = "dance_activity"
    }
}
