package com.ana.theflow.ui.jobs

import android.graphics.Typeface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.ana.theflow.MainActivity
import com.ana.theflow.R
import com.ana.theflow.data.model.account.ActiveAccount
import com.ana.theflow.data.model.jobs.DanceJob
import com.ana.theflow.data.model.jobs.JobApplication
import com.ana.theflow.data.repository.AuthRepository
import com.ana.theflow.data.repository.JobRepository
import com.ana.theflow.data.session.ActiveAccountHolder
import com.ana.theflow.ui.settings.SettingsUi
import com.ana.theflow.ui.settings.dp

// Jobs are business-account-only: browsing/saving/applying is open to everyone, but posting and
// the "Posted jobs" tab only ever appear when a studio account is active.
class JobsFragment : Fragment() {

    private val jobRepository = JobRepository()
    private val authRepository = AuthRepository()

    private lateinit var content: LinearLayout
    private lateinit var progress: ProgressBar
    private var selectedTab = JobsTab.BROWSE
    private var canPostAsActiveAccount = false

    private var activeJobs: List<DanceJob> = emptyList()
    private var savedJobs: List<DanceJob> = emptyList()
    private var myApplications: List<Pair<JobApplication, DanceJob?>> = emptyList()
    private var postedJobs: List<DanceJob> = emptyList()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val root = SettingsUi.screen(this, "Jobs")
        val scroll = SettingsUi.contentScroll(requireContext())
        content = SettingsUi.contentColumn(requireContext())
        progress = ProgressBar(requireContext()).apply {
            indeterminateTintList = android.content.res.ColorStateList.valueOf(requireContext().getColor(R.color.flow_brand))
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = 12.dp()
            }
        }
        content.addView(progress)
        scroll.addView(content)
        root.addView(scroll)
        return root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        loadEverything()
    }

    override fun onResume() {
        super.onResume()
        if (::content.isInitialized) loadEverything()
    }

    private fun loadEverything() {
        progress.visibility = View.VISIBLE
        jobRepository.canPublishJobsAs(onSuccess = { canPost ->
            if (!isAdded) return@canPublishJobsAs
            canPostAsActiveAccount = canPost
            loadTabData()
        })
    }

    private fun loadTabData() {
        when (selectedTab) {
            JobsTab.BROWSE -> jobRepository.loadActiveJobs(
                onSuccess = { jobs -> activeJobs = jobs; finishLoad() },
                onFailure = { activeJobs = emptyList(); finishLoad() }
            )
            JobsTab.SAVED -> jobRepository.loadSavedJobs(
                onSuccess = { jobs -> savedJobs = jobs; finishLoad() },
                onFailure = { savedJobs = emptyList(); finishLoad() }
            )
            JobsTab.APPLICATIONS -> jobRepository.loadMyApplications(
                onSuccess = { applications -> loadApplicationJobs(applications) },
                onFailure = { myApplications = emptyList(); finishLoad() }
            )
            JobsTab.POSTED -> jobRepository.loadListings(
                account = ActiveAccountHolder.current(),
                onSuccess = { jobs -> postedJobs = jobs; finishLoad() },
                onFailure = { postedJobs = emptyList(); finishLoad() }
            )
        }
    }

    private fun loadApplicationJobs(applications: List<JobApplication>) {
        if (applications.isEmpty()) {
            myApplications = emptyList()
            finishLoad()
            return
        }
        var pending = applications.size
        val resolved = MutableList<Pair<JobApplication, DanceJob?>?>(applications.size) { null }
        applications.forEachIndexed { index, application ->
            jobRepository.loadJob(
                jobId = application.jobId,
                onSuccess = { job ->
                    resolved[index] = application to job
                    pending -= 1
                    if (pending == 0 && isAdded) {
                        myApplications = resolved.filterNotNull()
                        finishLoad()
                    }
                },
                onFailure = {
                    resolved[index] = application to null
                    pending -= 1
                    if (pending == 0 && isAdded) {
                        myApplications = resolved.filterNotNull()
                        finishLoad()
                    }
                }
            )
        }
    }

    private fun finishLoad() {
        if (!isAdded) return
        progress.visibility = View.GONE
        render()
    }

    private fun render() {
        content.removeAllViews()
        content.addView(tabRow())
        content.addView(postJobRow())

        when (selectedTab) {
            JobsTab.BROWSE -> addJobSection(activeJobs, "No open jobs right now.")
            JobsTab.SAVED -> addJobSection(savedJobs, "You haven't saved any jobs yet.")
            JobsTab.APPLICATIONS -> addApplicationsSection()
            JobsTab.POSTED -> addJobSection(postedJobs, "This studio hasn't posted any jobs yet.")
        }
    }

    private fun postJobRow(): View {
        val account = ActiveAccountHolder.current()
        return if (account is ActiveAccount.StudioAccount && canPostAsActiveAccount) {
            SettingsUi.row(
                context = requireContext(),
                title = "Post a job",
                description = "Publish a job opening from this studio.",
                onClick = { (requireActivity() as MainActivity).openJobCreation() }
            )
        } else {
            SettingsUi.message(requireContext(), "Job posting requires a business account. Switch to a studio account to post a job.")
        }
    }

    private fun tabRow(): View {
        val account = ActiveAccountHolder.current()
        val showPosted = account is ActiveAccount.StudioAccount
        return LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(tabButton("Browse", JobsTab.BROWSE))
            addView(tabButton("Saved", JobsTab.SAVED))
            addView(tabButton("Applied", JobsTab.APPLICATIONS))
            if (showPosted) addView(tabButton("Posted", JobsTab.POSTED))
        }
    }

    private fun tabButton(textValue: String, tab: JobsTab): Button {
        val selected = selectedTab == tab
        return Button(requireContext()).apply {
            text = textValue
            isAllCaps = false
            textSize = 12f
            minWidth = 0
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            setTextColor(context.getColor(if (selected) R.color.white else R.color.flow_brand))
            setBackgroundResource(if (selected) R.drawable.bg_flow_button_primary else R.drawable.bg_flow_button_secondary)
            layoutParams = LinearLayout.LayoutParams(0, 42.dp(), 1f).apply {
                rightMargin = 6.dp()
                bottomMargin = 12.dp()
            }
            setOnClickListener {
                if (selectedTab != tab) {
                    selectedTab = tab
                    progress.visibility = View.VISIBLE
                    content.removeAllViews()
                    content.addView(progress)
                    loadTabData()
                }
            }
        }
    }

    private fun addJobSection(jobs: List<DanceJob>, emptyText: String) {
        if (jobs.isEmpty()) {
            content.addView(SettingsUi.message(requireContext(), emptyText))
            return
        }
        jobs.forEach { job -> content.addView(jobCard(job)) }
    }

    private fun addApplicationsSection() {
        if (myApplications.isEmpty()) {
            content.addView(SettingsUi.message(requireContext(), "You haven't applied to any jobs yet."))
            return
        }
        myApplications.forEach { (application, job) -> content.addView(applicationCard(application, job)) }
    }

    private fun jobCard(job: DanceJob): View {
        val context = requireContext()
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(R.drawable.bg_flow_card)
            setPadding(14.dp(), 12.dp(), 14.dp(), 12.dp())
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = 10.dp()
            }
            isClickable = true
            isFocusable = true
            setOnClickListener { (requireActivity() as MainActivity).openJobDetail(job.jobId) }
            addView(TextView(context).apply {
                text = job.title
                setTextColor(context.getColor(R.color.flow_ink))
                textSize = 16f
                setTypeface(typeface, Typeface.BOLD)
            })
            addView(TextView(context).apply {
                text = listOf(job.employerName, job.city).filter { it.isNotBlank() }.joinToString(" · ")
                setTextColor(context.getColor(R.color.flow_text_secondary))
                textSize = 13f
                setPadding(0, 2.dp(), 0, 0)
            })
            addView(TextView(context).apply {
                text = listOf(job.workType.cleanLabel(), job.jobType.cleanLabel()).filter { it.isNotBlank() }.joinToString(" · ")
                setTextColor(context.getColor(R.color.flow_brand))
                textSize = 12f
                setPadding(0, 6.dp(), 0, 0)
            })
        }
    }

    private fun applicationCard(application: JobApplication, job: DanceJob?): View {
        val context = requireContext()
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(R.drawable.bg_flow_card)
            setPadding(14.dp(), 12.dp(), 14.dp(), 12.dp())
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = 10.dp()
            }
            isClickable = job != null
            isFocusable = job != null
            if (job != null) setOnClickListener { (requireActivity() as MainActivity).openJobDetail(job.jobId) }
            addView(TextView(context).apply {
                text = job?.title ?: "Job no longer available"
                setTextColor(context.getColor(R.color.flow_ink))
                textSize = 16f
                setTypeface(typeface, Typeface.BOLD)
            })
            addView(TextView(context).apply {
                text = "Status: ${application.status.replaceFirstChar { it.uppercase() }}"
                setTextColor(context.getColor(R.color.flow_brand))
                textSize = 12f
                setPadding(0, 4.dp(), 0, 0)
            })
        }
    }

    private fun String.cleanLabel(): String {
        return replace("_", " ").replaceFirstChar { it.uppercase() }
    }

    private enum class JobsTab { BROWSE, SAVED, APPLICATIONS, POSTED }
}
