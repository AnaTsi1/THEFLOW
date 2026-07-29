package com.ana.theflow.ui.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.Switch
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.ana.theflow.R
import com.ana.theflow.data.model.settings.MessageSettings
import com.ana.theflow.data.repository.SettingsRepository
import com.ana.theflow.ui.common.UiText

class PrivacySafetySettingsFragment : Fragment() {
    private val repository = SettingsRepository()
    private var binding = false
    private lateinit var receiveGroup: RadioGroup
    private lateinit var messageNotifications: Switch
    private lateinit var previews: Switch
    private lateinit var readReceipts: Switch
    private lateinit var emojiSuggestions: Switch

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val root = SettingsUi.screen(this, "Privacy and Safety")
        val scroll = SettingsUi.contentScroll(requireContext())
        val content = SettingsUi.contentColumn(requireContext())
        content.addView(SettingsUi.message(requireContext(), "Message privacy here is the same setting used when another user tries to start a conversation."))
        content.addView(SettingsUi.message(requireContext(), "Who can message me"))
        receiveGroup = RadioGroup(requireContext()).apply {
            orientation = RadioGroup.VERTICAL
            addView(radio("Everyone", ID_EVERYONE))
            addView(radio("People I follow", ID_FOLLOWING))
            addView(radio("Nobody", ID_NOBODY))
        }
        content.addView(receiveGroup)
        messageNotifications = switchRow("Message notifications")
        previews = switchRow("Show message previews")
        readReceipts = switchRow("Read receipts")
        emojiSuggestions = switchRow("Emoji suggestions")
        listOf(messageNotifications, previews, readReceipts, emojiSuggestions).forEach(content::addView)
        content.addView(SettingsUi.row(requireContext(), "Blocked users", "Management screen requires a blocked-user list endpoint.", enabled = false))
        content.addView(SettingsUi.row(requireContext(), "Hidden or muted accounts", "Muted accounts are not modeled yet.", enabled = false))
        content.addView(SettingsUi.row(requireContext(), "Profile visibility", "Private profiles require Firestore rules and feed-query changes.", enabled = false))
        receiveGroup.setOnCheckedChangeListener { _, _ -> save() }
        listOf(messageNotifications, previews, readReceipts, emojiSuggestions).forEach {
            it.setOnCheckedChangeListener { _, _ -> save() }
        }
        load()
        scroll.addView(content)
        root.addView(scroll)
        return root
    }

    private fun load() {
        repository.loadSettings(
            onSuccess = { _, messages ->
                if (!isAdded) return@loadSettings
                binding = true
                receiveGroup.check(
                    when (messages.receiveMessagesFrom) {
                        MessageSettings.RECEIVE_FOLLOWING -> ID_FOLLOWING
                        MessageSettings.RECEIVE_NOBODY -> ID_NOBODY
                        else -> ID_EVERYONE
                    }
                )
                messageNotifications.isChecked = messages.messageNotificationsEnabled
                previews.isChecked = messages.showMessagePreviews
                readReceipts.isChecked = messages.readReceipts
                emojiSuggestions.isChecked = messages.emojiSuggestions
                binding = false
            },
            onFailure = { error -> if (isAdded) Toast.makeText(requireContext(), UiText.friendlyError(error, "We could not load privacy settings."), Toast.LENGTH_SHORT).show() }
        )
    }

    private fun save() {
        if (binding) return
        val messages = MessageSettings(
            messageNotificationsEnabled = messageNotifications.isChecked,
            showMessagePreviews = previews.isChecked,
            receiveMessagesFrom = when (receiveGroup.checkedRadioButtonId) {
                ID_FOLLOWING -> MessageSettings.RECEIVE_FOLLOWING
                ID_NOBODY -> MessageSettings.RECEIVE_NOBODY
                else -> MessageSettings.RECEIVE_EVERYONE
            },
            readReceipts = readReceipts.isChecked,
            emojiSuggestions = emojiSuggestions.isChecked
        )
        repository.saveMessageSettings(
            settings = messages,
            onSuccess = {},
            onFailure = { error -> if (isAdded) Toast.makeText(requireContext(), UiText.friendlyError(error, "We could not save privacy settings."), Toast.LENGTH_SHORT).show() }
        )
    }

    private fun radio(label: String, idValue: Int): RadioButton {
        return RadioButton(requireContext()).apply {
            id = idValue
            text = label
            setTextColor(context.getColor(R.color.flow_ink))
            textSize = 14f
            minHeight = 48.dp()
        }
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

    companion object {
        private const val ID_EVERYONE = 8101
        private const val ID_FOLLOWING = 8102
        private const val ID_NOBODY = 8103
    }
}
