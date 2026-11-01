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
import android.os.UserHandle
import android.provider.Settings
import android.service.notification.NotificationListenerService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** The key of a dot: an app in one profile (Trebuchet's PackageUserKey). */
data class DotKey(val packageName: String, val user: UserHandle)

object NotificationDots {

    private val _keys = MutableStateFlow<Set<DotKey>>(emptySet())

    /** The apps with a dot now. Empty while the listener is not bound (access not granted, or dots turned off in Android's settings). */
    val keys: StateFlow<Set<DotKey>> = _keys.asStateFlow()

    /** NotificationListener.sIsConnected. */
    @Volatile
    internal var connected = false

    internal fun publish(dots: Set<DotKey>) {
        _keys.value = dots
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
