package com.yaz.launcher.data.notify

// JUnit 4 (https://junit.org/junit4/). The cases follow DotInfo's own comments: "Returns whether the notification was added or its count changed",
// "Returns whether the notification was removed (false if it didn't exist)"
// (https://github.com/LineageOS/android_packages_apps_Trebuchet/blob/lineage-22.2/src/com/android/launcher3/dot/DotInfo.java).
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DotInfoTest {

    @Test
    fun aNewKeyIsAddedAndCounted() {
        val dot = DotInfo()
        assertTrue(dot.addOrUpdateNotificationKey(NotificationKeyData("a", 0)))
        assertEquals(1, dot.totalCount)
        assertTrue(dot.addOrUpdateNotificationKey(NotificationKeyData("b", 3)))
        assertEquals(4, dot.totalCount)
    }

    @Test
    fun theSameKeyWithTheSameCountChangesNothing() {
        val dot = DotInfo()
        dot.addOrUpdateNotificationKey(NotificationKeyData("a", 2))
        assertFalse(dot.addOrUpdateNotificationKey(NotificationKeyData("a", 2)))
        assertEquals(2, dot.totalCount)
        assertEquals(1, dot.getNotificationKeys().size)
    }

    @Test
    fun theSameKeyWithAnotherCountIsUpdated() {
        val dot = DotInfo()
        dot.addOrUpdateNotificationKey(NotificationKeyData("a", 2))
        assertTrue(dot.addOrUpdateNotificationKey(NotificationKeyData("a", 5)))
        assertEquals(5, dot.totalCount)
        assertEquals(1, dot.getNotificationKeys().size)
    }

    @Test
    fun aKeyIsRemovedOnceAndTakesItsStoredCount() {
        val dot = DotInfo()
        dot.addOrUpdateNotificationKey(NotificationKeyData("a", 4))
        dot.addOrUpdateNotificationKey(NotificationKeyData("b", 1))
        // The key that comes in carries another count: the stored one is taken off.
        assertTrue(dot.removeNotificationKey(NotificationKeyData("a", 1)))
        assertEquals(1, dot.totalCount)
        assertFalse(dot.removeNotificationKey(NotificationKeyData("a", 1)))
        assertEquals(1, dot.totalCount)
    }
}
