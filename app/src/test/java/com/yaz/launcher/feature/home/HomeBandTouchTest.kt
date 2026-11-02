package com.yaz.launcher.feature.home

// A gesture refused at its down (it went down in the system's gesture band) must not keep the next one from opening a panel. Trebuchet decides once
// per gesture, at ACTION_DOWN, and never switches its detector in the middle of one (AbstractStateChangeTouchController.onControllerInterceptTouchEvent,
// https://github.com/LineageOS/android_packages_apps_Trebuchet/blob/lineage-22.2/src/com/android/launcher3/touch/AbstractStateChangeTouchController.java#onControllerInterceptTouchEvent).
// Compose's testing API (https://developer.android.com/develop/ui/compose/testing, createAndroidComposeRule, performTouchInput with swipe), the
// setup of HomeTouchTest in this folder. The band is the window insets the home reads (mandatory system gestures and navigation bars), given to
// the activity's decor view with ViewCompat.dispatchApplyWindowInsets, which Compose's WindowInsets listen to
// (https://developer.android.com/develop/ui/compose/system/insets).
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
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
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
class HomeBandTouchTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val launched = mutableListOf<String>()

    // The home with Apps on the swipe up and Recents on the swipe left, nothing open, and a gesture band of 120 px at the bottom and 60 px on each side.
    private fun show() {
        RuntimeEnvironment.setQualifiers("w411dp-h914dp-420dpi")
        val actions = HomeActions(launch = { entry -> launched += entry.packageName })
        compose.activity.setContent {
            CompositionLocalProvider(LocalInspectionMode provides true) {
                AppTheme(darkTheme = false, dynamicColor = false) {
                    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onBackground) {
                        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                            HomeContent(
                                Fixtures.full, ThemeMode.SYSTEM, actions, initial = Panel.NONE,
                                layout = PanelLayout(left = PanelKind.NONE, right = PanelKind.RECENTS, bottom = PanelKind.APPS)
                            )
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
        compose.runOnUiThread {
            val insets = WindowInsetsCompat.Builder()
                .setInsets(WindowInsetsCompat.Type.navigationBars(), Insets.of(0, 0, 0, BAND_BOTTOM))
                .setInsets(WindowInsetsCompat.Type.mandatorySystemGestures(), Insets.of(BAND_SIDE, 0, BAND_SIDE, BAND_BOTTOM))
                .build()
            ViewCompat.dispatchApplyWindowInsets(compose.activity.window.decorView, insets)
        }
        compose.waitForIdle()
    }

    private fun home() = compose.onAllNodes(isRoot())[0]

    private fun settle() {
        compose.mainClock.advanceTimeBy(5_000)
        compose.waitForIdle()
    }

    private fun swipeUpFromBottomBand() {
        home().performTouchInput { swipe(Offset(centerX, height - BAND_BOTTOM / 2f), Offset(centerX, height * 0.15f), 300) }
        settle()
    }

    private fun swipeUpFromMiddle() {
        home().performTouchInput { swipe(Offset(centerX, height * 0.6f), Offset(centerX, height * 0.15f), 300) }
        settle()
    }

    private fun swipeLeftFromSideBand() {
        home().performTouchInput { swipe(Offset(width - BAND_SIDE / 2f, centerY), Offset(width * 0.1f, centerY), 300) }
        settle()
    }

    private fun swipeLeftFromMiddle() {
        home().performTouchInput { swipe(Offset(width * 0.8f, centerY), Offset(width * 0.1f, centerY), 300) }
        settle()
    }

    @Test
    fun aSwipeUpFromTheMiddleOpensTheBottomPanelRightAfterOneFromTheBottomBand() {
        show()
        swipeUpFromBottomBand()
        // The band refused the first gesture: nothing opened (the closed panel is hidden from accessibility).
        compose.onNodeWithText("Calculator").assertDoesNotExist()
        swipeUpFromMiddle()
        compose.onNodeWithText("Calculator").performClick()
        compose.waitUntil(5_000) { launched.isNotEmpty() }
        assertEquals(listOf("com.example.app0"), launched)
    }

    @Test
    fun aSwipeLeftFromTheMiddleOpensTheRightPanelRightAfterOneFromTheSideBand() {
        show()
        val recent = Fixtures.full.recents.first().label
        swipeLeftFromSideBand()
        compose.onNodeWithText(recent).assertDoesNotExist()
        swipeLeftFromMiddle()
        compose.onNodeWithText(recent).assertExists()
    }

    private companion object {
        const val BAND_BOTTOM = 120
        const val BAND_SIDE = 60
    }
}
