package com.yaz.launcher

// Ported from Store (YAZ/Store at HEAD, MainActivity.kt), itself from Files and Calculator (https://github.com/213YaZ786/Calculator, MainActivity.kt).
// The model starts and stops with the activity's ON_START and ON_STOP (Lifecycle's observer,
// https://developer.android.com/topic/libraries/architecture/lifecycle#lc), so nothing listens while another app is in front.
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.yaz.launcher.data.apps.LauncherModel
import com.yaz.launcher.navigation.LauncherApp
import com.yaz.launcher.ui.theme.AppSurface
import org.koin.android.ext.android.inject

/** Launcher, the home screen: the app's one window. */
class MainActivity : ComponentActivity() {

    private val model: LauncherModel by inject()

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
        setContent { AppSurface { LauncherApp() } }
    }
}
