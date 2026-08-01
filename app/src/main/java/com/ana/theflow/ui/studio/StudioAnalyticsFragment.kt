package com.ana.theflow.ui.studio

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
import com.ana.theflow.R
import com.ana.theflow.ui.settings.SettingsUi
import com.ana.theflow.ui.settings.dp

// A studio-wide dashboard (jobs posted, applicants, engagement) isn't built yet - applicants are
// still only reachable per-job from JobDetailFragment. This gives managers a real, findable entry
// point instead of the feature looking entirely missing.
class StudioAnalyticsFragment : Fragment() {

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val root = SettingsUi.screen(this, "Studio Analytics")
        val scroll = SettingsUi.contentScroll(requireContext())
        val content = SettingsUi.contentColumn(requireContext())
        content.addView(comingSoonCard())
        scroll.addView(content)
        root.addView(scroll)
        return root
    }

    private fun comingSoonCard(): View {
        val context = requireContext()
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setBackgroundResource(R.drawable.bg_flow_card)
            setPadding(20.dp(), 36.dp(), 20.dp(), 36.dp())
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )

            addView(ImageView(context).apply {
                setImageResource(R.drawable.ic_work_24)
                setColorFilter(context.getColor(R.color.flow_brand))
                layoutParams = LinearLayout.LayoutParams(52.dp(), 52.dp())
            })
            addView(TextView(context).apply {
                text = "Coming Soon"
                setTextColor(context.getColor(R.color.flow_ink))
                textSize = 20f
                setTypeface(typeface, Typeface.BOLD)
                setPadding(0, 16.dp(), 0, 6.dp())
            })
            addView(TextView(context).apply {
                text = "A studio-wide view of your posted jobs, applicants, and engagement is on the way.\n\nFor now, open a job from your studio profile to see its applicants."
                setTextColor(context.getColor(R.color.flow_text_secondary))
                textSize = 14f
                gravity = Gravity.CENTER
                setLineSpacing(3.dp().toFloat(), 1f)
            })
        }
    }
}
