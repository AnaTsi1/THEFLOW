// Reusable renderer for Discover-style cards across discovery, saved items, and search screens.
package com.ana.theflow.ui.common

import android.content.Intent
import android.net.Uri
import android.text.Html
import android.text.TextUtils
import android.view.Gravity
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
                LinearLayout.LayoutParams.WRAP_CONTENT
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
                    GooglePlacePhotoLoader.load(context, item.googlePlaceId, image, attribution)
                }
            }
        })

        if (item.source == DiscoveryItem.SOURCE_GOOGLE && item.attributionHtml.isNotBlank()) {
            attribution.text = Html.fromHtml(item.attributionHtml, Html.FROM_HTML_MODE_LEGACY)
            attribution.visibility = View.VISIBLE
        }
        card.addView(attribution)
    }

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

    private fun subtitleFor(item: DiscoveryItem): String {
        return when {
            item.source == DiscoveryItem.SOURCE_GOOGLE -> item.address.ifBlank { item.location }
            item.displayType.equals("studio", ignoreCase = true) || item.type.equals("studio", ignoreCase = true) -> item.location
            else -> listOf(item.studio, item.teacher).filter { it.isNotBlank() }.distinct().joinToString(" / ")
        }
    }

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

    private fun naturalReason(explanation: String): String {
        val reason = explanation
            .lineSequence()
            .map { it.trim() }
            .firstOrNull { it.isNotBlank() }
            .orEmpty()
        return when {
            reason.startsWith("Because you viewed", ignoreCase = true) -> ""
            reason.startsWith("Because you opened", ignoreCase = true) -> ""
            reason.contains("searched for", ignoreCase = true) ->
                reason.substringAfter("searched for").trim().takeIf { it.isNotBlank() }?.let { "Because you like $it" }.orEmpty()
            reason.startsWith("Popular near", ignoreCase = true) -> reason.replace("Popular near", "Near")
            reason.startsWith("Based on your dance profile", ignoreCase = true) -> "Matches your dance profile"
            reason.startsWith("Based on your dance preferences", ignoreCase = true) -> "Matches your preferences"
            reason.contains("level", ignoreCase = true) -> "Matches your level"
            else -> reason
        }
    }

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
