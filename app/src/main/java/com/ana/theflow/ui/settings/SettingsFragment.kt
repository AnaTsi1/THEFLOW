package com.ana.theflow.ui.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.ana.theflow.MainActivity
import com.ana.theflow.databinding.FragmentSettingsBinding
import com.ana.theflow.ui.common.ResponsiveLayout

class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.settingsBTNBack.setOnClickListener { parentFragmentManager.popBackStack() }
        ResponsiveLayout.constrainToReadableWidth(binding.settingsLAYCategories)
        renderCategories()
    }

    private fun renderCategories() {
        val activity = requireActivity() as MainActivity
        val context = requireContext()
        binding.settingsLAYCategories.removeAllViews()
        binding.settingsLAYCategories.addView(SettingsUi.row(context, "Account", "Personal information, sign out, and account deletion.", onClick = activity::openAccountSettings))
        binding.settingsLAYCategories.addView(SettingsUi.row(context, "Profile", "Public profile, dance experience, and professional verification.", onClick = activity::openProfileSettings))
        binding.settingsLAYCategories.addView(SettingsUi.row(context, "Feed and Discovery", "Styles, level, city, recommendations, and search preferences.", onClick = activity::openFeedDiscoverySettings))
        binding.settingsLAYCategories.addView(SettingsUi.row(context, "Notifications", "Likes, comments, followers, messages, events, and verification updates.", onClick = activity::openNotificationSettings))
        binding.settingsLAYCategories.addView(SettingsUi.row(context, "Privacy and Safety", "Message privacy, blocked users, visibility, and safety controls.", onClick = activity::openPrivacySafetySettings))
        binding.settingsLAYCategories.addView(SettingsUi.row(context, "Appearance", "Theme, language, and display options.", onClick = activity::openAppearanceSettings))
        binding.settingsLAYCategories.addView(SettingsUi.row(context, "Help and About", "Help, report a problem, guidelines, legal, and app version.", onClick = activity::openHelpAboutSettings))
        binding.settingsLAYCategories.addView(SettingsUi.row(context, "Create a studio page", "Request a new business account for your studio.", onClick = { activity.openStudioRequest(mode = "create") }))
        binding.settingsLAYCategories.addView(SettingsUi.row(context, "Claim an existing studio", "Find your studio in Search, then tap Claim Studio on its page.", onClick = { activity.openSearch() }))
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
