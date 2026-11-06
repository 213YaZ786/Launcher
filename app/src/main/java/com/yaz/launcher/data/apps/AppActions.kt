package com.yaz.launcher.data.apps

import android.app.ActivityOptions
import android.app.WallpaperManager
import android.app.role.RoleManager
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.content.pm.ApplicationInfo
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.graphics.Rect
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.os.UserHandle
import android.os.UserManager
import android.util.ArrayMap
import android.view.Display
import android.view.View
import android.widget.Toast
import com.yaz.launcher.R

/**
 * The tapped cell of a launch: the screen's host [view], the cell's [cell] bounds on the screen and its [icon]'s bounds in the window's pixels. They are
 * the `v` and the icon's `bounds` that ActivityContext.getActivityLaunchOptions reads (a BubbleTextView and its icon drawable)
 * (https://github.com/LineageOS/android_packages_apps_Trebuchet/blob/lineage-22.2/src/com/android/launcher3/views/ActivityContext.java#L486-L507).
 */
class LaunchSource(val view: View, val cell: Rect, val icon: Rect)

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
     * ActivityContext.getActivityLaunchOptions
     * (https://github.com/LineageOS/android_packages_apps_Trebuchet/blob/lineage-22.2/src/com/android/launcher3/views/ActivityContext.java#L486-L507):
     * the launch grows out of the icon, "Launch from center of icon, not entire view": ActivityOptions.makeClipRevealAnimation with the icon's
     * left, top, width and height in [v], and the launch display. Here [v] is the screen's host view and the icon's bounds come from [source],
     * in the window's pixels, so the view's own place in the window is taken off. Trebuchet's allowBGLaunch is for pending intents: left out.
     */
    fun getActivityLaunchOptions(v: View, source: LaunchSource): ActivityOptions {
        val inWindow = IntArray(2).also { v.getLocationInWindow(it) }
        val bounds = source.icon
        val left = bounds.left - inWindow[0]
        val top = bounds.top - inWindow[1]
        val width = bounds.width()
        val height = bounds.height()
        val options = ActivityOptions.makeClipRevealAnimation(v, left, top, width, height)
        options.launchDisplayId = if (v.display != null) v.display.displayId else Display.DEFAULT_DISPLAY
        return options
    }

    /**
     * ActivityContext.startActivitySafely
     * (https://github.com/LineageOS/android_packages_apps_Trebuchet/blob/lineage-22.2/src/com/android/launcher3/views/ActivityContext.java#L416-L466):
     * the options of the tapped cell, "intent.setSourceBounds(Utilities.getViewBounds(v))" (the cell on the screen), and the start in the app's
     * own profile (LauncherApps.startMainActivity); on a refusal "Toast.makeText(context, R.string.activity_not_found, Toast.LENGTH_SHORT)".
     * Answers whether Android accepted; without a [source] (no cell) the app starts without animation.
     */
    fun startActivitySafely(context: Context, component: ComponentName, user: UserHandle, source: LaunchSource?): Boolean {
        val options = source?.let { getActivityLaunchOptions(it.view, it) }
        // intent.setSourceBounds(Utilities.getViewBounds(v)): the cell's bounds on the screen.
        val started = launch(context, component, user, source?.cell, options?.toBundle())
        if (!started) {
            Toast.makeText(context, R.string.activity_not_found, Toast.LENGTH_SHORT).show()
        }
        return started
    }

    /**
     * ItemClickHandler.handleDisabledItemClicked
     * (https://github.com/LineageOS/android_packages_apps_Trebuchet/blob/lineage-22.2/src/com/android/launcher3/touch/ItemClickHandler.java#L278-L310):
     * true when the tap on a disabled app is handled here and nothing is started. An app disabled only because it is suspended or its profile
     * is paused is started anyway: "Framework will tell the user why the app is suspended." Any other disabled app gets the generic message
     * (Trebuchet's shortcut-specific messages are for shortcuts, left out).
     */
    fun handleDisabledItemClicked(entry: AppEntry, context: Context): Boolean {
        val disabledFlags = entry.runtimeStatusFlags and AppEntry.FLAG_DISABLED_MASK
        if ((disabledFlags and AppEntry.FLAG_DISABLED_SUSPENDED.inv() and AppEntry.FLAG_DISABLED_QUIET_USER.inv()) == 0) {
            // If the app is only disabled because of the above flags, launch activity anyway.
            return false
        } else {
            // Otherwise just use a generic error message.
            Toast.makeText(context, R.string.activity_not_available, Toast.LENGTH_SHORT).show()
            return true
        }
    }

    // SystemShortcut.AppInfo.onClick, named appInfo here: "Rect sourceBounds = Utilities.getViewBounds(view)" (the cell [source] on the screen) and the
    // activity options of that view ([getActivityLaunchOptions]), then PackageManagerHelper.startDetailsActivityForInfo. Differences: the view is the
    // app's cell, not the menu row (the menu is a dialog window, the launch grows out of the cell in the screen's window); options.onEndCallback and
    // its dismissTaskMenuView (the menu closes at once here) and the stats log are left out.
    // (https://github.com/LineageOS/android_packages_apps_Trebuchet/blob/lineage-22.2/src/com/android/launcher3/popup/SystemShortcut.java#AppInfo)
    // not ported: createAccessibilityAction and SplitAccessibilityInfo (overview's split-screen a11y; the menu row has its own label)
    // not ported: onClick (it is appInfo, below)
    fun appInfo(context: Context, component: ComponentName, user: UserHandle, source: LaunchSource?): Boolean {
        val sourceBounds = source?.cell
        val options = source?.let { getActivityLaunchOptions(it.view, it) }
        return startDetailsActivityForInfo(context, component, user, sourceBounds, options?.toBundle())
    }

    // PackageManagerHelper.startDetailsActivityForInfo, for an app: Android's App info of the component (where Force stop is),
    // LauncherApps.startAppDetailsActivity; a SecurityException or ActivityNotFoundException shows "Toast.makeText(context, R.string.activity_not_found,
    // Toast.LENGTH_SHORT)". Answers whether Android accepted. The install session branch (FLAG_INSTALL_SESSION_ACTIVE) is left out: no install sessions here.
    // (https://github.com/LineageOS/android_packages_apps_Trebuchet/blob/lineage-22.2/src/com/android/launcher3/util/PackageManagerHelper.java#startDetailsActivityForInfo)
    // not ported: getAppInstallerPackage, getAppLaunchIntent, getAppLaunchInfo (nothing here asks them)
    // not ported: isLauncherAppTarget, supportsMultiInstance, isSameAppForMultiInstance (multi-instance split screen is the system's)
    // not ported: hasShortcutsPermission (AppShortcuts.hasHostPermission has it), getLoadingProgress (no downloading icons)
    private fun startDetailsActivityForInfo(context: Context, componentName: ComponentName, user: UserHandle, sourceBounds: Rect?, opts: Bundle?): Boolean = try {
        context.getSystemService(LauncherApps::class.java).startAppDetailsActivity(componentName, user, sourceBounds, opts)
        true
    } catch (_: SecurityException) {
        Toast.makeText(context, R.string.activity_not_found, Toast.LENGTH_SHORT).show()
        false
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, R.string.activity_not_found, Toast.LENGTH_SHORT).show()
        false
    } catch (_: IllegalStateException) {
        // Not in the original: the exception this app's guarded() has always caught (a profile that is not running), shown the same way.
        Toast.makeText(context, R.string.activity_not_found, Toast.LENGTH_SHORT).show()
        false
    }

    // SecondaryDropTarget's CACHE_EXPIRE_TIMEOUT and mUninstallDisabledCache: the restrictions of a user are asked once, then kept for 5 seconds
    // (https://github.com/LineageOS/android_packages_apps_Trebuchet/blob/lineage-22.2/src/com/android/launcher3/SecondaryDropTarget.java#getButtonType).
    // not ported: onAlarm (the cache holds the time it ends and is emptied by the next call after it: an Alarm on a handler would be a timer left running)
    // not ported: onAttachedToWindow and onDetachedFromWindow (they only move the alarm)
    // not ported: onFinishInflate and setupUi (the drop target's drawable and text; the menu entry has its own)
    // not ported: setupItemInfo, supportsDrop and supportsAccessibilityDrop (drag and drop; the menu asks getButtonType itself)
    // not ported: getAccessibilityAction (a drop target's action id)
    private const val CACHE_EXPIRE_TIMEOUT = 5000L
    private val mUninstallDisabledCache = ArrayMap<UserHandle, Boolean>(1)
    private var mCacheExpiresAt = 0L

    // SecondaryDropTarget's button types: nothing to offer, or Uninstall (RECONFIGURE and DISMISS_PREDICTION are for widgets and predicted items).
    const val INVALID = -1
    const val UNINSTALL = 1

    /**
     * SecondaryDropTarget.getButtonType, the branch of an app (an AppEntry is no widget and no predicted item): INVALID when the user's restrictions
     * (UserManager.getUserRestrictions) have DISALLOW_APPS_CONTROL or DISALLOW_UNINSTALL_APPS, when [info] is in the private space, when it is a
     * system app, or when it has no uninstall target ([getUninstallTarget]); else UNINSTALL. Binder calls: off the main thread.
     * (https://github.com/LineageOS/android_packages_apps_Trebuchet/blob/lineage-22.2/src/com/android/launcher3/SecondaryDropTarget.java#getButtonType)
     */
    fun getButtonType(context: Context, info: AppEntry): Int {
        var uninstallDisabled: Boolean?
        synchronized(mUninstallDisabledCache) {
            // Cancel any pending alarm and set cache expiry after some time
            val now = SystemClock.uptimeMillis()
            if (now >= mCacheExpiresAt) mUninstallDisabledCache.clear()
            mCacheExpiresAt = now + CACHE_EXPIRE_TIMEOUT
            uninstallDisabled = mUninstallDisabledCache[info.user]
            if (uninstallDisabled == null) {
                val userManager = context.getSystemService(Context.USER_SERVICE) as UserManager
                val restrictions = userManager.getUserRestrictions(info.user)
                uninstallDisabled = restrictions.getBoolean(UserManager.DISALLOW_APPS_CONTROL, false) ||
                    restrictions.getBoolean(UserManager.DISALLOW_UNINSTALL_APPS, false)
                mUninstallDisabledCache[info.user] = uninstallDisabled
            }
        }
        return getButtonType(info, uninstallDisabled == true) { getUninstallTarget(context, info) }
    }

    /** The rest of [getButtonType] once the user's restrictions are known; [uninstallTarget] is asked last, as the original does. */
    internal fun getButtonType(info: AppEntry, uninstallDisabled: Boolean, uninstallTarget: () -> ComponentName?): Int {
        if (uninstallDisabled) {
            return INVALID
        }
        if (info.profile == ProfileKind.PRIVATE) {
            return INVALID
        }
        if ((info.runtimeStatusFlags and AppEntry.FLAG_SYSTEM_MASK) != 0 &&
            (info.runtimeStatusFlags and AppEntry.FLAG_SYSTEM_NO) == 0
        ) {
            return INVALID
        }
        if (uninstallTarget() == null) {
            return INVALID
        }
        return UNINSTALL
    }

    /**
     * SecondaryDropTarget.getUninstallTarget: the component that should be uninstalled, or null: the app's launch intent resolved in its user
     * (LauncherApps.resolveActivity), kept only if its application is not a system one. Every entry here is an ITEM_TYPE_APPLICATION.
     * (https://github.com/LineageOS/android_packages_apps_Trebuchet/blob/lineage-22.2/src/com/android/launcher3/SecondaryDropTarget.java#getUninstallTarget)
     */
    fun getUninstallTarget(context: Context, item: AppEntry): ComponentName? {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setComponent(ComponentName(item.packageName, item.className))
        val user = item.user
        val info = context.getSystemService(LauncherApps::class.java).resolveActivity(intent, user)
        if (info != null && (info.applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM) == 0) {
            return info.componentName
        }
        return null
    }

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
     * default home app may call. UserProfileManager.setQuietModeSafely: on a SecurityException (this app is not the home app) it asks for the
     * home role ([assignDefaultHomeRole]). The answer tells the cases apart (Trebuchet's method returns nothing): true when Android took the
     * request, false when it refused (requestQuietModeEnabled answers false when a credential is needed first), null on the SecurityException
     * (not the home app).
     * (https://github.com/LineageOS/android_packages_apps_Trebuchet/blob/lineage-22.2/src/com/android/launcher3/allapps/UserProfileManager.java#setQuietModeSafely).
     * Blocking: the caller runs it off the main thread, where Trebuchet posts it to UI_HELPER_EXECUTOR.
     */
    fun setQuietModeSafely(context: Context, userHandle: UserHandle, enable: Boolean): Boolean? = try {
        context.getSystemService(UserManager::class.java).requestQuietModeEnabled(enable, userHandle)
    } catch (_: SecurityException) {
        assignDefaultHomeRole(context)
        null
    } catch (_: IllegalStateException) {
        false
    } catch (_: NullPointerException) {
        false
    }

    /**
     * Starts the consent screen that makes this launcher the home app: when the role is available and not held, RoleManager.createRequestRoleIntent
     * (https://github.com/LineageOS/android_packages_apps_Trebuchet/blob/lineage-22.2/quickstep/src/com/android/launcher3/uioverrides/SystemApiWrapper.kt#assignDefaultHomeRole).
     * Trebuchet starts it through its ProxyActivityStarter and shows the toast set_default_home_app when the user refuses: both left out here,
     * [context] is the activity and starts it itself.
     */
    fun assignDefaultHomeRole(context: Context) {
        val roleManager = context.getSystemService(RoleManager::class.java)
        if (roleManager != null && roleManager.isRoleAvailable(RoleManager.ROLE_HOME) && !roleManager.isRoleHeld(RoleManager.ROLE_HOME)) {
            val roleRequestIntent = roleManager.createRequestRoleIntent(RoleManager.ROLE_HOME)
            runCatching { context.startActivity(roleRequestIntent) }
        }
    }

    /**
     * The intent sender that opens the private space's settings: LauncherApps.getPrivateSpaceSettingsIntent, null when there is none
     * (Android before 15, or no private space)
     * (https://github.com/LineageOS/android_packages_apps_Trebuchet/blob/lineage-22.2/quickstep/src/com/android/launcher3/uioverrides/SystemApiWrapper.kt#getPrivateSpaceSettingsIntent).
     * Trebuchet wraps it in a ProxyActivityStarter intent and tests the allowPrivateProfile and enablePrivateSpace flags: the call itself
     * answers null (or refuses) without them.
     */
    fun getPrivateSpaceSettingsIntent(context: Context): IntentSender? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) return null
        return runCatching { context.getSystemService(LauncherApps::class.java)?.privateSpaceSettingsIntent }.getOrNull()
    }

    /**
     * The intent sender that opens the app market in the private space [user]: LauncherApps.getAppMarketActivityIntent, null when there is none
     * (https://github.com/LineageOS/android_packages_apps_Trebuchet/blob/lineage-22.2/quickstep/src/com/android/launcher3/uioverrides/SystemApiWrapper.kt#getAppMarketActivityIntent).
     * PrivateProfileManager.initializeInBackgroundThread asks it with BuildConfig.APPLICATION_ID, the launcher's own package.
     */
    fun getAppMarketActivityIntent(context: Context, packageName: String, user: UserHandle): IntentSender? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) return null
        return runCatching { context.getSystemService(LauncherApps::class.java)?.getAppMarketActivityIntent(packageName, user) }.getOrNull()
    }

    /**
     * What a tap on the private space's settings gear does: PrivateSpaceSettingsButton.onClick starts the settings intent through
     * startActivitySafely (the ProxyActivityStarter); here the intent sender is started with the options Trebuchet puts in its StartActivityParams,
     * ActivityOptions.makeBasic().setPendingIntentBackgroundActivityStartMode(MODE_BACKGROUND_ACTIVITY_START_ALLOWED), from the activity [context]
     * (https://github.com/LineageOS/android_packages_apps_Trebuchet/blob/lineage-22.2/src/com/android/launcher3/allapps/PrivateSpaceSettingsButton.java#onClick).
     * Answers whether Android accepted; false too when there is no settings intent.
     */
    fun onClickPrivateSpaceSettings(context: Context): Boolean {
        val sender = getPrivateSpaceSettingsIntent(context) ?: return false
        return startIntentSender(context, sender)
    }

    /**
     * The Install apps cell: the app market's intent sender of the private space [user], started as [onClickPrivateSpaceSettings] starts the gear's
     * (PrivateProfileManager.addPrivateSpaceInstallAppButton gives the button the intent of getAppMarketActivityIntent,
     * https://github.com/LineageOS/android_packages_apps_Trebuchet/blob/lineage-22.2/src/com/android/launcher3/allapps/PrivateProfileManager.java#addPrivateSpaceInstallAppButton).
     */
    fun onClickPrivateSpaceInstall(context: Context, user: UserHandle): Boolean {
        val sender = getAppMarketActivityIntent(context, context.packageName, user) ?: return false
        return startIntentSender(context, sender)
    }

    // SystemApiWrapper's StartActivityParams: no result wanted (requireActivityResult = false), the options that let the sender start in the background.
    private fun startIntentSender(context: Context, sender: IntentSender): Boolean = try {
        val options = ActivityOptions.makeBasic()
            .setPendingIntentBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED)
        context.startIntentSender(sender, null, 0, 0, 0, options.toBundle())
        true
    } catch (_: IntentSender.SendIntentException) {
        false
    } catch (_: SecurityException) {
        false
    } catch (_: IllegalStateException) {
        false
    } catch (_: ActivityNotFoundException) {
        false
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
