package com.example.magneticclock

import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.annotation.RequiresApi
import androidx.compose.runtime.mutableStateListOf

@RequiresApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
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
            // Some devices might throw an exception if the listener is not yet fully initialized
            val currentNotifications = try { activeNotifications } catch (_: Exception) { null }
            
            notificationList.clear()
            if (currentNotifications != null) {
                notificationList.addAll(currentNotifications.toList())
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
