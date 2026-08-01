package com.ana.theflow.ui.settings

import android.content.Context
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.fragment.app.Fragment
import com.ana.theflow.R
import com.ana.theflow.ui.common.ResponsiveLayout

object SettingsUi {
    fun screen(fragment: Fragment, title: String, onBack: (() -> Unit)? = null): LinearLayout {
        val context = fragment.requireContext()
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(R.color.flow_background)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT
            )
            addView(appBar(fragment, title, onBack))
        }
    }

    fun contentScroll(context: Context): ScrollView {
        return ScrollView(context).apply {
            isFillViewport = true
            clipToPadding = false
            setBackgroundResource(R.color.flow_background)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        }
    }

    fun contentColumn(context: Context): LinearLayout {
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(18.dp(), 14.dp(), 18.dp(), 28.dp())
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            ResponsiveLayout.constrainToReadableWidth(this)
        }
    }

    fun row(
        context: Context,
        title: String,
        description: String = "",
        value: String = "",
        enabled: Boolean = true,
        destructive: Boolean = false,
        @DrawableRes iconRes: Int? = null,
        onClick: (() -> Unit)? = null
    ): View {
        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            alpha = if (enabled) 1f else 0.56f
            setBackgroundResource(R.drawable.bg_flow_card)
            setPadding(14.dp(), 12.dp(), 14.dp(), 12.dp())
            isClickable = enabled && onClick != null
            isFocusable = isClickable
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 10.dp() }
            if (iconRes != null) {
                addView(ImageView(context).apply {
                    setImageResource(iconRes)
                    setColorFilter(context.getColor(if (destructive) R.color.flow_error else R.color.flow_brand))
                    layoutParams = LinearLayout.LayoutParams(22.dp(), 22.dp()).apply {
                        rightMargin = 14.dp()
                    }
                })
            }
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                addView(TextView(context).apply {
                    text = title
                    setTextColor(context.getColor(if (destructive) R.color.flow_error else R.color.flow_ink))
                    textSize = 16f
                    setTypeface(typeface, Typeface.BOLD)
                })
                if (description.isNotBlank()) {
                    addView(TextView(context).apply {
                        text = description
                        setTextColor(context.getColor(R.color.flow_text_secondary))
                        textSize = 12f
                        setPadding(0, 3.dp(), 0, 0)
                    })
                }
            })
            addView(TextView(context).apply {
                text = value.ifBlank { if (onClick != null) ">" else "Coming soon" }
                setTextColor(context.getColor(if (destructive) R.color.flow_error else R.color.flow_brand))
                textSize = if (value.isBlank() && onClick != null) 20f else 12f
                setTypeface(typeface, Typeface.BOLD)
            })
            setOnClickListener { if (enabled) onClick?.invoke() }
        }
    }

    fun message(context: Context, textValue: String): TextView {
        return TextView(context).apply {
            text = textValue
            setTextColor(context.getColor(R.color.flow_text_secondary))
            textSize = 13f
            setPadding(0, 6.dp(), 0, 14.dp())
        }
    }

    private fun appBar(fragment: Fragment, title: String, onBack: (() -> Unit)?): View {
        val context = fragment.requireContext()
        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundResource(R.color.flow_background)
            setPadding(10.dp(), 12.dp(), 14.dp(), 8.dp())
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                64.dp()
            )
            addView(ImageButton(context).apply {
                contentDescription = context.getString(R.string.action_back)
                setImageResource(R.drawable.ic_arrow_back_24)
                setBackgroundResource(R.drawable.bg_icon_button_compact)
                setPadding(10.dp(), 10.dp(), 10.dp(), 10.dp())
                layoutParams = LinearLayout.LayoutParams(44.dp(), 44.dp())
                setOnClickListener { onBack?.invoke() ?: fragment.parentFragmentManager.popBackStack() }
            })
            addView(TextView(context).apply {
                text = title
                setTextColor(context.getColor(R.color.flow_ink))
                textSize = 22f
                setTypeface(typeface, Typeface.BOLD)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                    leftMargin = 12.dp()
                }
            })
        }
    }
}

fun Int.dp(): Int {
    return (this * android.content.res.Resources.getSystem().displayMetrics.density).toInt()
}
