package com.ana.theflow.ui.jobs

import android.graphics.Typeface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.ana.theflow.R

class JobsFragment : Fragment() {

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val context = requireContext()
        return ScrollView(context).apply {
            setBackgroundResource(R.drawable.bg_flow_screen)
            clipToPadding = false
            setPadding(18.dp(), 18.dp(), 18.dp(), 96.dp())
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                addView(TextView(context).apply {
                    text = "Jobs"
                    setTextColor(context.getColor(R.color.flow_ink))
                    textSize = 28f
                    setTypeface(typeface, Typeface.BOLD)
                })
                addView(TextView(context).apply {
                    text = "Coming soon"
                    setTextColor(context.getColor(R.color.flow_brand))
                    textSize = 22f
                    setTypeface(typeface, Typeface.BOLD)
                    setPadding(0, 28.dp(), 0, 0)
                })
                addView(TextView(context).apply {
                    text = "מחפשים עבודה כרקדנים? בקרוב ב-THE FLOW"
                    setTextColor(context.getColor(R.color.flow_text_secondary))
                    textSize = 16f
                    setPadding(0, 8.dp(), 0, 0)
                })
            })
        }
    }
}

private fun Int.dp(): Int {
    return (this * android.content.res.Resources.getSystem().displayMetrics.density).toInt()
}
