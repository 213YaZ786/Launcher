package com.yaz.launcher.data.apps

// A locked private space lists no app, an unlocked one does; the work profile is never touched. The cases follow PrivateProfileManager's states
// (STATE_DISABLED: only the header, STATE_ENABLED: the apps; https://github.com/LineageOS/android_packages_apps_Trebuchet/blob/lineage-22.2/src/com/android/launcher3/allapps/PrivateProfileManager.java),
// in the plain JUnit 4 style of the other tests of the model (HideTest, ModelPartsTest; https://junit.org/junit4/).
import android.app.Application
import android.content.Intent
import android.os.Build
import android.os.Process
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(application = Application::class, sdk = [36])
class LockedPrivateTest {

    private data class Fake(
        override val packageName: String,
        override val userSerial: Long,
        val kind: ProfileKind,
        override val className: String = "$packageName.Main",
        override val label: String = packageName
    ) : Listed

    private fun profile(kind: ProfileKind, serial: Long, quiet: Boolean) =
        ProfileInfo(Process.myUserHandle(), kind, serial, quiet = quiet, unlocked = true)

    private val apps = listOf(
        Fake("main", 0L, ProfileKind.MAIN),
        Fake("work", 10L, ProfileKind.WORK),
        Fake("vault", 11L, ProfileKind.PRIVATE),
        Fake("notes", 11L, ProfileKind.PRIVATE)
    )

    private fun shown(profiles: List<ProfileInfo>) = LockedPrivate.without(apps, profiles) { it.kind }.map { it.packageName }

    @Test
    fun aLockedPrivateSpaceListsNoApp() {
        val profiles = listOf(profile(ProfileKind.MAIN, 0L, false), profile(ProfileKind.WORK, 10L, false), profile(ProfileKind.PRIVATE, 11L, true))
        assertEquals(listOf("main", "work"), shown(profiles))
    }

    @Test
    fun anUnlockedPrivateSpaceListsItsApps() {
        val profiles = listOf(profile(ProfileKind.MAIN, 0L, false), profile(ProfileKind.WORK, 10L, false), profile(ProfileKind.PRIVATE, 11L, false))
        assertEquals(listOf("main", "work", "vault", "notes"), shown(profiles))
    }

    @Test
    fun aPausedWorkProfileKeepsItsApps() {
        val profiles = listOf(profile(ProfileKind.MAIN, 0L, false), profile(ProfileKind.WORK, 10L, true), profile(ProfileKind.PRIVATE, 11L, false))
        assertTrue("work" in shown(profiles))
    }

    @Test
    fun aQuietProfileOfAnotherKindWithTheSameSerialDoesNotHidePrivateApps() {
        val profiles = listOf(profile(ProfileKind.WORK, 11L, true))
        assertEquals(listOf("main", "work", "vault", "notes"), shown(profiles))
    }

    // UserProfileManager's states (STATE_UNKNOWN, STATE_ENABLED, STATE_DISABLED, STATE_TRANSITION) and reset(): the state follows the quiet flag, and a
    // TRANSITION stays until the quiet state arrives (https://github.com/LineageOS/android_packages_apps_Trebuchet/blob/lineage-22.2/src/com/android/launcher3/allapps/UserProfileManager.java#STATE_TRANSITION).
    @Test
    fun resetFollowsTheQuietFlagAndATransitionLeavesWhenItArrives() {
        val manager = UserProfileManager()
        val running = profile(ProfileKind.PRIVATE, 11L, false)
        assertEquals(UserProfileManager.STATE_UNKNOWN, manager.getCurrentState(11L))
        assertEquals(UserProfileManager.STATE_ENABLED, manager.reset(listOf(running))[11L])
        manager.setCurrentState(running, UserProfileManager.STATE_TRANSITION)
        // The profile reloaded with the same quiet flag (another event): still in transition.
        assertEquals(UserProfileManager.STATE_TRANSITION, manager.reset(listOf(running))[11L])
        // Android refused, or the panels are at rest: the state leaves the transition.
        assertEquals(UserProfileManager.STATE_ENABLED, manager.reset(listOf(running), force = true)[11L])
        manager.setCurrentState(running, UserProfileManager.STATE_TRANSITION)
        // The quiet state arrived.
        val locked = profile(ProfileKind.PRIVATE, 11L, true)
        assertEquals(UserProfileManager.STATE_DISABLED, manager.reset(listOf(locked))[11L])
        assertFalse(manager.isEnabled(11L))
    }

    @Test
    fun aProfileWithoutAStateShowsWhatItsQuietFlagGives() {
        val states = mapOf(10L to UserProfileManager.STATE_TRANSITION)
        assertEquals(UserProfileManager.STATE_TRANSITION, UserProfileManager.getCurrentState(profile(ProfileKind.WORK, 10L, true), states))
        assertEquals(UserProfileManager.STATE_DISABLED, UserProfileManager.getCurrentState(profile(ProfileKind.WORK, 12L, true), states))
        assertEquals(UserProfileManager.STATE_ENABLED, UserProfileManager.getCurrentState(profile(ProfileKind.WORK, 12L, false), states))
    }

    // WorkProfileManager.shouldShowWorkApps and addWorkItems: only STATE_DISABLED hides the work apps and adds the disabled card
    // (https://github.com/LineageOS/android_packages_apps_Trebuchet/blob/lineage-22.2/src/com/android/launcher3/allapps/WorkProfileManager.java#addWorkItems).
    @Test
    fun pausedWorkAppsAreNotListedAndTheDisabledCardIsAdded() {
        assertFalse(WorkProfileManager.shouldShowWorkApps(UserProfileManager.STATE_DISABLED))
        assertTrue(WorkProfileManager.shouldShowWorkApps(UserProfileManager.STATE_TRANSITION))
        assertTrue(WorkProfileManager.shouldShowWorkApps(UserProfileManager.STATE_ENABLED))
        assertTrue(WorkProfileManager.addWorkItems(UserProfileManager.STATE_DISABLED))
        assertFalse(WorkProfileManager.addWorkItems(UserProfileManager.STATE_ENABLED))
    }

    // The settings cell and the Install apps cell of the unlocked private space (PrivateProfileManager.updateView, addPrivateSpaceInstallAppButton) count in
    // the block, so the next block still starts at the first row of a column
    // (https://github.com/LineageOS/android_packages_apps_Trebuchet/blob/lineage-22.2/src/com/android/launcher3/allapps/PrivateProfileManager.java#addPrivateSpaceInstallAppButton).
    @Test
    fun theExtraCellsOfThePrivateSpaceCountInItsBlock() {
        val blocks = com.yaz.launcher.feature.home.profileBlocks(5, 4, listOf(listOf("a", "b"), listOf("c")), listOf(true, false), listOf(true, false))
        assertEquals(3, blocks[0].padding)
        assertEquals(2, blocks[0].extra)
        // 5 placed, 3 empty cells, the tile, two apps and the two extra cells: 13 cells, so the next block fills 3 cells of the last column.
        assertEquals(3, blocks[1].padding)
    }

    @Test
    fun thePrivateSpaceBroadcastsAreListenedToFromAndroid15() {
        assertTrue(Profiles.ACTION_PROFILE_AVAILABLE in Profiles.actions(Build.VERSION_CODES.VANILLA_ICE_CREAM))
        assertTrue(Profiles.ACTION_PROFILE_UNAVAILABLE in Profiles.actions(Build.VERSION_CODES.VANILLA_ICE_CREAM))
        assertFalse(Profiles.ACTION_PROFILE_AVAILABLE in Profiles.actions(Build.VERSION_CODES.UPSIDE_DOWN_CAKE))
        assertTrue(Intent.ACTION_MANAGED_PROFILE_AVAILABLE in Profiles.actions(Build.VERSION_CODES.UPSIDE_DOWN_CAKE))
    }
}
