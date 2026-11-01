package com.yaz.launcher.data.notify

// The listener behind the notification dots. Ported from Trebuchet's NotificationListener
// (https://github.com/LineageOS/android_packages_apps_Trebuchet/blob/lineage-22.2/src/com/android/launcher3/notification/NotificationListener.java):
// the callbacks only post to a worker thread; a full refresh on connect, an empty answer on disconnect; the dots' setting watched,
// and the service asks to unbind when it is off. Differences, on purpose: Trebuchet keeps each notification's key per app to add and remove one by one;
// here every change recomputes the set of apps from the active notifications (one refresh queued at most), so nothing but the set of
// package and profile keys is ever held; its group bookkeeping and popup data are left out (only the dot is drawn).
// Nothing of a notification is kept, logged or written: title and text are looked at for emptiness and dropped.
import android.app.Notification
import android.database.ContentObserver
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.Process
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

class DotListener : NotificationListenerService() {

    private var thread: HandlerThread? = null
    private var worker: Handler? = null
    private var observer: ContentObserver? = null
    private val ranking = Ranking()

    override fun onCreate() {
        super.onCreate()
        val t = HandlerThread("dots").also { it.start() }
        thread = t
        worker = Handler(t.looper) { message -> handleWorkerMessage(message.what) }
    }

    override fun onDestroy() {
        thread?.quitSafely()
        thread = null
        worker = null
        super.onDestroy()
    }

    // The worker's work: the full refresh (MSG_NOTIFICATION_FULL_REFRESH).
    private fun handleWorkerMessage(what: Int): Boolean {
        if (what != MSG_REFRESH) return false
        val valid = if (NotificationDots.connected) {
            runCatching { (getActiveNotifications() ?: emptyArray()).filter(::validForDot) }.getOrDefault(emptyList())
        } else {
            emptyList()
        }
        // The count of a package: its owner's notifications, each Notification.number or 1 (NotificationKeyData's count, DotInfo's total).
        val mine = Process.myUserHandle()
        val counts = valid.filter { it.user == mine }.groupBy { it.packageName }
            .mapValues { (_, list) -> list.sumOf { (it.notification?.number ?: 0).coerceAtLeast(1) } }
        NotificationDots.publish(valid.map { DotKey(it.packageName, it.user) }.toSet(), counts)
        return true
    }

    // notificationIsValidForUI: the ranking's say first, then the notification's own flags; the filter is DotFilter.valid.
    private fun validForDot(sbn: StatusBarNotification): Boolean {
        val notification = sbn.notification ?: return false
        if (!currentRanking.getRanking(sbn.key, ranking)) return false
        val extras = notification.extras
        return DotFilter.valid(
            canShowBadge = ranking.canShowBadge(),
            channelId = ranking.channel?.id,
            flags = notification.flags,
            hasTitle = !extras?.getCharSequence(Notification.EXTRA_TITLE).isNullOrEmpty(),
            hasText = !extras?.getCharSequence(Notification.EXTRA_TEXT).isNullOrEmpty()
        )
    }

    // onNotificationFullRefresh: one message on the worker, none queued twice.
    private fun refresh() {
        val handler = worker ?: return
        if (!handler.hasMessages(MSG_REFRESH)) handler.sendEmptyMessage(MSG_REFRESH)
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        NotificationDots.connected = true
        // Trebuchet's SettingsCache observer on notification_badging: turned off, the service unbinds itself.
        val watcher = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) = onSettingsChanged()
        }
        observer = watcher
        contentResolver.registerContentObserver(Settings.Secure.getUriFor(NotificationDots.BADGING), false, watcher)
        onSettingsChanged()
        refresh()
    }

    // onNotificationSettingsChanged: dots off while connected, so unbind.
    private fun onSettingsChanged() {
        if (!NotificationDots.badgingOn(this) && NotificationDots.connected) requestUnbind()
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        NotificationDots.connected = false
        observer?.let { contentResolver.unregisterContentObserver(it) }
        observer = null
        // The empty answer of the disconnected listener.
        NotificationDots.publish(emptySet())
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) = refresh()

    override fun onNotificationRemoved(sbn: StatusBarNotification?) = refresh()

    // A channel or ranking change can allow or forbid a badge.
    override fun onNotificationRankingUpdate(rankingMap: RankingMap?) = refresh()

    private companion object {
        const val MSG_REFRESH = 3
    }
}
