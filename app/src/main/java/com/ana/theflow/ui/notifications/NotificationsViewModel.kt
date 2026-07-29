package com.ana.theflow.ui.notifications

import androidx.lifecycle.ViewModel
import com.ana.theflow.data.model.notification.InAppNotification

class NotificationsViewModel : ViewModel() {
    var notifications: List<InAppNotification> = emptyList()
    var error: String = ""
}
