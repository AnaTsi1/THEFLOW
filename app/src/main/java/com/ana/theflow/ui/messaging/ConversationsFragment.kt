package com.ana.theflow.ui.messaging

import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.ana.theflow.MainActivity
import com.ana.theflow.R
import com.ana.theflow.data.model.messaging.Conversation
import com.ana.theflow.data.repository.AuthRepository
import com.ana.theflow.data.repository.MessagingRepository
import com.ana.theflow.databinding.FragmentConversationsBinding
import com.ana.theflow.ui.common.ResponsiveLayout
import com.ana.theflow.ui.common.UiText
import com.bumptech.glide.Glide
import com.google.firebase.firestore.ListenerRegistration
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ConversationsFragment : Fragment() {

    private var _binding: FragmentConversationsBinding? = null
    private val binding get() = _binding!!
    private val messagingRepository = MessagingRepository()
    private val authRepository = AuthRepository()
    private val viewModel: ConversationsViewModel by activityViewModels()
    private var conversationsListener: ListenerRegistration? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentConversationsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.conversationsBTNNew.setOnClickListener {
            (requireActivity() as MainActivity).openNewMessage()
        }
        (binding.root as? ViewGroup)?.let { root ->
            ResponsiveLayout.constrainToReadableWidth(*Array(root.childCount) { index -> root.getChildAt(index) })
        }
        ResponsiveLayout.ensureTouchTarget(binding.conversationsBTNNew)
        if (viewModel.conversations.isNotEmpty()) renderConversations(viewModel.conversations)
        listen()
    }

    private fun listen() {
        binding.conversationsProgress.visibility = if (viewModel.conversations.isEmpty()) View.VISIBLE else View.GONE
        binding.conversationsLBLMessage.visibility = if (viewModel.conversations.isEmpty()) View.GONE else binding.conversationsLBLMessage.visibility
        conversationsListener?.remove()
        conversationsListener = messagingRepository.listenToConversations(
            onUpdate = { conversations ->
                if (_binding == null) return@listenToConversations
                viewModel.conversations = conversations
                viewModel.error = ""
                binding.conversationsProgress.visibility = View.GONE
                renderConversations(conversations)
            },
            onError = { error ->
                if (_binding == null) return@listenToConversations
                viewModel.error = error
                binding.conversationsProgress.visibility = View.GONE
                if (viewModel.conversations.isEmpty()) {
                    binding.conversationsLBLMessage.text = UiText.friendlyError(error, "We could not load your messages. Please try again.")
                    binding.conversationsLBLMessage.visibility = View.VISIBLE
                } else {
                    Toast.makeText(requireContext(), UiText.friendlyError(error, "Messages could not refresh."), Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    private fun renderConversations(conversations: List<Conversation>) {
        val currentUid = authRepository.getCurrentUserUid().orEmpty()
        binding.conversationsLAYList.removeAllViews()
        binding.conversationsLBLMessage.visibility = if (conversations.isEmpty()) View.VISIBLE else View.GONE
        binding.conversationsLBLMessage.text = "No conversations yet. Start a new message when you're ready."
        conversations.forEach { conversation ->
            binding.conversationsLAYList.addView(conversationRow(conversation, currentUid))
        }
    }

    private fun conversationRow(conversation: Conversation, currentUid: String): View {
        val context = requireContext()
        val otherUid = conversation.participantIds.firstOrNull { it != currentUid }.orEmpty()
        val other = conversation.participantInfo[otherUid]
        val unreadCount = conversation.unreadCounts[currentUid] ?: 0L
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundResource(if (unreadCount > 0) R.drawable.bg_flow_event_panel else R.drawable.bg_flow_card)
            setPadding(12.dp(), 12.dp(), 12.dp(), 12.dp())
            isClickable = true
            isFocusable = true
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = 10.dp()
            }
            setOnClickListener {
                (requireActivity() as MainActivity).openChat(conversation.conversationId)
            }
        }

        val avatar = ImageView(context).apply {
            setBackgroundResource(R.drawable.bg_avatar)
            scaleType = ImageView.ScaleType.CENTER_CROP
            layoutParams = LinearLayout.LayoutParams(48.dp(), 48.dp()).apply {
                rightMargin = 12.dp()
            }
        }
        row.addView(avatar)
        if (!other?.profileImageUrl.isNullOrBlank()) {
            Glide.with(this).load(other?.profileImageUrl).circleCrop().into(avatar)
        }

        row.addView(LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            addView(TextView(context).apply {
                text = other?.name?.ifBlank { "Dancer" } ?: "Dancer"
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
                setTextColor(context.getColor(R.color.flow_ink))
                textSize = 15f
                setTypeface(typeface, if (unreadCount > 0) Typeface.BOLD else Typeface.NORMAL)
            })
            addView(TextView(context).apply {
                text = conversation.lastMessage.ifBlank { "No messages yet" }
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
                setTextColor(context.getColor(if (unreadCount > 0) R.color.flow_ink else R.color.flow_text_secondary))
                textSize = 13f
                setPadding(0, 4.dp(), 0, 0)
            })
        })

        row.addView(LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
            addView(TextView(context).apply {
                text = formatTime(conversation)
                setTextColor(context.getColor(R.color.flow_text_muted))
                textSize = 11f
            })
            if (unreadCount > 0) {
                addView(TextView(context).apply {
                    text = unreadCount.coerceAtMost(99).toString()
                    gravity = Gravity.CENTER
                    setTextColor(context.getColor(R.color.white))
                    textSize = 10f
                    setTypeface(typeface, Typeface.BOLD)
                    setBackgroundResource(R.drawable.bg_badge)
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        18.dp()
                    ).apply {
                        topMargin = 6.dp()
                    }
                })
            }
        })

        return row
    }

    private fun formatTime(conversation: Conversation): String {
        val timestamp = conversation.lastMessageAt ?: return ""
        return SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(timestamp.seconds * 1000))
    }

    override fun onDestroyView() {
        conversationsListener?.remove()
        conversationsListener = null
        super.onDestroyView()
        _binding = null
    }
}

private fun Int.dp(): Int {
    return (this * android.content.res.Resources.getSystem().displayMetrics.density).toInt()
}
