package com.ana.theflow.ui.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.ana.theflow.R
import com.ana.theflow.data.model.settings.NotificationSettings
import com.ana.theflow.data.repository.SettingsRepository
import com.ana.theflow.ui.common.UiText

class NotificationSettingsFragment : Fragment() {
    private val repository = SettingsRepository()
    private var current = NotificationSettings()
    private var binding = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val root = SettingsUi.screen(this, "Notifications")
        val scroll = SettingsUi.contentScroll(requireContext())
        val content = SettingsUi.contentColumn(requireContext())
        content.addView(SettingsUi.message(requireContext(), "Notification controls save individually without leaving this screen."))
        val all = switchRow("Enable notifications")
        val likes = switchRow("Likes and comments")
        val followers = switchRow("New followers")
        val messages = switchRow("Messages")
        val events = switchRow("Events and classes")
        val recommendations = switchRow("Recommendations")
        val verification = switchRow("Professional-verification updates")
        listOf(all, likes, followers, messages, events, recommendations, verification).forEach(content::addView)

        fun save() {
            if (binding) return
            current = NotificationSettings(
                allNotificationsEnabled = all.isChecked,
                likes = likes.isChecked,
                comments = likes.isChecked,
                newFollowers = followers.isChecked,
                privateMessages = messages.isChecked,
                eventRecommendations = events.isChecked || recommendations.isChecked,
                registeredEventUpdates = events.isChecked,
                professionalApplicationUpdates = verification.isChecked
            )
            repository.saveNotificationSettings(
                settings = current,
                onSuccess = {},
                onFailure = { error -> if (isAdded) Toast.makeText(requireContext(), UiText.friendlyError(error, "We could not save notification settings."), Toast.LENGTH_SHORT).show() }
            )
        }

        listOf(all, likes, followers, messages, events, recommendations, verification).forEach {
            it.setOnCheckedChangeListener { _, _ -> save() }
        }
        repository.loadSettings(
            onSuccess = { notifications, _ ->
                if (!isAdded) return@loadSettings
                binding = true
                current = notifications
                all.isChecked = notifications.allNotificationsEnabled
                likes.isChecked = notifications.likes || notifications.comments
                followers.isChecked = notifications.newFollowers
                messages.isChecked = notifications.privateMessages
                events.isChecked = notifications.eventRecommendations || notifications.registeredEventUpdates
                recommendations.isChecked = notifications.eventRecommendations
                verification.isChecked = notifications.professionalApplicationUpdates
                binding = false
            },
            onFailure = { error -> if (isAdded) Toast.makeText(requireContext(), UiText.friendlyError(error, "We could not load notification settings."), Toast.LENGTH_SHORT).show() }
        )
        scroll.addView(content)
        root.addView(scroll)
        return root
    }

    private fun switchRow(label: String): Switch {
        return Switch(requireContext()).apply {
            text = label
            setTextColor(context.getColor(R.color.flow_ink))
            textSize = 14f
            minHeight = 48.dp()
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 52.dp()).apply {
                bottomMargin = 8.dp()
            }
        }
    }
}
