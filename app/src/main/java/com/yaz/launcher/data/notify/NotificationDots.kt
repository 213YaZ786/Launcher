package com.yaz.launcher.data.notify

// The notification dots' state: the set of apps (package and profile) that have a notification worth a dot, and what the model and the guide ask about
// the access. Only keys are kept: never a notification's title, text, extras or intent, nothing logged, nothing written to disk.
// Sources: Trebuchet's PackageUserKey, the key of a dot (package and UserHandle,
// https://github.com/LineageOS/android_packages_apps_Trebuchet/blob/lineage-22.2/src/com/android/launcher3/util/PackageUserKey.java);
// NotificationListener's static connected flag and its listeners' full refresh, here a StateFlow
// (https://github.com/LineageOS/android_packages_apps_Trebuchet/blob/lineage-22.2/src/com/android/launcher3/notification/NotificationListener.java).
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.os.Process
import android.os.UserHandle
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** The key of a dot: an app in one profile (Trebuchet's PackageUserKey). */
data class DotKey(val packageName: String, val user: UserHandle)

object NotificationDots {

    private val _keys = MutableStateFlow<Set<DotKey>>(emptySet())

    /** The apps with a dot now. Empty while the listener is not bound (access not granted, or dots turned off in Android's settings). */
    val keys: StateFlow<Set<DotKey>> = _keys.asStateFlow()

    private val _counts = MutableStateFlow<Map<String, Int>>(emptyMap())

    /**
     * How many notifications each app of the owner has now, by package: each active notification that deserves a dot counts its
     * Notification.number, or 1 when that is 0 (Trebuchet's NotificationKeyData.count, summed by DotInfo.getNotificationCount,
     * https://github.com/LineageOS/android_packages_apps_Trebuchet/blob/lineage-22.2/src/com/android/launcher3/dot/DotInfo.java).
     * Only numbers are kept. Empty while the listener is not bound; the panel header's missed calls and unread messages read it.
     */
    val counts: StateFlow<Map<String, Int>> = _counts.asStateFlow()

    /** NotificationListener.sIsConnected. */
    @Volatile
    internal var connected = false

    /** PopupDataProvider.mPackageUserToDotInfos: the dot of each app in each profile, with the keys of the notifications it stands for. */
    private val packageUserToDotInfos = HashMap<DotKey, DotInfo>()

    /**
     * PopupDataProvider.updateNotificationDots: the flows publish the apps with a dot and their counts. Called only when a package's dot
     * changed (a key added, removed or given a new count), where Trebuchet hands the changed keys to the views that redraw them.
     */
    private fun updateNotificationDots() {
        val mine = Process.myUserHandle()
        _keys.value = packageUserToDotInfos.keys.toSet()
        // The count of a package: its owner's notifications, each Notification.number or 1 (DotInfo's total).
        _counts.value = packageUserToDotInfos.filterKeys { it.user == mine }.entries.associate { (key, dot) -> key.packageName to dot.totalCount }
    }

    /**
     * PopupDataProvider.onNotificationPosted
     * (https://github.com/LineageOS/android_packages_apps_Trebuchet/blob/lineage-22.2/src/com/android/launcher3/popup/PopupDataProvider.java).
     * Called on the listener's worker thread; the lock keeps the map whole.
     */
    @Synchronized
    internal fun onNotificationPosted(postedPackageUserKey: DotKey, notificationKey: NotificationKeyData) {
        var dotInfo = packageUserToDotInfos[postedPackageUserKey]
        if (dotInfo == null) {
            dotInfo = DotInfo()
            packageUserToDotInfos[postedPackageUserKey] = dotInfo
        }
        if (dotInfo.addOrUpdateNotificationKey(notificationKey)) {
            updateNotificationDots()
        }
    }

    /** PopupDataProvider.onNotificationRemoved. */
    @Synchronized
    internal fun onNotificationRemoved(removedPackageUserKey: DotKey, notificationKey: NotificationKeyData) {
        val oldDotInfo = packageUserToDotInfos[removedPackageUserKey]
        if (oldDotInfo != null && oldDotInfo.removeNotificationKey(notificationKey)) {
            if (oldDotInfo.getNotificationKeys().isEmpty()) {
                packageUserToDotInfos.remove(removedPackageUserKey)
            }
            updateNotificationDots()
        }
    }

    /** PopupDataProvider.onNotificationFullRefresh: the dots are made again from [activeNotifications], published if one differs. */
    @Synchronized
    internal fun onNotificationFullRefresh(activeNotifications: List<StatusBarNotification>) {
        // This will contain the DotKeys which have updated dots.
        val updatedDots = HashMap(packageUserToDotInfos)
        packageUserToDotInfos.clear()
        for (notification in activeNotifications) {
            val packageUserKey = DotKey(notification.packageName, notification.user)
            var dotInfo = packageUserToDotInfos[packageUserKey]
            if (dotInfo == null) {
                dotInfo = DotInfo()
                packageUserToDotInfos[packageUserKey] = dotInfo
            }
            dotInfo.addOrUpdateNotificationKey(NotificationKeyData.fromNotification(notification))
        }

        // Add and remove from updatedDots so it contains the DotKeys of updated dots.
        for (packageUserKey in packageUserToDotInfos.keys) {
            val prevDot = updatedDots[packageUserKey]
            val newDot = packageUserToDotInfos.getValue(packageUserKey)
            if (prevDot == null || prevDot.totalCount != newDot.totalCount) {
                updatedDots[packageUserKey] = newDot
            } else {
                // No need to update the dot if it already existed (no visual change).
                updatedDots.remove(packageUserKey)
            }
        }

        if (updatedDots.isNotEmpty()) updateNotificationDots()
    }

    /** The listener, as the manifest declares it. */
    fun component(context: Context): ComponentName = ComponentName(context, DotListener::class.java)

    /**
     * Whether the user gave the listener access: NotificationManager.isNotificationListenerAccessGranted (API 27,
     * https://developer.android.com/reference/android/app/NotificationManager#isNotificationListenerAccessGranted(android.content.ComponentName)).
     */
    fun accessGranted(context: Context): Boolean =
        context.getSystemService(NotificationManager::class.java)?.isNotificationListenerAccessGranted(component(context)) == true

    /**
     * Whether Android's "Notification dots" setting is on. SettingsCache's NOTIFICATION_BADGING_URI is Settings.Secure "notification_badging",
     * on unless the value is 0 (https://github.com/LineageOS/android_packages_apps_Trebuchet/blob/lineage-22.2/src/com/android/launcher3/util/SettingsCache.java).
     */
    fun badgingOn(context: Context): Boolean = Settings.Secure.getInt(context.contentResolver, BADGING, 1) == 1

    /**
     * Asks Android to bind the listener again when access is granted, dots are on and it is not bound: after [DotListener] asked to unbind
     * because dots were off, nothing binds it again by itself. NotificationListenerService.requestRebind
     * (https://developer.android.com/reference/android/service/notification/NotificationListenerService#requestRebind(android.content.ComponentName)).
     * Called when a panel opens, never on a timer.
     */
    fun rebindIfNeeded(context: Context) {
        if (!connected && accessGranted(context) && badgingOn(context)) {
            runCatching { NotificationListenerService.requestRebind(component(context)) }
        }
    }

    internal const val BADGING = "notification_badging"
}
