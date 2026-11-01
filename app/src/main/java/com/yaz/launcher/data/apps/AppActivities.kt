package com.yaz.launcher.data.apps

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import java.io.File

/**
 * One activity of an app. sdex/ActivityManager's ActivityModel (Apache-2.0,
 * https://github.com/sdex/ActivityManager/blob/main/app/src/main/java/com/sdex/activityrunner/app/ActivityModel.kt): name, package, class,
 * label, exported, enabled, permission; `launchRequiresRoot` (not exported, or guarded by a permission) is [launchable] turned round,
 * since this app has no root: such an activity is listed and cannot be started.
 */
data class ActivityModel(
    /** The class name without its package path, as ActivityManager shows it. */
    val name: String,
    val packageName: String,
    val className: String,
    val label: String?,
    val exported: Boolean,
    val enabled: Boolean,
    val permission: String?
) {
    val componentName: ComponentName get() = ComponentName(packageName, className)

    /** ActivityManager's `!launchRequiresRoot`: exported, enabled and open to every app (no permission). */
    val launchable: Boolean get() = exported && enabled && permission == null
}

/**
 * The activities of an app and their start, from sdex/ActivityManager (Apache-2.0,
 * https://github.com/sdex/ActivityManager/tree/main/app/src/main/java/com/sdex/activityrunner). Main profile only: a package of the work
 * profile or private space is not visible to this app's PackageManager (that would need INTERACT_ACROSS_USERS), and an activity could not be
 * started in another profile by an explicit intent.
 */
object AppActivities {

    /**
     * PackageInfoProviderImpl.getActivities and getPackageInfo (util/PackageInfoProviderImpl.kt): the package asked with GET_ACTIVITIES
     * (the Tiramisu flags class from API 33), each ActivityInfo made an [ActivityModel] with its label; a disabled app falls back on
     * its archive (getPackageArchiveInfo of its APK), since Android lists no activities of a disabled package. Sorted by name, as
     * ActivitiesListViewModel.getActivitiesList sorts. Empty when the package is not visible or gone. Off the main thread.
     * Package visibility: the manifest's `<queries>` for MAIN/LAUNCHER makes every app with a launcher entry visible, which every
     * package listed here has.
     */
    fun list(context: Context, packageName: String): List<ActivityModel> = try {
        val pm = context.packageManager
        val info = packageInfo(pm, packageName)
        val infos = if (info.applicationInfo?.enabled == false) archiveInfo(pm, packageName).activities else info.activities
        infos.orEmpty().map { it.toModel(pm) }.sortedBy { it.name }
    } catch (_: Exception) {
        emptyList()
    }

    /** The app's own label (PackageInfoProviderImpl.getApplicationName), the package name when it has none. */
    fun appLabel(context: Context, packageName: String): String = try {
        val pm = context.packageManager
        val app = packageInfo(pm, packageName).applicationInfo
        if (app != null) pm.getApplicationLabel(app).toString() else packageName
    } catch (_: Exception) {
        packageName
    }

    /** The label of one activity, as the list shows it; null when it has none of its own. */
    fun labelOf(context: Context, component: ComponentName): String? = try {
        val pm = context.packageManager
        pm.getActivityInfo(component, 0).loadLabel(pm).toString().takeIf { it.isNotBlank() }
    } catch (_: Exception) {
        null
    }

    /**
     * IntentUtils.launchActivity (util/IntentUtils.kt): an explicit intent on the component, NEW_TASK and CLEAR_TOP, a refusal
     * (SecurityException, a missing activity) answered false and not thrown. Added: the activity is checked again at the moment of the
     * start (exported, enabled, no permission), so a pinned shortcut whose app changed cannot start what was never open to us, and
     * nothing but the component goes in the intent (no action, data or extras). Nothing is logged.
     */
    fun launch(context: Context, component: ComponentName): Boolean = try {
        val pm = context.packageManager
        val info = pm.getActivityInfo(component, 0)
        if (!info.exported || !info.enabled || !info.permission.isNullOrEmpty()) {
            false
        } else {
            context.startActivity(Intent().setComponent(component).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP))
            true
        }
    } catch (_: Exception) {
        false
    }

    // PackageInfoProviderImpl.getPackageInfo.
    private fun packageInfo(pm: PackageManager, packageName: String): PackageInfo =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(PackageManager.GET_ACTIVITIES.toLong()))
        } else {
            @Suppress("DEPRECATION")
            pm.getPackageInfo(packageName, PackageManager.GET_ACTIVITIES)
        }

    // PackageInfoProviderImpl.getPackageArchiveInfo and getPackageArchiveActivities.
    private fun archiveInfo(pm: PackageManager, packageName: String): PackageInfo {
        val base = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(PackageManager.GET_META_DATA.toLong()))
        } else {
            @Suppress("DEPRECATION")
            pm.getPackageInfo(packageName, PackageManager.GET_META_DATA)
        }
        val source = base.applicationInfo?.publicSourceDir ?: throw IllegalStateException("ApplicationInfo is null")
        val path = File(source).absolutePath
        val archive = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getPackageArchiveInfo(path, PackageManager.PackageInfoFlags.of(PackageManager.GET_ACTIVITIES.toLong()))
        } else {
            @Suppress("DEPRECATION")
            pm.getPackageArchiveInfo(path, PackageManager.GET_ACTIVITIES)
        }
        base.activities = archive?.activities
        return base
    }

    // PackageInfoProviderImpl.toActivityModel.
    private fun ActivityInfo.toModel(pm: PackageManager) = ActivityModel(
        name = name.split(".").last(),
        packageName = packageName,
        className = name,
        label = loadLabel(pm).toString(),
        exported = exported,
        enabled = enabled,
        permission = permission?.ifEmpty { null }
    )
}
