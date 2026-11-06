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
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.lazy.grid.LazyGridItemScope
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.graphics.vector.ImageVector
import com.yaz.launcher.data.apps.UserProfileManager
import com.yaz.launcher.data.apps.WorkProfileManager
import com.yaz.launcher.ui.component.LinearWavyProgress
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyHorizontalGrid
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yaz.launcher.R
import com.yaz.launcher.data.apps.AppActions
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
import com.yaz.launcher.ui.component.navigationBarBottom
import com.yaz.launcher.ui.component.entrancePop
import com.yaz.launcher.ui.component.rememberPillMenu
import com.yaz.launcher.ui.component.sinkClickable
import com.yaz.launcher.ui.component.softTopEdge
import com.yaz.launcher.ui.icon.AppIcons
import kotlinx.coroutines.launch

private const val COLUMNS = 3

// The private space's lock and unlock animation. PrivateProfileManager's constants: the collapse and the expansion take EXPAND_COLLAPSE_DURATION, the
// private apps fade over APP_OPACITY_DURATION, starting APP_OPACITY_DELAY after the unlock tap (NO_DELAY when they fade out)
// (https://github.com/LineageOS/android_packages_apps_Trebuchet/blob/lineage-22.2/src/com/android/launcher3/allapps/PrivateProfileManager.java#EXPAND_COLLAPSE_DURATION).
private const val EXPAND_COLLAPSE_DURATION = 400
private const val APP_OPACITY_DURATION = 400
private const val APP_OPACITY_DELAY = 400
private const val NO_DELAY = 0

// animateAlphaOfIcons: the alpha of the private items goes 0 to 1 when expanding and 1 to 0 when collapsing, Interpolators.LINEAR, after which the
// collapse (animateCollapseAnimation) takes the items out of the list and the cells after them slide into the place, EXPAND_COLLAPSE_DURATION long.
// Compose's LazyGrid item animations are that: fadeInSpec, fadeOutSpec and placementSpec of Modifier.animateItem
// (https://developer.android.com/develop/ui/compose/lists#item-animations).
// (https://github.com/LineageOS/android_packages_apps_Trebuchet/blob/lineage-22.2/src/com/android/launcher3/allapps/PrivateProfileManager.java#animateAlphaOfIcons).
private fun LazyGridItemScope.animateAlphaOfIcons(): Modifier = Modifier.animateItem(
    fadeInSpec = tween(durationMillis = APP_OPACITY_DURATION, delayMillis = APP_OPACITY_DELAY, easing = LinearEasing),
    fadeOutSpec = tween(durationMillis = APP_OPACITY_DURATION, delayMillis = NO_DELAY, easing = LinearEasing),
    placementSpec = tween(durationMillis = EXPAND_COLLAPSE_DURATION)
)

/**
 * The panel: the grid laid straight on the scrim, as the Quick Settings shade lays its tiles (no pane), its alpha the shade's content alpha
 * of [progress], the panel's own opening 0 to 1 (SystemUI's ShadeInterpolation.getContentAlpha, ui/component/ShadeInterpolation.kt). [grid] is
 * the grid's state, kept by the screen. On a side ([edge] left or right) it is three columns wide and the panel closes by the drag when the
 * grid is at its edge on the closing side; [close] is the connection that lets the drag the grid cannot use pull the panel shut. At the bottom
 * it is as wide as the screen, shows as many columns as fit and scrolls up and down (a vertical grid, closing by a drag down at its top).
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
    close: NestedScrollConnection? = null,
    /** Goes up each time the grid must show the work profile's tile (Launcher.showAllAppsFromIntent with the WORK tab). */
    workScroll: Int = 0
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
            val usable = maxHeight - TOP - lineHeight
            val rows = (usable / rowHeight).toInt().coerceAtLeast(1)
            // The work profile and the private space, each with its apps (an empty one still shows its title and button).
            val profiles = state.profiles.filter { it.kind == ProfileKind.WORK || it.kind == ProfileKind.PRIVATE }
            // WorkProfileManager.shouldShowWorkApps: the work apps of a profile in STATE_DISABLED are not listed (addWorkItems adds the disabled card
            // instead: the tile says so, see ProfileCell). The private space keeps its own rule, its apps leave the model's list while it is locked.
            // (https://github.com/LineageOS/android_packages_apps_Trebuchet/blob/lineage-22.2/src/com/android/launcher3/allapps/WorkProfileManager.java#shouldShowWorkApps)
            val currentStates = profiles.map { UserProfileManager.getCurrentState(it, state.profileStates) }
            val others = profiles.mapIndexed { b, profile ->
                val showApps = profile.kind != ProfileKind.WORK || WorkProfileManager.shouldShowWorkApps(currentStates[b])
                state.others.filter { showApps && it.userSerial == profile.serial }
            }
            // PrivateProfileManager.updateView, case STATE_ENABLED: the settings gear is visible when isPrivateSpaceSettingsAvailable; and
            // AlphabeticalAppsList.addPrivateSpaceApps adds addPrivateSpaceInstallAppButton in that state only.
            // (https://github.com/LineageOS/android_packages_apps_Trebuchet/blob/lineage-22.2/src/com/android/launcher3/allapps/PrivateProfileManager.java#updateView)
            val isEnabled = profiles.mapIndexed { b, profile -> profile.kind == ProfileKind.PRIVATE && currentStates[b] == UserProfileManager.STATE_ENABLED }
            val settings = isEnabled.map { it && state.privateSpaceSettingsAvailable }
            val install = isEnabled.map { it && state.privateSpaceInstallAvailable }
            val placed = pinned.size + state.apps.size
            // At least two rows, so a title and its button fit in a short list. The empty cells that start each block in a new column
            // are not counted: they only fill what is left of a column.
            val count = placed + profiles.indices.sumOf { 1 + others[it].size + listOf(settings[it], install[it]).count { added -> added } }
            val shownRows = count.coerceIn(minOf(2, rows), rows)
            val blocks = profileBlocks(placed, if (edge == Panel.BOTTOM) columns else shownRows, others, settings, install)

            // PrivateProfileManager.reset: `previousState == STATE_DISABLED && updatedState == STATE_ENABLED` is postUnlock, which runs
            // expandPrivateSpace: scrollForHeaderToBeVisibleInContainer scrolls to the itemToScrollTo that shows the header (here the private
            // tile; the apps fade in by animateAlphaOfIcons). A STATE_TRANSITION between is not a state: a refused lock goes ENABLED, TRANSITION,
            // ENABLED and unlocks nothing.
            // (https://github.com/LineageOS/android_packages_apps_Trebuchet/blob/lineage-22.2/src/com/android/launcher3/allapps/PrivateProfileManager.java#expandPrivateSpace)
            val itemToScrollTo = profiles.indexOfFirst { it.kind == ProfileKind.PRIVATE }
            val updatedState = if (itemToScrollTo >= 0) currentStates[itemToScrollTo] else UserProfileManager.STATE_UNKNOWN
            var previousState by remember { mutableIntStateOf(UserProfileManager.STATE_UNKNOWN) }
            LaunchedEffect(updatedState) {
                if (updatedState != UserProfileManager.STATE_TRANSITION) {
                    if (previousState == UserProfileManager.STATE_DISABLED && updatedState == UserProfileManager.STATE_ENABLED) {
                        // The index of the tile: the cells before it, each earlier block whole, and the empty cells that start its own.
                        val tileIndex = blocks.take(itemToScrollTo).fold(placed) { index, block -> index + block.padding + 1 + block.apps.size + block.extra }
                        grid.animateScrollToItem(tileIndex + blocks[itemToScrollTo].padding)
                    }
                    previousState = updatedState
                }
            }

            // Launcher.showAllAppsWithSelectedTabFromIntent: mAppsView.switchToTab(WORK) -- here the grid scrolls to the work profile's tile, at once,
            // as the private space's header is scrolled to above.
            // (https://github.com/LineageOS/android_packages_apps_Trebuchet/blob/lineage-22.2/src/com/android/launcher3/Launcher.java#showAllAppsFromIntent)
            val workTile = profiles.indexOfFirst { it.kind == ProfileKind.WORK }
            LaunchedEffect(workScroll) {
                if (workScroll > 0 && workTile >= 0) {
                    grid.scrollToItem(blocks.take(workTile).fold(placed) { index, block -> index + block.padding + 1 + block.apps.size + block.extra } + blocks[workTile].padding)
                }
            }

            if (edge == Panel.BOTTOM) {
                // From the bottom the list goes up and down, as the Pixel Launcher's and Trebuchet's drawer: Compose's LazyVerticalGrid
                // (https://developer.android.com/develop/ui/compose/lists, "Lazy grids"), a profile starting a new row (the rest of the
                // row before it is empty cells), the Settings row the last item over the whole width. The panel closes by a drag down
                // once the grid is at its top (the [close] connection).
                //
                // The panel reaches the screen's bottom and the window's bottom inset is the list's own bottom padding:
                // ActivityAllAppsContainerView#applyAdapterSideAndBottomPaddings, bottomPadding = Math.max(mInsets.bottom, mNavBarScrimHeight), set
                // on the adapter holder, whose applyPadding gives the RecyclerView mPadding.bottom (+ bottomOffset, 0 here: no search bar or tab
                // header under it); the list draws its items into that padding, android:clipToPadding="false" in all_apps_rv_layout.xml (a
                // Compose lazy grid draws into its contentPadding by default). The last row, Settings, then ends above the navigation bar and the
                // apps scroll under it.
                // (https://github.com/LineageOS/android_packages_apps_Trebuchet/blob/lineage-22.2/src/com/android/launcher3/allapps/ActivityAllAppsContainerView.java#applyAdapterSideAndBottomPaddings)
                // (https://github.com/LineageOS/android_packages_apps_Trebuchet/blob/lineage-22.2/res/layout/all_apps_rv_layout.xml)
                // mNavBarScrimHeight is 0 in the base computeNavBarScrimHeight, so the padding is mInsets.bottom alone.
                // not ported: computeNavBarScrimHeight override (no nav bar scrim of our own), grid.allAppsPadding.left and right (the cells
                // fill the width), the work tab's second AdapterHolder (one grid holds every profile).
                val navigationBarInset = navigationBarBottom()
                LazyVerticalGrid(
                    columns = GridCells.Fixed(columns),
                    state = grid,
                    contentPadding = PaddingValues(top = TOP, bottom = maxOf(navigationBarInset, 0.dp)),
                    // What scrolls past the panel's top fades out as in SMS (FloatingPane.kt's softTopEdge, the panes kit), its strength the
                    // scroll over the fade length as ScrollView.getTopFadingEdgeStrength
                    // (https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/core/java/android/widget/ScrollView.java).
                    modifier = Modifier
                        .fillMaxSize()
                        .softTopEdge({ 0.dp }) {
                            if (grid.firstVisibleItemIndex > 0) 1f
                            else (grid.firstVisibleItemScrollOffset / with(density) { 16.dp.toPx() }).coerceIn(0f, 1f)
                        }
                ) {
                    appCells(pinned, state, profiles, currentStates, blocks, columnWidth, iconSize, actions, seen)
                }
            } else {
                // The block of rows is centred in the pane; the grid is as tall as its rows, so each track is one row high. The panel on the
                // left is the right one mirrored (reverseLayout, https://developer.android.com/develop/ui/compose/lists): its first apps sit
                // at its right, next to the screen, and a swipe right brings the next columns.
                Column(Modifier.align(Alignment.Center).fillMaxWidth()) {
                    LazyHorizontalGrid(
                        rows = GridCells.Fixed(shownRows),
                        state = grid,
                        reverseLayout = edge == Panel.LEFT,
                        modifier = Modifier.fillMaxWidth().height(rowHeight * shownRows)
                    ) {
                        appCells(pinned, state, profiles, currentStates, blocks, columnWidth, iconSize, actions, seen)
                    }
                }
            }
        }
    }
}

// The cells of the grid, the same in both directions: the pinned activities, the apps, then each profile's block. A profile starts a new line
// (a column sideways, a row from the bottom): empty cells fill the rest of the line before it, then its tile is one ordinary cell, drawn like
// an app, and its apps follow.
private fun LazyGridScope.appCells(
    pinned: List<PinnedRow>,
    state: LauncherState,
    profiles: List<ProfileInfo>,
    currentStates: List<Int>,
    blocks: List<ProfileBlock<AppEntry>>,
    columnWidth: Dp,
    iconSize: Dp,
    actions: HomeActions,
    seen: MutableSet<String>
) {
    itemsIndexed(pinned, key = { _, row -> "pin/" + row.pin.packageName + "/" + row.pin.className + "/" + row.pin.userSerial }) { index, row ->
        PinnedCell(row, iconSize, actions, seen, index, Modifier.width(columnWidth))
    }
    itemsIndexed(state.apps, key = { _, entry -> entry.listKey() }) { index, entry ->
        AppCell(entry, iconSize, actions, seen, index, Modifier.width(columnWidth))
    }
    profiles.forEachIndexed { b, profile ->
        items(blocks[b].padding, key = { "pad/" + profile.serial + "/" + it }) {
            Spacer(Modifier.width(columnWidth))
        }
        item(key = "profile/" + profile.serial) {
            ProfileCell(profile, currentStates[b], iconSize, actions, seen, Modifier.width(columnWidth))
        }
        // PrivateProfileManager.updateView: the private space's header holds the settings gear next to the lock (PrivateSpaceSettingsButton).
        if (blocks[b].settings) {
            item(key = "ps-settings/" + profile.serial) {
                ProfileCell(
                    profile, currentStates[b], iconSize, actions, seen, Modifier.width(columnWidth).then(animateAlphaOfIcons()),
                    beside = AppIcons.Settings, besideLabel = stringResource(R.string.ps_container_settings), onBesideClick = actions.openPrivateSpaceSettings
                )
            }
        }
        itemsIndexed(blocks[b].apps, key = { _, entry -> entry.listKey() }) { index, entry ->
            AppCell(entry, iconSize, actions, seen, index, Modifier.width(columnWidth).then(animateAlphaOfIcons()))
        }
        // PrivateProfileManager.addPrivateSpaceInstallAppButton: the Install apps button after the private apps.
        if (blocks[b].install) {
            item(key = "ps-install/" + profile.serial) {
                ProfileCell(
                    profile, currentStates[b], iconSize, actions, seen, Modifier.width(columnWidth).then(animateAlphaOfIcons()),
                    beside = PanelIcons.Add, besideLabel = stringResource(R.string.ps_add_button_label), onBesideClick = { actions.installToPrivateSpace(profile) }
                )
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
private fun ProfileCell(
    profile: ProfileInfo,
    currentState: Int,
    iconSize: Dp,
    actions: HomeActions,
    seen: MutableSet<String>,
    modifier: Modifier = Modifier,
    // The cells beside the private space's tile (the settings gear, the Install apps button) are drawn the same way: their own glyph, label and tap.
    beside: ImageVector? = null,
    besideLabel: String = "",
    onBesideClick: () -> Unit = {}
) {
    val work = profile.kind == ProfileKind.WORK
    // WorkProfileManager.addWorkItems: in STATE_DISABLED the work apps are replaced by the disabled card, whose title is work_apps_paused_title; here
    // the work tile is that card: its label says the work apps are paused, Resume is its action.
    // (https://github.com/LineageOS/android_packages_apps_Trebuchet/blob/lineage-22.2/src/com/android/launcher3/allapps/WorkProfileManager.java#addWorkItems)
    val paused = work && WorkProfileManager.addWorkItems(currentState)
    val title = beside?.let { besideLabel } ?: stringResource(if (paused) R.string.work_apps_paused_title else if (work) R.string.apps_work else R.string.apps_private)
    // The label says the action a tap does.
    val action = if (beside != null) besideLabel else stringResource(
        when {
            work && profile.quiet -> R.string.apps_resume
            work -> R.string.apps_pause
            profile.quiet -> R.string.apps_unlock
            else -> R.string.apps_lock
        }
    )
    // The glyph shows the state: the private space locked or open, the work apps paused or running. In STATE_TRANSITION there is none: the progress
    // indicator is drawn in its place, as PrivateProfileManager.updateView hides the lock pill and shows the transition view
    // (https://github.com/LineageOS/android_packages_apps_Trebuchet/blob/lineage-22.2/src/com/android/launcher3/allapps/PrivateProfileManager.java#updateView).
    val glyph = when {
        beside != null -> beside
        currentState == UserProfileManager.STATE_TRANSITION -> null
        work && profile.quiet -> PanelIcons.WorkOff
        work -> PanelIcons.Work
        profile.quiet -> PanelIcons.Lock
        else -> PanelIcons.LockOpen
    }
    val style = LocalIconStyle.current
    val shape = style.shape.toComposeShape()
    // Themed icons on: the tile is drawn as a themed icon, the shape filled with primaryContainer and the glyph in onPrimaryContainer, the
    // ground and the tint AppIcon passes to renderThemedIcon (Launcher3's ThemedIconDrawable, themed_icon_background_color and themed_icon_color,
    // https://github.com/LineageOS/android_frameworks_libs_systemui/blob/lineage-22.2/iconloaderlib/src/com/android/launcher3/icons/mono/ThemedIconDrawable.kt).
    val ground = if (style.themed) MaterialTheme.colorScheme.primaryContainer else null
    val glyphTint = if (style.themed) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.primary
    Box(modifier.entrancePop(seen, (if (beside != null) "ps/" + besideLabel + "/" else "profile/") + profile.serial, 0)) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                // While in STATE_TRANSITION a tap does nothing.
                .sinkClickable(action, onClick = {
                    if (beside != null) onBesideClick()
                    else if (currentState != UserProfileManager.STATE_TRANSITION) actions.setQuiet(profile, !profile.quiet)
                })
                .padding(horizontal = 2.dp, vertical = CELL_PAD)
        ) {
            Box(Modifier.size(iconSize), contentAlignment = Alignment.Center) {
                if (ground != null) {
                    Box(Modifier.size(iconSize).background(ground, shape))
                } else {
                    ZoneSurface(shape = shape, modifier = Modifier.size(iconSize)) { }
                }
                if (glyph != null) {
                    Icon(glyph, contentDescription = null, tint = glyphTint, modifier = Modifier.size(iconSize * 0.45f))
                } else {
                    LinearWavyProgress(Modifier.width(iconSize * 0.5f), color = glyphTint, trackColor = glyphTint.copy(alpha = 0.25f))
                }
            }
            CellLabel(title)
        }
    }
}

// A profile's block of the grid: the empty cells that fill the column before it, and its apps. Trebuchet's AlphabeticalAppsList puts the
// private space's header as one item of the list, before its apps, after the others
// (https://github.com/LineageOS/android_packages_apps_Trebuchet/blob/lineage-22.2/src/com/android/launcher3/allapps/AlphabeticalAppsList.java).
// The unlocked private space adds a settings cell before its apps and an Install apps cell after them (PrivateProfileManager.updateView,
// addPrivateSpaceInstallAppButton): [settings] and [install] say so, [extra] counts them.
internal class ProfileBlock<T>(val padding: Int, val apps: List<T>, val settings: Boolean = false, val install: Boolean = false) {
    val extra: Int get() = (if (settings) 1 else 0) + (if (install) 1 else 0)
}

// The blocks of the work profile and the private space, each starting at the first row of a new column: [placed] cells come before the first,
// the column is [rows] cells high, and what is left of the current column ((rows - placed % rows) % rows cells) is filled with empty cells;
// the block is then its header (one cell) and its apps, the next one counting from there. [apps] is the list of each profile's apps.
// Trebuchet's list order (the profiles' apps after the others, AlphabeticalAppsList) in Compose's LazyHorizontalGrid, which fills a column
// top to bottom then the next (https://developer.android.com/develop/ui/compose/lists, "Lazy grids"); the padding is this app's.
internal fun <T> profileBlocks(
    placed: Int,
    rows: Int,
    apps: List<List<T>>,
    settings: List<Boolean> = emptyList(),
    install: List<Boolean> = emptyList()
): List<ProfileBlock<T>> {
    var used = placed
    return apps.mapIndexed { b, own ->
        val padding = (rows - used % rows) % rows
        val block = ProfileBlock(padding, own, settings.getOrElse(b) { false }, install.getOrElse(b) { false })
        used += padding + 1 + own.size + block.extra
        block
    }
}

// A pinned activity as a cell: the app's icon and the activity's label, a tap starts it, a long press offers Remove (the panes kit's PillMenu,
// as the apps' cells; the pinned activity is sdex/ActivityManager's
// https://github.com/sdex/ActivityManager/blob/main/app/src/main/java/com/sdex/activityrunner/shortcut/ShortcutManager.kt).
@Composable
private fun PinnedCell(row: PinnedRow, iconSize: Dp, actions: HomeActions, seen: MutableSet<String>, index: Int, modifier: Modifier = Modifier) {
    val menu = rememberPillMenu()
    CloseOnHome(menu)
    val launchSource = rememberLaunchSource()
    val remove = stringResource(R.string.menu_remove_shortcut)
    Box(modifier.entrancePop(seen, "pin/" + row.app.listKey() + row.pin.className, index).then(menu.tracker)) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .then(launchSource.cell)
                // The long click's label for TalkBack: the menu's one action (BubbleTextView.java#L535-L545, as the app cells).
                .semantics { onLongClick(label = remove, action = null) }
                .sinkClickable(row.pin.label, onLongClick = menu::open, onClick = { actions.launchPinned(row.pin, launchSource.getActivityLaunchOptions()) })
                .padding(horizontal = 2.dp, vertical = CELL_PAD)
        ) {
            AppIcon(row.app, iconSize, launchSource.iconBounds)
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
    CloseOnHome(menu)
    val launchSource = rememberLaunchSource()
    // BubbleTextView.applyLabel: the label TalkBack reads, "Chrome, Work" for a work app, "Disabled ..." for a suspended one; the text drawn stays the label.
    val description = entry.applyContentDescription()
    val scope = rememberCoroutineScope()
    var shortcuts by remember { mutableStateOf(emptyList<ShortcutInfo>()) }
    // SecondaryDropTarget.getButtonType: Uninstall is offered only when it answers UNINSTALL (no user restriction, not the private space, not a
    // system app, an uninstall target); asked with the shortcuts when the menu opens, off the main thread.
    var buttonType by remember { mutableIntStateOf(AppActions.INVALID) }
    val deepShortcut = stringResource(R.string.action_deep_shortcut)
    val info = stringResource(R.string.menu_app_info)
    val uninstall = stringResource(R.string.menu_uninstall)
    val hide = stringResource(R.string.menu_hide)
    val activities = stringResource(R.string.menu_activities)
    val updateInStore = stringResource(R.string.menu_update_in_store)
    val items = shortcuts.map { shortcut ->
        PillItem(AppIcons.Play, (shortcut.shortLabel ?: shortcut.longLabel ?: shortcut.id).toString(), PillMotion.BOUNCE) { actions.startShortcut(shortcut, launchSource.getActivityLaunchOptions()) }
    } + listOfNotNull(
        // An app of ours with an update waiting: Store opens (it shows no page per app).
        if (LocalAppMarks.current.update(entry)) PillItem(AppIcons.ArrowCircleUp, updateInStore, PillMotion.BOUNCE) { actions.openStore() } else null,
        // The activities of an app of the main profile only: another profile's packages are not visible to this app, and an explicit intent
        // could not start an activity there.
        if (entry.profile == ProfileKind.MAIN) PillItem(PanelIcons.Code, activities, PillMotion.BOUNCE) { actions.openActivities(entry) } else null,
        PillItem(AppIcons.Info, info, PillMotion.WIGGLE) { actions.appInfo(entry, launchSource.getActivityLaunchOptions()) },
        PillItem(PanelIcons.VisibilityOff, hide, PillMotion.WIGGLE) { actions.hide(entry) },
        if (buttonType == AppActions.UNINSTALL) PillItem(AppIcons.Delete, uninstall, PillMotion.DROP, danger = true) { actions.uninstall(entry) } else null
    )
    Box(modifier.entrancePop(seen, entry.listKey(), index).then(menu.tracker)) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .then(launchSource.cell)
                // BubbleTextView.onInitializeAccessibilityNodeInfo adds the action's label for TalkBack; the long click opens the shortcut menu
                // (R.string.action_deep_shortcut). The action itself is sinkClickable's: a label alone merges with it.
                // (https://github.com/LineageOS/android_packages_apps_Trebuchet/blob/lineage-22.2/src/com/android/launcher3/BubbleTextView.java#L535-L545)
                .semantics { contentDescription = description; onLongClick(label = deepShortcut, action = null) }
                .sinkClickable(
                    label = description,
                    onLongClick = {
                        // PopupPopulator.createUpdateRunnable
                        // (https://github.com/LineageOS/android_packages_apps_Trebuchet/blob/lineage-22.2/src/com/android/launcher3/popup/PopupPopulator.java#createUpdateRunnable):
                        // the container opens at once with its fixed items, the runnable queries the shortcuts on the model's worker thread
                        // (sorted and cut by sortAndFilterShortcuts inside the query) and posts them to the views on the UI handler when they
                        // arrive; they sit at the head of the menu (a refusal or another home app gives none).
                        shortcuts = emptyList()
                        buttonType = AppActions.INVALID
                        menu.open()
                        scope.launch { shortcuts = actions.shortcuts(entry) }
                        scope.launch { buttonType = actions.getButtonType(entry) }
                    },
                    onClick = { actions.launchFrom(entry, launchSource.getActivityLaunchOptions()) }
                )
                .padding(horizontal = 2.dp, vertical = CELL_PAD)
        ) {
            AppIcon(entry, iconSize, launchSource.iconBounds)
            CellLabel(entry.label)
        }
        PillMenu(menu, items)
    }
}

private val TOP = 14.dp
internal val CELL_PAD = 6.dp
internal val LABEL_GAP = 4.dp

/** About as wide as a column at the bottom. */
private val BOTTOM_COLUMN = 96.dp
