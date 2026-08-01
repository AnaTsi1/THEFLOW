// Builds reusable post cards for feeds, profiles, and detail screens.
package com.ana.theflow.ui.common

import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.text.Editable
import android.text.TextUtils
import android.text.TextWatcher
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.animation.OvershootInterpolator
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import com.ana.theflow.R
import com.ana.theflow.data.model.post.AuthorRef
import com.ana.theflow.data.model.post.Post
import com.ana.theflow.data.model.post.PostComment
import com.ana.theflow.data.model.post.authorRef
import com.ana.theflow.data.model.post.isStudioAuthored
import com.ana.theflow.data.model.post.originalAuthorRef
import com.ana.theflow.data.repository.ActivityTrackingRepository
import com.bumptech.glide.Glide
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.WeakHashMap

// Renders post UI while delegating persistence actions back to the hosting screen.
object PostCardRenderer {
    private val replyTargets = WeakHashMap<LinearLayout, PostComment>()

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
        onRepost: ((Post) -> Unit)? = null,
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
        // Routes the post's own header tap by account type (user vs. studio). Falls back to
        // onAuthorOpen(post.authorId) when not supplied, so existing callers keep working.
        onAuthorEntityOpen: ((AuthorRef) -> Unit)? = null,
        cardStyle: CardStyle = CardStyle.DARK,
        // Small "Hosting" / "Registered" style chip shown on the event cover, used by screens that
        // merge otherwise-separate lists (e.g. Events > My Events) into one feed.
        eventBadge: String? = null
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

        addHeader(card, post, canEdit, onAuthorOpen, onAuthorEntityOpen, onReport, onHide, onEdit, onDelete, cardStyle)
        if (post.originalPostId.isNotBlank()) {
            addRepostAttribution(card, post, cardStyle)
        }
        if (post.postType == POST_TYPE_DANCE_ACTIVITY) {
            addEventContent(card, post, isEventRegistered, onEventRegister, onOpen, onMediaOpen, cardStyle, eventBadge)
        } else if (post.postType == POST_TYPE_REPOST && post.activityType.isNotBlank()) {
            addBodyText(card, post.text, cardStyle)
            addSharedEventContent(card, post, onOpen, cardStyle)
        } else {
            addBodyText(card, post.text, cardStyle)
            addPostMedia(card, post, onMediaOpen, cardStyle)
            if (post.postType == POST_TYPE_COLLABORATION) addCollaborationDetails(card, post, cardStyle)
        }
        addActionRow(card, post, comments, isLiked, isSaved, currentUserId, onLike, onSave, onComment, onRepost, onEditComment, onDeleteComment, onLikeComment, onReplyComment, onReportComment, onAuthorOpen, cardStyle)
        addEngagementFooter(card, post, comments, currentUserId, onComment, onEditComment, onDeleteComment, onLikeComment, onReplyComment, onReportComment, onAuthorOpen, cardStyle)

        parent.addView(card)
    }

    // A standalone comments card (used by the post detail screen) - just a title and the same
    // inline comments section used inline on feed cards.
    fun addCommentThread(
        parent: LinearLayout,
        post: Post,
        comments: List<PostComment>,
        currentUserId: String = "",
        title: String,
        onComment: ((Post, String) -> Unit)? = null,
        onEditComment: ((PostComment, String) -> Unit)? = null,
        onDeleteComment: ((PostComment) -> Unit)? = null,
        onLikeComment: ((PostComment) -> Unit)? = null,
        onReplyComment: ((PostComment, String) -> Unit)? = null,
        onReportComment: ((PostComment) -> Unit)? = null,
        onAuthorOpen: ((String) -> Unit)? = null,
        cardStyle: CardStyle = CardStyle.FLOW_LIGHT
    ) {
        val context = parent.context
        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(cardBackground(cardStyle))
            setPadding(16.dp(), 14.dp(), 16.dp(), 12.dp())
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 12.dp() }
        }
        card.addView(TextView(context).apply {
            text = title
            setTextColor(context.getColor(primaryTextColor(cardStyle)))
            textSize = 17f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        })
        addInlineCommentsSection(card, post, comments, currentUserId, onComment, onEditComment, onDeleteComment, onLikeComment, onReplyComment, onReportComment, onAuthorOpen, cardStyle)
        parent.addView(card)
    }

    private fun addRepostAttribution(card: LinearLayout, post: Post, cardStyle: CardStyle) {
        val context = card.context
        card.addView(TextView(context).apply {
            text = "Repost of ${post.originalAuthorRef().name.ifBlank { "a post" }}"
            setTextColor(context.getColor(brandColor(cardStyle)))
            textSize = 12f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setPadding(0, 10.dp(), 0, 0)
        })
    }

    // Avatar, author name, "business account / dancer · timestamp" line, and the options
    // (report/hide/edit/delete) button.
    private fun addHeader(
        card: LinearLayout,
        post: Post,
        canEdit: Boolean,
        onAuthorOpen: ((String) -> Unit)?,
        onAuthorEntityOpen: ((AuthorRef) -> Unit)?,
        onReport: ((Post) -> Unit)?,
        onHide: ((Post) -> Unit)?,
        onEdit: ((Post) -> Unit)?,
        onDelete: ((Post) -> Unit)?,
        cardStyle: CardStyle
    ) {
        val context = card.context
        val authorRef = post.authorRef()
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val canOpenAuthor = (onAuthorEntityOpen != null && authorRef.id.isNotBlank()) ||
            (onAuthorOpen != null && post.authorId.isNotBlank())
        val openAuthor = View.OnClickListener {
            subtleTap(it)
            if (onAuthorEntityOpen != null && authorRef.id.isNotBlank()) {
                onAuthorEntityOpen.invoke(authorRef)
            } else if (post.authorId.isNotBlank()) {
                onAuthorOpen?.invoke(post.authorId)
            }
        }

        row.addView(ImageView(context).apply {
            setBackgroundResource(R.drawable.bg_avatar)
            scaleType = ImageView.ScaleType.CENTER_CROP
            contentDescription = context.getString(R.string.post_author_photo)
            isClickable = canOpenAuthor
            isFocusable = isClickable
            setOnClickListener(openAuthor)
            layoutParams = LinearLayout.LayoutParams(42.dp(), 42.dp()).apply {
                rightMargin = 10.dp()
            }
            if (authorRef.imageUrl.isNotBlank()) {
                Glide.with(context).load(authorRef.imageUrl).circleCrop().into(this)
            }
        })

        row.addView(LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)

            addView(TextView(context).apply {
                text = authorRef.name.ifBlank { context.getString(R.string.post_fallback_author) }
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                setTextColor(context.getColor(primaryTextColor(cardStyle)))
                textSize = 15f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                isClickable = canOpenAuthor
                isFocusable = isClickable
                setOnClickListener(openAuthor)
            })

            addView(TextView(context).apply {
                val subtitlePrefix = if (post.isStudioAuthored()) "Business account" else post.authorType.ifBlank { "dancer" }
                text = "$subtitlePrefix / ${formatTimestamp(post)}"
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

    // Adds the post's text, truncated with a "Read more" link past LONG_TEXT_THRESHOLD characters
    // so one long post doesn't push everything else off the visible feed.
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

    // Adds the post's first visible media item, if it has one.
    private fun addPostMedia(card: LinearLayout, post: Post, onMediaOpen: ((String, String) -> Unit)?, cardStyle: CardStyle) {
        val firstMedia = firstVisibleMedia(post) ?: return
        card.addView(mediaFrame(card, firstMedia.first, firstMedia.second, onMediaOpen, cardStyle))
    }

    // The full dance-activity layout: poster cover, organizer line, registration count,
    // description, and the View Details / Register buttons.
    private fun addEventContent(
        card: LinearLayout,
        post: Post,
        isEventRegistered: Boolean,
        onEventRegister: ((Post) -> Unit)?,
        onOpen: ((Post) -> Unit)?,
        onMediaOpen: ((String, String) -> Unit)?,
        cardStyle: CardStyle,
        eventBadge: String? = null
    ) {
        val context = card.context
        val firstMedia = firstVisibleMedia(post)
        card.addView(eventCoverFrame(card, post, firstMedia, onMediaOpen, cardStyle, eventBadge))

        card.addView(LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(2.dp(), 10.dp(), 2.dp(), 0)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )

            addView(TextView(context).apply {
                text = post.authorRef().name.takeIf { it.isNotBlank() }?.let { "Organized by $it" }.orEmpty()
                setTextColor(context.getColor(secondaryTextColor(cardStyle)))
                textSize = 13f
                visibility = if (text.isBlank()) View.GONE else View.VISIBLE
            })

            addView(TextView(context).apply {
                text = eventCapacityLine(post)
                setTextColor(context.getColor(secondaryTextColor(cardStyle)))
                textSize = 13f
                setPadding(0, 4.dp(), 0, 0)
                visibility = if (text.isBlank()) View.GONE else View.VISIBLE
            })

            addBodyText(this, post.activityDescription.ifBlank { post.text }, cardStyle)

            if (onOpen != null || onEventRegister != null) {
                addView(LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply { topMargin = 10.dp() }
                    if (onOpen != null) {
                        addView(eventButton(context.getString(R.string.post_view_details), primary = true, cardStyle = cardStyle) {
                            onOpen.invoke(post)
                        })
                    }
                    if (onEventRegister != null) {
                        addView(eventButton(eventActionLabel(context, post, isEventRegistered), primary = !isEventRegistered, cardStyle = cardStyle) {
                            onEventRegister.invoke(post)
                        }.apply {
                            isEnabled = !isEventFull(post) || isEventRegistered
                            alpha = if (isEnabled) 1f else 0.58f
                            if (onOpen != null) {
                                (layoutParams as LinearLayout.LayoutParams).leftMargin = 8.dp()
                            }
                        })
                    }
                })
            }
        })
    }

    // Builds the poster-style cover: the real photo (or a style-derived gradient placeholder so
    // the card never looks empty) with the title/date/location layered over a bottom scrim, like a
    // real event poster rather than a plain data box underneath a picture.
    private fun eventCoverFrame(
        card: LinearLayout,
        post: Post,
        firstMedia: Pair<String, String>?,
        onMediaOpen: ((String, String) -> Unit)?,
        cardStyle: CardStyle,
        eventBadge: String?
    ): View {
        val context = card.context
        val hasPhoto = firstMedia != null && (firstMedia.second == MEDIA_TYPE_PHOTO || firstMedia.second == MEDIA_TYPE_MEDIA)
        return FrameLayout(context).apply {
            setBackgroundResource(mediaBackground(cardStyle))
            clipToOutline = true
            isClickable = firstMedia != null
            isFocusable = firstMedia != null
            if (firstMedia != null) {
                setOnClickListener {
                    subtleTap(this)
                    onMediaOpen?.invoke(firstMedia.first, firstMedia.second)
                }
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                EVENT_COVER_HEIGHT.dp()
            ).apply { topMargin = 12.dp() }

            addView(ImageView(context).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP
                layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
                if (hasPhoto) {
                    Glide.with(context).load(firstMedia!!.first).centerCrop().into(this)
                } else {
                    setBackgroundResource(eventPlaceholderDrawable(post))
                }
            })

            if (!hasPhoto) {
                addView(ImageView(context).apply {
                    setImageResource(R.drawable.ic_event_24)
                    setColorFilter(Color.argb(70, 255, 255, 255))
                    layoutParams = FrameLayout.LayoutParams(64.dp(), 64.dp(), Gravity.CENTER).apply {
                        bottomMargin = 26.dp()
                    }
                })
            }

            addView(View(context).apply {
                setBackgroundResource(R.drawable.bg_event_cover_scrim)
                layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, 118.dp(), Gravity.BOTTOM)
            })

            if (!eventBadge.isNullOrBlank()) {
                addView(TextView(context).apply {
                    text = eventBadge
                    setTextColor(context.getColor(R.color.white))
                    textSize = 11f
                    setTypeface(typeface, android.graphics.Typeface.BOLD)
                    setBackgroundResource(R.drawable.bg_event_badge)
                    setPadding(10.dp(), 4.dp(), 10.dp(), 4.dp())
                    layoutParams = FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.WRAP_CONTENT,
                        FrameLayout.LayoutParams.WRAP_CONTENT,
                        Gravity.TOP or Gravity.START
                    ).apply {
                        topMargin = 10.dp()
                        leftMargin = 10.dp()
                    }
                })
            }

            addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    Gravity.BOTTOM
                ).apply {
                    leftMargin = 14.dp()
                    rightMargin = 14.dp()
                    bottomMargin = 12.dp()
                }

                addView(TextView(context).apply {
                    text = post.activityType.ifBlank { context.getString(R.string.post_event_title) }
                    setTextColor(context.getColor(R.color.white))
                    textSize = 19f
                    maxLines = 2
                    ellipsize = TextUtils.TruncateAt.END
                    setTypeface(typeface, android.graphics.Typeface.BOLD)
                })

                val subtitle = listOf(post.activityDate, post.activityTime, post.activityLocation)
                    .filter { it.isNotBlank() }
                    .joinToString(" / ")
                if (subtitle.isNotBlank()) {
                    addView(TextView(context).apply {
                        text = subtitle
                        setTextColor(Color.argb(230, 255, 255, 255))
                        textSize = 13f
                        maxLines = 1
                        ellipsize = TextUtils.TruncateAt.END
                        setPadding(0, 4.dp(), 0, 0)
                    })
                }
            })
        }
    }

    // Demo/legacy events rarely carry a cover photo. Rather than an empty box, pick a tasteful
    // gradient deterministically from the event's dance style (folded into activityDescription by
    // the composer as "Dance style: X") so the same event always gets the same look.
    private fun eventPlaceholderDrawable(post: Post): Int {
        val options = listOf(
            R.drawable.bg_event_cover_placeholder_1,
            R.drawable.bg_event_cover_placeholder_2,
            R.drawable.bg_event_cover_placeholder_3,
            R.drawable.bg_event_cover_placeholder_4,
            R.drawable.bg_event_cover_placeholder_5
        )
        val key = danceStyleOf(post) ?: post.activityType.ifBlank { post.postId }
        val index = kotlin.math.abs(key.hashCode()) % options.size
        return options[index]
    }

    private fun danceStyleOf(post: Post): String? {
        return Regex("Dance style: (.+)").find(post.activityDescription)?.groupValues?.get(1)?.trim()?.takeIf { it.isNotBlank() }
    }

    private fun LinearLayout.eventButton(
        textValue: String,
        primary: Boolean,
        cardStyle: CardStyle,
        onClick: () -> Unit
    ): Button {
        val context = this.context
        return Button(context).apply {
            text = textValue
            isAllCaps = false
            setTextColor(context.getColor(if (primary) R.color.white else brandColor(cardStyle)))
            setBackgroundResource(
                if (primary) {
                    if (cardStyle == CardStyle.FLOW_LIGHT) R.drawable.bg_flow_button_primary else R.drawable.bg_button_lilac
                } else {
                    if (cardStyle == CardStyle.FLOW_LIGHT) R.drawable.bg_flow_button_secondary else R.drawable.bg_button_secondary
                }
            )
            minHeight = 0
            minWidth = 0
            setPadding(14.dp(), 8.dp(), 14.dp(), 8.dp())
            layoutParams = LinearLayout.LayoutParams(0, 38.dp(), 1f)
            setOnClickListener {
                subtleTap(this)
                onClick()
            }
        }
    }

    // The compact event summary panel shown inside a repost of a dance activity - just the key
    // facts and a View Details button, not the full poster layout addEventContent uses.
    private fun addSharedEventContent(
        card: LinearLayout,
        post: Post,
        onOpen: ((Post) -> Unit)?,
        cardStyle: CardStyle
    ) {
        val context = card.context
        card.addView(LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(eventBackground(cardStyle))
            setPadding(12.dp(), 12.dp(), 12.dp(), 12.dp())
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = 10.dp()
            }

            addView(TextView(context).apply {
                text = post.activityType
                setTextColor(context.getColor(primaryTextColor(cardStyle)))
                textSize = 18f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
            })

            addView(TextView(context).apply {
                text = listOf(post.activityDate, post.activityTime, post.activityLocation)
                    .filter { it.isNotBlank() }
                    .joinToString(" / ")
                setTextColor(context.getColor(secondaryTextColor(cardStyle)))
                textSize = 13f
                setPadding(0, 6.dp(), 0, 0)
            })

            addView(TextView(context).apply {
                text = "Organized by ${post.originalAuthorRef().name.ifBlank { post.authorRef().name }}"
                setTextColor(context.getColor(secondaryTextColor(cardStyle)))
                textSize = 13f
                setPadding(0, 5.dp(), 0, 0)
            })

            addBodyText(this, post.activityDescription, cardStyle)

            addView(Button(context).apply {
                text = context.getString(R.string.post_view_details)
                isAllCaps = false
                setTextColor(context.getColor(R.color.white))
                setBackgroundResource(
                    if (cardStyle == CardStyle.FLOW_LIGHT) R.drawable.bg_flow_button_primary else R.drawable.bg_button_lilac
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
                    onOpen?.invoke(post)
                }
            })
        })
    }

    // A single tappable media preview - shows the image directly, or a dimmed frame with a play
    // icon overlay for anything that isn't a plain photo.
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

    // Adds a short list of collaboration-specific fields (style/location/deadline/compensation),
    // skipping any that weren't filled in.
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

    // The like/comment/save/repost/share icon row under every post.
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
        onRepost: ((Post) -> Unit)?,
        onEditComment: ((PostComment, String) -> Unit)?,
        onDeleteComment: ((PostComment) -> Unit)?,
        onLikeComment: ((PostComment) -> Unit)?,
        onReplyComment: ((PostComment, String) -> Unit)?,
        onReportComment: ((PostComment) -> Unit)?,
        onAuthorOpen: ((String) -> Unit)?,
        cardStyle: CardStyle
    ) {
        val context = card.context
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 6.dp(), 0, 0)
        }

        row.addView(iconAction(card, R.drawable.ic_heart_24, context.getString(R.string.post_like), isLiked, likeColor(cardStyle), cardStyle, hapticOnTap = true, burstIfActivating = true) {
            onLike?.invoke(post)
        })
        row.addView(iconAction(card, R.drawable.ic_comment_24, context.getString(R.string.post_comment), false, brandColor(cardStyle), cardStyle) {
            focusInlineCommentInput(card)
        })
        row.addView(iconAction(card, R.drawable.ic_bookmark_24, context.getString(R.string.post_save), isSaved, brandColor(cardStyle), cardStyle, hapticOnTap = true) {
            if (onSave == null) {
                Toast.makeText(context, R.string.post_save_unavailable, Toast.LENGTH_SHORT).show()
            } else {
                onSave.invoke(post)
            }
        })
        row.addView(iconAction(card, R.drawable.ic_add_24, context.getString(R.string.post_repost), false, brandColor(cardStyle), cardStyle) {
            if (onRepost == null) {
                Toast.makeText(context, R.string.post_repost_unavailable, Toast.LENGTH_SHORT).show()
            } else {
                onRepost.invoke(post)
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

    // One icon button in the action row, with an optional haptic tap and a bouncier "burst"
    // animation when it's the one turning an inactive state active (used for the like button).
    private fun iconAction(
        card: LinearLayout,
        iconRes: Int,
        description: String,
        active: Boolean,
        activeColorRes: Int = R.color.neon_pink,
        cardStyle: CardStyle,
        hapticOnTap: Boolean = false,
        burstIfActivating: Boolean = false,
        onClick: () -> Unit
    ): ImageButton {
        val context = card.context
        return ImageButton(context).apply {
            contentDescription = description
            setImageResource(iconRes)
            setColorFilter(context.getColor(if (active) activeColorRes else secondaryTextColor(cardStyle)))
            setBackgroundResource(iconButtonBackground(cardStyle))
            scaleType = ImageView.ScaleType.FIT_CENTER
            val iconInset = 7.dp()
            setPadding(iconInset, iconInset, iconInset, iconInset)
            alpha = if (active) 1f else 0.72f
            layoutParams = LinearLayout.LayoutParams(34.dp(), 34.dp()).apply {
                rightMargin = 10.dp()
            }
            setOnClickListener {
                if (hapticOnTap) {
                    performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                }
                if (burstIfActivating && !active) {
                    likeBurstAnimation(this)
                } else {
                    subtleTap(this)
                }
                onClick()
            }
        }
    }

    // The likes count line, the "view N comments" line, and the inline comments section below the
    // action row.
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
        onAuthorOpen: ((String) -> Unit)?,
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
        card.addView(TextView(context).apply {
            text = context.resources.getQuantityString(
                R.plurals.post_view_comments,
                commentCount.toInt(),
                commentCount
            )
            setTextColor(context.getColor(secondaryTextColor(cardStyle)))
            textSize = 13f
            setPadding(0, 5.dp(), 0, 0)
            visibility = if (commentCount > 0) View.VISIBLE else View.GONE
        })
        addInlineCommentsSection(card, post, comments, currentUserId, onComment, onEditComment, onDeleteComment, onLikeComment, onReplyComment, onReportComment, onAuthorOpen, cardStyle)
    }

    // The comment preview list (up to COMMENTS_PREVIEW_LIMIT, plus a "view all" link past that)
    // and the comment composer with its reply-context row underneath it.
    private fun addInlineCommentsSection(
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
        onAuthorOpen: ((String) -> Unit)?,
        cardStyle: CardStyle
    ) {
        val context = card.context
        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 10.dp(), 0, 0)
        }

        val commentsBox = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }
        if (comments.isEmpty()) {
            commentsBox.addView(TextView(context).apply {
                text = context.getString(R.string.post_no_comments)
                setTextColor(context.getColor(secondaryTextColor(cardStyle)))
                textSize = 13f
                setPadding(0, 0, 0, 8.dp())
            })
        } else {
            comments.take(COMMENTS_PREVIEW_LIMIT).forEach { comment ->
                commentsBox.addView(commentRow(card, post, comment, currentUserId, onEditComment, onDeleteComment, onLikeComment, onReplyComment, onReportComment, onAuthorOpen, cardStyle))
            }
            if (comments.size > COMMENTS_PREVIEW_LIMIT || post.commentsCount > COMMENTS_PREVIEW_LIMIT) {
                commentsBox.addView(TextView(context).apply {
                    text = context.resources.getQuantityString(
                        R.plurals.post_view_comments,
                        post.commentsCount.toInt().coerceAtLeast(comments.size),
                        post.commentsCount.coerceAtLeast(comments.size.toLong())
                    )
                    setTextColor(context.getColor(brandColor(cardStyle)))
                    textSize = 13f
                    setTypeface(typeface, android.graphics.Typeface.BOLD)
                    setPadding(0, 8.dp(), 0, 2.dp())
                    isClickable = true
                    isFocusable = true
                    setOnClickListener { onOpenCommentThread(card, post) }
                })
            }
        }
        content.addView(commentsBox)

        val composer = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.BOTTOM
            setPadding(0, if (comments.isEmpty()) 0 else 8.dp(), 0, 0)
        }
        val replyContext = LinearLayout(context).apply {
            tag = INLINE_REPLY_CONTEXT_TAG
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            visibility = View.GONE
            setPadding(10.dp(), 8.dp(), 8.dp(), 8.dp())
            setBackgroundResource(inputBackground(cardStyle))
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = 8.dp()
            }
            addView(TextView(context).apply {
                tag = INLINE_REPLY_LABEL_TAG
                setTextColor(context.getColor(secondaryTextColor(cardStyle)))
                textSize = 12f
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            })
            addView(ImageButton(context).apply {
                contentDescription = context.getString(R.string.action_cancel)
                setImageResource(R.drawable.ic_close_24)
                setColorFilter(context.getColor(secondaryTextColor(cardStyle)))
                setBackgroundResource(iconButtonBackground(cardStyle))
                layoutParams = LinearLayout.LayoutParams(36.dp(), 36.dp())
                setOnClickListener {
                    replyTargets.remove(card)
                    hideReplyContext(card)
                    findInlineCommentInput(card)?.hint = context.getString(R.string.post_comment_hint)
                }
            })
        }
        content.addView(replyContext)
        val input = EditText(context).apply {
            tag = INLINE_COMMENT_INPUT_TAG
            hint = context.getString(R.string.post_comment_hint)
            setTextColor(context.getColor(primaryTextColor(cardStyle)))
            setHintTextColor(context.getColor(mutedTextColor(cardStyle)))
            setBackgroundResource(inputBackground(cardStyle))
            maxLines = 3
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val send = ImageButton(context).apply {
            contentDescription = context.getString(R.string.post_send_comment)
            setImageResource(R.drawable.ic_send_24)
            setColorFilter(context.getColor(primaryTextColor(cardStyle)))
            setBackgroundResource(if (cardStyle == CardStyle.FLOW_LIGHT) R.drawable.bg_flow_button_primary else R.drawable.bg_button_lilac)
            minimumWidth = 0
            minimumHeight = 0
            alpha = 0.45f
            setPadding(14.dp(), 0, 14.dp(), 0)
            layoutParams = LinearLayout.LayoutParams(44.dp(), 44.dp()).apply {
                leftMargin = 8.dp()
            }
            isEnabled = false
        }
        input.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                val enabled = s?.isNotBlank() == true && onComment != null
                send.isEnabled = enabled
                send.alpha = if (enabled) 1f else 0.45f
            }
        })
        send.setOnClickListener {
            val text = input.text.toString().trim()
            if (text.isBlank()) return@setOnClickListener
            send.isEnabled = false
            val replyTarget = replyTargets.remove(card)
            if (replyTarget != null && onReplyComment != null) {
                onReplyComment.invoke(replyTarget.copy(postId = replyTarget.postId.ifBlank { post.postId }), text)
            } else {
                onComment?.invoke(post, text)
            }
            input.text?.clear()
            input.hint = context.getString(R.string.post_comment_hint)
            hideReplyContext(card)
            send.postDelayed({ send.isEnabled = true }, 1000L)
        }
        composer.addView(input)
        composer.addView(send)
        content.addView(composer)
        card.addView(content)
    }

    private fun focusInlineCommentInput(card: LinearLayout) {
        val input = findInlineCommentInput(card) ?: return
        input.requestFocus()
    }

    private fun findInlineCommentInput(view: View): EditText? {
        if (view is EditText && view.tag == INLINE_COMMENT_INPUT_TAG) return view
        if (view is android.view.ViewGroup) {
            for (index in 0 until view.childCount) {
                findInlineCommentInput(view.getChildAt(index))?.let { return it }
            }
        }
        return null
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
        onAuthorOpen: ((String) -> Unit)?,
        cardStyle: CardStyle
    ): View {
        val context = card.context
        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.TOP
            setPadding(0, 10.dp(), 0, 0)

            addView(ImageView(context).apply {
                setBackgroundResource(R.drawable.bg_avatar)
                scaleType = ImageView.ScaleType.CENTER_CROP
                contentDescription = comment.authorName.ifBlank { context.getString(R.string.post_fallback_author) }
                isClickable = comment.authorId.isNotBlank() && onAuthorOpen != null
                isFocusable = isClickable
                layoutParams = LinearLayout.LayoutParams(34.dp(), 34.dp()).apply { rightMargin = 9.dp() }
                if (comment.authorProfileImageUrl.isNotBlank()) Glide.with(context).load(comment.authorProfileImageUrl).circleCrop().into(this)
                setOnClickListener { if (comment.authorId.isNotBlank()) onAuthorOpen?.invoke(comment.authorId) }
            })

            addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)

                addView(LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    addView(TextView(context).apply {
                        text = comment.authorName.ifBlank { context.getString(R.string.post_fallback_author) }
                        setTextColor(context.getColor(primaryTextColor(cardStyle)))
                        textSize = 13f
                        setTypeface(typeface, android.graphics.Typeface.BOLD)
                        isClickable = comment.authorId.isNotBlank() && onAuthorOpen != null
                        isFocusable = isClickable
                        setOnClickListener { if (comment.authorId.isNotBlank()) onAuthorOpen?.invoke(comment.authorId) }
                    })
                    addView(TextView(context).apply {
                        text = " / ${formatCommentTimestamp(comment)}"
                        setTextColor(context.getColor(mutedTextColor(cardStyle)))
                        textSize = 12f
                    })
                })

                addView(TextView(context).apply {
                    text = comment.text
                    setTextColor(context.getColor(secondaryTextColor(cardStyle)))
                    textSize = 13f
                    setPadding(0, 2.dp(), 0, 0)
                })

                addView(commentActions(card, comment, onLikeComment, onReplyComment, cardStyle))
                val hiddenReplyViews = mutableListOf<View>()
                comment.replies.forEachIndexed { index, reply ->
                    val replyRow = LinearLayout(context).apply {
                        orientation = LinearLayout.HORIZONTAL
                        setPadding(12.dp(), 7.dp(), 0, 0)
                        visibility = if (index < REPLIES_PREVIEW_LIMIT) View.VISIBLE else View.GONE
                        addView(View(context).apply {
                            setBackgroundColor(context.getColor(if (cardStyle == CardStyle.FLOW_LIGHT) R.color.flow_border else R.color.surface_night_light))
                            layoutParams = LinearLayout.LayoutParams(2.dp(), LinearLayout.LayoutParams.MATCH_PARENT).apply {
                                rightMargin = 9.dp()
                            }
                        })
                        addView(LinearLayout(context).apply {
                            orientation = LinearLayout.VERTICAL
                            addView(TextView(context).apply {
                                text = reply.authorName.ifBlank { context.getString(R.string.post_fallback_author) }
                                setTextColor(context.getColor(primaryTextColor(cardStyle)))
                                textSize = 12f
                                setTypeface(typeface, android.graphics.Typeface.BOLD)
                                isClickable = reply.authorId.isNotBlank() && onAuthorOpen != null
                                isFocusable = isClickable
                                setOnClickListener { if (reply.authorId.isNotBlank()) onAuthorOpen?.invoke(reply.authorId) }
                            })
                            addView(TextView(context).apply {
                                text = reply.text
                                setTextColor(context.getColor(secondaryTextColor(cardStyle)))
                                textSize = 12f
                                setPadding(0, 2.dp(), 0, 0)
                            })
                        })
                    }
                    if (index >= REPLIES_PREVIEW_LIMIT) hiddenReplyViews.add(replyRow)
                    addView(replyRow)
                }
                if (comment.replies.size > REPLIES_PREVIEW_LIMIT) {
                    addView(TextView(context).apply {
                        text = "View ${comment.replies.size - REPLIES_PREVIEW_LIMIT} more replies"
                        setTextColor(context.getColor(brandColor(cardStyle)))
                        textSize = 12f
                        setTypeface(typeface, android.graphics.Typeface.BOLD)
                        setPadding(14.dp(), 6.dp(), 0, 0)
                        isClickable = true
                        isFocusable = true
                        setOnClickListener {
                            val shouldShow = hiddenReplyViews.firstOrNull()?.visibility != View.VISIBLE
                            hiddenReplyViews.forEach { it.visibility = if (shouldShow) View.VISIBLE else View.GONE }
                            text = if (shouldShow) "Hide replies" else "View ${comment.replies.size - REPLIES_PREVIEW_LIMIT} more replies"
                        }
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
                text = if (comment.likesCount > 0) {
                    context.resources.getQuantityString(
                        R.plurals.post_comment_likes_count,
                        comment.likesCount.toInt(),
                        comment.likesCount
                    )
                } else if (comment.isLikedByCurrentUser) {
                    context.getString(R.string.post_comment_liked)
                } else {
                    context.getString(R.string.post_like)
                }
                setTextColor(context.getColor(if (comment.isLikedByCurrentUser) likeColor(cardStyle) else mutedTextColor(cardStyle)))
                textSize = 12f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                isClickable = onLikeComment != null
                isFocusable = isClickable
                setOnClickListener {
                    isEnabled = false
                    onLikeComment?.invoke(comment)
                }
            })

            addView(TextView(context).apply {
                text = context.getString(R.string.post_comment_reply)
                setTextColor(context.getColor(brandColor(cardStyle)))
                textSize = 12f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                setPadding(14.dp(), 0, 0, 0)
                isClickable = onReplyComment != null
                isFocusable = isClickable
                setOnClickListener {
                    isEnabled = false
                    startInlineReply(card, comment)
                    postDelayed({ isEnabled = true }, 300L)
                }
            })
        }
    }

    // Reuses the card composer for replies so the thread stays in context.
    private fun startInlineReply(
        card: LinearLayout,
        comment: PostComment
    ) {
        val context = card.context
        val input = findInlineCommentInput(card) ?: return
        replyTargets[card] = comment
        val label = context.getString(
            R.string.post_comment_replying_to,
            comment.authorName.ifBlank { context.getString(R.string.post_fallback_author) }
        )
        input.hint = label
        showReplyContext(card, label)
        input.requestFocus()
        (context.getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as? InputMethodManager)
            ?.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT)
    }

    private fun showReplyContext(card: LinearLayout, label: String) {
        val row = findTaggedView(card, INLINE_REPLY_CONTEXT_TAG) as? LinearLayout ?: return
        val text = findTaggedView(row, INLINE_REPLY_LABEL_TAG) as? TextView ?: return
        text.text = label
        row.visibility = View.VISIBLE
    }

    private fun hideReplyContext(card: LinearLayout) {
        (findTaggedView(card, INLINE_REPLY_CONTEXT_TAG) as? LinearLayout)?.visibility = View.GONE
    }

    private fun findTaggedView(view: View, tagValue: String): View? {
        if (view.tag == tagValue) return view
        if (view is android.view.ViewGroup) {
            for (index in 0 until view.childCount) {
                findTaggedView(view.getChildAt(index), tagValue)?.let { return it }
            }
        }
        return null
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

    // Popup menu for the post's "..." button - hide/report for anyone, edit/delete for the owner.
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

    // Hands the post's text off to the system share sheet and logs the share as an activity signal.
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
        ActivityTrackingRepository().trackPostShared(post)
    }

    private fun detailLine(label: String, value: String): String? {
        return value.ifBlank { null }?.let { "$label: $it" }
    }

    // Builds the "N / capacity registered · N waitlisted" line, adapting to whichever of those
    // numbers the event actually has.
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

    private fun eventActionLabel(context: android.content.Context, post: Post, isEventRegistered: Boolean): String {
        return when {
            isEventRegistered -> context.getString(R.string.post_registered)
            isEventFull(post) -> "Full"
            else -> context.getString(R.string.post_register)
        }
    }

    private fun isEventFull(post: Post): Boolean {
        return post.activityCapacity > 0 && post.registrationsCount >= post.activityCapacity
    }

    // Tapping "view all comments" on a feed card just surfaces a toast for now rather than
    // navigating anywhere - the full thread is reachable from the post detail screen itself.
    private fun onOpenCommentThread(card: LinearLayout, post: Post) {
        Toast.makeText(card.context, card.context.getString(R.string.post_comments_title), Toast.LENGTH_SHORT).show()
    }

    // Picks the first media item to show on the card - prefers the newer mediaItems list (which
    // can hide specific items from the feed) and falls back to the older flat mediaUrls field for
    // posts written before that list existed.
    private fun firstVisibleMedia(post: Post): Pair<String, String>? {
        val item = post.mediaItems.firstOrNull { it.visibleInMedia && it.url.isNotBlank() }
        if (item != null) return item.url to item.mediaType
        val legacyUrl = post.mediaUrls.firstOrNull { it.isNotBlank() } ?: return null
        return legacyUrl to post.mediaType.ifBlank { MEDIA_TYPE_PHOTO }
    }

    // A plain date/time string for the post header.
    private fun formatTimestamp(post: Post): String {
        val createdAt = post.createdAt ?: return "just now"
        return SimpleDateFormat("MMM d, HH:mm", Locale.getDefault())
            .format(Date(createdAt.seconds * 1000))
    }

    // A relative age string for comments ("now", "5m", "3h", "2d"), falling back to a plain date
    // once a comment is more than a week old.
    private fun formatCommentTimestamp(comment: PostComment): String {
        val createdAt = comment.createdAt ?: return "now"
        val ageSeconds = ((System.currentTimeMillis() / 1000L) - createdAt.seconds).coerceAtLeast(0)
        return when {
            ageSeconds < 60 -> "now"
            ageSeconds < 3600 -> "${ageSeconds / 60}m"
            ageSeconds < 86400 -> "${ageSeconds / 3600}h"
            ageSeconds < 604800 -> "${ageSeconds / 86400}d"
            else -> SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(createdAt.seconds * 1000))
        }
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

    // A distinct, more satisfying "like" moment - pops past full size with an overshoot bounce
    // instead of the generic press-in every other action uses, so liking a post actually feels
    // different from saving/reposting/sharing it.
    private fun likeBurstAnimation(view: View) {
        view.animate().cancel()
        view.scaleX = 1f
        view.scaleY = 1f
        view.animate()
            .scaleX(1.5f)
            .scaleY(1.5f)
            .setDuration(180)
            .setInterpolator(OvershootInterpolator(3f))
            .withEndAction {
                view.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(150)
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
    private const val POST_TYPE_REPOST = "repost"
    private const val MEDIA_TYPE_PHOTO = "photo"
    private const val MEDIA_TYPE_MEDIA = "media"
    private const val LONG_TEXT_THRESHOLD = 180
    private const val EVENT_COVER_HEIGHT = 188
    private const val INLINE_COMMENT_INPUT_TAG = "post_inline_comment_input"
    private const val INLINE_REPLY_CONTEXT_TAG = "post_inline_reply_context"
    private const val INLINE_REPLY_LABEL_TAG = "post_inline_reply_label"
    // Not private: PostRepository.loadComments' feed/list callers (Home, Profile's own-posts
    // list) fetch exactly this many comments per post instead of the fuller detail-screen limit,
    // since a feed card never displays more than this anyway - see loadComments' commentLimit
    // param. Keeping it here (not duplicating the number in PostRepository) means the fetched
    // count and the displayed count can't drift apart again.
    const val COMMENTS_PREVIEW_LIMIT = 3
    private const val REPLIES_PREVIEW_LIMIT = 2
}

private fun Int.dp(): Int {
    return (this * android.content.res.Resources.getSystem().displayMetrics.density).toInt()
}
