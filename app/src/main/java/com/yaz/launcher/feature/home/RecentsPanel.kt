package com.yaz.launcher.feature.home

// The Recents panel, up from the bottom: the apps by last use, newest first, icons with labels wrapping in rows.
// - The list is the model's (Taskbar's recents from UsageStatsManager, https://github.com/farmerbb/Taskbar/blob/master/app/src/main/java/com/farmerbb/taskbar/ui/TaskbarController.java).
// - An icon swiped up leaves the list: iOS 4's recents bar closed an app's icon with a gesture, and Compose's anchored draggable
//   (https://developer.android.com/develop/ui/compose/touch-input/pointer-input/drag-swipe-fling, "Anchored draggable") gives each icon
//   its own vertical state, two anchors, the icon following the finger and fading as it goes.
// - The long press is the panes kit's PillMenu (SMS's message menu): Remove from list, Hide (Trebuchet's hidden apps, same as the apps panel), Force stop (Android's App info, where the
//   button is; no app can stop another).
// - Without usage access, the "dead end handled" rule: a short instruction and the button that opens Android's usage access page.
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.DraggableAnchors
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.anchoredDraggable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.yaz.launcher.R
import com.yaz.launcher.data.apps.AppEntry
import com.yaz.launcher.data.apps.LauncherState
import com.yaz.launcher.ui.component.EmptyZone
import com.yaz.launcher.ui.component.ShadeInterpolation
import com.yaz.launcher.ui.component.PillItem
import com.yaz.launcher.ui.component.PillMenu
import com.yaz.launcher.ui.component.PillMotion
import com.yaz.launcher.ui.component.entrancePop
import com.yaz.launcher.ui.component.rememberHaptics
import com.yaz.launcher.ui.component.rememberPillMenu
import com.yaz.launcher.ui.component.sinkClickable
import com.yaz.launcher.ui.icon.AppIcons
import com.yaz.launcher.ui.icon.PanelIcons

private enum class Dismiss { REST, GONE }

/**
 * The panel: the icons laid straight on the scrim, as the Quick Settings shade lays its tiles (no pane). It is a full page like the Apps
 * panel, [modifier] giving it the page its edge has (from under the header on), the icons centred in it; its alpha is the shade's content
 * alpha of [progress], the panel's own opening 0 to 1 (SystemUI's ShadeInterpolation.getContentAlpha, ui/component/ShadeInterpolation.kt).
 */
@Composable
fun RecentsPanel(
    state: LauncherState,
    actions: HomeActions,
    seen: MutableSet<String>,
    modifier: Modifier = Modifier,
    progress: () -> Float = { 1f }
) {
    Box(modifier.graphicsLayer { alpha = ShadeInterpolation.getContentAlpha(progress()) }) {
        BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            when {
                !state.usageGranted -> EmptyZone(
                    title = stringResource(R.string.recents_usage_title),
                    message = stringResource(R.string.recents_usage_text),
                    iconShape = null,
                    actionLabel = stringResource(R.string.recents_usage_allow),
                    onAction = actions.allowUsage
                )
                state.recents.isEmpty() -> EmptyZone(
                    title = stringResource(R.string.recents_empty_title),
                    message = stringResource(R.string.recents_empty_text),
                    iconShape = null
                )
                else -> {
                    val density = LocalDensity.current
                    val lineHeight = with(density) { MaterialTheme.typography.labelMedium.lineHeight.toDp() }
                    val columns = ((maxWidth - SIDE * 2) / 84.dp).toInt().coerceIn(3, 6)
                    val iconSize = ((maxWidth - SIDE * 2) / columns * 0.6f).coerceIn(44.dp, 64.dp)
                    val rowHeight = CELL_PAD * 2 + iconSize + LABEL_GAP + lineHeight * 2
                    // Rows are not scrolled: what does not fit on the page shows when a row above leaves.
                    val rows = ((maxHeight - TOP - BOTTOM) / rowHeight).toInt().coerceAtLeast(1)
                    val shown = state.recents.take(rows * columns)
                    GridRows(
                        shown,
                        columns,
                        rowHeight,
                        Modifier.fillMaxWidth().padding(start = SIDE, end = SIDE, top = TOP, bottom = BOTTOM)
                    ) { index, entry ->
                        key(entry.listKey()) {
                            RecentCell(entry, iconSize, actions, seen, index, Modifier.fillMaxWidth())
                        }
                    }
                }
            }
        }
    }
}

/**
 * One recent app. Tap starts it again; dragged up past half its height it leaves the list, following the finger and fading; a long
 * press opens the menu.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RecentCell(entry: AppEntry, iconSize: Dp, actions: HomeActions, seen: MutableSet<String>, index: Int, modifier: Modifier = Modifier) {
    val haptics = rememberHaptics()
    val menu = rememberPillMenu()
    val drag = remember { AnchoredDraggableState(Dismiss.REST) }
    var height by remember { mutableIntStateOf(0) }
    // The anchors are set once the cell has a size: resting at 0, gone one cell height up (the recipe's onSizeChanged way).
    LaunchedEffect(height) {
        if (height > 0) drag.updateAnchors(DraggableAnchors { Dismiss.REST at 0f; Dismiss.GONE at -height.toFloat() })
    }
    LaunchedEffect(drag.settledValue) {
        if (drag.settledValue == Dismiss.GONE) {
            haptics.done()
            actions.removeFromRecents(entry)
        }
    }
    val remove = stringResource(R.string.menu_remove_recent)
    val stop = stringResource(R.string.menu_force_stop)
    val hide = stringResource(R.string.menu_hide)
    val updateInStore = stringResource(R.string.menu_update_in_store)
    val items = listOfNotNull(
        PillItem(AppIcons.Close, remove, PillMotion.DROP) { actions.removeFromRecents(entry) },
        if (LocalAppMarks.current.update(entry)) PillItem(AppIcons.ArrowCircleUp, updateInStore, PillMotion.BOUNCE) { actions.openStore() } else null,
        PillItem(PanelIcons.VisibilityOff, hide, PillMotion.WIGGLE) { actions.hide(entry) },
        PillItem(AppIcons.Warning, stop, PillMotion.WIGGLE, danger = true) { actions.appInfo(entry) }
    )
    Box(
        modifier
            .entrancePop(seen, "recent/" + entry.listKey(), index)
            .onSizeChanged { height = it.height }
            .graphicsLayer {
                val offset = drag.offsetOrZero()
                translationY = offset
                alpha = if (height > 0) (1f + offset / height).coerceIn(0f, 1f) else 1f
            }
            .then(menu.tracker)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .anchoredDraggable(drag, Orientation.Vertical)
                .sinkClickable(label = entry.label, onLongClick = menu::open, onClick = { actions.launch(entry) })
                .padding(horizontal = 2.dp, vertical = CELL_PAD)
        ) {
            AppIcon(entry, iconSize)
            Text(
                entry.label,
                style = MaterialTheme.typography.labelMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = LABEL_GAP)
            )
        }
        PillMenu(menu, items)
    }
}

private val SIDE = 8.dp
private val TOP = 14.dp
private val BOTTOM = 10.dp
// CELL_PAD and LABEL_GAP are AppsPanel.kt's (the same cell in both panels).
