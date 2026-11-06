package com.yaz.launcher.data.apps

// Robolectric for the android types of an entry (https://robolectric.org/). The cases follow SecondaryDropTarget.getButtonType, app branch: INVALID when
// the user's restrictions disallow it, in the private space, for a system app, without an uninstall target; UNINSTALL otherwise
// (https://github.com/LineageOS/android_packages_apps_Trebuchet/blob/lineage-22.2/src/com/android/launcher3/SecondaryDropTarget.java#getButtonType).
import android.app.Application
import android.content.ComponentName
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.yaz.launcher.feature.home.Fixtures
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(application = Application::class, sdk = [36])
class UninstallButtonTest {

    private val app = Fixtures.apps[0]
    private val target = ComponentName(app.packageName, app.className)

    @Test
    fun anAppOfTheUserMayBeUninstalled() {
        val flagged = app.copy(runtimeStatusFlags = AppEntry.FLAG_SYSTEM_NO)
        assertEquals(AppActions.UNINSTALL, AppActions.getButtonType(flagged, false) { target })
    }

    @Test
    fun aRestrictedUserHasNoUninstall() {
        // DISALLOW_APPS_CONTROL or DISALLOW_UNINSTALL_APPS in UserManager.getUserRestrictions: the caller passes uninstallDisabled.
        val flagged = app.copy(runtimeStatusFlags = AppEntry.FLAG_SYSTEM_NO)
        assertEquals(AppActions.INVALID, AppActions.getButtonType(flagged, true) { target })
    }

    @Test
    fun theUninstallTargetIsNotAskedOnceTheAnswerIsKnown() {
        val system = app.copy(runtimeStatusFlags = AppEntry.FLAG_SYSTEM_YES)
        var asked = false
        assertEquals(AppActions.INVALID, AppActions.getButtonType(system, false) { asked = true; target })
        assertEquals(false, asked)
    }

    // SecondaryDropTarget.getButtonType: "UserCache.getUserInfo(info.user).isPrivate()" gives INVALID
    // (https://github.com/LineageOS/android_packages_apps_Trebuchet/blob/lineage-22.2/src/com/android/launcher3/SecondaryDropTarget.java#getButtonType)
    @Test
    fun thePrivateSpaceHasNoUninstall() {
        val private = app.copy(profile = ProfileKind.PRIVATE, runtimeStatusFlags = AppEntry.FLAG_SYSTEM_NO)
        assertEquals(AppActions.INVALID, AppActions.getButtonType(private, false) { target })
    }

    @Test
    fun aWorkAppMayBeUninstalled() {
        val work = app.copy(profile = ProfileKind.WORK, runtimeStatusFlags = AppEntry.FLAG_SYSTEM_NO)
        assertEquals(AppActions.UNINSTALL, AppActions.getButtonType(work, false) { target })
    }

    @Test
    fun aSystemAppHasNoUninstall() {
        val system = app.copy(runtimeStatusFlags = AppEntry.FLAG_SYSTEM_YES)
        assertEquals(AppActions.INVALID, AppActions.getButtonType(system, false) { target })
    }

    @Test
    fun anEntryWithNoSystemFlagIsNotTakenForASystemApp() {
        // (runtimeStatusFlags & FLAG_SYSTEM_MASK) != 0 && (runtimeStatusFlags & FLAG_SYSTEM_NO) == 0: neither bit set (flags not read) is not a system app.
        assertEquals(AppActions.UNINSTALL, AppActions.getButtonType(app, false) { target })
    }

    @Test
    fun withoutAnUninstallTargetThereIsNoUninstall() {
        val flagged = app.copy(runtimeStatusFlags = AppEntry.FLAG_SYSTEM_NO)
        assertEquals(AppActions.INVALID, AppActions.getButtonType(flagged, false) { null })
    }

    @Test
    fun theSystemFlagsDoNotDisableTheApp() {
        val system = app.copy(runtimeStatusFlags = AppEntry.FLAG_SYSTEM_YES)
        assertEquals(false, system.isDisabled())
    }
}
