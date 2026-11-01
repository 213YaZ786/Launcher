package com.yaz.launcher.navigation

// Ported from Store (YAZ/Store at HEAD, navigation/StoreApp.kt), itself from Files and Calculator (https://github.com/213YaZ786/Calculator, navigation/CalculatorApp.kt):
// the NavHost and PlainBack (the base kit's). One route for now, Home; the panels come with the next lot.
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.navigation.compose.currentBackStackEntryAsState
import kotlinx.coroutines.flow.first
import android.net.Uri
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.navArgument
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.yaz.launcher.BuildConfig
import com.yaz.launcher.data.settings.SettingsStore
import com.yaz.launcher.feature.activities.ActivitiesScreen
import com.yaz.launcher.feature.hidden.HiddenAppsScreen
import com.yaz.launcher.feature.home.HomeScreen
import com.yaz.launcher.feature.settings.SettingsScreen
import com.yaz.launcher.feature.setup.GuideScreen
import com.yaz.launcher.ui.component.UpdatePrompt
import org.koin.compose.koinInject

/** Long enough to be read as motion, short enough not to be waited on (SMS's navigation/SmsApp.kt, NAV_MS). */
private const val NAV_MS = 260

object Routes {
    const val HOME = "home"
    const val SETTINGS = "settings"
    const val GUIDE = "guide"
    const val HIDDEN = "hidden"
    const val ACTIVITIES = "activities/{package}"

    // A route with an argument, the package name encoded in the path (https://developer.android.com/guide/navigation/design#nav-with-args).
    fun activities(packageName: String) = "activities/" + Uri.encode(packageName)
}

// Store's StoreApp (YAZ/Store at HEAD, navigation/StoreApp.kt): Home, Settings and the guide shown again from Settings' About.
// [homeSignal] goes up each time Home is pressed while the app runs (MainActivity.onNewIntent, Trebuchet's Launcher.onNewIntent
// https://github.com/LineageOS/android_packages_apps_Trebuchet/blob/lineage-22.2/src/com/android/launcher3/Launcher.java): every route above Home
// is left, as popBackStack(route, inclusive = false) does (https://developer.android.com/guide/navigation/backstack#pop), the animation being the pop's.
@Composable
fun LauncherApp(homeSignal: Int = 0, homeAnimated: Boolean = true) {
    val nav = rememberNavController()
    val store: SettingsStore = koinInject()
    val settings by store.settings.collectAsState()
    val current by nav.currentBackStackEntryAsState()
    // True while a pop to Home must show no transition: Trebuchet's goToState(NORMAL, alreadyOnHome), instant when the home was brought to the front.
    // The NavHost's transition lambdas read it (https://developer.android.com/guide/navigation/use-graph/animate-transitions).
    var instant by remember { mutableStateOf(false) }
    LaunchedEffect(homeSignal) {
        if (homeSignal > 0) {
            instant = !homeAnimated
            nav.popBackStack(Routes.HOME, inclusive = false)
            // Cleared once the pop has settled: Home is the current route and two frames have been drawn.
            snapshotFlow { current?.destination?.route }.first { it == Routes.HOME }
            withFrameNanos { }
            withFrameNanos { }
            instant = false
        }
    }

    // SMS's transitions, "the same motion as the other apps" (YAZ/SMS at HEAD, navigation/SmsApp.kt, the NavHost's four lambdas and NAV_MS = 260):
    // opening scales up from 0.94 and fades in, going back scales down and fades out. None while [instant].
    NavHost(
        nav,
        startDestination = Routes.HOME,
        enterTransition = { if (instant) EnterTransition.None else scaleIn(initialScale = 0.94f, animationSpec = tween(NAV_MS)) + fadeIn(animationSpec = tween(NAV_MS)) },
        exitTransition = { if (instant) ExitTransition.None else fadeOut(animationSpec = tween(NAV_MS)) },
        popEnterTransition = { if (instant) EnterTransition.None else fadeIn(animationSpec = tween(NAV_MS)) },
        popExitTransition = { if (instant) ExitTransition.None else scaleOut(targetScale = 0.94f, animationSpec = tween(NAV_MS)) + fadeOut(animationSpec = tween(NAV_MS)) }
    ) {
        composable(Routes.HOME) {
            HomeScreen(
                homeSignal = homeSignal,
                homeAnimated = homeAnimated,
                onSettings = { nav.navigate(Routes.SETTINGS) },
                onActivities = { nav.navigate(Routes.activities(it)) }
            )
            // The update kit's check, once per launch of the process (Calculator's CalculatorApp.kt).
            UpdatePrompt(settings.updates, BuildConfig.VERSION_NAME)
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(
                onBack = { nav.popBackStack() },
                onGuide = { nav.navigate(Routes.GUIDE) },
                onHidden = { nav.navigate(Routes.HIDDEN) }
            )
        }
        composable(Routes.HIDDEN) {
            HiddenAppsScreen(onBack = { nav.popBackStack() })
        }
        // The package is the route's argument, a string Navigation decodes (https://developer.android.com/guide/navigation/design#nav-with-args).
        composable(Routes.ACTIVITIES, arguments = listOf(navArgument("package") { type = NavType.StringType })) { entry ->
            ActivitiesScreen(entry.arguments?.getString("package").orEmpty(), onBack = { nav.popBackStack() })
        }
        composable(Routes.GUIDE) {
            GuideScreen(onFinish = { nav.popBackStack() })
        }
    }
    PlainBack(nav)
}
