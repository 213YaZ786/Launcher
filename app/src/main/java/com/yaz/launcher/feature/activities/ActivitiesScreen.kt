package com.yaz.launcher.feature.activities

// The Activities screen of an app, opened from the apps panel's long press (main profile apps only): the app's activities by name, the label and the
// short class name on each row. ActivitiesListViewModel.getItems loads the list off the main thread
// (https://github.com/sdex/ActivityManager/blob/main/app/src/main/java/com/sdex/activityrunner/app/ActivitiesListViewModel.kt), here a
// LaunchedEffect on the IO dispatcher over AppActivities.list; a tap starts the activity (ActivityLauncher.launch, explicit intent, a refusal
// handled), one that is not exported or is guarded by a permission is greyed and does nothing, since it would need root
// (ActivityModel.launchRequiresRoot) and this app has none. "Add to Apps" (long press and swipe) pins the activity at the head of the
// Apps panel (ActivityManager's pinned shortcut, shortcut/ShortcutManager.kt and CreateShortcutActivity.kt: its label and the app's icon).
// The rows are Passwords' lines with a SwipeRow and a PillMenu (YAZ/Passwords at HEAD, feature/list/ListScreen.kt, EntryItem).
import android.content.ComponentName
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.yaz.launcher.R
import com.yaz.launcher.data.apps.ActivityModel
import com.yaz.launcher.data.apps.AppActivities
import com.yaz.launcher.data.apps.LauncherModel
import com.yaz.launcher.data.settings.PinnedActivity
import com.yaz.launcher.data.settings.SettingsStore
import com.yaz.launcher.feature.common.ListFrame
import com.yaz.launcher.feature.common.ListRow
import com.yaz.launcher.navigation.LocalReadableInset
import com.yaz.launcher.ui.component.EmptyZone
import com.yaz.launcher.ui.component.PillItem
import com.yaz.launcher.ui.component.PillMenu
import com.yaz.launcher.ui.component.PillMotion
import com.yaz.launcher.ui.component.SwipeAction
import com.yaz.launcher.ui.component.SwipeRow
import com.yaz.launcher.ui.component.navigationBarBottom
import com.yaz.launcher.ui.component.rememberHaptics
import com.yaz.launcher.ui.component.rememberPillMenu
import com.yaz.launcher.ui.icon.AppIcons
import com.yaz.launcher.ui.icon.PanelIcons
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject

/** The activities of [packageName]; null while they load. */
private class Loaded(val title: String, val activities: List<ActivityModel>)

@Composable
fun ActivitiesScreen(packageName: String, onBack: () -> Unit) {
    val model: LauncherModel = koinInject()
    val store: SettingsStore = koinInject()
    val state by model.state.collectAsState()
    val settings by store.settings.collectAsState()
    val context = LocalContext.current
    val haptics = rememberHaptics()
    val list = rememberLazyListState()

    // The app's entry in the main profile, for the name at once and the profile's serial the pins carry.
    val entry = remember(state) { (state.apps + state.hidden).firstOrNull { it.packageName == packageName && it.userSerial == MAIN_SERIAL } }
    val loaded by produceState<Loaded?>(null, packageName) {
        value = withContext(Dispatchers.IO) { Loaded(AppActivities.appLabel(context, packageName), AppActivities.list(context, packageName)) }
    }
    val title = entry?.label ?: loaded?.title ?: packageName

    fun isPinned(m: ActivityModel) =
        settings.pinned.any { it.packageName == m.packageName && it.className == m.className && it.userSerial == MAIN_SERIAL }

    fun toggle(m: ActivityModel) {
        store.update { s ->
            val existing = s.pinned.filterNot { it.packageName == m.packageName && it.className == m.className && it.userSerial == MAIN_SERIAL }
            if (existing.size != s.pinned.size) {
                s.copy(pinned = existing)
            } else {
                s.copy(pinned = existing + PinnedActivity(m.packageName, m.className, MAIN_SERIAL, m.label?.takeIf { it.isNotBlank() } ?: m.name))
            }
        }
    }

    ListFrame(title, list, onBack) { padding ->
        Box(Modifier.fillMaxSize()) {
            val shown = loaded
            when {
                shown == null -> Unit
                shown.activities.isEmpty() -> EmptyZone(
                    title = stringResource(R.string.activities_empty_title),
                    message = stringResource(R.string.activities_empty_text),
                    icon = PanelIcons.Code,
                    modifier = Modifier.fillMaxSize()
                )
                else -> {
                    val inset = LocalReadableInset.current
                    LazyColumn(
                        state = list,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            start = inset + 16.dp,
                            end = inset + 16.dp,
                            top = padding.calculateTopPadding() + 4.dp,
                            bottom = navigationBarBottom() + 24.dp
                        ),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        items(shown.activities, key = { it.className }) { activity ->
                            ActivityItem(
                                activity,
                                pinned = isPinned(activity),
                                onLaunch = {
                                    if (!AppActivities.launch(context, ComponentName(activity.packageName, activity.className))) haptics.reject()
                                },
                                onToggle = { toggle(activity) }
                            )
                        }
                    }
                }
            }
        }
    }
}

private const val MAIN_SERIAL = 0L

// One activity: SwipeRow around a Box carrying the PillMenu tracker (Passwords' EntryItem). Add to Apps by a swipe and by the menu, Remove
// for one already added. An activity closed to other apps is greyed, without swipe or menu.
@Composable
private fun ActivityItem(activity: ActivityModel, pinned: Boolean, onLaunch: () -> Unit, onToggle: () -> Unit) {
    val menu = rememberPillMenu()
    val add = stringResource(R.string.menu_add_to_apps)
    val remove = stringResource(R.string.menu_remove_from_apps)
    val name = activity.label?.takeIf { it.isNotBlank() } ?: activity.name
    val row: @Composable () -> Unit = {
        ListRow(
            title = name,
            second = activity.name,
            third = if (activity.launchable) null else stringResource(R.string.activities_closed),
            enabled = activity.launchable,
            trailing = if (pinned) {
                { Icon(PanelIcons.PushPin, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp)) }
            } else null,
            onLongClick = menu::open,
            onClick = onLaunch
        )
    }
    if (!activity.launchable) {
        row()
        return
    }
    val toggleLabel = if (pinned) remove else add
    SwipeRow(start = SwipeAction(if (pinned) AppIcons.Close else PanelIcons.PushPin, toggleLabel, removes = false, run = onToggle), end = null) {
        Box(menu.tracker) {
            row()
            PillMenu(menu, listOf(PillItem(if (pinned) AppIcons.Close else PanelIcons.PushPin, toggleLabel, PillMotion.BOUNCE) { onToggle() }))
        }
    }
}
