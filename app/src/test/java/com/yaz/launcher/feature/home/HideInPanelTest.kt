package com.yaz.launcher.feature.home

// Hide through the screen: a long press on an app in the Apps panel, a tap on Hide, and the app must leave the panel once the state no
// longer lists it. Compose's testing API (https://developer.android.com/develop/ui/compose/testing, createAndroidComposeRule, performTouchInput
// longClick, waitUntil), the state changed by the action as the model's flow changes it, the setup of the screenshot test of this app
// (LauncherScreenshotTest, from Now in Android's screenshot-testing).
import android.app.Application
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.yaz.launcher.data.settings.ThemeMode
import com.yaz.launcher.ui.theme.AppTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, sdk = [36])
@OptIn(ExperimentalTestApi::class)
class HideInPanelTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun hideTakesTheAppOutOfTheOpenAppsPanel() {
        RuntimeEnvironment.setQualifiers("w411dp-h914dp-420dpi")
        var state by mutableStateOf(Fixtures.full)
        val hidden = mutableListOf<String>()
        val actions = HomeActions(
            hide = { entry ->
                hidden += entry.packageName
                // What LauncherModel does with the set of hidden packages: the app moves from the panel's list to the hidden list.
                state = state.copy(
                    apps = state.apps.filter { it.packageName != entry.packageName },
                    recents = state.recents.filter { it.packageName != entry.packageName },
                    hidden = state.hidden + entry
                )
            }
        )
        compose.activity.setContent {
            CompositionLocalProvider(LocalInspectionMode provides true) {
                AppTheme(darkTheme = false, dynamicColor = false) {
                    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onBackground) {
                        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                            HomeContent(state, ThemeMode.SYSTEM, actions, initial = Panel.RIGHT)
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
        compose.onNodeWithText("Calculator").assertExists()

        compose.onNodeWithText("Calculator").performTouchInput { longClick() }
        compose.waitUntilAtLeastOneExists(hasText("Hide"), 5_000)
        compose.onNodeWithText("Hide").performClick()
        compose.waitUntil(5_000) { hidden.isNotEmpty() }

        assertEquals(listOf("com.example.app0"), hidden)
        compose.waitForIdle()
        compose.onNodeWithText("Calculator").assertDoesNotExist()
    }
}
