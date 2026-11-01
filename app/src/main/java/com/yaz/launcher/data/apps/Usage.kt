package com.yaz.launcher.data.apps

import android.Manifest
import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Process

/** What the phone knows of the use of one package: when it was last used and how long it stayed in front. */
data class UsageRow(val packageName: String, val lastUsed: Long, val foregroundMs: Long)

/** The window the ranking looks at: the last 7 days. */
const val USAGE_WINDOW_MS = 7L * 24 * 60 * 60 * 1000

/** The window of the foreground events that refine "last used": the last day. */
const val EVENT_WINDOW_MS = 24L * 60 * 60 * 1000

object Usage {

    /**
     * Whether the user granted usage access. The check is Taskbar's, AppOpsManager OPSTR_GET_USAGE_STATS on the app's
     * own uid (https://github.com/farmerbb/Taskbar/blob/master/app/src/main/java/com/farmerbb/taskbar/util/U.java, l.1597),
     * with the non-deprecated call, and with the rule of Android's own service for a mode left at the default: the
     * permission PACKAGE_USAGE_STATS decides
     * (https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/services/usage/java/com/android/server/usage/UsageStatsService.java,
     * BinderService.hasQueryPermission). Granting it over ADB (`appops set ... GET_USAGE_STATS allow`) passes.
     */
    fun granted(context: Context): Boolean {
        val ops = context.getSystemService(AppOpsManager::class.java) ?: return false
        val mode = ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        return if (mode == AppOpsManager.MODE_DEFAULT) {
            context.checkCallingOrSelfPermission(Manifest.permission.PACKAGE_USAGE_STATS) == PackageManager.PERMISSION_GRANTED
        } else {
            mode == AppOpsManager.MODE_ALLOWED
        }
    }

    /**
     * The use of every package over the last 7 days: UsageStatsManager.queryUsageStats at INTERVAL_BEST, as Taskbar's
     * getAppEntriesUsingUsageStats does (TaskbarController.java, l.1712), the buckets of one package merged
     * ([merge]), then the foreground events of the last day: an app moved to the front after its bucket was written
     * counts as used at that moment (Taskbar's loop on MOVE_TO_FOREGROUND, TaskbarController.java, l.1145, with its
     * API 29 name ACTIVITY_RESUMED). Off the main thread. Empty without usage access.
     */
    fun read(context: Context, now: Long = System.currentTimeMillis()): List<UsageRow> {
        if (!granted(context)) return emptyList()
        val manager = context.getSystemService(UsageStatsManager::class.java) ?: return emptyList()
        val buckets = runCatching {
            manager.queryUsageStats(UsageStatsManager.INTERVAL_BEST, now - USAGE_WINDOW_MS, now)
        }.getOrNull().orEmpty().map { UsageRow(it.packageName, it.lastTimeUsed, it.totalTimeInForeground) }
        return merge(buckets, lastForegrounds(manager, now - EVENT_WINDOW_MS, now))
    }

    /** The last time each package came to the front between [begin] and [end]. */
    private fun lastForegrounds(manager: UsageStatsManager, begin: Long, end: Long): List<UsageRow> {
        val events = runCatching { manager.queryEvents(begin, end) }.getOrNull() ?: return emptyList()
        val last = HashMap<String, Long>()
        val event = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED) {
                last[event.packageName] = maxOf(last[event.packageName] ?: 0L, event.timeStamp)
            }
        }
        return last.map { (pkg, time) -> UsageRow(pkg, time, 0L) }
    }

    /**
     * One row per package out of the buckets and events of every package. The rule is UsageStats.add, which
     * UsageStatsManager.queryAndAggregateUsageStats applies to the buckets of INTERVAL_BEST: the last use is the latest,
     * the time in the foreground is the sum
     * (https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/core/java/android/app/usage/UsageStats.java, add).
     */
    internal fun merge(vararg parts: List<UsageRow>): List<UsageRow> {
        val byPackage = LinkedHashMap<String, UsageRow>()
        for (row in parts.asSequence().flatten()) {
            val known = byPackage[row.packageName]
            byPackage[row.packageName] = if (known == null) row else UsageRow(
                row.packageName,
                maxOf(known.lastUsed, row.lastUsed),
                known.foregroundMs + row.foregroundMs
            )
        }
        return byPackage.values.toList()
    }

    /** Time in the foreground per package, for [AppRanking.rank]. */
    fun foregroundByPackage(rows: List<UsageRow>): Map<String, Long> = rows.filter { it.foregroundMs > 0L }.associate { it.packageName to it.foregroundMs }
}
