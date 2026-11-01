package com.yaz.launcher.feature.setup

// The guide, at the first start and again from Settings, About. Ported from Store's setup screen
// (https://github.com/213YaZ786/Store/blob/main/app/src/main/java/com/yaz/store/feature/setup/SetupScreen.kt), itself Files' and Calculator's welcome:
// zones that turn into a tick once settled and are read again when the app comes back from Android's own pages (LifecycleResumeEffect).
// Launcher's two steps:
// - Make Launcher the home app: RoleManager's ROLE_HOME consent screen, started with rememberLauncherForActivityResult
//   (https://developer.android.com/reference/android/app/role/RoleManager#createRequestRoleIntent(java.lang.String), the request as
//   Trebuchet's ApiWrapper.assignDefaultHomeRole makes it, via DefaultHome), and Settings.ACTION_HOME_SETTINGS when Android offers no consent screen;
// - Allow usage access: Settings.ACTION_USAGE_ACCESS_SETTINGS
//   (https://developer.android.com/reference/android/provider/Settings#ACTION_USAGE_ACCESS_SETTINGS), its state read again on resume with Usage.granted.
// - Notification dots (optional): Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS (API 30) for DotListener, its state read again on resume with
//   NotificationManager.isNotificationListenerAccessGranted; GutapK can grant it over ADB (`cmd notification allow_listener <component>`).
// Finish is allowed without the second step; the zone says what it costs (a dead end handled, not blocked).
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ShapeDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
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
import com.yaz.launcher.ui.component.BoldButton
import com.yaz.launcher.ui.component.LoadingMark
import com.yaz.launcher.ui.component.ZoneSurface
import com.yaz.launcher.ui.glass.LocalGlass
import com.yaz.launcher.ui.glass.glassGround
import com.yaz.launcher.ui.icon.AppIcons
import org.koin.compose.koinInject

// Store's SetupScreen: the checks, read again on resume; the flag is the settings' setupDone.
@Composable
fun GuideScreen(onFinish: () -> Unit) {
    val settings: SettingsStore = koinInject()
    val context = LocalContext.current
    var checks by remember { mutableIntStateOf(0) }
    // The home role and usage access are granted on Android's own screens: read again when the app comes back.
    LifecycleResumeEffect(Unit) {
        checks++
        onPauseOrDispose { }
    }
    val home = remember(checks) { DefaultHome.held(context) }
    val usage = remember(checks) { Usage.granted(context) }
    val dots = remember(checks) { NotificationDots.accessGranted(context) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { checks++ }
    val finish = {
        settings.update { it.copy(setupDone = true) }
        onFinish()
    }
    // Back leaves the guide the way Finish does.
    BackHandler(onBack = finish)
    GuideContent(
        home = home,
        usage = usage,
        dots = dots,
        onMakeHome = {
            val request = DefaultHome.requestIntent(context)
            if (request != null) ask.launch(request) else runCatching { context.startActivity(Intent(Settings.ACTION_HOME_SETTINGS)) }
        },
        onAllowUsage = {
            // The app's own page of Android's usage access (Android 10 and later), else the list.
            val own = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS, Uri.parse("package:" + context.packageName))
            runCatching { context.startActivity(own) }.onFailure {
                runCatching { context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }
            }
        },
        onAllowDots = {
            // Android's page of this listener (API 30 and later: Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS with the component,
            // https://developer.android.com/reference/android/provider/Settings#ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS), else the list.
            val detail = Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
                .putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME, NotificationDots.component(context).flattenToString())
            runCatching { context.startActivity(detail) }.onFailure {
                runCatching { context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
            }
        },
        onFinish = finish
    )
}

// Store's SetupContent, stateless so a test draws it with fixed data: two zones and one Finish.
@Composable
fun GuideContent(
    home: Boolean,
    usage: Boolean,
    dots: Boolean = false,
    onMakeHome: () -> Unit = {},
    onAllowUsage: () -> Unit = {},
    onAllowDots: () -> Unit = {},
    onFinish: () -> Unit = {}
) {
    val context = LocalContext.current
    // The component as the manifest names it, the debug package included: the string GutapK sends over ADB.
    val listener = remember { NotificationDots.component(context).flattenToString() }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        // The home is the wallpaper, so the guide brings its own ground (the glass kit's glassGround, as the apps' pages have).
        modifier = Modifier
            .fillMaxSize()
            .glassGround(LocalGlass.current, MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp)
    ) {
        Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars))
        Spacer(Modifier.height(32.dp))
        LoadingMark(size = 120.dp)
        Spacer(Modifier.height(16.dp))
        // Every title in the accent (the user, 2026-10-31), as Modules' SettingsSectionTitle.
        Text(
            stringResource(R.string.guide_title),
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(28.dp))

        GuideZone(AppIcons.Apps, stringResource(R.string.guide_home), done = home) {
            if (!home) BoldButton(onClick = onMakeHome, filled = true) { Text(stringResource(R.string.guide_home_action)) }
        }
        Spacer(Modifier.height(16.dp))
        GuideZone(AppIcons.Update, stringResource(R.string.guide_usage), done = usage) {
            if (!usage) {
                Text(
                    stringResource(R.string.guide_usage_note),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                BoldButton(onClick = onAllowUsage) { Text(stringResource(R.string.guide_usage_action)) }
            }
        }
        Spacer(Modifier.height(16.dp))
        // Optional: the guide goes on without it, and the note says so.
        GuideZone(AppIcons.Notifications, stringResource(R.string.guide_dots), done = dots) {
            if (!dots) {
                Text(
                    stringResource(R.string.guide_dots_note, listener),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                BoldButton(onClick = onAllowDots) { Text(stringResource(R.string.guide_dots_action)) }
            }
        }

        Spacer(Modifier.height(28.dp))
        Row(Modifier.widthIn(max = 520.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            BoldButton(onClick = onFinish, modifier = Modifier.weight(1f), filled = true) { Text(stringResource(R.string.guide_finish)) }
        }
        Spacer(Modifier.height(24.dp))
        Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
    }
}

/** One thing to settle, a tick once done (Store's SetupZone, with the ExtraLarge corner of every pane). */
@Composable
private fun GuideZone(icon: ImageVector, title: String, done: Boolean, content: @Composable () -> Unit) {
    ZoneSurface(shape = ShapeDefaults.ExtraLarge, modifier = Modifier.widthIn(max = 520.dp).fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(horizontal = 22.dp, vertical = 20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (done) AppIcons.CheckCircle else icon,
                    contentDescription = if (done) stringResource(R.string.done) else null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 12.dp))
            }
            content()
        }
    }
}
