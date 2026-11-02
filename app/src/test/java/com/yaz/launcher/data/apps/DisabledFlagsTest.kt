package com.yaz.launcher.data.apps

// Robolectric for the android types of an entry (https://robolectric.org/). The cases follow PackageUpdatedTask's OP_SUSPEND and OP_UNSUSPEND, which set
// and clear FLAG_DISABLED_SUSPENDED on the items of the packages only, and ItemClickHandler.handleDisabledItemClicked, which lets a suspended or
// paused app start (https://github.com/LineageOS/android_packages_apps_Trebuchet/blob/lineage-22.2/src/com/android/launcher3/model/PackageUpdatedTask.java,
// https://github.com/LineageOS/android_packages_apps_Trebuchet/blob/lineage-22.2/src/com/android/launcher3/touch/ItemClickHandler.java).
import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.yaz.launcher.feature.home.Fixtures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(application = Application::class, sdk = [36])
class DisabledFlagsTest {

    private val apps = Fixtures.apps.take(3)

    private fun suspend(on: Boolean) = { flags: Int ->
        if (on) flags or AppEntry.FLAG_DISABLED_SUSPENDED else flags and AppEntry.FLAG_DISABLED_SUSPENDED.inv()
    }

    @Test
    fun suspendingAPackageDisablesOnlyItsEntries() {
        val target = apps[1].packageName
        val after = AppList.updateDisabledFlags(apps, { it.packageName == target }, suspend(true))
        assertTrue(after[1].isDisabled())
        assertFalse(after[0].isDisabled())
        assertFalse(after[2].isDisabled())
        // The icon layers are the same, so the screen's caches still hit.
        assertSame(apps[1].icon, after[1].icon)
        // The entries differ for the flow, the untouched ones are the same objects.
        assertFalse(apps[1] == after[1])
        assertSame(apps[0], after[0])
    }

    @Test
    fun unsuspendingClearsTheFlagAndKeepsTheOthers() {
        val quiet = apps[0].copy(runtimeStatusFlags = AppEntry.FLAG_DISABLED_SUSPENDED or AppEntry.FLAG_DISABLED_QUIET_USER)
        val after = AppList.updateDisabledFlags(listOf(quiet), { true }, suspend(false))
        assertEquals(AppEntry.FLAG_DISABLED_QUIET_USER, after[0].runtimeStatusFlags)
        assertTrue(after[0].isDisabled())
    }

    @Test
    fun aFlagThatDoesNotChangeKeepsTheEntry() {
        val after = AppList.updateDisabledFlags(apps, { true }, suspend(false))
        assertSame(apps[0], after[0])
    }

    @Test
    fun aSuspendedOrPausedAppStillStarts() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val suspended = apps[0].copy(runtimeStatusFlags = AppEntry.FLAG_DISABLED_SUSPENDED)
        val paused = apps[0].copy(runtimeStatusFlags = AppEntry.FLAG_DISABLED_QUIET_USER)
        assertFalse(AppActions.handleDisabledItemClicked(suspended, context))
        assertFalse(AppActions.handleDisabledItemClicked(paused, context))
    }
}
