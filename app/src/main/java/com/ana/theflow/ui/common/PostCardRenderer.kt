// Builds reusable post cards for feeds, profiles, and detail screens.
package com.ana.theflow.ui.common

import android.app.AlertDialog
import android.content.Intent
import android.text.Editable
import android.text.TextUtils
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.ana.theflow.R
import com.ana.theflow.data.model.post.Post
import com.ana.theflow.data.model.post.PostComment
import com.bumptech.glide.Glide
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Renders post UI while delegating persistence actions back to the hosting screen.
object PostCardRenderer {
    enum class CardStyle {
        DARK,
        FLOW_LIGHT
    }

    // Adds one post card to the supplied parent and wires the available interaction callbacks.
    fun addPostCard(
        parent: LinearLayout,
        post: Post,
        comments: List<PostComment> = emptyList(),
        isLiked: Boolean = false,
        isSaved: Boolean = false,
        isEventRegistered: Boolean = false,
        canEdit: Boolean = false,
        currentUserId: String = "",
        onOpen: ((Post) -> Unit)? = null,
        onLike: ((Post) -> Unit)? = null,
        onSave: ((Post) -> Unit)? = null,
        onComment: ((Post, String) -> Unit)? = null,
        onEditComment: ((PostComment, String) -> Unit)? = null,
        onDeleteComment: ((PostComment) -> Unit)? = null,
        onLikeComment: ((PostComment) -> Unit)? = null,
        onReplyComment: ((PostComment, String) -> Unit)? = null,
        onReportComment: ((PostComment) -> Unit)? = null,
        onEventRegister: ((Post) -> Unit)? = null,
        onReport: ((Post) -> Unit)? = null,
        onHide: ((Post) -> Unit)? = null,
        onEdit: ((Post) -> Unit)? = null,
        onDelete: ((Post) -> Unit)? = null,
        onMediaOpen: ((String, String) -> Unit)? = null,
        onAuthorOpen: ((String) -> Unit)? = null,
        cardStyle: CardStyle = CardStyle.DARK
    ) {
        val context = parent.context
        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(cardBackground(cardStyle))
            elevation = if (cardStyle == CardStyle.FLOW_LIGHT) 0.dp().toFloat() else 2.dp().toFloat()
            isClickable = onOpen != null
            isFocusable = onOpen != null
            setOnClickListener {
                subtleTap(this)
                onOpen?.invoke(post)
            }
            val horizontalPadding = if (cardStyle == CardStyle.FLOW_LIGHT) 16.dp() else 14.dp()
            setPadding(horizontalPadding, 14.dp(), horizontalPadding, 12.dp())
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = 12.dp()
            }
        }

        addHeader(card, post, canEdit, onAuthorOpen, onReport, onHide, onEdit, onDelete, cardStyle)
        if (post.postType == POST_TYPE_DANCE_ACTIVITY) {
            addEventContent(card, post, isEventRegistered, onEventRegister, onMediaOpen, cardStyle)
        } else {
            addBodyText(card, post.text, cardStyle)
            addPostMedia(card, post, onMediaOpen, cardStyle)
            if (post.postType == POST_TYPE_COLLABORATION) addCollaborationDetails(card, post, cardStyle)
        }
        addActionRow(card, post, comments, isLiked, isSaved, currentUserId, onLike, onSave, onComment, onEditComment, onDeleteComment, onLikeComment, onReplyComment, onReportComment, cardStyle)
        addEngagementFooter(card, post, comments, currentUserId, onComment, onEditComment, onDeleteComment, onLikeComment, onReplyComment, onReportComment, cardStyle)

        parent.addView(card)
    }

    private fun addHeader(
        card: LinearLayout,
        post: Post,
        canEdit: Boolean,
        onAuthorOpen: ((String) -> Unit)?,
        onReport: ((Post) -> Unit)?,
        onHide: ((Post) -> Unit)?,
        onEdit: ((Post) -> Unit)?,
        onDelete: ((Post) -> Unit)?,
        cardStyle: CardStyle
    ) {
        val context = card.context
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val openAuthor = View.OnClickListener {
            subtleTap(it)
            if (post.authorId.isNotBlank()) onAuthorOpen?.invoke(post.authorId)
        }

        row.addView(ImageView(context).apply {
            setBackgroundResource(R.drawable.bg_avatar)
            scaleType = ImageView.ScaleType.CENTER_CROP
            contentDescription = context.getString(R.string.post_author_photo)
            isClickable = post.authorId.isNotBlank() && onAuthorOpen != null
            isFocusable = isClickable
            setOnClickListener(openAuthor)
            layoutParams = LinearLayout.LayoutParams(42.dp(), 42.dp()).apply {
                rightMargin = 10.dp()
            }
            if (post.authorProfileImageUrl.isNotBlank()) {
                Glide.with(context).load(post.authorProfileImageUrl).circleCrop().into(this)
            }
        })

        row.addView(LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)

            addView(TextView(context).apply {
                text = post.authorName.ifBlank { context.getString(R.string.post_fallback_author) }
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                setTextColor(context.getColor(primaryTextColor(cardStyle)))
                textSize = 15f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                isClickable = post.authorId.isNotBlank() && onAuthorOpen != null
                isFocusable = isClickable
                setOnClickListener(openAuthor)
            })

            addView(TextView(context).apply {
                text = "${post.authorType.ifBlank { "dancer" }} / ${formatTimestamp(post)}"
                setTextColor(context.getColor(secondaryTextColor(cardStyle)))
                textSize = 12f
                setPadding(0, 2.dp(), 0, 0)
            })
        })

        row.addView(ImageButton(context).apply {
            contentDescription = context.getString(R.string.post_options)
            setImageResource(R.drawable.ic_more_horizontal_24)
            setColorFilter(context.getColor(secondaryTextColor(cardStyle)))
            setBackgroundResource(iconButtonBackground(cardStyle))
            layoutParams = LinearLayout.LayoutParams(44.dp(), 44.dp())
            scaleType = ImageView.ScaleType.CENTER
            alpha = if (canEdit) 1f else 0.55f
            visibility = if (canEdit || onHide != null || onReport != null) View.VISIBLE else View.INVISIBLE
            setOnClickListener { anchor ->
                subtleTap(this)
                showPostOptions(anchor, post, canEdit, onReport, onHide, onEdit, onDelete)
            }
        })

        card.addView(row)
    }

    private fun addBodyText(card: LinearLayout, textValue: String, cardStyle: CardStyle) {
        if (textValue.isBlank()) return
        val context = card.context
        val body = TextView(context).apply {
            text = textValue
            setTextColor(context.getColor(primaryTextColor(cardStyle)))
            textSize = 15f
            setLineSpacing(3.dp().toFloat(), 1f)
            setPadding(0, 12.dp(), 0, 0)
            maxLines = if (textValue.length > LONG_TEXT_THRESHOLD) 4 else Int.MAX_VALUE
            ellipsize = if (textValue.length > LONG_TEXT_THRESHOLD) TextUtils.TruncateAt.END else null
        }
        card.addView(body)

        if (textValue.length <= LONG_TEXT_THRESHOLD) return
        card.addView(TextView(context).apply {
            text = context.getString(R.string.post_read_more)
            setTextColor(context.getColor(brandColor(cardStyle)))
            textSize = 13f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setPadding(0, 6.dp(), 0, 0)
            isClickable = true
            isFocusable = true
            setOnClickListener {
                body.maxLines = Int.MAX_VALUE
                body.ellipsize = null
                visibility = View.GONE
            }
        })
    }

    private fun addPostMedia(card: LinearLayout, post: Post, onMediaOpen: ((String, String) -> Unit)?, cardStyle: CardStyle) {
        val firstMedia = firstVisibleMedia(post) ?: return
        card.addView(mediaFrame(card, firstMedia.first, firstMedia.second, onMediaOpen, cardStyle))
    }

    private fun addEventContent(
        card: LinearLayout,
        post: Post,
        isEventRegistered: Boolean,
        onEventRegister: ((Post) -> Unit)?,
        onMediaOpen: ((String, String) -> Unit)?,
        cardStyle: CardStyle
    ) {
        val context = card.context
        val firstMedia = firstVisibleMedia(post)
        if (firstMedia != null) {
            card.addView(mediaFrame(card, firstMedia.first, firstMedia.second, onMediaOpen, cardStyle))
        }

        card.addView(LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(eventBackground(cardStyle))
            setPadding(12.dp(), 12.dp(), 12.dp(), 12.dp())
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = if (firstMedia == null) 12.dp() else 10.dp()
            }

            addView(TextView(context).apply {
                text = post.activityType.ifBlank { context.getString(R.string.post_event_title) }
                setTextColor(context.getColor(primaryTextColor(cardStyle)))
                textSize = 18f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
            })

            addView(TextView(context).apply {
                text = listOf(
                    post.activityDate,
                    post.activityTime,
                    post.activityLocation
                ).filter { it.isNotBlank() }.joinToString(" / ")
                setTextColor(context.getColor(secondaryTextColor(cardStyle)))
                textSize = 13f
                setPadding(0, 6.dp(), 0, 0)
            })

            addView(TextView(context).apply {
                text = eventCapacityLine(post)
                setTextColor(context.getColor(secondaryTextColor(cardStyle)))
                textSize = 13f
                setPadding(0, 6.dp(), 0, 0)
                visibility = if (text.isBlank()) View.GONE else View.VISIBLE
            })

            addBodyText(this, post.activityDescription.ifBlank { post.text }, cardStyle)

            addView(Button(context).apply {
                text = context.getString(
                    if (isEventRegistered) R.string.post_registered else R.string.post_register
                )
                setTextColor(context.getColor(if (isEventRegistered && cardStyle == CardStyle.FLOW_LIGHT) R.color.flow_brand else primaryTextColor(cardStyle)))
                setBackgroundResource(
                    if (cardStyle == CardStyle.FLOW_LIGHT) {
                        if (isEventRegistered) R.drawable.bg_flow_button_secondary else R.drawable.bg_flow_button_primary
                    } else {
                        if (isEventRegistered) R.drawable.bg_button_secondary else R.drawable.bg_button_lilac
                    }
                )
                minHeight = 0
                minWidth = 0
                setPadding(16.dp(), 8.dp(), 16.dp(), 8.dp())
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    38.dp()
                ).apply {
                    topMargin = 10.dp()
                }
                setOnClickListener {
                    subtleTap(this)
                    onEventRegister?.invoke(post)
                }
            })
        })
    }

    private fun mediaFrame(
        card: LinearLayout,
        url: String,
        mediaType: String,
        onMediaOpen: ((String, String) -> Unit)?,
        cardStyle: CardStyle
    ): View {
        val context = card.context
        return FrameLayout(context).apply {
            setBackgroundResource(mediaBackground(cardStyle))
            clipToOutline = true
            isClickable = true
            isFocusable = true
            setOnClickListener {
                subtleTap(this)
                onMediaOpen?.invoke(url, mediaType)
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                260.dp()
            ).apply {
                topMargin = 12.dp()
            }

            addView(ImageView(context).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP
                setBackgroundResource(mediaBackground(cardStyle))
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
                if (mediaType == MEDIA_TYPE_PHOTO || mediaType == MEDIA_TYPE_MEDIA) {
                    Glide.with(context).load(url).centerCrop().into(this)
                } else {
                    alpha = 0.55f
                }
            })

            if (mediaType != MEDIA_TYPE_PHOTO && mediaType != MEDIA_TYPE_MEDIA) {
                addView(FrameLayout(context).apply {
                    setBackgroundResource(R.drawable.bg_play_button)
                    layoutParams = FrameLayout.LayoutParams(44.dp(), 44.dp(), Gravity.CENTER)
                    addView(ImageView(context).apply {
                        setImageResource(R.drawable.ic_play_24)
                        setColorFilter(context.getColor(primaryTextColor(cardStyle)))
                        layoutParams = FrameLayout.LayoutParams(20.dp(), 20.dp(), Gravity.CENTER)
                    })
                })
            }
        }
    }

    private fun addCollaborationDetails(card: LinearLayout, post: Post, cardStyle: CardStyle) {
        val context = card.context
        val details = listOfNotNull(
            detailLine(context.getString(R.string.post_detail_style), post.collaborationStyle),
            detailLine(context.getString(R.string.post_detail_location), post.collaborationLocation),
            detailLine(context.getString(R.string.post_detail_deadline), post.collaborationDate),
            detailLine(context.getString(R.string.post_detail_compensation), post.collaborationPaid)
        )
        if (details.isEmpty()) return
        card.addView(TextView(context).apply {
            text = details.joinToString("\n")
            setTextColor(context.getColor(secondaryTextColor(cardStyle)))
            textSize = 13f
            setPadding(0, 10.dp(), 0, 0)
            setLineSpacing(3.dp().toFloat(), 1f)
        })
    }

    private fun addActionRow(
        card: LinearLayout,
        post: Post,
        comments: List<PostComment>,
        isLiked: Boolean,
        isSaved: Boolean,
        currentUserId: String,
        onLike: ((Post) -> Unit)?,
        onSave: ((Post) -> Unit)?,
        onComment: ((Post, String) -> Unit)?,
        onEditComment: ((PostComment, String) -> Unit)?,
        onDeleteComment: ((PostComment) -> Unit)?,
        onLikeComment: ((PostComment) -> Unit)?,
        onReplyComment: ((PostComment, String) -> Unit)?,
        onReportComment: ((PostComment) -> Unit)?,
        cardStyle: CardStyle
    ) {
        val context = card.context
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 12.dp(), 0, 0)
        }

        row.addView(iconAction(card, R.drawable.ic_heart_24, context.getString(R.string.post_like), isLiked, likeColor(cardStyle), cardStyle) {
            onLike?.invoke(post)
        })
        row.addView(iconAction(card, R.drawable.ic_comment_24, context.getString(R.string.post_comment), false, brandColor(cardStyle), cardStyle) {
            showCommentsDialog(card, post, comments, currentUserId, onComment, onEditComment, onDeleteComment, onLikeComment, onReplyComment, onReportComment, cardStyle)
        })
        row.addView(iconAction(card, R.drawable.ic_bookmark_24, context.getString(R.string.post_save), isSaved, brandColor(cardStyle), cardStyle) {
            if (onSave == null) {
                Toast.makeText(context, R.string.post_save_unavailable, Toast.LENGTH_SHORT).show()
            } else {
                onSave.invoke(post)
            }
        })

        row.addView(View(context).apply {
            layoutParams = LinearLayout.LayoutParams(0, 1.dp(), 1f)
        })

        row.addView(iconAction(card, R.drawable.ic_share_24, context.getString(R.string.post_share), false, brandColor(cardStyle), cardStyle) {
            sharePost(post, card)
        })
        card.addView(row)
    }

    private fun iconAction(
        card: LinearLayout,
        iconRes: Int,
        description: String,
        active: Boolean,
        activeColorRes: Int = R.color.neon_pink,
        cardStyle: CardStyle,
        onClick: () -> Unit
    ): ImageButton {
        val context = card.context
        return ImageButton(context).apply {
            contentDescription = description
            setImageResource(iconRes)
            setColorFilter(context.getColor(if (active) activeColorRes else secondaryTextColor(cardStyle)))
            setBackgroundResource(iconButtonBackground(cardStyle))
            scaleType = ImageView.ScaleType.CENTER
            layoutParams = LinearLayout.LayoutParams(44.dp(), 44.dp()).apply {
                rightMargin = 4.dp()
            }
            setOnClickListener {
                subtleTap(this)
                onClick()
            }
        }
    }

    private fun addEngagementFooter(
        card: LinearLayout,
        post: Post,
        comments: List<PostComment>,
        currentUserId: String,
        onComment: ((Post, String) -> Unit)?,
        onEditComment: ((PostComment, String) -> Unit)?,
        onDeleteComment: ((PostComment) -> Unit)?,
        onLikeComment: ((PostComment) -> Unit)?,
        onReplyComment: ((PostComment, String) -> Unit)?,
        onReportComment: ((PostComment) -> Unit)?,
        cardStyle: CardStyle
    ) {
        val context = card.context
        card.addView(TextView(context).apply {
            text = context.resources.getQuantityString(
                R.plurals.post_likes_count,
                post.likesCount.toInt(),
                post.likesCount
            )
            setTextColor(context.getColor(primaryTextColor(cardStyle)))
            textSize = 13f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setPadding(0, 4.dp(), 0, 0)
        })

        val commentCount = if (comments.isNotEmpty()) comments.size.toLong() else post.commentsCount
        if (commentCount <= 0) return
        card.addView(TextView(context).apply {
            text = context.resources.getQuantityString(
                R.plurals.post_view_comments,
                commentCount.toInt(),
                commentCount
            )
            setTextColor(context.getColor(secondaryTextColor(cardStyle)))
            textSize = 13f
            setPadding(0, 5.dp(), 0, 0)
            isClickable = true
            isFocusable = true
            setOnClickListener {
                showCommentsDialog(card, post, comments, currentUserId, onComment, onEditComment, onDeleteComment, onLikeComment, onReplyComment, onReportComment, cardStyle)
            }
        })
    }

    private fun showCommentsDialog(
        card: LinearLayout,
        post: Post,
        comments: List<PostComment>,
        currentUserId: String,
        onComment: ((Post, String) -> Unit)?,
        onEditComment: ((PostComment, String) -> Unit)?,
        onDeleteComment: ((PostComment) -> Unit)?,
        onLikeComment: ((PostComment) -> Unit)?,
        onReplyComment: ((PostComment, String) -> Unit)?,
        onReportComment: ((PostComment) -> Unit)?,
        cardStyle: CardStyle
    ) {
        val context = card.context
        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(18.dp(), 16.dp(), 18.dp(), 10.dp())
        }

        content.addView(TextView(context).apply {
            text = context.getString(R.string.post_comments_title)
            setTextColor(context.getColor(primaryTextColor(cardStyle)))
            textSize = 18f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        })

        val commentsBox = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 8.dp(), 0, 0)
        }
        if (comments.isEmpty()) {
            commentsBox.addView(TextView(context).apply {
                text = context.getString(R.string.post_no_comments)
                setTextColor(context.getColor(secondaryTextColor(cardStyle)))
                textSize = 13f
            })
        } else {
            comments.forEach { comment ->
                commentsBox.addView(commentRow(card, post, comment, currentUserId, onEditComment, onDeleteComment, onLikeComment, onReplyComment, onReportComment, cardStyle))
            }
        }
        content.addView(ScrollView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                220.dp()
            )
            addView(commentsBox)
        })

        val input = EditText(context).apply {
            hint = context.getString(R.string.post_comment_hint)
            setTextColor(context.getColor(primaryTextColor(cardStyle)))
            setHintTextColor(context.getColor(mutedTextColor(cardStyle)))
            setBackgroundResource(inputBackground(cardStyle))
            maxLines = 3
        }
        content.addView(input)

        val dialog = AlertDialog.Builder(context)
            .setView(content)
            .setNegativeButton(R.string.action_cancel, null)
            .setPositiveButton(R.string.post_send_comment, null)
            .create()
        dialog.setOnShowListener {
            val send = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            send.setTextColor(context.getColor(brandColor(cardStyle)))
            send.isEnabled = false
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(context.getColor(secondaryTextColor(cardStyle)))
            input.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
                override fun afterTextChanged(s: Editable?) {
                    send.isEnabled = s?.isNotBlank() == true && onComment != null
                }
            })
            send.setOnClickListener {
                val text = input.text.toString().trim()
                if (text.isBlank()) return@setOnClickListener
                onComment?.invoke(post, text)
                dialog.dismiss()
            }
        }
        dialog.show()
    }

    // Creates one comment row and shows edit/delete only for comments owned by the viewer.
    private fun commentRow(
        card: LinearLayout,
        post: Post,
        comment: PostComment,
        currentUserId: String,
        onEditComment: ((PostComment, String) -> Unit)?,
        onDeleteComment: ((PostComment) -> Unit)?,
        onLikeComment: ((PostComment) -> Unit)?,
        onReplyComment: ((PostComment, String) -> Unit)?,
        onReportComment: ((PostComment) -> Unit)?,
        cardStyle: CardStyle
    ): View {
        val context = card.context
        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 8.dp(), 0, 0)

            addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)

                addView(TextView(context).apply {
                    text = "${comment.authorName.ifBlank { context.getString(R.string.post_fallback_author) }}: ${comment.text}"
                    setTextColor(context.getColor(secondaryTextColor(cardStyle)))
                    textSize = 13f
                })

                addView(commentActions(card, comment, onLikeComment, onReplyComment, cardStyle))
                comment.replies.forEach { reply ->
                    addView(TextView(context).apply {
                        text = "${reply.authorName.ifBlank { context.getString(R.string.post_fallback_author) }}: ${reply.text}"
                        setTextColor(context.getColor(mutedTextColor(cardStyle)))
                        textSize = 12f
                        setPadding(14.dp(), 5.dp(), 0, 0)
                    })
                }
            })

            val canManageComment = currentUserId.isNotBlank() && comment.authorId == currentUserId
            if (canManageComment || onReportComment != null) {
                addView(ImageButton(context).apply {
                    contentDescription = context.getString(R.string.post_comment_options)
                    setImageResource(R.drawable.ic_more_horizontal_24)
                    setColorFilter(context.getColor(secondaryTextColor(cardStyle)))
                    setBackgroundResource(iconButtonBackground(cardStyle))
                    layoutParams = LinearLayout.LayoutParams(44.dp(), 44.dp())
                    setOnClickListener { anchor ->
                        showCommentOptions(anchor, card, post, comment, canManageComment, onEditComment, onDeleteComment, onReportComment, cardStyle)
                    }
                })
            }
        }
    }

    // Creates inline Like and Reply controls for one comment.
    private fun commentActions(
        card: LinearLayout,
        comment: PostComment,
        onLikeComment: ((PostComment) -> Unit)?,
        onReplyComment: ((PostComment, String) -> Unit)?,
        cardStyle: CardStyle
    ): View {
        val context = card.context
        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 4.dp(), 0, 0)

            addView(TextView(context).apply {
                text = context.resources.getQuantityString(
                    R.plurals.post_comment_likes_count,
                    comment.likesCount.toInt(),
                    comment.likesCount
                )
                setTextColor(context.getColor(if (comment.isLikedByCurrentUser) likeColor(cardStyle) else mutedTextColor(cardStyle)))
                textSize = 12f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                isClickable = onLikeComment != null
                isFocusable = isClickable
                setOnClickListener { onLikeComment?.invoke(comment) }
            })

            addView(TextView(context).apply {
                text = context.getString(R.string.post_comment_reply)
                setTextColor(context.getColor(brandColor(cardStyle)))
                textSize = 12f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                setPadding(14.dp(), 0, 0, 0)
                isClickable = onReplyComment != null
                isFocusable = isClickable
                setOnClickListener { showReplyDialog(card, comment, onReplyComment, cardStyle) }
            })
        }
    }

    // Opens a small input dialog for replying to a comment.
    private fun showReplyDialog(
        card: LinearLayout,
        comment: PostComment,
        onReplyComment: ((PostComment, String) -> Unit)?,
        cardStyle: CardStyle
    ) {
        val context = card.context
        val input = EditText(context).apply {
            hint = context.getString(R.string.post_comment_reply_hint)
            setTextColor(context.getColor(primaryTextColor(cardStyle)))
            setHintTextColor(context.getColor(mutedTextColor(cardStyle)))
            setBackgroundResource(inputBackground(cardStyle))
            maxLines = 4
        }
        AlertDialog.Builder(context)
            .setTitle(R.string.post_comment_reply)
            .setView(input)
            .setNegativeButton(R.string.action_cancel, null)
            .setPositiveButton(R.string.post_send_comment) { _, _ ->
                val text = input.text.toString().trim()
                if (text.isNotBlank()) onReplyComment?.invoke(comment, text)
            }
            .show()
    }

    // Opens edit/delete actions for one owned comment.
    private fun showCommentOptions(
        anchor: View,
        card: LinearLayout,
        post: Post,
        comment: PostComment,
        canManageComment: Boolean,
        onEditComment: ((PostComment, String) -> Unit)?,
        onDeleteComment: ((PostComment) -> Unit)?,
        onReportComment: ((PostComment) -> Unit)?,
        cardStyle: CardStyle
    ) {
        PopupMenu(anchor.context, anchor).apply {
            val scopedComment = comment.copy(postId = comment.postId.ifBlank { post.postId })
            if (canManageComment && onEditComment != null) menu.add(anchor.context.getString(R.string.post_comment_edit))
            if (canManageComment && onDeleteComment != null) menu.add(anchor.context.getString(R.string.post_comment_delete))
            if (onReportComment != null) menu.add(anchor.context.getString(R.string.post_comment_report))
            setOnMenuItemClickListener { item ->
                when (item.title.toString()) {
                    anchor.context.getString(R.string.post_comment_edit) -> {
                        showEditCommentDialog(card, scopedComment, onEditComment, cardStyle)
                        true
                    }
                    anchor.context.getString(R.string.post_comment_delete) -> {
                        onDeleteComment?.invoke(scopedComment)
                        true
                    }
                    anchor.context.getString(R.string.post_comment_report) -> {
                        onReportComment?.invoke(scopedComment)
                        true
                    }
                    else -> false
                }
            }
            show()
        }
    }

    // Opens a small editor for updating one existing comment.
    private fun showEditCommentDialog(
        card: LinearLayout,
        comment: PostComment,
        onEditComment: ((PostComment, String) -> Unit)?,
        cardStyle: CardStyle
    ) {
        val context = card.context
        val input = EditText(context).apply {
            setText(comment.text)
            setSelection(text.length)
            setTextColor(context.getColor(primaryTextColor(cardStyle)))
            setHintTextColor(context.getColor(mutedTextColor(cardStyle)))
            setBackgroundResource(inputBackground(cardStyle))
            maxLines = 4
        }
        AlertDialog.Builder(context)
            .setTitle(R.string.post_comment_edit)
            .setView(input)
            .setNegativeButton(R.string.action_cancel, null)
            .setPositiveButton(R.string.post_comment_save) { _, _ ->
                onEditComment?.invoke(comment, input.text.toString())
            }
            .show()
    }

    private fun showPostOptions(
        anchor: View,
        post: Post,
        canEdit: Boolean,
        onReport: ((Post) -> Unit)?,
        onHide: ((Post) -> Unit)?,
        onEdit: ((Post) -> Unit)?,
        onDelete: ((Post) -> Unit)?
    ) {
        PopupMenu(anchor.context, anchor).apply {
            if (onHide != null) menu.add(anchor.context.getString(R.string.post_hide))
            if (onReport != null) menu.add(anchor.context.getString(R.string.post_report))
            if (canEdit) {
                menu.add(anchor.context.getString(R.string.post_edit))
                menu.add(anchor.context.getString(R.string.post_delete))
            }
            setOnMenuItemClickListener { item ->
                when (item.title.toString()) {
                    anchor.context.getString(R.string.post_hide) -> {
                        onHide?.invoke(post)
                        true
                    }
                    anchor.context.getString(R.string.post_report) -> {
                        onReport?.invoke(post)
                        true
                    }
                    anchor.context.getString(R.string.post_edit) -> {
                        onEdit?.invoke(post)
                        true
                    }
                    anchor.context.getString(R.string.post_delete) -> {
                        onDelete?.invoke(post)
                        true
                    }
                    else -> false
                }
            }
            show()
        }
    }

    private fun sharePost(post: Post, card: LinearLayout) {
        val context = card.context
        val text = listOf(post.authorName, post.text.ifBlank { post.activityDescription })
            .filter { it.isNotBlank() }
            .joinToString("\n")
            .ifBlank { context.getString(R.string.post_share_fallback) }
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        context.startActivity(Intent.createChooser(intent, context.getString(R.string.post_share)))
    }

    private fun detailLine(label: String, value: String): String? {
        return value.ifBlank { null }?.let { "$label: $it" }
    }

    private fun eventCapacityLine(post: Post): String {
        val registered = post.registrationsCount
        val waitlist = post.waitlistCount
        val capacity = post.activityCapacity
        val main = when {
            capacity > 0 -> "$registered / $capacity registered"
            registered > 0 -> "$registered registered"
            else -> ""
        }
        val waitlistText = if (waitlist > 0) "$waitlist waitlisted" else ""
        return listOf(main, waitlistText).filter { it.isNotBlank() }.joinToString(" / ")
    }

    private fun firstVisibleMedia(post: Post): Pair<String, String>? {
        val item = post.mediaItems.firstOrNull { it.visibleInMedia && it.url.isNotBlank() }
        if (item != null) return item.url to item.mediaType
        val legacyUrl = post.mediaUrls.firstOrNull { it.isNotBlank() } ?: return null
        return legacyUrl to post.mediaType.ifBlank { MEDIA_TYPE_PHOTO }
    }

    private fun formatTimestamp(post: Post): String {
        val createdAt = post.createdAt ?: return "just now"
        return SimpleDateFormat("MMM d, HH:mm", Locale.getDefault())
            .format(Date(createdAt.seconds * 1000))
    }

    private fun subtleTap(view: View) {
        view.animate()
            .scaleX(0.98f)
            .scaleY(0.98f)
            .alpha(0.88f)
            .setDuration(70)
            .withEndAction {
                view.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .alpha(1f)
                    .setDuration(90)
                    .start()
            }
            .start()
    }

    private fun cardBackground(cardStyle: CardStyle): Int {
        return if (cardStyle == CardStyle.FLOW_LIGHT) R.drawable.bg_flow_card else R.drawable.bg_post_card
    }

    private fun eventBackground(cardStyle: CardStyle): Int {
        return if (cardStyle == CardStyle.FLOW_LIGHT) R.drawable.bg_flow_event_panel else R.drawable.bg_event_accent
    }

    private fun mediaBackground(cardStyle: CardStyle): Int {
        return if (cardStyle == CardStyle.FLOW_LIGHT) R.drawable.bg_flow_media else R.drawable.bg_post_media
    }

    private fun iconButtonBackground(cardStyle: CardStyle): Int {
        return if (cardStyle == CardStyle.FLOW_LIGHT) R.drawable.bg_flow_icon_button else android.R.color.transparent
    }

    private fun inputBackground(cardStyle: CardStyle): Int {
        return if (cardStyle == CardStyle.FLOW_LIGHT) R.drawable.bg_flow_input else R.drawable.bg_input
    }

    private fun primaryTextColor(cardStyle: CardStyle): Int {
        return if (cardStyle == CardStyle.FLOW_LIGHT) R.color.flow_ink else R.color.text_primary
    }

    private fun secondaryTextColor(cardStyle: CardStyle): Int {
        return if (cardStyle == CardStyle.FLOW_LIGHT) R.color.flow_text_secondary else R.color.text_secondary
    }

    private fun mutedTextColor(cardStyle: CardStyle): Int {
        return if (cardStyle == CardStyle.FLOW_LIGHT) R.color.flow_text_muted else R.color.text_muted
    }

    private fun brandColor(cardStyle: CardStyle): Int {
        return if (cardStyle == CardStyle.FLOW_LIGHT) R.color.flow_brand else R.color.neon_purple
    }

    private fun likeColor(cardStyle: CardStyle): Int {
        return if (cardStyle == CardStyle.FLOW_LIGHT) R.color.flow_like else R.color.neon_pink
    }

    private const val POST_TYPE_DANCE_ACTIVITY = "dance_activity"
    private const val POST_TYPE_COLLABORATION = "collaboration"
    private const val MEDIA_TYPE_PHOTO = "photo"
    private const val MEDIA_TYPE_MEDIA = "media"
    private const val LONG_TEXT_THRESHOLD = 180
}

private fun Int.dp(): Int {
    return (this * android.content.res.Resources.getSystem().displayMetrics.density).toInt()
}
