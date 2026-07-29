package com.ana.theflow.ui.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment

class AppearanceSettingsFragment : Fragment() {
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val root = SettingsUi.screen(this, "Appearance")
        val scroll = SettingsUi.contentScroll(requireContext())
        val content = SettingsUi.contentColumn(requireContext())
        content.addView(SettingsUi.row(requireContext(), "Theme", "Theme switching needs app-wide theme persistence.", enabled = false))
        content.addView(SettingsUi.row(requireContext(), "Language", "Language selection needs localization resources and persistence.", enabled = false))
        content.addView(SettingsUi.row(requireContext(), "Display preferences", "Additional display options are not modeled yet.", enabled = false))
        scroll.addView(content)
        root.addView(scroll)
        return root
    }
}
