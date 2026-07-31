package com.ana.theflow.ui.creation

import android.widget.EditText
import android.widget.AutoCompleteTextView
import android.widget.LinearLayout
import android.widget.Toast
import android.view.View
import androidx.appcompat.app.AlertDialog
import com.ana.theflow.utilities.CityOptions

class CollaborationCreationFragment : BaseCreationFragment() {

    override val titleText = "Create collaboration"
    override val publishButtonText = "Create"
    override val publishSuccessMessage = "Collaboration created"

    private lateinit var description: EditText
    private lateinit var lookingFor: EditText
    private lateinit var danceStyle: EditText
    private lateinit var location: EditText
    private lateinit var dates: EditText
    private lateinit var paymentStatus: EditText
    private lateinit var applicationOptions: EditText
    private lateinit var contactOptions: EditText
    private lateinit var styleContainer: LinearLayout
    private lateinit var logisticsContainer: LinearLayout
    private lateinit var mediaContainer: LinearLayout
    private lateinit var contactContainer: LinearLayout

    override fun buildFields(parent: LinearLayout) {
        description = field("Describe the collaboration", minHeightDp = 140, multiLine = true)
        parent.addView(description)

        lookingFor = pickerField("Who are you looking for?", listOf("Practice partner", "Dancer", "Teacher", "Choreographer", "Studio", "Videographer", "Project partner"))
        parent.addView(lookingFor)

        parent.addView(optionButton("Dance style and experience") {
            toggle(styleContainer)
        })
        styleContainer = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            danceStyle = pickerField("Dance style", listOf("Hip Hop", "Heels", "Salsa", "Contemporary", "Afro", "Ballet", "Jazz", "Bachata"))
            addView(danceStyle)
        }
        parent.addView(styleContainer)

        parent.addView(optionButton("Logistics") {
            toggle(logisticsContainer)
        })
        logisticsContainer = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
        }
        location = AutoCompleteTextView(requireContext()).apply {
            hint = "City"
            setTextColor(context.getColor(com.ana.theflow.R.color.flow_ink))
            setHintTextColor(context.getColor(com.ana.theflow.R.color.flow_text_muted))
            setBackgroundResource(com.ana.theflow.R.drawable.bg_flow_input)
            minHeight = 52.dp()
            gravity = android.view.Gravity.CENTER_VERTICAL
            setPadding(14.dp(), 0, 14.dp(), 0)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                topMargin = 10.dp()
            }
        }
        CityOptions.configureCitySelector(requireContext(), location as AutoCompleteTextView)
        dates = field("Relevant dates")
        paymentStatus = pickerField("Payment status", listOf("Unpaid", "Paid", "Revenue share", "Open to discuss"))
        logisticsContainer.addView(location)
        logisticsContainer.addView(dates)
        logisticsContainer.addView(paymentStatus)
        parent.addView(logisticsContainer)

        parent.addView(optionButton("Supporting media") {
            toggle(mediaContainer)
        })
        mediaContainer = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            visibility = View.GONE
            addView(optionButton("Add photo") { openMediaPicker() })
            addView(optionButton("Add video") { openMediaPicker() })
        }
        parent.addView(mediaContainer)

        parent.addView(optionButton("Applications and contact") {
            toggle(contactContainer)
        })
        contactContainer = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            applicationOptions = field("Application options")
            contactOptions = field("Contact options")
            addView(applicationOptions)
            addView(contactOptions)
        }
        parent.addView(contactContainer)
    }

    override fun buildPayload(): CreatePayload? {
        val body = description.text.toString().trim()
        val target = lookingFor.text.toString().trim()
        if (body.isBlank() && target.isBlank()) {
            Toast.makeText(requireContext(), "Describe the collaboration or who you need", Toast.LENGTH_SHORT).show()
            return null
        }
        val details = joinedDetails(
            "Applications" to applicationOptions.text.toString(),
            "Contact" to contactOptions.text.toString()
        )
        return CreatePayload(
            text = listOf(target, body).filter { it.isNotBlank() }.joinToString("\n\n"),
            postType = POST_TYPE_COLLABORATION,
            collaborationLookingFor = target,
            collaborationStyle = danceStyle.text.toString().trim(),
            collaborationLocation = location.text.toString().trim(),
            collaborationDate = dates.text.toString().trim(),
            collaborationPaid = paymentStatus.text.toString().trim(),
            collaborationDescription = listOf(body, details).filter { it.isNotBlank() }.joinToString("\n\n")
        )
    }

    private fun toggle(view: View) {
        view.visibility = if (view.visibility == View.VISIBLE) View.GONE else View.VISIBLE
    }

    private fun pickerField(hint: String, options: List<String>): EditText {
        return field(hint).apply {
            isFocusable = false
            isClickable = true
            setOnClickListener {
                AlertDialog.Builder(requireContext())
                    .setTitle(hint)
                    .setItems(options.toTypedArray()) { _, which -> setText(options[which]) }
                    .setNegativeButton(com.ana.theflow.R.string.action_cancel, null)
                    .show()
            }
        }
    }
}

private fun Int.dp(): Int {
    return (this * android.content.res.Resources.getSystem().displayMetrics.density).toInt()
}
