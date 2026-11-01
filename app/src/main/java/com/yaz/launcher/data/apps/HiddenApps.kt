package com.yaz.launcher.data.apps

import com.yaz.launcher.core.common.writeTextAtomically
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.builtins.SetSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/**
 * The filter of the apps list: an app whose package is hidden is not shown. Trebuchet's HiddenAppsFilter.shouldShowApp
 * (`!mDbHelper.isPackageHidden(app.getPackageName()) && super.shouldShowApp(app)`), here on the set of hidden packages
 * so it runs on the JVM
 * (https://github.com/LineageOS/android_packages_apps_Trebuchet/blob/lineage-22.2/src/com/android/launcher3/lineage/trust/HiddenAppsFilter.java).
 */
object HiddenAppsFilter {
    fun shouldShowApp(hidden: Set<String>, packageName: String): Boolean = packageName !in hidden
}

/**
 * The packages the user hid, kept in a small JSON file written atomically (the family's SettingsStore way,
 * https://github.com/213YaZ786/Calculator, data/settings/SettingsStore.kt). The set and its three calls are Trebuchet's
 * TrustDatabaseHelper for the hidden flag: addHiddenApp, removeHiddenApp, isPackageHidden (a package once hidden is not added
 * twice), with a Set in place of its SQLite table, since a package is one row with one flag
 * (https://github.com/LineageOS/android_packages_apps_Trebuchet/blob/lineage-22.2/src/com/android/launcher3/lineage/trust/db/TrustDatabaseHelper.java).
 * Not taken: the "protected" flag and the lock screen that guards a protected app's launch (LineageUtils.showLockScreen); Trebuchet's
 * hidden list is not behind any authentication, and neither is this one.
 */
class HiddenApps(private val file: File) {

    private val json = Json { ignoreUnknownKeys = true }
    private val serializer = SetSerializer(String.serializer())

    private val _hidden = MutableStateFlow(load())
    val hidden: StateFlow<Set<String>> = _hidden.asStateFlow()

    private fun load(): Set<String> {
        if (!file.exists()) return emptySet()
        return runCatching { json.decodeFromString(serializer, file.readText()) }.getOrDefault(emptySet())
    }

    /** TrustDatabaseHelper.isPackageHidden. */
    fun isPackageHidden(packageName: String): Boolean = packageName in _hidden.value

    /** TrustDatabaseHelper.addHiddenApp. */
    fun hide(packageName: String) {
        if (isPackageHidden(packageName)) return
        set(_hidden.value + packageName)
    }

    /** TrustDatabaseHelper.removeHiddenApp. */
    fun unhide(packageName: String) {
        if (!isPackageHidden(packageName)) return
        set(_hidden.value - packageName)
    }

    private fun set(next: Set<String>) {
        _hidden.value = next
        runCatching { file.writeTextAtomically(json.encodeToString(serializer, next)) }
    }
}
