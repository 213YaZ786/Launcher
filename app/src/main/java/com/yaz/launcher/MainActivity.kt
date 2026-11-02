package com.yaz.launcher

// Ported from Store (YAZ/Store at HEAD, MainActivity.kt), itself from Files and Calculator (https://github.com/213YaZ786/Calculator, MainActivity.kt).
// The model starts and stops with the activity's ON_START and ON_STOP (Lifecycle's observer,
// https://developer.android.com/topic/libraries/architecture/lifecycle#lc), so nothing listens while another app is in front.
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import androidx.core.content.ContextCompat
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.yaz.launcher.data.apps.LauncherModel
import com.yaz.launcher.navigation.LauncherApp
import com.yaz.launcher.ui.theme.AppSurface
import org.koin.android.ext.android.inject

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
        setContent { AppSurface { LauncherApp(homeSignal, homeAnimated, screenOffSignal) } }
    }

    // Launcher.mScreenOnListener = this::onScreenOnChanged, the tracker registered while the activity exists (onCreate to onDestroy).
    private var screenOnTracker: ScreenOnTracker? = null

    override fun onDestroy() {
        screenOnTracker?.close()
        screenOnTracker = null
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
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.action == Intent.ACTION_MAIN && intent.hasCategory(Intent.CATEGORY_HOME)) {
            homeAnimated = hasWindowFocus() && (intent.flags and Intent.FLAG_ACTIVITY_BROUGHT_TO_FRONT) != Intent.FLAG_ACTIVITY_BROUGHT_TO_FRONT
            homeSignal++
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
