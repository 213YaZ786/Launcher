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

    @Test
    fun thePrivateSpaceBroadcastsAreListenedToFromAndroid15() {
        assertTrue(Profiles.ACTION_PROFILE_AVAILABLE in Profiles.actions(Build.VERSION_CODES.VANILLA_ICE_CREAM))
        assertTrue(Profiles.ACTION_PROFILE_UNAVAILABLE in Profiles.actions(Build.VERSION_CODES.VANILLA_ICE_CREAM))
        assertFalse(Profiles.ACTION_PROFILE_AVAILABLE in Profiles.actions(Build.VERSION_CODES.UPSIDE_DOWN_CAKE))
        assertTrue(Intent.ACTION_MANAGED_PROFILE_AVAILABLE in Profiles.actions(Build.VERSION_CODES.UPSIDE_DOWN_CAKE))
    }
}
