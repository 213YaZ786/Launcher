package com.yaz.launcher.feature.setup

// The guide, at the first start and again from Settings, About, as tiles (the user, 2026-11-06: "le tuto en tuile doit être appliqué sur toutes les apps",
// only the choices that matter). Ported from Language's welcome (feature/welcome/WelcomeScreen.kt: rememberSetupTiles, WelcomeScreen and
// WelcomeContent, cited at each function), itself FlorisBoard's SetupScreen on Modules' SetupTiles: each step is read from the system again
// whenever the app comes back from Android's own pages (LifecycleResumeEffect), and turns done by itself.
// Launcher's three steps; every other setting keeps its default (SettingsStore's Settings) without a question:
// - Home app (required): RoleManager's ROLE_HOME consent screen (createRequestRoleIntent), started with rememberLauncherForActivityResult,
//   the request as Trebuchet's ApiWrapper.assignDefaultHomeRole makes it, via DefaultHome; Settings.ACTION_HOME_SETTINGS when Android offers no consent screen;
// - Usage access (offered): Settings.ACTION_USAGE_ACCESS_SETTINGS, its state read with Usage.granted;
// - Notification dots (offered): Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS (API 30) for DotListener, its state read with
//   NotificationManager.isNotificationListenerAccessGranted; GutapK can grant it over ADB (`cmd notification allow_listener <component>`).
// not ported: the microphone, engine, voice input, voice keyboard, languages and updates steps and the speech model they read (Language's own, not Launcher's).
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.yaz.launcher.R
import com.yaz.launcher.data.apps.DefaultHome
import com.yaz.launcher.data.apps.Usage
import com.yaz.launcher.data.notify.NotificationDots
import com.yaz.launcher.data.settings.SettingsStore
import com.yaz.launcher.feature.home.setInsets
import com.yaz.launcher.ui.component.BoldButton
import com.yaz.launcher.ui.component.LoadingMark
import com.yaz.launcher.ui.component.SetupTile
import com.yaz.launcher.ui.component.SetupTileDialog
import com.yaz.launcher.ui.component.SetupTileGrid
import com.yaz.launcher.ui.component.leftText
import com.yaz.launcher.ui.component.requiredDone
import com.yaz.launcher.ui.component.requiredLeft
import com.yaz.launcher.ui.glass.LocalGlass
import com.yaz.launcher.ui.glass.glassGround
import com.yaz.launcher.ui.icon.AppIcons
import org.koin.compose.koinInject

/**
 * The few choices worth making, as tiles: each is read from the system again each time the screen comes back
 * (SetupScreen reads isFlorisBoardEnabled, isFlorisBoardSelected and hasNotificationPermission, and sets the current step from them),
 * and turns done by itself. Shared by the guide and by Settings' Guide tile (its value is the count left).
 * Ported from rememberSetupTiles of
 * https://github.com/213YaZ786/Language/blob/main/app/src/main/java/com/yaz/language/feature/welcome/WelcomeScreen.kt#rememberSetupTiles
 * not ported: WelcomeScreen (below, as GuideScreen), WelcomeContent (below, as GuideContent)
 */
@Composable
fun rememberSetupTiles(): List<SetupTile> {
    val context = LocalContext.current
    var checks by remember { mutableIntStateOf(0) }
    // The home role and usage access are granted on Android's own screens: read again when the app comes back.
    LifecycleResumeEffect(Unit) {
        checks++
        onPauseOrDispose { }
    }
    val isHome = remember(checks) { DefaultHome.held(context) }
    val hasUsage = remember(checks) { Usage.granted(context) }
    val hasDots = remember(checks) { NotificationDots.accessGranted(context) }
    val requestHome = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { checks++ }

    return guideTiles(
        home = isHome,
        usage = hasUsage,
        dots = hasDots,
        onHome = {
            val request = DefaultHome.requestIntent(context)
            if (request != null) requestHome.launch(request) else runCatching { context.startActivity(Intent(Settings.ACTION_HOME_SETTINGS)) }
        },
        onUsage = {
            // The app's own page of Android's usage access (Android 10 and later), else the list.
            val own = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS, Uri.parse("package:" + context.packageName))
            runCatching { context.startActivity(own) }.onFailure {
                runCatching { context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }
            }
        },
        onDots = {
            // Android's page of this listener (API 30 and later: Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS with the component), else the list.
            val detail = Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
                .putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME, NotificationDots.component(context).flattenToString())
            runCatching { context.startActivity(detail) }.onFailure {
                runCatching { context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
            }
        }
    )
}

/**
 * The three tiles from what the system says, given: the list rememberSetupTiles returns and a test draws with fixed data.
 * Steps: the home app (required), usage access and notification dots (offered), as the SetupTile list of rememberSetupTiles of
 * https://github.com/213YaZ786/Language/blob/main/app/src/main/java/com/yaz/language/feature/welcome/WelcomeScreen.kt#rememberSetupTiles
 * not ported: WelcomeScreen (below, as GuideScreen), WelcomeContent (below, as GuideContent)
 */
@Composable
fun guideTiles(
    home: Boolean,
    usage: Boolean,
    dots: Boolean,
    onHome: () -> Unit = {},
    onUsage: () -> Unit = {},
    onDots: () -> Unit = {}
): List<SetupTile> {
    val context = LocalContext.current
    // The component as the manifest names it, the debug package included: the string GutapK sends over ADB.
    val listener = remember { NotificationDots.component(context).flattenToString() }
    val homeName = stringResource(R.string.guide_home_name)
    val homeTitle = stringResource(R.string.guide_home)
    val usageName = stringResource(R.string.guide_usage_name)
    val usageTitle = stringResource(R.string.guide_usage)
    val dotsName = stringResource(R.string.guide_dots_name)
    val dotsTitle = stringResource(R.string.guide_dots)
    return listOf(
        SetupTile(
            id = "home", name = homeName, title = homeTitle, icon = AppIcons.Apps,
            state = stringResource(if (home) R.string.guide_home_on else R.string.guide_home_off),
            summary = stringResource(R.string.guide_home_summary),
            done = home, required = true,
            onClick = onHome
        ),
        SetupTile(
            id = "usage", name = usageName, title = usageTitle, icon = AppIcons.Update,
            state = stringResource(if (usage) R.string.guide_allowed else R.string.guide_not_allowed),
            summary = stringResource(R.string.guide_usage_summary),
            done = usage, required = false,
            onClick = onUsage
        ),
        SetupTile(
            id = "dots", name = dotsName, title = dotsTitle, icon = AppIcons.Notifications,
            state = stringResource(if (dots) R.string.guide_on else R.string.guide_off),
            summary = stringResource(R.string.guide_dots_summary, listener),
            done = dots, required = false,
            onClick = onDots
        )
    )
}

/**
 * The guide: the tiles, the tile whose long press is open, Finish held back while the required one is left; the flag is the settings' setupDone.
 * Ported from WelcomeScreen of
 * https://github.com/213YaZ786/Language/blob/main/app/src/main/java/com/yaz/language/feature/welcome/WelcomeScreen.kt#WelcomeScreen
 */
@Composable
fun GuideScreen(onFinish: () -> Unit) {
    val settings: SettingsStore = koinInject()
    val steps = rememberSetupTiles()
    // The tile whose long press is open (its id), "" when none.
    var explained by rememberSaveable { mutableStateOf("") }
    val finish = {
        settings.update { it.copy(setupDone = true) }
        onFinish()
    }
    // Back leaves the guide the way Finish does.
    BackHandler(onBack = finish)

    GuideContent(steps, finish) { explained = it.id }

    steps.firstOrNull { it.id == explained }?.let { tile ->
        SetupTileDialog(tile, onDismiss = { explained = "" })
    }
}

/**
 * What the guide draws from the tiles it is given: the layout of SetupScreen (header, steps, footer with the one button), split from
 * [GuideScreen] so that a picture can be taken with fixed tiles, without the system checks. Ported from WelcomeContent of
 * https://github.com/213YaZ786/Language/blob/main/app/src/main/java/com/yaz/language/feature/welcome/WelcomeScreen.kt#WelcomeContent
 */
@Composable
fun GuideContent(tiles: List<SetupTile>, onFinish: () -> Unit = {}, onExplain: (SetupTile) -> Unit = {}) {
    val steps = tiles
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        // The home is the wallpaper, so the guide brings its own ground (the glass kit's glassGround, as the apps' pages have).
        modifier = Modifier
            .fillMaxSize()
            .glassGround(LocalGlass.current, MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            // ActivityAllAppsContainerView.setInsets' left and right margins (feature/home/AppsPanel.kt's setInsets): clear of the side bars and the cut-out.
            .setInsets()
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.widthIn(max = 720.dp).fillMaxWidth().padding(horizontal = 8.dp)
        ) {
            Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars))
            Spacer(Modifier.height(40.dp))
            LoadingMark(size = 120.dp)
            Spacer(Modifier.height(20.dp))
            // Every title in the accent (the user, 2026-10-31), as Modules' SettingsSectionTitle.
            Text(
                stringResource(R.string.guide_title),
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(8.dp))
            Text(steps.leftText(), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            Spacer(Modifier.height(24.dp))
            SetupTileGrid(steps, onExplain)

            Spacer(Modifier.height(32.dp))
            val ready = steps.requiredDone()
            BoldButton(filled = true, enabled = ready, onClick = onFinish) { Text(stringResource(R.string.guide_finish)) }
            if (!ready) {
                Spacer(Modifier.height(12.dp))
                Text(
                    stringResource(R.string.guide_needed, steps.requiredLeft().joinToString(", ") { it.name }),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
            Spacer(Modifier.height(24.dp))
            Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
        }
    }
}
