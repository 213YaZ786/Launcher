package com.yaz.launcher.feature.home

// Compared: Pixel Launcher (long press on the wallpaper gives Wallpaper & style and Home settings), iOS (long press edits, no settings menu), Kvaesitso (one row per swipe), Niagara (one fixed drawer swipe); winners: Pixel Launcher and Trebuchet for the menu and the profiles, Kvaesitso for the layout.
// The home screen: the wallpaper, small glass tabs on the edges that have a panel, and up to three panels, one per swipe (Settings, Layout).
// - The window is transparent over the wallpaper, as Trebuchet's workspace is (windowShowWallpaper, see Theme.Launcher and
//   https://github.com/LineageOS/android_packages_apps_Trebuchet/blob/lineage-22.2/res/values/styles.xml).
// - The panels follow the finger with Compose's anchored draggable, the official recipe
//   (https://developer.android.com/develop/ui/compose/touch-input/pointer-input/drag-swipe-fling, "Anchored draggable"): one horizontal state
//   with three anchors (left panel, closed, right panel; an anchor only where that edge has a panel) on the whole window, one vertical state
//   (closed, open; only when the bottom has a panel), the anchors set in Modifier.layout, the panels placed by the state's offset, `animateTo` for
//   the taps on the tabs. A panel open takes Back, as the Pixel Launcher's drawer does (Modules' PlainBack keeps predictive back off).
// - Each edge shows the panel its swipe is set to (PanelLayout): Apps or Recents. A side panel starts from its screen edge (square corners
//   there); the bottom one is screen wide.
// - The tabs are small glass tabs flush against the edges that have a panel, each in a transparent tap target with the panel's name as description,
//   so a gesture is never the only way in; they sink under the finger as the suite's buttons do (Store's Pop.kt). The bottom has none.
// - A long press on the wallpaper, no panel open, gives Wallpaper and Settings in the panes kit's PillMenu, the options Trebuchet's
//   OptionsPopupView.showDefaultOptions lists on a long press of the workspace
//   (https://github.com/LineageOS/android_packages_apps_Trebuchet/blob/lineage-22.2/src/com/android/launcher3/views/OptionsPopupView.java).
// - The wallpaper blurs behind the panels as they open (WindowBlur.kt, https://source.android.com/docs/core/display/window-blurs).
// - The stateful part answers the panels with AppActions and AppShortcuts (Trebuchet's ActivityContext.startActivitySafely and
//   PopupPopulator, https://github.com/LineageOS/android_packages_apps_Trebuchet/blob/lineage-22.2/src/com/android/launcher3/popup/PopupPopulator.java).
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.DraggableAnchors
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.animateTo
import androidx.compose.foundation.gestures.snapTo
import androidx.compose.foundation.gestures.anchoredDraggable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.foundation.layout.mandatorySystemGestures
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.LayoutDirection
import android.app.WallpaperManager
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import androidx.compose.ui.res.stringResource
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import com.yaz.launcher.data.glance.launchPackage
import com.yaz.launcher.data.settings.DateMode
import com.yaz.launcher.data.settings.SwipeDown
import com.yaz.launcher.ui.component.findActivity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.yaz.launcher.R
import com.yaz.launcher.data.apps.AppActions
import com.yaz.launcher.data.apps.AppActivities
import com.yaz.launcher.data.apps.AppEntry
import com.yaz.launcher.data.apps.AppShortcuts
import com.yaz.launcher.data.apps.LaunchSource
import com.yaz.launcher.data.apps.LauncherModel
import com.yaz.launcher.data.apps.LauncherState
import com.yaz.launcher.data.store.StoreMarks
import com.yaz.launcher.data.settings.IconShape
import com.yaz.launcher.data.settings.PanelKind
import com.yaz.launcher.data.settings.SettingsStore
import com.yaz.launcher.data.settings.ThemeMode
import com.yaz.launcher.feature.settings.SettingsIcons
import com.yaz.launcher.feature.setup.GuideScreen
import com.yaz.launcher.ui.component.EdgeTab
import com.yaz.launcher.ui.component.Haptics
import com.yaz.launcher.ui.component.PillItem
import com.yaz.launcher.ui.component.PillMenu
import com.yaz.launcher.ui.component.PillMotion
import com.yaz.launcher.ui.component.ShadeInterpolation
import com.yaz.launcher.ui.component.navigationBarBottom
import com.yaz.launcher.ui.component.rememberPillMenu
import com.yaz.launcher.ui.component.rememberWindowBlur
import com.yaz.launcher.ui.component.rememberHaptics
import com.yaz.launcher.ui.component.sinkClickable
import com.yaz.launcher.ui.component.statusBarTop
import com.yaz.launcher.ui.icon.AppIcons
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject

// ActivityAllAppsContainerView.AdapterHolder.MAIN and WORK, the tab Launcher.onNewIntent asks showAllAppsWithSelectedTabFromIntent for: the
// apps, or the work apps (here the work profile's tile of the Apps panel).
// (https://github.com/LineageOS/android_packages_apps_Trebuchet/blob/lineage-22.2/src/com/android/launcher3/allapps/ActivityAllAppsContainerView.java#L1564-L1567)
object AdapterHolder {
    const val MAIN = 0
    const val WORK = 1
}

/**
 * The home screen with its model: the guide first, then the wallpaper and the panels. [onSettings] opens the Settings screen, [onActivities] the
 * Activities screen of a package. [appsTab] is an intent's ask for the Apps panel on a tab (Launcher.showAllAppsFromIntent), null when none,
 * answered once by [onAppsTabShown].
 */
@Composable
fun HomeScreen(
    homeSignal: Int = 0,
    homeAnimated: Boolean = true,
    screenOffSignal: Int = 0,
    appsTab: Int? = null,
    onAppsTabShown: () -> Unit = {},
    onSettings: () -> Unit = {},
    onActivities: (String) -> Unit = {}
) {
    val model: LauncherModel = koinInject()
    val store: SettingsStore = koinInject()
    val state by model.state.collectAsState()
    val dots by model.dots.collectAsState()
    val updates by model.updates.collectAsState()
    val settings by store.settings.collectAsState()
    val context = LocalContext.current
    val haptics = rememberHaptics()
    val scope = rememberCoroutineScope()
    // The guide, at the first start; again from Settings, About (its own route).
    if (!settings.setupDone) {
        // An ask for the apps has nothing to show before the guide is done: it is dropped, not kept for later.
        if (appsTab != null) LaunchedEffect(appsTab) { onAppsTabShown() }
        GuideScreen(onFinish = {})
        return
    }
    val actions = remember(model, store, context) {
        HomeActions(
            launch = { entry -> onClickAppShortcut(null, entry, context, haptics) },
            launchFrom = { entry, source -> onClickAppShortcut(source, entry, context, haptics) },
            appInfo = { entry -> if (!AppActions.appInfo(context, ComponentName(entry.packageName, entry.className), entry.user)) haptics.reject() },
            uninstall = { entry -> if (!AppActions.uninstall(context, ComponentName(entry.packageName, entry.className), entry.user)) haptics.reject() },
            shortcuts = { entry ->
                withContext(Dispatchers.IO) {
                    // Android hands the shortcuts to the default home app only: elsewhere none, without an error.
                    if (AppShortcuts.hasHostPermission(context)) {
                        AppShortcuts.query(context, ComponentName(entry.packageName, entry.className), entry.user)
                    } else {
                        emptyList()
                    }
                }
            },
            startShortcut = { shortcut -> if (!AppShortcuts.start(context, shortcut)) haptics.reject() },
            removeFromRecents = { entry -> model.removeFromRecents(entry.packageName) },
            openStore = { if (!StoreMarks.open(context)) haptics.reject() },
            allowUsage = {
                // The app's own page of Android's usage access (Android 10 and later), else the list.
                val own = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS, Uri.parse("package:" + context.packageName))
                runCatching { context.startActivity(own) }.onFailure {
                    runCatching { context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }
                }
            },
            openSettings = onSettings,
            // Trebuchet's OptionsPopupView.startWallpaperPicker: not started when the user may not set a wallpaper (a reject haptic stands for its toast).
            openWallpaper = { if (!AppActions.openWallpaper(context)) haptics.reject() },
            // Trebuchet's WorkProfileManager.setWorkProfileEnabled and UserProfileManager.setQuietModeSafely: the tile goes to STATE_TRANSITION, then
            // quiet mode is asked; only the default home app may pause or lock a profile (a SecurityException asks for the home role, from the activity).
            setQuiet = { profile, quiet ->
                scope.launch {
                    val accepted = model.setWorkProfileEnabled(profile, enabled = !quiet, context = context.findActivity() ?: context)
                    if (accepted != true) haptics.reject()
                }
            },
            // PrivateSpaceSettingsButton.onClick and the Install apps button: an intent sender started from the activity.
            openPrivateSpaceSettings = { if (!AppActions.onClickPrivateSpaceSettings(context.findActivity() ?: context)) haptics.reject() },
            installToPrivateSpace = { profile -> if (!AppActions.onClickPrivateSpaceInstall(context.findActivity() ?: context, profile.user)) haptics.reject() },
            refresh = model::refresh,
            hide = { entry -> model.hide(entry.packageName) },
            openActivities = { entry -> onActivities(entry.packageName) },
            // A pinned activity that cannot start says so as startActivitySafely does ("Toast.makeText(context, R.string.activity_not_found, ...)",
            // https://github.com/LineageOS/android_packages_apps_Trebuchet/blob/lineage-22.2/src/com/android/launcher3/views/ActivityContext.java#L456-L461).
            launchPinned = { pin, source ->
                if (!AppActivities.launch(context, ComponentName(pin.packageName, pin.className), source)) {
                    Toast.makeText(context, R.string.activity_not_found, Toast.LENGTH_SHORT).show()
                    haptics.reject()
                }
            },
            unpin = { pin -> store.update { it.copy(pinned = it.pinned - pin) } },
            openPackage = { packageName -> if (!launchPackage(context, packageName)) haptics.reject() },
            openNotifications = { if (!expandNotificationShade(context)) haptics.reject() }
        )
    }
    // The status bar is hidden while the home screen is shown and the setting is on (the guide, Settings and the other screens show it:
    // this leaves the composition with the home route). Android's immersive recipe for the status bar only, the navigation bar stays
    // (https://developer.android.com/develop/ui/views/layout/immersive): a swipe from the top edge shows it for a moment.
    val view = LocalView.current
    val hideBar = settings.hideStatusBar && settings.setupDone
    DisposableEffect(hideBar, view) {
        val window = view.context.findActivity()?.window
        if (window != null && hideBar) {
            val controller = WindowCompat.getInsetsController(window, view)
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.statusBars())
        }
        onDispose {
            if (window != null && hideBar) WindowCompat.getInsetsController(window, view).show(WindowInsetsCompat.Type.statusBars())
        }
    }
    // The pinned activities with their app's icon; one whose app is gone is not shown (it comes back with the app).
    val pinned = remember(state, settings.pinned) {
        val every = state.apps + state.others + state.hidden
        settings.pinned.mapNotNull { pin ->
            every.firstOrNull { it.packageName == pin.packageName && it.userSerial == pin.userSerial }?.let { PinnedRow(pin, it) }
        }
    }
    // A swipe left brings a panel in from the right edge, a swipe right from the left edge, a swipe up from the bottom.
    val layout = PanelLayout(left = settings.swipeRight, right = settings.swipeLeft, bottom = settings.swipeUp)
    HomeContent(
        state, settings.themeMode, actions, panelOpacity = settings.panelOpacity, iconShape = settings.iconShape, themedIcons = settings.themedIcons,
        edgeTabs = settings.edgeTabs, marks = AppMarks(dots, updates), pinned = pinned, layout = layout, homeSignal = homeSignal, homeAnimated = homeAnimated,
        screenOffSignal = screenOffSignal, swipeDown = settings.swipeDown, dateMode = settings.dateMode,
        appsTab = appsTab, onAppsTabShown = onAppsTabShown
    )
}

// ItemClickHandler.onClickAppShortcut and startAppShortcutOrInfoActivity, as they run for an app of the list
// (https://github.com/LineageOS/android_packages_apps_Trebuchet/blob/lineage-22.2/src/com/android/launcher3/touch/ItemClickHandler.java#L355-L399):
// [v] is the tapped cell, [launcher] the context; the toast of a failed start is ActivityContext.startActivitySafely's.
private fun onClickAppShortcut(v: LaunchSource?, shortcut: AppEntry, launcher: Context, haptics: Haptics) {
    if (shortcut.isDisabled() && AppActions.handleDisabledItemClicked(shortcut, launcher)) {
        return
    }
    // Start activities
    startAppShortcutOrInfoActivity(v, shortcut, launcher, haptics)
}

private fun startAppShortcutOrInfoActivity(v: LaunchSource?, item: AppEntry, launcher: Context, haptics: Haptics) {
    if (!AppActions.startActivitySafely(launcher, ComponentName(item.packageName, item.className), item.user, v)) haptics.reject()
}

/** Stateless, so a test draws it with fixed data; [initial] is the edge whose panel is open at the start. */
@Composable
fun HomeContent(
    state: LauncherState,
    theme: ThemeMode,
    actions: HomeActions,
    modifier: Modifier = Modifier,
    initial: Panel = Panel.NONE,
    /** How strongly the screen is dimmed behind the panels, in percent. */
    panelOpacity: Int = 54,
    /** The shape of the app icons, and whether they are themed. */
    iconShape: IconShape = IconShape.CIRCLE,
    themedIcons: Boolean = false,
    /** How strongly the tabs on the screen edges blur the wallpaper, in percent. */
    edgeTabs: Int = 30,
    /** The notification dots and the updates waiting in Store, drawn over the icons. */
    marks: AppMarks = AppMarks(),
    /** The activities pinned at the head of the Apps panel. */
    pinned: List<PinnedRow> = emptyList(),
    /** Which panel each edge holds. */
    layout: PanelLayout = PanelLayout(),
    /** Goes up each time Home is pressed while the app runs: the open panel closes (Trebuchet's Launcher.onNewIntent closes its floating views). */
    homeSignal: Int = 0,
    /** False when the system brought the home to the front: the panels then close at once (Trebuchet's alreadyOnHome). */
    homeAnimated: Boolean = true,
    /** Goes up each time the screen goes off: the panels close at once and the menus close (Trebuchet's Launcher.onScreenOnChanged); the routes stay. */
    screenOffSignal: Int = 0,
    /** What a swipe down on the wallpaper does. */
    swipeDown: SwipeDown = SwipeDown.NOTHING,
    /** The calendar of the header shown over the open panels. */
    dateMode: DateMode = DateMode.BOTH,
    /** An intent's ask for the Apps panel on a tab (Launcher.showAllAppsFromIntent), null when none, answered once by [onAppsTabShown]. */
    appsTab: Int? = null,
    onAppsTabShown: () -> Unit = {}
) {
    val scope = rememberCoroutineScope()
    val haptics = rememberHaptics()
    val density = LocalDensity.current
    // Trebuchet's release thresholds (see successProgress and flingVelocityPx in PanelDrag.kt): a fling is over 1 dp per ms; a tablet is
    // a smallest width of 600 dp (DeviceProfile.isTablet).
    val flingVelocity = flingVelocityPx(with(density) { 1.dp.toPx() })
    val tablet = LocalConfiguration.current.smallestScreenWidthDp >= 600
    val hasLeft = layout.left != PanelKind.NONE
    val hasRight = layout.right != PanelKind.NONE
    val hasBottom = layout.bottom != PanelKind.NONE
    // A panel starts open only on an edge that has one.
    val side = remember {
        AnchoredDraggableState(
            when {
                initial == Panel.LEFT && hasLeft -> Side.LEFT
                initial == Panel.RIGHT && hasRight -> Side.RIGHT
                else -> Side.CLOSED
            }
        )
    }
    val sheet = remember { AnchoredDraggableState(if (initial == Panel.BOTTOM && hasBottom) Sheet.OPEN else Sheet.CLOSED) }
    val seen = remember { mutableSetOf<String>() }
    // Goes up each time the Apps panel must show the work profile's tile (the work apps intent): the panel scrolls to it.
    var workScroll by remember { mutableIntStateOf(0) }
    // The header's measured height in px: the side panels and the Apps panel at the bottom start under it.
    var headerPx by remember { mutableIntStateOf(0) }
    // Read when a finger goes down (see onGestureDown below): the Apps grids were at the edge the panel closes towards. Whether it went down
    // outside the system's gesture band is not state: onControllerInterceptTouchEvent decides it once per gesture.
    var sheetStartAtTop by remember { mutableStateOf(true) }
    var sideStartAtEdge by remember { mutableStateOf(true) }
    // The apps grid's scroll, kept here: a side Apps panel follows the drag only while the grid is at its start (the right panel's grid runs
    // left to right, the left panel's is mirrored, so both close by a drag towards their edge at their first apps), and it starts again at the
    // start once the panel has closed. The bottom panel has its own vertical grid, closing by a drag down at its top (Trebuchet's
    // AllAppsSwipeController asks shouldContainerScroll first).
    val appsGrid = rememberLazyGridState()
    val bottomGrid = rememberLazyGridState()
    val gridAtStart by remember { derivedStateOf { !appsGrid.canScrollBackward } }
    val bottomGridAtTop by remember { derivedStateOf { !bottomGrid.canScrollBackward } }
    val sideAppsOpen = (side.settledValue == Side.LEFT && layout.left == PanelKind.APPS) ||
        (side.settledValue == Side.RIGHT && layout.right == PanelKind.APPS)
    val bottomAppsOpen = sheet.settledValue == Sheet.OPEN && layout.bottom == PanelKind.APPS
    LaunchedEffect(sideAppsOpen) { if (!sideAppsOpen) appsGrid.scrollToItem(0) }
    LaunchedEffect(bottomAppsOpen) { if (!bottomAppsOpen) bottomGrid.scrollToItem(0) }

    // A panel comes in and goes out on a spring, as the suite's panes pop.
    val spring = spring<Float>(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow)
    // Where a release ends: AbstractStateChangeTouchController.onDragEnd's rule, with the share of the travel that commits depending on whether
    // Apps is one end (https://github.com/LineageOS/android_packages_apps_Trebuchet/blob/lineage-22.2/src/com/android/launcher3/touch/AbstractStateChangeTouchController.java).
    val sideSuccess = remember(layout, tablet) {
        { from: Side, to: Side ->
            successProgress(
                tablet,
                fromApps = (from == Side.LEFT && layout.left == PanelKind.APPS) || (from == Side.RIGHT && layout.right == PanelKind.APPS),
                toApps = (to == Side.LEFT && layout.left == PanelKind.APPS) || (to == Side.RIGHT && layout.right == PanelKind.APPS)
            )
        }
    }
    val sheetSuccess = remember(layout, tablet) {
        { from: Sheet, to: Sheet ->
            successProgress(tablet, fromApps = from == Sheet.OPEN && layout.bottom == PanelKind.APPS, toApps = to == Sheet.OPEN && layout.bottom == PanelKind.APPS)
        }
    }
    // The release settles for BaseSwipeDetector.calculateDuration's duration (settleSpec in PanelDrag.kt); the spring stays for the taps on the
    // tabs, Back and the scrim, which have no finger velocity.
    val sideFling = remember(side, flingVelocity, sideSuccess) { releaseFlingBehavior(side, flingVelocity, sideSuccess) }
    val sheetFling = remember(sheet, flingVelocity, sheetSuccess) { releaseFlingBehavior(sheet, flingVelocity, sheetSuccess) }
    val close: () -> Unit = {
        scope.launch { side.animateTo(Side.CLOSED, spring) }
        scope.launch { sheet.animateTo(Sheet.CLOSED, spring) }
    }
    // Trebuchet's Launcher.onNewIntent: goToState(NORMAL, alreadyOnHome), the panel closes by a snap when it was not already on home
    // (https://github.com/LineageOS/android_packages_apps_Trebuchet/blob/lineage-22.2/src/com/android/launcher3/Launcher.java).
    LaunchedEffect(homeSignal) {
        if (homeSignal > 0) {
            if (homeAnimated) close() else {
                side.snapTo(Side.CLOSED)
                sheet.snapTo(Sheet.CLOSED)
            }
        }
    }
    // Launcher.onScreenOnChanged: "if (!isOn ...) mStateManager.goToState(NORMAL)", at once: nobody sees the panel close
    // (https://github.com/LineageOS/android_packages_apps_Trebuchet/blob/lineage-22.2/src/com/android/launcher3/Launcher.java#L2144-L2153).
    LaunchedEffect(screenOffSignal) {
        if (screenOffSignal > 0) {
            side.snapTo(Side.CLOSED)
            sheet.snapTo(Sheet.CLOSED)
        }
    }
    val anyOpen = side.targetValue != Side.CLOSED || sheet.targetValue != Sheet.CLOSED
    // The bars' icons follow the wallpaper, and the panel's theme while a panel is out.
    HomeSystemBars(hasScrim = anyOpen, isMainColorDark = MaterialTheme.colorScheme.background.luminance() < 0.5f)
    // Back on the home screen does nothing: Trebuchet's Launcher.onBackPressed hands Back to its callbacks, ends in the state handler and
    // never calls finish() (https://github.com/LineageOS/android_packages_apps_Trebuchet/blob/lineage-22.2/src/com/android/launcher3/Launcher.java).
    // Handlers declared later win, so this one, always on, is declared first and the panel's below takes Back while a panel is open.
    // (androidx.activity OnBackPressedDispatcher: https://developer.android.com/guide/navigation/custom-back/handle-system-back)
    BackHandler(enabled = true) { }
    // Back closes the open panel.
    BackHandler(enabled = anyOpen, onBack = close)
    // The order is fixed while a panel opens or is open, as Trebuchet fixes the apps list at bind (AlphabeticalAppsList.updateItemFilter and
    // onAppsUpdated build the adapter items once per model change, never as the drawer moves,
    // https://github.com/LineageOS/android_packages_apps_Trebuchet/blob/lineage-22.2/src/com/android/launcher3/allapps/AlphabeticalAppsList.java):
    // the usage is read again, and the apps and Recents ranked anew, only once every panel is back at rest, closed.
    val refresh by rememberUpdatedState(actions.refresh)
    LaunchedEffect(side, sheet) {
        snapshotFlow {
            kotlin.math.abs(side.offsetOrZero()) < 0.5f && kotlin.math.abs(sheet.offsetOrZero()) < 0.5f &&
                side.targetValue == Side.CLOSED && sheet.targetValue == Sheet.CLOSED
        }.distinctUntilChanged().drop(1).filter { it }.collect { refresh() }
    }
    LaunchedEffect(side.settledValue, sheet.settledValue) {
        if (side.settledValue != Side.CLOSED || sheet.settledValue != Sheet.CLOSED) haptics.tick()
    }

    // The panels shut when an app starts, App info opens or a shortcut starts.
    val panelActions = remember(actions) {
        actions.copy(
            launch = { actions.launch(it); close() },
            launchFrom = { entry, source -> actions.launchFrom(entry, source); close() },
            appInfo = { actions.appInfo(it); close() },
            startShortcut = { actions.startShortcut(it); close() },
            openStore = { actions.openStore(); close() },
            openActivities = { actions.openActivities(it); close() },
            launchPinned = { pin, source -> actions.launchPinned(pin, source); close() },
            openSettings = { actions.openSettings(); close() },
            openPackage = { actions.openPackage(it); close() }
        )
    }

    // The wallpaper blurs as far as a panel is open: the largest of the three edges' fractions of their travel (Trebuchet's DepthController
    // drives the blur from its state progress, https://github.com/LineageOS/android_packages_apps_Trebuchet/blob/lineage-22.2/quickstep/src/com/android/launcher3/statehandlers/DepthController.java;
    // here the AnchoredDraggableState offsets are the progress). The same progress sets the scrim behind the panels, as the Quick Settings shade's
    // (SystemUI's ScrimController: a full-screen fill of surfaceDim, both by day and by night, whose alpha follows
    // ShadeInterpolation.getNotificationScrimAlpha(expansion),
    // https://github.com/LineageOS/android_frameworks_base/blob/lineage-22.2/packages/SystemUI/src/com/android/systemui/statusbar/phone/ScrimController.java,
    // updateThemeColors). While the system does not blur windows, the scrim is fully opaque, as ScrimController.getDefaultScrimAlpha gives
    // BUSY_SCRIM_ALPHA (1) without blur and TRANSPARENT_BOUNCER_SCRIM_ALPHA (0.54) with it
    // (https://github.com/LineageOS/android_frameworks_base/blob/lineage-23.0/packages/SystemUI/src/com/android/systemui/statusbar/phone/ScrimController.java),
    // so the content stays readable without a pane.
    val leftProgress: () -> Float = {
        val offset = side.offsetOrZero()
        if (offset > 0f) fractionOf(offset, side.anchors.positionOf(Side.LEFT)) else 0f
    }
    val rightProgress: () -> Float = {
        val offset = side.offsetOrZero()
        if (offset < 0f) fractionOf(offset, side.anchors.positionOf(Side.RIGHT)) else 0f
    }
    val bottomProgress: () -> Float = { fractionOf(sheet.offsetOrZero(), sheet.anchors.positionOf(Sheet.OPEN)) }
    val progress: () -> Float = { maxOf(leftProgress(), rightProgress(), bottomProgress()) }
    val blurOn = rememberWindowBlur(progress)
    val chosen = panelOpacity.coerceIn(0, 100) / 100f
    val dim = if (blurOn) chosen else maxOf(chosen, MIN_SCRIM_STRENGTH)
    val scrim = MaterialTheme.colorScheme.surfaceDim

    // The menu of a long press on the wallpaper: Wallpaper and Settings, as Trebuchet's OptionsPopupView.getOptions lists them.
    val wallpaperMenu = rememberPillMenu()
    // Launcher.showAllAppsWithSelectedTabFromIntent(alreadyOnHome, tab), called by onNewIntent for ACTION_ALL_APPS (showAllAppsFromIntent, tab
    // MAIN) and ACTION_SHOW_WORK_APPS (tab WORK): AbstractFloatingView.closeAllOpenViews(this), getStateManager().goToState(ALL_APPS, alreadyOnHome),
    // mAppsView.switchToTab(tab), in that order. ALL_APPS is the panel that holds Apps (the right one first, then the left, then the bottom
    // one), the others closed, animated only when already on home; the WORK tab is the work profile's tile, scrolled to by the Apps panel.
    // (https://github.com/LineageOS/android_packages_apps_Trebuchet/blob/lineage-22.2/src/com/android/launcher3/Launcher.java#showAllAppsFromIntent)
    LaunchedEffect(appsTab) {
        val tab = appsTab ?: return@LaunchedEffect
        wallpaperMenu.close()
        repeat(2) { withFrameNanos { } } // the anchors are set by the layout
        val allApps = listOf(Panel.RIGHT, Panel.LEFT, Panel.BOTTOM).firstOrNull { layout.at(it) == PanelKind.APPS }
        val alreadyOnHome = homeAnimated
        val toSide = if (allApps == Panel.LEFT) Side.LEFT else if (allApps == Panel.RIGHT) Side.RIGHT else Side.CLOSED
        val toSheet = if (allApps == Panel.BOTTOM) Sheet.OPEN else Sheet.CLOSED
        if (tab == AdapterHolder.WORK) workScroll++
        scope.launch { if (alreadyOnHome) side.animateTo(toSide, spring) else side.snapTo(toSide) }
        scope.launch { if (alreadyOnHome) sheet.animateTo(toSheet, spring) else sheet.snapTo(toSheet) }
        onAppsTabShown()
    }
    // Launcher.onNewIntent: closeAllOpenViewsExcept, the menu of a long press on the wallpaper closes when Home is pressed (or the screen turns off).
    LaunchedEffect(homeSignal, screenOffSignal) {
        if (homeSignal > 0 || screenOffSignal > 0) wallpaperMenu.close()
    }
    val wallpaperItems = listOf(
        PillItem(SettingsIcons.Palette, stringResource(R.string.menu_wallpaper), PillMotion.BOUNCE) { actions.openWallpaper() },
        PillItem(AppIcons.Settings, stringResource(R.string.menu_settings), PillMotion.WIGGLE) { actions.openSettings() }
    )
    val homeOptions = stringResource(R.string.home_options)
    val rootView = LocalView.current
    // The swipe down for the shade, null when off; the edge tabs' windows read it too (they are windows of their own).
    val shadeSwipe: (() -> Unit)? = if (swipeDown == SwipeDown.NOTIFICATIONS) actions.openNotifications else null

    CompositionLocalProvider(
        LocalContentColor provides MaterialTheme.colorScheme.onSurface,
        LocalIconStyle provides IconStyle(iconShape, themedIcons),
        LocalAppMarks provides marks,
        LocalHomeSignal provides homeSignal,
        LocalScreenOffSignal provides screenOffSignal
    ) {
    BoxWithConstraints(modifier.fillMaxSize()) {
        val margin = 12.dp
        // The panels start under the header, which holds the system bars' and the cut-out's room itself; before it is measured, the safe top
        // (the status bar when shown, else the cut-out) and a margin.
        val safeTop = WindowInsets.systemBars.union(WindowInsets.displayCutout).asPaddingValues().calculateTopPadding()
        val top = if (headerPx > 0) with(density) { headerPx.toDp() } else safeTop + margin
        val bottom = navigationBarBottom() + margin
        // The side panels start from their screen edge (no margin on that side, the other keeps one on a phone); their travel is their width,
        // which takes them fully off the screen. The bottom panel keeps its bottom margin; Apps and Recents both take the height from under
        // the header to the navigation bar's margin, as the Pixel Launcher's drawer fills the screen, and the screen's width.
        val sideWidth = (maxWidth - margin).coerceAtMost(420.dp)
        val sideTravel = with(density) { sideWidth.toPx() }
        val appsHeight = (maxHeight - top - bottom).coerceAtLeast(0.dp)
        // ActivityAllAppsContainerView.setInsets: the container takes no bottom padding (setPadding(left, top, right, 0)) and reaches the screen's
        // bottom; the window's bottom inset is the list's own padding (applyAdapterSideAndBottomPaddings, done in AppsPanel). So the bottom Apps
        // panel is as high as the screen under the header, with no bottom margin; Recents keeps appsHeight above.
        // (https://github.com/LineageOS/android_packages_apps_Trebuchet/blob/lineage-22.2/src/com/android/launcher3/allapps/ActivityAllAppsContainerView.java#setInsets)
        // not ported: setInsets' left and right margins (insets.left, insets.right; the panel is screen wide and the grid draws under the side bars),
        // grid.allAppsLeftRightMargin and the top padding (the header holds the top), InsettableFrameLayout.dispatchInsets (no child view to inset).
        val bottomAppsHeight = (maxHeight - top).coerceAtLeast(0.dp)
        val leftVisible by remember { derivedStateOf { side.offsetOrZero() > 0.5f } }
        val rightVisible by remember { derivedStateOf { side.offsetOrZero() < -0.5f } }
        val bottomVisible by remember { derivedStateOf { sheet.offsetOrZero() < -0.5f } }
        // The bottom panel's travel does not change while a panel is out: Trebuchet's AllAppsTransitionController gives its shift range once
        // (setShiftRange, a constant of the device profile, never a size that moves with the content,
        // https://github.com/LineageOS/android_packages_apps_Trebuchet/blob/lineage-22.2/src/com/android/launcher3/allapps/AllAppsTransitionController.java).
        // Here the header grows as a panel comes out (its clock, capsules and alarm appear): Modifier.anchoredDraggable's state, given new anchors
        // while the finger drags it, cannot snap and keeps a target of the old anchors for good (AnchoredDraggableState.updateAnchors sets
        // dragTarget, which only an animateTo clears, https://github.com/androidx/androidx/blob/androidx-main/compose/foundation/foundation/src/commonMain/kotlin/androidx/compose/foundation/gestures/AnchoredDraggable.kt):
        // isAnimationRunning then stays true, so the next touch starts a drag at once (a tap on an icon is lost, a long press on the wallpaper
        // never comes) and targetValue says Closed on an open panel. So the header height that sets the travel is the one read while no
        // bottom panel is out; the panel's own height follows the live header.
        val sheetOut by remember { derivedStateOf { sheet.offsetOrZero() < -0.5f || sheet.targetValue != Sheet.CLOSED } }
        val travelHeader = remember { IntArray(1) }
        if (!sheetOut) travelHeader[0] = headerPx
        val travelTop = if (travelHeader[0] > 0) with(density) { travelHeader[0].toDp() } else safeTop + margin
        val bottomTravel = with(density) { (maxHeight - travelTop).toPx() }
        // A side panel with the Apps grid open takes the drag only at the grid's edge on the closing side (the grid takes the swipe elsewhere).
        // The grid's place is the one it had when the finger went down (sideStartAtEdge, set below), not the live one, as Trebuchet's
        // AllAppsSwipeController decides at the touch's start: a drag that began in the middle of the grid never closes the panel.
        val sideDrag = when (side.targetValue) {
            Side.LEFT -> layout.left != PanelKind.APPS || sideStartAtEdge
            Side.RIGHT -> layout.right != PanelKind.APPS || sideStartAtEdge
            Side.CLOSED -> true
        }
        // The same for the bottom panel: its vertical grid takes the swipe except at its top.
        val sheetDrag = when (sheet.targetValue) {
            Sheet.OPEN -> layout.bottom != PanelKind.APPS || sheetStartAtTop
            Sheet.CLOSED -> true
        }
        // The bottom edge stays Android's: the home and quick-switch gestures start in the navigation bar and the mandatory system gesture inset,
        // so a drag that went down there never moves the sheet (the guide's "Conflicts with home or quick-switch gestures",
        // https://developer.android.com/develop/ui/views/touch-and-input/gestures/gesturenav, which reads the mandatory system gesture insets).
        val gestureInsets = WindowInsets.mandatorySystemGestures
        val bottomBand = WindowInsets.mandatorySystemGestures.union(WindowInsets.navigationBars)
        val direction = LocalLayoutDirection.current

        Box(
            Modifier
                .fillMaxSize()
                // The anchors are set when the layout is measured, from the panels' sizes (the recipe's Modifier.layout way).
                .layout { measurable, constraints ->
                    val placeable = measurable.measure(constraints)
                    // The target is kept when the anchors move (Material 3's ModalBottomSheet passes its target the same way), or a panel
                    // whose size changes would fall back to the anchor nearest its old place.
                    side.updateAnchors(
                        DraggableAnchors {
                            if (hasLeft) Side.LEFT at sideTravel
                            Side.CLOSED at 0f
                            if (hasRight) Side.RIGHT at -sideTravel
                        },
                        side.targetValue
                    )
                    sheet.updateAnchors(
                        DraggableAnchors {
                            Sheet.CLOSED at 0f
                            if (hasBottom) Sheet.OPEN at -bottomTravel
                        },
                        sheet.targetValue
                    )
                    layout(placeable.width, placeable.height) { placeable.place(0, 0) }
                }
                // A swipe down opens the notification shade when no panel is open or opening (it watches the touches and consumes none).
                .onSwipeDown(
                    enabled = swipeDown == SwipeDown.NOTIFICATIONS,
                    idle = {
                        side.offsetOrZero() == 0f && sheet.offsetOrZero() == 0f &&
                            side.targetValue == Side.CLOSED && sheet.targetValue == Sheet.CLOSED && !wallpaperMenu.shown
                    },
                    onSwipeDown = actions.openNotifications
                )
                // What is decided when a finger goes down, before the drags see it: whether it went down in the system's gesture band (refused once
                // per gesture, as Trebuchet's AbstractStateChangeTouchController does at ACTION_DOWN, mNoIntercept = !canInterceptTouch(ev); the
                // draggables below keep their `enabled` to the panels' state, so a gesture that follows another at once finds them ready for its
                // down), and where the Apps grids were (nothing consumed).
                // A drag along neither axis starts no panel (SingleAxisSwipeDetector.shouldScrollStart), while nothing is open.
                .singleAxisSwipeGate { side.targetValue == Side.CLOSED && sheet.targetValue == Sheet.CLOSED }
                .onControllerInterceptTouchEvent { position, size, mDir ->
                    // AllAppsSwipeController.canInterceptTouch: the vertical panel is refused in the bottom band, the horizontal ones in a side band.
                    when (mDir) {
                        Direction.VERTICAL -> position.y < size.height - bottomBand.getBottom(density)
                        Direction.HORIZONTAL ->
                            position.x >= gestureInsets.getLeft(density, direction) && position.x <= size.width - gestureInsets.getRight(density, direction)
                    }
                }
                .onGestureDown { _, _ ->
                    sheetStartAtTop = bottomGridAtTop
                    sideStartAtEdge = gridAtStart
                }
                // A swipe left or right brings in the panel of that edge, a swipe up the bottom one. Only one panel at a time.
                .anchoredDraggable(side, Orientation.Horizontal, enabled = sheet.targetValue == Sheet.CLOSED && sideDrag, flingBehavior = sideFling)
                .anchoredDraggable(sheet, Orientation.Vertical, enabled = side.targetValue == Side.CLOSED && sheetDrag, flingBehavior = sheetFling)
        ) {
            // The scrim, over the wallpaper and under the panels, drawn with the progress read in the draw phase; a tap on it closes the open panel.
            // With no panel open it is the wallpaper itself: a long press opens the Wallpaper and Settings menu (Compose's detectTapGestures,
            // https://developer.android.com/develop/ui/compose/touch-input/pointer-input/tap-and-press, which leaves a drag to the panels'
            // draggable; the semantics action gives TalkBack the same long press).
            Box(
                Modifier
                    .fillMaxSize()
                    .drawBehind { drawRect(scrim, alpha = (dim * ShadeInterpolation.getNotificationScrimAlpha(progress())).coerceIn(0f, 1f)) }
                    .then(
                        if (anyOpen) {
                            Modifier.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = close)
                        } else {
                            Modifier
                                .then(wallpaperMenu.tracker)
                                // WorkspaceTouchListener, set on the workspace by Workspace (the wallpaper's touches):
                                // https://github.com/LineageOS/android_packages_apps_Trebuchet/blob/lineage-22.2/src/com/android/launcher3/touch/WorkspaceTouchListener.java#onTouch
                                // canHandleLongPress: AbstractFloatingView.getTopOpenView(mLauncher) == null && mLauncher.isInState(NORMAL).
                                // edgeMarginPx: DeviceProfile's R.dimen.dynamic_grid_edge_margin, 10.77dp.
                                // Workspace.onWallpaperTap sends the tap to the wallpaper at its place on the screen, so a live wallpaper reacts:
                                // https://github.com/LineageOS/android_packages_apps_Trebuchet/blob/lineage-22.2/src/com/android/launcher3/Workspace.java#onWallpaperTap
                                .workspaceTouchListener(
                                    canHandleLongPress = { !wallpaperMenu.shown && side.targetValue == Side.CLOSED && sheet.targetValue == Sheet.CLOSED },
                                    insets = WindowInsets.systemBars.union(WindowInsets.displayCutout),
                                    edgeMarginPx = with(density) { 10.77.dp.toPx() },
                                    direction = direction,
                                    showDefaultOptions = { wallpaperMenu.open() },
                                    onWallpaperTap = { position, secondary ->
                                        val position0 = IntArray(2).also { rootView.getLocationOnScreen(it) }
                                        runCatching {
                                            WallpaperManager.getInstance(rootView.context).sendWallpaperCommand(
                                                rootView.windowToken,
                                                if (secondary) WallpaperManager.COMMAND_SECONDARY_TAP else WallpaperManager.COMMAND_TAP,
                                                position0[0] + position.x.toInt(), position0[1] + position.y.toInt(), 0, null
                                            )
                                        }
                                    }
                                )
                                .semantics { onLongClick(label = homeOptions) { wallpaperMenu.open(); true } }
                        }
                    )
            ) {
                PillMenu(wallpaperMenu, wallpaperItems)
            }

            // The header, over the scrim and under the panels, in place of the status bar: it shows as far as a panel is open, with the panels'
            // content alpha, and is hidden from accessibility otherwise (it is always composed so that its height is known; it reads nothing
            // and answers no tap while no panel is open).
            val headerShown = leftVisible || rightVisible || bottomVisible || anyOpen
            PanelHeader(
                shown = headerShown,
                dateMode = dateMode,
                onOpen = panelActions.openPackage,
                onSettings = panelActions.openSettings,
                // Shut after a launch from the header, as panelActions wraps openPackage and openSettings.
                onLaunched = { close() },
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .onSizeChanged { headerPx = it.height }
                    .graphicsLayer { alpha = ShadeInterpolation.getContentAlpha(progress()) }
                    .then(if (headerShown) Modifier else Modifier.clearAndSetSemantics { })
            )

            // The panels stay composed all the time, as Trebuchet keeps its apps view in the hierarchy and only moves it
            // (AllAppsTransitionController.setProgress sets getAppsViewProgressTranslationY to progress * shiftRange, and its MultiValueAlpha with
            // setUpdateVisibility hides the view at alpha 0,
            // https://github.com/LineageOS/android_packages_apps_Trebuchet/blob/lineage-22.2/src/com/android/launcher3/allapps/AllAppsTransitionController.java),
            // so a swipe never composes a panel. Closed, a panel sits fully off the screen (its travel is its size, so no touch reaches it),
            // draws nothing (alpha 0 from the shade's content alpha) and is hidden from accessibility services.
            if (hasLeft) {
                SidePanel(
                    layout.left, Panel.LEFT, state, pinned, panelActions, seen, appsGrid, sideWidth, top, bottom,
                    progress = leftProgress, travelX = { -sideTravel + side.offsetOrZero() },
                    close = remember(side, flingVelocity, sideSuccess) { side.closeWithScroll(Side.LEFT, flingVelocity, sideSuccess) { gridAtStart } },
                    open = leftVisible || side.targetValue == Side.LEFT,
                    workScroll = workScroll
                )
            }
            if (hasRight) {
                SidePanel(
                    layout.right, Panel.RIGHT, state, pinned, panelActions, seen, appsGrid, sideWidth, top, bottom,
                    progress = rightProgress, travelX = { sideTravel + side.offsetOrZero() },
                    close = remember(side, flingVelocity, sideSuccess) { side.closeWithScroll(Side.RIGHT, flingVelocity, sideSuccess) { gridAtStart } },
                    open = rightVisible || side.targetValue == Side.RIGHT,
                    workScroll = workScroll
                )
            }
            val bottomOpen = bottomVisible || sheet.targetValue == Sheet.OPEN
            when (layout.bottom) {
                // Recents at the bottom is a full page too, the same size and travel as Apps.
                PanelKind.RECENTS -> RecentsPanel(
                    state,
                    panelActions,
                    seen,
                    progress = bottomProgress,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = bottom)
                        .fillMaxWidth()
                        .height(appsHeight)
                        .graphicsLayer { translationY = bottomTravel + sheet.offsetOrZero() }
                        .then(if (bottomOpen) Modifier else Modifier.clearAndSetSemantics { })
                )
                // Apps at the bottom is screen wide and as high as the screen under the header, its travel the same.
                PanelKind.APPS -> AppsPanel(
                    state, pinned, panelActions, seen, bottomGrid,
                    edge = Panel.BOTTOM,
                    progress = bottomProgress,
                    close = remember(sheet, flingVelocity, sheetSuccess) { sheet.closeWithScrollDown(flingVelocity, sheetSuccess) { bottomGridAtTop } },
                    workScroll = workScroll,
                    // No bottom padding (ActivityAllAppsContainerView#setInsets): the panel ends at the screen's bottom; the travel is unchanged, the
                    // panel's top still at the header and its height now reaching the screen's bottom, so bottomTravel takes it fully off.
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(bottomAppsHeight)
                        .graphicsLayer { translationY = bottomTravel + sheet.offsetOrZero() }
                        .then(if (bottomOpen) Modifier else Modifier.clearAndSetSemantics { })
                )
                PanelKind.NONE -> Unit
            }

            // The tabs on the side edges that have a panel, hidden while a panel is open; the tab opens its panel. The bottom has none: the swipe up.
            if (hasLeft) {
                AnimatedVisibility(
                    visible = !anyOpen,
                    enter = fadeIn() + scaleIn(spring(dampingRatio = 0.6f, stiffness = 420f), initialScale = 0.6f),
                    exit = fadeOut() + scaleOut(targetScale = 0.8f),
                    modifier = Modifier.align(Alignment.CenterStart)
                ) {
                    EdgeEntry(panelName(layout.left), start = true, visibility = edgeTabs, windowShown = !anyOpen, onSwipeDown = shadeSwipe) {
                        scope.launch { side.animateTo(Side.LEFT, spring) }
                    }
                }
            }
            if (hasRight) {
                AnimatedVisibility(
                    visible = !anyOpen,
                    enter = fadeIn() + scaleIn(spring(dampingRatio = 0.6f, stiffness = 420f), initialScale = 0.6f),
                    exit = fadeOut() + scaleOut(targetScale = 0.8f),
                    modifier = Modifier.align(Alignment.CenterEnd)
                ) {
                    EdgeEntry(panelName(layout.right), start = false, visibility = edgeTabs, windowShown = !anyOpen, onSwipeDown = shadeSwipe) {
                        scope.launch { side.animateTo(Side.RIGHT, spring) }
                    }
                }
            }
        }
    }
    }
}

// A panel on the left or right edge, the one its kind says: Apps (three columns, closing with the grid's leftover drag) or Recents (the side's
// width, centred vertically). It comes in from its edge by [travelX], the state's offset. The sides follow Trebuchet's drawer, which fills the
// height (AllAppsContainerView, https://github.com/LineageOS/android_packages_apps_Trebuchet/blob/lineage-22.2/src/com/android/launcher3/allapps/ActivityAllAppsContainerView.java).
@Composable
private fun BoxScope.SidePanel(
    kind: PanelKind,
    edge: Panel,
    state: LauncherState,
    pinned: List<PinnedRow>,
    actions: HomeActions,
    seen: MutableSet<String>,
    grid: LazyGridState,
    width: Dp,
    top: Dp,
    bottom: Dp,
    progress: () -> Float,
    travelX: () -> Float,
    close: androidx.compose.ui.input.nestedscroll.NestedScrollConnection,
    open: Boolean,
    workScroll: Int
) {
    val left = edge == Panel.LEFT
    // Closed, the panel stays composed off the screen and is hidden from accessibility services.
    val semantics = if (open) Modifier else Modifier.clearAndSetSemantics { }
    when (kind) {
        PanelKind.APPS -> AppsPanel(
            state, pinned, actions, seen, grid,
            edge = edge,
            progress = progress,
            modifier = Modifier
                .align(if (left) Alignment.TopStart else Alignment.TopEnd)
                .padding(top = top, bottom = bottom)
                .width(width)
                .fillMaxHeight()
                .graphicsLayer { translationX = travelX() }
                .then(semantics),
            close = close,
            workScroll = workScroll
        )
        PanelKind.RECENTS -> RecentsPanel(
            state, actions, seen,
            progress = progress,
            modifier = Modifier
                .align(if (left) Alignment.TopStart else Alignment.TopEnd)
                .padding(top = top, bottom = bottom)
                .width(width)
                .fillMaxHeight()
                .graphicsLayer { translationX = travelX() }
                .then(semantics)
        )
        PanelKind.NONE -> Unit
    }
}

/** The name of a panel, the description of its edge tab. */
@Composable
private fun panelName(kind: PanelKind): String = stringResource(if (kind == PanelKind.RECENTS) R.string.entry_recents else R.string.entry_apps)

/**
 * The least strength of the scrim while the system does not blur windows: SystemUI's BUSY_SCRIM_ALPHA, 1
 * (https://github.com/LineageOS/android_frameworks_base/blob/lineage-23.0/packages/SystemUI/src/com/android/systemui/statusbar/phone/ScrimController.java).
 */
private const val MIN_SCRIM_STRENGTH = 1f

// The part of the way to [end] that [offset] has covered, 0 to 1; 0 while the anchors are not set (NaN) or the end is the rest place.
// Trebuchet's DepthController derives the blur from the state's progress the same way:
// https://github.com/LineageOS/android_packages_apps_Trebuchet/blob/lineage-22.2/quickstep/src/com/android/launcher3/statehandlers/DepthController.java
private fun fractionOf(offset: Float, end: Float): Float = if (end.isNaN() || end == 0f) 0f else (offset / end).coerceIn(0f, 1f)

// WorkspaceTouchListener's long press states: STATE_PENDING_PARENT_INFORM is the one between the long press and the next event, which tells the
// workspace to drop its touch; then STATE_COMPLETED, and every later event is eaten.
private const val STATE_CANCELLED = 0
private const val STATE_REQUESTED = 1
private const val STATE_PENDING_PARENT_INFORM = 2
private const val STATE_COMPLETED = 3

// TouchUtil.isMouseRightClickDownOrMove: the event comes from the mouse and its secondary (right) button is down.
// (https://github.com/LineageOS/android_packages_apps_Trebuchet/blob/lineage-22.2/src/com/android/launcher3/util/TouchUtil.java#isMouseRightClickDownOrMove)
private fun isMouseRightClickDownOrMove(ev: PointerEvent): Boolean =
    ev.changes.first().type == PointerType.Mouse && ev.buttons.isSecondaryPressed

// CheckLongPressHelper.isStylusButtonPressed: a stylus with its button pressed.
// (https://github.com/LineageOS/android_packages_apps_Trebuchet/blob/lineage-22.2/src/com/android/launcher3/CheckLongPressHelper.java#isStylusButtonPressed)
private fun isStylusButtonPressed(ev: PointerEvent): Boolean =
    ev.changes.first().type == PointerType.Stylus && ev.buttons.isSecondaryPressed

// The touch listener of the bare wallpaper, WorkspaceTouchListener.onTouch: a long press (the system's timeout, GestureDetector.onLongPress) shows the
// default options at the touch point, only inside the window less the system insets and the edge margin (mTempRect.inset(edgeMarginPx)); it is
// cancelled once the finger moves past twice the touch slop, or when another gesture took the touch (mWorkspace.isHandlingTouch); a mouse right
// click (TouchUtil.isMouseRightClickDownOrMove) or a stylus with its button down (CheckLongPressHelper.isStylusButtonPressed) shows them at once.
// The up of a touch nobody took is Workspace.onWallpaperTap: COMMAND_TAP for the first finger, COMMAND_SECONDARY_TAP for another one (the callback
// sends it to the wallpaper). The shape of the gesture loop is detectTapGestures' (Compose's tap recipe, waitForLongPress and consumeUntilUp).
// (https://github.com/LineageOS/android_packages_apps_Trebuchet/blob/lineage-22.2/src/com/android/launcher3/touch/WorkspaceTouchListener.java#onTouch)
// (https://github.com/LineageOS/android_packages_apps_Trebuchet/blob/lineage-22.2/src/com/android/launcher3/Workspace.java#onWallpaperTap)
// (https://github.com/androidx/androidx/blob/androidx-main/compose/foundation/foundation/src/commonMain/kotlin/androidx/compose/foundation/gestures/TapGestureDetector.kt#detectTapGestures)
private fun Modifier.workspaceTouchListener(
    canHandleLongPress: () -> Boolean,
    insets: WindowInsets,
    edgeMarginPx: Float,
    direction: LayoutDirection,
    showDefaultOptions: () -> Unit,
    onWallpaperTap: (Offset, Boolean) -> Unit
): Modifier = pointerInput(Unit) {
    // Use twice the touch slop as we are looking for long press which is more likely to cause movement.
    val mTouchSlop = 2 * viewConfiguration.touchSlop
    awaitEachGesture {
        val ev = awaitFirstDown(requireUnconsumed = false)
        ev.consume()
        var mLongPressState = STATE_CANCELLED
        val mTouchDownPoint = ev.position
        fun cancelLongPress() {
            mLongPressState = STATE_CANCELLED
        }
        fun maybeShowMenu() {
            if (mLongPressState == STATE_REQUESTED) {
                if (canHandleLongPress()) {
                    mLongPressState = STATE_PENDING_PARENT_INFORM
                    showDefaultOptions()
                } else {
                    cancelLongPress()
                }
            }
        }
        // Check if we can handle long press, and if the event is not near the edges.
        var handleLongPress = canHandleLongPress()
        if (handleLongPress) {
            val mTempRect = Rect(
                insets.getLeft(this, direction).toFloat(), insets.getTop(this).toFloat(),
                (size.width - insets.getRight(this, direction)).toFloat(), (size.height - insets.getBottom(this)).toFloat()
            ).deflate(edgeMarginPx)
            handleLongPress = mTempRect.contains(mTouchDownPoint)
        }
        if (handleLongPress) {
            mLongPressState = STATE_REQUESTED
            // Mouse right button's ACTION_DOWN should immediately show menu
            if (isMouseRightClickDownOrMove(currentEvent) || isStylusButtonPressed(currentEvent)) maybeShowMenu()
        }
        var lastUptime = ev.uptimeMillis
        while (true) {
            val event = if (mLongPressState == STATE_REQUESTED) {
                withTimeoutOrNull(ev.uptimeMillis + viewConfiguration.longPressTimeoutMillis - lastUptime) { awaitPointerEvent() }
            } else {
                awaitPointerEvent()
            }
            if (event == null) {
                // GestureDetector.onLongPress
                maybeShowMenu()
                continue
            }
            lastUptime = event.changes.first().uptimeMillis
            val action = event.changes.firstOrNull { it.id == ev.id } ?: break
            event.changes.firstOrNull { it.id != ev.id && it.changedToUp() }?.let { onWallpaperTap(it.position, true) }
            if (mLongPressState == STATE_PENDING_PARENT_INFORM || mLongPressState == STATE_COMPLETED) {
                // We have handled the touch, so nobody needs to know anything anymore.
                mLongPressState = STATE_COMPLETED
                event.changes.forEach { it.consume() }
                if (!action.pressed) break
                continue
            }
            if (action.isConsumed) {
                cancelLongPress()
                break
            }
            if (mLongPressState == STATE_REQUESTED) {
                if ((action.position - mTouchDownPoint).getDistance() > mTouchSlop) cancelLongPress()
                else if (isStylusButtonPressed(event)) maybeShowMenu()
            }
            if (action.changedToUp()) {
                onWallpaperTap(action.position, false)
                break
            }
            if (!action.pressed) break
        }
    }
}

// An entry on the left or right edge: a transparent tap target 48 dp wide (the Core App Quality touch target, reaching inwards from the edge)
// with the entry's description, so TalkBack finds it. The tab itself, 12 dp by 64 dp, is a window of its own that blurs the wallpaper
// (ui/component/EdgeTabWindow.kt's EdgeTab, like Samsung Edge panels' handle), shown while no panel is open ([windowShown]).
@Composable
private fun EdgeEntry(label: String, start: Boolean, visibility: Int, windowShown: Boolean, onSwipeDown: (() -> Unit)?, onClick: () -> Unit) {
    Box(
        Modifier
            .size(width = 48.dp, height = 96.dp)
            .sinkClickable(label, onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = label },
        contentAlignment = if (start) Alignment.CenterStart else Alignment.CenterEnd
    ) {
        EdgeTab(start, visibility, label, windowShown, onClick, onSwipeDown)
    }
}
