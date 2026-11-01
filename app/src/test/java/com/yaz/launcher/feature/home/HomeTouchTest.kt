package com.yaz.launcher.feature.home

// Touches on the home screen: a tap on an app of the Apps panel at the bottom starts it, and a long press on the wallpaper opens the Wallpaper and
// Settings menu, whatever the panels did before. Compose's testing API (https://developer.android.com/develop/ui/compose/testing,
// createAndroidComposeRule, performTouchInput with swipe and longClick, waitUntil), the setup of HideInPanelTest in this folder (itself from the
// screenshot test of this app, Now in Android's screenshot-testing). The behaviour expected is Trebuchet's: taps and long presses always reach
// their target, a drag starts only after the touch slop (AllAppsSwipeController, https://github.com/LineageOS/android_packages_apps_Trebuchet/blob/lineage-22.2/src/com/android/launcher3/touch/AllAppsSwipeController.java).
import android.app.Application
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.yaz.launcher.data.settings.PanelKind
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
class HomeTouchTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val launched = mutableListOf<String>()

    // The home with Apps on the swipe up (the bottom panel, full height); [initial] is the panel open at the start.
    private fun show(initial: Panel) {
        RuntimeEnvironment.setQualifiers("w411dp-h914dp-420dpi")
        val actions = HomeActions(launch = { entry -> launched += entry.packageName })
        compose.activity.setContent {
            CompositionLocalProvider(LocalInspectionMode provides true) {
                AppTheme(darkTheme = false, dynamicColor = false) {
                    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onBackground) {
                        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                            HomeContent(
                                Fixtures.full, ThemeMode.SYSTEM, actions, initial = initial,
                                layout = PanelLayout(left = PanelKind.NONE, right = PanelKind.RECENTS, bottom = PanelKind.APPS)
                            )
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    // The activity's own root: the edge tabs are windows of their own, with a root each (Compose testing, onAllNodes(isRoot()),
    // https://developer.android.com/develop/ui/compose/testing/common-patterns#multiple-windows).
    private fun home() = compose.onAllNodes(isRoot())[0]

    private fun swipeUpFromMiddle() {
        home().performTouchInput {
            swipe(Offset(centerX, height * 0.6f), Offset(centerX, height * 0.15f), 300)
        }
    }

    private fun settle() {
        compose.mainClock.advanceTimeBy(5_000)
        compose.waitForIdle()
    }

    // Back closes the open panel (the scrim and the tab do the same).
    private fun backAndSettle() {
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        settle()
    }

    private fun assertAppStarts() {
        compose.onNodeWithText("Calculator").performClick()
        compose.waitUntil(5_000) { launched.isNotEmpty() }
        assertEquals(listOf("com.example.app0"), launched)
    }

    private fun assertMenuOpensOnLongPress() {
        home().performTouchInput { longClick(Offset(centerX, height * 0.4f)) }
        compose.waitUntilAtLeastOneExists(hasText("Wallpaper"), 5_000)
        compose.onNodeWithText("Settings").assertExists()
    }

    @Test
    fun tapOnAnAppOfTheBottomPanelStartsItWhenOpenAtTheStart() {
        show(Panel.BOTTOM)
        assertAppStarts()
    }

    @Test
    fun tapOnAnAppOfTheBottomPanelStartsItAfterASwipeUp() {
        show(Panel.NONE)
        swipeUpFromMiddle()
        settle()
        assertAppStarts()
    }

    @Test
    fun tapOnAnAppStartsItRightAfterTheSwipeUp() {
        // No wait for the spring to settle: a finger lands on the panel while it still moves.
        show(Panel.NONE)
        swipeUpFromMiddle()
        compose.waitForIdle()
        assertAppStarts()
    }

    @Test
    fun longPressOnTheWallpaperOpensTheMenu() {
        show(Panel.NONE)
        assertMenuOpensOnLongPress()
    }

    @Test
    fun longPressOnTheWallpaperOpensTheMenuAfterAPanelWasClosed() {
        show(Panel.BOTTOM)
        backAndSettle()
        assertMenuOpensOnLongPress()
    }

    @Test
    fun longPressOnTheWallpaperOpensTheMenuAfterASwipeUpAndBack() {
        show(Panel.NONE)
        swipeUpFromMiddle()
        settle()
        backAndSettle()
        assertMenuOpensOnLongPress()
    }
}
