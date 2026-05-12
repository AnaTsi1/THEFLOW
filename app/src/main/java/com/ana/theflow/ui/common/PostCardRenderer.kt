package com.ana.theflow.ui.common

import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.ana.theflow.R
import com.ana.theflow.data.model.post.Post
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object PostCardRenderer {

    fun addPostCard(
        parent: LinearLayout,
        post: Post,
        onOpen: ((Post) -> Unit)? = null
    ) {
        val context = parent.context
        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(R.drawable.bg_card)
            isClickable = onOpen != null
            isFocusable = onOpen != null
            setOnClickListener { onOpen?.invoke(post) }
            setPadding(18.dp(), 18.dp(), 18.dp(), 18.dp())
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = 12.dp()
            }
        }

        card.addView(TextView(context).apply {
            text = post.authorName.ifBlank { "Dancer" }
            setTextColor(context.getColor(R.color.text_primary))
            textSize = 17f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        })

        card.addView(TextView(context).apply {
            text = "${post.authorType.ifBlank { "dancer" }} / ${formatTimestamp(post)}"
            setTextColor(context.getColor(R.color.text_muted))
            textSize = 12f
            setPadding(0, 4.dp(), 0, 0)
        })

        card.addView(TextView(context).apply {
            text = post.text
            setTextColor(context.getColor(R.color.text_secondary))
            textSize = 15f
            setPadding(0, 14.dp(), 0, 0)
        })

        if (post.mediaUrls.isNotEmpty()) {
            card.addView(TextView(context).apply {
                text = post.mediaType.ifBlank { "MEDIA" }.uppercase()
                gravity = android.view.Gravity.CENTER
                setTextColor(context.getColor(R.color.text_primary))
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                setBackgroundResource(R.drawable.bg_media_gradient)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    140.dp()
                ).apply {
                    topMargin = 14.dp()
                }
            })
        }

        card.addView(TextView(context).apply {
            text = "${post.likesCount} likes / ${post.commentsCount} comments"
            setTextColor(context.getColor(R.color.text_muted))
            textSize = 13f
            setPadding(0, 14.dp(), 0, 0)
        })

        parent.addView(card)
    }

    private fun formatTimestamp(post: Post): String {
        val createdAt = post.createdAt ?: return "just now"
        return SimpleDateFormat("MMM d, HH:mm", Locale.getDefault())
            .format(Date(createdAt.seconds * 1000))
    }
}

private fun Int.dp(): Int {
    return (this * android.content.res.Resources.getSystem().displayMetrics.density).toInt()
}
