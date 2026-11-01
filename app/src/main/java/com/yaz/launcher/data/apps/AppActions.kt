package com.yaz.launcher.data.apps

import android.app.WallpaperManager
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.graphics.Rect
import android.net.Uri
import android.os.Bundle
import android.os.UserHandle
import android.os.UserManager

/**
 * What a tap or a long press on an app does: start it, open its App info, uninstall it. Each answers whether Android
 * accepted, and none throws: a refusal is the caller's to show.
 */
object AppActions {

    /**
     * Starts the launcher activity of an app, in its own profile for a work or private app. LauncherApps.startMainActivity,
     * as Trebuchet's ActivityContext.startActivitySafely calls it for another user
     * (https://github.com/LineageOS/android_packages_apps_Trebuchet/blob/lineage-22.2/src/com/android/launcher3/views/ActivityContext.java),
     * with the exceptions Taskbar catches around it
     * (https://github.com/farmerbb/Taskbar/blob/master/app/src/main/java/com/farmerbb/taskbar/util/U.java, l.581).
     */
    fun launch(context: Context, component: ComponentName, user: UserHandle, sourceBounds: Rect? = null, options: Bundle? = null): Boolean =
        guarded { context.getSystemService(LauncherApps::class.java).startMainActivity(component, user, sourceBounds, options) }

    /**
     * Opens Android's App info of the app (where Force stop is). LauncherApps.startAppDetailsActivity, with the
     * exceptions PackageManagerHelper.startDetailsActivityForInfo catches (SecurityException, ActivityNotFoundException)
     * (https://github.com/LineageOS/android_packages_apps_Trebuchet/blob/lineage-22.2/src/com/android/launcher3/util/PackageManagerHelper.java).
     */
    fun appInfo(context: Context, component: ComponentName, user: UserHandle, sourceBounds: Rect? = null, options: Bundle? = null): Boolean =
        guarded { context.getSystemService(LauncherApps::class.java).startAppDetailsActivity(component, user, sourceBounds, options) }

    /**
     * Asks Android to uninstall the app, which shows its own confirmation. ACTION_DELETE on the package, the user as
     * an extra and the flags 0x10800000 (new task, not in recents), as SecondaryDropTarget.performUninstall builds it
     * from the string delete_package_intent
     * (https://github.com/LineageOS/android_packages_apps_Trebuchet/blob/lineage-22.2/src/com/android/launcher3/SecondaryDropTarget.java
     * and res/values/config.xml). Needs REQUEST_DELETE_PACKAGES, which the manifest holds.
     */
    fun uninstall(context: Context, component: ComponentName, user: UserHandle): Boolean = guarded {
        val intent = Intent(Intent.ACTION_DELETE)
            .setData(Uri.fromParts("package", component.packageName, component.className))
            .putExtra(Intent.EXTRA_USER, user)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
        context.startActivity(intent)
    }

    /**
     * Pauses (work profile) or locks (private space) a profile, or brings it back: UserManager.requestQuietModeEnabled, which only the
     * default home app may call, as Trebuchet's UserProfileManager.setQuietModeSafely calls it (a SecurityException means this app is not
     * the home app)
     * (https://github.com/LineageOS/android_packages_apps_Trebuchet/blob/lineage-22.2/src/com/android/launcher3/allapps/UserProfileManager.java;
     * WorkProfileManager.setWorkProfileEnabled and PrivateProfileManager.setQuietMode call it). Answers whether Android accepted.
     */
    fun setQuietMode(context: Context, user: UserHandle, quiet: Boolean): Boolean = guarded {
        context.getSystemService(UserManager::class.java).requestQuietModeEnabled(quiet, user)
    }

    /**
     * Opens the wallpaper picker (the menu of a long press on the wallpaper): ACTION_SET_WALLPAPER with the launch source and the flavour
     * Trebuchet gives the picker, started safely, and not started at all when the user may not set a wallpaper
     * (WallpaperManager.isSetWallpaperAllowed). OptionsPopupView.startWallpaperPicker and its getOptions check
     * (https://github.com/LineageOS/android_packages_apps_Trebuchet/blob/lineage-22.2/src/com/android/launcher3/views/OptionsPopupView.java).
     * Answers whether Android accepted.
     */
    fun openWallpaper(context: Context): Boolean {
        if (!context.getSystemService(WallpaperManager::class.java).isSetWallpaperAllowed) return false
        return guarded {
            val intent = Intent(Intent.ACTION_SET_WALLPAPER)
                .putExtra("com.android.wallpaper.LAUNCH_SOURCE", "app_launched_launcher")
                .putExtra("com.android.launcher3.WALLPAPER_FLAVOR", "focus_wallpaper")
            // Trebuchet sets the picker's package (R.string.wallpaper_picker_package) so Android shows no chooser. Without such a config,
            // the system app which handles ACTION_SET_WALLPAPER is the picker: the first of the system activities answering the intent
            // (PackageManager.queryIntentActivities with MATCH_SYSTEM_ONLY | MATCH_DEFAULT_ONLY,
            // https://developer.android.com/reference/android/content/pm/PackageManager#queryIntentActivities(android.content.Intent,%20android.content.pm.PackageManager.ResolveInfoFlags)).
            // None found: the intent starts as it is.
            val picker = context.packageManager.queryIntentActivities(
                intent,
                PackageManager.ResolveInfoFlags.of((PackageManager.MATCH_SYSTEM_ONLY or PackageManager.MATCH_DEFAULT_ONLY).toLong())
            ).firstOrNull()?.activityInfo?.packageName
            if (picker != null) intent.setPackage(picker)
            context.startActivity(intent)
        }
    }

    private inline fun guarded(action: () -> Unit): Boolean = try {
        action()
        true
    } catch (_: ActivityNotFoundException) {
        false
    } catch (_: SecurityException) {
        false
    } catch (_: IllegalStateException) {
        false
    } catch (_: NullPointerException) {
        false
    }
}
