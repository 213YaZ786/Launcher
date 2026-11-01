package com.yaz.launcher.data.apps

import java.text.Collator
import java.util.Locale

/**
 * The order of the apps panel: the apps used in the last 7 days first, the longest in the foreground first
 * (UsageStats.totalTimeInForeground), then every other app from A to Z. Without usage access the map is empty and the
 * whole list is A to Z.
 *
 * Sources: the sort by time is Taskbar's "most_used" order, `us2.getTotalTimeInForeground()` against
 * `us1.getTotalTimeInForeground()`
 * (https://github.com/farmerbb/Taskbar/blob/master/app/src/main/java/com/farmerbb/taskbar/ui/TaskbarController.java, l.855);
 * the A to Z order compares labels with a Collator at PRIMARY strength and canonical decomposition, as Trebuchet's
 * StringMatcher does, so "Écouter" stands with the Es and case is ignored
 * (https://github.com/LineageOS/android_packages_apps_Trebuchet/blob/lineage-22.2/src/com/android/launcher3/search/StringMatcherUtility.java).
 */
object AppRanking {

    /** Every app from A to Z. */
    fun <T : Listed> az(items: List<T>, locale: Locale = Locale.getDefault()): List<T> = items.sortedWith(labelOrder(locale))

    /**
     * [foregroundMs] is the time in the foreground per package over the last 7 days. An app with none, or a package
     * absent from the map, is "the rest". Ties in time fall back to A to Z.
     */
    fun <T : Listed> rank(items: List<T>, foregroundMs: Map<String, Long>, locale: Locale = Locale.getDefault()): List<T> {
        val byLabel = labelOrder<T>(locale)
        val (used, rest) = items.partition { (foregroundMs[it.packageName] ?: 0L) > 0L }
        val byTime = compareByDescending<T> { foregroundMs[it.packageName] ?: 0L }.then(byLabel)
        return used.sortedWith(byTime) + rest.sortedWith(byLabel)
    }

    /**
     * Labels by Collator at PRIMARY strength; labels that read the same ("e" and "é") fall to the full-strength
     * Collator, then to the package, activity and profile, so the order never changes between two loads.
     */
    internal fun <T : Listed> labelOrder(locale: Locale): Comparator<T> {
        // StringMatcher's collator: PRIMARY strength, canonical decomposition.
        val primary = Collator.getInstance(locale).apply {
            strength = Collator.PRIMARY
            decomposition = Collator.CANONICAL_DECOMPOSITION
        }
        val full = Collator.getInstance(locale).apply { decomposition = Collator.CANONICAL_DECOMPOSITION }
        return Comparator<T> { a, b -> primary.compare(a.label, b.label) }
            .thenComparator { a, b -> full.compare(a.label, b.label) }
            .thenBy { it.packageName }
            .thenBy { it.className }
            .thenBy { it.userSerial }
    }
}
