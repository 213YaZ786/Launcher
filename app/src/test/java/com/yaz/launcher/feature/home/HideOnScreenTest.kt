package com.yaz.launcher.feature.home

// Hide through the whole chain, as on the phone: Koin's appModule (the real model, repository, hidden set and settings), HomeScreen, the Apps
// panel opened by its edge tab, a long press on an app, Hide. Koin's startKoin with the app's module as in Koin's Android test documentation
// (https://insert-koin.io/docs/reference/koin-test/testing); apps listed by Robolectric's ShadowLauncherApps (https://github.com/robolectric/robolectric/blob/master/robolectric/src/test/java/org/robolectric/shadows/ShadowLauncherAppsTest.java);
// the screen driven with Compose's testing API (https://developer.android.com/develop/ui/compose/testing).
import android.app.Application
import android.content.Context
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.LauncherActivityInfo
import android.content.pm.LauncherApps
import android.os.Process
import android.os.UserHandle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.yaz.launcher.data.apps.LauncherModel
import com.yaz.launcher.data.settings.SettingsStore
import com.yaz.launcher.di.appModule
import com.yaz.launcher.ui.theme.AppTheme
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.Rule
import org.junit.runner.RunWith
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.GlobalContext
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadow.api.Shadow

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, sdk = [36])
@OptIn(ExperimentalTestApi::class)
class HideOnScreenTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @After
    fun tearDown() = stopKoin()

    // Android 16: LauncherActivityInfo(Context, LauncherActivityInfoInternal), hidden classes built by name through Robolectric's Shadow.newInstance.
    private fun activity(context: Context, pkg: String, label: String): LauncherActivityInfo {
        val info = ActivityInfo().apply {
            packageName = pkg
            name = "$pkg.Main"
            nonLocalizedLabel = label
            applicationInfo = ApplicationInfo().apply { packageName = pkg; nonLocalizedLabel = label }
        }
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

    @Test
    fun hideTakesTheAppOutOfTheAppsPanelOfTheRealScreen() {
        RuntimeEnvironment.setQualifiers("w411dp-h914dp-420dpi")
        val context = RuntimeEnvironment.getApplication()
        val shadow = shadowOf(context.getSystemService(LauncherApps::class.java))
        listOf("Alpha", "Bravo", "Charlie").forEach { shadow.addActivity(Process.myUserHandle(), activity(context, "com.example.${it.lowercase()}", it)) }
        startKoin {
            androidContext(context)
            modules(appModule)
        }
        val koin = GlobalContext.get()
        koin.get<SettingsStore>().update { it.copy(setupDone = true) }
        val model = koin.get<LauncherModel>()
        model.start(this)

        compose.activity.setContent {
            CompositionLocalProvider(LocalInspectionMode provides true) {
                AppTheme(darkTheme = false, dynamicColor = false) { HomeScreen() }
            }
        }
        compose.waitUntil(15_000) { model.state.value.apps.size == 3 }
        compose.waitForIdle()

        // The edge tab of the Apps panel (swipe left by default: the panel on the right).
        compose.onNodeWithContentDescription("Apps").performClick()
        compose.waitUntilAtLeastOneExists(hasText("Bravo"), 10_000)

        compose.onNodeWithText("Bravo").performTouchInput { longClick() }
        compose.waitUntilAtLeastOneExists(hasText("Hide"), 10_000)
        compose.onNodeWithText("Hide").performClick()

        compose.waitUntil(10_000) { model.state.value.hidden.size == 1 }
        assertTrue(model.state.value.apps.none { it.label == "Bravo" })
        compose.waitUntil(10_000) { compose.onAllNodes(hasText("Bravo")).fetchSemanticsNodes().isEmpty() }
        model.stop(this)
    }
}
