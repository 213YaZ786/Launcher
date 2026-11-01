package com.yaz.launcher.data.apps

// Sources: the shape is Trebuchet's LauncherModel, which owns the loader and the package callbacks and hands the
// result to the screen (https://github.com/LineageOS/android_packages_apps_Trebuchet/blob/lineage-22.2/src/com/android/launcher3/LauncherModel.java);
// the flows are the data layer of Android's architecture guide, StateFlow for state and kotlinx.coroutines' `combine`
// (https://developer.android.com/topic/architecture/data-layer, https://kotlinlang.org/api/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines.flow/combine.html);
// the usage, ranking and recents are Taskbar's (https://github.com/farmerbb/Taskbar/blob/master/app/src/main/java/com/farmerbb/taskbar/ui/TaskbarController.java).
import android.content.Context
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
 * reads the usage again (when a panel opens).
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

    // The four flows' latest values, for kotlinx.coroutines' combine with four flows
    // (https://kotlinlang.org/api/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines.flow/combine.html).
    private data class Inputs(val apps: AppsState, val use: UsageSnapshot, val gone: Map<String, Long>, val hidden: Set<String>)

    /** A screen ([owner]) came to the front: read everything and follow the changes, unless another screen already does. */
    fun start(owner: Any) {
        owners.add(owner)
        if (job != null) return
        repository.start()
        job = scope.launch {
            readUsage()
            combine(repository.state, usage, removed.removed, hiddenApps.hidden) { apps, use, gone, hidden -> Inputs(apps, use, gone, hidden) }
                .collect { (apps, use, gone, hidden) ->
                    _state.value = withContext(Dispatchers.Default) { build(apps, use, gone, hidden) }
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
    }

    /** The usage again, Store's waiting updates and the dots' listener bound again if it should be, for a panel that opens. */
    fun refresh() {
        scope.launch { readUsage() }
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

    private fun build(apps: AppsState, use: UsageSnapshot, gone: Map<String, Long>, hidden: Set<String>): LauncherState {
        // The filter is applied where the list is built, as Trebuchet applies HiddenAppsFilter in the loader: a hidden package is in
        // neither panel nor Recents (Recents is made from the ranked list below), only in [LauncherState.hidden].
        val (shown, hiddenEntries) = apps.apps.partition { HiddenAppsFilter.shouldShowApp(hidden, it.packageName) }
        val main = shown.filter { it.profile == ProfileKind.MAIN }
        val ranked = AppRanking.rank(main, Usage.foregroundByPackage(use.rows))
        val byPackage = ranked.groupBy { it.packageName }
        val recents = if (use.granted) {
            Recents.build(use.rows, byPackage.keys, app.packageName, gone).mapNotNull { byPackage[it]?.first() }
        } else {
            emptyList()
        }
        return LauncherState(
            loaded = apps.loaded,
            usageGranted = use.granted,
            profiles = apps.profiles,
            apps = ranked,
            others = AppRanking.az(shown.filter { it.profile != ProfileKind.MAIN }),
            recents = recents,
            hidden = AppRanking.az(hiddenEntries)
        )
    }
}
