package com.yaz.launcher.data.apps

import android.content.pm.LauncherApps
import android.os.UserHandle

/**
 * What happened to some packages of a profile. SUSPEND and UNSUSPEND are PackageUpdatedTask's OP_SUSPEND and OP_UNSUSPEND, which only set or
 * clear FLAG_DISABLED_SUSPENDED on the packages' items
 * (https://github.com/LineageOS/android_packages_apps_Trebuchet/blob/lineage-22.2/src/com/android/launcher3/model/PackageUpdatedTask.java#L174-L179).
 */
enum class PackageOp { ADD, UPDATE, REMOVE, SUSPEND, UNSUSPEND }

/**
 * Android's callbacks for the packages of every profile, turned into [PackageOp]s for the list of apps.
 *
 * Ported from Trebuchet's ModelLauncherCallbacks, which does the same with its PackageUpdatedTask (OP_ADD, OP_UPDATE,
 * OP_REMOVE, OP_SUSPEND, OP_UNAVAILABLE, OP_UNSUSPEND)
 * (https://github.com/LineageOS/android_packages_apps_Trebuchet/blob/lineage-22.2/src/com/android/launcher3/model/ModelLauncherCallbacks.kt).
 * Left out: install sessions and incremental download progress (promise icons), shortcut changes (they are read when
 * the menu opens). Availability and a package coming back are an UPDATE: the package's activities are
 * asked again, and an unavailable package, which Android lists no more, leaves with that. Suspension only sets the disabled flag.
 */
class ModelLauncherCallbacks(private val onTask: (PackageOp, UserHandle, List<String>) -> Unit) : LauncherApps.Callback() {

    override fun onPackageAdded(packageName: String, user: UserHandle) {
        onTask(PackageOp.ADD, user, listOf(packageName))
    }

    override fun onPackageChanged(packageName: String, user: UserHandle) {
        onTask(PackageOp.UPDATE, user, listOf(packageName))
    }

    override fun onPackageRemoved(packageName: String, user: UserHandle) {
        onTask(PackageOp.REMOVE, user, listOf(packageName))
    }

    override fun onPackagesAvailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) {
        onTask(PackageOp.UPDATE, user, packageNames.toList())
    }

    override fun onPackagesUnavailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) {
        // A package being replaced comes back at once: only a real loss counts.
        if (!replacing) onTask(PackageOp.UPDATE, user, packageNames.toList())
    }

    override fun onPackagesSuspended(packageNames: Array<out String>, user: UserHandle) {
        onTask(PackageOp.SUSPEND, user, packageNames.toList())
    }

    override fun onPackagesUnsuspended(packageNames: Array<out String>, user: UserHandle) {
        onTask(PackageOp.UNSUSPEND, user, packageNames.toList())
    }
}
