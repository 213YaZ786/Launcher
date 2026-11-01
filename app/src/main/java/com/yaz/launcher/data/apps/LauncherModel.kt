package com.yaz.launcher.data.apps

// Sources: the shape is Trebuchet's LauncherModel, which owns the loader and the package callbacks and hands the
// result to the screen (https://github.com/LineageOS/android_packages_apps_Trebuchet/blob/lineage-22.2/src/com/android/launcher3/LauncherModel.java);
// the flows are the data layer of Android's architecture guide, StateFlow for state and kotlinx.coroutines' `combine`
// (https://developer.android.com/topic/architecture/data-layer, https://kotlinlang.org/api/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines.flow/combine.html);
// the usage, ranking and recents are Taskbar's (https://github.com/farmerbb/Taskbar/blob/master/app/src/main/java/com/farmerbb/taskbar/ui/TaskbarController.java).
import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import com.yaz.launcher.data.notify.DotKey
import com.yaz.launcher.data.notify.NotificationDots
import com.yaz.launcher.data.store.StoreMarks
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The most apps Recents lists: Taskbar's max_num_of_recents default (U.java, PREF_MAX_NUM_OF_RECENTS, "10"). */
const val MAX_RECENTS = 10

/**
 * The apps of a locked private space are not listed, only its header is shown. After PrivateProfileManager: in STATE_DISABLED (the space is
 * locked, `isQuietModeEnabled`) the private apps are not added to the list and only the header with its lock button stays, and
 * `isPrivateSpaceHidden` / `setQuietMode` follow the profile's quiet state
 * (https://github.com/LineageOS/android_packages_apps_Trebuchet/blob/lineage-22.2/src/com/android/launcher3/allapps/PrivateProfileManager.java).
 * Difference: Trebuchet filters in its adapter items, here the list is filtered where the model is built, so the Hidden apps screen
 * does not list a locked private app either. The work profile is not touched.
 */
object LockedPrivate {
    /**
     * [profiles] without a locked private space when the user chose to hide it while locked: PrivateProfileManager.isPrivateSpaceHidden is
     * `getCurrentState() == STATE_DISABLED && SettingsCache.getValue(PRIVATE_SPACE_HIDE_WHEN_LOCKED_URI, 0)`, and
     * AlphabeticalAppsList.addPrivateSpaceItems adds neither the header nor the apps then
     * (https://github.com/LineageOS/android_packages_apps_Trebuchet/blob/lineage-22.2/src/com/android/launcher3/allapps/AlphabeticalAppsList.java).
     * The panel makes the private space's title and tile from this list, so a profile out of it shows nothing.
     */
    fun visible(profiles: List<ProfileInfo>, hideWhenLocked: Boolean): List<ProfileInfo> =
        if (hideWhenLocked) profiles.filterNot { it.kind == ProfileKind.PRIVATE && it.quiet } else profiles

    /** [items] without those of a PRIVATE profile (their [kindOf]) whose [ProfileInfo.quiet] is true. */
    fun <T : Listed> without(items: List<T>, profiles: List<ProfileInfo>, kindOf: (T) -> ProfileKind): List<T> {
        val locked = profiles.filter { it.kind == ProfileKind.PRIVATE && it.quiet }.map { it.serial }.toSet()
        if (locked.isEmpty()) return items
        return items.filterNot { kindOf(it) == ProfileKind.PRIVATE && it.userSerial in locked }
    }
}

/**
 * The setting "Hide when locked" of the private space, Settings.Secure `hide_privatespace_entry_point` (hidden field
 * Settings.Secure.HIDE_PRIVATESPACE_ENTRY_POINT), read and watched as Trebuchet's SettingsCache does: `updateValue` reads
 * `Settings.Secure.getInt(resolver, key, default) == 1` (default 0 for this key), and a ContentObserver on the key's Uri, registered with
 * `registerContentObserver(uri, false, observer)` on a main-thread Handler, reads it again on each change
 * (https://github.com/LineageOS/android_packages_apps_Trebuchet/blob/lineage-22.2/src/com/android/launcher3/util/SettingsCache.java,
 * PRIVATE_SPACE_HIDE_WHEN_LOCKED_URI). Difference: the observer lives from [start] to [stop] only, the model's lifetime, where
 * SettingsCache lives with the app process, so nothing listens while no home screen is in front.
 */
class HideWhenLocked(context: Context) {
    private val resolver = context.applicationContext.contentResolver
    private val _value = MutableStateFlow(false)

    /** True when the user hides the private space while it is locked. */
    val value: StateFlow<Boolean> = _value.asStateFlow()

    private val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) = read()
    }

    private fun read() {
        _value.value = Settings.Secure.getInt(resolver, KEY, 0) == 1
    }

    /** Reads the setting and follows it. Main thread. */
    fun start() {
        read()
        resolver.registerContentObserver(Settings.Secure.getUriFor(KEY), false, observer)
    }

    /** Stops following. */
    fun stop() = resolver.unregisterContentObserver(observer)

    private companion object {
        const val KEY = "hide_privatespace_entry_point"
    }
}

/** What the panels show. */
data class LauncherState(
    val loaded: Boolean = false,
    /** Usage access is granted: Recents and the order by use work. Without it, A to Z and no Recents. */
    val usageGranted: Boolean = false,
    val profiles: List<ProfileInfo> = emptyList(),
    /** The apps panel: the owner's apps, the most used of the last 7 days first, then A to Z. */
    val apps: List<AppEntry> = emptyList(),
    /** The apps of the other profiles (work, private space, clone), A to Z. */
    val others: List<AppEntry> = emptyList(),
    /** The Recents panel: apps by last use, newest first. */
    val recents: List<AppEntry> = emptyList(),
    /** The apps the user hid, of every profile, A to Z: in no panel, only in the Hidden apps screen. */
    val hidden: List<AppEntry> = emptyList()
)

/**
 * The model of the panels: [AppsRepository]'s list, the usage of the last 7 days and the set of removed recents,
 * combined into [state]. It works from [start] to [stop], that is while a home screen is in front; [refresh]
 * reads the usage again (when the panels are closed).
 *
 * Recents and the order by use: see [Recents], [AppRanking] and [Usage]. The usage statistics are those of the
 * owner, so only the owner's apps are ranked and have recents; the other profiles' apps stay A to Z.
 */
class LauncherModel(
    context: Context,
    private val repository: AppsRepository,
    private val removed: RemovedRecents,
    private val hiddenApps: HiddenApps,
    private val scope: CoroutineScope,
    private val io: CoroutineDispatcher = Dispatchers.IO
) {
    private val app = context.applicationContext

    private val usage = MutableStateFlow(UsageSnapshot())
    private val _state = MutableStateFlow(LauncherState())
    val state: StateFlow<LauncherState> = _state.asStateFlow()

    /** The apps with a notification dot (the listener's set; empty without access). */
    val dots: StateFlow<Set<DotKey>> = NotificationDots.keys

    private val _updates = MutableStateFlow<Set<String>>(emptySet())

    /** The packages with an update waiting in Store, read when a panel opens. */
    val updates: StateFlow<Set<String>> = _updates.asStateFlow()

    private var job: Job? = null

    // Per-instance registration, as Trebuchet's LauncherModel.addCallbacks and removeCallbacks (`mCallbacksList.remove(callbacks)` detaches only that
    // Launcher): when Android replaces the home activity, the new one starts before the old one stops (events log: wm_on_start_called new, then wm_on_stop_called old)
    // (https://github.com/LineageOS/android_packages_apps_Trebuchet/blob/lineage-22.2/src/com/android/launcher3/LauncherModel.kt). Main thread only.
    private val owners = mutableSetOf<Any>()

    private data class UsageSnapshot(val granted: Boolean = false, val rows: List<UsageRow> = emptyList())

    private val hideWhenLocked = HideWhenLocked(app)

    // The five flows' latest values, for kotlinx.coroutines' combine with five flows
    // (https://kotlinlang.org/api/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines.flow/combine.html).
    private data class Inputs(
        val apps: AppsState,
        val use: UsageSnapshot,
        val gone: Map<String, Long>,
        val hidden: Set<String>,
        val hideLocked: Boolean
    )

    /** A screen ([owner]) came to the front: read everything and follow the changes, unless another screen already does. */
    fun start(owner: Any) {
        owners.add(owner)
        if (job != null) return
        repository.start()
        hideWhenLocked.start()
        readMarks()
        job = scope.launch {
            readUsage()
            combine(repository.state, usage, removed.removed, hiddenApps.hidden, hideWhenLocked.value) { apps, use, gone, hidden, hideLocked ->
                Inputs(apps, use, gone, hidden, hideLocked)
            }
                .collect { (apps, use, gone, hidden, hideLocked) ->
                    _state.value = withContext(Dispatchers.Default) { build(apps, use, gone, hidden, hideLocked) }
                    if (apps.loaded) {
                        val launchable = apps.apps.filter { it.profile == ProfileKind.MAIN }.map { it.packageName }.toSet()
                        removed.retain(Recents.prune(gone, launchable, System.currentTimeMillis()))
                    }
                }
        }
    }

    /** A screen ([owner]) went: nothing listens any more once no screen is left in front. */
    fun stop(owner: Any) {
        if (!owners.remove(owner) || owners.isNotEmpty()) return
        job?.cancel()
        job = null
        repository.stop()
        hideWhenLocked.stop()
    }

    /**
     * The usage again, which ranks the apps and Recents anew, Store's waiting updates and the dots' listener bound again if it should be.
     * Called once every panel is back at rest, closed, and never while one opens or is open: the order is fixed at bind, as Trebuchet's
     * AlphabeticalAppsList builds its items once per model change (https://github.com/LineageOS/android_packages_apps_Trebuchet/blob/lineage-22.2/src/com/android/launcher3/allapps/AlphabeticalAppsList.java).
     */
    fun refresh() {
        scope.launch { readUsage() }
        readMarks()
    }

    private fun readMarks() {
        scope.launch(io) {
            NotificationDots.rebindIfNeeded(app)
            _updates.value = StoreMarks.pending(app)
        }
    }

    /** A recent swiped away: out of the list until the app is used again. */
    fun removeFromRecents(packageName: String) = removed.remove(packageName)

    // TrustDatabaseHelper.addHiddenApp and removeHiddenApp, through [HiddenApps]
    // (https://github.com/LineageOS/android_packages_apps_Trebuchet/blob/lineage-22.2/src/com/android/launcher3/lineage/trust/db/TrustDatabaseHelper.java).
    /** The package leaves the apps panel and Recents. */
    fun hide(packageName: String) = hiddenApps.hide(packageName)

    /** The package comes back. */
    fun unhide(packageName: String) = hiddenApps.unhide(packageName)

    private suspend fun readUsage() {
        usage.value = withContext(io) { UsageSnapshot(Usage.granted(app), Usage.read(app)) }
    }

    private fun build(apps: AppsState, use: UsageSnapshot, gone: Map<String, Long>, hidden: Set<String>, hideLocked: Boolean): LauncherState {
        // The filter is applied where the list is built, as Trebuchet applies HiddenAppsFilter in the loader: a hidden package is in
        // neither panel nor Recents (Recents is made from the ranked list below), only in [LauncherState.hidden].
        // A locked private space shows its header only, no app anywhere (PrivateProfileManager, see [LockedPrivate]).
        val (shown, hiddenEntries) = LockedPrivate.without(apps.apps, apps.profiles, { it.profile }).partition { HiddenAppsFilter.shouldShowApp(hidden, it.packageName) }
        val main = shown.filter { it.profile == ProfileKind.MAIN }
        val ranked = AppRanking.rank(main, Usage.foregroundByPackage(use.rows))
        val byPackage = ranked.groupBy { it.packageName }
        val recents = if (use.granted) {
            // At most 10, as Taskbar's recents default (its max_num_of_recents preference, read with the default "10" in
            // https://github.com/farmerbb/Taskbar/blob/master/app/src/main/java/com/farmerbb/taskbar/util/U.java), after the filtering.
            Recents.build(use.rows, byPackage.keys, app.packageName, gone).mapNotNull { byPackage[it]?.first() }.take(MAX_RECENTS)
        } else {
            emptyList()
        }
        return LauncherState(
            loaded = apps.loaded,
            usageGranted = use.granted,
            // A locked private space the user hides while locked has no title and no tile (see [LockedPrivate.visible]).
            profiles = LockedPrivate.visible(apps.profiles, hideLocked),
            apps = ranked,
            others = AppRanking.az(shown.filter { it.profile != ProfileKind.MAIN }),
            recents = recents,
            hidden = AppRanking.az(hiddenEntries)
        )
    }
}
