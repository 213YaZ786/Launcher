package com.yaz.launcher

// The screens as drawn on a phone and a tablet, by day and by night, compared with the pictures kept in src/test/screenshots: a block moved
// or cut in two fails the build. Taken from Store's StoreScreenshotTest (https://github.com/213YaZ786/Store/blob/main/app/src/test/java/com/yaz/store/StoreScreenshotTest.kt),
// itself SMS's PersonPageScreenshotTest and Now in Android's captureForDevice
// (https://github.com/android/nowinandroid, core/screenshot-testing/.../ScreenshotHelper.kt): the screen's size set through Robolectric's
// qualifiers, inspection mode on, one picture each (Roborazzi, https://github.com/takahirom/roborazzi).
// The home is the wallpaper: a plain gradient stands for it here.
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.captureRoboImage
import com.yaz.launcher.data.settings.PanelKind
import com.yaz.launcher.data.settings.ThemeMode
import com.yaz.launcher.feature.home.PanelLayout
import com.yaz.launcher.feature.home.Fixtures
import com.yaz.launcher.feature.home.HomeActions
import com.yaz.launcher.feature.home.HomeContent
import com.yaz.launcher.feature.home.Panel
import com.yaz.launcher.feature.hidden.HiddenAppsContent
import com.yaz.launcher.feature.setup.GuideContent
import com.yaz.launcher.feature.setup.guideTiles
import com.yaz.launcher.ui.theme.AppTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
// A plain Application: the app's own starts Koin, which a second test would start again.
@Config(application = android.app.Application::class, sdk = [36])
class LauncherScreenshotTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    // Now in Android's DefaultTestDevices, phone and tablet.
    private val devices = listOf(
        "phone" to "w411dp-h914dp-420dpi",
        "tablet" to "w1280dp-h800dp-320dpi"
    )

    private val options = RoborazziOptions(
        // Pixel-perfect, as Now in Android's DefaultRoborazziOptions: a word changed is a change.
        compareOptions = RoborazziOptions.CompareOptions(changeThreshold = 0f),
        recordOptions = RoborazziOptions.RecordOptions(resizeScale = 0.5)
    )

    // As captureForDevice: the qualifiers first, then the content set on the activity again.
    // [rtl] draws the screen right to left, on the phone by day only (one picture): the qualifiers ar and ldrtl set the configuration's locale and layout
    // direction, which the windows (the edge tabs' dialogs) and Compose's LocalLayoutDirection both follow, as on a phone set to Arabic.
    private fun capture(name: String, rtl: Boolean = false, body: @Composable () -> Unit) {
        (if (rtl) devices.take(1) else devices).forEach { (device, qualifiers) ->
            RuntimeEnvironment.setQualifiers(if (rtl) "ar-ldrtl-$qualifiers" else qualifiers)
            (if (rtl) listOf(false) else listOf(false, true)).forEach { night ->
                compose.activity.setContent {
                    CompositionLocalProvider(LocalInspectionMode provides true) {
                        AppTheme(darkTheme = night, dynamicColor = false) {
                            // As AppSurface gives the app's windows: text takes the theme's colour, or it falls to black at night.
                            CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onBackground) {
                                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) { body() }
                            }
                        }
                    }
                }
                compose.waitForIdle()
                compose.onRoot().captureRoboImage("src/test/screenshots/${name}_${device}_${if (night) "dark" else "light"}.png", roborazziOptions = options)
            }
        }
    }

    // A stand-in for the wallpaper behind the panels.
    @Composable
    private fun Wallpaper(content: @Composable () -> Unit) {
        Box(
            Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF3B5BA5), Color(0xFFB5651D)))),
            content = { content() }
        )
    }

    @Test
    fun home() = capture("home") {
        Wallpaper { HomeContent(Fixtures.full, ThemeMode.SYSTEM, HomeActions()) }
    }

    // Right to left (Arabic): the panels and the tabs stay on the edge their swipe comes from, the text and the apps read right to left. Robolectric
    // places a dialog window's Gravity.START on the left whatever the configuration, so the tab (a dialog window) shows on the left here; on a phone
    // the window manager reads the configuration (WindowState.isRtl) and puts it on the right. Taken as the
    // screens above are (Store's StoreScreenshotTest, https://github.com/213YaZ786/Store/blob/main/app/src/test/java/com/yaz/store/StoreScreenshotTest.kt).
    @Test
    fun homeRightToLeft() = capture("home_rtl", rtl = true) {
        Wallpaper { HomeContent(Fixtures.full, ThemeMode.SYSTEM, HomeActions()) }
    }

    // Same source: Store's StoreScreenshotTest (https://github.com/213YaZ786/Store/blob/main/app/src/test/java/com/yaz/store/StoreScreenshotTest.kt).
    @Test
    fun appsPanelRightToLeft() = capture("apps_panel_rtl", rtl = true) {
        Wallpaper { HomeContent(Fixtures.full, ThemeMode.SYSTEM, HomeActions(), initial = Panel.RIGHT) }
    }

    @Test
    fun appsPanel() = capture("apps_panel") {
        Wallpaper { HomeContent(Fixtures.full, ThemeMode.SYSTEM, HomeActions(), initial = Panel.RIGHT) }
    }

    // The work profile running and the private space locked, then the work profile paused and the private space unlocked; one picture each, taken
    // as the screens above are (Store's StoreScreenshotTest, https://github.com/213YaZ786/Store/blob/main/app/src/test/java/com/yaz/store/StoreScreenshotTest.kt).
    @Test
    fun appsPanelWithProfiles() = capture("apps_panel_profiles") {
        Wallpaper { HomeContent(Fixtures.withProfiles, ThemeMode.SYSTEM, HomeActions(), initial = Panel.RIGHT) }
    }

    @Test
    fun appsPanelWithPausedWork() = capture("apps_panel_paused_work") {
        Wallpaper { HomeContent(Fixtures.withPausedWork, ThemeMode.SYSTEM, HomeActions(), initial = Panel.RIGHT) }
    }

    // Few apps: each profile starts a new column at its first row; the private space locked shows its header only, then unlocked with its apps. Taken as
    // the tests above are (Store's StoreScreenshotTest, https://github.com/213YaZ786/Store/blob/main/app/src/test/java/com/yaz/store/StoreScreenshotTest.kt).
    @Test
    fun appsPanelWithLockedPrivate() = capture("apps_panel_locked_private") {
        Wallpaper { HomeContent(Fixtures.withLockedPrivate, ThemeMode.SYSTEM, HomeActions(), initial = Panel.RIGHT) }
    }

    @Test
    fun appsPanelWithUnlockedPrivate() = capture("apps_panel_unlocked_private") {
        Wallpaper { HomeContent(Fixtures.withUnlockedPrivate, ThemeMode.SYSTEM, HomeActions(), initial = Panel.RIGHT) }
    }

    // Apps given to the swipe up (a vertical grid, screen wide) and to the swipe right (the left panel, the right one mirrored); taken as the
    // tests above are (Store's StoreScreenshotTest, https://github.com/213YaZ786/Store/blob/main/app/src/test/java/com/yaz/store/StoreScreenshotTest.kt).
    @Test
    fun appsPanelFromTheBottom() = capture("apps_panel_bottom") {
        Wallpaper {
            HomeContent(Fixtures.withProfiles, ThemeMode.SYSTEM, HomeActions(), initial = Panel.BOTTOM, layout = PanelLayout(bottom = PanelKind.APPS))
        }
    }

    // Same source: Store's StoreScreenshotTest (https://github.com/213YaZ786/Store/blob/main/app/src/test/java/com/yaz/store/StoreScreenshotTest.kt).
    @Test
    fun appsPanelFromTheLeft() = capture("apps_panel_left") {
        Wallpaper {
            HomeContent(
                Fixtures.withProfiles, ThemeMode.SYSTEM, HomeActions(), initial = Panel.LEFT,
                layout = PanelLayout(left = PanelKind.APPS, right = PanelKind.NONE)
            )
        }
    }

    @Test
    fun recentsPanel() = capture("recents_panel") {
        Wallpaper { HomeContent(Fixtures.full, ThemeMode.SYSTEM, HomeActions(), initial = Panel.BOTTOM) }
    }

    @Test
    fun recentsWithoutUsageAccess() = capture("recents_no_usage") {
        Wallpaper { HomeContent(Fixtures.noUsage, ThemeMode.SYSTEM, HomeActions(), initial = Panel.BOTTOM) }
    }

    // Two hidden apps, each with its unhide toggle; taken as the tests above are (Store's StoreScreenshotTest,
    // https://github.com/213YaZ786/Store/blob/main/app/src/test/java/com/yaz/store/StoreScreenshotTest.kt).
    @Test
    fun hiddenApps() = capture("hidden_apps") {
        HiddenAppsContent(Fixtures.withHidden, onBack = {}, onLaunch = {}, onUnhide = {})
    }

    @Test
    fun guideStart() = capture("guide_start") {
        GuideContent(guideTiles(home = false, usage = false, dots = false))
    }

    // The home app chosen, the offered tiles still open: Finish is open and the count says two left. Taken as the tests above are (Store's StoreScreenshotTest,
    // https://github.com/213YaZ786/Store/blob/main/app/src/test/java/com/yaz/store/StoreScreenshotTest.kt).
    @Test
    fun guideHome() = capture("guide_home") {
        GuideContent(guideTiles(home = true, usage = false, dots = false))
    }

    @Test
    fun guideDone() = capture("guide_done") {
        GuideContent(guideTiles(home = true, usage = true, dots = true))
    }
}
