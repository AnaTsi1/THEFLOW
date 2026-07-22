package com.ana.theflow.ui.common

import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.ana.theflow.R
import com.ana.theflow.data.model.discovery.DiscoveryItem
import com.ana.theflow.data.repository.DiscoveryRepository

object DiscoveryCardRenderer {

    // Adds a discovery item card to a parent layout.
    fun addItemCard(
        parent: LinearLayout,
        item: DiscoveryItem,
        explanation: String,
        onOpen: (DiscoveryItem) -> Unit,
        onSave: (DiscoveryItem) -> Unit
    ) {
        val context = parent.context
        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(R.drawable.bg_card_highlight)
            setPadding(18.dp(), 18.dp(), 18.dp(), 18.dp())
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = 12.dp()
            }
        }

        card.addView(TextView(context).apply {
            text = item.title
            setTextColor(context.getColor(R.color.text_primary))
            textSize = 19f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        })

        card.addView(TextView(context).apply {
            text = "${item.studio} / ${item.teacher}"
            setTextColor(context.getColor(R.color.text_secondary))
            textSize = 14f
            setPadding(0, 6.dp(), 0, 0)
        })

        card.addView(TextView(context).apply {
            text = "${item.style} / ${item.level} / ${item.location} / ${item.time}"
            setTextColor(context.getColor(R.color.text_muted))
            textSize = 13f
            setPadding(0, 8.dp(), 0, 0)
        })

        card.addView(TextView(context).apply {
            text = explanation
            setTextColor(context.getColor(R.color.neon_pink))
            textSize = 13f
            setPadding(0, 10.dp(), 0, 0)
        })

        card.addView(TextView(context).apply {
            text = item.type.uppercase()
            gravity = android.view.Gravity.CENTER
            setTextColor(context.getColor(R.color.text_primary))
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setBackgroundResource(R.drawable.bg_media_gradient)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                96.dp()
            ).apply {
                topMargin = 14.dp()
            }
        })

        val actions = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = 14.dp()
            }
        }

        actions.addView(Button(context).apply {
            text = "Open"
            setTextColor(context.getColor(R.color.text_primary))
            setBackgroundResource(R.drawable.bg_button_primary)
            setOnClickListener { onOpen(item) }
            layoutParams = LinearLayout.LayoutParams(0, 48.dp(), 1f)
        })

        actions.addView(Button(context).apply {
            val isSaved = DiscoveryRepository.isSaved(item)
            text = if (isSaved) "Saved" else "Save"
            isEnabled = !isSaved
            setTextColor(context.getColor(R.color.text_primary))
            setBackgroundResource(R.drawable.bg_button_secondary)
            setOnClickListener {
                onSave(item)
                text = "Saved"
                isEnabled = false
            }
            layoutParams = LinearLayout.LayoutParams(0, 48.dp(), 1f).apply {
                leftMargin = 10.dp()
            }
        })

        card.addView(actions)
        parent.addView(card)
    }
}

// Converts dp units to pixels.
private fun Int.dp(): Int {
    return (this * android.content.res.Resources.getSystem().displayMetrics.density).toInt()
}
