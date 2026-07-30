package com.ana.theflow.ui.common

import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import com.ana.theflow.R

object ResponsiveLayout {
    fun constrainToReadableWidth(vararg views: View) {
        views.forEach { view ->
            view.post {
                val parentWidth = (view.parent as? View)?.width ?: return@post
                if (parentWidth <= 0) return@post
                val maxWidth = view.resources.getDimensionPixelSize(R.dimen.flow_content_max_width)
                val targetWidth = minOf(parentWidth, maxWidth)
                if (targetWidth <= 0 || view.layoutParams.width == targetWidth) return@post
                val params = view.layoutParams
                params.width = targetWidth
                when (params) {
                    is LinearLayout.LayoutParams -> params.gravity = Gravity.CENTER_HORIZONTAL
                    is FrameLayout.LayoutParams -> params.gravity = Gravity.CENTER_HORIZONTAL
                }
                view.layoutParams = params
            }
        }
    }

    fun ensureTouchTarget(vararg views: View) {
        val minSize = views.firstOrNull()?.resources?.getDimensionPixelSize(R.dimen.flow_touch_size) ?: return
        views.forEach { view ->
            view.minimumWidth = minSize
            view.minimumHeight = minSize
            val params = view.layoutParams ?: return@forEach
            var changed = false
            if (params.width in 1 until minSize) {
                params.width = minSize
                changed = true
            }
            if (params.height in 1 until minSize) {
                params.height = minSize
                changed = true
            }
            if (changed) view.layoutParams = params
        }
    }
}
