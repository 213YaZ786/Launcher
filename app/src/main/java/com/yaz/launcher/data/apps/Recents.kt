package com.yaz.launcher.data.apps

import com.yaz.launcher.core.common.writeTextAtomically
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/**
 * Recents, the list of the panel opened by a swipe up: the packages by last time used, newest first.
 *
 * Source: Taskbar's recents without root. It asks UsageStatsManager for the use of each package, keeps the apps with a
 * launcher intent, leaves out itself, sorts `us2.getLastTimeUsed()` against `us1.getLastTimeUsed()` and drops the
 * duplicates (https://github.com/farmerbb/Taskbar/blob/master/app/src/main/java/com/farmerbb/taskbar/ui/TaskbarController.java,
 * l.828 to 866). Added here: the "removed from recents" set, which Taskbar does not have.
 */
object Recents {

    /**
     * The packages to show. [rows] are the merged rows of [Usage.read]; [launchable] the packages with a launcher
     * activity in the main profile; [own] this app, left out. [removed] maps a package the user swiped away to the
     * time it left: it stays out until it is used again, that is until its last use is later than that time.
     */
    fun build(rows: List<UsageRow>, launchable: Set<String>, own: String, removed: Map<String, Long>): List<String> =
        rows.asSequence()
            .filter { it.lastUsed > 0L }
            .filter { it.packageName != own && it.packageName in launchable }
            // Taskbar's "filter out any duplicate entries", after the sort: the latest use of a package stands.
            .groupBy { it.packageName }
            .map { (pkg, same) -> pkg to same.maxOf { it.lastUsed } }
            .filter { (pkg, lastUsed) -> removed[pkg]?.let { lastUsed > it } ?: true }
            .sortedWith(compareByDescending<Pair<String, Long>> { it.second }.thenBy { it.first })
            .map { it.first }

    /**
     * The removed set without what no longer matters: a package that has no launcher activity any more, and a
     * removal older than the window (the app, used since, is back whatever the set says, and unused it is not in
     * the usage window at all).
     */
    fun prune(removed: Map<String, Long>, launchable: Set<String>, now: Long): Map<String, Long> =
        removed.filter { (pkg, at) -> pkg in launchable && at >= now - USAGE_WINDOW_MS }
}

/**
 * The packages the user took out of Recents, with the time they left, kept in a small JSON file written atomically
 * (the family's SettingsStore way, https://github.com/213YaZ786/Calculator, data/settings/SettingsStore.kt).
 */
class RemovedRecents(private val file: File) {

    private val json = Json { ignoreUnknownKeys = true }
    private val serializer = MapSerializer(String.serializer(), Long.serializer())

    private val _removed = MutableStateFlow(load())
    val removed: StateFlow<Map<String, Long>> = _removed.asStateFlow()

    private fun load(): Map<String, Long> {
        if (!file.exists()) return emptyMap()
        return runCatching { json.decodeFromString(serializer, file.readText()) }.getOrDefault(emptyMap())
    }

    /** The user swiped [packageName] out of Recents at [now]. */
    fun remove(packageName: String, now: Long = System.currentTimeMillis()) = set(_removed.value + (packageName to now))

    /** Keeps only [keep] (see [Recents.prune]). */
    fun retain(keep: Map<String, Long>) {
        if (keep.size != _removed.value.size) set(keep)
    }

    private fun set(map: Map<String, Long>) {
        _removed.value = map
        runCatching { file.writeTextAtomically(json.encodeToString(serializer, map)) }
    }
}
