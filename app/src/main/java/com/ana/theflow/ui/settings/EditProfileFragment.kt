package com.ana.theflow.ui.settings

import android.net.Uri
import android.os.Bundle
import android.text.InputFilter
import android.text.InputType
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.widget.AutoCompleteTextView
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import com.ana.theflow.R
import com.ana.theflow.data.model.user.User
import com.ana.theflow.data.repository.AuthRepository
import com.ana.theflow.data.repository.DiscoveryRepository
import com.ana.theflow.data.repository.StorageRepository
import com.ana.theflow.data.repository.UserRepository
import com.ana.theflow.ui.common.UiText
import com.ana.theflow.utilities.CityOptions
import com.bumptech.glide.Glide

class EditProfileFragment : Fragment() {
    private val authRepository = AuthRepository()
    private val userRepository = UserRepository()
    private val storageRepository = StorageRepository()
    private var user: User? = null
    private lateinit var content: LinearLayout
    private lateinit var progress: TextView
    private var pendingImageTarget: ImageTarget = ImageTarget.PROFILE

    private val imagePicker = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) uploadImage(uri, pendingImageTarget)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val root = SettingsUi.screen(this, "Edit profile")
        val scroll = SettingsUi.contentScroll(requireContext())
        content = SettingsUi.contentColumn(requireContext())
        progress = SettingsUi.message(requireContext(), "Loading profile...")
        content.addView(progress)
        scroll.addView(content)
        root.addView(scroll)
        load()
        return root
    }

    private fun load() {
        val uid = authRepository.getCurrentUserUid()
        if (uid == null) {
            progress.text = "Please log in to edit your profile."
            return
        }
        userRepository.getUserByUid(
            uid = uid,
            onSuccess = {
                if (!isAdded) return@getUserByUid
                user = it
                render()
            },
            onFailure = { error ->
                if (!isAdded) return@getUserByUid
                progress.text = UiText.friendlyError(error, "We could not load your profile.")
            }
        )
    }

    private fun render() {
        val current = user ?: return
        content.removeAllViews()
        content.addView(profilePreview(current))
        addSection("Profile appearance")
        content.addView(editableRow(R.drawable.ic_profile_24, "Profile photo", imageValue(current.profileImageUrl)) {
            pendingImageTarget = ImageTarget.PROFILE
            imagePicker.launch("image/*")
        })
        content.addView(editableRow(R.drawable.ic_image_24, "Cover image", imageValue(current.coverImageUrl)) {
            pendingImageTarget = ImageTarget.COVER
            imagePicker.launch("image/*")
        })

        addSection("Basic information")
        content.addView(editableRow(R.drawable.ic_profile_24, "Bio", current.bio.ifBlank { "Add a short bio" }) { showBioEditor(current) })
        content.addView(editableRow(R.drawable.ic_location_24, "Location", current.location.ifBlank { "Choose city" }) { showCityEditor(current) })

        addSection("Dance information")
        content.addView(editableRow(R.drawable.ic_event_24, "Dance level", current.danceLevel.ifBlank { "Choose level" }) { showLevelEditor(current) })
        content.addView(editableRow(R.drawable.ic_discover_24, "Dance styles", current.danceStyles.summary("Choose styles")) { showStylesEditor(current) })
        content.addView(editableRow(R.drawable.ic_add_24, "Skills", current.skills.summary("Add skills")) { showSkillsEditor(current) })

        addSection("Professional information")
        content.addView(editableRow(R.drawable.ic_work_24, "Professional role", current.headline.ifBlank { "Add role or headline" }) {
            showTextEditor("Professional role", "Save Role", current.headline, "headline")
        })
        content.addView(editableRow(R.drawable.ic_work_24, "Experience", current.professionalBackground.ifBlank { current.yearsOfExperience.ifBlank { "Add experience" } }) {
            showExperienceEditor(current)
        })

        addSection("Links and availability")
        content.addView(editableRow(R.drawable.ic_event_24, "Availability", current.availability.ifBlank { "Choose availability" }) { showAvailabilityEditor(current) })
        content.addView(editableRow(R.drawable.ic_share_24, "Social links", socialSummary(current)) { showSocialLinksEditor(current) })
    }

    private fun profilePreview(current: User): View {
        return FrameLayout(requireContext()).apply {
            setBackgroundResource(R.drawable.bg_flow_card)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 176.dp()).apply { bottomMargin = 14.dp() }
            val cover = ImageView(context).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP
                setBackgroundResource(R.drawable.bg_flow_media)
                layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, 104.dp())
                if (current.coverImageUrl.isNotBlank()) Glide.with(this).load(current.coverImageUrl).centerCrop().into(this)
            }
            addView(cover)
            val avatar = ImageView(context).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP
                setBackgroundResource(R.drawable.bg_avatar)
                layoutParams = FrameLayout.LayoutParams(74.dp(), 74.dp(), Gravity.START or Gravity.BOTTOM).apply {
                    leftMargin = 16.dp()
                    bottomMargin = 18.dp()
                }
                if (current.profileImageUrl.isNotBlank()) Glide.with(this).load(current.profileImageUrl).circleCrop().into(this)
            }
            addView(avatar)
            addView(TextView(context).apply {
                text = "${current.firstName} ${current.lastName}".trim().ifBlank { "Dancer" }
                setTextColor(context.getColor(R.color.flow_ink))
                textSize = 18f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.START or Gravity.BOTTOM).apply {
                    leftMargin = 104.dp()
                    bottomMargin = 44.dp()
                }
            })
            addView(TextView(context).apply {
                text = current.headline.ifBlank { current.role.cleanLabel().ifBlank { "THE FLOW profile" } }
                setTextColor(context.getColor(R.color.flow_text_secondary))
                textSize = 13f
                layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.START or Gravity.BOTTOM).apply {
                    leftMargin = 104.dp()
                    bottomMargin = 24.dp()
                }
            })
            addView(iconButton(R.drawable.ic_image_24, "Edit cover") {
                pendingImageTarget = ImageTarget.COVER
                imagePicker.launch("image/*")
            }.apply {
                layoutParams = FrameLayout.LayoutParams(42.dp(), 42.dp(), Gravity.TOP or Gravity.END).apply {
                    topMargin = 10.dp()
                    rightMargin = 10.dp()
                }
            })
            addView(iconButton(R.drawable.ic_profile_24, "Edit profile photo") {
                pendingImageTarget = ImageTarget.PROFILE
                imagePicker.launch("image/*")
            }.apply {
                layoutParams = FrameLayout.LayoutParams(38.dp(), 38.dp(), Gravity.START or Gravity.BOTTOM).apply {
                    leftMargin = 66.dp()
                    bottomMargin = 16.dp()
                }
            })
        }
    }

    private fun addSection(title: String) {
        content.addView(TextView(requireContext()).apply {
            text = title
            setTextColor(context.getColor(R.color.flow_ink))
            textSize = 16f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setPadding(0, 14.dp(), 0, 6.dp())
        })
    }

    private fun editableRow(icon: Int, title: String, value: String, onClick: () -> Unit): View {
        return LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundResource(R.drawable.bg_flow_card)
            setPadding(12.dp(), 12.dp(), 12.dp(), 12.dp())
            isClickable = true
            isFocusable = true
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 9.dp() }
            addView(ImageView(context).apply {
                setImageResource(icon)
                setColorFilter(context.getColor(R.color.flow_brand))
                contentDescription = title
                layoutParams = LinearLayout.LayoutParams(34.dp(), 34.dp()).apply { rightMargin = 10.dp() }
                setPadding(7.dp(), 7.dp(), 7.dp(), 7.dp())
            })
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                addView(TextView(context).apply {
                    text = title
                    setTextColor(context.getColor(R.color.flow_ink))
                    textSize = 14f
                    setTypeface(typeface, android.graphics.Typeface.BOLD)
                })
                addView(TextView(context).apply {
                    text = value
                    maxLines = 2
                    ellipsize = android.text.TextUtils.TruncateAt.END
                    setTextColor(context.getColor(if (value.startsWith("Add") || value.startsWith("Choose")) R.color.flow_text_muted else R.color.flow_text_secondary))
                    textSize = 12f
                    setPadding(0, 3.dp(), 0, 0)
                })
            })
            addView(TextView(context).apply {
                text = ">"
                setTextColor(context.getColor(R.color.flow_brand))
                textSize = 20f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
            })
            setOnClickListener { onClick() }
        }
    }

    private fun showBioEditor(current: User) {
        val maxChars = 220
        val input = formField("Bio", current.bio, 120.dp(), multiLine = true).apply {
            filters = arrayOf(InputFilter.LengthFilter(maxChars))
        }
        val counter = SettingsUi.message(requireContext(), "${current.bio.length.coerceAtMost(maxChars)} / $maxChars")
        input.addTextChangedListener(simpleWatcher { counter.text = "${input.text.length} / $maxChars" })
        showFocusedEditor("Edit Bio", listOf(input, counter), "Save Bio") {
            updateProfile(mapOf("bio" to input.text.toString().trim()))
        }
    }

    private fun showLevelEditor(current: User) {
        showSingleChoiceEditor(
            title = "Dance Level",
            options = listOf("Beginner", "Intermediate", "Advanced", "Professional"),
            currentValue = current.danceLevel,
            positive = "Save Level"
        ) { level ->
            updateProfile(mapOf("danceLevel" to level)) { updatePreference(level = level) }
        }
    }

    private fun showStylesEditor(current: User) {
        showMultiChoiceEditor(
            title = "Dance Styles",
            options = danceStyles,
            selectedValues = current.danceStyles,
            positive = "Apply Styles"
        ) { styles ->
            updateProfile(mapOf("danceStyles" to styles)) { updatePreference(styles = styles) }
        }
    }

    private fun showSkillsEditor(current: User) {
        showMultiChoiceEditor(
            title = "Skills",
            options = skillOptions,
            selectedValues = current.skills,
            positive = "Apply Skills"
        ) { skills ->
            updateProfile(mapOf("skills" to skills))
        }
    }

    private fun showCityEditor(current: User) {
        val input = AutoCompleteTextView(requireContext()).apply {
            setText(current.location, false)
            hint = "City"
            setTextColor(context.getColor(R.color.flow_ink))
            setHintTextColor(context.getColor(R.color.flow_text_muted))
            setBackgroundResource(R.drawable.bg_flow_input)
            setPadding(14.dp(), 0, 14.dp(), 0)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 52.dp())
        }
        CityOptions.configureCitySelector(requireContext(), input)
        showFocusedEditor("Location", listOf(input), "Save Location") {
            val city = CityOptions.normalizeOptionalCity(input.text.toString()) ?: return@showFocusedEditor Toast.makeText(requireContext(), "Choose a city from the list.", Toast.LENGTH_SHORT).show()
            updateProfile(mapOf("location" to city)) { updatePreference(location = city) }
        }
    }

    private fun showExperienceEditor(current: User) {
        val range = if (current.yearsOfExperience.isNotBlank()) current.yearsOfExperience else "Not selected"
        showSingleChoiceEditor("Experience", experienceRanges, range, "Save Experience") { selected ->
            val background = current.professionalBackground
            updateProfile(mapOf("yearsOfExperience" to selected, "professionalBackground" to background))
        }
    }

    private fun showAvailabilityEditor(current: User) {
        showMultiChoiceEditor("Availability", availabilityOptions, commaList(current.availability), "Save Availability") { selected ->
            updateProfile(mapOf("availability" to selected.joinToString(", ")))
        }
    }

    private fun showTextEditor(title: String, positive: String, value: String, fieldName: String) {
        val input = formField(title, value, 54.dp(), multiLine = false)
        showFocusedEditor(title, listOf(input), positive) {
            updateProfile(mapOf(fieldName to input.text.toString().trim()))
        }
    }

    private fun showSocialLinksEditor(current: User) {
        val instagram = formField("Instagram URL", current.instagramUrl, 54.dp()).apply { inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI }
        val tiktok = formField("TikTok URL", current.tiktokUrl, 54.dp()).apply { inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI }
        val youtube = formField("YouTube URL", current.youtubeUrl, 54.dp()).apply { inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI }
        showFocusedEditor("Social Links", listOf(instagram, tiktok, youtube), "Save Links") {
            updateProfile(
                mapOf(
                    "instagramUrl" to instagram.text.toString().trim(),
                    "tiktokUrl" to tiktok.text.toString().trim(),
                    "youtubeUrl" to youtube.text.toString().trim()
                )
            )
        }
    }

    private fun showSingleChoiceEditor(title: String, options: List<String>, currentValue: String, positive: String, onSave: (String) -> Unit) {
        var selected = options.indexOfFirst { it.equals(currentValue, ignoreCase = true) }.coerceAtLeast(0)
        AlertDialog.Builder(requireContext())
            .setTitle(title)
            .setSingleChoiceItems(options.toTypedArray(), selected) { _, which -> selected = which }
            .setNegativeButton(R.string.action_cancel, null)
            .setPositiveButton(positive) { _, _ -> onSave(options[selected]) }
            .create()
            .also(::showConstrainedDialog)
    }

    private fun showMultiChoiceEditor(title: String, options: List<String>, selectedValues: List<String>, positive: String, onSave: (List<String>) -> Unit) {
        val selected = options.map { option -> selectedValues.any { it.equals(option, ignoreCase = true) } }.toBooleanArray()
        AlertDialog.Builder(requireContext())
            .setTitle(title)
            .setMultiChoiceItems(options.toTypedArray(), selected) { _, which, checked -> selected[which] = checked }
            .setNegativeButton(R.string.action_cancel, null)
            .setPositiveButton(positive) { _, _ -> onSave(options.filterIndexed { index, _ -> selected[index] }) }
            .create()
            .also(::showConstrainedDialog)
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

    private fun uploadImage(uri: Uri, target: ImageTarget) {
        val uid = authRepository.getCurrentUserUid() ?: return
        progress.text = if (target == ImageTarget.PROFILE) "Uploading profile photo..." else "Uploading cover image..."
        content.removeAllViews()
        content.addView(progress)
        val success: (String) -> Unit = {
            Toast.makeText(requireContext(), "Image updated", Toast.LENGTH_SHORT).show()
            load()
        }
        val failure: (String) -> Unit = { error ->
            Toast.makeText(requireContext(), UiText.friendlyError(error, "We could not upload the image."), Toast.LENGTH_SHORT).show()
            render()
        }
        if (target == ImageTarget.PROFILE) {
            storageRepository.uploadProfileImage(uid, uri, onSuccess = success, onFailure = failure)
        } else {
            storageRepository.uploadCoverImage(uid, uri, onSuccess = success, onFailure = failure)
        }
    }

    private fun updateProfile(updates: Map<String, Any>, afterSuccess: () -> Unit = {}) {
        val uid = authRepository.getCurrentUserUid() ?: return
        userRepository.updateUserFields(
            uid = uid,
            updates = updates,
            onSuccess = {
                afterSuccess()
                requireContext().getSharedPreferences(PROFILE_EDIT_PREFS, android.content.Context.MODE_PRIVATE)
                    .edit().putBoolean(PROFILE_EDIT_UPDATED, true).apply()
                Toast.makeText(requireContext(), "Profile updated", Toast.LENGTH_SHORT).show()
                load()
            },
            onFailure = { error ->
                Toast.makeText(requireContext(), UiText.friendlyError(error, "We could not update your profile."), Toast.LENGTH_SHORT).show()
            }
        )
    }

    private fun updatePreference(styles: List<String>? = null, level: String? = null, location: String? = null) {
        val current = user ?: return
        userRepository.loadPreferenceSettings(
            onSuccess = { prefs ->
                val newStyles = styles ?: prefs.styles.ifEmpty { current.danceStyles }
                val newLevel = level ?: prefs.level.ifBlank { current.danceLevel }
                val newLocation = location ?: prefs.location.ifBlank { current.location }
                userRepository.updatePreferenceSettings(
                    styles = newStyles,
                    level = newLevel,
                    location = newLocation,
                    preferredStudios = prefs.preferredStudios,
                    preferredTeachers = prefs.preferredTeachers,
                    preferredDancers = prefs.preferredDancers,
                    onSuccess = {
                        DiscoveryRepository.hydratePreferences(newStyles, newLevel, newLocation, prefs.preferredStudios, prefs.preferredTeachers, prefs.preferredDancers)
                    },
                    onFailure = {}
                )
            },
            onFailure = {}
        )
    }

    private fun formField(hint: String, value: String, height: Int, multiLine: Boolean = false): EditText {
        return EditText(requireContext()).apply {
            this.hint = hint
            setText(value)
            setTextColor(context.getColor(R.color.flow_ink))
            setHintTextColor(context.getColor(R.color.flow_text_muted))
            setBackgroundResource(R.drawable.bg_flow_input)
            minHeight = height
            gravity = if (multiLine) Gravity.TOP else Gravity.CENTER_VERTICAL
            inputType = if (multiLine) InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE else InputType.TYPE_CLASS_TEXT
            setPadding(14.dp(), if (multiLine) 12.dp() else 0, 14.dp(), 0)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 10.dp() }
        }
    }

    private fun iconButton(icon: Int, description: String, onClick: () -> Unit): ImageButton {
        return ImageButton(requireContext()).apply {
            setImageResource(icon)
            setColorFilter(context.getColor(R.color.flow_brand))
            setBackgroundResource(R.drawable.bg_flow_icon_button)
            contentDescription = description
            setPadding(9.dp(), 9.dp(), 9.dp(), 9.dp())
            setOnClickListener { onClick() }
        }
    }

    private fun showConstrainedDialog(dialog: AlertDialog) {
        dialog.setOnShowListener { constrainDialog(dialog.window) }
        dialog.show()
    }

    private fun constrainDialog(window: Window?) {
        window ?: return
        val metrics = resources.displayMetrics
        val maxWidth = if (metrics.widthPixels / metrics.density >= 600) 520.dp() else (metrics.widthPixels - 32.dp())
        window.setLayout(maxWidth, WindowManager.LayoutParams.WRAP_CONTENT)
        window.setGravity(Gravity.CENTER)
    }

    private fun imageValue(value: String): String = if (value.isBlank()) "Add image" else "Image selected"
    private fun socialSummary(current: User): String =
        listOf(current.instagramUrl, current.tiktokUrl, current.youtubeUrl).count { it.isNotBlank() }.let { if (it == 0) "Add links" else "$it links" }

    private fun List<String>.summary(empty: String): String = if (isEmpty()) empty else take(3).joinToString(", ") + if (size > 3) " +${size - 3}" else ""
    private fun commaList(value: String): List<String> = value.split(",").map { it.trim() }.filter { it.isNotBlank() }
    private fun simpleWatcher(after: () -> Unit) = object : android.text.TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, afterCount: Int) = Unit
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
        override fun afterTextChanged(s: android.text.Editable?) = after()
    }

    private enum class ImageTarget { PROFILE, COVER }

    companion object {
        const val PROFILE_EDIT_PREFS = "profile_edit_state"
        const val PROFILE_EDIT_UPDATED = "profile_updated"
        private val danceStyles = listOf("Hip Hop", "Heels", "Salsa", "Contemporary", "Afro", "Ballet", "Jazz", "Bachata")
        private val skillOptions = listOf("Choreography", "Teaching", "Freestyle", "Performance", "Improvisation", "Musicality", "Partner work", "Audition prep")
        private val availabilityOptions = listOf("Weekday mornings", "Weekday afternoons", "Weekday evenings", "Friday", "Saturday", "Online sessions", "Workshops")
        private val experienceRanges = listOf("0-1 years", "1-3 years", "3-5 years", "5-10 years", "10+ years")
    }
}

private fun String.cleanLabel(): String {
    return trim().replace('_', ' ').replace('-', ' ')
}
