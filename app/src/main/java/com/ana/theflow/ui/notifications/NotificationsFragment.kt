package com.ana.theflow.ui.notifications

import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.ana.theflow.MainActivity
import com.ana.theflow.R
import com.ana.theflow.data.model.notification.InAppNotification
import com.ana.theflow.data.repository.NotificationRepository
import com.ana.theflow.databinding.FragmentNotificationsBinding
import com.bumptech.glide.Glide
import com.google.firebase.firestore.ListenerRegistration
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class NotificationsFragment : Fragment() {

    private var _binding: FragmentNotificationsBinding? = null
    private val binding get() = _binding!!
    private val notificationRepository = NotificationRepository()
    private var notificationsListener: ListenerRegistration? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentNotificationsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.notificationsBTNMarkAll.setOnClickListener {
            notificationRepository.markAllAsRead { error ->
                if (_binding != null) Toast.makeText(requireContext(), error, Toast.LENGTH_SHORT).show()
            }
        }
        listen()
    }

    private fun listen() {
        binding.notificationsProgress.visibility = View.VISIBLE
        notificationsListener?.remove()
        notificationsListener = notificationRepository.listenToNotifications(
            onUpdate = { notifications ->
                if (_binding == null) return@listenToNotifications
                binding.notificationsProgress.visibility = View.GONE
                renderNotifications(notifications)
            },
            onError = { error ->
                if (_binding == null) return@listenToNotifications
                binding.notificationsProgress.visibility = View.GONE
                binding.notificationsLBLMessage.text = error
                binding.notificationsLBLMessage.visibility = View.VISIBLE
            }
        )
    }

    private fun renderNotifications(notifications: List<InAppNotification>) {
        binding.notificationsLAYList.removeAllViews()
        binding.notificationsLBLMessage.visibility = if (notifications.isEmpty()) View.VISIBLE else View.GONE
        binding.notificationsLBLMessage.text = "No notifications yet."
        notifications.forEach { notification ->
            binding.notificationsLAYList.addView(notificationRow(notification))
        }
    }

    private fun notificationRow(notification: InAppNotification): View {
        val context = requireContext()
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundResource(if (notification.isRead) R.drawable.bg_post_card else R.drawable.bg_card_highlight)
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
                notificationRepository.markAsRead(notification.notificationId)
                openDestination(notification)
            }
        }

        val avatar = ImageView(context).apply {
            setBackgroundResource(R.drawable.bg_avatar)
            scaleType = ImageView.ScaleType.CENTER_CROP
            layoutParams = LinearLayout.LayoutParams(42.dp(), 42.dp()).apply {
                rightMargin = 12.dp()
            }
        }
        row.addView(avatar)
        if (notification.actorProfileImageUrl.isNotBlank()) {
            Glide.with(this).load(notification.actorProfileImageUrl).circleCrop().into(avatar)
        }

        row.addView(LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            addView(TextView(context).apply {
                text = notification.title.ifBlank { typeLabel(notification.type) }
                setTextColor(context.getColor(R.color.text_primary))
                setTypeface(typeface, if (notification.isRead) Typeface.NORMAL else Typeface.BOLD)
                textSize = 14f
            })
            addView(TextView(context).apply {
                text = notification.message
                setTextColor(context.getColor(R.color.text_secondary))
                textSize = 13f
                maxLines = 2
                ellipsize = android.text.TextUtils.TruncateAt.END
                setPadding(0, 4.dp(), 0, 0)
            })
            addView(TextView(context).apply {
                text = "${typeLabel(notification.type)} / ${formatTime(notification)}"
                setTextColor(context.getColor(R.color.text_muted))
                textSize = 11f
                setPadding(0, 5.dp(), 0, 0)
            })
        })

        if (!notification.isRead) {
            row.addView(View(context).apply {
                setBackgroundResource(R.drawable.bg_unread_dot)
                layoutParams = LinearLayout.LayoutParams(9.dp(), 9.dp()).apply {
                    leftMargin = 8.dp()
                }
            })
        }

        return row
    }

    private fun openDestination(notification: InAppNotification) {
        val activity = requireActivity() as MainActivity
        when {
            notification.postId.isNotBlank() -> activity.openPost(notification.postId)
            notification.conversationId.isNotBlank() -> activity.openChat(notification.conversationId)
            notification.actorId.isNotBlank() -> activity.openUserProfile(notification.actorId)
            notification.eventId.isNotBlank() -> Toast.makeText(requireContext(), "Event details are not available yet.", Toast.LENGTH_SHORT).show()
            notification.applicationId.isNotBlank() -> Toast.makeText(requireContext(), "Application status updated.", Toast.LENGTH_SHORT).show()
            else -> Toast.makeText(requireContext(), "This item is no longer available.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun typeLabel(type: String): String {
        return when (type) {
            InAppNotification.Types.LIKE -> "Like"
            InAppNotification.Types.COMMENT -> "Comment"
            InAppNotification.Types.FOLLOW -> "New follower"
            InAppNotification.Types.PRIVATE_MESSAGE -> "Message"
            InAppNotification.Types.PROFESSIONAL_APPROVED -> "Application approved"
            InAppNotification.Types.PROFESSIONAL_REJECTED -> "Application rejected"
            InAppNotification.Types.EVENT_UPDATED -> "Event update"
            InAppNotification.Types.EVENT_RECOMMENDED -> "Recommended event"
            else -> "Notification"
        }
    }

    private fun formatTime(notification: InAppNotification): String {
        val timestamp = notification.createdAt ?: return "just now"
        return SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()).format(Date(timestamp.seconds * 1000))
    }

    override fun onDestroyView() {
        notificationsListener?.remove()
        notificationsListener = null
        super.onDestroyView()
        _binding = null
    }
}

private fun Int.dp(): Int {
    return (this * android.content.res.Resources.getSystem().displayMetrics.density).toInt()
}
