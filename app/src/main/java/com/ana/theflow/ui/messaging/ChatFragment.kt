package com.ana.theflow.ui.messaging

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.LayoutInflater
import android.view.inputmethod.InputMethodManager
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.getSystemService
import androidx.fragment.app.Fragment
import com.ana.theflow.MainActivity
import com.ana.theflow.R
import com.ana.theflow.data.model.messaging.Conversation
import com.ana.theflow.data.model.messaging.Message
import com.ana.theflow.data.repository.AuthRepository
import com.ana.theflow.data.repository.MessagingRepository
import com.ana.theflow.data.repository.SettingsRepository
import com.ana.theflow.databinding.FragmentChatBinding
import com.bumptech.glide.Glide
import com.google.firebase.firestore.ListenerRegistration
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ChatFragment : Fragment() {

    private var _binding: FragmentChatBinding? = null
    private val binding get() = _binding!!
    private val messagingRepository = MessagingRepository()
    private val authRepository = AuthRepository()
    private val settingsRepository = SettingsRepository()
    private var messagesListener: ListenerRegistration? = null
    private var conversation: Conversation? = null
    private var isSending = false
    private var readReceiptsEnabled = true

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentChatBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.chatBTNBack.setOnClickListener { parentFragmentManager.popBackStack() }
        binding.chatBTNSend.setOnClickListener { sendMessage() }
        binding.chatBTNEmoji.setOnClickListener { openKeyboard() }
        binding.chatEDTMessage.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                updateSendState()
            }
        })
        updateSendState()
        loadSettings()
        loadConversation()
    }

    private fun loadSettings() {
        settingsRepository.loadSettings(
            onSuccess = { _, messageSettings ->
                readReceiptsEnabled = messageSettings.readReceipts
            },
            onFailure = {}
        )
    }

    private fun loadConversation() {
        val conversationId = requireArguments().getString(ARG_CONVERSATION_ID).orEmpty()
        binding.chatProgress.visibility = View.VISIBLE
        messagingRepository.loadConversation(
            conversationId = conversationId,
            onSuccess = { loaded ->
                if (_binding == null) return@loadConversation
                conversation = loaded
                renderHeader(loaded)
                listenMessages(conversationId)
            },
            onFailure = { error ->
                if (_binding == null) return@loadConversation
                binding.chatProgress.visibility = View.GONE
                binding.chatLBLMessage.text = error
                binding.chatLBLMessage.visibility = View.VISIBLE
            }
        )
    }

    private fun renderHeader(conversation: Conversation) {
        val currentUid = authRepository.getCurrentUserUid().orEmpty()
        val otherUid = conversation.participantIds.firstOrNull { it != currentUid }.orEmpty()
        val other = conversation.participantInfo[otherUid]
        binding.chatLBLName.text = other?.name?.ifBlank { "Dancer" } ?: "Dancer"
        binding.chatBTNProfile.setOnClickListener {
            if (otherUid.isNotBlank()) (requireActivity() as MainActivity).openUserProfile(otherUid)
        }
        binding.chatIMGAvatar.setImageResource(android.R.color.transparent)
        if (!other?.profileImageUrl.isNullOrBlank()) {
            Glide.with(this).load(other?.profileImageUrl).circleCrop().into(binding.chatIMGAvatar)
        }
    }

    private fun listenMessages(conversationId: String) {
        messagesListener?.remove()
        messagesListener = messagingRepository.listenToMessages(
            conversationId = conversationId,
            onUpdate = { messages ->
                if (_binding == null) return@listenToMessages
                binding.chatProgress.visibility = View.GONE
                renderMessages(messages)
                messagingRepository.markConversationRead(conversationId)
                if (readReceiptsEnabled) {
                    messagingRepository.markMessagesRead(conversationId, messages)
                }
            },
            onError = { error ->
                if (_binding == null) return@listenToMessages
                binding.chatProgress.visibility = View.GONE
                binding.chatLBLMessage.text = error
                binding.chatLBLMessage.visibility = View.VISIBLE
            }
        )
    }

    private fun renderMessages(messages: List<Message>) {
        val currentUid = authRepository.getCurrentUserUid().orEmpty()
        val shouldScroll = isNearBottom()
        binding.chatLAYMessages.removeAllViews()
        binding.chatLBLMessage.visibility = if (messages.isEmpty()) View.VISIBLE else View.GONE
        binding.chatLBLMessage.text = "No messages yet. Start the conversation."
        messages.forEachIndexed { index, message ->
            val previous = messages.getOrNull(index - 1)
            binding.chatLAYMessages.addView(messageRow(message, previous, currentUid))
        }
        if (shouldScroll || messages.size <= 1) {
            binding.chatSCROLLMessages.post {
                binding.chatSCROLLMessages.fullScroll(View.FOCUS_DOWN)
            }
        }
    }

    private fun messageRow(message: Message, previous: Message?, currentUid: String): View {
        val context = requireContext()
        val isMine = message.senderId == currentUid
        val grouped = previous?.senderId == message.senderId
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = if (isMine) Gravity.END else Gravity.START
            setPadding(0, if (grouped) 3.dp() else 9.dp(), 0, 0)
        }
        row.addView(TextView(context).apply {
            text = message.text
            setTextColor(context.getColor(R.color.text_primary))
            textSize = if (isEmojiOnly(message.text)) 28f else 15f
            setLineSpacing(2.dp().toFloat(), 1f)
            setBackgroundResource(if (isMine) R.drawable.bg_bubble_sent else R.drawable.bg_bubble_received)
            layoutParams = LinearLayout.LayoutParams(
                (resources.displayMetrics.widthPixels * 0.74f).toInt(),
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        })
        row.addView(TextView(context).apply {
            text = formatTime(message)
            setTextColor(context.getColor(R.color.text_muted))
            textSize = 10f
            setPadding(4.dp(), 3.dp(), 4.dp(), 0)
        })
        return row
    }

    private fun sendMessage() {
        if (isSending) return
        val conversationId = requireArguments().getString(ARG_CONVERSATION_ID).orEmpty()
        val text = binding.chatEDTMessage.text.toString()
        if (text.trim().isBlank()) return
        isSending = true
        updateSendState()
        messagingRepository.sendMessage(
            conversationId = conversationId,
            text = text,
            onSuccess = {
                if (_binding == null) return@sendMessage
                isSending = false
                binding.chatEDTMessage.text?.clear()
                updateSendState()
            },
            onFailure = { error ->
                if (_binding == null) return@sendMessage
                isSending = false
                updateSendState()
                Toast.makeText(requireContext(), error, Toast.LENGTH_SHORT).show()
            }
        )
    }

    private fun updateSendState() {
        val enabled = !isSending && binding.chatEDTMessage.text?.isNotBlank() == true
        binding.chatBTNSend.isEnabled = enabled
        binding.chatBTNSend.alpha = if (enabled) 1f else 0.42f
    }

    private fun openKeyboard() {
        binding.chatEDTMessage.requestFocus()
        requireContext().getSystemService<InputMethodManager>()
            ?.showSoftInput(binding.chatEDTMessage, InputMethodManager.SHOW_IMPLICIT)
    }

    private fun isNearBottom(): Boolean {
        val child = binding.chatSCROLLMessages.getChildAt(0) ?: return true
        val diff = child.bottom - (binding.chatSCROLLMessages.height + binding.chatSCROLLMessages.scrollY)
        return diff < 80.dp()
    }

    private fun formatTime(message: Message): String {
        val timestamp = message.sentAt ?: return ""
        return SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(timestamp.seconds * 1000))
    }

    private fun isEmojiOnly(text: String): Boolean {
        val trimmed = text.trim()
        if (trimmed.isBlank() || trimmed.length > 8) return false
        return trimmed.none { it.isLetterOrDigit() }
    }

    override fun onDestroyView() {
        messagesListener?.remove()
        messagesListener = null
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val ARG_CONVERSATION_ID = "ARG_CONVERSATION_ID"

        fun newInstance(conversationId: String): ChatFragment {
            return ChatFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_CONVERSATION_ID, conversationId)
                }
            }
        }
    }
}

private fun Int.dp(): Int {
    return (this * android.content.res.Resources.getSystem().displayMetrics.density).toInt()
}
