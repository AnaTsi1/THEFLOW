package com.ana.theflow.ui.common

import android.view.View
import android.text.Editable
import android.text.TextWatcher
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.TextView
import com.bumptech.glide.Glide
import com.ana.theflow.R
import com.ana.theflow.data.model.post.Post
import com.ana.theflow.data.model.post.PostComment
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object PostCardRenderer {

    // Adds a post card to a parent layout.
    fun addPostCard(
        parent: LinearLayout,
        post: Post,
        comments: List<PostComment> = emptyList(),
        isLiked: Boolean = false,
        canEdit: Boolean = false,
        onOpen: ((Post) -> Unit)? = null,
        onLike: ((Post) -> Unit)? = null,
        onComment: ((Post, String) -> Unit)? = null,
        onEdit: ((Post) -> Unit)? = null,
        onDelete: ((Post) -> Unit)? = null,
        onMediaOpen: ((String, String) -> Unit)? = null
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

        addTypedPostContent(card, post)

        if (post.mediaItems.any { it.visibleInMedia && it.url.isNotBlank() } ||
            post.mediaUrls.any { it.isNotBlank() }
        ) {
            addPostMedia(card, post, onMediaOpen)
        }

        addActionRow(
            card = card,
            post = post,
            isLiked = isLiked,
            canEdit = canEdit,
            likesCount = post.likesCount,
            commentsCount = if (comments.isNotEmpty()) comments.size.toLong() else post.commentsCount,
            onLike = onLike,
            onEdit = onEdit,
            onDelete = onDelete
        )
        addComments(card, comments)
        addCommentComposer(card, post, onComment)

        parent.addView(card)
    }

    // Adds the body content that matches the post type.
    private fun addTypedPostContent(card: LinearLayout, post: Post) {
        when (post.postType) {
            POST_TYPE_DANCE_ACTIVITY -> addDanceActivityContent(card, post)
            POST_TYPE_COLLABORATION -> addCollaborationContent(card, post)
            else -> addBodyText(card, post.text)
        }
    }

    // Adds activity-specific details to a post card.
    private fun addDanceActivityContent(card: LinearLayout, post: Post) {
        val title = post.activityType.ifBlank { "Dance Activity" }
        addSectionTitle(card, title)
        addBodyText(card, post.text)
        addDetailLines(
            card = card,
            lines = listOfNotNull(
                detailLine("Location", post.activityLocation),
                detailLine("Date", post.activityDate),
                detailLine("Time", post.activityTime),
                detailLine("Price", post.activityPrice),
                detailLine("Level", post.activityLevel),
                detailLine("Description", post.activityDescription)
            )
        )
    }

    // Adds collaboration-specific details to a post card.
    private fun addCollaborationContent(card: LinearLayout, post: Post) {
        val title = post.collaborationLookingFor.ifBlank { "Collaboration" }
        addSectionTitle(card, "Looking for: $title")
        addBodyText(card, post.text)
        addDetailLines(
            card = card,
            lines = listOfNotNull(
                detailLine("Style", post.collaborationStyle),
                detailLine("Location", post.collaborationLocation),
                detailLine("Deadline", post.collaborationDate),
                detailLine("Compensation", post.collaborationPaid),
                detailLine("Description", post.collaborationDescription)
            )
        )
    }

    // Adds normal post text when it exists.
    private fun addBodyText(card: LinearLayout, textValue: String) {
        if (textValue.isBlank()) return
        val context = card.context
        card.addView(TextView(context).apply {
            text = textValue
            setTextColor(context.getColor(R.color.text_secondary))
            textSize = 15f
            setPadding(0, 14.dp(), 0, 0)
        })
    }

    // Adds a compact heading inside a post card.
    private fun addSectionTitle(card: LinearLayout, title: String) {
        val context = card.context
        card.addView(TextView(context).apply {
            text = title
            setTextColor(context.getColor(R.color.text_primary))
            textSize = 16f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setPadding(0, 14.dp(), 0, 0)
        })
    }

    // Adds metadata lines for structured post cards.
    private fun addDetailLines(card: LinearLayout, lines: List<String>) {
        if (lines.isEmpty()) return
        val context = card.context
        card.addView(TextView(context).apply {
            text = lines.joinToString("\n")
            setTextColor(context.getColor(R.color.text_secondary))
            textSize = 14f
            setPadding(0, 10.dp(), 0, 0)
            setLineSpacing(3.dp().toFloat(), 1f)
        })
    }

    // Formats one structured post detail line.
    private fun detailLine(label: String, value: String): String? {
        return value.ifBlank { null }?.let { "$label: $it" }
    }

    // Adds the selected post media preview to a card.
    private fun addPostMedia(card: LinearLayout, post: Post, onMediaOpen: ((String, String) -> Unit)?) {
        val context = card.context
        val firstMedia = firstVisibleMedia(post) ?: return
        if (firstMedia.second == MEDIA_TYPE_PHOTO || firstMedia.second == MEDIA_TYPE_MEDIA) {
            val imageView = ImageView(context).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP
                setBackgroundResource(R.drawable.bg_media_gradient)
                setOnClickListener {
                    onMediaOpen?.invoke(firstMedia.first, firstMedia.second)
                }
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    180.dp()
                ).apply {
                    topMargin = 14.dp()
                }
            }
            card.addView(imageView)
            Glide.with(context)
                .load(firstMedia.first)
                .centerCrop()
                .into(imageView)
            return
        }

        card.addView(TextView(context).apply {
            text = "Video attached"
            gravity = android.view.Gravity.CENTER
            setOnClickListener {
                onMediaOpen?.invoke(firstMedia.first, firstMedia.second)
            }
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

    // Adds icon actions for like, comment, and post options.
    private fun addActionRow(
        card: LinearLayout,
        post: Post,
        isLiked: Boolean,
        canEdit: Boolean,
        likesCount: Long,
        commentsCount: Long,
        onLike: ((Post) -> Unit)?,
        onEdit: ((Post) -> Unit)?,
        onDelete: ((Post) -> Unit)?
    ) {
        val context = card.context
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 12.dp(), 0, 0)
            gravity = android.view.Gravity.CENTER_VERTICAL
        }
        row.addView(LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)

            addView(createActionCluster(
                context = context,
                iconRes = R.drawable.ic_heart_24,
                iconTint = if (isLiked) R.color.neon_pink else R.color.text_primary,
                count = likesCount,
                contentDescription = if (isLiked) "Unlike" else "Like",
                onClick = { onLike?.invoke(post) }
            ))

            addView(createActionCluster(
                context = context,
                iconRes = R.drawable.ic_comment_24,
                iconTint = R.color.text_primary,
                count = commentsCount,
                contentDescription = "Comment",
                onClick = {
                    card.findViewWithTag<EditText>(commentInputTag(post.postId))?.requestFocus()
                }
            ))
        })
        if (canEdit) {
            row.addView(ImageButton(context).apply {
                contentDescription = "Post options"
                setImageResource(R.drawable.ic_more_horizontal_24)
                setColorFilter(context.getColor(R.color.text_primary))
                setBackgroundResource(R.drawable.bg_button_secondary)
                layoutParams = LinearLayout.LayoutParams(38.dp(), 38.dp())
                scaleType = ImageView.ScaleType.CENTER
                setOnClickListener { anchor ->
                    showPostOptions(anchor, post, onEdit, onDelete)
                }
            })
        }
        card.addView(row)
    }

    // Builds a compact icon-plus-count action.
    private fun createActionCluster(
        context: android.content.Context,
        iconRes: Int,
        iconTint: Int,
        count: Long,
        contentDescription: String,
        onClick: () -> Unit
    ): LinearLayout {
        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            isClickable = true
            isFocusable = true
            setPadding(0, 0, 12.dp(), 0)
            setOnClickListener { onClick() }

            addView(ImageButton(context).apply {
                this.contentDescription = contentDescription
                setImageResource(iconRes)
                setColorFilter(context.getColor(iconTint))
                setBackgroundColor(android.graphics.Color.TRANSPARENT)
                layoutParams = LinearLayout.LayoutParams(24.dp(), 24.dp())
                scaleType = ImageView.ScaleType.CENTER
            })

            addView(TextView(context).apply {
                text = count.toString()
                setTextColor(context.getColor(R.color.text_muted))
                textSize = 11f
                setPadding(3.dp(), 0, 0, 0)
            })
        }
    }

    // Opens the edit/delete menu for an owned post.
    private fun showPostOptions(
        anchor: View,
        post: Post,
        onEdit: ((Post) -> Unit)?,
        onDelete: ((Post) -> Unit)?
    ) {
        PopupMenu(anchor.context, anchor).apply {
            menu.add("Edit post")
            menu.add("Delete post")
            setOnMenuItemClickListener { item ->
                when (item.title.toString()) {
                    "Edit post" -> {
                        onEdit?.invoke(post)
                        true
                    }
                    "Delete post" -> {
                        onDelete?.invoke(post)
                        true
                    }
                    else -> false
                }
            }
            show()
        }
    }

    // Adds loaded comments below a post.
    private fun addComments(card: LinearLayout, comments: List<PostComment>) {
        if (comments.isEmpty()) return
        val context = card.context
        comments.takeLast(5).forEach { comment ->
            card.addView(TextView(context).apply {
                text = "${comment.authorName.ifBlank { "Dancer" }}: ${comment.text}"
                setTextColor(context.getColor(R.color.text_secondary))
                textSize = 13f
                setPadding(0, 8.dp(), 0, 0)
            })
        }
    }

    // Adds a comment input to the card.
    private fun addCommentComposer(
        card: LinearLayout,
        post: Post,
        onComment: ((Post, String) -> Unit)?
    ) {
        val context = card.context
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 10.dp(), 0, 0)
            gravity = android.view.Gravity.CENTER_VERTICAL
        }
        val input = EditText(context).apply {
            tag = commentInputTag(post.postId)
            hint = "Write a comment..."
            setTextColor(context.getColor(R.color.text_primary))
            setHintTextColor(context.getColor(R.color.text_muted))
            setBackgroundResource(R.drawable.bg_input)
            layoutParams = LinearLayout.LayoutParams(0, 46.dp(), 1f).apply {
                rightMargin = 8.dp()
            }
        }
        row.addView(input)
        val sendButton = ImageButton(context).apply {
            contentDescription = "Send comment"
            setImageResource(R.drawable.ic_send_24)
            setColorFilter(context.getColor(R.color.text_primary))
            setBackgroundResource(R.drawable.bg_button_secondary)
            layoutParams = LinearLayout.LayoutParams(40.dp(), 40.dp())
            scaleType = ImageView.ScaleType.CENTER
            alpha = 0.45f
            isEnabled = false
        }
        fun updateState() {
            val hasText = input.text?.isNotBlank() == true
            sendButton.isEnabled = hasText && onComment != null
            sendButton.alpha = if (sendButton.isEnabled) 1f else 0.45f
        }
        input.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                updateState()
            }
        })
        sendButton.setOnClickListener {
            val message = input.text.toString().trim()
            if (message.isBlank()) return@setOnClickListener
            onComment?.invoke(post, message)
            input.text?.clear()
        }
        updateState()
        row.addView(sendButton)
        card.addView(row)
    }

    // Returns a stable tag for a post comment input.
    private fun commentInputTag(postId: String): String {
        return "comment_input_$postId"
    }

    // Returns the first visible media url and type for a post.
    private fun firstVisibleMedia(post: Post): Pair<String, String>? {
        val item = post.mediaItems.firstOrNull { it.visibleInMedia && it.url.isNotBlank() }
        if (item != null) return item.url to item.mediaType
        val legacyUrl = post.mediaUrls.firstOrNull { it.isNotBlank() } ?: return null
        return legacyUrl to post.mediaType.ifBlank { MEDIA_TYPE_PHOTO }
    }

    // Formats a post timestamp for display.
    private fun formatTimestamp(post: Post): String {
        val createdAt = post.createdAt ?: return "just now"
        return SimpleDateFormat("MMM d, HH:mm", Locale.getDefault())
            .format(Date(createdAt.seconds * 1000))
    }

    private const val POST_TYPE_DANCE_ACTIVITY = "dance_activity"
    private const val POST_TYPE_COLLABORATION = "collaboration"
    private const val MEDIA_TYPE_PHOTO = "photo"
    private const val MEDIA_TYPE_MEDIA = "media"
}

// Converts dp units to pixels.
private fun Int.dp(): Int {
    return (this * android.content.res.Resources.getSystem().displayMetrics.density).toInt()
}
