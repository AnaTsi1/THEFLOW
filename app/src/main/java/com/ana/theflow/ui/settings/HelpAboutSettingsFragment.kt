package com.ana.theflow.ui.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.ana.theflow.BuildConfig

class HelpAboutSettingsFragment : Fragment() {
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val root = SettingsUi.screen(this, "Help and About")
        val scroll = SettingsUi.contentScroll(requireContext())
        val content = SettingsUi.contentColumn(requireContext())
        content.addView(SettingsUi.row(requireContext(), "Help", "Support content URL is not configured yet.", enabled = false))
        content.addView(SettingsUi.row(requireContext(), "Report a problem", "Issue-report destination requires backend or support link.", enabled = false))
        content.addView(SettingsUi.row(requireContext(), "Community guidelines", "Legal/community content is not configured yet.", enabled = false))
        content.addView(SettingsUi.row(requireContext(), "Privacy policy", "No production privacy-policy URL has been configured.", enabled = false))
        content.addView(SettingsUi.row(requireContext(), "Terms", "No production terms URL has been configured.", enabled = false))
        content.addView(SettingsUi.row(requireContext(), "App version", value = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})", enabled = false))
        scroll.addView(content)
        root.addView(scroll)
        return root
    }
}
