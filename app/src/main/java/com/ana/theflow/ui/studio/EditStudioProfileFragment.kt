package com.ana.theflow.ui.studio

import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AutoCompleteTextView
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import com.ana.theflow.R
import com.ana.theflow.data.model.studio.Studio
import com.ana.theflow.data.repository.StorageRepository
import com.ana.theflow.data.repository.StudioRepository
import com.ana.theflow.ui.common.UiText
import com.ana.theflow.ui.settings.SettingsUi
import com.ana.theflow.ui.settings.dp
import com.ana.theflow.utilities.CityOptions

// Lets a manager edit their studio's business profile. Only reachable from StudioProfileFragment
// for a studio the signed-in user actually manages - Firestore rules enforce this independently.
class EditStudioProfileFragment : Fragment() {

    private val studioRepository = StudioRepository()
    private val storageRepository = StorageRepository()
    private lateinit var content: LinearLayout
    private lateinit var progress: TextView
    private var studio: Studio? = null
    private var selectedStyles: MutableSet<String> = mutableSetOf()

    private lateinit var nameField: EditText
    private lateinit var cityField: AutoCompleteTextView
    private lateinit var addressField: EditText
    private lateinit var bioField: EditText
    private lateinit var websiteField: EditText
    private lateinit var phoneField: EditText
    private lateinit var emailField: EditText

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

        content.addView(android.widget.Button(requireContext()).apply {
            text = "Save changes"
            isAllCaps = false
            setTextColor(context.getColor(R.color.flow_surface))
            setBackgroundResource(R.drawable.bg_flow_button_primary)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 52.dp()).apply { topMargin = 12.dp() }
            setOnClickListener { save() }
        })
    }

    private fun save() {
        val city = CityOptions.normalizeOptionalCity(cityField.text.toString()).orEmpty()
        studioRepository.updateStudioProfile(
            studioId = studioId,
            updates = mapOf(
                "displayName" to nameField.text.toString().trim(),
                "searchName" to nameField.text.toString().trim().lowercase(),
                "city" to city,
                "location" to city,
                "address" to addressField.text.toString().trim(),
                "bio" to bioField.text.toString().trim(),
                "danceStyles" to selectedStyles.toList(),
                "websiteUrl" to websiteField.text.toString().trim(),
                "contactPhone" to phoneField.text.toString().trim(),
                "contactEmail" to emailField.text.toString().trim()
            ),
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
