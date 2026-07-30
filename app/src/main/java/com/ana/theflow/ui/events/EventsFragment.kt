package com.ana.theflow.ui.events

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.ana.theflow.MainActivity
import com.ana.theflow.R
import com.ana.theflow.data.model.post.Post
import com.ana.theflow.data.repository.AuthRepository
import com.ana.theflow.data.repository.PostRepository
import com.ana.theflow.ui.common.PostCardRenderer
import com.ana.theflow.ui.common.UiText
import com.ana.theflow.ui.settings.SettingsUi
import com.ana.theflow.ui.settings.dp

class EventsFragment : Fragment() {

    private val postRepository = PostRepository()
    private val authRepository = AuthRepository()
    private lateinit var content: LinearLayout
    private lateinit var progress: ProgressBar
    private lateinit var message: TextView
    private var upcomingEvents: List<Post> = emptyList()
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
        content.addView(progress)
        content.addView(message)
        scroll.addView(content)
        root.addView(scroll)
        return root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        loadEvents()
    }

    private fun loadEvents() {
        pendingLoads = 2
        progress.visibility = View.VISIBLE
        message.text = getString(R.string.events_loading)
        postRepository.loadUpcomingEventPosts(
            onSuccess = {
                upcomingEvents = it
                finishOneLoad()
            },
            onFailure = {
                Toast.makeText(requireContext(), UiText.friendlyError(it, "We could not load upcoming events."), Toast.LENGTH_SHORT).show()
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
        val shown = mutableSetOf<String>()
        val upcoming = upcomingEvents.distinctBy { it.postId }.take(12).also { posts ->
            posts.forEach { shown.add(it.postId) }
        }
        val registered = registeredEvents.filter { shown.add(it.postId) }.take(12)

        if (upcoming.isEmpty() && registered.isEmpty()) {
            content.addView(SettingsUi.message(requireContext(), getString(R.string.events_empty)))
            content.addView(SettingsUi.row(requireContext(), "Create event", "Post a class, social, workshop, audition, or dance gathering.", onClick = {
                (requireActivity() as MainActivity).openEventCreation()
            }))
            content.addView(SettingsUi.row(requireContext(), "Explore events", "Search nearby events, classes, and workshops in Discover.", onClick = {
                (requireActivity() as MainActivity).openSearch()
            }))
            return
        }

        content.addView(SettingsUi.row(requireContext(), "Create event", "Post a dance event from this account.", onClick = {
            (requireActivity() as MainActivity).openEventCreation()
        }))
        addSection(getString(R.string.events_upcoming), upcoming, getString(R.string.events_upcoming_empty))
        addSection(getString(R.string.events_registered), registered, getString(R.string.events_registered_empty))
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
                onAuthorOpen = { (requireActivity() as MainActivity).openUserProfile(it) },
                cardStyle = PostCardRenderer.CardStyle.FLOW_LIGHT
            )
        }
    }
}
