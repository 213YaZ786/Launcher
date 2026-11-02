package com.yaz.launcher

// Ported from Store (YAZ/Store at HEAD, MainActivity.kt), itself from Files and Calculator (https://github.com/213YaZ786/Calculator, MainActivity.kt).
// The model starts and stops with the activity's ON_START and ON_STOP (Lifecycle's observer,
// https://developer.android.com/topic/libraries/architecture/lifecycle#lc), so nothing listens while another app is in front.
import android.app.WallpaperColors
import android.app.WallpaperManager
import android.app.WallpaperManager.FLAG_SYSTEM
import android.app.WallpaperManager.OnColorsChangedListener
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.Window
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import java.util.concurrent.Executors
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.yaz.launcher.data.apps.LauncherModel
import com.yaz.launcher.feature.home.AdapterHolder
import com.yaz.launcher.navigation.LauncherApp
import com.yaz.launcher.ui.theme.AppSurface
import org.koin.android.ext.android.inject

/** Intent.ACTION_SHOW_WORK_APPS, hidden from the SDK: the intent the work profile's launcher shortcut sends the home app. */
private const val ACTION_SHOW_WORK_APPS = "android.intent.action.SHOW_WORK_APPS"

/** Launcher, the home screen: the app's one window. */
class MainActivity : ComponentActivity() {

    private val model: LauncherModel by inject()

    // How many times Home was pressed while the app was running; a state, so the screens that read it react (Compose state,
    // https://developer.android.com/develop/ui/compose/state).
    private var homeSignal by mutableIntStateOf(0)

    // How many times the screen went off while the app existed (ScreenOnTracker); only the home screen reads it.
    private var screenOffSignal by mutableIntStateOf(0)

    // Whether the screens may animate the last Home press: Trebuchet's alreadyOnHome, false when the system brings the home to the front.
    private var homeAnimated by mutableStateOf(true)

    // How many times the apps were asked for by an intent (ACTION_ALL_APPS, ACTION_SHOW_WORK_APPS), and the ask not yet answered by the home
    // screen: the count makes every route above Home leave, the request opens the panel once Home is in front (the home screen clears it).
    private var appsSignal by mutableIntStateOf(0)
    private var appsTab by mutableStateOf<Int?>(null)

    // Store's MainActivity.onCreate.
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        lifecycle.addObserver(LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> model.start(this)
                Lifecycle.Event.ON_STOP -> model.stop(this)
                else -> Unit
            }
        })
        screenOnTracker = ScreenOnTracker(this, ::onScreenOnChanged)
        // Launcher.getSystemUiController and Themes.getActivityThemeRes: the wallpaper's hints and the controller of the bars' icons, which the
        // home screen reads through composition locals.
        val hintsOfWallpaper = WallpaperColorHints(this)
        wallpaperColorHints = hintsOfWallpaper
        val systemUiController = SystemUiController(window, window.decorView)
        setContent {
            CompositionLocalProvider(LocalWallpaperColorHints provides hintsOfWallpaper.hints, LocalSystemUiController provides systemUiController) {
                AppSurface { LauncherApp(homeSignal, homeAnimated, screenOffSignal, appsSignal, appsTab) { appsTab = null } }
            }
        }
    }

    // Launcher.mScreenOnListener = this::onScreenOnChanged, the tracker registered while the activity exists (onCreate to onDestroy).
    private var screenOnTracker: ScreenOnTracker? = null

    // WallpaperColorHints.get(context), closed with the activity (its listener lives from onCreate to onDestroy).
    private var wallpaperColorHints: WallpaperColorHints? = null

    override fun onDestroy() {
        screenOnTracker?.close()
        screenOnTracker = null
        wallpaperColorHints?.close()
        wallpaperColorHints = null
        super.onDestroy()
    }

    // Launcher.onScreenOnChanged: "Reset AllApps to its initial state": when the screen goes off, goToState(NORMAL) on the Launcher activity only
    // (its Settings is another activity, untouched). The panels close at once, nobody sees them close, and every open menu closes: a count of its
    // own, read by HomeScreen only (LauncherApp passes it through and never reads it, so Settings and the other routes stay)
    // (https://github.com/LineageOS/android_packages_apps_Trebuchet/blob/lineage-22.2/src/com/android/launcher3/Launcher.java#L2144-L2153).
    private fun onScreenOnChanged(isOn: Boolean) {
        if (!isOn) {
            screenOffSignal++
        }
    }

    // The Home button or gesture brings an ACTION_MAIN + CATEGORY_HOME intent to the running home (singleTask). Trebuchet's Launcher.onNewIntent
    // does the same on ACTION_MAIN: it closes every open floating view and goes back to the NORMAL state, the bare home
    // (https://github.com/LineageOS/android_packages_apps_Trebuchet/blob/lineage-22.2/src/com/android/launcher3/Launcher.java). Here the count
    // goes up and the screens read it: LauncherApp leaves Settings and the other routes, HomeScreen closes its panel.
    // Trebuchet's rule in the same method: alreadyOnHome = mHasFocus && (intent.getFlags() & FLAG_ACTIVITY_BROUGHT_TO_FRONT) != FLAG_ACTIVITY_BROUGHT_TO_FRONT;
    // the state change and the closing views animate only then (goToState(NORMAL, alreadyOnHome)), otherwise they are instant, the system
    // animating the way home itself. mHasFocus is the window's focus, hasWindowFocus() here.
    // The same method takes two more intents: ACTION_ALL_APPS shows the apps (showAllAppsFromIntent(alreadyOnHome)) and ACTION_SHOW_WORK_APPS
    // shows them on the work tab (showAllAppsWithSelectedTabFromIntent(alreadyOnHome, WORK)); both close what is open first and animate only
    // when already on home, like the Home press. Here the tab is the work profile's tile, scrolled to by the Apps panel.
    // (https://github.com/LineageOS/android_packages_apps_Trebuchet/blob/lineage-22.2/src/com/android/launcher3/Launcher.java#showAllAppsFromIntent)
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        val alreadyOnHome = hasWindowFocus() && (intent.flags and Intent.FLAG_ACTIVITY_BROUGHT_TO_FRONT) != Intent.FLAG_ACTIVITY_BROUGHT_TO_FRONT
        if (intent.action == Intent.ACTION_MAIN && intent.hasCategory(Intent.CATEGORY_HOME)) {
            homeAnimated = alreadyOnHome
            homeSignal++
        } else if (intent.action == Intent.ACTION_ALL_APPS) {
            homeAnimated = alreadyOnHome
            appsTab = AdapterHolder.MAIN
            appsSignal++
        } else if (intent.action == ACTION_SHOW_WORK_APPS) {
            homeAnimated = alreadyOnHome
            appsTab = AdapterHolder.WORK
            appsSignal++
        }
    }
}

/**
 * ScreenOnTracker: tells when the screen goes on or off, with a runtime receiver registered for ACTION_SCREEN_ON and ACTION_SCREEN_OFF
 * (https://github.com/LineageOS/android_packages_apps_Trebuchet/blob/lineage-22.2/src/com/android/launcher3/util/ScreenOnTracker.java#L52-L95).
 * Differences: it lives with the activity (registered in the constructor, [close] in onDestroy), where Trebuchet's lives with the process; the
 * receiver runs on the main thread; ACTION_USER_PRESENT is left out (nothing here needs it); one listener, a function.
 */
private class ScreenOnTracker(private val mContext: Context, private val listener: (Boolean) -> Unit) {

    private var mIsScreenOn = true

    private val mReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = this@ScreenOnTracker.onReceive(intent)
    }

    init {
        // Assume that the screen is on to begin with. System broadcasts: a receiver that is not exported still gets them.
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
        }
        ContextCompat.registerReceiver(mContext, mReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    fun close() {
        // unregisterReceiverSafely
        runCatching { mContext.unregisterReceiver(mReceiver) }
    }

    fun onReceive(intent: Intent) {
        val action = intent.action
        if (Intent.ACTION_SCREEN_ON == action) {
            mIsScreenOn = true
            dispatchScreenOnChanged()
        } else if (Intent.ACTION_SCREEN_OFF == action) {
            mIsScreenOn = false
            dispatchScreenOnChanged()
        }
    }

    private fun dispatchScreenOnChanged() = listener(mIsScreenOn)
}

/** The system wallpaper's colour hints (WallpaperColors.getColorHints), read by the home screen to choose its bars' icons. */
val LocalWallpaperColorHints = compositionLocalOf { 0 }

/** The controller of the bars' icons of the activity's window, null where there is no activity (previews, tests). */
val LocalSystemUiController = staticCompositionLocalOf<SystemUiController?> { null }

/**
 * WallpaperColorHints: caches the system's wallpaper color hints (Apache-2.0)
 * https://github.com/LineageOS/android_packages_apps_Trebuchet/blob/lineage-22.2/src/com/android/launcher3/util/WallpaperColorHints.kt#hints
 * The hints are read once, then follow WallpaperManager's colors listener (FLAG_SYSTEM only), registered on a helper thread with the main
 * thread's handler and removed on [close]. Differences: [hints] is a Compose state, which the screens read, where the original keeps a list
 * of OnColorHintListener; UI_HELPER_EXECUTOR is a single thread executor of the class; MainThreadInitializedObject is the activity's field.
 */
class WallpaperColorHints(private val context: Context) : AutoCloseable {
    var hints: Int by mutableIntStateOf(0)
        private set

    private val wallpaperManager
        get() = context.getSystemService(WallpaperManager::class.java)!!

    private val uiHelperExecutor = Executors.newSingleThreadExecutor()
    private val onColorsChangedListener = OnColorsChangedListener { colors, which -> onColorsChanged(colors, which) }

    init {
        hints = wallpaperManager.getWallpaperColors(FLAG_SYSTEM)?.colorHints ?: 0
        uiHelperExecutor.execute {
            wallpaperManager.addOnColorsChangedListener(onColorsChangedListener, Handler(Looper.getMainLooper()))
        }
    }

    // @MainThread
    private fun onColorsChanged(colors: WallpaperColors?, which: Int) {
        if ((which and FLAG_SYSTEM) != 0) {
            val newHints = colors?.colorHints ?: 0
            if (newHints != hints) {
                hints = newHints
            }
        }
    }

    override fun close() {
        uiHelperExecutor.execute {
            wallpaperManager.removeOnColorsChangedListener(onColorsChangedListener)
        }
        uiHelperExecutor.shutdown()
    }
}

/**
 * Themes.getActivityThemeRes(Context, int): the wallpaper decides "dark text": supportsDarkText = (wallpaperColorHints and
 * HINT_SUPPORTS_DARK_TEXT) != 0. The original picks one of six styles from it, the dark theme and HINT_SUPPORTS_DARK_THEME; here the app's
 * theme is its own (AppSurface), so only the dark text is kept: it chooses the icons of the bars over the bare home.
 * https://github.com/LineageOS/android_packages_apps_Trebuchet/blob/lineage-22.2/src/com/android/launcher3/util/Themes.java#getActivityThemeRes
 */
fun supportsDarkText(wallpaperColorHints: Int): Boolean = (wallpaperColorHints and WallpaperColors.HINT_SUPPORTS_DARK_TEXT) != 0

/**
 * SystemUiController: the icons of the status and navigation bars, set by states in increasing order of priority (Apache-2.0)
 * https://github.com/LineageOS/android_packages_apps_Trebuchet/blob/lineage-22.2/src/com/android/launcher3/util/SystemUiController.java#updateUiState
 * Difference: the flags are applied with WindowInsetsControllerCompat.isAppearanceLightNavigationBars and isAppearanceLightStatusBars
 * (View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR and SYSTEM_UI_FLAG_LIGHT_STATUS_BAR, deprecated, are what the original sets on the view).
 * FLAG_LIGHT_* means a light bar, so dark icons.
 */
class SystemUiController(private val window: Window, private val view: View) {

    private val mStates = IntArray(5)

    fun updateUiState(uiState: Int, isLight: Boolean) {
        updateUiState(uiState, if (isLight) (FLAG_LIGHT_NAV or FLAG_LIGHT_STATUS) else (FLAG_DARK_NAV or FLAG_DARK_STATUS))
    }

    fun updateUiState(uiState: Int, flags: Int) {
        if (mStates[uiState] == flags) {
            return
        }
        mStates[uiState] = flags

        val controller = WindowCompat.getInsetsController(window, view)
        var lightNav = controller.isAppearanceLightNavigationBars
        var lightStatus = controller.isAppearanceLightStatusBars
        // Apply the state flags in priority order
        for (stateFlag in mStates) {
            if ((stateFlag and FLAG_LIGHT_NAV) != 0) {
                lightNav = true
            } else if ((stateFlag and FLAG_DARK_NAV) != 0) {
                lightNav = false
            }

            if ((stateFlag and FLAG_LIGHT_STATUS) != 0) {
                lightStatus = true
            } else if ((stateFlag and FLAG_DARK_STATUS) != 0) {
                lightStatus = false
            }
        }
        if (lightNav != controller.isAppearanceLightNavigationBars) controller.isAppearanceLightNavigationBars = lightNav
        if (lightStatus != controller.isAppearanceLightStatusBars) controller.isAppearanceLightStatusBars = lightStatus
    }

    companion object {
        // Various UI states in increasing order of priority
        const val UI_STATE_BASE_WINDOW = 0
        const val UI_STATE_SCRIM_VIEW = 1
        const val UI_STATE_WIDGET_BOTTOM_SHEET = 2
        const val UI_STATE_FULLSCREEN_TASK = 3
        const val UI_STATE_ALL_APPS = 4

        const val FLAG_LIGHT_NAV = 1 shl 0
        const val FLAG_DARK_NAV = 1 shl 1
        const val FLAG_LIGHT_STATUS = 1 shl 2
        const val FLAG_DARK_STATUS = 1 shl 3
    }
}
