package com.ana.theflow.ui.studio

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AutoCompleteTextView
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import com.ana.theflow.R
import com.ana.theflow.data.model.studio.StudioRequest
import com.ana.theflow.data.repository.StudioRequestRepository
import com.ana.theflow.ui.common.UiText
import com.ana.theflow.ui.settings.SettingsUi
import com.ana.theflow.ui.settings.dp
import com.ana.theflow.utilities.CityOptions

// Lets any dancer request a brand-new studio business account or claim an existing one.
// Both flows land in the same admin-reviewed queue - nothing here grants permission directly.
class StudioRequestFragment : Fragment() {

    private val studioRequestRepository = StudioRequestRepository()
    private lateinit var content: LinearLayout
    private lateinit var pendingList: LinearLayout
    private lateinit var submitButton: Button
    private lateinit var messageLabel: TextView

    private lateinit var nameField: EditText
    private lateinit var cityField: AutoCompleteTextView
    private lateinit var addressField: EditText
    private lateinit var bioField: EditText
    private lateinit var websiteField: EditText
    private lateinit var phoneField: EditText
    private lateinit var emailField: EditText
    private lateinit var justificationField: EditText
    private lateinit var verificationField: EditText
    private var selectedStyles: MutableSet<String> = mutableSetOf()

    private val mode: String get() = arguments?.getString(ARG_MODE).orEmpty().ifBlank { MODE_CREATE }
    private val isClaim: Boolean get() = mode == MODE_CLAIM
    private val claimStudioId: String get() = arguments?.getString(ARG_STUDIO_ID).orEmpty()
    private val claimStudioName: String get() = arguments?.getString(ARG_STUDIO_NAME).orEmpty()
    private val claimGooglePlaceId: String get() = arguments?.getString(ARG_GOOGLE_PLACE_ID).orEmpty()
    private val claimAddress: String get() = arguments?.getString(ARG_ADDRESS).orEmpty()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val title = if (isClaim) "Claim a studio" else "Create a studio"
        val root = SettingsUi.screen(this, title)
        val scroll = SettingsUi.contentScroll(requireContext())
        content = SettingsUi.contentColumn(requireContext())
        scroll.addView(content)
        root.addView(scroll)
        render()
        return root
    }

    private fun render() {
        content.removeAllViews()
        content.addView(
            SettingsUi.message(
                requireContext(),
                if (isClaim) {
                    "Tell us why $claimStudioName is yours. An admin reviews every request before manager access is granted."
                } else {
                    "Requests are reviewed by an admin before the studio page goes live and you become its manager."
                }
            )
        )

        if (isClaim) {
            content.addView(readOnlyRow("Studio", claimStudioName))
            if (claimAddress.isNotBlank()) content.addView(readOnlyRow("Address", claimAddress))
        } else {
            nameField = field("Studio name")
            content.addView(nameField)
            cityField = cityField()
            content.addView(cityField)
            addressField = field("Address")
            content.addView(addressField)
            bioField = field("Short bio", minHeightDp = 90, multiLine = true)
            content.addView(bioField)
            content.addView(styleRow())
            websiteField = field("Website (optional)")
            content.addView(websiteField)
            phoneField = field("Contact phone (optional)")
            content.addView(phoneField)
            emailField = field("Contact email (optional)")
            content.addView(emailField)
        }

        justificationField = field(
            if (isClaim) "Why is this studio yours?" else "Why are you requesting this studio?",
            minHeightDp = 90,
            multiLine = true
        )
        content.addView(justificationField)

        if (isClaim) {
            verificationField = field("Verification details: phone, website, Instagram...", minHeightDp = 90, multiLine = true)
            content.addView(verificationField)
        }

        messageLabel = SettingsUi.message(requireContext(), "")
        messageLabel.visibility = View.GONE
        content.addView(messageLabel)

        submitButton = Button(requireContext()).apply {
            text = if (isClaim) "Submit claim" else "Submit request"
            isAllCaps = false
            setTextColor(context.getColor(R.color.flow_surface))
            setBackgroundResource(R.drawable.bg_flow_button_primary)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 52.dp()).apply {
                topMargin = 8.dp()
            }
            setOnClickListener { submit() }
        }
        content.addView(submitButton)

        content.addView(TextView(requireContext()).apply {
            text = "Your requests"
            setTextColor(context.getColor(R.color.flow_ink))
            textSize = 16f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setPadding(0, 20.dp(), 0, 6.dp())
        })
        pendingList = LinearLayout(requireContext()).apply { orientation = LinearLayout.VERTICAL }
        content.addView(pendingList)
        loadMyRequests()
    }

    private fun submit() {
        submitButton.isEnabled = false
        messageLabel.visibility = View.GONE
        if (isClaim) {
            studioRequestRepository.submitClaimRequest(
                studioId = claimStudioId,
                studioName = claimStudioName,
                googlePlaceId = claimGooglePlaceId,
                address = claimAddress,
                justification = justificationField.text.toString(),
                verificationDetails = verificationField.text.toString(),
                onSuccess = { onSubmitSuccess("Claim submitted for review.") },
                onFailure = ::onSubmitFailure
            )
        } else {
            val city = CityOptions.normalizeOptionalCity(cityField.text.toString())
            if (nameField.text.toString().isBlank() || city == null) {
                submitButton.isEnabled = true
                messageLabel.text = "Add a studio name and choose a city from the list."
                messageLabel.visibility = View.VISIBLE
                return
            }
            studioRequestRepository.submitCreateRequest(
                displayName = nameField.text.toString(),
                city = city,
                address = addressField.text.toString(),
                bio = bioField.text.toString(),
                danceStyles = selectedStyles.toList(),
                websiteUrl = websiteField.text.toString(),
                contactPhone = phoneField.text.toString(),
                contactEmail = emailField.text.toString(),
                socialLinks = emptyMap(),
                justification = justificationField.text.toString(),
                onSuccess = { onSubmitSuccess("Studio request submitted for review.") },
                onFailure = ::onSubmitFailure
            )
        }
    }

    private fun onSubmitSuccess(message: String) {
        if (!isAdded) return
        submitButton.isEnabled = false
        submitButton.text = "Submitted"
        Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show()
        loadMyRequests()
    }

    private fun onSubmitFailure(error: String) {
        if (!isAdded) return
        submitButton.isEnabled = true
        messageLabel.text = UiText.friendlyError(error, "We could not submit this request.")
        messageLabel.visibility = View.VISIBLE
    }

    private fun loadMyRequests() {
        studioRequestRepository.loadMyRequests(
            onSuccess = { requests ->
                if (!isAdded) return@loadMyRequests
                pendingList.removeAllViews()
                if (requests.isEmpty()) {
                    pendingList.addView(SettingsUi.message(requireContext(), "No requests yet."))
                    return@loadMyRequests
                }
                requests.forEach { request -> pendingList.addView(requestRow(request)) }
            },
            onFailure = {}
        )
    }

    private fun requestRow(request: StudioRequest): View {
        val label = request.draftDisplayName.ifBlank { request.studioName }.ifBlank { "Studio request" }
        val typeLabel = if (request.type == StudioRequest.TYPE_CREATE) "Create" else "Claim"
        return SettingsUi.row(
            context = requireContext(),
            title = label,
            description = "$typeLabel request",
            value = request.status.lowercase().replaceFirstChar { it.uppercase() },
            enabled = false
        )
    }

    private fun readOnlyRow(label: String, value: String): View {
        return LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(R.drawable.bg_flow_card)
            setPadding(14.dp(), 12.dp(), 14.dp(), 12.dp())
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = 10.dp()
            }
            addView(TextView(context).apply {
                text = label
                setTextColor(context.getColor(R.color.flow_text_secondary))
                textSize = 12f
            })
            addView(TextView(context).apply {
                text = value
                setTextColor(context.getColor(R.color.flow_ink))
                textSize = 15f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                setPadding(0, 3.dp(), 0, 0)
            })
        }
    }

    private fun styleRow(): View {
        val summary = TextView(requireContext()).apply {
            text = selectedStyles.summary()
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
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = 10.dp()
            }
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
                    .setPositiveButton("Done") { _, _ -> summary.text = selectedStyles.summary() }
                    .show()
            }
        }
    }

    private fun Set<String>.summary(): String {
        return if (isEmpty()) "Choose styles" else joinToString(", ")
    }

    private fun cityField(): AutoCompleteTextView {
        return AutoCompleteTextView(requireContext()).apply {
            hint = "City"
            setTextColor(context.getColor(R.color.flow_ink))
            setHintTextColor(context.getColor(R.color.flow_text_muted))
            setBackgroundResource(R.drawable.bg_flow_input)
            minHeight = 52.dp()
            setPadding(14.dp(), 0, 14.dp(), 0)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = 10.dp()
            }
            CityOptions.configureCitySelector(requireContext(), this)
        }
    }

    private fun field(hint: String, minHeightDp: Int = 52, multiLine: Boolean = false): EditText {
        return EditText(requireContext()).apply {
            this.hint = hint
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
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = 10.dp()
            }
        }
    }

    companion object {
        private const val ARG_MODE = "ARG_MODE"
        private const val ARG_STUDIO_ID = "ARG_STUDIO_ID"
        private const val ARG_STUDIO_NAME = "ARG_STUDIO_NAME"
        private const val ARG_GOOGLE_PLACE_ID = "ARG_GOOGLE_PLACE_ID"
        private const val ARG_ADDRESS = "ARG_ADDRESS"
        const val MODE_CREATE = "create"
        const val MODE_CLAIM = "claim"

        private val danceStyles = listOf("Hip Hop", "Heels", "Salsa", "Contemporary", "Afro", "Ballet", "Jazz", "Bachata")

        fun newInstance(
            mode: String,
            studioId: String = "",
            studioName: String = "",
            googlePlaceId: String = "",
            address: String = ""
        ): StudioRequestFragment {
            return StudioRequestFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_MODE, mode)
                    putString(ARG_STUDIO_ID, studioId)
                    putString(ARG_STUDIO_NAME, studioName)
                    putString(ARG_GOOGLE_PLACE_ID, googlePlaceId)
                    putString(ARG_ADDRESS, address)
                }
            }
        }
    }
}
