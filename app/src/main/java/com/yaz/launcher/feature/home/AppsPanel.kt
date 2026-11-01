package com.yaz.launcher.feature.home

// The apps panel, in from the edge its swipe starts at (the right by default). Trebuchet's drawer is a grid of icons with the label under each, in a fixed number of columns
// (https://github.com/LineageOS/android_packages_apps_Trebuchet/blob/lineage-22.2/src/com/android/launcher3/allapps/AllAppsGridAdapter.java and
// DeviceProfile.numShownAllAppsColumns): the same here with three columns, one icon size taken from the column's width, and one continuous
// scroll sideways, no pages, as the Pixel Launcher's drawer and iOS's App Library scroll: Compose's LazyHorizontalGrid, the official
// lists recipe (https://developer.android.com/develop/ui/compose/lists, "Lazy grids"), with as many rows as fit the height and a column a
// third of the pane wide, so the next column starts at the right edge. The order is the model's (most used of the last 7 days, then A to Z).
// The long press is the message menu of every app, the panes kit's PillMenu (SMS's menu; usage as YAZ/Gallery's AlbumsScreen.AlbumCard):
// the app's shortcuts (Trebuchet's PopupPopulator, at most four), Activities (sdex/ActivityManager), App info, Hide (Trebuchet's hidden apps,
// https://github.com/LineageOS/android_packages_apps_Trebuchet/blob/lineage-22.2/src/com/android/launcher3/lineage/trust/HiddenAppsFilter.java) and Uninstall.
import android.content.pm.ShortcutInfo
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyHorizontalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yaz.launcher.R
import com.yaz.launcher.data.apps.AppEntry
import com.yaz.launcher.data.apps.LauncherState
import com.yaz.launcher.data.apps.ProfileInfo
import com.yaz.launcher.data.apps.ProfileKind
import com.yaz.launcher.ui.component.ZoneSurface
import com.yaz.launcher.ui.icon.PanelIcons
import com.yaz.launcher.ui.icon.toComposeShape
import com.yaz.launcher.ui.component.ShadeInterpolation
import com.yaz.launcher.ui.component.PillItem
import com.yaz.launcher.ui.component.PillMenu
import com.yaz.launcher.ui.component.PillMotion
import com.yaz.launcher.ui.component.entrancePop
import com.yaz.launcher.ui.component.rememberPillMenu
import com.yaz.launcher.ui.component.sinkClickable
import com.yaz.launcher.ui.icon.AppIcons
import kotlinx.coroutines.launch

private const val COLUMNS = 3

/**
 * The panel: the grid laid straight on the scrim, as the Quick Settings shade lays its tiles (no pane), its alpha the shade's content alpha
 * of [progress], the panel's own opening 0 to 1 (SystemUI's ShadeInterpolation.getContentAlpha, ui/component/ShadeInterpolation.kt). [grid] is
 * the grid's state, kept by the screen. On a side ([edge] left or right) it is three columns wide and the panel closes by the drag when the
 * grid is at its edge on the closing side; [close] is the connection that lets the drag the grid cannot use pull the panel shut. At the bottom
 * it is as wide as the screen and shows as many columns as fit.
 *
 * The head holds the pinned activities as cells (sdex/ActivityManager's pinned shortcut), the end the work profile and the private space,
 * each opening with a tile that pauses or locks it, as the Pixel Launcher's and Trebuchet's drawer have their button (WorkProfileManager and
 * PrivateProfileManager, https://github.com/LineageOS/android_packages_apps_Trebuchet/blob/lineage-22.2/src/com/android/launcher3/allapps/WorkProfileManager.java
 * and PrivateProfileManager.java), and below the grid a Settings row.
 */
@Composable
fun AppsPanel(
    state: LauncherState,
    pinned: List<PinnedRow>,
    actions: HomeActions,
    seen: MutableSet<String>,
    grid: LazyGridState,
    modifier: Modifier = Modifier,
    edge: Panel = Panel.RIGHT,
    progress: () -> Float = { 1f },
    close: NestedScrollConnection? = null
) {
    Box(
        modifier
            .graphicsLayer { alpha = ShadeInterpolation.getContentAlpha(progress()) }
            .then(if (close != null) Modifier.nestedScroll(close) else Modifier)
    ) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val density = LocalDensity.current
            val label = MaterialTheme.typography.labelMedium
            val lineHeight = with(density) { label.lineHeight.toDp() }
            // A column is a third of the pane on a side: three show, the fourth starts at the edge once scrolled. At the bottom, as many as fit.
            val columns = if (edge == Panel.BOTTOM) (maxWidth / BOTTOM_COLUMN).toInt().coerceIn(COLUMNS, 10) else COLUMNS
            val columnWidth = maxWidth / columns
            // One icon size, relative to the column.
            val iconSize = (columnWidth * 0.6f).coerceIn(44.dp, 72.dp)
            // A slot holds the icon and the label's two lines.
            val rowHeight = CELL_PAD * 2 + iconSize + LABEL_GAP + lineHeight * 2
            val usable = maxHeight - TOP - lineHeight - SETTINGS_ROW
            val rows = (usable / rowHeight).toInt().coerceAtLeast(1)
            // The work profile and the private space, each with its apps (an empty one still shows its title and button).
            val profiles = state.profiles.filter { it.kind == ProfileKind.WORK || it.kind == ProfileKind.PRIVATE }
            val count = pinned.size + state.apps.size + profiles.sumOf { profile -> 1 + state.others.count { it.userSerial == profile.serial } }
            // At least two rows, so a title and its button fit in a short list. The empty cells that start each block in a new column
            // are not counted: they only fill what is left of a column.
            val shownRows = count.coerceIn(minOf(2, rows), rows)
            val blocks = profileBlocks(pinned.size + state.apps.size, shownRows, profiles.map { profile -> state.others.filter { it.userSerial == profile.serial } })

            // The block of rows is centred in the pane; the grid is as tall as its rows, so each track is one row high.
            Column(Modifier.align(Alignment.Center).fillMaxWidth()) {
                LazyHorizontalGrid(
                    rows = GridCells.Fixed(shownRows),
                    state = grid,
                    modifier = Modifier.fillMaxWidth().height(rowHeight * shownRows)
                ) {
                    itemsIndexed(pinned, key = { _, row -> "pin/" + row.pin.packageName + "/" + row.pin.className + "/" + row.pin.userSerial }) { index, row ->
                        PinnedCell(row, iconSize, actions, seen, index, Modifier.width(columnWidth))
                    }
                    itemsIndexed(state.apps, key = { _, entry -> entry.listKey() }) { index, entry ->
                        AppCell(entry, iconSize, actions, seen, index, Modifier.width(columnWidth))
                    }
                    profiles.forEachIndexed { b, profile ->
                        // A profile starts a new column at its first row: empty cells fill the rest of the column before it, then its
                        // tile is one ordinary cell, drawn like an app, and its apps follow below in the same column.
                        items(blocks[b].padding, key = { "pad/" + profile.serial + "/" + it }) {
                            Spacer(Modifier.width(columnWidth))
                        }
                        item(key = "profile/" + profile.serial) {
                            ProfileCell(profile, iconSize, actions, seen, Modifier.width(columnWidth))
                        }
                        val own = blocks[b].apps
                        itemsIndexed(own, key = { _, entry -> entry.listKey() }) { index, entry ->
                            AppCell(entry, iconSize, actions, seen, index, Modifier.width(columnWidth))
                        }
                    }
                }
                SettingsRow(actions.openSettings)
            }
        }
    }
}

// The work profile or the private space as one tile of the grid, drawn like an app: the glass pane of an app icon, in the user's shape and
// size, with a glyph on it, and the name under it. A tap pauses or resumes the work apps, locks or unlocks the private space (Trebuchet's
// WorkProfileManager.setWorkProfileEnabled and PrivateProfileManager.setQuietMode ask Android for quiet mode the same way,
// https://github.com/LineageOS/android_packages_apps_Trebuchet/blob/lineage-22.2/src/com/android/launcher3/allapps/UserProfileManager.java).
// Compared: Trebuchet's private_space_header.xml (a lock pill beside the title) and the Pixel Launcher (the same pill); the tile in the grid is
// iOS's App Library, where each group is one tile among the others. The cell is AppCell's, the glyph is centred in the pane at 45 % of its size.
@Composable
private fun ProfileCell(profile: ProfileInfo, iconSize: Dp, actions: HomeActions, seen: MutableSet<String>, modifier: Modifier = Modifier) {
    val work = profile.kind == ProfileKind.WORK
    val title = stringResource(if (work) R.string.apps_work else R.string.apps_private)
    // The label says the action a tap does.
    val action = stringResource(
        when {
            work && profile.quiet -> R.string.apps_resume
            work -> R.string.apps_pause
            profile.quiet -> R.string.apps_unlock
            else -> R.string.apps_lock
        }
    )
    // The glyph shows the state: the private space locked or open, the work apps paused or running.
    val glyph = when {
        work && profile.quiet -> PanelIcons.WorkOff
        work -> PanelIcons.Work
        profile.quiet -> PanelIcons.Lock
        else -> PanelIcons.LockOpen
    }
    val shape = LocalIconStyle.current.shape.toComposeShape()
    Box(modifier.entrancePop(seen, "profile/" + profile.serial, 0)) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .sinkClickable(action, onClick = { actions.setQuiet(profile, !profile.quiet) })
                .padding(horizontal = 2.dp, vertical = CELL_PAD)
        ) {
            Box(Modifier.size(iconSize), contentAlignment = Alignment.Center) {
                ZoneSurface(shape = shape, modifier = Modifier.size(iconSize)) { }
                Icon(glyph, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(iconSize * 0.45f))
            }
            CellLabel(title)
        }
    }
}

// A profile's block of the grid: the empty cells that fill the column before it, and its apps. Trebuchet's AlphabeticalAppsList puts the
// private space's header as one item of the list, before its apps, after the others
// (https://github.com/LineageOS/android_packages_apps_Trebuchet/blob/lineage-22.2/src/com/android/launcher3/allapps/AlphabeticalAppsList.java).
internal class ProfileBlock<T>(val padding: Int, val apps: List<T>)

// The blocks of the work profile and the private space, each starting at the first row of a new column: [placed] cells come before the first,
// the column is [rows] cells high, and what is left of the current column ((rows - placed % rows) % rows cells) is filled with empty cells;
// the block is then its header (one cell) and its apps, the next one counting from there. [apps] is the list of each profile's apps.
// Trebuchet's list order (the profiles' apps after the others, AlphabeticalAppsList) in Compose's LazyHorizontalGrid, which fills a column
// top to bottom then the next (https://developer.android.com/develop/ui/compose/lists, "Lazy grids"); the padding is this app's.
internal fun <T> profileBlocks(placed: Int, rows: Int, apps: List<List<T>>): List<ProfileBlock<T>> {
    var used = placed
    return apps.map { own ->
        val padding = (rows - used % rows) % rows
        used += padding + 1 + own.size
        ProfileBlock(padding, own)
    }
}

// The row below the grid that opens Settings: its icon and its name, one tap, no zone.
@Composable
private fun SettingsRow(onClick: () -> Unit) {
    val label = stringResource(R.string.apps_settings)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = SETTINGS_ROW)
            .sinkClickable(label, depth = 0.96f, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Icon(AppIcons.Settings, contentDescription = null, modifier = Modifier.size(20.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = 12.dp))
    }
}

// A pinned activity as a cell: the app's icon and the activity's label, a tap starts it, a long press offers Remove (the panes kit's PillMenu,
// as the apps' cells; the pinned activity is sdex/ActivityManager's
// https://github.com/sdex/ActivityManager/blob/main/app/src/main/java/com/sdex/activityrunner/shortcut/ShortcutManager.kt).
@Composable
private fun PinnedCell(row: PinnedRow, iconSize: Dp, actions: HomeActions, seen: MutableSet<String>, index: Int, modifier: Modifier = Modifier) {
    val menu = rememberPillMenu()
    val remove = stringResource(R.string.menu_remove_shortcut)
    Box(modifier.entrancePop(seen, "pin/" + row.app.listKey() + row.pin.className, index).then(menu.tracker)) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .sinkClickable(row.pin.label, onLongClick = menu::open, onClick = { actions.launchPinned(row.pin) })
                .padding(horizontal = 2.dp, vertical = CELL_PAD)
        ) {
            AppIcon(row.app, iconSize)
            CellLabel(row.pin.label)
        }
        PillMenu(menu, listOf(PillItem(AppIcons.Close, remove, PillMotion.DROP, danger = true) { actions.unpin(row.pin) }))
    }
}

// The label under an icon: two lines at most, never cut or ellipsized, a longer name shrinking down to 9 sp, Compose's TextAutoSize.StepBased
// (https://developer.android.com/reference/kotlin/androidx/compose/foundation/text/TextAutoSize).
@Composable
internal fun CellLabel(text: String) {
    val style = MaterialTheme.typography.labelMedium
    BasicText(
        text,
        style = style.copy(color = LocalContentColor.current, textAlign = TextAlign.Center),
        maxLines = 2,
        autoSize = TextAutoSize.StepBased(minFontSize = 9.sp, maxFontSize = style.fontSize, stepSize = 0.5.sp),
        modifier = Modifier.padding(top = LABEL_GAP)
    )
}

/**
 * One app: its icon, its label under it (two lines at most, shrinking to fit rather than cut). Tap starts it; a long press opens
 * the menu with its shortcuts, App info and Uninstall.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AppCell(entry: AppEntry, iconSize: Dp, actions: HomeActions, seen: MutableSet<String>, index: Int, modifier: Modifier = Modifier) {
    val menu = rememberPillMenu()
    val scope = rememberCoroutineScope()
    var shortcuts by remember { mutableStateOf(emptyList<ShortcutInfo>()) }
    val info = stringResource(R.string.menu_app_info)
    val uninstall = stringResource(R.string.menu_uninstall)
    val hide = stringResource(R.string.menu_hide)
    val activities = stringResource(R.string.menu_activities)
    val updateInStore = stringResource(R.string.menu_update_in_store)
    val items = shortcuts.map { shortcut ->
        PillItem(AppIcons.Play, (shortcut.shortLabel ?: shortcut.longLabel ?: shortcut.id).toString(), PillMotion.BOUNCE) { actions.startShortcut(shortcut) }
    } + listOfNotNull(
        // An app of ours with an update waiting: Store opens (it shows no page per app).
        if (LocalAppMarks.current.update(entry)) PillItem(AppIcons.ArrowCircleUp, updateInStore, PillMotion.BOUNCE) { actions.openStore() } else null,
        // The activities of an app of the main profile only: another profile's packages are not visible to this app, and an explicit intent
        // could not start an activity there.
        if (entry.profile == ProfileKind.MAIN) PillItem(PanelIcons.Code, activities, PillMotion.BOUNCE) { actions.openActivities(entry) } else null,
        PillItem(AppIcons.Info, info, PillMotion.WIGGLE) { actions.appInfo(entry) },
        PillItem(PanelIcons.VisibilityOff, hide, PillMotion.WIGGLE) { actions.hide(entry) },
        PillItem(AppIcons.Delete, uninstall, PillMotion.DROP, danger = true) { actions.uninstall(entry) }
    )
    Box(modifier.entrancePop(seen, entry.listKey(), index).then(menu.tracker)) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .sinkClickable(
                    label = entry.label,
                    onLongClick = {
                        // The shortcuts are asked when the menu opens, off the main thread (a refusal or another home app gives none).
                        scope.launch {
                            shortcuts = actions.shortcuts(entry)
                            menu.open()
                        }
                    },
                    onClick = { actions.launch(entry) }
                )
                .padding(horizontal = 2.dp, vertical = CELL_PAD)
        ) {
            AppIcon(entry, iconSize)
            CellLabel(entry.label)
        }
        PillMenu(menu, items)
    }
}

private val TOP = 14.dp
internal val CELL_PAD = 6.dp
internal val LABEL_GAP = 4.dp

/** About as wide as a column at the bottom, and the least height of the Settings row (a minimum: it grows with its words). */
private val BOTTOM_COLUMN = 96.dp
private val SETTINGS_ROW = 56.dp
