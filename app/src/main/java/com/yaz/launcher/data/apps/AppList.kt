package com.yaz.launcher.data.apps

/**
 * The list of every app, changed the way Trebuchet's AllAppsList changes its own, here as functions on an
 * immutable list so they run on the JVM
 * (https://github.com/LineageOS/android_packages_apps_Trebuchet/blob/lineage-22.2/src/com/android/launcher3/model/AllAppsList.java).
 */
object AppList {

    /** AllAppsList.add: "If the app is already in the list, doesn't add it." */
    fun <T : Listed> add(list: List<T>, item: T): List<T> =
        if (list.any { it.key == item.key }) list else list + item

    /** AllAppsList.removePackage: every activity of [packageName] in the profile [userSerial] leaves. */
    fun <T : Listed> removePackage(list: List<T>, packageName: String, userSerial: Long): List<T> =
        list.filterNot { it.packageName == packageName && it.userSerial == userSerial }

    /**
     * AllAppsList.updatePackage: [fresh] is what LauncherApps.getActivityList(packageName, user) answers now. The
     * activities that disappeared leave, the known ones take their new label and icon, the new ones come in; with
     * nothing fresh, "Remove all data for this package". Activities of another package or profile are left alone.
     */
    fun <T : Listed> updatePackage(list: List<T>, packageName: String, userSerial: Long, fresh: List<T>): List<T> =
        removePackage(list, packageName, userSerial) + fresh.filter { it.packageName == packageName && it.userSerial == userSerial }.distinctBy { it.key }

    /**
     * AllAppsList.updateDisabledFlags (https://github.com/LineageOS/android_packages_apps_Trebuchet/blob/lineage-22.2/src/com/android/launcher3/model/AllAppsList.java#L283-L292):
     * "Updates the disabled flags of apps matching {@param matcher} based on {@param op}." The entries are immutable, so a matching one is
     * replaced by a copy with its new runtimeStatusFlags (same icon layers); [op] is a FlagOp, here a function of the old flags.
     */
    fun updateDisabledFlags(data: List<AppEntry>, matcher: (AppEntry) -> Boolean, op: (Int) -> Int): List<AppEntry> =
        data.map { info ->
            if (matcher(info)) {
                val flags = op(info.runtimeStatusFlags)
                if (flags == info.runtimeStatusFlags) info else info.copy(runtimeStatusFlags = flags)
            } else {
                info
            }
        }

    /** A profile that went: all its apps leave (UserCache's removal event, then the loader reloads without it). */
    fun <T : Listed> removeProfile(list: List<T>, userSerial: Long): List<T> = list.filterNot { it.userSerial == userSerial }

    /**
     * Whether [a] and [b] hold the same entries, whatever their order. AllAppsList keeps a change flag (`mDataChanged`, read and reset by
     * getAndResetChangeFlag) and PackageUpdatedTask binds the apps again only if it is set (bindApplicationsIfNeeded), so a callback that
     * changed nothing makes no new state
     * (https://github.com/LineageOS/android_packages_apps_Trebuchet/blob/lineage-22.2/src/com/android/launcher3/model/AllAppsList.java).
     */
    fun <T : Listed> sameEntries(a: List<T>, b: List<T>): Boolean = a.size == b.size && a.toSet() == b.toSet()
}
