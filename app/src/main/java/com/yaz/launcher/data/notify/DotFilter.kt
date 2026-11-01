package com.yaz.launcher.data.notify

import android.app.Notification
import android.app.NotificationChannel

/** Which notifications deserve a dot, with plain values so it runs on the JVM. */
object DotFilter {

    /**
     * notificationIsValidForUI of Trebuchet's NotificationListener: a notification the ranking does not let badge is out; an ongoing one on
     * the default, legacy "Miscellaneous" channel is out; a group summary is out; one with no title and no text is out
     * (https://github.com/LineageOS/android_packages_apps_Trebuchet/blob/lineage-22.2/src/com/android/launcher3/notification/NotificationListener.java).
     */
    fun valid(canShowBadge: Boolean, channelId: String?, flags: Int, hasTitle: Boolean, hasText: Boolean): Boolean {
        if (!canShowBadge) return false
        if (channelId == NotificationChannel.DEFAULT_CHANNEL_ID && flags and Notification.FLAG_ONGOING_EVENT != 0) return false
        val missingTitleAndText = !hasTitle && !hasText
        val isGroupHeader = flags and Notification.FLAG_GROUP_SUMMARY != 0
        return !isGroupHeader && !missingTitleAndText
    }
}
