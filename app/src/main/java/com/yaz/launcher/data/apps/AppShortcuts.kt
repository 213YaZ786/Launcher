package com.yaz.launcher.data.apps

import android.content.ComponentName
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.pm.LauncherApps
import android.content.pm.ShortcutInfo
import android.os.Bundle
import android.os.UserHandle
import android.widget.Toast
import com.yaz.launcher.R

/**
 * The shortcuts an app publishes (the entries of its long press menu: "New message", "Compose"), at most four.
 * Android hands them only to the default home app: elsewhere [hasHostPermission] is false and the query is empty.
 */
object AppShortcuts {

    /** PopupPopulator.MAX_SHORTCUTS. */
    const val MAX_SHORTCUTS = 4

    /** PopupPopulator.NUM_DYNAMIC: dynamic shortcuts kept when more than four are published. */
    internal const val NUM_DYNAMIC = 2

    /**
     * Whether this app is the shortcut host, i.e. the default home. After PackageManagerHelper.hasShortcutsPermission
     * (https://github.com/LineageOS/android_packages_apps_Trebuchet/blob/lineage-22.2/src/com/android/launcher3/util/PackageManagerHelper.java):
     * LauncherApps.hasShortcutHostPermission, a SecurityException or IllegalStateException meaning no.
     */
    fun hasHostPermission(context: Context): Boolean = try {
        context.getSystemService(LauncherApps::class.java).hasShortcutHostPermission()
    } catch (_: SecurityException) {
        false
    } catch (_: IllegalStateException) {
        false
    }

    /**
     * The published shortcuts (dynamic and from the manifest) of [activity] in [user], sorted and cut to four. After
     * ShortcutRequest.query with ShortcutRequest.PUBLISHED, `forPackage` then `withContainer`
     * (https://github.com/LineageOS/android_packages_apps_Trebuchet/blob/lineage-22.2/src/com/android/launcher3/shortcuts/ShortcutRequest.java),
     * as PopupPopulator.createUpdateRunnable calls it. A locked profile or a refusal gives an empty list. Off the main thread.
     */
    fun query(context: Context, activity: ComponentName, user: UserHandle): List<ShortcutInfo> {
        val query = LauncherApps.ShortcutQuery()
            .setPackage(activity.packageName)
            .setActivity(activity)
            .setQueryFlags(LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST)
        val found = try {
            context.getSystemService(LauncherApps::class.java).getShortcuts(query, user)
        } catch (_: SecurityException) {
            null
        } catch (_: IllegalStateException) {
            null
        }
        return sortAndFilter(found.orEmpty())
    }

    /** [sortAndFilter] on Android's own class. */
    fun sortAndFilter(shortcuts: List<ShortcutInfo>): List<ShortcutInfo> =
        sortAndFilter(shortcuts, { it.isDeclaredInManifest }, { it.isDynamic }, { it.rank })

    /**
     * PopupPopulator.sortAndFilterShortcuts
     * (https://github.com/LineageOS/android_packages_apps_Trebuchet/blob/lineage-22.2/src/com/android/launcher3/popup/PopupPopulator.java):
     * sorted by rank with the manifest shortcuts before the dynamic ones; with more than four, the first four stay,
     * except that two dynamic shortcuts, when there are that many, replace the last static ones. Written over
     * accessors so it runs on the JVM.
     */
    fun <T> sortAndFilter(shortcuts: List<T>, manifest: (T) -> Boolean, dynamic: (T) -> Boolean, rank: (T) -> Int): List<T> {
        val sorted = shortcuts.sortedWith { a, b ->
            when {
                manifest(a) && !manifest(b) -> -1
                !manifest(a) && manifest(b) -> 1
                else -> rank(a).compareTo(rank(b))
            }
        }
        if (sorted.size <= MAX_SHORTCUTS) return sorted

        // The list is sorted with static shortcuts followed by dynamic ones. We want to keep this order, but only MAX_SHORTCUTS.
        val filtered = ArrayList<T>(MAX_SHORTCUTS)
        var numDynamic = 0
        for (shortcut in sorted) {
            val filteredSize = filtered.size
            if (filteredSize < MAX_SHORTCUTS) {
                // Always add the first MAX_SHORTCUTS to the filtered list.
                filtered.add(shortcut)
                if (dynamic(shortcut)) numDynamic++
                continue
            }
            // We have MAX_SHORTCUTS already, but they may all be static: swap static ones for dynamic ones.
            if (dynamic(shortcut) && numDynamic < NUM_DYNAMIC) {
                numDynamic++
                val lastStaticIndex = filteredSize - numDynamic
                filtered.removeAt(lastStaticIndex)
                filtered.add(shortcut)
            }
        }
        return filtered
    }

    // ActivityContext.startActivitySafely, the shortcut branch: the options of the touched view (getActivityLaunchOptions) and
    // "intent.setSourceBounds(Utilities.getViewBounds(v))" (the cell [source] on the screen), then
    // "startShortcut(packageName, id, intent.getSourceBounds(), optsBundle, user)"; "catch (NullPointerException | ActivityNotFoundException | SecurityException e)"
    // shows "Toast.makeText(context, R.string.activity_not_found, Toast.LENGTH_SHORT)". Answers whether Android accepted; false too when this app is
    // not the shortcut host (hasHostPermission). Differences: [source] is the app's cell, not the menu row (the menu is a dialog window, the launch
    // grows out of the cell in the screen's window); IllegalStateException (a locked profile) is caught as this app always did; the safe mode check,
    // WIDGETS_ENABLED, the intent branches (startActivity, startMainActivity: AppActions.startActivitySafely has them), the logging and the end
    // callback are not ported.
    // (https://github.com/LineageOS/android_packages_apps_Trebuchet/blob/lineage-22.2/src/com/android/launcher3/views/ActivityContext.java#startActivitySafely)
    fun start(context: Context, shortcut: ShortcutInfo, source: LaunchSource?): Boolean {
        if (!hasHostPermission(context)) return false
        val options = source?.let { AppActions.getActivityLaunchOptions(it.view, it) }
        val optsBundle: Bundle? = options?.toBundle()
        val sourceBounds = source?.cell
        val user = shortcut.userHandle
        return try {
            val id = shortcut.id
            val packageName = shortcut.`package`
            context.getSystemService(LauncherApps::class.java).startShortcut(packageName, id, sourceBounds, optsBundle, user)
            true
        } catch (_: NullPointerException) {
            Toast.makeText(context, R.string.activity_not_found, Toast.LENGTH_SHORT).show()
            false
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(context, R.string.activity_not_found, Toast.LENGTH_SHORT).show()
            false
        } catch (_: SecurityException) {
            Toast.makeText(context, R.string.activity_not_found, Toast.LENGTH_SHORT).show()
            false
        } catch (_: IllegalStateException) {
            Toast.makeText(context, R.string.activity_not_found, Toast.LENGTH_SHORT).show()
            false
        }
    }
}
