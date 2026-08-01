package com.ana.theflow.ui.studio

import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AutoCompleteTextView
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import com.ana.theflow.R
import com.ana.theflow.data.model.studio.Studio
import com.ana.theflow.data.repository.StorageRepository
import com.ana.theflow.data.repository.StudioRepository
import com.ana.theflow.data.repository.UserRepository
import com.ana.theflow.ui.common.UiText
import com.ana.theflow.ui.settings.SettingsUi
import com.ana.theflow.ui.settings.dp
import com.ana.theflow.utilities.CityOptions

// Lets a manager edit their studio's business profile. Only reachable from StudioProfileFragment
// for a studio the signed-in user actually manages - Firestore rules enforce this independently.
class EditStudioProfileFragment : Fragment() {

    private val studioRepository = StudioRepository()
    private val storageRepository = StorageRepository()
    private val userRepository = UserRepository()
    private lateinit var content: LinearLayout
    private lateinit var progress: TextView
    private var studio: Studio? = null
    private var selectedStyles: MutableSet<String> = mutableSetOf()
    private var currentTeachers: MutableList<Map<String, Any>> = mutableListOf()

    private lateinit var nameField: EditText
    private lateinit var cityField: AutoCompleteTextView
    private lateinit var addressField: EditText
    private lateinit var bioField: EditText
    private lateinit var websiteField: EditText
    private lateinit var phoneField: EditText
    private lateinit var emailField: EditText
    private lateinit var hoursField: EditText
    private lateinit var instagramField: EditText
    private lateinit var tiktokField: EditText
    private lateinit var youtubeField: EditText
    private lateinit var teachersContainer: LinearLayout

    private val studioId: String get() = arguments?.getString(ARG_STUDIO_ID).orEmpty()

    private val logoPicker = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) uploadLogo(uri)
    }
    private val coverPicker = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) uploadCover(uri)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val root = SettingsUi.screen(this, "Edit Business Profile")
        val scroll = SettingsUi.contentScroll(requireContext())
        content = SettingsUi.contentColumn(requireContext())
        progress = SettingsUi.message(requireContext(), "Loading studio...")
        content.addView(progress)
        scroll.addView(content)
        root.addView(scroll)
        load()
        return root
    }

    private fun load() {
        studioRepository.loadStudio(
            studioId = studioId,
            onSuccess = { loaded -> if (isAdded) { studio = loaded; render(loaded) } },
            onFailure = { error -> if (isAdded) progress.text = UiText.friendlyError(error, "We could not load this studio.") }
        )
    }

    private fun render(loaded: Studio) {
        content.removeAllViews()
        selectedStyles = loaded.danceStyles.toMutableSet()

        content.addView(SettingsUi.row(requireContext(), "Logo", "Tap to change", onClick = { logoPicker.launch("image/*") }))
        content.addView(SettingsUi.row(requireContext(), "Cover image", "Tap to change", onClick = { coverPicker.launch("image/*") }))

        nameField = field("Studio name", loaded.displayName)
        content.addView(nameField)
        cityField = cityField(loaded.city)
        content.addView(cityField)
        addressField = field("Address", loaded.address)
        content.addView(addressField)
        bioField = field("Bio", loaded.bio, minHeightDp = 100, multiLine = true)
        content.addView(bioField)
        content.addView(styleRow())
        websiteField = field("Website", loaded.websiteUrl)
        content.addView(websiteField)
        phoneField = field("Contact phone", loaded.contactPhone)
        content.addView(phoneField)
        emailField = field("Contact email", loaded.contactEmail)
        content.addView(emailField)
        hoursField = field("Opening hours", loaded.openingHours, minHeightDp = 72, multiLine = true)
        content.addView(hoursField)

        content.addView(sectionLabel("Social links"))
        instagramField = field("Instagram", loaded.socialLinks[Studio.SOCIAL_INSTAGRAM].orEmpty())
        content.addView(instagramField)
        tiktokField = field("TikTok", loaded.socialLinks[Studio.SOCIAL_TIKTOK].orEmpty())
        content.addView(tiktokField)
        youtubeField = field("YouTube", loaded.socialLinks[Studio.SOCIAL_YOUTUBE].orEmpty())
        content.addView(youtubeField)

        content.addView(android.widget.Button(requireContext()).apply {
            text = "Save changes"
            isAllCaps = false
            setTextColor(context.getColor(R.color.flow_surface))
            setBackgroundResource(R.drawable.bg_flow_button_primary)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 52.dp()).apply { topMargin = 12.dp() }
            setOnClickListener { save() }
        })

        content.addView(sectionLabel("Teachers"))
        teachersContainer = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                topMargin = 4.dp()
            }
        }
        content.addView(teachersContainer)
        renderTeachers(loaded.teacherProfiles)
        content.addView(Button(requireContext()).apply {
            text = "Add Teacher"
            isAllCaps = false
            setTextColor(context.getColor(R.color.flow_brand))
            setBackgroundResource(R.drawable.bg_flow_button_secondary)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 46.dp()).apply { topMargin = 6.dp() }
            setOnClickListener { showAddTeacherDialog() }
        })
    }

    private fun sectionLabel(title: String): View {
        return TextView(requireContext()).apply {
            text = title
            setTextColor(context.getColor(R.color.flow_ink))
            textSize = 15f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setPadding(0, 18.dp(), 0, 4.dp())
        }
    }

    private fun renderTeachers(profiles: List<Map<String, Any>>) {
        currentTeachers = profiles.toMutableList()
        teachersContainer.removeAllViews()
        if (currentTeachers.isEmpty()) {
            teachersContainer.addView(SettingsUi.message(requireContext(), "No teachers listed yet."))
            return
        }
        currentTeachers.forEach { profile -> teachersContainer.addView(teacherRow(profile)) }
    }

    private fun teacherRow(profile: Map<String, Any>): View {
        val context = requireContext()
        val name = profile[Studio.TEACHER_KEY_NAME] as? String ?: "Teacher"
        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundResource(R.drawable.bg_flow_card)
            setPadding(14.dp(), 12.dp(), 14.dp(), 12.dp())
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = 8.dp()
            }
            addView(TextView(context).apply {
                text = name
                setTextColor(context.getColor(R.color.flow_ink))
                textSize = 14f
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            })
            addView(Button(context).apply {
                text = "Remove"
                isAllCaps = false
                minWidth = 0
                setTextColor(context.getColor(R.color.flow_error))
                setBackgroundResource(R.drawable.bg_flow_button_secondary)
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, 36.dp())
                setOnClickListener {
                    persistTeachers(currentTeachers.filterNot { it[Studio.TEACHER_KEY_UID] == profile[Studio.TEACHER_KEY_UID] })
                }
            })
        }
    }

    // Persists the whole roster in one write (matches StudioRepository.updateStudioTeachers'
    // signature, which replaces both teacherUids and teacherProfiles together) and re-renders
    // immediately - each add/remove commits right away rather than waiting for "Save changes",
    // consistent with how AdminUserPermissionsFragment's per-studio remove button behaves.
    private fun persistTeachers(updated: List<Map<String, Any>>) {
        val uids = updated.mapNotNull { it[Studio.TEACHER_KEY_UID] as? String }.filter { it.isNotBlank() }
        studioRepository.updateStudioTeachers(
            studioId = studioId,
            teacherUids = uids,
            profiles = updated,
            onSuccess = {
                if (!isAdded) return@updateStudioTeachers
                renderTeachers(updated)
                Toast.makeText(requireContext(), "Teachers updated", Toast.LENGTH_SHORT).show()
            },
            onFailure = { error ->
                if (!isAdded) return@updateStudioTeachers
                Toast.makeText(requireContext(), UiText.friendlyError(error, "We could not update teachers."), Toast.LENGTH_SHORT).show()
            }
        )
    }

    // Searchable user picker for adding a teacher, mirroring AdminUserPermissionsFragment's
    // "Assign to Studio" studio picker (same live-filter-as-you-type pattern, applied to users
    // here instead of studios).
    private fun showAddTeacherDialog() {
        val context = requireContext()
        lateinit var dialog: AlertDialog
        val resultsContainer = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }

        fun runSearch(query: String) {
            if (query.isBlank()) {
                resultsContainer.removeAllViews()
                return
            }
            resultsContainer.removeAllViews()
            userRepository.searchUsers(
                query = query,
                dancersOnly = false,
                onSuccess = { users ->
                    if (!isAdded) return@searchUsers
                    resultsContainer.removeAllViews()
                    val alreadyAdded = currentTeachers.mapNotNull { it[Studio.TEACHER_KEY_UID] as? String }.toSet()
                    val filtered = users.filterNot { it.uid in alreadyAdded }
                    if (filtered.isEmpty()) {
                        resultsContainer.addView(SettingsUi.message(context, "No users found."))
                        return@searchUsers
                    }
                    filtered.take(20).forEach { user ->
                        val name = "${user.firstName} ${user.lastName}".trim().ifBlank { "Dancer" }
                        resultsContainer.addView(
                            SettingsUi.row(
                                context = context,
                                title = name,
                                description = user.headline.ifBlank { "Tap to add as a teacher" },
                                onClick = {
                                    dialog.dismiss()
                                    val profile = mapOf(
                                        Studio.TEACHER_KEY_UID to user.uid,
                                        Studio.TEACHER_KEY_NAME to name,
                                        Studio.TEACHER_KEY_HEADLINE to user.headline,
                                        Studio.TEACHER_KEY_PHOTO to user.profileImageUrl
                                    )
                                    persistTeachers(currentTeachers + profile)
                                }
                            )
                        )
                    }
                },
                onFailure = { error ->
                    if (!isAdded) return@searchUsers
                    resultsContainer.removeAllViews()
                    resultsContainer.addView(SettingsUi.message(context, error))
                }
            )
        }

        val searchInput = EditText(context).apply {
            hint = "Search by name"
            setTextColor(context.getColor(R.color.flow_ink))
            setHintTextColor(context.getColor(R.color.flow_text_muted))
            setBackgroundResource(R.drawable.bg_flow_input)
            minHeight = 50.dp()
            setPadding(14.dp(), 0, 14.dp(), 0)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
                override fun afterTextChanged(s: Editable?) { runSearch(s?.toString().orEmpty()) }
            })
        }
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24.dp(), 12.dp(), 24.dp(), 4.dp())
            addView(searchInput)
            addView(resultsContainer)
        }

        dialog = AlertDialog.Builder(context)
            .setTitle("Add Teacher")
            .setView(ScrollView(context).apply { addView(container) })
            .setNegativeButton("Cancel", null)
            .create()
        dialog.show()
    }

    private fun save() {
        val city = CityOptions.normalizeOptionalCity(cityField.text.toString()).orEmpty()
        val updates = mutableMapOf<String, Any>(
            "displayName" to nameField.text.toString().trim(),
            "searchName" to nameField.text.toString().trim().lowercase(),
            "city" to city,
            "location" to city,
            "address" to addressField.text.toString().trim(),
            "bio" to bioField.text.toString().trim(),
            "danceStyles" to selectedStyles.toList(),
            "websiteUrl" to websiteField.text.toString().trim(),
            "contactPhone" to phoneField.text.toString().trim(),
            "contactEmail" to emailField.text.toString().trim(),
            "openingHours" to hoursField.text.toString().trim(),
            "socialLinks" to mapOf(
                Studio.SOCIAL_INSTAGRAM to instagramField.text.toString().trim(),
                Studio.SOCIAL_TIKTOK to tiktokField.text.toString().trim(),
                Studio.SOCIAL_YOUTUBE to youtubeField.text.toString().trim()
            ).filterValues { it.isNotBlank() }
        )
        // Studios created before the app captured coordinates (or created without a Google-sourced
        // address) can be stuck with no map pin at all - backfill from the city here so saving the
        // profile once is enough to make an old studio visible on the map again.
        if (studio?.latitude == null || studio?.longitude == null) {
            CityOptions.cityFor(city)?.let { cityOption ->
                updates["latitude"] = cityOption.latitude
                updates["longitude"] = cityOption.longitude
            }
        }
        studioRepository.updateStudioProfile(
            studioId = studioId,
            updates = updates,
            onSuccess = {
                if (!isAdded) return@updateStudioProfile
                Toast.makeText(requireContext(), "Studio profile updated", Toast.LENGTH_SHORT).show()
                parentFragmentManager.popBackStack()
            },
            onFailure = { error ->
                if (!isAdded) return@updateStudioProfile
                Toast.makeText(requireContext(), UiText.friendlyError(error, "We could not update this studio."), Toast.LENGTH_SHORT).show()
            }
        )
    }

    private fun uploadLogo(uri: Uri) {
        storageRepository.uploadStudioLogo(
            studioId = studioId,
            imageUri = uri,
            onSuccess = { if (isAdded) Toast.makeText(requireContext(), "Logo updated", Toast.LENGTH_SHORT).show() },
            onFailure = { error -> if (isAdded) Toast.makeText(requireContext(), UiText.friendlyError(error, "We could not update the logo."), Toast.LENGTH_SHORT).show() }
        )
    }

    private fun uploadCover(uri: Uri) {
        storageRepository.uploadStudioCover(
            studioId = studioId,
            imageUri = uri,
            onSuccess = { if (isAdded) Toast.makeText(requireContext(), "Cover image updated", Toast.LENGTH_SHORT).show() },
            onFailure = { error -> if (isAdded) Toast.makeText(requireContext(), UiText.friendlyError(error, "We could not update the cover image."), Toast.LENGTH_SHORT).show() }
        )
    }

    private fun styleRow(): View {
        val summary = TextView(requireContext()).apply {
            text = selectedStyles.ifEmpty { setOf("Choose styles") }.joinToString(", ")
            setTextColor(context.getColor(R.color.flow_text_secondary))
            textSize = 12f
            setPadding(0, 3.dp(), 0, 0)
        }
        return LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(R.drawable.bg_flow_card)
            setPadding(14.dp(), 12.dp(), 14.dp(), 12.dp())
            isClickable = true
            isFocusable = true
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 10.dp() }
            addView(TextView(context).apply {
                text = "Dance styles"
                setTextColor(context.getColor(R.color.flow_ink))
                textSize = 14f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
            })
            addView(summary)
            setOnClickListener {
                val selected = danceStyles.map { selectedStyles.contains(it) }.toBooleanArray()
                AlertDialog.Builder(requireContext())
                    .setTitle("Dance styles")
                    .setMultiChoiceItems(danceStyles.toTypedArray(), selected) { _, which, checked ->
                        if (checked) selectedStyles.add(danceStyles[which]) else selectedStyles.remove(danceStyles[which])
                    }
                    .setPositiveButton("Done") { _, _ -> summary.text = selectedStyles.ifEmpty { setOf("Choose styles") }.joinToString(", ") }
                    .show()
            }
        }
    }

    private fun cityField(value: String): AutoCompleteTextView {
        return AutoCompleteTextView(requireContext()).apply {
            hint = "City"
            setText(value, false)
            setTextColor(context.getColor(R.color.flow_ink))
            setHintTextColor(context.getColor(R.color.flow_text_muted))
            setBackgroundResource(R.drawable.bg_flow_input)
            minHeight = 52.dp()
            setPadding(14.dp(), 0, 14.dp(), 0)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 10.dp() }
            CityOptions.configureCitySelector(requireContext(), this)
        }
    }

    private fun field(hint: String, value: String, minHeightDp: Int = 52, multiLine: Boolean = false): EditText {
        return EditText(requireContext()).apply {
            this.hint = hint
            setText(value)
            setTextColor(context.getColor(R.color.flow_ink))
            setHintTextColor(context.getColor(R.color.flow_text_muted))
            setBackgroundResource(R.drawable.bg_flow_input)
            minHeight = minHeightDp.dp()
            gravity = if (multiLine) android.view.Gravity.TOP else android.view.Gravity.CENTER_VERTICAL
            inputType = if (multiLine) {
                android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
            } else {
                android.text.InputType.TYPE_CLASS_TEXT
            }
            setPadding(14.dp(), if (multiLine) 12.dp() else 0, 14.dp(), 0)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 10.dp() }
        }
    }

    companion object {
        private const val ARG_STUDIO_ID = "ARG_STUDIO_ID"
        private val danceStyles = listOf("Hip Hop", "Heels", "Salsa", "Contemporary", "Afro", "Ballet", "Jazz", "Bachata")

        fun newInstance(studioId: String): EditStudioProfileFragment {
            return EditStudioProfileFragment().apply {
                arguments = Bundle().apply { putString(ARG_STUDIO_ID, studioId) }
            }
        }
    }
}
