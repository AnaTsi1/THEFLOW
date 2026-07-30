package com.ana.theflow.utilities

import com.ana.theflow.data.model.notification.InAppNotification
import com.ana.theflow.data.model.settings.NotificationSettings

object NotificationPreferenceUtils {
    fun isTypeEnabled(type: String, settings: NotificationSettings): Boolean {
        if (!settings.allNotificationsEnabled) return false
        return when (type) {
            InAppNotification.Types.LIKE -> settings.likes
            InAppNotification.Types.COMMENT -> settings.comments
            InAppNotification.Types.FOLLOW -> settings.newFollowers
            InAppNotification.Types.PRIVATE_MESSAGE -> settings.privateMessages
            InAppNotification.Types.EVENT_RECOMMENDED -> settings.eventRecommendations
            InAppNotification.Types.EVENT_UPDATED -> settings.registeredEventUpdates
            InAppNotification.Types.PROFESSIONAL_APPROVED,
            InAppNotification.Types.PROFESSIONAL_REJECTED -> settings.professionalApplicationUpdates
            else -> true
        }
    }
}
