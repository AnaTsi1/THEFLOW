package com.ana.theflow.ui.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.ana.theflow.MainActivity
import com.ana.theflow.R
import com.ana.theflow.data.model.account.ActiveAccount
import com.ana.theflow.data.repository.AuthRepository
import com.ana.theflow.data.repository.UserRepository
import com.ana.theflow.data.session.ActiveAccountHolder
import com.ana.theflow.databinding.FragmentSettingsBinding
import com.ana.theflow.ui.common.ResponsiveLayout
import com.ana.theflow.utilities.Constants

class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!
    private val authRepository = AuthRepository()
    private val userRepository = UserRepository()
    private var isAdmin = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.settingsBTNBack.setOnClickListener { parentFragmentManager.popBackStack() }
        ResponsiveLayout.constrainToReadableWidth(binding.settingsLAYCategories)
        renderCategories()
        loadAdminState()
    }

    // The Admin row must never appear for non-admins - not disabled, not visible-but-inert - so
    // it only gets added once the signed-in user's role is confirmed, and the whole list is
    // re-rendered from scratch if that flips the state.
    private fun loadAdminState() {
        val uid = authRepository.getCurrentUserUid() ?: return
        userRepository.getUserByUid(
            uid = uid,
            onSuccess = { user ->
                if (_binding == null) return@getUserByUid
                val admin = user.role.isAdminRole()
                if (admin != isAdmin) {
                    isAdmin = admin
                    renderCategories()
                }
            },
            onFailure = {}
        )
    }

    private fun renderCategories() {
        val activity = requireActivity() as MainActivity
        val context = requireContext()
        // Feed & Discovery is entirely personal-taste recommendation preferences (styles, level,
        // city, preferred studios/teachers/dancers) with no studio equivalent in the data model at
        // all - it stays hidden while a business account is active instead of offering settings
        // that quietly do nothing for the account you're actually using.
        val isStudioActive = ActiveAccountHolder.current() is ActiveAccount.StudioAccount
        binding.settingsLAYCategories.removeAllViews()
        if (isAdmin) {
            binding.settingsLAYCategories.addView(SettingsUi.row(context, "Admin", "Studio requests, professional applications, content reports, and user permissions.", iconRes = R.drawable.ic_check_circle_24, onClick = activity::openAdminSettings))
        }
        binding.settingsLAYCategories.addView(SettingsUi.row(context, "Account", "Personal information, sign out, and account deletion.", iconRes = R.drawable.ic_profile_24, onClick = activity::openAccountSettings))
        binding.settingsLAYCategories.addView(SettingsUi.row(context, "Profile", "Public profile, dance experience, and professional verification.", iconRes = R.drawable.ic_edit_24, onClick = activity::openProfileSettings))
        binding.settingsLAYCategories.addView(SettingsUi.row(context, "Add a business account", "Create a studio page or claim one you already run.", iconRes = R.drawable.ic_work_24, onClick = activity::openAddBusinessAccountMenu))
        if (!isStudioActive) {
            binding.settingsLAYCategories.addView(SettingsUi.row(context, "Feed and Discovery", "Styles, level, city, recommendations, and search preferences.", iconRes = R.drawable.ic_discover_24, onClick = activity::openFeedDiscoverySettings))
        }
        binding.settingsLAYCategories.addView(SettingsUi.row(context, "Notifications", "Likes, comments, followers, messages, events, and verification updates.", iconRes = R.drawable.ic_notifications_24, onClick = activity::openNotificationSettings))
        binding.settingsLAYCategories.addView(SettingsUi.row(context, "Privacy and Safety", "Message privacy, blocked users, visibility, and safety controls.", iconRes = R.drawable.ic_lock_24, onClick = activity::openPrivacySafetySettings))
        binding.settingsLAYCategories.addView(SettingsUi.row(context, "Appearance", "Theme, language, and display options.", iconRes = R.drawable.ic_appearance_24, onClick = activity::openAppearanceSettings))
        binding.settingsLAYCategories.addView(SettingsUi.row(context, "Help and About", "Help, report a problem, guidelines, legal, and app version.", iconRes = R.drawable.ic_help_24, onClick = activity::openHelpAboutSettings))
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    private fun String.isAdminRole(): Boolean {
        return equals(Constants.UserRole.ADMIN.name, ignoreCase = true) ||
            equals(Constants.UserRole.ADMIN.firestoreValue, ignoreCase = true)
    }
}
