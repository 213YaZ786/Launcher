package com.yaz.launcher.data.apps

// JUnit 4 (https://junit.org/junit4/); TemporaryFolder is JUnit's rule for files (https://junit.org/junit4/javadoc/latest/org/junit/rules/TemporaryFolder.html).
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class RecentsTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val own = "com.yaz.launcher"
    private val launchable = setOf("a", "b", "c", own)

    private fun row(pkg: String, lastUsed: Long, fg: Long = 0L) = UsageRow(pkg, lastUsed, fg)

    @Test
    fun newestFirst() {
        val rows = listOf(row("a", 100), row("b", 300), row("c", 200))
        assertEquals(listOf("b", "c", "a"), Recents.build(rows, launchable, own, emptyMap()))
    }

    @Test
    fun ourOwnPackageIsLeftOut() {
        val rows = listOf(row(own, 900), row("a", 100))
        assertEquals(listOf("a"), Recents.build(rows, launchable, own, emptyMap()))
    }

    @Test
    fun onlyAppsWithALauncherEntryCount() {
        val rows = listOf(row("systemui", 900), row("a", 100))
        assertEquals(listOf("a"), Recents.build(rows, launchable, own, emptyMap()))
    }

    @Test
    fun anAppNeverUsedIsNotThere() {
        val rows = listOf(row("a", 0), row("b", 50))
        assertEquals(listOf("b"), Recents.build(rows, launchable, own, emptyMap()))
    }

    @Test
    fun aPackageAppearsOnce() {
        val rows = listOf(row("a", 100), row("a", 400), row("b", 300))
        assertEquals(listOf("a", "b"), Recents.build(rows, launchable, own, emptyMap()))
    }

    @Test
    fun equalTimesKeepAStableOrder() {
        val rows = listOf(row("c", 100), row("a", 100), row("b", 100))
        assertEquals(listOf("a", "b", "c"), Recents.build(rows, launchable, own, emptyMap()))
    }

    @Test
    fun aRemovedAppStaysOutUntilUsedAgain() {
        val removed = mapOf("a" to 250L)
        assertEquals(listOf("b"), Recents.build(listOf(row("a", 100), row("b", 200)), launchable, own, removed))
        // Used at the very moment it left: still out.
        assertEquals(listOf("b"), Recents.build(listOf(row("a", 250), row("b", 200)), launchable, own, removed))
    }

    @Test
    fun aRemovedAppReappearsWhenUsedAfterItsRemoval() {
        val removed = mapOf("a" to 250L)
        assertEquals(listOf("a", "b"), Recents.build(listOf(row("a", 251), row("b", 200)), launchable, own, removed))
    }

    @Test
    fun pruneDropsWhatNoLongerMatters() {
        val now = 10 * USAGE_WINDOW_MS
        val removed = mapOf(
            "a" to now - 1000,
            "gone" to now - 1000,
            "old" to now - USAGE_WINDOW_MS - 1
        )
        val kept = Recents.prune(removed, setOf("a", "old"), now)
        assertEquals(mapOf("a" to now - 1000), kept)
    }

    @Test
    fun theRemovedSetSurvivesARestart() {
        val file = File(folder.root, "removed.json")
        val first = RemovedRecents(file)
        first.remove("a", 111L)
        first.remove("b", 222L)
        val second = RemovedRecents(file)
        assertEquals(mapOf("a" to 111L, "b" to 222L), second.removed.value)
        second.retain(mapOf("b" to 222L))
        assertEquals(mapOf("b" to 222L), RemovedRecents(file).removed.value)
    }

    @Test
    fun aDamagedFileGivesAnEmptySet() {
        val file = File(folder.root, "removed.json")
        file.writeText("{not json")
        assertTrue(RemovedRecents(file).removed.value.isEmpty())
    }

    @Test
    fun aMissingFileGivesAnEmptySet() {
        assertTrue(RemovedRecents(File(folder.root, "none.json")).removed.value.isEmpty())
    }
}
