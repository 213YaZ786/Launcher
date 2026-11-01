package com.yaz.launcher.data.apps

// JUnit 4 (https://junit.org/junit4/). The cases for the shortcuts follow what PopupPopulator's comments promise
// (https://github.com/LineageOS/android_packages_apps_Trebuchet/blob/lineage-22.2/src/com/android/launcher3/popup/PopupPopulator.java);
// the cases for the list follow AllAppsList's own comments (model/AllAppsList.java).
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelPartsTest {

    private data class Sc(val id: String, val manifest: Boolean, val rank: Int) {
        val dynamic: Boolean get() = !manifest
    }

    private fun sort(list: List<Sc>) = AppShortcuts.sortAndFilter(list, { it.manifest }, { it.dynamic }, { it.rank }).map { it.id }

    @Test
    fun manifestShortcutsComeBeforeDynamicOnesThenByRank() {
        val list = listOf(Sc("d2", false, 2), Sc("m1", true, 1), Sc("d1", false, 1), Sc("m0", true, 0))
        assertEquals(listOf("m0", "m1", "d1", "d2"), sort(list))
    }

    @Test
    fun fourOrFewerAreAllKept() {
        assertEquals(emptyList<String>(), sort(emptyList()))
        assertEquals(listOf("m0", "d0"), sort(listOf(Sc("d0", false, 0), Sc("m0", true, 0))))
    }

    @Test
    fun moreThanFourKeepTheFirstFourOfStaticOnes() {
        val list = (0..5).map { Sc("m$it", true, it) }
        assertEquals(listOf("m0", "m1", "m2", "m3"), sort(list))
    }

    @Test
    fun twoDynamicShortcutsReplaceTheLastStaticOnes() {
        val list = (0..3).map { Sc("m$it", true, it) } + listOf(Sc("d0", false, 0), Sc("d1", false, 1), Sc("d2", false, 2))
        assertEquals(listOf("m0", "m1", "d0", "d1"), sort(list))
    }

    @Test
    fun oneDynamicShortcutTakesOnePlace() {
        val list = (0..4).map { Sc("m$it", true, it) } + Sc("d0", false, 0)
        assertEquals(listOf("m0", "m1", "m2", "d0"), sort(list))
        assertEquals(AppShortcuts.MAX_SHORTCUTS, sort(list).size)
    }

    // The list of apps.

    private fun app(pkg: String, cls: String, user: Long = 0L, label: String = pkg) = Fake(pkg, label, cls, user)

    @Test
    fun anAppAlreadyListedIsNotAddedTwice() {
        val list = listOf(app("a", "A"))
        assertEquals(list, AppList.add(list, app("a", "A")))
        assertEquals(2, AppList.add(list, app("a", "B")).size)
        assertEquals(2, AppList.add(list, app("a", "A", user = 10L)).size)
    }

    @Test
    fun removingAPackageLeavesTheOthersAndOtherProfiles() {
        val list = listOf(app("a", "A"), app("a", "B"), app("a", "A", 10L), app("b", "A"))
        val left = AppList.removePackage(list, "a", 0L)
        assertEquals(listOf(app("a", "A", 10L), app("b", "A")), left)
    }

    @Test
    fun updatingAPackageDropsTheVanishedActivityKeepsTheKnownAndAddsTheNew() {
        val list = listOf(app("a", "Old"), app("a", "Same", label = "before"), app("b", "B"))
        val fresh = listOf(app("a", "Same", label = "after"), app("a", "New"))
        val updated = AppList.updatePackage(list, "a", 0L, fresh)
        assertEquals(setOf(app("b", "B"), app("a", "Same", label = "after"), app("a", "New")), updated.toSet())
        assertEquals(3, updated.size)
    }

    @Test
    fun updatingAPackageWithNothingFreshRemovesIt() {
        val list = listOf(app("a", "A"), app("b", "B"))
        assertEquals(listOf(app("b", "B")), AppList.updatePackage(list, "a", 0L, emptyList()))
    }

    @Test
    fun updatingIgnoresFreshEntriesOfAnotherPackageOrProfile() {
        val list = listOf(app("a", "A"))
        val fresh = listOf(app("a", "A"), app("x", "X"), app("a", "A", 10L))
        assertEquals(listOf(app("a", "A")), AppList.updatePackage(list, "a", 0L, fresh))
    }

    @Test
    fun aRemovedProfileTakesItsAppsWithIt() {
        val list = listOf(app("a", "A"), app("a", "A", 10L), app("b", "B", 10L))
        assertEquals(listOf(app("a", "A")), AppList.removeProfile(list, 10L))
    }

    // The profiles and the usage.

    @Test
    fun userTypesGiveTheirKind() {
        assertEquals(ProfileKind.WORK, Profiles.kindOfUserType("android.os.usertype.profile.MANAGED"))
        assertEquals(ProfileKind.CLONE, Profiles.kindOfUserType("android.os.usertype.profile.CLONE"))
        assertEquals(ProfileKind.PRIVATE, Profiles.kindOfUserType("android.os.usertype.profile.PRIVATE"))
        assertEquals(ProfileKind.MAIN, Profiles.kindOfUserType("android.os.usertype.full.SYSTEM"))
        assertEquals(ProfileKind.MAIN, Profiles.kindOfUserType(null))
    }

    @Test
    fun bucketsOfOnePackageAreMergedLikeUsageStatsAdd() {
        val buckets = listOf(UsageRow("a", 100, 1000), UsageRow("b", 50, 10), UsageRow("a", 300, 2500))
        val events = listOf(UsageRow("a", 200, 0), UsageRow("c", 400, 0))
        val merged = Usage.merge(buckets, events).associateBy { it.packageName }
        assertEquals(UsageRow("a", 300, 3500), merged["a"])
        assertEquals(UsageRow("b", 50, 10), merged["b"])
        assertEquals(UsageRow("c", 400, 0), merged["c"])
        assertEquals(3, merged.size)
    }

    @Test
    fun anEventLaterThanTheBucketsMovesTheLastUse() {
        val merged = Usage.merge(listOf(UsageRow("a", 100, 5)), listOf(UsageRow("a", 900, 0)))
        assertEquals(listOf(UsageRow("a", 900, 5)), merged)
    }

    @Test
    fun onlyPackagesWithTimeAreRanked() {
        val times = Usage.foregroundByPackage(listOf(UsageRow("a", 1, 0), UsageRow("b", 1, 40)))
        assertEquals(mapOf("b" to 40L), times)
        assertTrue(Usage.foregroundByPackage(emptyList()).isEmpty())
    }
}
