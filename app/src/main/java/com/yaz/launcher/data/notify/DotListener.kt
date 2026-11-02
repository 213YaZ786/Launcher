package com.yaz.launcher.data.notify

// The listener behind the notification dots. Ported from Trebuchet's NotificationListener
// (https://github.com/LineageOS/android_packages_apps_Trebuchet/blob/lineage-22.2/src/com/android/launcher3/notification/NotificationListener.java):
// the callbacks only post to a worker thread; the worker handles one notification at a time (posted, removed, ranking update) and makes the full
// refresh only when the listener connects; an empty answer on disconnect; the dots' setting watched, and the service asks to unbind when it is off.
// Differences, on purpose: the worker hands its result straight to NotificationDots (PopupDataProvider's map and DotInfo), with no UI handler between,
// the dots being a StateFlow; the group maps are kept as Trebuchet keeps them, though only the dot is drawn here (no popup cancels a summary).
// Nothing of a notification is kept, logged or written but its key and count: title and text are looked at for emptiness and dropped.
import android.app.Notification
import android.database.ContentObserver
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.Message
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

class DotListener : NotificationListenerService() {

    private var thread: HandlerThread? = null
    private var worker: Handler? = null
    private var observer: ContentObserver? = null
    private val tempRanking = Ranking()

    /** Maps groupKey's to the corresponding group of notifications. */
    private val notificationGroupMap = HashMap<String, NotificationGroup>()

    /** Maps keys to their corresponding current group key. */
    private val notificationGroupKeyMap = HashMap<String, String>()

    override fun onCreate() {
        super.onCreate()
        val t = HandlerThread("dots").also { it.start() }
        thread = t
        worker = Handler(t.looper) { message -> handleWorkerMessage(message) }
    }

    override fun onDestroy() {
        thread?.quitSafely()
        thread = null
        worker = null
        super.onDestroy()
    }

    // NotificationListener.handleWorkerMessage.
    private fun handleWorkerMessage(message: Message): Boolean {
        when (message.what) {
            MSG_NOTIFICATION_POSTED -> {
                val sbn = message.obj as StatusBarNotification
                if (notificationIsValidForUI(sbn)) {
                    NotificationDots.onNotificationPosted(DotKey(sbn.packageName, sbn.user), NotificationKeyData.fromNotification(sbn))
                } else {
                    NotificationDots.onNotificationRemoved(DotKey(sbn.packageName, sbn.user), NotificationKeyData.fromNotification(sbn))
                }
                return true
            }
            MSG_NOTIFICATION_REMOVED -> {
                val sbn = message.obj as StatusBarNotification
                NotificationDots.onNotificationRemoved(DotKey(sbn.packageName, sbn.user), NotificationKeyData.fromNotification(sbn))

                val notificationGroup = notificationGroupMap[sbn.groupKey]
                val key = sbn.key
                // Added: a gone notification leaves the key map too (Trebuchet's keeps it for the life of the process).
                notificationGroupKeyMap.remove(key)
                if (notificationGroup != null) {
                    notificationGroup.removeChildKey(key)
                    if (notificationGroup.isEmpty()) {
                        notificationGroupMap.remove(sbn.groupKey)
                    }
                }
                return true
            }
            MSG_NOTIFICATION_FULL_REFRESH -> {
                val activeNotifications = if (NotificationDots.connected) {
                    getActiveNotificationsSafely(null).filter(::notificationIsValidForUI)
                } else {
                    emptyList()
                }
                NotificationDots.onNotificationFullRefresh(activeNotifications)
                return true
            }
            MSG_RANKING_UPDATE -> {
                // Trebuchet only updates the group keys here. A ranking change can also allow or forbid a badge (a channel turned off): the
                // validity is checked again for each notification, and only a package whose dot changes is published.
                val keys = (message.obj as RankingMap).orderedKeys
                for (sbn in getActiveNotificationsSafely(keys)) {
                    if (notificationIsValidForUI(sbn)) {
                        NotificationDots.onNotificationPosted(DotKey(sbn.packageName, sbn.user), NotificationKeyData.fromNotification(sbn))
                    } else {
                        NotificationDots.onNotificationRemoved(DotKey(sbn.packageName, sbn.user), NotificationKeyData.fromNotification(sbn))
                    }
                }
                return true
            }
        }
        return false
    }

    // getActiveNotificationsSafely: a refusal or a null answer gives none.
    private fun getActiveNotificationsSafely(keys: Array<String>?): List<StatusBarNotification> =
        try {
            getActiveNotifications(keys)?.toList() ?: emptyList()
        } catch (_: SecurityException) {
            emptyList()
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

        onNotificationFullRefresh()
    }

    // onNotificationSettingsChanged: dots off while connected, so unbind.
    private fun onSettingsChanged() {
        if (!NotificationDots.badgingOn(this) && NotificationDots.connected) requestUnbind()
    }

    private fun onNotificationFullRefresh() {
        worker?.obtainMessage(MSG_NOTIFICATION_FULL_REFRESH)?.sendToTarget()
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        NotificationDots.connected = false
        observer?.let { contentResolver.unregisterContentObserver(it) }
        observer = null
        // The empty answer of the disconnected listener: the worker finds it not connected.
        onNotificationFullRefresh()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn != null) worker?.obtainMessage(MSG_NOTIFICATION_POSTED, sbn)?.sendToTarget()
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        if (sbn != null) worker?.obtainMessage(MSG_NOTIFICATION_REMOVED, sbn)?.sendToTarget()
    }

    override fun onNotificationRankingUpdate(rankingMap: RankingMap?) {
        if (rankingMap != null) worker?.obtainMessage(MSG_RANKING_UPDATE, rankingMap)?.sendToTarget()
    }

    // updateGroupKeyIfNecessary: keeps the group of each notification current, to know when a summary has no child left.
    private fun updateGroupKeyIfNecessary(sbn: StatusBarNotification) {
        val childKey = sbn.key
        val oldGroupKey = notificationGroupKeyMap[childKey]
        val newGroupKey = sbn.groupKey
        if (oldGroupKey == null || oldGroupKey != newGroupKey) {
            // The group key has changed.
            if (newGroupKey != null) notificationGroupKeyMap[childKey] = newGroupKey
            if (oldGroupKey != null && notificationGroupMap.containsKey(oldGroupKey)) {
                // Remove the child key from the old group.
                val oldGroup = notificationGroupMap.getValue(oldGroupKey)
                oldGroup.removeChildKey(childKey)
                if (oldGroup.isEmpty()) {
                    notificationGroupMap.remove(oldGroupKey)
                }
            }
        }
        if (sbn.isGroup && newGroupKey != null) {
            // Maintain group info so we can cancel the summary when the last child is canceled.
            var notificationGroup = notificationGroupMap[newGroupKey]
            if (notificationGroup == null) {
                notificationGroup = NotificationGroup()
                notificationGroupMap[newGroupKey] = notificationGroup
            }
            val isGroupSummary = (sbn.notification.flags and Notification.FLAG_GROUP_SUMMARY) != 0
            if (isGroupSummary) {
                notificationGroup.groupSummaryKey = childKey
            } else {
                notificationGroup.addChildKey(childKey)
            }
        }
    }

    // notificationIsValidForUI: the group key first, then the ranking's say, then the notification's own flags; the filter is DotFilter.valid.
    private fun notificationIsValidForUI(sbn: StatusBarNotification): Boolean {
        val notification = sbn.notification ?: return false
        updateGroupKeyIfNecessary(sbn)

        if (!currentRanking.getRanking(sbn.key, tempRanking)) return false
        val extras = notification.extras
        return DotFilter.valid(
            canShowBadge = tempRanking.canShowBadge(),
            channelId = tempRanking.channel?.id,
            flags = notification.flags,
            hasTitle = !extras?.getCharSequence(Notification.EXTRA_TITLE).isNullOrEmpty(),
            hasText = !extras?.getCharSequence(Notification.EXTRA_TEXT).isNullOrEmpty()
        )
    }

    private companion object {
        const val MSG_NOTIFICATION_POSTED = 1
        const val MSG_NOTIFICATION_REMOVED = 2
        const val MSG_NOTIFICATION_FULL_REFRESH = 3
        const val MSG_RANKING_UPDATE = 4
    }
}

/** NotificationGroup: data related to a group of notifications, the group summary key and the child keys. */
private class NotificationGroup {
    var groupSummaryKey: String? = null
    private val childKeys = HashSet<String>()

    fun addChildKey(childKey: String) {
        childKeys.add(childKey)
    }

    fun removeChildKey(childKey: String) {
        childKeys.remove(childKey)
    }

    fun isEmpty(): Boolean = childKeys.isEmpty()
}
