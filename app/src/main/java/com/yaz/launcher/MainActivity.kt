package com.yaz.launcher

// Ported from Store (YAZ/Store at HEAD, MainActivity.kt), itself from Files and Calculator (https://github.com/213YaZ786/Calculator, MainActivity.kt).
// The model starts and stops with the activity's ON_START and ON_STOP (Lifecycle's observer,
// https://developer.android.com/topic/libraries/architecture/lifecycle#lc), so nothing listens while another app is in front.
import android.content.Intent
import android.os.Bundle
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
        setContent { AppSurface { LauncherApp(homeSignal, homeAnimated) } }
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
