package com.example.magneticclock

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.compose.runtime.mutableStateListOf

class NotificationService : NotificationListenerService() {

    companion object {
        val notificationList = mutableStateListOf<StatusBarNotification>()
    }

    override fun onListenerConnected() {
        refreshNotifications()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        refreshNotifications()
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        refreshNotifications()
    }

    private fun refreshNotifications() {
        try {
            val notifications = activeNotifications
            notificationList.clear()
            if (notifications != null) {
                notificationList.addAll(notifications.toList())
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
