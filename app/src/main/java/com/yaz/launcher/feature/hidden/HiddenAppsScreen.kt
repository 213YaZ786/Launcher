package com.yaz.launcher.feature.hidden

// The Hidden apps screen, opened from Settings: the apps the user hid, A to Z, each launchable, Unhide by the toggle at the row's end
// (Trebuchet's item_hidden_app_switch), a long press (the panes kit's PillMenu) or a swipe (SwipeRow). Trebuchet's TrustAppsActivity lists every app with a lock to hide or show it
// (https://github.com/LineageOS/android_packages_apps_Trebuchet/blob/lineage-22.2/src/com/android/launcher3/lineage/trust/TrustAppsActivity.java and
// TrustAppsAdapter.java, a RecyclerView of icon, label and the hidden switch); here the apps hidden are the list, since the apps panel's long press
// hides. Trebuchet asks for no authentication to open this list (only a "protected" app asks the lock screen to start), so none is asked here.
// The rows are Passwords' lines with a SwipeRow and a PillMenu (YAZ/Passwords at HEAD, feature/list/ListScreen.kt, EntryItem).
import android.content.ComponentName
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.yaz.launcher.R
import com.yaz.launcher.data.apps.AppActions
import com.yaz.launcher.data.apps.AppEntry
import com.yaz.launcher.data.apps.LauncherModel
import com.yaz.launcher.data.apps.LauncherState
import com.yaz.launcher.data.settings.SettingsStore
import com.yaz.launcher.feature.common.ListFrame
import com.yaz.launcher.feature.common.ListRow
import com.yaz.launcher.feature.home.AppIcon
import com.yaz.launcher.feature.home.IconStyle
import com.yaz.launcher.feature.home.LocalIconStyle
import com.yaz.launcher.feature.home.listKey
import com.yaz.launcher.navigation.LocalReadableInset
import com.yaz.launcher.ui.component.BoldIconButton
import com.yaz.launcher.ui.component.EmptyZone
import com.yaz.launcher.ui.component.PillItem
import com.yaz.launcher.ui.component.PillMenu
import com.yaz.launcher.ui.component.PillMotion
import com.yaz.launcher.ui.component.SwipeAction
import com.yaz.launcher.ui.component.SwipeRow
import com.yaz.launcher.ui.component.navigationBarBottom
import com.yaz.launcher.ui.component.rememberHaptics
import com.yaz.launcher.ui.component.rememberPillMenu
import com.yaz.launcher.ui.icon.PanelIcons
import org.koin.compose.koinInject

@Composable
fun HiddenAppsScreen(onBack: () -> Unit) {
    val model: LauncherModel = koinInject()
    val store: SettingsStore = koinInject()
    val state by model.state.collectAsState()
    val settings by store.settings.collectAsState()
    val context = LocalContext.current
    val haptics = rememberHaptics()
    CompositionLocalProvider(LocalIconStyle provides IconStyle(settings.iconShape, settings.themedIcons)) {
        HiddenAppsContent(
            state = state,
            onBack = onBack,
            onLaunch = { entry ->
                if (!AppActions.launch(context, ComponentName(entry.packageName, entry.className), entry.user)) haptics.reject()
            },
            onUnhide = { entry -> model.unhide(entry.packageName) }
        )
    }
}

// The screen without the model, so the screenshot test draws it from fixed data: Now in Android's ForYouRoute / ForYouScreen split, the route reads
// the state, the screen only draws it (https://github.com/android/nowinandroid/blob/main/feature/foryou/impl/src/main/kotlin/com/google/samples/apps/nowinandroid/feature/foryou/impl/ForYouScreen.kt).
@Composable
fun HiddenAppsContent(state: LauncherState, onBack: () -> Unit, onLaunch: (AppEntry) -> Unit, onUnhide: (AppEntry) -> Unit) {
    val list = rememberLazyListState()
    ListFrame(stringResource(R.string.hidden_title), list, onBack) { padding ->
        Box(Modifier.fillMaxSize()) {
            when {
                // Nothing before the model has loaded, or the empty state would flash.
                !state.loaded -> Unit
                state.hidden.isEmpty() -> EmptyZone(
                    title = stringResource(R.string.hidden_empty_title),
                    message = stringResource(R.string.hidden_empty_text),
                    icon = PanelIcons.VisibilityOff,
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
                        items(state.hidden, key = { it.listKey() }) { entry ->
                            HiddenItem(entry, onLaunch = { onLaunch(entry) }, onUnhide = { onUnhide(entry) })
                        }
                    }
                }
            }
        }
    }
}

// One hidden app: SwipeRow around a Box carrying the PillMenu tracker (Passwords' EntryItem). A swipe and the menu both unhide.
@Composable
private fun HiddenItem(entry: AppEntry, onLaunch: () -> Unit, onUnhide: () -> Unit) {
    val menu = rememberPillMenu()
    val haptics = rememberHaptics()
    val unhide = stringResource(R.string.menu_unhide)
    // The row leaves the way it was pushed (removes), then the list drops it.
    SwipeRow(start = SwipeAction(PanelIcons.Visibility, unhide, removes = true, run = onUnhide), end = null) {
        Box(menu.tracker) {
            ListRow(
                title = entry.label,
                leading = { AppIcon(entry, 48.dp) },
                // Trebuchet's TrustAppsAdapter: a visible toggle on every row, its tap inverts the app's visibility
                // (https://github.com/LineageOS/android_packages_apps_Trebuchet/blob/lineage-22.2/src/com/android/launcher3/lineage/trust/TrustAppsAdapter.java).
                trailing = {
                    BoldIconButton(onClick = { haptics.done(); onUnhide() }) {
                        Icon(PanelIcons.Visibility, contentDescription = unhide)
                    }
                },
                onLongClick = menu::open,
                onClick = onLaunch
            )
            PillMenu(menu, listOf(PillItem(PanelIcons.Visibility, unhide, PillMotion.BOUNCE) { onUnhide() }))
        }
    }
}
