package com.ana.theflow.ui.messaging

import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextUtils
import android.text.TextWatcher
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.ana.theflow.MainActivity
import com.ana.theflow.R
import com.ana.theflow.data.model.user.User
import com.ana.theflow.data.repository.AuthRepository
import com.ana.theflow.data.repository.MessagingRepository
import com.ana.theflow.data.repository.UserRepository
import com.ana.theflow.ui.common.UiText
import com.bumptech.glide.Glide

class MessageUserPickerFragment : Fragment() {

    private val authRepository = AuthRepository()
    private val userRepository = UserRepository()
    private val messagingRepository = MessagingRepository()
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var list: LinearLayout
    private lateinit var message: TextView
    private lateinit var progress: ProgressBar
    private lateinit var search: EditText
    private var currentUid = ""
    private val eligibility = mutableMapOf<String, Pair<Boolean, String>>()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val context = requireContext()
        currentUid = authRepository.getCurrentUserUid().orEmpty()
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(14.dp(), 14.dp(), 14.dp(), 0)
            setBackgroundColor(context.getColor(R.color.flow_background))
            addView(header())
            search = EditText(context).apply {
                hint = "Search people"
                setSingleLine(true)
                setTextColor(context.getColor(R.color.flow_ink))
                setHintTextColor(context.getColor(R.color.flow_text_muted))
                setBackgroundResource(R.drawable.bg_flow_input)
                setPadding(14.dp(), 0, 14.dp(), 0)
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 48.dp()).apply {
                    topMargin = 12.dp()
                }
            }
            addView(search)
            progress = ProgressBar(context).apply {
                visibility = View.GONE
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                    gravity = Gravity.CENTER_HORIZONTAL
                    topMargin = 14.dp()
                }
            }
            addView(progress)
            message = TextView(context).apply {
                visibility = View.GONE
                setTextColor(context.getColor(R.color.flow_text_secondary))
                textSize = 14f
                setBackgroundResource(R.drawable.bg_flow_card)
                setPadding(14.dp(), 14.dp(), 14.dp(), 14.dp())
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                    topMargin = 14.dp()
                }
            }
            addView(message)
            addView(ScrollView(context).apply {
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f).apply {
                    topMargin = 12.dp()
                }
                list = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
                addView(list)
            })
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                handler.removeCallbacksAndMessages(null)
                handler.postDelayed({ loadUsers(s?.toString().orEmpty()) }, 280)
            }
        })
        loadUsers("")
    }

    private fun header(): View {
        val context = requireContext()
        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(ImageButton(context).apply {
                setImageResource(R.drawable.ic_close_24)
                setColorFilter(context.getColor(R.color.flow_brand))
                setBackgroundResource(R.drawable.bg_flow_icon_button)
                contentDescription = "Back"
                setOnClickListener { parentFragmentManager.popBackStack() }
                layoutParams = LinearLayout.LayoutParams(44.dp(), 44.dp()).apply { rightMargin = 12.dp() }
            })
            addView(TextView(context).apply {
                text = "New Message"
                setTextColor(context.getColor(R.color.flow_ink))
                textSize = 24f
                setTypeface(typeface, Typeface.BOLD)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            })
        }
    }

    private fun loadUsers(query: String) {
        progress.visibility = View.VISIBLE
        message.visibility = View.GONE
        list.removeAllViews()
        if (query.isBlank()) {
            userRepository.loadFollowing(
                uid = currentUid,
                onSuccess = { users -> renderUsers("People You Follow", users.filterEligibleBase(), showFollowedBadge = true) },
                onFailure = { error -> renderError(error, "We couldn't load people you follow.") }
            )
        } else {
            userRepository.searchUsers(
                query = query,
                dancersOnly = false,
                onSuccess = { users -> renderUsers("People", users.filterEligibleBase(), showFollowedBadge = false) },
                onFailure = { error -> renderError(error, "We couldn't search people right now.") }
            )
        }
    }

    private fun renderUsers(title: String, users: List<User>, showFollowedBadge: Boolean) {
        if (!isAdded) return
        progress.visibility = View.GONE
        list.removeAllViews()
        if (users.isEmpty()) {
            showMessage(if (search.text.isNullOrBlank()) "You're not following anyone yet. Search people to start a conversation." else "No people match this search.")
            return
        }
        list.addView(sectionTitle(title))
        users.distinctBy { it.uid }.forEach { user ->
            list.addView(userRow(user, showFollowedBadge))
            if (!eligibility.containsKey(user.uid)) {
                messagingRepository.canStartConversationWith(user.uid) { canMessage, reason ->
                    if (!isAdded) return@canStartConversationWith
                    eligibility[user.uid] = canMessage to reason
                    renderUsers(title, users, showFollowedBadge)
                }
            }
        }
    }

    private fun userRow(user: User, showFollowedBadge: Boolean): View {
        val context = requireContext()
        val state = eligibility[user.uid]
        val canMessage = state?.first ?: true
        val reason = state?.second.orEmpty()
        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundResource(R.drawable.bg_post_card)
            alpha = if (canMessage) 1f else 0.56f
            isClickable = true
            isFocusable = true
            setPadding(12.dp(), 12.dp(), 12.dp(), 12.dp())
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = 10.dp()
            }
            setOnClickListener {
                if (!canMessage) {
                    Toast.makeText(context, reason.ifBlank { "This person cannot receive messages right now." }, Toast.LENGTH_SHORT).show()
                } else {
                    openConversation(user)
                }
            }
            val avatar = ImageView(context).apply {
                setBackgroundResource(R.drawable.bg_avatar)
                scaleType = ImageView.ScaleType.CENTER_CROP
                layoutParams = LinearLayout.LayoutParams(48.dp(), 48.dp()).apply { rightMargin = 12.dp() }
            }
            addView(avatar)
            if (user.profileImageUrl.isNotBlank()) Glide.with(this@MessageUserPickerFragment).load(user.profileImageUrl).circleCrop().into(avatar)
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                addView(TextView(context).apply {
                    text = user.fullName()
                    maxLines = 1
                    ellipsize = TextUtils.TruncateAt.END
                    setTextColor(context.getColor(R.color.flow_ink))
                    textSize = 15f
                    setTypeface(typeface, Typeface.BOLD)
                })
                addView(TextView(context).apply {
                    text = listOf(user.roleLabel(), user.danceStyles.take(2).joinToString(", "), user.location).filter { it.isNotBlank() }.joinToString(" / ")
                    maxLines = 1
                    ellipsize = TextUtils.TruncateAt.END
                    setTextColor(context.getColor(R.color.flow_text_secondary))
                    textSize = 12f
                    setPadding(0, 3.dp(), 0, 0)
                })
                if (!canMessage) {
                    addView(TextView(context).apply {
                        text = reason
                        setTextColor(context.getColor(R.color.flow_error))
                        textSize = 12f
                        setPadding(0, 5.dp(), 0, 0)
                    })
                }
            })
            if (showFollowedBadge) {
                addView(TextView(context).apply {
                    text = "Followed"
                    setTextColor(context.getColor(R.color.flow_brand))
                    textSize = 12f
                })
            }
        }
    }

    private fun openConversation(user: User) {
        progress.visibility = View.VISIBLE
        messagingRepository.resolveOrCreateConversation(
            otherUserId = user.uid,
            onSuccess = { conversationId ->
                if (!isAdded) return@resolveOrCreateConversation
                progress.visibility = View.GONE
                (requireActivity() as MainActivity).openChat(conversationId)
            },
            onFailure = { error -> renderError(error, "We couldn't open this conversation.") }
        )
    }

    private fun renderError(error: String, fallback: String) {
        if (!isAdded) return
        progress.visibility = View.GONE
        showMessage(UiText.friendlyError(error, fallback))
    }

    private fun showMessage(textValue: String) {
        message.text = textValue
        message.visibility = View.VISIBLE
    }

    private fun sectionTitle(textValue: String): TextView {
        return TextView(requireContext()).apply {
            text = textValue
            setTextColor(context.getColor(R.color.flow_ink))
            textSize = 17f
            setTypeface(typeface, Typeface.BOLD)
            setPadding(0, 4.dp(), 0, 10.dp())
        }
    }

    private fun List<User>.filterEligibleBase(): List<User> {
        return filter { it.uid.isNotBlank() && it.uid != currentUid }
    }

    private fun User.fullName(): String {
        return "${firstName.trim()} ${lastName.trim()}".trim().ifBlank { "Dancer" }
    }

    private fun User.roleLabel(): String {
        return when {
            verifiedTeacher -> "Teacher"
            verifiedChoreographer -> "Choreographer"
            role.contains("studio", ignoreCase = true) -> "Studio"
            else -> "Dancer"
        }
    }

    override fun onDestroyView() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroyView()
    }
}

private fun Int.dp(): Int {
    return (this * android.content.res.Resources.getSystem().displayMetrics.density).toInt()
}
