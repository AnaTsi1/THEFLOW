package com.ana.theflow.ui.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.ana.theflow.MainActivity

class FeedDiscoverySettingsFragment : Fragment() {
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val root = SettingsUi.screen(this, "Feed and Discovery")
        val scroll = SettingsUi.contentScroll(requireContext())
        val content = SettingsUi.contentColumn(requireContext())
        val activity = requireActivity() as MainActivity
        content.addView(SettingsUi.message(requireContext(), "These preferences use the existing recommendation and Discover settings flow."))
        content.addView(SettingsUi.row(requireContext(), "Preferred dance styles", "Choose the styles that shape recommendations.", onClick = activity::openEditPreferences))
        content.addView(SettingsUi.row(requireContext(), "Dance level", "Set your current level for classes and events.", onClick = activity::openEditPreferences))
        content.addView(SettingsUi.row(requireContext(), "Preferred city", "Used for nearby classes, studios, and events.", onClick = activity::openEditPreferences))
        content.addView(SettingsUi.row(requireContext(), "Preferred studios, teachers, and dancers", "Select people and places that influence Discover.", onClick = activity::openEditPreferences))
        content.addView(SettingsUi.row(requireContext(), "Recommendation preferences", "Update recommendation signals.", onClick = activity::openEditPreferences))
        content.addView(SettingsUi.row(requireContext(), "Location and search radius", "Precise radius persistence requires additional preference fields.", enabled = false))
        scroll.addView(content)
        root.addView(scroll)
        return root
    }
}
