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
            setBackgroundResource(R.drawable.bg_post_card)
            elevation = 2.dp().toFloat()
            isClickable = true
            isFocusable = true
            setPadding(14.dp(), 14.dp(), 14.dp(), 14.dp())
            setOnClickListener {
                subtleTap(this)
                onOpen(item)
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = 10.dp()
            }
        }

        val header = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
        }

        header.addView(LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)

            addView(TextView(context).apply {
                text = item.title
                maxLines = 2
                ellipsize = android.text.TextUtils.TruncateAt.END
                setTextColor(context.getColor(R.color.text_primary))
                textSize = 17f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
            })

            addView(TextView(context).apply {
                text = "${item.studio} · ${item.teacher}"
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
                setTextColor(context.getColor(R.color.text_secondary))
                textSize = 13f
                setPadding(0, 4.dp(), 0, 0)
            })
        })

        header.addView(TextView(context).apply {
            text = sourceLabel(item)
            gravity = android.view.Gravity.CENTER
            setTextColor(context.getColor(R.color.text_primary))
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            textSize = 10f
            setBackgroundResource(R.drawable.bg_chip)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                30.dp()
            ).apply {
                leftMargin = 10.dp()
            }
        })

        card.addView(header)

        card.addView(TextView(context).apply {
            text = itemMeta(item)
            setTextColor(context.getColor(R.color.text_muted))
            textSize = 13f
            maxLines = 2
            setPadding(0, 10.dp(), 0, 0)
        })

        card.addView(TextView(context).apply {
            text = explanation
            setTextColor(context.getColor(R.color.text_secondary))
            textSize = 12f
            maxLines = 2
            ellipsize = android.text.TextUtils.TruncateAt.END
            setPadding(0, 8.dp(), 0, 0)
        })

        val actions = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = 12.dp()
            }
        }

        actions.addView(Button(context).apply {
            text = "Open"
            isAllCaps = false
            setTextColor(context.getColor(R.color.text_primary))
            setBackgroundResource(R.drawable.bg_button_primary)
            setOnClickListener {
                subtleTap(this)
                onOpen(item)
            }
            layoutParams = LinearLayout.LayoutParams(0, 42.dp(), 1f)
        })

        actions.addView(Button(context).apply {
            val isSaved = DiscoveryRepository.isSaved(item)
            text = if (isSaved) "Saved" else "Save"
            isAllCaps = false
            isEnabled = !isSaved
            setTextColor(context.getColor(R.color.text_primary))
            setBackgroundResource(R.drawable.bg_button_secondary)
            setOnClickListener {
                subtleTap(this)
                onSave(item)
                text = "Saved"
                isEnabled = false
            }
            layoutParams = LinearLayout.LayoutParams(0, 42.dp(), 1f).apply {
                leftMargin = 10.dp()
            }
        })

        card.addView(actions)
        parent.addView(card)
    }

    private fun sourceLabel(item: DiscoveryItem): String {
        return when {
            item.source == DiscoveryItem.SOURCE_GOOGLE -> "GOOGLE"
            item.ownerUid.isNotBlank() || item.claimStatus.equals("CLAIMED", ignoreCase = true) -> "VERIFIED"
            else -> item.type.uppercase()
        }
    }

    private fun itemMeta(item: DiscoveryItem): String {
        val ratingText = item.rating?.let { rating ->
            val count = item.ratingCount?.let { " ($it)" }.orEmpty()
            "Rating ${"%.1f".format(rating)}$count"
        }
        val distanceText = item.distanceMeters?.let { meters ->
            if (meters >= 1000) "${"%.1f".format(meters / 1000.0)} km" else "${meters.toInt()} m"
        }
        return listOfNotNull(
            item.style.takeIf { it.isNotBlank() },
            item.level.takeIf { it.isNotBlank() },
            item.location.takeIf { it.isNotBlank() },
            item.time.takeIf { it.isNotBlank() },
            ratingText,
            distanceText
        ).joinToString(" / ")
    }

    private fun subtleTap(view: View) {
        view.animate()
            .scaleX(0.98f)
            .scaleY(0.98f)
            .setDuration(70)
            .withEndAction {
                view.animate().scaleX(1f).scaleY(1f).setDuration(90).start()
            }
            .start()
    }
}

// Converts dp units to pixels.
private fun Int.dp(): Int {
    return (this * android.content.res.Resources.getSystem().displayMetrics.density).toInt()
}
