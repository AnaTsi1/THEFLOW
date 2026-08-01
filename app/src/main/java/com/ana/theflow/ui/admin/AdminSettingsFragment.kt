// Admin landing screen reached from Settings - splits admin work into "Approve Requests" (studio
// requests, professional applications, content reports) and "Manage User Permissions" (direct
// grant/revoke), instead of cramming both into one screen.
package com.ana.theflow.ui.admin

import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.ana.theflow.MainActivity
import com.ana.theflow.R
import com.ana.theflow.data.repository.AdminRepository
import com.ana.theflow.ui.settings.SettingsUi
import com.ana.theflow.ui.settings.dp

class AdminSettingsFragment : Fragment() {

    private val adminRepository = AdminRepository()
    private lateinit var content: LinearLayout
    private var pendingCount: Int? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val root = SettingsUi.screen(this, "Admin")
        val scroll = SettingsUi.contentScroll(requireContext())
        content = SettingsUi.contentColumn(requireContext())
        scroll.addView(content)
        root.addView(scroll)
        render()
        return root
    }

    // Refreshes the pending-request badge every time this screen comes back into view, so an
    // admin who just cleared the queue elsewhere sees an up-to-date count.
    override fun onResume() {
        super.onResume()
        loadPendingCount()
    }

    // Loads the current pending-request total to show as a badge on the Approve Requests row.
    private fun loadPendingCount() {
        adminRepository.loadPendingReviews(
            onSuccess = { data ->
                if (!isAdded) return@loadPendingReviews
                pendingCount = data.studioRequests.size + data.professionalApplications.size + data.contentReports.size
                render()
            },
            onFailure = {
                if (!isAdded) return@loadPendingReviews
                pendingCount = null
                render()
            }
        )
    }

    // Builds the three admin entry rows.
    private fun render() {
        content.removeAllViews()
        content.addView(approveRequestsRow())
        content.addView(
            SettingsUi.row(
                context = requireContext(),
                title = "Manage User Permissions",
                description = "Grant or revoke teacher, choreographer, and studio-manager access.",
                iconRes = R.drawable.ic_switch_24,
                onClick = { (requireActivity() as MainActivity).openAdminUserPermissions() }
            )
        )
        content.addView(
            SettingsUi.row(
                context = requireContext(),
                title = "Recommendation Insights",
                description = "See any user's real learned profile and a live preview of their Home/Discover feeds.",
                iconRes = R.drawable.ic_discover_24,
                onClick = { (requireActivity() as MainActivity).openAdminRecommendationInsights() }
            )
        )
    }

    // The "Approve Requests" row, with a numbered badge when there's a nonzero pending count.
    private fun approveRequestsRow(): View {
        val context = requireContext()
        val count = pendingCount
        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            isClickable = true
            isFocusable = true
            setBackgroundResource(R.drawable.bg_flow_card)
            setPadding(14.dp(), 12.dp(), 14.dp(), 12.dp())
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 10.dp() }

            addView(ImageView(context).apply {
                setImageResource(R.drawable.ic_check_circle_24)
                setColorFilter(context.getColor(R.color.flow_brand))
                layoutParams = LinearLayout.LayoutParams(22.dp(), 22.dp()).apply { rightMargin = 14.dp() }
            })
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                addView(TextView(context).apply {
                    text = "Approve Requests"
                    setTextColor(context.getColor(R.color.flow_ink))
                    textSize = 16f
                    setTypeface(typeface, Typeface.BOLD)
                })
                addView(TextView(context).apply {
                    text = "Studio requests, professional applications, and content reports."
                    setTextColor(context.getColor(R.color.flow_text_secondary))
                    textSize = 12f
                    setPadding(0, 3.dp(), 0, 0)
                })
            })
            if (count != null && count > 0) {
                addView(TextView(context).apply {
                    text = count.coerceAtMost(99).toString()
                    setBackgroundResource(R.drawable.bg_badge)
                    setTextColor(context.getColor(R.color.white))
                    textSize = 12f
                    setTypeface(typeface, Typeface.BOLD)
                    gravity = Gravity.CENTER
                    minWidth = 20.dp()
                    layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, 20.dp()).apply {
                        rightMargin = 8.dp()
                    }
                })
            }
            addView(TextView(context).apply {
                text = ">"
                setTextColor(context.getColor(R.color.flow_brand))
                textSize = 20f
                setTypeface(typeface, Typeface.BOLD)
            })
            setOnClickListener { (requireActivity() as MainActivity).openAdminReview() }
        }
    }
}
