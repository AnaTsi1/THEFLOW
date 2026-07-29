package com.ana.theflow.ui.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.ana.theflow.MainActivity

class ProfileSettingsFragment : Fragment() {
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val root = SettingsUi.screen(this, "Profile")
        val scroll = SettingsUi.contentScroll(requireContext())
        val content = SettingsUi.contentColumn(requireContext())
        val activity = requireActivity() as MainActivity
        content.addView(SettingsUi.row(requireContext(), "Edit public profile", "Bio, profile image, cover image, and public presentation.", onClick = activity::openEditProfile))
        content.addView(SettingsUi.row(requireContext(), "Dance experience", "Styles, level, studios, teachers, and background.", onClick = activity::openEditPreferences))
        content.addView(SettingsUi.row(requireContext(), "Professional role", "Role details are stored on your profile and verification application.", enabled = false))
        content.addView(SettingsUi.row(requireContext(), "Professional verification", "Apply or review your teacher, choreographer, or studio status.", onClick = activity::openProfessionalVerification))
        scroll.addView(content)
        root.addView(scroll)
        return root
    }
}
