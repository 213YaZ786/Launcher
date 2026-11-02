package com.yaz.launcher.data.apps

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.LauncherActivityInfo
import android.content.pm.LauncherApps
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.os.UserHandle
import android.text.TextUtils
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** Every launcher activity of every profile, once [loaded]. */
data class AppsState(
    val loaded: Boolean = false,
    val profiles: List<ProfileInfo> = emptyList(),
    val apps: List<AppEntry> = emptyList()
)

/**
 * The list of apps of every profile, loaded off the main thread and kept current by Android's package callbacks while
 * the screen is on ([start] to [stop]); nothing listens otherwise, and each [start] reads everything again.
 *
 * Sources, all Trebuchet's (Apache-2.0, https://github.com/LineageOS/android_packages_apps_Trebuchet/blob/lineage-22.2/):
 * `LoaderTask.loadAllApps` (src/com/android/launcher3/model/LoaderTask.java): for each profile
 * LauncherApps.getActivityList(null, user). `AllAppsList.addPackage`, `updatePackage`, `removePackage`
 * (model/AllAppsList.java, here [AppList]): the answer of getActivityList(package, user) replaces what was listed.
 * `ModelLauncherCallbacks` (model/ModelLauncherCallbacks.kt): [ModelLauncherCallbacks]. `UserCache` (pm/UserCache.java):
 * [ProfileEvents] reload the profiles. The work is serialized on one thread, as Trebuchet's MODEL_EXECUTOR does.
 * Differences: a profile with no activity (a locked private space) is skipped, where loadAllApps gives up the whole
 * load ("TODO: Fix this. Only fail for the current user"); the icons are loaded here with the list.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AppsRepository(
    context: Context,
    private val scope: CoroutineScope,
    io: CoroutineDispatcher = Dispatchers.IO
) {
    private val app = context.applicationContext
    private val launcherApps: LauncherApps = app.getSystemService(LauncherApps::class.java)

    // BaseIconFactory.getNormalizer: one IconNormalizer for the loads, which run on one thread ([model]); the bitmap size is the largest icon
    // the panel draws, 72 dp, where Trebuchet's is the grid's icon size (DeviceProfile.iconSizePx).
    private val normalizer by lazy { IconNormalizer((MAX_ICON_DP * app.resources.displayMetrics.density).roundToInt()) }

    private val _state = MutableStateFlow(AppsState())
    val state: StateFlow<AppsState> = _state.asStateFlow()

    private val model = io.limitedParallelism(1)
    private val callbacks = ModelLauncherCallbacks(::onPackages)
    private val profileEvents = ProfileEvents(app) { scope.launch(model) { loadAll(onlyChanged = true) } }
    private var started = false

    /** The package callbacks not yet applied: one pass of [applyPackages] takes them all, so a burst makes one state. */
    private val pending = ArrayList<Triple<PackageOp, UserHandle, List<String>>>()

    /** Registers for the changes and loads. Called when the screen comes to the front. */
    fun start() {
        if (started) return
        started = true
        launcherApps.registerCallback(callbacks)
        profileEvents.start()
        scope.launch(model) { loadAll(onlyChanged = false) }
    }

    /** Stops listening. Called when the screen goes. */
    fun stop() {
        if (!started) return
        started = false
        launcherApps.unregisterCallback(callbacks)
        profileEvents.stop()
    }

    /**
     * LoaderTask.loadAllApps (https://github.com/LineageOS/android_packages_apps_Trebuchet/blob/lineage-22.2/src/com/android/launcher3/model/LoaderTask.java).
     * With [onlyChanged] (a profile event), only the profiles that were added, removed or whose state changed are read again, as
     * PackageUpdatedTask's OP_USER_AVAILABILITY_CHANGE touches only that user's items, `ItemInfoMatcher.ofUser(mUser)`
     * (https://github.com/LineageOS/android_packages_apps_Trebuchet/blob/lineage-22.2/src/com/android/launcher3/model/PackageUpdatedTask.java,
     * [Profiles.changed]); the entries of the others stay the same objects, so their icons stay in the screen's caches. In a profile that is
     * read, an entry whose package was not updated is kept too ([entriesOf]). StateFlow publishes only a value that differs: an event that
     * changed nothing makes no new state.
     */
    private fun loadAll(onlyChanged: Boolean) {
        // BaseIconCache.updateSystemState: "mUserFormatString.clear()", the locale may have changed since the last load.
        if (!onlyChanged) mUserFormatString.clear()
        val before = _state.value
        val profiles = Profiles.queryAll(app)
        val reload = if (onlyChanged && before.loaded) Profiles.changed(before.profiles, profiles) else null
        val previous = before.apps.associateBy { it.key }
        val apps = profiles.flatMap { profile ->
            if (reload != null && profile.serial !in reload) before.apps.filter { it.userSerial == profile.serial } else entriesOf(profile, null, previous)
        }
        _state.value = AppsState(loaded = true, profiles = profiles, apps = apps)
    }

    /** The package callbacks: AllAppsList's addPackage, updatePackage and removePackage on the list, in one pass for the burst ([applyPackages]). */
    private fun onPackages(op: PackageOp, user: UserHandle, packages: List<String>) {
        synchronized(pending) { pending.add(Triple(op, user, packages)) }
        scope.launch(model) { applyPackages() }
    }

    /**
     * Applies every callback waiting, then publishes once, and only if the list changed: PackageUpdatedTask runs its operation and ends with
     * bindApplicationsIfNeeded (https://github.com/LineageOS/android_packages_apps_Trebuchet/blob/lineage-22.2/src/com/android/launcher3/model/PackageUpdatedTask.java),
     * which binds only if AllAppsList.getAndResetChangeFlag says something changed (see [AppList.sameEntries]). Locking or unlocking the
     * private space makes Android call onPackagesAvailable or onPackagesUnavailable for each of its packages in a row: they are taken
     * together here, and the entries that did not change are kept ([entriesOf]), so they make no new state.
     */
    private fun applyPackages() {
        val tasks = synchronized(pending) { pending.toList().also { pending.clear() } }
        if (tasks.isEmpty()) return
        val before = _state.value
        val previous = before.apps.associateBy { it.key }
        var profiles = before.profiles
        var apps = before.apps
        for ((op, user, packages) in tasks) {
            var profile = profiles.firstOrNull { it.user == user }
            if (profile == null) {
                // A profile that came after the last load.
                profiles = Profiles.queryAll(app)
                profile = profiles.firstOrNull { it.user == user } ?: continue
            }
            for (pkg in packages) {
                apps = when (op) {
                    PackageOp.REMOVE -> AppList.removePackage(apps, pkg, profile.serial)
                    // PackageUpdatedTask OP_SUSPEND and OP_UNSUSPEND: FlagOp.NO_OP.setFlag(FLAG_DISABLED_SUSPENDED, mOp == OP_SUSPEND) on the items of the
                    // packages (ItemInfoMatcher.ofPackages(packageSet, mUser)), then appsList.updateDisabledFlags(matcher, flagOp)
                    // (https://github.com/LineageOS/android_packages_apps_Trebuchet/blob/lineage-22.2/src/com/android/launcher3/model/PackageUpdatedTask.java#L174-L179).
                    PackageOp.SUSPEND, PackageOp.UNSUSPEND -> AppList.updateDisabledFlags(
                        apps,
                        { info -> info.packageName == pkg && info.userSerial == profile.serial },
                        { flags -> if (op == PackageOp.SUSPEND) flags or AppEntry.FLAG_DISABLED_SUSPENDED else flags and AppEntry.FLAG_DISABLED_SUSPENDED.inv() }
                    )
                    else -> AppList.updatePackage(apps, pkg, profile.serial, entriesOf(profile, pkg, previous))
                }
            }
        }
        val same = AppList.sameEntries(before.apps, apps)
        if (same && profiles == before.profiles) return
        _state.value = before.copy(profiles = profiles, apps = if (same) before.apps else apps)
    }

    /**
     * The launcher activities of [profile], of one package or of all. The icon is the activity's own, LauncherActivityInfo.getIcon(0),
     * in layers ([IconLayers.of]); the profile's badge comes apart ([badgeOf]), drawn by the screen over the shape. The label is
     * LauncherActivityInfo.getLabel.
     *
     * An activity that is in [previous] (the same component in the same profile) with the same label and the same [stampOf] is not built
     * again: the old entry, with its IconLayers, is returned, as Trebuchet's AllAppsList.updatePackage / IconCache.updateTitleAndIcon only
     * reload the icon of a package that was replaced (PackageUpdatedTask OP_UPDATE), where here a new object would miss the icon caches.
     * (https://github.com/LineageOS/android_packages_apps_Trebuchet/blob/lineage-22.2/src/com/android/launcher3/model/PackageUpdatedTask.java)
     */
    private fun entriesOf(profile: ProfileInfo, packageName: String?, previous: Map<AppKey, AppEntry>): List<AppEntry> {
        val activities = runCatching { launcherApps.getActivityList(packageName, profile.user) }.getOrNull().orEmpty()
        val badge by lazy { if (profile.kind == ProfileKind.MAIN) null else badgeOf(profile.user) }
        return activities.mapNotNull { info ->
            runCatching {
                val label = info.label.toString()
                val stamp = stampOf(info)
                val runtimeStatusFlags = disabledFlagsOf(info, profile)
                val contentDescription = getUserBadgedLabel(label, profile.user).toString()
                val kept = previous[AppKey(info.componentName.packageName, info.componentName.className, profile.serial)]
                if (kept != null && kept.stamp == stamp && kept.label == label && kept.profile == profile.kind && kept.contentDescription == contentDescription) {
                    // The icon layers stay; the disabled flags are the ones read now.
                    if (kept.runtimeStatusFlags == runtimeStatusFlags) kept else kept.copy(runtimeStatusFlags = runtimeStatusFlags)
                } else {
                    AppEntry(
                        packageName = info.componentName.packageName,
                        className = info.componentName.className,
                        label = label,
                        user = profile.user,
                        userSerial = profile.serial,
                        profile = profile.kind,
                        icon = IconLayers.of(info.getIcon(0), badge, normalizer),
                        stamp = stamp,
                        runtimeStatusFlags = runtimeStatusFlags,
                        contentDescription = contentDescription
                    )
                }
            }.getOrNull()
        }
    }

    /**
     * What an icon was made from: the apk path (Android 11 and later gives a package's base apk a new random directory at each install or
     * update), the split apks and the flags (suspended) of the ApplicationInfo
     * (https://developer.android.com/reference/android/content/pm/ApplicationInfo#sourceDir,
     * https://developer.android.com/reference/android/content/pm/ApplicationInfo#splitSourceDirs), and the screen density the icon was loaded at
     * (Configuration.densityDpi). LauncherActivityInfo has no update time (only getFirstInstallTime, which an update does not change).
     */
    private fun stampOf(info: LauncherActivityInfo): String {
        val application = info.applicationInfo
        // Without FLAG_SUSPENDED: a suspension changes the disabled flags only, never the icon (see [disabledFlagsOf]).
        val flags = application.flags and ApplicationInfo.FLAG_SUSPENDED.inv()
        return "${application.sourceDir}|${application.splitSourceDirs?.joinToString(",")}|$flags|${app.resources.configuration.densityDpi}"
    }

    /**
     * The disabled flags an activity has when it is loaded: FLAG_DISABLED_SUSPENDED when its application is suspended
     * (ApplicationInfo.FLAG_SUSPENDED), FLAG_DISABLED_QUIET_USER when its profile is paused or locked (UserManagerState.isUserQuiet, which
     * PackageUpdatedTask OP_USER_AVAILABILITY_CHANGE sets on the user's items)
     * (https://github.com/LineageOS/android_packages_apps_Trebuchet/blob/lineage-22.2/src/com/android/launcher3/model/PackageUpdatedTask.java#L174-L190).
     */
    private fun disabledFlagsOf(info: LauncherActivityInfo, profile: ProfileInfo): Int {
        var runtimeStatusFlags = 0
        if (info.applicationInfo.flags and ApplicationInfo.FLAG_SUSPENDED != 0) runtimeStatusFlags = runtimeStatusFlags or AppEntry.FLAG_DISABLED_SUSPENDED
        if (profile.quiet) runtimeStatusFlags = runtimeStatusFlags or AppEntry.FLAG_DISABLED_QUIET_USER
        return runtimeStatusFlags
    }

    /** The format of each user's label, by the user's hash: null when the user's label is the label itself (BaseIconCache.mUserFormatString). */
    private val mUserFormatString = HashMap<Int, String?>()

    /**
     * BaseIconCache.getUserBadgedLabel
     * (https://github.com/LineageOS/android_frameworks_libs_systemui/blob/lineage-22.2/iconloaderlib/src/com/android/launcher3/icons/cache/BaseIconCache.java#L315-L328):
     * the label as TalkBack should read it, "Chrome, Work" for an app of the work profile. The format is asked once per user:
     * PackageManager.getUserBadgedLabel on "%1$s", null when it comes back unchanged.
     */
    private fun getUserBadgedLabel(label: CharSequence, user: UserHandle): CharSequence {
        val key = user.hashCode()
        val format: String?
        if (!mUserFormatString.containsKey(key)) {
            var asked: String? = app.packageManager.getUserBadgedLabel(IDENTITY_FORMAT_STRING, user).toString()
            if (TextUtils.equals(IDENTITY_FORMAT_STRING, asked)) {
                asked = null
            }
            mUserFormatString[key] = asked
            format = asked
        } else {
            format = mUserFormatString[key]
        }
        return if (format == null) label else String.format(format, label)
    }

    /**
     * The badge of [user] alone: PackageManager.getUserBadgedIcon on a clear picture draws the badge in its bottom end corner and
     * nothing else (BaseIconFactory.getUserInfo asks the same call, on a blank drawable, to tell a badged profile,
     * https://github.com/LineageOS/android_frameworks_libs_systemui/blob/lineage-22.2/iconloaderlib/src/com/android/launcher3/icons/BaseIconFactory.java;
     * Launcher3 draws its own UserBadgeDrawable at ICON_BADGE_SCALE 0.444, whose resources are not public). Null when the call hands
     * the picture back unchanged.
     */
    private fun badgeOf(user: UserHandle): Bitmap? = runCatching {
        val clear = BitmapDrawable(app.resources, Bitmap.createBitmap(BADGE_PX, BADGE_PX, Bitmap.Config.ARGB_8888))
        val badged = app.packageManager.getUserBadgedIcon(clear, user)
        if (badged === clear) null else badged.toBitmap(BADGE_PX, BADGE_PX)
    }.getOrNull()

    private companion object {
        /** The badge picture's side in pixels: the badge is a part of it, drawn over an icon of 56 dp or so. */
        const val BADGE_PX = 160

        /** The largest icon the apps panel draws, in dp (AppsPanel's coerceIn(44.dp, 72.dp)). */
        const val MAX_ICON_DP = 72

        /** BaseIconCache.IDENTITY_FORMAT_STRING. */
        const val IDENTITY_FORMAT_STRING = "%1\$s"
    }
}
