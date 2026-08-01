package com.ana.theflow.ui.settings

import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.widget.AutoCompleteTextView
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import com.ana.theflow.R
import com.ana.theflow.data.repository.DiscoveryRepository
import com.ana.theflow.data.repository.UserRepository
import com.ana.theflow.ui.common.UiText
import com.ana.theflow.utilities.CityOptions

class FeedDiscoverySettingsFragment : Fragment() {
    private val userRepository = UserRepository()
    private lateinit var content: LinearLayout
    private var preferences = UserRepository.PreferenceSettings()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val root = SettingsUi.screen(this, "Feed and Discovery")
        val scroll = SettingsUi.contentScroll(requireContext())
        content = SettingsUi.contentColumn(requireContext())
        scroll.addView(content)
        root.addView(scroll)
        renderLoading()
        loadPreferences()
        return root
    }

    private fun renderLoading() {
        content.removeAllViews()
        content.addView(SettingsUi.message(requireContext(), "Loading recommendation preferences..."))
    }

    private fun loadPreferences() {
        userRepository.loadPreferenceSettings(
            onSuccess = {
                if (!isAdded) return@loadPreferenceSettings
                preferences = it
                render()
            },
            onFailure = { error ->
                if (!isAdded) return@loadPreferenceSettings
                content.removeAllViews()
                content.addView(SettingsUi.message(requireContext(), UiText.friendlyError(error, "We could not load preferences.")))
            }
        )
    }

    private fun render() {
        content.removeAllViews()
        content.addView(SettingsUi.message(requireContext(), "Edit only the preference you selected. These values continue to shape Discover and recommendations."))
        content.addView(SettingsUi.row(requireContext(), "Dance styles", "Styles used by Discover and recommendation ranking.", preferences.styles.summary("Choose styles"), onClick = ::editStyles))
        content.addView(SettingsUi.row(requireContext(), "Dance level", "Used for matching classes and events.", preferences.level.ifBlank { "Choose level" }, onClick = ::editLevel))
        content.addView(SettingsUi.row(requireContext(), "Preferred city", "Default area for Discover recommendations.", preferences.location.ifBlank { "Choose city" }, onClick = ::editCity))
        content.addView(SettingsUi.row(requireContext(), "Preferred studios", "Names already saved in your recommendation profile.", preferences.preferredStudios.summary("None selected"), onClick = { editList("Preferred studios", preferences.preferredStudios) { save(preferredStudios = it) } }))
        content.addView(SettingsUi.row(requireContext(), "Preferred teachers", "Profiles that influence Discover.", preferences.preferredTeachers.summary("None selected"), onClick = { editList("Preferred teachers", preferences.preferredTeachers) { save(preferredTeachers = it) } }))
        content.addView(SettingsUi.row(requireContext(), "Preferred dancers", "Dancers that influence Discover.", preferences.preferredDancers.summary("None selected"), onClick = { editList("Preferred dancers", preferences.preferredDancers) { save(preferredDancers = it) } }))
        content.addView(SettingsUi.row(requireContext(), "Location radius", "Radius preferences are not modeled in Firebase yet.", "Coming soon", enabled = false))
    }

    private fun editStyles() {
        val selected = styleOptions.map { option -> preferences.styles.any { it.equals(option, ignoreCase = true) } }.toBooleanArray()
        AlertDialog.Builder(requireContext())
            .setTitle("Dance styles")
            .setMultiChoiceItems(styleOptions.toTypedArray(), selected) { _, which, checked -> selected[which] = checked }
            .setNegativeButton(R.string.action_cancel, null)
            .setPositiveButton("Apply Styles") { _, _ ->
                val styles = styleOptions.filterIndexed { index, _ -> selected[index] }
                save(styles = styles.ifEmpty { preferences.styles })
            }
            .create()
            .also(::showConstrainedDialog)
    }

    private fun editLevel() {
        var selected = levelOptions.indexOfFirst { it.equals(preferences.level, ignoreCase = true) }.coerceAtLeast(0)
        AlertDialog.Builder(requireContext())
            .setTitle("Dance level")
            .setSingleChoiceItems(levelOptions.toTypedArray(), selected) { _, which -> selected = which }
            .setNegativeButton(R.string.action_cancel, null)
            .setPositiveButton("Save Level") { _, _ -> save(level = levelOptions[selected]) }
            .create()
            .also(::showConstrainedDialog)
    }

    private fun editCity() {
        val input = AutoCompleteTextView(requireContext()).apply {
            hint = "Preferred city"
            setText(preferences.location, false)
            setTextColor(context.getColor(R.color.flow_ink))
            setHintTextColor(context.getColor(R.color.flow_text_muted))
            setBackgroundResource(R.drawable.bg_flow_input)
            setPadding(14.dp(), 0, 14.dp(), 0)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 52.dp())
        }
        CityOptions.configureCitySelector(requireContext(), input)
        showFocusedEditor("Preferred city", listOf(input), "Save City") {
            val city = CityOptions.normalizeOptionalCity(input.text.toString())
            if (city == null) {
                Toast.makeText(requireContext(), "Choose a city from the list.", Toast.LENGTH_SHORT).show()
            } else {
                save(location = city)
            }
        }
    }

    private fun editList(title: String, values: List<String>, onSave: (List<String>) -> Unit) {
        val input = EditText(requireContext()).apply {
            hint = "$title, comma separated"
            setText(values.joinToString(", "))
            setTextColor(context.getColor(R.color.flow_ink))
            setHintTextColor(context.getColor(R.color.flow_text_muted))
            setBackgroundResource(R.drawable.bg_flow_input)
            minHeight = 54.dp()
            setPadding(14.dp(), 0, 14.dp(), 0)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }
        val note = SettingsUi.message(requireContext(), "This field is stored in the existing recommendation profile. A full entity picker needs a shared profile/studio search contract.")
        showFocusedEditor(title, listOf(input, note), "Save") {
            onSave(input.text.toString().split(",").map { it.trim() }.filter { it.isNotBlank() })
        }
    }

    private fun showFocusedEditor(title: String, views: List<View>, positive: String, onSave: () -> Unit) {
        val container = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20.dp(), 12.dp(), 20.dp(), 0)
            views.forEach { addView(it) }
        }
        AlertDialog.Builder(requireContext())
            .setTitle(title)
            .setView(ScrollView(requireContext()).apply { addView(container) })
            .setNegativeButton(R.string.action_cancel, null)
            .setPositiveButton(positive) { _, _ -> onSave() }
            .create()
            .also(::showConstrainedDialog)
    }

    private fun save(
        styles: List<String> = preferences.styles,
        level: String = preferences.level,
        location: String = preferences.location,
        preferredStudios: List<String> = preferences.preferredStudios,
        preferredTeachers: List<String> = preferences.preferredTeachers,
        preferredDancers: List<String> = preferences.preferredDancers
    ) {
        userRepository.updatePreferenceSettings(
            styles = styles,
            level = level,
            location = location,
            preferredStudios = preferredStudios,
            preferredTeachers = preferredTeachers,
            preferredDancers = preferredDancers,
            onSuccess = {
                if (!isAdded) return@updatePreferenceSettings
                preferences = UserRepository.PreferenceSettings(styles, level, location, preferredStudios, preferredTeachers, preferredDancers)
                DiscoveryRepository.hydratePreferences(styles, level, location, preferredStudios, preferredTeachers, preferredDancers)
                Toast.makeText(requireContext(), "Preferences updated", Toast.LENGTH_SHORT).show()
                render()
            },
            onFailure = { error ->
                if (!isAdded) return@updatePreferenceSettings
                Toast.makeText(requireContext(), UiText.friendlyError(error, "We could not update preferences."), Toast.LENGTH_SHORT).show()
            }
        )
    }

    private fun showConstrainedDialog(dialog: AlertDialog) {
        dialog.setOnShowListener { constrainDialog(dialog.window) }
        dialog.show()
    }

    private fun constrainDialog(window: Window?) {
        window ?: return
        val metrics = resources.displayMetrics
        val width = if (metrics.widthPixels / metrics.density >= 600) 520.dp() else metrics.widthPixels - 32.dp()
        window.setLayout(width, WindowManager.LayoutParams.WRAP_CONTENT)
        window.setGravity(Gravity.CENTER)
    }

    private fun List<String>.summary(empty: String): String {
        if (isEmpty()) return empty
        return take(3).joinToString(", ") + if (size > 3) " +${size - 3}" else ""
    }

    companion object {
        private val styleOptions = listOf("Hip Hop", "Heels", "Salsa", "Contemporary", "Afro", "Ballet", "Jazz", "Bachata")
        private val levelOptions = listOf("Beginner", "Intermediate", "Advanced", "Professional")
    }
}
