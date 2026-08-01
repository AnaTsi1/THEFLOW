package com.ana.theflow.ui.creation

import android.app.AlertDialog
import android.view.View
import android.widget.Button
import android.widget.AutoCompleteTextView
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import com.ana.theflow.utilities.CityOptions

class PostCreationFragment : BaseCreationFragment() {

    override val titleText = "Create post"
    override val publishButtonText = "Post"
    override val publishSuccessMessage = "Post created"

    private lateinit var postText: EditText
    private lateinit var audienceButton: Button
    private lateinit var selectedDetails: LinearLayout
    private var visibility = "public"
    private val postDetails = linkedMapOf<String, String>()

    override fun buildFields(parent: LinearLayout) {
        audienceButton = optionButton("Public") { showAudienceMenu() }
        parent.addView(LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 12.dp(), 0, 0)
            addView(audienceButton)
        })

        postText = field("What's flowing today?", minHeightDp = 180, multiLine = true)
        parent.addView(postText)

        // Only genuinely useful add-ons: media, and the two tags that actually feed
        // Discover/recommendations (location and dance style). No mood/tagging/music
        // gimmicks that don't do anything beyond decorating the caption text.
        parent.addView(LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 14.dp(), 0, 0)
            addView(optionButton("Photo") { openMediaPicker() })
            addView(optionButton("Video") { openMediaPicker() })
        })
        parent.addView(LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(optionButton("Location") { editDetail("Location") })
            addView(optionButton("Dance style") { editDetail("Dance style") })
        })

        selectedDetails = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 8.dp(), 0, 0)
        }
        parent.addView(selectedDetails)
    }

    override fun buildPayload(): CreatePayload? {
        val body = postText.text.toString().trim()
        val details = joinedDetails(
            "Location" to postDetails["Location"].orEmpty(),
            "Dance style" to postDetails["Dance style"].orEmpty()
        )
        val text = listOf(body, details).filter { it.isNotBlank() }.joinToString("\n\n")
        if (text.isBlank() && mediaType() == MEDIA_TYPE_NONE) {
            Toast.makeText(requireContext(), "Add text or media before posting", Toast.LENGTH_SHORT).show()
            return null
        }
        return CreatePayload(
            text = text,
            postType = POST_TYPE_REGULAR,
            visibility = visibility
        )
    }

    private fun showAudienceMenu() {
        PopupMenu(requireContext(), audienceButton).apply {
            menu.add("Public")
            menu.add("Followers")
            menu.add("Private")
            setOnMenuItemClickListener { item ->
                val label = item.title.toString()
                audienceButton.text = label
                visibility = when (label) {
                    "Followers" -> "followers"
                    "Private" -> "private"
                    else -> "public"
                }
                true
            }
            show()
        }
    }

    private fun editDetail(label: String) {
        if (label == "Dance style") {
            showChoiceDetail(label, listOf("Hip Hop", "Heels", "Salsa", "Contemporary", "Afro", "Ballet", "Jazz", "Bachata"))
            return
        }
        val input = field(label).apply {
            setText(postDetails[label].orEmpty())
            selectAll()
        }
        if (label == "Location") {
            val cityInput = AutoCompleteTextView(requireContext()).apply {
                hint = "City"
                setText(postDetails[label].orEmpty(), false)
                setTextColor(context.getColor(com.ana.theflow.R.color.flow_ink))
                setHintTextColor(context.getColor(com.ana.theflow.R.color.flow_text_muted))
                setBackgroundResource(com.ana.theflow.R.drawable.bg_flow_input)
                minHeight = 52.dp()
                setPadding(14.dp(), 0, 14.dp(), 0)
            }
            CityOptions.configureCitySelector(requireContext(), cityInput)
            AlertDialog.Builder(requireContext())
                .setTitle(label)
                .setView(cityInput)
                .setNegativeButton("Clear") { _, _ ->
                    postDetails.remove(label)
                    renderSelectedDetails()
                }
                .setPositiveButton("Done") { _, _ ->
                    val city = CityOptions.normalizeOptionalCity(cityInput.text.toString())
                    if (city.isNullOrBlank()) postDetails.remove(label) else postDetails[label] = city
                    renderSelectedDetails()
                }
                .show()
            return
        }
        AlertDialog.Builder(requireContext())
            .setTitle(label)
            .setView(input)
            .setNegativeButton("Clear") { _, _ ->
                postDetails.remove(label)
                renderSelectedDetails()
            }
            .setPositiveButton("Done") { _, _ ->
                val value = input.text.toString().trim()
                if (value.isBlank()) postDetails.remove(label) else postDetails[label] = value
                renderSelectedDetails()
            }
            .show()
    }

    private fun showChoiceDetail(label: String, options: List<String>) {
        AlertDialog.Builder(requireContext())
            .setTitle(label)
            .setItems(options.toTypedArray()) { _, which ->
                postDetails[label] = options[which]
                renderSelectedDetails()
            }
            .setNegativeButton("Clear") { _, _ ->
                postDetails.remove(label)
                renderSelectedDetails()
            }
            .show()
    }

    private fun renderSelectedDetails() {
        selectedDetails.removeAllViews()
        postDetails.forEach { (label, value) ->
            if (value.isNotBlank()) {
                selectedDetails.addView(TextView(requireContext()).apply {
                    text = "$label: $value"
                    setTextColor(context.getColor(com.ana.theflow.R.color.flow_ink))
                    textSize = 14f
                    setBackgroundResource(com.ana.theflow.R.drawable.bg_flow_input)
                    setPadding(12.dp(), 8.dp(), 12.dp(), 8.dp())
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply { topMargin = 6.dp() }
                    setOnClickListener { editDetail(label) }
                })
            }
        }
    }
}

private fun Int.dp(): Int {
    return (this * android.content.res.Resources.getSystem().displayMetrics.density).toInt()
}
