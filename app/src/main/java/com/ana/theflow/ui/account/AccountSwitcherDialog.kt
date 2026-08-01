// Bottom-anchored account switcher, listing the personal account plus every managed studio.
// This app has no Material Components dependency, so this is a plain DialogFragment positioned
// at the bottom rather than a BottomSheetDialogFragment.
package com.ana.theflow.ui.account

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.activityViewModels
import com.ana.theflow.MainActivity
import com.ana.theflow.R
import com.ana.theflow.data.model.account.AccountSummary
import com.ana.theflow.ui.settings.dp
import com.bumptech.glide.Glide

class AccountSwitcherDialog : DialogFragment() {

    private val accountViewModel: ActiveAccountViewModel by activityViewModels()

    // Builds the dialog's whole layout in code: a title, one row per account, and a "create or
    // claim a studio" link at the bottom.
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val context = requireContext()
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(R.drawable.bg_flow_card)
            setPadding(18.dp(), 16.dp(), 18.dp(), 22.dp())
        }

        root.addView(TextView(context).apply {
            text = "Switch account"
            setTextColor(context.getColor(R.color.flow_ink))
            textSize = 18f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setPadding(0, 0, 0, 12.dp())
        })

        accountViewModel.accounts.forEach { summary -> root.addView(accountRow(summary)) }

        root.addView(TextView(context).apply {
            text = "Create or claim a studio  >"
            setTextColor(context.getColor(R.color.flow_brand))
            textSize = 14f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setPadding(4.dp(), 14.dp(), 4.dp(), 4.dp())
            isClickable = true
            isFocusable = true
            setOnClickListener {
                dismiss()
                (requireActivity() as MainActivity).openStudioRequest(mode = "create")
            }
        })

        return root
    }

    // Builds one row: avatar, name + subtitle, and a checkmark if this is the currently active
    // account. Tapping the row switches to that account and closes the dialog.
    private fun accountRow(summary: AccountSummary): View {
        val context = requireContext()
        val isActive = summary.account == accountViewModel.active
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            isClickable = true
            isFocusable = true
            setBackgroundResource(if (isActive) R.drawable.bg_flow_event_panel else R.drawable.bg_flow_card)
            setPadding(12.dp(), 10.dp(), 12.dp(), 10.dp())
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = 8.dp()
            }
        }
        val avatar = ImageView(context).apply {
            setBackgroundResource(R.drawable.bg_avatar)
            scaleType = ImageView.ScaleType.CENTER_CROP
            layoutParams = LinearLayout.LayoutParams(44.dp(), 44.dp()).apply { rightMargin = 12.dp() }
        }
        row.addView(avatar)
        if (summary.imageUrl.isNotBlank()) {
            Glide.with(this).load(summary.imageUrl).circleCrop().into(avatar)
        }
        row.addView(LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            addView(TextView(context).apply {
                text = summary.displayName + if (summary.isVerified) " ✓" else ""
                setTextColor(context.getColor(R.color.flow_ink))
                textSize = 15f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
            })
            addView(TextView(context).apply {
                text = summary.subtitle
                setTextColor(context.getColor(R.color.flow_text_secondary))
                textSize = 12f
                setPadding(0, 2.dp(), 0, 0)
            })
        })
        if (isActive) {
            row.addView(TextView(context).apply {
                text = "✓"
                setTextColor(context.getColor(R.color.flow_brand))
                textSize = 18f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
            })
        }
        row.setOnClickListener {
            accountViewModel.switchTo(
                account = summary.account,
                onSuccess = { dismiss() }
            )
        }
        return row
    }

    // Strips the default dialog chrome (background, sizing, position) so it looks like a sheet
    // anchored to the bottom of the screen instead of a centered popup.
    override fun onStart() {
        super.onStart()
        dialog?.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT)
            setGravity(Gravity.BOTTOM)
        }
    }

    companion object {
        const val TAG = "AccountSwitcherDialog"
    }
}
