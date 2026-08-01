package com.ana.theflow.ui.jobs

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import com.ana.theflow.MainActivity
import com.ana.theflow.R
import com.ana.theflow.data.model.jobs.DanceJob
import com.ana.theflow.data.repository.JobRepository
import com.ana.theflow.data.session.ActiveAccountHolder
import com.ana.theflow.ui.common.UiText
import com.ana.theflow.ui.settings.SettingsUi
import com.ana.theflow.ui.settings.dp

// Publishes a job opening on behalf of the studio the signed-in user currently has active.
// Jobs are business-account-only - there is no personal posting path.
class JobCreationFragment : Fragment() {

    private val jobRepository = JobRepository()

    private lateinit var content: LinearLayout
    private lateinit var titleField: EditText
    private lateinit var descriptionField: EditText
    private lateinit var experienceField: EditText
    private lateinit var requirementsField: EditText
    private lateinit var paymentField: EditText
    private lateinit var contactField: EditText
    private lateinit var externalUrlField: EditText
    private lateinit var workTypeSummary: TextView
    private lateinit var jobTypeSummary: TextView
    private lateinit var stylesSummary: TextView
    private lateinit var submitButton: Button
    private lateinit var messageLabel: TextView

    private var selectedWorkType = DanceJob.WORK_ON_SITE
    private var selectedJobType = DanceJob.TYPE_FREELANCE
    private var selectedStyles: MutableSet<String> = mutableSetOf()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val root = SettingsUi.screen(this, "Post a job")
        val scroll = SettingsUi.contentScroll(requireContext())
        content = SettingsUi.contentColumn(requireContext())
        scroll.addView(content)
        root.addView(scroll)
        render()
        return root
    }

    private fun render() {
        content.removeAllViews()
        content.addView(SettingsUi.message(requireContext(), "This job will be posted by your active studio account."))

        titleField = field("Job title")
        content.addView(titleField)
        descriptionField = field("Description", minHeightDp = 110, multiLine = true)
        content.addView(descriptionField)

        content.addView(choiceRow("Work type", workTypeLabel(selectedWorkType)) { summary ->
            workTypeSummary = summary
            AlertDialog.Builder(requireContext())
                .setTitle("Work type")
                .setSingleChoiceItems(workTypeOptions.map { workTypeLabel(it) }.toTypedArray(), workTypeOptions.indexOf(selectedWorkType)) { dialog, which ->
                    selectedWorkType = workTypeOptions[which]
                    summary.text = workTypeLabel(selectedWorkType)
                    dialog.dismiss()
                }
                .show()
        })

        content.addView(choiceRow("Job type", jobTypeLabel(selectedJobType)) { summary ->
            jobTypeSummary = summary
            AlertDialog.Builder(requireContext())
                .setTitle("Job type")
                .setSingleChoiceItems(jobTypeOptions.map { jobTypeLabel(it) }.toTypedArray(), jobTypeOptions.indexOf(selectedJobType)) { dialog, which ->
                    selectedJobType = jobTypeOptions[which]
                    summary.text = jobTypeLabel(selectedJobType)
                    dialog.dismiss()
                }
                .show()
        })

        content.addView(choiceRow("Dance styles", stylesLabel()) { summary ->
            stylesSummary = summary
            val selected = danceStyleOptions.map { selectedStyles.contains(it) }.toBooleanArray()
            AlertDialog.Builder(requireContext())
                .setTitle("Dance styles")
                .setMultiChoiceItems(danceStyleOptions.toTypedArray(), selected) { _, which, checked ->
                    if (checked) selectedStyles.add(danceStyleOptions[which]) else selectedStyles.remove(danceStyleOptions[which])
                }
                .setPositiveButton("Done") { _, _ -> summary.text = stylesLabel() }
                .show()
        })

        experienceField = field("Experience level (optional)")
        content.addView(experienceField)
        requirementsField = field("Requirements, one per line (optional)", minHeightDp = 80, multiLine = true)
        content.addView(requirementsField)
        paymentField = field("Payment (optional)")
        content.addView(paymentField)
        contactField = field("Contact method (optional)")
        content.addView(contactField)
        externalUrlField = field("External application link (optional)")
        content.addView(externalUrlField)

        messageLabel = SettingsUi.message(requireContext(), "")
        messageLabel.visibility = View.GONE
        content.addView(messageLabel)

        submitButton = Button(requireContext()).apply {
            text = "Publish job"
            isAllCaps = false
            setTextColor(context.getColor(R.color.flow_surface))
            setBackgroundResource(R.drawable.bg_flow_button_primary)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 52.dp()).apply {
                topMargin = 8.dp()
            }
            setOnClickListener { submit() }
        }
        content.addView(submitButton)
    }

    private fun submit() {
        val studioId = ActiveAccountHolder.currentStudioId()
        if (studioId.isBlank()) {
            showMessage("Switch to a studio account before posting a job.")
            return
        }
        val title = titleField.text.toString()
        val description = descriptionField.text.toString()
        if (title.isBlank() || description.isBlank()) {
            showMessage("Add a job title and description.")
            return
        }
        submitButton.isEnabled = false
        val requirements = requirementsField.text.toString().split("\n").map { it.trim() }.filter { it.isNotBlank() }
        jobRepository.createJob(
            studioId = studioId,
            title = title,
            danceStyles = selectedStyles.toList(),
            description = description,
            workType = selectedWorkType,
            jobType = selectedJobType,
            experienceLevel = experienceField.text.toString(),
            requirements = requirements,
            paymentText = paymentField.text.toString(),
            contactMethod = contactField.text.toString(),
            externalApplyUrl = externalUrlField.text.toString(),
            onSuccess = { jobId ->
                if (!isAdded) return@createJob
                Toast.makeText(requireContext(), "Job posted", Toast.LENGTH_SHORT).show()
                parentFragmentManager.popBackStack()
                (activity as? MainActivity)?.openJobDetail(jobId)
            },
            onFailure = { error ->
                if (!isAdded) return@createJob
                submitButton.isEnabled = true
                showMessage(UiText.friendlyError(error, "We could not publish this job."))
            }
        )
    }

    private fun showMessage(text: String) {
        messageLabel.text = text
        messageLabel.visibility = View.VISIBLE
    }

    private fun stylesLabel(): String = if (selectedStyles.isEmpty()) "Choose styles" else selectedStyles.joinToString(", ")

    private fun workTypeLabel(value: String): String = when (value) {
        DanceJob.WORK_REMOTE -> "Remote"
        DanceJob.WORK_HYBRID -> "Hybrid"
        else -> "On-site"
    }

    private fun jobTypeLabel(value: String): String = when (value) {
        DanceJob.TYPE_FULL_TIME -> "Full-time"
        DanceJob.TYPE_PART_TIME -> "Part-time"
        DanceJob.TYPE_TEMPORARY -> "Temporary"
        DanceJob.TYPE_ONE_TIME -> "One-time"
        else -> "Freelance"
    }

    private fun choiceRow(label: String, initialSummary: String, onClick: (TextView) -> Unit): View {
        val context = requireContext()
        val summary = TextView(context).apply {
            text = initialSummary
            setTextColor(context.getColor(R.color.flow_text_secondary))
            textSize = 12f
            setPadding(0, 3.dp(), 0, 0)
        }
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(R.drawable.bg_flow_card)
            setPadding(14.dp(), 12.dp(), 14.dp(), 12.dp())
            isClickable = true
            isFocusable = true
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = 10.dp()
            }
            addView(TextView(context).apply {
                text = label
                setTextColor(context.getColor(R.color.flow_ink))
                textSize = 14f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
            })
            addView(summary)
            setOnClickListener { onClick(summary) }
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

    private companion object {
        val workTypeOptions = listOf(DanceJob.WORK_ON_SITE, DanceJob.WORK_REMOTE, DanceJob.WORK_HYBRID)
        val jobTypeOptions = listOf(
            DanceJob.TYPE_FREELANCE, DanceJob.TYPE_FULL_TIME, DanceJob.TYPE_PART_TIME,
            DanceJob.TYPE_TEMPORARY, DanceJob.TYPE_ONE_TIME
        )
        val danceStyleOptions = listOf("Hip Hop", "Heels", "Salsa", "Contemporary", "Afro", "Ballet", "Jazz", "Bachata")
    }
}
