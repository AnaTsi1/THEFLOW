package com.ana.theflow.ui.creation

import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast

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

    override fun buildFields(parent: LinearLayout) {
        description = field("Describe the collaboration", minHeightDp = 140, multiLine = true)
        parent.addView(description)

        parent.addView(sectionTitle("Who and what"))
        lookingFor = field("Who are you looking for?")
        danceStyle = field("Dance style")
        parent.addView(lookingFor)
        parent.addView(danceStyle)

        parent.addView(sectionTitle("Logistics"))
        location = field("Location")
        dates = field("Relevant dates")
        paymentStatus = field("Payment status")
        parent.addView(location)
        parent.addView(dates)
        parent.addView(paymentStatus)

        parent.addView(sectionTitle("Supporting media"))
        parent.addView(LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(optionButton("Add photo") { openMediaPicker() })
            addView(optionButton("Add video") { openMediaPicker() })
        })

        parent.addView(sectionTitle("Applications and contact"))
        applicationOptions = field("Application options")
        contactOptions = field("Contact options")
        parent.addView(applicationOptions)
        parent.addView(contactOptions)
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
}
