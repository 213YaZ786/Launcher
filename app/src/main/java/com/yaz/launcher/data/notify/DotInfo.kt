package com.yaz.launcher.data.notify

// The data of one notification dot. Ported from Trebuchet's DotInfo and NotificationKeyData
// (https://github.com/LineageOS/android_packages_apps_Trebuchet/blob/lineage-22.2/src/com/android/launcher3/dot/DotInfo.java,
// https://github.com/LineageOS/android_packages_apps_Trebuchet/blob/lineage-22.2/src/com/android/launcher3/notification/NotificationKeyData.java).
// Only keys and counts are kept: never a notification's title, text, extras or intent. Left out: MAX_COUNT (999) and getNotificationCount's cap
// (the panel header shows its own "99+"), the shortcut id and the person keys (no pinned-shortcut dots here).
import android.service.notification.StatusBarNotification

/** NotificationKeyData: a notification's key and its count (Notification.number, at least 1). */
class NotificationKeyData(val notificationKey: String, count: Int) {
    var count: Int = Math.max(1, count)

    override fun equals(other: Any?): Boolean {
        if (other !is NotificationKeyData) return false
        // Only compare the keys.
        return other.notificationKey == notificationKey
    }

    override fun hashCode(): Int = notificationKey.hashCode()

    companion object {
        fun fromNotification(sbn: StatusBarNotification): NotificationKeyData =
            NotificationKeyData(sbn.key, sbn.notification?.number ?: 0)
    }
}

/** DotInfo: contains data to be used for a notification dot. */
class DotInfo {

    /** The keys of the notifications that this dot represents. */
    private val notificationKeys = ArrayList<NotificationKeyData>()

    /** The current sum of the counts in [notificationKeys], updated whenever a key is added or removed. */
    var totalCount = 0
        private set

    /** Returns whether the notification was added or its count changed. */
    fun addOrUpdateNotificationKey(notificationKey: NotificationKeyData): Boolean {
        val indexOfPrevKey = notificationKeys.indexOf(notificationKey)
        val prevKey = if (indexOfPrevKey == -1) null else notificationKeys[indexOfPrevKey]
        if (prevKey != null) {
            if (prevKey.count == notificationKey.count) return false
            // Notification was updated with a new count.
            totalCount -= prevKey.count
            totalCount += notificationKey.count
            prevKey.count = notificationKey.count
            return true
        }
        val added = notificationKeys.add(notificationKey)
        if (added) totalCount += notificationKey.count
        return added
    }

    /**
     * Returns whether the notification was removed (false if it didn't exist). Difference: the count taken off is the stored key's, where
     * Trebuchet takes off the count of the key that comes in, which differs when the notification's number changed since it was added.
     */
    fun removeNotificationKey(notificationKey: NotificationKeyData): Boolean {
        val index = notificationKeys.indexOf(notificationKey)
        if (index == -1) return false
        totalCount -= notificationKeys.removeAt(index).count
        return true
    }

    fun getNotificationKeys(): List<NotificationKeyData> = notificationKeys

    override fun toString(): String = totalCount.toString()
}
