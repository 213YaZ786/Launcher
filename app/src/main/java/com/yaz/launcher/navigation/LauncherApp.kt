package com.yaz.launcher.navigation

// Ported from Store (YAZ/Store at HEAD, navigation/StoreApp.kt), itself from Files and Calculator (https://github.com/213YaZ786/Calculator, navigation/CalculatorApp.kt):
// the NavHost and PlainBack (the base kit's). One route for now, Home; the panels come with the next lot.
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
fun LauncherApp(homeSignal: Int = 0) {
    val nav = rememberNavController()
    val store: SettingsStore = koinInject()
    val settings by store.settings.collectAsState()
    LaunchedEffect(homeSignal) { if (homeSignal > 0) nav.popBackStack(Routes.HOME, inclusive = false) }

    NavHost(nav, startDestination = Routes.HOME) {
        composable(Routes.HOME) {
            HomeScreen(
                homeSignal = homeSignal,
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
