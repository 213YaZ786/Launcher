package com.yaz.launcher.data.notify

// JUnit 4 (https://junit.org/junit4/). The cases are the four tests of Trebuchet's notificationIsValidForUI
// (https://github.com/LineageOS/android_packages_apps_Trebuchet/blob/lineage-22.2/src/com/android/launcher3/notification/NotificationListener.java).
import android.app.Notification
import android.app.NotificationChannel
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DotFilterTest {

    @Test
    fun anOrdinaryNotificationGetsADot() {
        assertTrue(DotFilter.valid(true, "messages", 0, hasTitle = true, hasText = true))
        assertTrue(DotFilter.valid(true, "messages", 0, hasTitle = false, hasText = true))
    }

    @Test
    fun aChannelThatCannotBadgeGetsNone() {
        assertFalse(DotFilter.valid(false, "messages", 0, hasTitle = true, hasText = true))
    }

    @Test
    fun anOngoingOneOnTheLegacyChannelGetsNone() {
        assertFalse(DotFilter.valid(true, NotificationChannel.DEFAULT_CHANNEL_ID, Notification.FLAG_ONGOING_EVENT, hasTitle = true, hasText = true))
        // On another channel an ongoing notification counts.
        assertTrue(DotFilter.valid(true, "playback", Notification.FLAG_ONGOING_EVENT, hasTitle = true, hasText = true))
    }

    @Test
    fun aGroupSummaryAndAnEmptyOneGetNone() {
        assertFalse(DotFilter.valid(true, "messages", Notification.FLAG_GROUP_SUMMARY, hasTitle = true, hasText = true))
        assertFalse(DotFilter.valid(true, "messages", 0, hasTitle = false, hasText = false))
    }
}
