package com.yaz.launcher.data.apps

import android.content.Context
import android.content.pm.LauncherApps
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.os.UserHandle
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

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

    private val _state = MutableStateFlow(AppsState())
    val state: StateFlow<AppsState> = _state.asStateFlow()

    private val model = io.limitedParallelism(1)
    private val callbacks = ModelLauncherCallbacks(::onPackages)
    private val profileEvents = ProfileEvents(app) { scope.launch(model) { loadAll() } }
    private var started = false

    /** Registers for the changes and loads. Called when the screen comes to the front. */
    fun start() {
        if (started) return
        started = true
        launcherApps.registerCallback(callbacks)
        profileEvents.start()
        scope.launch(model) { loadAll() }
    }

    /** Stops listening. Called when the screen goes. */
    fun stop() {
        if (!started) return
        started = false
        launcherApps.unregisterCallback(callbacks)
        profileEvents.stop()
    }

    /** LoaderTask.loadAllApps. */
    private fun loadAll() {
        val profiles = Profiles.queryAll(app)
        val apps = profiles.flatMap { entriesOf(it, null) }
        _state.value = AppsState(loaded = true, profiles = profiles, apps = apps)
    }

    /** The package callbacks: AllAppsList's addPackage, updatePackage and removePackage on the list. */
    private fun onPackages(op: PackageOp, user: android.os.UserHandle, packages: List<String>) {
        scope.launch(model) {
            var profiles = _state.value.profiles
            var profile = profiles.firstOrNull { it.user == user }
            if (profile == null) {
                // A profile that came after the last load.
                profiles = Profiles.queryAll(app)
                profile = profiles.firstOrNull { it.user == user } ?: return@launch
            }
            var apps = _state.value.apps
            for (pkg in packages) {
                apps = if (op == PackageOp.REMOVE) {
                    AppList.removePackage(apps, pkg, profile.serial)
                } else {
                    AppList.updatePackage(apps, pkg, profile.serial, entriesOf(profile, pkg))
                }
            }
            _state.value = _state.value.copy(profiles = profiles, apps = apps)
        }
    }

    /**
     * The launcher activities of [profile], of one package or of all. The icon is the activity's own, LauncherActivityInfo.getIcon(0),
     * in layers ([IconLayers.of]); the profile's badge comes apart ([badgeOf]), drawn by the screen over the shape. The label is
     * LauncherActivityInfo.getLabel.
     */
    private fun entriesOf(profile: ProfileInfo, packageName: String?): List<AppEntry> {
        val activities = runCatching { launcherApps.getActivityList(packageName, profile.user) }.getOrNull().orEmpty()
        val badge = if (profile.kind == ProfileKind.MAIN) null else badgeOf(profile.user)
        return activities.mapNotNull { info ->
            runCatching {
                AppEntry(
                    packageName = info.componentName.packageName,
                    className = info.componentName.className,
                    label = info.label.toString(),
                    user = profile.user,
                    userSerial = profile.serial,
                    profile = profile.kind,
                    icon = IconLayers.of(info.getIcon(0), badge)
                )
            }.getOrNull()
        }
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
    }
}
