// Admin queue for pending studio requests, professional applications, and open content reports,
// each with its own tab and approve/reject actions.
package com.ana.theflow.ui.admin

import android.content.Intent
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.ana.theflow.MainActivity
import com.ana.theflow.R
import com.ana.theflow.data.model.professional.ProfessionalApplication
import com.ana.theflow.data.model.report.ContentReport
import com.ana.theflow.data.model.studio.StudioRequest
import com.ana.theflow.data.model.user.User
import com.ana.theflow.data.repository.AdminRepository
import com.ana.theflow.data.repository.UserRepository
import com.ana.theflow.databinding.FragmentAdminReviewBinding
import com.ana.theflow.utilities.Constants
import com.bumptech.glide.Glide
import com.google.firebase.Timestamp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AdminReviewFragment : Fragment() {

    private var _binding: FragmentAdminReviewBinding? = null
    private val binding get() = _binding!!
    private val adminRepository = AdminRepository()
    private val userRepository = UserRepository()
    private var selectedTab = AdminTab.STUDIO_REQUESTS
    // Kept in memory and adjusted locally when a card is approved/rejected, so the summary line
    // ("N pending requests") stays correct without re-fetching everything from Firestore.
    private var studioRequestCount = 0
    private var applicationCount = 0
    private var reportCount = 0

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentAdminReviewBinding.inflate(inflater, container, false)
        return binding.root
    }

    // Wires up the tabs and pull-to-refresh, then kicks off the initial load.
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.adminBTNBack.setOnClickListener {
            parentFragmentManager.popBackStack()
        }
        setupTabs()
        setupSwipeRefresh()
        loadPendingReviews()
    }

    // Hooks up the three tab buttons and shows whichever one is selected by default.
    private fun setupTabs() {
        binding.adminTABStudioRequests.setOnClickListener { selectTab(AdminTab.STUDIO_REQUESTS) }
        binding.adminTABProfessionalApplications.setOnClickListener { selectTab(AdminTab.PROFESSIONAL_APPLICATIONS) }
        binding.adminTABContentReports.setOnClickListener { selectTab(AdminTab.CONTENT_REPORTS) }
        renderTabSelection()
    }

    // Switching tabs is purely a visibility toggle - all three sections were already loaded
    // together by loadPendingReviews(), so this never triggers a network call on its own.
    private fun selectTab(tab: AdminTab) {
        if (selectedTab == tab) return
        selectedTab = tab
        renderTabSelection()
    }

    // Shows the selected tab's list and hides the other two, restyling the tab buttons to match.
    private fun renderTabSelection() {
        binding.adminSWIPEStudioRequests.visibility = if (selectedTab == AdminTab.STUDIO_REQUESTS) View.VISIBLE else View.GONE
        binding.adminSWIPEProfessionalApplications.visibility = if (selectedTab == AdminTab.PROFESSIONAL_APPLICATIONS) View.VISIBLE else View.GONE
        binding.adminSWIPEContentReports.visibility = if (selectedTab == AdminTab.CONTENT_REPORTS) View.VISIBLE else View.GONE

        styleTab(binding.adminTABStudioRequests, selectedTab == AdminTab.STUDIO_REQUESTS)
        styleTab(binding.adminTABProfessionalApplications, selectedTab == AdminTab.PROFESSIONAL_APPLICATIONS)
        styleTab(binding.adminTABContentReports, selectedTab == AdminTab.CONTENT_REPORTS)
    }

    private fun styleTab(button: Button, selected: Boolean) {
        button.setBackgroundResource(if (selected) R.drawable.bg_flow_button_primary else R.drawable.bg_flow_button_secondary)
        button.setTextColor(requireContext().getColor(if (selected) R.color.white else R.color.flow_brand))
    }

    // One SwipeRefreshLayout per tab (so the pull gesture only ever lives where the user is
    // actually looking), but all three trigger the same underlying load - the three request types
    // already come back from one combined Firestore round trip, so there is nothing to gain from
    // three separate network calls, and the other two tabs end up with fresh data too.
    private fun setupSwipeRefresh() {
        val onRefresh = SwipeRefreshLayout.OnRefreshListener { loadPendingReviews(isRefresh = true) }
        binding.adminSWIPEStudioRequests.setOnRefreshListener(onRefresh)
        binding.adminSWIPEProfessionalApplications.setOnRefreshListener(onRefresh)
        binding.adminSWIPEContentReports.setOnRefreshListener(onRefresh)
        listOf(binding.adminSWIPEStudioRequests, binding.adminSWIPEProfessionalApplications, binding.adminSWIPEContentReports)
            .forEach { it.setColorSchemeResources(R.color.flow_brand) }
    }

    // Loads all three review sections and re-renders every tab's list.
    private fun loadPendingReviews(isRefresh: Boolean = false) {
        if (!isRefresh) setLoading(true)

        adminRepository.loadPendingReviews(
            onSuccess = { data ->
                if (_binding == null) return@loadPendingReviews
                setLoading(false)
                stopRefreshing()

                studioRequestCount = data.studioRequests.size
                applicationCount = data.professionalApplications.size
                reportCount = data.contentReports.size

                binding.adminLAYStudioClaims.removeAllViews()
                binding.adminLAYProfessionalApplications.removeAllViews()
                binding.adminLAYContentReports.removeAllViews()
                renderStudioRequests(data.studioRequests, failed = AdminRepository.ReviewSection.STUDIO_REQUESTS in data.failedSections)
                renderProfessionalApplications(data.professionalApplications, failed = AdminRepository.ReviewSection.PROFESSIONAL_APPLICATIONS in data.failedSections)
                renderContentReports(data.contentReports, failed = AdminRepository.ReviewSection.CONTENT_REPORTS in data.failedSections)
                renderSummary()
            },
            onFailure = {
                if (_binding == null) return@loadPendingReviews
                setLoading(false)
                stopRefreshing()
                binding.adminLBLMessage.text = "Couldn't load admin requests. Pull down to try again."
                binding.adminLBLMessage.visibility = View.VISIBLE
            }
        )
    }

    private fun stopRefreshing() {
        binding.adminSWIPEStudioRequests.isRefreshing = false
        binding.adminSWIPEProfessionalApplications.isRefreshing = false
        binding.adminSWIPEContentReports.isRefreshing = false
    }

    // Updates the "N pending requests" message at the top of the screen.
    private fun renderSummary() {
        val total = studioRequestCount + applicationCount + reportCount
        binding.adminLBLMessage.text = if (total == 0) {
            "No pending requests right now."
        } else {
            "$total pending request${if (total == 1) "" else "s"} waiting for review."
        }
        binding.adminLBLMessage.visibility = View.VISIBLE
    }

    // Builds one card per pending studio request, showing different fields depending on whether
    // it's a brand-new studio or a claim on an existing one.
    private fun renderStudioRequests(requests: List<StudioRequest>, failed: Boolean) {
        if (failed) {
            binding.adminLAYStudioClaims.addView(retryRow("Couldn't load studio requests. Tap to retry.") { loadPendingReviews() })
            return
        }
        if (requests.isEmpty()) {
            binding.adminLAYStudioClaims.addView(emptyText("No pending studio requests."))
            return
        }

        requests.forEach { request ->
            val isCreate = request.type == StudioRequest.TYPE_CREATE
            val title = (if (isCreate) request.draftDisplayName else request.studioName)
                .ifBlank { if (isCreate) "New studio request" else "Studio claim" }
            val body = if (isCreate) {
                listOf(
                    "Type: Create new studio",
                    "Requester: ${request.requesterName.ifBlank { request.requesterEmail }}",
                    "City: ${request.draftCity.ifBlank { "Not provided" }}",
                    "Address: ${request.draftAddress.ifBlank { "Not provided" }}",
                    "Bio: ${request.draftBio.ifBlank { "Not provided" }}",
                    "Why: ${request.justification.ifBlank { "Not provided" }}"
                ).joinToString("\n")
            } else {
                listOf(
                    "Type: Claim existing studio",
                    "Requester: ${request.requesterName.ifBlank { request.requesterEmail }}",
                    "Email: ${request.requesterEmail}",
                    "Why: ${request.justification.ifBlank { "Not provided" }}",
                    "Verification: ${request.verificationDetails.ifBlank { "Not provided" }}"
                ).joinToString("\n")
            }

            lateinit var cardView: View
            cardView = reviewCard(
                title = title,
                body = body,
                onApprove = {
                    setCardBusy(cardView, true)
                    adminRepository.approveStudioRequest(
                        request = request,
                        onSuccess = {
                            if (_binding == null) return@approveStudioRequest
                            Toast.makeText(requireContext(), "Studio request approved", Toast.LENGTH_SHORT).show()
                            studioRequestCount = (studioRequestCount - 1).coerceAtLeast(0)
                            removeCard(binding.adminLAYStudioClaims, cardView, "No pending studio requests.")
                        },
                        onFailure = { error -> onCardActionFailed(cardView, error) }
                    )
                },
                onReject = {
                    setCardBusy(cardView, true)
                    adminRepository.rejectStudioRequest(
                        request = request,
                        onSuccess = {
                            if (_binding == null) return@rejectStudioRequest
                            Toast.makeText(requireContext(), "Studio request rejected", Toast.LENGTH_SHORT).show()
                            studioRequestCount = (studioRequestCount - 1).coerceAtLeast(0)
                            removeCard(binding.adminLAYStudioClaims, cardView, "No pending studio requests.")
                        },
                        onFailure = { error -> onCardActionFailed(cardView, error) }
                    )
                }
            )
            binding.adminLAYStudioClaims.addView(cardView)
        }
    }

    // Shows open moderation reports submitted by users.
    private fun renderContentReports(reports: List<ContentReport>, failed: Boolean) {
        if (failed) {
            binding.adminLAYContentReports.addView(retryRow("Couldn't load content reports. Tap to retry.") { loadPendingReviews() })
            return
        }
        if (reports.isEmpty()) {
            binding.adminLAYContentReports.addView(emptyText("No open content reports."))
            return
        }

        reports.forEach { report ->
            lateinit var cardView: View
            cardView = reviewCard(
                title = "${report.targetType.ifBlank { "content" }} report",
                body = listOf(
                    "Reporter: ${report.reporterId}",
                    "Target: ${report.targetId}",
                    "Owner: ${report.targetOwnerId.ifBlank { "Unknown" }}",
                    "Post: ${report.postId.ifBlank { "N/A" }}",
                    "Comment: ${report.commentId.ifBlank { "N/A" }}",
                    "Reason: ${report.reason.ifBlank { "Needs review" }}"
                ).joinToString("\n"),
                approveText = "Resolve",
                rejectText = "Keep open",
                onApprove = {
                    setCardBusy(cardView, true)
                    adminRepository.resolveContentReport(
                        report = report,
                        onSuccess = {
                            if (_binding == null) return@resolveContentReport
                            Toast.makeText(requireContext(), "Report resolved", Toast.LENGTH_SHORT).show()
                            reportCount = (reportCount - 1).coerceAtLeast(0)
                            removeCard(binding.adminLAYContentReports, cardView, "No open content reports.")
                        },
                        onFailure = { error -> onCardActionFailed(cardView, error) }
                    )
                },
                onReject = {
                    setCardBusy(cardView, true)
                    adminRepository.dismissContentReport(
                        report = report,
                        onSuccess = {
                            if (_binding == null) return@dismissContentReport
                            Toast.makeText(requireContext(), "Report dismissed - content stays up", Toast.LENGTH_SHORT).show()
                            reportCount = (reportCount - 1).coerceAtLeast(0)
                            removeCard(binding.adminLAYContentReports, cardView, "No open content reports.")
                        },
                        onFailure = { error -> onCardActionFailed(cardView, error) }
                    )
                }
            )
            binding.adminLAYContentReports.addView(cardView)
        }
    }

    private fun renderProfessionalApplications(applications: List<ProfessionalApplication>, failed: Boolean) {
        if (failed) {
            binding.adminLAYProfessionalApplications.addView(retryRow("Couldn't load professional applications. Tap to retry.") { loadPendingReviews() })
            return
        }
        if (applications.isEmpty()) {
            binding.adminLAYProfessionalApplications.addView(emptyText("No pending professional applications."))
            return
        }

        applications.forEach { application -> addProfessionalApplicationCard(application) }
    }

    // The applicant's name/photo live on their user profile, not the application document - fetch
    // it so the admin sees a person, not a raw Firebase uid. Falls back gracefully if the lookup
    // fails (deleted account, permission hiccup) rather than blocking the whole card.
    private fun addProfessionalApplicationCard(application: ProfessionalApplication) {
        userRepository.getUserByUid(
            uid = application.applicantUid,
            onSuccess = { user ->
                if (_binding == null) return@getUserByUid
                binding.adminLAYProfessionalApplications.addView(professionalApplicationCard(application, user))
            },
            onFailure = {
                if (_binding == null) return@getUserByUid
                binding.adminLAYProfessionalApplications.addView(professionalApplicationCard(application, null))
            }
        )
    }

    // Builds the card for one professional application: applicant photo/name, experience details,
    // any submitted verification documents, and the approve/reject buttons.
    private fun professionalApplicationCard(application: ProfessionalApplication, user: User?): View {
        val context = requireContext()
        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(R.drawable.bg_card)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(12) }
        }

        val fullName = user?.let { "${it.firstName} ${it.lastName}".trim() }.orEmpty()
            .ifBlank { application.requestedDisplayName }

        val header = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        header.addView(ImageView(context).apply {
            setBackgroundResource(R.drawable.bg_avatar)
            scaleType = ImageView.ScaleType.CENTER_CROP
            layoutParams = LinearLayout.LayoutParams(dp(44), dp(44)).apply { rightMargin = dp(10) }
            val photoUrl = user?.profileImageUrl.orEmpty()
            if (photoUrl.isNotBlank()) Glide.with(this@AdminReviewFragment).load(photoUrl).circleCrop().into(this)
        })
        header.addView(LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            addView(TextView(context).apply {
                text = fullName.ifBlank { "Unknown applicant" }
                setTextColor(context.getColor(R.color.flow_ink))
                textSize = 17f
                setTypeface(null, Typeface.BOLD)
            })
            addView(TextView(context).apply {
                text = listOf(
                    applicationTypeLabel(application.applicationType),
                    formatApplicationDate(application.createdAt)
                ).filter { it.isNotBlank() }.joinToString(" · ")
                setTextColor(context.getColor(R.color.flow_text_secondary))
                textSize = 13f
                setPadding(0, dp(2), 0, 0)
            })
        })
        card.addView(header)

        val bodyLines = listOfNotNull(
            application.experienceDetails.takeIf { it.isNotBlank() }?.let { "Experience: $it" }
        )
        if (bodyLines.isNotEmpty()) {
            card.addView(TextView(context).apply {
                text = bodyLines.joinToString("\n")
                setTextColor(context.getColor(R.color.flow_text_secondary))
                textSize = 14f
                setLineSpacing(3f, 1f)
                setPadding(0, dp(8), 0, 0)
            })
        }

        application.documents.forEach { url -> card.addView(documentCard(url)) }

        card.addView(
            buildActionsRow(
                approveText = "Approve",
                rejectText = "Reject",
                onApprove = {
                    setCardBusy(card, true)
                    adminRepository.approveProfessionalApplication(
                        application = application,
                        onSuccess = {
                            if (_binding == null) return@approveProfessionalApplication
                            Toast.makeText(requireContext(), "Application approved", Toast.LENGTH_SHORT).show()
                            applicationCount = (applicationCount - 1).coerceAtLeast(0)
                            removeCard(binding.adminLAYProfessionalApplications, card, "No pending professional applications.")
                        },
                        onFailure = { error -> onCardActionFailed(card, error) }
                    )
                },
                onReject = {
                    setCardBusy(card, true)
                    adminRepository.rejectProfessionalApplication(
                        application = application,
                        onSuccess = {
                            if (_binding == null) return@rejectProfessionalApplication
                            Toast.makeText(requireContext(), "Application rejected", Toast.LENGTH_SHORT).show()
                            applicationCount = (applicationCount - 1).coerceAtLeast(0)
                            removeCard(binding.adminLAYProfessionalApplications, card, "No pending professional applications.")
                        },
                        onFailure = { error -> onCardActionFailed(card, error) }
                    )
                }
            )
        )
        return card
    }

    // Approving/rejecting one card should never reset the admin's scroll position or re-fetch
    // everything - it just fades and drops that one card, then swaps in the empty state if that
    // was the last one left, and refreshes the pending-request summary count.
    private fun removeCard(container: LinearLayout, card: View, emptyMessage: String) {
        card.animate()
            .alpha(0f)
            .setDuration(180)
            .withEndAction {
                if (_binding == null) return@withEndAction
                container.removeView(card)
                if (container.childCount == 0) container.addView(emptyText(emptyMessage))
                renderSummary()
            }
            .start()
    }

    private fun onCardActionFailed(card: View, error: String) {
        if (!isAdded) return
        setCardBusy(card, false)
        Toast.makeText(requireContext(), error, Toast.LENGTH_SHORT).show()
    }

    private fun setCardBusy(card: View, busy: Boolean) {
        card.isEnabled = !busy
        card.alpha = if (busy) 0.6f else 1f
    }

    private fun formatApplicationDate(timestamp: Timestamp?): String {
        if (timestamp == null) return ""
        return SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(timestamp.seconds * 1000))
    }

    // Generic card shape reused by studio requests and content reports: a title, a body of
    // key/value lines, and an approve/reject action row.
    private fun reviewCard(
        title: String,
        body: String,
        approveText: String = "Approve",
        rejectText: String = "Reject",
        onApprove: () -> Unit,
        onReject: () -> Unit
    ): View {
        val context = requireContext()
        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(R.drawable.bg_card)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dp(12)
            }
        }

        card.addView(TextView(context).apply {
            text = title
            setTextColor(requireContext().getColor(R.color.flow_ink))
            textSize = 18f
            setTypeface(null, Typeface.BOLD)
        })

        card.addView(TextView(context).apply {
            text = body
            setTextColor(requireContext().getColor(R.color.flow_text_secondary))
            textSize = 14f
            setLineSpacing(3f, 1f)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(8)
            }
        })

        card.addView(buildActionsRow(approveText, rejectText, onApprove, onReject))
        return card
    }

    // The approve/reject button pair shared by every card type. Approve always confirms first
    // via a dialog since it's the harder action to undo; reject fires immediately.
    private fun buildActionsRow(
        approveText: String,
        rejectText: String,
        onApprove: () -> Unit,
        onReject: () -> Unit
    ): View {
        val context = requireContext()
        val actions = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(12)
            }
        }

        actions.addView(Button(context).apply {
            text = approveText
            setTextColor(context.getColor(R.color.white))
            setTypeface(null, Typeface.BOLD)
            setBackgroundResource(R.drawable.bg_flow_button_primary)
            layoutParams = LinearLayout.LayoutParams(0, dp(46), 1f)
            setOnClickListener {
                AlertDialog.Builder(context)
                    .setMessage("$approveText this request?")
                    .setNegativeButton("Cancel", null)
                    .setPositiveButton(approveText) { _, _ -> onApprove() }
                    .show()
            }
        })

        actions.addView(Button(context).apply {
            text = rejectText
            setTextColor(context.getColor(R.color.flow_brand))
            setBackgroundResource(R.drawable.bg_flow_button_secondary)
            layoutParams = LinearLayout.LayoutParams(0, dp(46), 1f).apply {
                leftMargin = dp(10)
            }
            setOnClickListener { onReject() }
        })

        return actions
    }

    // Friendly stand-in for a section that failed to load - never show a raw Firestore error
    // string to the admin. Tapping anywhere on the row retries the whole review load.
    private fun retryRow(message: String, onRetry: () -> Unit): View {
        val context = requireContext()
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            isClickable = true
            isFocusable = true
            setBackgroundResource(R.drawable.bg_flow_button_secondary)
            setPadding(dp(14), dp(12), dp(14), dp(12))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(12) }
            addView(TextView(context).apply {
                text = message
                setTextColor(context.getColor(R.color.flow_text_secondary))
                textSize = 14f
            })
            addView(TextView(context).apply {
                text = "Tap to retry"
                setTextColor(context.getColor(R.color.flow_brand))
                setTypeface(null, Typeface.BOLD)
                textSize = 13f
                setPadding(0, dp(6), 0, 0)
            })
            setOnClickListener { onRetry() }
        }
    }

    private fun emptyText(message: String): TextView {
        return TextView(requireContext()).apply {
            text = message
            setTextColor(requireContext().getColor(R.color.flow_text_muted))
            textSize = 14f
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dp(12)
            }
        }
    }

    // A submitted verification document used to render as a bare, unstyled URL - hard to use and
    // unprofessional for an admin reviewing credentials. Firebase Storage's own download URL
    // already encodes the original (sanitized) filename as its last path segment
    // (professionalApplications/{id}/documents/{fileName}) - decoding it out of the URL avoids
    // needing a separate filename field on ProfessionalApplication just to display this nicely.
    private fun documentCard(url: String): View {
        val context = requireContext()
        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundResource(R.drawable.bg_flow_input)
            setPadding(dp(10), dp(8), dp(10), dp(8))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(8) }

            addView(ImageView(context).apply {
                setImageResource(R.drawable.ic_document_24)
                setColorFilter(context.getColor(R.color.flow_brand))
                layoutParams = LinearLayout.LayoutParams(dp(22), dp(22)).apply { rightMargin = dp(10) }
            })
            addView(TextView(context).apply {
                text = documentFileNameFrom(url)
                setTextColor(context.getColor(R.color.flow_ink))
                textSize = 13f
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.MIDDLE
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            })
            addView(Button(context).apply {
                text = "View"
                isAllCaps = false
                minWidth = 0
                setTextColor(context.getColor(R.color.flow_brand))
                setBackgroundResource(R.drawable.bg_flow_button_secondary)
                setPadding(dp(14), 0, dp(14), 0)
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(34)).apply { marginStart = dp(8) }
                setOnClickListener { openDocument(url) }
            })
        }
    }

    private fun documentFileNameFrom(url: String): String {
        return runCatching {
            val withoutQuery = url.substringBefore("?")
            val objectPath = withoutQuery.substringAfterLast("/o/", withoutQuery)
            Uri.decode(objectPath).substringAfterLast("/").ifBlank { "Document" }
        }.getOrDefault("Document")
    }

    // Hands off to whatever app the device has for this file type (browser, PDF viewer, image
    // viewer) rather than building an in-app viewer - same "open externally, fail gracefully"
    // pattern already used for external links elsewhere in the app (e.g. DetailFragment.openUri).
    private fun openDocument(url: String) {
        runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
            .onFailure { Toast.makeText(requireContext(), "No app can open this document", Toast.LENGTH_SHORT).show() }
    }

    private fun applicationTypeLabel(type: String): String {
        return when {
            type.equals(Constants.ProfessionalApplicationType.VERIFIED_TEACHER.firestoreValue, ignoreCase = true) -> "Verified Teacher"
            type.equals(Constants.ProfessionalApplicationType.CHOREOGRAPHER.firestoreValue, ignoreCase = true) -> "Choreographer"
            else -> "Studio / Dance School"
        }
    }

    private fun setLoading(isLoading: Boolean) {
        binding.adminProgress.visibility = if (isLoading) View.VISIBLE else View.GONE
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private enum class AdminTab {
        STUDIO_REQUESTS,
        PROFESSIONAL_APPLICATIONS,
        CONTENT_REPORTS
    }
}
