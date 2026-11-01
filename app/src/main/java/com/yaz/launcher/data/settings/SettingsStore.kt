package com.yaz.launcher.data.settings

// Ported from Store (YAZ/Store at HEAD, data/settings/SettingsStore.kt), itself from Files and Calculator (https://github.com/213YaZ786/Calculator, data/settings/SettingsStore.kt),
// cut to the settings the look and the update kit need: Store's background check and Wi-Fi switch belong to its installer.
import android.content.Context
import com.yaz.launcher.core.common.writeTextAtomically
import com.yaz.launcher.core.update.UpdateMode
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
enum class ThemeMode { SYSTEM, LIGHT, DARK }

/**
 * The shape of every app icon: the five the Pixel Launcher offers since Android 16 QPR2, named as androidx's MaterialShapes names them
 * (https://github.com/androidx/androidx/blob/androidx-main/compose/material3/material3/src/commonMain/kotlin/androidx/compose/material3/MaterialShapes.kt:
 * Circle, Square, Cookie4Sided, Cookie7Sided, Arch).
 */
@Serializable
enum class IconShape { CIRCLE, SQUARE, COOKIE_4, COOKIE_7, ARCH }

/**
 * An activity the user put in the head of the Apps panel: its component and its profile ([userSerial], UserManager's serial number,
 * 0 for the owner) with the label it had when it was added. The idea is sdex/ActivityManager's pinned shortcut (shortcut/ShortcutManager.kt,
 * https://github.com/sdex/ActivityManager/blob/main/app/src/main/java/com/sdex/activityrunner/shortcut/ShortcutManager.kt: a name and the
 * activity's intent), kept here in the settings instead of a launcher shortcut.
 */
@Serializable
data class PinnedActivity(val packageName: String, val className: String, val userSerial: Long, val label: String)

/**
 * What a swipe opens: the Apps panel, Recents, or nothing. One row per gesture with a list of actions is Kvaesitso's gesture settings
 * (https://github.com/MM2-0/Kvaesitso/blob/main/core/preferences/src/main/java/de/mm20/launcher2/preferences/ui/GestureSettings.kt and
 * app/ui/src/main/java/de/mm20/launcher2/ui/settings/gestures/GestureSettingsScreen.kt, GPL-3.0: the model and the idea only); unlike there, a
 * panel sits on one gesture only.
 */
@Serializable
enum class PanelKind { APPS, RECENTS, NONE }

/**
 * What a swipe down on the home screen does: opens the notification shade (the Pixel Launcher's gesture; Olauncher's swipe down does the same,
 * https://github.com/tanujnotes/Olauncher/blob/master/app/src/main/java/app/olauncher/helper/Utils.kt, expandNotificationDrawer) or nothing.
 */
@Serializable
enum class SwipeDown { NOTIFICATIONS, NOTHING }

/** Which calendar the header shows: Hijri, Gregorian or both (the Gregorian line first). */
@Serializable
enum class DateMode { HIJRI, GREGORIAN, BOTH }

/** The three swipes that open a panel: it comes in from the edge the swipe starts at (a swipe left brings it in from the right). */
enum class Swipe { LEFT, RIGHT, UP }

@Serializable
data class Settings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    /**
     * How strongly the screen is dimmed behind the panels, in percent (0 to 100). 54, SystemUI's TRANSPARENT_BOUNCER_SCRIM_ALPHA 0.54, the shade's
     * default scrim alpha with blur
     * (https://github.com/LineageOS/android_frameworks_base/blob/lineage-23.0/packages/SystemUI/src/com/android/systemui/statusbar/phone/ScrimController.java).
     */
    val panelOpacity: Int = 54,
    /** How visible the two tabs on the screen edges are, in percent (0 to 100); 30 is a fill of white at 6 % and a rim at 15 %. */
    val edgeTabs: Int = 30,
    /** The shape app icons are drawn in. */
    val iconShape: IconShape = IconShape.CIRCLE,
    /** App icons with a monochrome layer drawn in the theme's colours (Material You themed icons). */
    val themedIcons: Boolean = false,
    /** True black instead of dark grey in dark mode. */
    val pureBlack: Boolean = false,
    /** Zones and floating controls in liquid glass, over a soft light in the wallpaper's colours. */
    val glass: Boolean = true,
    /** Multiplier on every text style, one of the steps in ui.theme.TEXT_SCALES. */
    val textScale: Float = 1f,
    /** The setup guide was done or skipped: it is never shown again. */
    val setupDone: Boolean = false,
    /** What the app does when a newer release is out (the update kit); Store, when present, does it instead. */
    val updates: UpdateMode = UpdateMode.INSTALL,
    /** The activities pinned at the head of the Apps panel, in the order they were added. */
    val pinned: List<PinnedActivity> = emptyList(),
    /** What a swipe left, a swipe right and a swipe up open. */
    val swipeLeft: PanelKind = PanelKind.APPS,
    val swipeRight: PanelKind = PanelKind.NONE,
    val swipeUp: PanelKind = PanelKind.RECENTS,
    /** What a swipe down on the home screen does. */
    val swipeDown: SwipeDown = SwipeDown.NOTIFICATIONS,
    /** The status bar hidden on the home screen: the panels' header replaces it (the Settings and the guide screens show it again). */
    val hideStatusBar: Boolean = true,
    /** The date in the panels' header. */
    val dateMode: DateMode = DateMode.BOTH
) {
    // One action per gesture, read by gesture: Kvaesitso's GestureSettings (swipeLeft, swipeRight, swipeUp, each its own action)
    // https://github.com/MM2-0/Kvaesitso/blob/main/core/preferences/src/main/java/de/mm20/launcher2/preferences/ui/GestureSettings.kt
    /** What [swipe] opens. */
    fun panelOf(swipe: Swipe): PanelKind = when (swipe) {
        Swipe.LEFT -> swipeLeft
        Swipe.RIGHT -> swipeRight
        Swipe.UP -> swipeUp
    }

    // Kvaesitso's setSwipeLeft, setSwipeRight, setSwipeUp (same file) each write one gesture and leave the others; here, by the user's choice
    // (2026-10-31), a panel sits on one gesture only, so the gesture that had it is left on nothing.
    /** [swipe] opens [kind]. */
    fun withPanel(swipe: Swipe, kind: PanelKind): Settings {
        fun other(current: PanelKind) = if (kind != PanelKind.NONE && current == kind) PanelKind.NONE else current
        return when (swipe) {
            Swipe.LEFT -> copy(swipeLeft = kind, swipeRight = other(swipeRight), swipeUp = other(swipeUp))
            Swipe.RIGHT -> copy(swipeRight = kind, swipeLeft = other(swipeLeft), swipeUp = other(swipeUp))
            Swipe.UP -> copy(swipeUp = kind, swipeLeft = other(swipeLeft), swipeRight = other(swipeRight))
        }
    }
}

/** Small preference file, plain JSON written atomically, like the other apps. */
class SettingsStore(context: Context) {

    private val file = File(context.filesDir, "settings.json")
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    private val _settings = MutableStateFlow(load())
    val settings: StateFlow<Settings> = _settings.asStateFlow()

    val current: Settings get() = _settings.value

    // Store's SettingsStore.load.
    private fun load(): Settings {
        if (!file.exists()) return Settings()
        return runCatching { json.decodeFromString<Settings>(file.readText()) }
            .getOrDefault(Settings())
    }

    // Store's SettingsStore.update.
    fun update(transform: (Settings) -> Settings) {
        val updated = transform(_settings.value)
        if (updated == _settings.value) return
        _settings.value = updated
        runCatching { file.writeTextAtomically(json.encodeToString(updated)) }
    }
}
