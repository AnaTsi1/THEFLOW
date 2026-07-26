package com.ana.theflow.ui.settings

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.RadioButton
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.ana.theflow.MainActivity
import com.ana.theflow.R
import com.ana.theflow.data.model.settings.MessageSettings
import com.ana.theflow.data.model.settings.NotificationSettings
import com.ana.theflow.data.repository.AuthRepository
import com.ana.theflow.data.repository.SettingsRepository
import com.ana.theflow.data.repository.UserRepository
import com.ana.theflow.databinding.FragmentSettingsBinding
import com.ana.theflow.ui.auth.LoginActivity
import com.ana.theflow.utilities.Constants

class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!
    private val authRepository = AuthRepository()
    private val userRepository = UserRepository()
    private val settingsRepository = SettingsRepository()
    private var isBindingSettings = false
    private var notificationSettings = NotificationSettings()
    private var messageSettings = MessageSettings()

    // Creates and returns the fragment view.
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    // Connects the screen UI after the view is ready.
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.settingsBTNBack.setOnClickListener {
            parentFragmentManager.popBackStack()
        }
        binding.settingsBTNEditPreferences.setOnClickListener {
            (requireActivity() as MainActivity).openEditPreferences()
        }
        binding.settingsBTNProfessionalVerification.setOnClickListener {
            (requireActivity() as MainActivity).openProfessionalVerification()
        }
        binding.settingsBTNAdminReview.setOnClickListener {
            (requireActivity() as MainActivity).openAdminReview()
        }
        binding.settingsBTNLogout.setOnClickListener {
            logout()
        }
        setupSettingsListeners()
        loadAdminAccess()
        loadUserSettings()
    }

    private fun setupSettingsListeners() {
        val saveNotifications = {
            if (!isBindingSettings) saveNotificationSettingsFromUi()
        }
        binding.settingsSWAllNotifications.setOnCheckedChangeListener { _, _ ->
            updateNotificationChildrenState()
            saveNotifications()
        }
        binding.settingsSWLikes.setOnCheckedChangeListener { _, _ -> saveNotifications() }
        binding.settingsSWComments.setOnCheckedChangeListener { _, _ -> saveNotifications() }
        binding.settingsSWNewFollowers.setOnCheckedChangeListener { _, _ -> saveNotifications() }
        binding.settingsSWPrivateMessages.setOnCheckedChangeListener { _, _ -> saveNotifications() }
        binding.settingsSWEventRecommendations.setOnCheckedChangeListener { _, _ -> saveNotifications() }
        binding.settingsSWRegisteredEventUpdates.setOnCheckedChangeListener { _, _ -> saveNotifications() }
        binding.settingsSWProfessionalApplicationUpdates.setOnCheckedChangeListener { _, _ -> saveNotifications() }

        val saveMessages = {
            if (!isBindingSettings) saveMessageSettingsFromUi()
        }
        binding.settingsSWMessageNotifications.setOnCheckedChangeListener { _, _ -> saveMessages() }
        binding.settingsSWMessagePreviews.setOnCheckedChangeListener { _, _ -> saveMessages() }
        binding.settingsSWReadReceipts.setOnCheckedChangeListener { _, _ -> saveMessages() }
        binding.settingsSWEmojiSuggestions.setOnCheckedChangeListener { _, _ -> saveMessages() }
        binding.settingsRADReceiveMessages.setOnCheckedChangeListener { _, _ -> saveMessages() }
    }

    private fun loadUserSettings() {
        setSettingsMessage("Loading settings...")
        isBindingSettings = true
        settingsRepository.loadSettings(
            onSuccess = { notifications, messages ->
                if (_binding == null) return@loadSettings
                notificationSettings = notifications
                messageSettings = messages
                bindNotificationSettings(notifications)
                bindMessageSettings(messages)
                isBindingSettings = false
                setSettingsMessage("")
            },
            onFailure = { error ->
                if (_binding == null) return@loadSettings
                isBindingSettings = false
                setSettingsMessage(error)
            }
        )
    }

    private fun bindNotificationSettings(settings: NotificationSettings) {
        binding.settingsSWAllNotifications.isChecked = settings.allNotificationsEnabled
        binding.settingsSWLikes.isChecked = settings.likes
        binding.settingsSWComments.isChecked = settings.comments
        binding.settingsSWNewFollowers.isChecked = settings.newFollowers
        binding.settingsSWPrivateMessages.isChecked = settings.privateMessages
        binding.settingsSWEventRecommendations.isChecked = settings.eventRecommendations
        binding.settingsSWRegisteredEventUpdates.isChecked = settings.registeredEventUpdates
        binding.settingsSWProfessionalApplicationUpdates.isChecked = settings.professionalApplicationUpdates
        updateNotificationChildrenState()
    }

    private fun bindMessageSettings(settings: MessageSettings) {
        binding.settingsSWMessageNotifications.isChecked = settings.messageNotificationsEnabled
        binding.settingsSWMessagePreviews.isChecked = settings.showMessagePreviews
        binding.settingsSWReadReceipts.isChecked = settings.readReceipts
        binding.settingsSWEmojiSuggestions.isChecked = settings.emojiSuggestions
        val selectedId = when (settings.receiveMessagesFrom) {
            MessageSettings.RECEIVE_FOLLOWING -> R.id.settings_RAD_following
            MessageSettings.RECEIVE_NOBODY -> R.id.settings_RAD_nobody
            else -> R.id.settings_RAD_everyone
        }
        binding.settingsRADReceiveMessages.check(selectedId)
    }

    private fun updateNotificationChildrenState() {
        val enabled = binding.settingsSWAllNotifications.isChecked
        listOf(
            binding.settingsSWLikes,
            binding.settingsSWComments,
            binding.settingsSWNewFollowers,
            binding.settingsSWPrivateMessages,
            binding.settingsSWEventRecommendations,
            binding.settingsSWRegisteredEventUpdates,
            binding.settingsSWProfessionalApplicationUpdates
        ).forEach { view ->
            view.isEnabled = enabled
            view.alpha = if (enabled) 1f else 0.45f
        }
    }

    private fun saveNotificationSettingsFromUi() {
        val updated = NotificationSettings(
            allNotificationsEnabled = binding.settingsSWAllNotifications.isChecked,
            likes = binding.settingsSWLikes.isChecked,
            comments = binding.settingsSWComments.isChecked,
            newFollowers = binding.settingsSWNewFollowers.isChecked,
            privateMessages = binding.settingsSWPrivateMessages.isChecked,
            eventRecommendations = binding.settingsSWEventRecommendations.isChecked,
            registeredEventUpdates = binding.settingsSWRegisteredEventUpdates.isChecked,
            professionalApplicationUpdates = binding.settingsSWProfessionalApplicationUpdates.isChecked
        )
        if (updated == notificationSettings) return
        notificationSettings = updated
        settingsRepository.saveNotificationSettings(
            settings = updated,
            onSuccess = { if (_binding != null) setSettingsMessage("Notification settings saved") },
            onFailure = { error ->
                if (_binding != null) {
                    Toast.makeText(requireContext(), error, Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    private fun saveMessageSettingsFromUi() {
        val updated = MessageSettings(
            messageNotificationsEnabled = binding.settingsSWMessageNotifications.isChecked,
            showMessagePreviews = binding.settingsSWMessagePreviews.isChecked,
            receiveMessagesFrom = receiveMessagesValue(),
            readReceipts = binding.settingsSWReadReceipts.isChecked,
            emojiSuggestions = binding.settingsSWEmojiSuggestions.isChecked
        )
        if (updated == messageSettings) return
        messageSettings = updated
        settingsRepository.saveMessageSettings(
            settings = updated,
            onSuccess = { if (_binding != null) setSettingsMessage("Message settings saved") },
            onFailure = { error ->
                if (_binding != null) {
                    Toast.makeText(requireContext(), error, Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    private fun receiveMessagesValue(): String {
        return when (binding.settingsRADReceiveMessages.checkedRadioButtonId) {
            R.id.settings_RAD_following -> MessageSettings.RECEIVE_FOLLOWING
            R.id.settings_RAD_nobody -> MessageSettings.RECEIVE_NOBODY
            else -> MessageSettings.RECEIVE_EVERYONE
        }
    }

    private fun setSettingsMessage(message: String) {
        binding.settingsLBLSettingsMessage.text = message
        binding.settingsLBLSettingsMessage.visibility = if (message.isBlank()) View.GONE else View.VISIBLE
    }

    private fun loadAdminAccess() {
        val uid = authRepository.getCurrentUserUid() ?: return
        userRepository.getUserByUid(
            uid = uid,
            onSuccess = success@ { user ->
                if (_binding == null) return@success
                binding.settingsBTNAdminReview.visibility =
                    if (user.role.isAdminRole()) View.VISIBLE else View.GONE
            },
            onFailure = failure@ {
                if (_binding == null) return@failure
                binding.settingsBTNAdminReview.visibility = View.GONE
            }
        )
    }

    // Signs out the current user and returns to login.
    private fun logout() {
        authRepository.logout()
        val intent = Intent(requireContext(), LoginActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        startActivity(intent)
    }

    // Clears the fragment binding when the view is destroyed.
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun String.isAdminRole(): Boolean {
        return equals(Constants.UserRole.ADMIN.name, ignoreCase = true) ||
            equals(Constants.UserRole.ADMIN.firestoreValue, ignoreCase = true)
    }
}
