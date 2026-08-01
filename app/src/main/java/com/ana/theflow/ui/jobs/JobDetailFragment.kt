package com.ana.theflow.ui.jobs

import android.graphics.Typeface
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
import com.ana.theflow.R
import com.ana.theflow.data.model.jobs.DanceJob
import com.ana.theflow.data.model.jobs.JobApplication
import com.ana.theflow.data.model.user.User
import com.ana.theflow.data.repository.AuthRepository
import com.ana.theflow.data.repository.JobRepository
import com.ana.theflow.data.repository.UserRepository
import com.ana.theflow.ui.common.UiText
import com.ana.theflow.ui.settings.SettingsUi
import com.ana.theflow.ui.settings.dp
import com.ana.theflow.utilities.AccountPermissions

// Shows one job posting: a manager of the posting studio sees applicants instead of an apply
// button, since business accounts never apply to their own postings.
class JobDetailFragment : Fragment() {

    private val jobRepository = JobRepository()
    private val authRepository = AuthRepository()
    private val userRepository = UserRepository()

    private val jobId: String get() = arguments?.getString(ARG_JOB_ID).orEmpty()

    private lateinit var content: LinearLayout
    private lateinit var messageLabel: TextView
    private var currentUser: User? = null
    private var isSaved = false
    private var hasApplied = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val root = SettingsUi.screen(this, "Job")
        val scroll = SettingsUi.contentScroll(requireContext())
        content = SettingsUi.contentColumn(requireContext())
        messageLabel = SettingsUi.message(requireContext(), "Loading job...")
        content.addView(messageLabel)
        scroll.addView(content)
        root.addView(scroll)
        return root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        loadCurrentUser()
        loadJob()
    }

    private fun loadCurrentUser() {
        val uid = authRepository.getCurrentUserUid() ?: return
        userRepository.getUserByUid(uid, onSuccess = { user -> currentUser = user }, onFailure = {})
    }

    private fun loadJob() {
        if (jobId.isBlank()) {
            messageLabel.text = "This job is not available."
            return
        }
        jobRepository.loadJob(
            jobId = jobId,
            onSuccess = { job -> if (isAdded) loadJobState(job) },
            onFailure = { if (isAdded) messageLabel.text = "This job is not available." }
        )
    }

    private fun loadJobState(job: DanceJob) {
        jobRepository.isJobSaved(jobId, onSuccess = { saved ->
            if (!isAdded) return@isJobSaved
            isSaved = saved
            jobRepository.loadMyApplications(
                onSuccess = { applications ->
                    hasApplied = applications.any { it.jobId == jobId }
                    if (isAdded) render(job)
                },
                onFailure = { if (isAdded) render(job) }
            )
        })
    }

    private fun render(job: DanceJob) {
        content.removeAllViews()
        val user = currentUser
        val manages = user != null && AccountPermissions.manages(user, job.studioId)

        content.addView(TextView(requireContext()).apply {
            text = job.title
            setTextColor(context.getColor(R.color.flow_ink))
            textSize = 22f
            setTypeface(typeface, Typeface.BOLD)
        })
        content.addView(TextView(requireContext()).apply {
            text = listOf(job.employerName, job.city).filter { it.isNotBlank() }.joinToString(" · ")
            setTextColor(context.getColor(R.color.flow_text_secondary))
            textSize = 14f
            setPadding(0, 4.dp(), 0, 12.dp())
        })
        content.addView(detailCard("Details", listOfNotNull(
            "Work type: ${job.workType.cleanLabel()}",
            "Job type: ${job.jobType.cleanLabel()}",
            job.experienceLevel.takeIf { it.isNotBlank() }?.let { "Experience: $it" },
            job.danceStyles.takeIf { it.isNotEmpty() }?.let { "Styles: ${it.joinToString(", ")}" },
            job.paymentText.takeIf { it.isNotBlank() }?.let { "Payment: $it" }
        ).joinToString("\n")))
        content.addView(detailCard("Description", job.description.ifBlank { "No description provided." }))
        if (job.requirements.isNotEmpty()) {
            content.addView(detailCard("Requirements", job.requirements.joinToString("\n") { "• $it" }))
        }
        if (job.contactMethod.isNotBlank() || job.externalApplyUrl.isNotBlank()) {
            content.addView(detailCard("How to apply", listOfNotNull(
                job.contactMethod.takeIf { it.isNotBlank() },
                job.externalApplyUrl.takeIf { it.isNotBlank() }
            ).joinToString("\n")))
        }

        if (manages) {
            content.addView(SettingsUi.message(requireContext(), "You manage this studio, so this job was posted by your business account."))
            content.addView(applicantsSection(job))
        } else {
            content.addView(actionRow(job))
        }
    }

    private fun actionRow(job: DanceJob): View {
        val row = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                topMargin = 8.dp()
            }
        }
        val applyButton = actionButton(if (hasApplied) "Applied" else "Apply", primary = true) {}
        applyButton.isEnabled = !hasApplied
        applyButton.setOnClickListener { showApplyDialog(job) }
        row.addView(applyButton)

        val saveButton = actionButton(if (isSaved) "Saved" else "Save", primary = false) {}
        saveButton.setOnClickListener {
            saveButton.isEnabled = false
            jobRepository.toggleSaveJob(
                job = job,
                onSuccess = { nowSaved ->
                    if (!isAdded) return@toggleSaveJob
                    isSaved = nowSaved
                    saveButton.isEnabled = true
                    saveButton.text = if (nowSaved) "Saved" else "Save"
                },
                onFailure = { error ->
                    if (!isAdded) return@toggleSaveJob
                    saveButton.isEnabled = true
                    Toast.makeText(requireContext(), UiText.friendlyError(error, "We could not update this job."), Toast.LENGTH_SHORT).show()
                }
            )
        }
        row.addView(saveButton)
        return row
    }

    private fun actionButton(label: String, primary: Boolean, onClick: () -> Unit): Button {
        return Button(requireContext()).apply {
            text = label
            isAllCaps = false
            setTextColor(context.getColor(if (primary) R.color.flow_surface else R.color.flow_brand))
            setBackgroundResource(if (primary) R.drawable.bg_flow_button_primary else R.drawable.bg_flow_button_secondary)
            layoutParams = LinearLayout.LayoutParams(0, 46.dp(), 1f).apply { rightMargin = 8.dp() }
            setOnClickListener { onClick() }
        }
    }

    private fun showApplyDialog(job: DanceJob) {
        val user = currentUser
        if (user == null) {
            Toast.makeText(requireContext(), "Please sign in to apply.", Toast.LENGTH_SHORT).show()
            return
        }
        val context = requireContext()
        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24.dp(), 16.dp(), 24.dp(), 0)
        }
        val introField = EditText(context).apply {
            hint = "Introduce yourself"
            minLines = 3
            setTextColor(context.getColor(R.color.flow_ink))
            setHintTextColor(context.getColor(R.color.flow_text_muted))
            setBackgroundResource(R.drawable.bg_flow_input)
            setPadding(14.dp(), 12.dp(), 14.dp(), 12.dp())
        }
        val experienceField = EditText(context).apply {
            hint = "Relevant experience (optional)"
            setTextColor(context.getColor(R.color.flow_ink))
            setHintTextColor(context.getColor(R.color.flow_text_muted))
            setBackgroundResource(R.drawable.bg_flow_input)
            setPadding(14.dp(), 12.dp(), 14.dp(), 12.dp())
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = 10.dp() }
        }
        val portfolioField = EditText(context).apply {
            hint = "Portfolio link (optional)"
            setTextColor(context.getColor(R.color.flow_ink))
            setHintTextColor(context.getColor(R.color.flow_text_muted))
            setBackgroundResource(R.drawable.bg_flow_input)
            setPadding(14.dp(), 12.dp(), 14.dp(), 12.dp())
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = 10.dp() }
        }
        layout.addView(introField)
        layout.addView(experienceField)
        layout.addView(portfolioField)

        AlertDialog.Builder(context)
            .setTitle("Apply to ${job.title}")
            .setView(layout)
            .setPositiveButton("Submit", null)
            .setNegativeButton("Cancel", null)
            .create()
            .apply {
                setOnShowListener {
                    getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                        jobRepository.submitApplication(
                            job = job,
                            applicant = user,
                            introduction = introField.text.toString(),
                            experience = experienceField.text.toString(),
                            portfolioUrl = portfolioField.text.toString(),
                            onSuccess = {
                                if (!isAdded) return@submitApplication
                                hasApplied = true
                                Toast.makeText(requireContext(), "Application submitted", Toast.LENGTH_SHORT).show()
                                dismiss()
                                render(job)
                            },
                            onFailure = { error ->
                                if (!isAdded) return@submitApplication
                                Toast.makeText(requireContext(), UiText.friendlyError(error, "We could not submit this application."), Toast.LENGTH_SHORT).show()
                            }
                        )
                    }
                }
            }
            .show()
    }

    private fun applicantsSection(job: DanceJob): View {
        val context = requireContext()
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                topMargin = 8.dp()
            }
            addView(TextView(context).apply {
                text = "Applicants"
                setTextColor(context.getColor(R.color.flow_ink))
                textSize = 16f
                setTypeface(typeface, Typeface.BOLD)
                setPadding(0, 0, 0, 6.dp())
            })
        }
        jobRepository.loadApplicationsForStudio(
            studioId = job.studioId,
            onSuccess = { applications ->
                if (!isAdded) return@loadApplicationsForStudio
                val forThisJob = applications.filter { it.jobId == jobId }
                if (forThisJob.isEmpty()) {
                    container.addView(SettingsUi.message(context, "No applicants yet."))
                } else {
                    forThisJob.forEach { application -> container.addView(applicantRow(application)) }
                }
            },
            onFailure = { if (isAdded) container.addView(SettingsUi.message(context, "We could not load applicants.")) }
        )
        return container
    }

    private fun applicantRow(application: JobApplication): View {
        return SettingsUi.row(
            context = requireContext(),
            title = application.applicantName.ifBlank { "Dancer" },
            description = application.introduction.take(120).let { if (it.length < application.introduction.length) "$it…" else it },
            value = application.status.replaceFirstChar { it.uppercase() },
            enabled = false
        )
    }

    private fun detailCard(title: String, body: String): View {
        val context = requireContext()
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(R.drawable.bg_flow_card)
            setPadding(14.dp(), 14.dp(), 14.dp(), 14.dp())
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = 12.dp()
            }
            addView(TextView(context).apply {
                text = title
                setTextColor(context.getColor(R.color.flow_ink))
                textSize = 15f
                setTypeface(typeface, Typeface.BOLD)
            })
            addView(TextView(context).apply {
                text = body
                setTextColor(context.getColor(R.color.flow_text_secondary))
                textSize = 13f
                setLineSpacing(3.dp().toFloat(), 1f)
                setPadding(0, 8.dp(), 0, 0)
            })
        }
    }

    private fun String.cleanLabel(): String = replace("_", " ").replaceFirstChar { it.uppercase() }

    companion object {
        private const val ARG_JOB_ID = "ARG_JOB_ID"

        fun newInstance(jobId: String): JobDetailFragment {
            return JobDetailFragment().apply {
                arguments = Bundle().apply { putString(ARG_JOB_ID, jobId) }
            }
        }
    }
}
