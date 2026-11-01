package com.yaz.launcher.data.apps

// JUnit 4 (https://junit.org/junit4/), the protocol of the family's unit tests. Locales are given, so the result
// does not depend on the machine's.
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

internal data class Fake(
    override val packageName: String,
    override val label: String,
    override val className: String = "Main",
    override val userSerial: Long = 0L
) : Listed

class AppRankingTest {

    private fun names(list: List<Fake>) = list.map { it.label }

    @Test
    fun usedAppsComeFirstLongestInTheForegroundFirst() {
        val apps = listOf(Fake("a", "Alpha"), Fake("b", "Beta"), Fake("c", "Gamma"), Fake("d", "Delta"))
        val ranked = AppRanking.rank(apps, mapOf("a" to 10L, "c" to 30L, "b" to 20L), Locale.US)
        assertEquals(listOf("Gamma", "Beta", "Alpha", "Delta"), names(ranked))
    }

    @Test
    fun theRestFollowsFromAToZ() {
        val apps = listOf(Fake("z", "Zoom"), Fake("m", "Maps"), Fake("u", "Used"), Fake("c", "Calendar"))
        val ranked = AppRanking.rank(apps, mapOf("u" to 5L), Locale.US)
        assertEquals(listOf("Used", "Calendar", "Maps", "Zoom"), names(ranked))
    }

    @Test
    fun equalTimesFallBackToAToZ() {
        val apps = listOf(Fake("b", "Bravo"), Fake("a", "Alpha"), Fake("n", "None"), Fake("k", "Key"))
        val ranked = AppRanking.rank(apps, mapOf("b" to 50L, "a" to 50L), Locale.US)
        assertEquals(listOf("Alpha", "Bravo", "Key", "None"), names(ranked))
    }

    @Test
    fun noUsageMeansAToZ() {
        val apps = listOf(Fake("c", "Camera"), Fake("a", "Agenda"), Fake("b", "Browser"))
        assertEquals(listOf("Agenda", "Browser", "Camera"), names(AppRanking.rank(apps, emptyMap(), Locale.US)))
    }

    @Test
    fun anAppWithZeroTimeIsInTheRest() {
        val apps = listOf(Fake("a", "Alpha"), Fake("b", "Beta"))
        val ranked = AppRanking.rank(apps, mapOf("b" to 0L), Locale.US)
        assertEquals(listOf("Alpha", "Beta"), names(ranked))
    }

    @Test
    fun accentsSortWithTheirLetter() {
        val apps = listOf(Fake("z", "Zoom"), Fake("e", "Écouter"), Fake("a", "Agenda"), Fake("c", "Calendrier"), Fake("o", "Œuvres"))
        val ranked = AppRanking.az(apps, Locale.FRENCH)
        assertEquals(listOf("Agenda", "Calendrier", "Écouter", "Œuvres", "Zoom"), names(ranked))
    }

    @Test
    fun accentedLabelsAreRankedLikeTheOthersByTime() {
        val apps = listOf(Fake("e", "Éditeur"), Fake("f", "Fichiers"), Fake("a", "Agenda"))
        val ranked = AppRanking.rank(apps, mapOf("f" to 10L), Locale.FRENCH)
        assertEquals(listOf("Fichiers", "Agenda", "Éditeur"), names(ranked))
    }

    @Test
    fun caseIsIgnored() {
        val apps = listOf(Fake("b", "banana"), Fake("a", "Apple"), Fake("c", "cherry"), Fake("d", "Date"))
        assertEquals(listOf("Apple", "banana", "cherry", "Date"), names(AppRanking.az(apps, Locale.US)))
    }

    @Test
    fun theOrderDoesNotDependOnTheOrderOfTheInput() {
        val apps = listOf(
            Fake("p1", "Phone", "A", 0L), Fake("p1", "Phone", "A", 10L), Fake("p2", "Phone", "B", 0L),
            Fake("e1", "Editeur"), Fake("e2", "Éditeur")
        )
        val first = AppRanking.az(apps, Locale.FRENCH)
        val second = AppRanking.az(apps.reversed(), Locale.FRENCH)
        assertEquals(first, second)
        assertEquals(apps.size, first.size)
    }

    @Test
    fun theSameAppInTwoProfilesKeepsBothEntries() {
        val apps = listOf(Fake("w", "Mail", userSerial = 10L), Fake("w", "Mail", userSerial = 0L))
        val ranked = AppRanking.rank(apps, mapOf("w" to 7L), Locale.US)
        assertEquals(listOf(0L, 10L), ranked.map { it.userSerial })
    }
}
