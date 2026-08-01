// Reusable renderer for Discover-style cards across discovery, saved items, and search screens.
package com.ana.theflow.ui.common

import android.content.Intent
import android.net.Uri
import android.text.Html
import android.text.TextUtils
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.ana.theflow.R
import com.ana.theflow.data.model.discovery.DiscoveryItem
import com.ana.theflow.data.repository.DiscoveryRepository
import com.bumptech.glide.Glide

object DiscoveryCardRenderer {
    enum class CardStyle {
        DARK,
        DISCOVER_LIGHT
    }

    // Adds a discovery item card to a parent layout.
    fun addItemCard(
        parent: LinearLayout,
        item: DiscoveryItem,
        explanation: String,
        onOpen: (DiscoveryItem) -> Unit,
        onSave: (DiscoveryItem) -> Unit,
        cardStyle: CardStyle = CardStyle.DARK
    ) {
        val context = parent.context
        val isLight = cardStyle == CardStyle.DISCOVER_LIGHT
        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(if (isLight) R.drawable.bg_discover_card else R.drawable.bg_post_card)
            elevation = if (isLight) 1.dp().toFloat() else 2.dp().toFloat()
            isClickable = true
            isFocusable = true
            setPadding(14.dp(), 14.dp(), 14.dp(), 14.dp())
            setOnClickListener {
                subtleTap(this)
                onOpen(item)
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT
            ).apply {
                topMargin = 10.dp()
            }
        }

        addMedia(card, item, cardStyle)
        addHeader(card, item, cardStyle)
        addMeta(card, item, cardStyle)
        addReason(card, naturalReason(explanation), cardStyle)
        addActions(card, item, onOpen, onSave, cardStyle)
        parent.addView(card)
    }

    // Events This Week: a larger, bolder card than the general-purpose one above - a big
    // day/month date badge over the cover instead of burying the date in a meta line, and a
    // dedicated Register action instead of the generic "View"/bookmark pair.
    fun addEventCard(parent: LinearLayout, item: DiscoveryItem, onOpen: (DiscoveryItem) -> Unit, onRegister: (DiscoveryItem) -> Unit) {
        val context = parent.context
        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(R.drawable.bg_discover_card)
            elevation = 2.dp().toFloat()
            isClickable = true
            isFocusable = true
            setPadding(0, 0, 0, 14.dp())
            setOnClickListener { subtleTap(this); onOpen(item) }
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT).apply {
                topMargin = 10.dp()
            }
        }

        card.addView(FrameLayout(context).apply {
            setBackgroundResource(R.drawable.bg_flow_media)
            clipToOutline = true
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 156.dp())
            val image = ImageView(context).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP
                setBackgroundResource(R.drawable.bg_flow_media)
                layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
            }
            addView(image)
            if (item.coverImageUrl.isNotBlank()) Glide.with(context).load(item.coverImageUrl).centerCrop().into(image)

            if (item.eventDayOfMonth.isNotBlank()) {
                addView(LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER
                    setBackgroundResource(R.drawable.bg_discover_card)
                    elevation = 2.dp().toFloat()
                    layoutParams = FrameLayout.LayoutParams(54.dp(), 54.dp(), Gravity.TOP or Gravity.START).apply {
                        topMargin = 10.dp()
                        marginStart = 10.dp()
                    }
                    addView(TextView(context).apply {
                        text = item.eventMonthAbbrev.uppercase()
                        gravity = Gravity.CENTER
                        setTextColor(context.getColor(R.color.discover_accent_coral))
                        setTypeface(typeface, android.graphics.Typeface.BOLD)
                        textSize = 10f
                    })
                    addView(TextView(context).apply {
                        text = item.eventDayOfMonth
                        gravity = Gravity.CENTER
                        setTextColor(context.getColor(R.color.discover_ink))
                        setTypeface(typeface, android.graphics.Typeface.BOLD)
                        textSize = 19f
                    })
                })
            }
        })

        card.addView(TextView(context).apply {
            text = item.title
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
            setTextColor(context.getColor(R.color.discover_ink))
            textSize = 18f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setPadding(14.dp(), 12.dp(), 14.dp(), 0)
        })

        val metaLine = listOfNotNull(
            item.studio.takeIf { it.isNotBlank() },
            item.style.takeIf { it.isNotBlank() && !it.equals("Dance", ignoreCase = true) },
            item.location.takeIf { it.isNotBlank() }
        ).joinToString(" / ")
        if (metaLine.isNotBlank()) {
            card.addView(TextView(context).apply {
                text = metaLine
                setTextColor(context.getColor(R.color.discover_text_secondary))
                textSize = 13f
                setPadding(14.dp(), 4.dp(), 14.dp(), 0)
            })
        }

        card.addView(Button(context).apply {
            text = context.getString(R.string.discover_register)
            isAllCaps = false
            setTextColor(context.getColor(R.color.white))
            setBackgroundResource(R.drawable.bg_discover_segment_active)
            setOnClickListener {
                subtleTap(this)
                onRegister(item)
            }
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 44.dp()).apply {
                topMargin = 12.dp()
                leftMargin = 14.dp()
                rightMargin = 14.dp()
            }
        })

        parent.addView(card)
    }

    // Teachers You May Like: a circular photo (matches how a person is framed everywhere else
    // in the app, unlike the rectangular cover used for studios/events) plus a Follow action
    // instead of the generic save/View pair.
    fun addTeacherCard(parent: LinearLayout, item: DiscoveryItem, onOpen: (DiscoveryItem) -> Unit, onFollow: (DiscoveryItem) -> Unit) {
        val context = parent.context
        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setBackgroundResource(R.drawable.bg_discover_card)
            elevation = 1.dp().toFloat()
            isClickable = true
            isFocusable = true
            setPadding(14.dp(), 16.dp(), 14.dp(), 14.dp())
            setOnClickListener { subtleTap(this); onOpen(item) }
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT).apply {
                topMargin = 10.dp()
            }
        }

        card.addView(ImageView(context).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            setBackgroundResource(R.drawable.bg_avatar)
            layoutParams = LinearLayout.LayoutParams(72.dp(), 72.dp())
            if (item.coverImageUrl.isNotBlank()) Glide.with(context).load(item.coverImageUrl).circleCrop().into(this)
        })
        card.addView(TextView(context).apply {
            text = item.title
            gravity = Gravity.CENTER
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            setTextColor(context.getColor(R.color.discover_ink))
            textSize = 15f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setPadding(0, 10.dp(), 0, 0)
        })
        val stylesLine = item.style.takeIf { it.isNotBlank() && !it.equals("Dance", ignoreCase = true) }
        card.addView(TextView(context).apply {
            text = stylesLine ?: "Dance instructor"
            gravity = Gravity.CENTER
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            setTextColor(context.getColor(R.color.discover_text_secondary))
            textSize = 12f
            setPadding(0, 2.dp(), 0, 0)
        })
        if (item.studio.isNotBlank()) {
            card.addView(TextView(context).apply {
                text = "Teaches at ${item.studio}"
                gravity = Gravity.CENTER
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                setTextColor(context.getColor(R.color.discover_text_muted))
                textSize = 11f
                setPadding(0, 2.dp(), 0, 0)
            })
        }
        card.addView(Button(context).apply {
            text = context.getString(R.string.discover_follow)
            isAllCaps = false
            setTextColor(context.getColor(R.color.white))
            setBackgroundResource(R.drawable.bg_discover_segment_active)
            setOnClickListener {
                performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                subtleTap(this)
                onFollow(item)
            }
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 38.dp()).apply {
                topMargin = 12.dp()
            }
        })

        parent.addView(card)
    }

    // A lightweight, non-blocking loading placeholder for a section still in flight - a few
    // pulsing bone blocks in the shape of that section's card, reusing the same card background
    // instead of a full-screen spinner.
    fun addSkeletonCard(parent: LinearLayout, width: Int, height: Int) {
        val context = parent.context
        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(R.drawable.bg_discover_card)
            setPadding(14.dp(), 14.dp(), 14.dp(), 14.dp())
            layoutParams = LinearLayout.LayoutParams(width, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                topMargin = 10.dp()
                rightMargin = 10.dp()
            }
        }
        fun bone(w: Int, h: Int, topMargin: Int): View {
            return View(context).apply {
                setBackgroundColor(context.getColor(R.color.discover_border))
                layoutParams = LinearLayout.LayoutParams(w, h).apply { this.topMargin = topMargin }
            }
        }
        card.addView(bone(LinearLayout.LayoutParams.MATCH_PARENT, height, 0))
        card.addView(bone((width * 0.6).toInt(), 16.dp(), 12.dp()))
        card.addView(bone((width * 0.4).toInt(), 12.dp(), 8.dp()))
        card.animate().alpha(0.5f).setDuration(650).withEndAction {
            card.animate().alpha(1f).setDuration(650).withEndAction {
                if (card.isAttachedToWindow) pulseSkeleton(card)
            }.start()
        }.start()
        parent.addView(card)
    }

    // Keeps a skeleton card gently fading in and out until it's removed from the screen.
    private fun pulseSkeleton(card: View) {
        card.animate().alpha(0.5f).setDuration(650).withEndAction {
            card.animate().alpha(1f).setDuration(650).withEndAction {
                if (card.isAttachedToWindow) pulseSkeleton(card)
            }.start()
        }.start()
    }

    // Adds the cover photo strip: a real image if one's set, a Google Places photo lookup for
    // Google-sourced items with none, or a plain labeled placeholder otherwise.
    private fun addMedia(card: LinearLayout, item: DiscoveryItem, cardStyle: CardStyle) {
        val context = card.context
        val attribution = TextView(context).apply {
            contentDescription = context.getString(R.string.discover_google_photo_attribution)
            setTextColor(context.getColor(mutedTextColor(cardStyle)))
            textSize = 10f
            visibility = View.GONE
            setPadding(0, 5.dp(), 0, 0)
        }

        card.addView(FrameLayout(context).apply {
            setBackgroundResource(if (cardStyle == CardStyle.DISCOVER_LIGHT) R.drawable.bg_flow_media else R.drawable.bg_post_media)
            clipToOutline = true
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                176.dp()
            )

            val image = ImageView(context).apply {
                contentDescription = context.getString(R.string.discover_photo)
                scaleType = ImageView.ScaleType.CENTER_CROP
                setBackgroundResource(if (cardStyle == CardStyle.DISCOVER_LIGHT) R.drawable.bg_flow_media else R.drawable.bg_post_media)
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
            }
            addView(image)

            val placeholder = TextView(context).apply {
                text = typeLabel(context, item)
                gravity = Gravity.CENTER
                setTextColor(context.getColor(sourceTextColor(item, cardStyle)))
                textSize = 13f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                contentDescription = context.getString(R.string.discover_photo_placeholder)
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
            }
            addView(placeholder)

            when {
                item.coverImageUrl.isNotBlank() -> {
                    placeholder.visibility = View.GONE
                    Glide.with(context).load(item.coverImageUrl).centerCrop().into(image)
                }
                item.source == DiscoveryItem.SOURCE_GOOGLE -> {
                    GooglePlacePhotoLoader.load(context, item.googlePlaceId, image, attribution, onPhotoLoaded = { placeholder.visibility = View.GONE })
                }
            }
        })

        if (item.source == DiscoveryItem.SOURCE_GOOGLE && item.attributionHtml.isNotBlank()) {
            attribution.text = Html.fromHtml(item.attributionHtml, Html.FROM_HTML_MODE_LEGACY)
            attribution.visibility = View.VISIBLE
        }
        card.addView(attribution)
    }

    // Title, subtitle (address/studio/teacher depending on item type), and a type chip
    // ("Class", "Studio", the Google badge, etc).
    private fun addHeader(card: LinearLayout, item: DiscoveryItem, cardStyle: CardStyle) {
        val context = card.context
        val header = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 12.dp(), 0, 0)
        }

        header.addView(LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)

            addView(TextView(context).apply {
                text = item.title
                maxLines = 2
                ellipsize = TextUtils.TruncateAt.END
                setTextColor(context.getColor(primaryTextColor(cardStyle)))
                textSize = 17f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
            })

            addView(TextView(context).apply {
                text = subtitleFor(item)
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                setTextColor(context.getColor(secondaryTextColor(cardStyle)))
                textSize = 13f
                visibility = if (text.isBlank()) View.GONE else View.VISIBLE
                setPadding(0, 4.dp(), 0, 0)
            })
        })

        header.addView(TextView(context).apply {
            text = typeLabel(context, item)
            gravity = Gravity.CENTER
            setTextColor(context.getColor(sourceTextColor(item, cardStyle)))
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            textSize = 10f
            setBackgroundResource(if (cardStyle == CardStyle.DISCOVER_LIGHT) R.drawable.bg_discover_chip else R.drawable.bg_chip)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                30.dp()
            ).apply {
                leftMargin = 10.dp()
            }
            setPadding(10.dp(), 0, 10.dp(), 0)
        })

        card.addView(header)
    }

    // A single line combining whichever of style/level/time/location/price/rating/distance the
    // item actually has, skipping anything blank.
    private fun addMeta(card: LinearLayout, item: DiscoveryItem, cardStyle: CardStyle) {
        val context = card.context
        val meta = itemMeta(context, item)
        if (meta.isBlank()) return
        card.addView(TextView(context).apply {
            text = meta
            setTextColor(context.getColor(mutedTextColor(cardStyle)))
            textSize = 13f
            maxLines = 3
            ellipsize = TextUtils.TruncateAt.END
            setPadding(0, 10.dp(), 0, 0)
        })
    }

    // The "Because you like Hip Hop"-style recommendation reason, if there is one.
    private fun addReason(card: LinearLayout, explanation: String, cardStyle: CardStyle) {
        if (explanation.isBlank()) return
        val context = card.context
        card.addView(TextView(context).apply {
            text = explanation
            setTextColor(context.getColor(secondaryTextColor(cardStyle)))
            textSize = 12f
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
            setPadding(0, 8.dp(), 0, 0)
        })
    }

    // The primary action button (opens Google Maps for Google-sourced items, otherwise opens the
    // detail screen) plus a save/bookmark button that disables itself once tapped.
    private fun addActions(
        card: LinearLayout,
        item: DiscoveryItem,
        onOpen: (DiscoveryItem) -> Unit,
        onSave: (DiscoveryItem) -> Unit,
        cardStyle: CardStyle
    ) {
        val context = card.context
        val actions = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = 12.dp()
            }
        }

        actions.addView(Button(context).apply {
            text = primaryActionLabel(context, item)
            isAllCaps = false
            setTextColor(context.getColor(if (cardStyle == CardStyle.DISCOVER_LIGHT) R.color.white else R.color.text_primary))
            setBackgroundResource(if (cardStyle == CardStyle.DISCOVER_LIGHT) R.drawable.bg_discover_segment_active else R.drawable.bg_button_primary)
            setOnClickListener {
                subtleTap(this)
                if (item.source == DiscoveryItem.SOURCE_GOOGLE && item.googleMapsUrl.isNotBlank()) {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(item.googleMapsUrl)))
                } else {
                    onOpen(item)
                }
            }
            layoutParams = LinearLayout.LayoutParams(0, 42.dp(), 1f)
        })

        actions.addView(ImageButton(context).apply {
            val isSaved = DiscoveryRepository.isSaved(item)
            contentDescription = context.getString(if (isSaved) R.string.discover_saved_item else R.string.discover_save_item)
            setImageResource(R.drawable.ic_bookmark_24)
            setColorFilter(context.getColor(if (isSaved) sourceTextColor(item, cardStyle) else mutedTextColor(cardStyle)))
            setBackgroundResource(if (cardStyle == CardStyle.DISCOVER_LIGHT) R.drawable.bg_discover_icon_button else android.R.color.transparent)
            isEnabled = !isSaved
            setOnClickListener {
                subtleTap(this)
                onSave(item)
                contentDescription = context.getString(R.string.discover_saved_item)
                setColorFilter(context.getColor(sourceTextColor(item, cardStyle)))
                isEnabled = false
            }
            layoutParams = LinearLayout.LayoutParams(44.dp(), 44.dp()).apply {
                leftMargin = 10.dp()
            }
            scaleType = ImageView.ScaleType.CENTER
            setPadding(10.dp(), 10.dp(), 10.dp(), 10.dp())
        })

        card.addView(actions)
    }

    // The short label shown in the type chip - "Google" for Places results, otherwise a
    // human-readable version of the item's type.
    private fun typeLabel(context: android.content.Context, item: DiscoveryItem): String {
        if (item.source == DiscoveryItem.SOURCE_GOOGLE) return context.getString(R.string.discover_google_badge)
        return when (item.displayType.ifBlank { item.type }.lowercase()) {
            "class" -> "Class"
            "workshop" -> "Workshop"
            "event" -> "Event"
            "studio" -> "Studio"
            "professional", "teacher", "choreographer" -> "Professional"
            else -> item.type.ifBlank { context.getString(R.string.discover_source_internal_place) }
        }
    }

    // The line shown under the title - address for Google results, city for studios, otherwise
    // whichever of studio/teacher name applies.
    private fun subtitleFor(item: DiscoveryItem): String {
        return when {
            item.source == DiscoveryItem.SOURCE_GOOGLE -> item.address.ifBlank { item.location }
            item.displayType.equals("studio", ignoreCase = true) || item.type.equals("studio", ignoreCase = true) -> item.location
            else -> listOf(item.studio, item.teacher).filter { it.isNotBlank() }.distinct().joinToString(" / ")
        }
    }

    // Builds the "/"-separated meta line: style, level, time, location, price, rating, distance -
    // whichever of these the item actually has data for.
    private fun itemMeta(context: android.content.Context, item: DiscoveryItem): String {
        val ratingText = item.rating?.takeIf { item.ratingCount == null || item.ratingCount > 0 }?.let { rating ->
            val count = item.ratingCount?.takeIf { it > 0 }?.let { " ($it)" }.orEmpty()
            context.getString(R.string.discover_rating_format, rating, count)
        }
        val distanceText = item.distanceMeters?.let { meters ->
            if (meters >= 1000) "${"%.1f".format(meters / 1000.0)} km" else "${meters.toInt()} m"
        }
        return listOfNotNull(
            item.style.takeIf { it.isNotBlank() && !it.equals("Dance", ignoreCase = true) },
            item.level.takeIf { it.isNotBlank() },
            item.dateTimeText.takeIf { it.isNotBlank() } ?: item.time.takeIf { it.isUserFacingTime() },
            item.location.takeIf { it.isNotBlank() && item.source != DiscoveryItem.SOURCE_GOOGLE },
            item.priceText.takeIf { it.isNotBlank() },
            ratingText,
            distanceText
        ).distinct().joinToString(" / ")
    }

    // Label for the primary action button, matched to what that button actually does for this
    // item's type ("View Class", "View Studio", "View on Google Maps", etc).
    private fun primaryActionLabel(context: android.content.Context, item: DiscoveryItem): String {
        if (item.source == DiscoveryItem.SOURCE_GOOGLE) return context.getString(R.string.discover_view_google_maps)
        return when (item.displayType.ifBlank { item.type }.lowercase()) {
            "class" -> context.getString(R.string.discover_view_class)
            "workshop" -> context.getString(R.string.discover_view_workshop)
            "event" -> context.getString(R.string.discover_view_event)
            "studio" -> context.getString(R.string.discover_view_studio)
            "professional", "teacher", "choreographer" -> context.getString(R.string.discover_view_profile)
            else -> context.getString(R.string.discover_open)
        }
    }

    // DiscoveryRepository.explanationFor() already returns one concrete, plain-language reason
    // (e.g. "Because you like Hip Hop") - this just takes the first line defensively, no further
    // rewriting needed.
    private fun naturalReason(explanation: String): String {
        return explanation.lineSequence().map { it.trim() }.firstOrNull { it.isNotBlank() }.orEmpty()
    }

    // Google Places sometimes returns a raw business-status string ("operational", "closed") in
    // the same field a human-readable time would go - this filters those out before display.
    private fun String.isUserFacingTime(): Boolean {
        if (isBlank()) return false
        val lower = lowercase()
        return lower != "operational" && lower != "closed_temporarily" && lower != "closed permanently"
    }

    private fun primaryTextColor(cardStyle: CardStyle): Int {
        return if (cardStyle == CardStyle.DISCOVER_LIGHT) R.color.discover_ink else R.color.text_primary
    }

    private fun secondaryTextColor(cardStyle: CardStyle): Int {
        return if (cardStyle == CardStyle.DISCOVER_LIGHT) R.color.discover_text_secondary else R.color.text_secondary
    }

    private fun mutedTextColor(cardStyle: CardStyle): Int {
        return if (cardStyle == CardStyle.DISCOVER_LIGHT) R.color.discover_text_muted else R.color.text_muted
    }

    private fun sourceTextColor(item: DiscoveryItem, cardStyle: CardStyle): Int {
        if (cardStyle != CardStyle.DISCOVER_LIGHT) return R.color.text_primary
        return if (item.source == DiscoveryItem.SOURCE_GOOGLE) R.color.discover_google else R.color.discover_purple
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
