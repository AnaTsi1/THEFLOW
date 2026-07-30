package com.ana.theflow.ui.jobs

import android.app.AlertDialog
import android.content.Intent
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.ana.theflow.R
import com.ana.theflow.data.model.jobs.DanceJob
import com.ana.theflow.data.model.jobs.JobApplication
import com.ana.theflow.data.model.user.User
import com.ana.theflow.data.repository.AuthRepository
import com.ana.theflow.data.repository.JobRepository
import com.ana.theflow.data.repository.UserRepository
import com.ana.theflow.ui.common.UiText

class JobsFragment : Fragment() {

    private val jobRepository = JobRepository()
    private val userRepository = UserRepository()
    private val authRepository = AuthRepository()
    private lateinit var root: LinearLayout
    private lateinit var searchInput: EditText
    private lateinit var progress: ProgressBar
    private lateinit var message: TextView
    private lateinit var jobsForYou: LinearLayout
    private lateinit var moreJobs: LinearLayout
    private lateinit var savedJobs: LinearLayout
    private lateinit var applications: LinearLayout
    private lateinit var listings: LinearLayout
    private lateinit var postJobButton: Button
    private var currentUser: User? = null
    private var canPublish = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val context = requireContext()
        return ScrollView(context).apply {
            setBackgroundResource(R.drawable.bg_flow_screen)
            clipToPadding = false
            setPadding(18.dp(), 18.dp(), 18.dp(), 96.dp())
            root = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
            }
            addView(root)
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        buildUi()
        loadCurrentUser()
    }

    private fun buildUi() {
        root.addView(header())
        searchInput = EditText(requireContext()).apply {
            hint = "Search title, employer, city, style..."
            setTextColor(requireContext().getColor(R.color.flow_ink))
            setHintTextColor(requireContext().getColor(R.color.flow_text_muted))
            setBackgroundResource(R.drawable.bg_flow_input)
            setSingleLine(true)
            setPadding(14.dp(), 0, 14.dp(), 0)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 52.dp()).apply {
                topMargin = 14.dp()
            }
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
                override fun afterTextChanged(s: Editable?) {
                    loadJobs()
                }
            })
        }
        root.addView(searchInput)
        postJobButton = Button(requireContext()).apply {
            text = "Post a Job"
            setTextColor(requireContext().getColor(R.color.white))
            setTypeface(typeface, Typeface.BOLD)
            setBackgroundResource(R.drawable.bg_flow_button_primary)
            visibility = View.GONE
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 46.dp()).apply {
                topMargin = 10.dp()
            }
            setOnClickListener { showPostJobDialog() }
        }
        root.addView(postJobButton)
        progress = ProgressBar(requireContext()).apply {
            isIndeterminate = true
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                topMargin = 18.dp()
            }
        }
        root.addView(progress)
        message = TextView(requireContext()).apply {
            setTextColor(requireContext().getColor(R.color.flow_text_secondary))
            textSize = 14f
            setPadding(0, 12.dp(), 0, 0)
        }
        root.addView(message)
        jobsForYou = addSection("Jobs for You")
        root.addView(sectionTitle("Explore Employers"))
        root.addView(employerStrip())
        moreJobs = addSection("More Jobs for You")
        savedJobs = addSection("Saved Jobs")
        applications = addSection("My Applications")
        listings = addSection("My Job Listings")
    }

    private fun header(): View {
        return LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            addView(TextView(context).apply {
                text = "Jobs"
                setTextColor(context.getColor(R.color.flow_ink))
                textSize = 28f
                setTypeface(typeface, Typeface.BOLD)
            })
            addView(TextView(context).apply {
                text = "Dance jobs, auditions, studio roles, and creative opportunities."
                setTextColor(context.getColor(R.color.flow_text_secondary))
                textSize = 14f
                setPadding(0, 6.dp(), 0, 0)
            })
        }
    }

    private fun addSection(title: String): LinearLayout {
        root.addView(sectionTitle(title))
        return LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            root.addView(this)
        }
    }

    private fun sectionTitle(title: String): TextView {
        return TextView(requireContext()).apply {
            text = title
            setTextColor(requireContext().getColor(R.color.flow_ink))
            textSize = 18f
            setTypeface(typeface, Typeface.BOLD)
            setPadding(0, 18.dp(), 0, 4.dp())
        }
    }

    private fun employerStrip(): View {
        return TextView(requireContext()).apply {
            text = "Verified studios and choreographers can publish opportunities. Employer profiles open from each listing."
            setTextColor(requireContext().getColor(R.color.flow_text_secondary))
            setBackgroundResource(R.drawable.bg_flow_card)
            setPadding(14.dp(), 14.dp(), 14.dp(), 14.dp())
        }
    }

    private fun loadCurrentUser() {
        val uid = authRepository.getCurrentUserUid()
        if (uid == null) {
            showError("Please log in to browse jobs.")
            return
        }
        userRepository.getUserByUid(
            uid = uid,
            onSuccess = { user ->
                currentUser = user
                jobRepository.canPublishJobs(
                    onSuccess = { allowed ->
                        canPublish = allowed
                        postJobButton.visibility = if (allowed) View.VISIBLE else View.GONE
                        listings.visibility = if (allowed) View.VISIBLE else View.GONE
                        loadJobs()
                        loadPrivateSections()
                    },
                    onFailure = {
                        loadJobs()
                        loadPrivateSections()
                    }
                )
            },
            onFailure = { error -> showError(error) }
        )
    }

    private fun loadJobs() {
        progress.visibility = View.VISIBLE
        message.text = ""
        jobRepository.loadActiveJobs(
            query = searchInput.text?.toString().orEmpty(),
            onSuccess = { jobs ->
                if (!isAdded) return@loadActiveJobs
                progress.visibility = View.GONE
                renderJobs(jobs)
            },
            onFailure = { error -> showError(error) }
        )
    }

    private fun loadPrivateSections() {
        jobRepository.loadSavedJobs(
            onSuccess = { jobs ->
                if (isAdded) renderJobSection(savedJobs, jobs.take(6), empty = "No saved jobs yet.")
            },
            onFailure = { if (isAdded) savedJobs.addView(emptyText("Saved jobs could not load.")) }
        )
        jobRepository.loadMyApplications(
            onSuccess = { apps ->
                if (isAdded) renderApplications(apps)
            },
            onFailure = { if (isAdded) applications.addView(emptyText("Applications could not load.")) }
        )
        if (canPublish) {
            jobRepository.loadMyListings(
                onSuccess = { jobs ->
                    if (isAdded) renderJobSection(listings, jobs, empty = "You have not posted jobs yet.")
                },
                onFailure = { if (isAdded) listings.addView(emptyText("Listings could not load.")) }
            )
        }
    }

    private fun renderJobs(jobs: List<DanceJob>) {
        renderJobSection(jobsForYou, jobs.take(5), empty = "No matching active jobs yet.")
        renderJobSection(moreJobs, jobs.drop(5).take(20), empty = "More jobs will appear as the demo seed grows.")
    }

    private fun renderJobSection(parent: LinearLayout, jobs: List<DanceJob>, empty: String) {
        parent.removeAllViews()
        if (jobs.isEmpty()) {
            parent.addView(emptyText(empty))
            return
        }
        jobs.forEach { job -> addJobCard(parent, job) }
    }

    private fun renderApplications(items: List<JobApplication>) {
        applications.removeAllViews()
        if (items.isEmpty()) {
            applications.addView(emptyText("No applications yet."))
            return
        }
        items.forEach { application ->
            applications.addView(TextView(requireContext()).apply {
                text = "${application.status.label()} application by ${application.applicantName.ifBlank { "you" }}"
                setTextColor(context.getColor(R.color.flow_text_secondary))
                setBackgroundResource(R.drawable.bg_flow_card)
                setPadding(14.dp(), 12.dp(), 14.dp(), 12.dp())
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                    topMargin = 8.dp()
                }
                setOnClickListener {
                    jobRepository.withdrawApplication(
                        applicationId = application.applicationId,
                        onSuccess = {
                            Toast.makeText(requireContext(), "Application withdrawn", Toast.LENGTH_SHORT).show()
                            loadPrivateSections()
                        },
                        onFailure = { Toast.makeText(requireContext(), UiText.friendlyError(it, "We could not withdraw this application."), Toast.LENGTH_SHORT).show() }
                    )
                }
            })
        }
    }

    private fun addJobCard(parent: LinearLayout, job: DanceJob) {
        val card = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(R.drawable.bg_flow_card)
            setPadding(14.dp(), 14.dp(), 14.dp(), 14.dp())
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                topMargin = 10.dp()
            }
        }
        card.addView(TextView(requireContext()).apply {
            text = job.title
            setTextColor(context.getColor(R.color.flow_ink))
            textSize = 18f
            setTypeface(typeface, Typeface.BOLD)
        })
        card.addView(TextView(requireContext()).apply {
            text = listOf(job.employerName, job.city, job.workType.label(), job.jobType.label()).filter { it.isNotBlank() }.joinToString(" / ")
            setTextColor(context.getColor(R.color.flow_text_secondary))
            textSize = 13f
            setPadding(0, 5.dp(), 0, 0)
        })
        card.addView(TextView(requireContext()).apply {
            text = job.description
            setTextColor(context.getColor(R.color.flow_ink))
            textSize = 14f
            maxLines = 4
            setPadding(0, 10.dp(), 0, 0)
        })
        card.addView(TextView(requireContext()).apply {
            text = job.danceStyles.joinToString(" / ").ifBlank { "Dance" }
            setTextColor(context.getColor(R.color.flow_brand))
            textSize = 13f
            setTypeface(typeface, Typeface.BOLD)
            setPadding(0, 8.dp(), 0, 0)
        })
        card.addView(actions(job))
        parent.addView(card)
    }

    private fun actions(job: DanceJob): View {
        return LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 12.dp(), 0, 0)
            addView(actionButton("Apply") { showApplyDialog(job) })
            addView(actionButton("Save") {
                jobRepository.toggleSaveJob(
                    job = job,
                    onSuccess = { saved ->
                        Toast.makeText(requireContext(), if (saved) "Job saved" else "Job unsaved", Toast.LENGTH_SHORT).show()
                        loadPrivateSections()
                    },
                    onFailure = { Toast.makeText(requireContext(), UiText.friendlyError(it, "We could not save this job."), Toast.LENGTH_SHORT).show() }
                )
            })
            addView(actionButton("Share") { shareJob(job) })
        }
    }

    private fun actionButton(label: String, action: () -> Unit): Button {
        return Button(requireContext()).apply {
            text = label
            setTextColor(context.getColor(R.color.flow_ink))
            setBackgroundResource(R.drawable.bg_flow_button_secondary)
            minWidth = 0
            layoutParams = LinearLayout.LayoutParams(0, 42.dp(), 1f).apply {
                rightMargin = 8.dp()
            }
            setOnClickListener { action() }
        }
    }

    private fun showApplyDialog(job: DanceJob) {
        if (job.externalApplyUrl.isNotBlank()) {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(job.externalApplyUrl)))
            return
        }
        val applicant = currentUser ?: return
        val content = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(18.dp(), 10.dp(), 18.dp(), 0)
        }
        val intro = dialogField("Short introduction", multiLine = true)
        val experience = dialogField("Relevant experience", multiLine = true)
        val portfolio = dialogField("Portfolio, social, or video link")
        content.addView(intro)
        content.addView(experience)
        content.addView(portfolio)
        AlertDialog.Builder(requireContext())
            .setTitle("Apply to ${job.title}")
            .setView(content)
            .setNegativeButton(R.string.action_cancel, null)
            .setPositiveButton("Submit") { _, _ ->
                jobRepository.submitApplication(
                    job = job,
                    applicant = applicant,
                    introduction = intro.text.toString(),
                    experience = experience.text.toString(),
                    portfolioUrl = portfolio.text.toString(),
                    onSuccess = {
                        Toast.makeText(requireContext(), "Application submitted", Toast.LENGTH_SHORT).show()
                        loadPrivateSections()
                    },
                    onFailure = { Toast.makeText(requireContext(), it, Toast.LENGTH_LONG).show() }
                )
            }
            .show()
    }

    private fun showPostJobDialog() {
        val content = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(18.dp(), 10.dp(), 18.dp(), 0)
        }
        val title = dialogField("Job title")
        val employer = dialogField("Employer or studio")
        val city = dialogField("City")
        val styles = dialogField("Dance styles, comma separated")
        val description = dialogField("Description", multiLine = true)
        listOf(title, employer, city, styles, description).forEach { content.addView(it) }
        AlertDialog.Builder(requireContext())
            .setTitle("Post a Job")
            .setView(content)
            .setNegativeButton(R.string.action_cancel, null)
            .setPositiveButton("Publish") { _, _ ->
                jobRepository.createJob(
                    title = title.text.toString(),
                    employerName = employer.text.toString(),
                    city = city.text.toString(),
                    danceStyles = styles.text.toString().split(",").map { it.trim() }.filter { it.isNotBlank() },
                    description = description.text.toString(),
                    onSuccess = {
                        Toast.makeText(requireContext(), "Job created", Toast.LENGTH_SHORT).show()
                        loadJobs()
                        loadPrivateSections()
                    },
                    onFailure = { Toast.makeText(requireContext(), it, Toast.LENGTH_LONG).show() }
                )
            }
            .show()
    }

    private fun dialogField(hintText: String, multiLine: Boolean = false): EditText {
        return EditText(requireContext()).apply {
            hint = hintText
            setTextColor(context.getColor(R.color.flow_ink))
            setHintTextColor(context.getColor(R.color.flow_text_muted))
            setBackgroundResource(R.drawable.bg_flow_input)
            minLines = if (multiLine) 3 else 1
            maxLines = if (multiLine) 5 else 1
            setPadding(12.dp(), 0, 12.dp(), 0)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, if (multiLine) 100.dp() else 50.dp()).apply {
                topMargin = 10.dp()
            }
        }
    }

    private fun shareJob(job: DanceJob) {
        val text = "${job.title}\n${job.employerName} / ${job.city}\n${job.description.take(160)}"
        startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }, "Share job"))
    }

    private fun emptyText(textValue: String): TextView {
        return TextView(requireContext()).apply {
            text = textValue
            setTextColor(context.getColor(R.color.flow_text_muted))
            setBackgroundResource(R.drawable.bg_flow_card)
            setPadding(14.dp(), 12.dp(), 14.dp(), 12.dp())
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                topMargin = 8.dp()
            }
        }
    }

    private fun showError(error: String) {
        progress.visibility = View.GONE
        message.text = UiText.friendlyError(error, "Jobs are unavailable right now.")
    }

    private fun String.label(): String {
        return replace("_", " ").replaceFirstChar { it.uppercase() }
    }
}

private fun Int.dp(): Int {
    return (this * android.content.res.Resources.getSystem().displayMetrics.density).toInt()
}
