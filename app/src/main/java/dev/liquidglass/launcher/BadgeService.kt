package dev.liquidglass.launcher

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

/** Optional: counts active notifications per app to draw iOS-style badges. */
class BadgeService : NotificationListenerService() {
    override fun onListenerConnected() = recount()
    override fun onNotificationPosted(sbn: StatusBarNotification?) = recount()
    override fun onNotificationRemoved(sbn: StatusBarNotification?) = recount()
    override fun onListenerDisconnected() { BadgeCenter.counts.clear() }

    private fun recount() {
        val counts = HashMap<String, Int>()
        val active = runCatching { activeNotifications }.getOrNull() ?: return
        for (n in active) {
            if (n.isOngoing || !n.isClearable) continue
            if (n.notification.flags and Notification.FLAG_GROUP_SUMMARY != 0) continue
            counts[n.packageName] = (counts[n.packageName] ?: 0) + maxOf(1, n.notification.number)
        }
        BadgeCenter.counts.keys.filter { it !in counts }.forEach { BadgeCenter.counts.remove(it) }
        counts.forEach { (k, v) -> if (BadgeCenter.counts[k] != v) BadgeCenter.counts[k] = v }
    }
}
