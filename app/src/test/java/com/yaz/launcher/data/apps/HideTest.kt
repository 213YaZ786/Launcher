package com.yaz.launcher.data.apps

// Hide, end to end on the JVM: the model with its real repository (Robolectric's ShadowLauncherApps lists the apps, as Robolectric's own
// ShadowLauncherAppsTest does, https://github.com/robolectric/robolectric/blob/master/robolectric/src/test/java/org/robolectric/shadows/ShadowLauncherAppsTest.java),
// the hidden set in a real file (JUnit's TemporaryFolder, https://junit.org/junit4/javadoc/latest/org/junit/rules/TemporaryFolder.html).
import android.app.Application
import android.content.pm.ActivityInfo
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.LauncherActivityInfo
import android.content.pm.LauncherApps
import android.os.Process
import android.os.UserHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadow.api.Shadow

@RunWith(AndroidJUnit4::class)
@Config(application = Application::class, sdk = [36])
class HideTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @After
    fun tearDown() = scope.cancel()

    // Robolectric's ShadowLauncherAppsTest builds a LauncherActivityInfo with Shadow.newInstance on its hidden constructor.
    private fun activity(context: Context, pkg: String): LauncherActivityInfo {
        val info = ActivityInfo().apply {
            packageName = pkg
            name = "$pkg.Main"
            nonLocalizedLabel = pkg
            applicationInfo = ApplicationInfo().apply { packageName = pkg; nonLocalizedLabel = pkg }
        }
        // Android 16: LauncherActivityInfo(Context, LauncherActivityInfoInternal), a hidden class (so by name), itself made of the
        // ActivityInfo, an IncrementalStatesInfo, the user and the multi-instance flag.
        val statesClass = Class.forName("android.content.pm.IncrementalStatesInfo")
        val internalClass = Class.forName("android.content.pm.LauncherActivityInfoInternal")
        val states = Shadow.newInstance(
            statesClass,
            arrayOf<Class<*>>(java.lang.Boolean.TYPE, java.lang.Float.TYPE, java.lang.Long.TYPE),
            arrayOf<Any?>(false, 1f, 0L)
        )
        val internal = Shadow.newInstance(
            internalClass,
            arrayOf<Class<*>>(ActivityInfo::class.java, statesClass, UserHandle::class.java, java.lang.Boolean.TYPE),
            arrayOf<Any?>(info, states, Process.myUserHandle(), false)
        )
        return Shadow.newInstance(
            LauncherActivityInfo::class.java,
            arrayOf<Class<*>>(Context::class.java, internalClass),
            arrayOf<Any?>(context, internal)
        )
    }

    // Waits for a state that the model reaches on its own threads.
    private fun await(what: String, check: () -> Boolean) {
        val end = System.currentTimeMillis() + 15_000
        while (System.currentTimeMillis() < end) {
            if (check()) return
            Thread.sleep(20)
        }
        throw AssertionError("Timed out: $what")
    }

    @Test
    fun aHiddenAppLeavesTheAppsAndEntersTheHiddenList() {
        val context = RuntimeEnvironment.getApplication()
        val shadow = shadowOf(context.getSystemService(LauncherApps::class.java))
        listOf("com.example.a", "com.example.b", "com.example.c").forEach { shadow.addActivity(Process.myUserHandle(), activity(context, it)) }
        val file = File(folder.root, "hidden-apps.json")
        val hiddenApps = HiddenApps(file)
        val model = LauncherModel(
            context, AppsRepository(context, scope), RemovedRecents(File(folder.root, "removed.json")), hiddenApps, scope
        )
        model.start(this)
        await("apps loaded") { model.state.value.apps.map { it.packageName }.toSet().containsAll(listOf("com.example.a", "com.example.b", "com.example.c")) }

        model.hide("com.example.b")

        await("b hidden") { "com.example.b" !in model.state.value.apps.map { it.packageName } }
        assertEquals(listOf("com.example.b"), model.state.value.hidden.map { it.packageName })
        assertTrue(file.readText().contains("com.example.b"))

        model.unhide("com.example.b")
        await("b back") { "com.example.b" in model.state.value.apps.map { it.packageName } }
        assertTrue(model.state.value.hidden.isEmpty())
        model.stop(this)
    }

    // Source: Trebuchet's per-instance callbacks, LauncherModel.removeCallbacks detaches only that Launcher
    // (https://github.com/LineageOS/android_packages_apps_Trebuchet/blob/lineage-22.2/src/com/android/launcher3/LauncherModel.kt);
    // same setup as the test above (Robolectric's ShadowLauncherAppsTest,
    // https://github.com/robolectric/robolectric/blob/master/robolectric/src/test/java/org/robolectric/shadows/ShadowLauncherAppsTest.java).
    // Android's events log when the home activity is replaced: the new instance starts, then the old one stops.
    @Test
    fun theOldScreenStoppingAfterTheNewOneStartedKeepsTheModelRunning() {
        val context = RuntimeEnvironment.getApplication()
        val shadow = shadowOf(context.getSystemService(LauncherApps::class.java))
        listOf("com.example.a", "com.example.b").forEach { shadow.addActivity(Process.myUserHandle(), activity(context, it)) }
        val model = LauncherModel(
            context, AppsRepository(context, scope), RemovedRecents(File(folder.root, "removed.json")), HiddenApps(File(folder.root, "hidden-apps.json")), scope
        )
        val old = Any()
        val new = Any()
        model.start(old)
        model.start(new)
        model.stop(old)
        await("apps loaded") { model.state.value.apps.map { it.packageName }.toSet().containsAll(listOf("com.example.a", "com.example.b")) }

        model.hide("com.example.b")
        await("b hidden") { "com.example.b" !in model.state.value.apps.map { it.packageName } }

        // The last screen going stops the model: a later hide no longer changes the state.
        model.stop(new)
        Thread.sleep(300)
        model.hide("com.example.a")
        Thread.sleep(500)
        assertTrue(model.state.value.apps.any { it.packageName == "com.example.a" })
    }

    @Test
    fun theFilterDropsWhatIsHidden() {
        assertFalse(HiddenAppsFilter.shouldShowApp(setOf("a"), "a"))
        assertTrue(HiddenAppsFilter.shouldShowApp(setOf("a"), "b"))
    }
}
