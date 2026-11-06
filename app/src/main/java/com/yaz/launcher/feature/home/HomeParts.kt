package com.yaz.launcher.feature.home

// The pieces the panels share.
// - AppIcon: an app's icon in a pane of glass of the chosen shape (the pane as the base kit's GlassIcon, a ZoneSurface), the layers made into
//   bitmaps at the size shown as Store's AppIcon does (YAZ/Store at HEAD, feature/home/AppIcon.kt), cut to the shape, themed if asked.
// - GridRows: rows of equal columns, each slot of equal weight, so a short last row keeps the columns; Compose's Row and Modifier.weight
//   (https://developer.android.com/develop/ui/compose/layouts/basics). The Recents panel is made of it.
// - HomeActions: what the panels ask of the screen; the stateful HomeScreen answers with AppActions and AppShortcuts.
import android.content.pm.ShortcutInfo
import android.graphics.Rect
import android.view.View
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toAndroidRect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionOnScreen
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import com.yaz.launcher.LocalSystemUiController
import com.yaz.launcher.LocalWallpaperColorHints
import com.yaz.launcher.R
import com.yaz.launcher.SystemUiController
import com.yaz.launcher.data.apps.AppActions
import com.yaz.launcher.data.apps.AppEntry
import com.yaz.launcher.supportsDarkText
import com.yaz.launcher.data.apps.LaunchSource
import com.yaz.launcher.data.apps.ProfileInfo
import com.yaz.launcher.data.settings.IconShape
import com.yaz.launcher.data.settings.PinnedActivity
import com.yaz.launcher.ui.component.PillMenuState
import com.yaz.launcher.ui.component.ZoneSurface
import com.yaz.launcher.ui.icon.IconCache
import com.yaz.launcher.ui.icon.IconKey
import com.yaz.launcher.ui.icon.toComposeShape

/** How the app icons are drawn: the shape the user chose, and whether the icons with a monochrome layer take the theme's colours. */
data class IconStyle(val shape: IconShape = IconShape.CIRCLE, val themed: Boolean = false)

/** Fed from the settings by HomeContent; the panels' [AppIcon] read it. */
val LocalIconStyle = compositionLocalOf { IconStyle() }

/**
 * The icon of [entry], [size] wide, in a pane of glass of the chosen shape. The two layers of the icon are cut to the shape (the
 * pictures are made at the size shown, kept by IconCache and built off the main thread); themed, the monochrome picture is tinted with onPrimaryContainer on a
 * primaryContainer ground, as Launcher3's ThemedIconDrawable draws it in its themed_icon_color over themed_icon_background_color
 * (https://github.com/LineageOS/android_frameworks_libs_systemui/blob/lineage-22.2/iconloaderlib/src/com/android/launcher3/icons/mono/ThemedIconDrawable.kt,
 * SRC_IN colour filters on the mono bitmap and the background); the Material You roles stand for the two fixed colours. An icon without
 * a monochrome layer is made one by MonochromeIconFactory ([renderMonochrome]). The profile's badge is drawn last, over the whole icon.
 * Over the pane, [LocalAppMarks] adds the notification dot (top end) and the mark of an update waiting in Store (bottom start), see AppMarks.kt.
 */
@Composable
fun AppIcon(entry: AppEntry, size: Dp, modifier: Modifier = Modifier) {
    val style = LocalIconStyle.current
    val shape = style.shape.toComposeShape()
    val colors = MaterialTheme.colorScheme
    val px = with(LocalDensity.current) { size.roundToPx() }.coerceAtLeast(1)
    val ground = colors.primaryContainer.toArgb()
    val tint = colors.onPrimaryContainer.toArgb()
    // The picture comes from IconCache (Trebuchet's IconCache idea, see IconRender.kt): a hit is read at once, in the same composition; else
    // it is built on the icon worker and the pane shows alone until it arrives, so scrolling a new column draws no bitmap on the main thread.
    // The previews and the screenshot tests (inspection mode) wait for no worker and build it in place.
    val inspection = LocalInspectionMode.current
    val key = IconKey(entry.listKey(), entry.icon, px, style.shape, style.themed, if (style.themed) ground else 0, if (style.themed) tint else 0)
    // The app keeps its last picture while the new one is built (key changed, listKey the same: a new shape, a new theme colour, an updated package):
    // Trebuchet's BubbleTextView keeps drawing its icon until applyIconAndLabel sets the new one
    // (https://github.com/LineageOS/android_packages_apps_Trebuchet/blob/lineage-22.2/src/com/android/launcher3/BubbleTextView.java).
    // Only a first appearance (another app in the slot) shows the pane alone.
    val listKey = entry.listKey()
    var held by remember(listKey) { mutableStateOf(IconCache.get(key) ?: if (inspection) IconCache.build(key) else null) }
    LaunchedEffect(key) { held = IconCache.load(key) }
    val picture = IconCache.get(key) ?: held
    val badge = remember(entry.icon) { entry.icon.badge?.asImageBitmap() }
    val marks = LocalAppMarks.current
    // The marks sit over the pane: the notification dot on the shape's edge, the update mark at the bottom start.
    Box(modifier.size(size)) {
        // The pane alone: the picture is drawn over it, not inside it, because the pane's surface clips its content to the shape with a
        // hard edge, which would cut the anti-aliased one again.
        ZoneSurface(shape = shape, modifier = Modifier.size(size)) { }
        picture?.let { ready ->
            // FastBitmapDrawable.updateFilter: "mPaint.setColorFilter(mIsDisabled ? getDisabledColorFilter(mDisabledAlpha) : mColorFilter)", the badge
            // keeps the normal filter (mBadge.setColorFilter(getColorFilter())).
            Image(ready, contentDescription = null, modifier = Modifier.size(size), colorFilter = if (entry.isDisabled()) DisabledColorFilter else null)
            if (badge != null) Image(badge, contentDescription = null, modifier = Modifier.size(size))
        }
        if (marks.update(entry)) UpdateMark(size * 0.36f, Modifier.align(Alignment.BottomStart))
        // BubbleTextView.applyDotState / animateDotScale (AppMarks.kt): the dot grows in or out; it stays composed while its scale is above 0.
        val isDotted = marks.dot(entry)
        val mDotParams = applyDotState(isDotted)
        val dotShown by remember { derivedStateOf { mDotParams.value > 0f } }
        if (isDotted || dotShown) NotificationDot(shape, size, dotScale = { mDotParams.value })
    }
}

/** The key of an app in a list: its activity in its profile. */
fun AppEntry.listKey(): String = "$packageName/$className/$userSerial"

// FastBitmapDrawable's DISABLED_DESATURATION and DISABLED_BRIGHTNESS
// (https://github.com/LineageOS/android_frameworks_libs_systemui/blob/lineage-22.2/iconloaderlib/src/com/android/launcher3/icons/FastBitmapDrawable.java#L58-L59).
private const val DISABLED_DESATURATION = 1f
private const val DISABLED_BRIGHTNESS = 0.5f

/**
 * FastBitmapDrawable.getDisabledColorFilter(float disabledAlpha)
 * (https://github.com/LineageOS/android_frameworks_libs_systemui/blob/lineage-22.2/iconloaderlib/src/com/android/launcher3/icons/FastBitmapDrawable.java#L355-L372):
 * the same matrix, a saturation of 0 concatenated with a brightness one (scale 0.5 on the colours, 127 added), as a Compose ColorFilter.
 */
private fun getDisabledColorFilter(disabledAlpha: Float): ColorFilter {
    val tempBrightnessMatrix = ColorMatrix()
    val tempFilterMatrix = ColorMatrix()

    tempFilterMatrix.setToSaturation(1f - DISABLED_DESATURATION)
    val scale = 1 - DISABLED_BRIGHTNESS
    val brightnessI = (255 * DISABLED_BRIGHTNESS).toInt()
    val mat = tempBrightnessMatrix.values
    mat[0] = scale
    mat[6] = scale
    mat[12] = scale
    mat[4] = brightnessI.toFloat()
    mat[9] = brightnessI.toFloat()
    mat[14] = brightnessI.toFloat()
    mat[18] = disabledAlpha
    // Android's preConcat: this = this x prematrix, which is Compose's timesAssign.
    tempFilterMatrix.timesAssign(tempBrightnessMatrix)
    return ColorFilter.colorMatrix(tempFilterMatrix)
}

/** FastBitmapDrawable.getDisabledColorFilter(): the filter of a disabled app's icon, at full alpha. */
private val DisabledColorFilter: ColorFilter = getDisabledColorFilter(1f)

/**
 * The label TalkBack reads for an app's cell: BubbleTextView.applyLabel sets the content description to the item's, "Disabled %1$s" of it when the
 * item is disabled (https://github.com/LineageOS/android_packages_apps_Trebuchet/blob/lineage-22.2/src/com/android/launcher3/BubbleTextView.java#L535-L545).
 * The text drawn stays the plain label.
 */
@Composable
internal fun AppEntry.applyContentDescription(): String =
    if (isDisabled()) stringResource(R.string.disabled_app_label, contentDescription) else contentDescription

/**
 * The count of Home presses (MainActivity.homeSignal), carried down to the cells. Launcher.onNewIntent calls AbstractFloatingView.closeAllOpenViewsExcept;
 * each long press menu here closes by [CloseOnHome] when it changes
 * (https://github.com/LineageOS/android_packages_apps_Trebuchet/blob/lineage-22.2/src/com/android/launcher3/Launcher.java#L1660-L1664).
 */
val LocalHomeSignal = compositionLocalOf { 0 }

/** The count of screen-off events (MainActivity.screenOffSignal): Launcher.onScreenOnChanged goes back to the NORMAL state, which has no open view. */
val LocalScreenOffSignal = compositionLocalOf { 0 }

/** Closes [menu] when Home is pressed or the screen turns off: the floating views Launcher.onNewIntent closes. */
@Composable
internal fun CloseOnHome(menu: PillMenuState) {
    val homeSignal = LocalHomeSignal.current
    val screenOffSignal = LocalScreenOffSignal.current
    LaunchedEffect(homeSignal, screenOffSignal) {
        if (homeSignal > 0 || screenOffSignal > 0) menu.close()
    }
}

/**
 * Where a cell is, for the launch animation: the cell's and its icon's bounds in the window, kept as the layout reports them (no recomposition),
 * and the [LaunchSource] ActivityContext.getActivityLaunchOptions reads, made when the cell is tapped
 * (https://github.com/LineageOS/android_packages_apps_Trebuchet/blob/lineage-22.2/src/com/android/launcher3/views/ActivityContext.java#L486-L507).
 */
class LaunchSourceTracker(private val v: View) {
    private var bounds = Rect()
    private var icon = Rect()

    // Utilities.getViewBounds (https://github.com/LineageOS/android_packages_apps_Trebuchet/blob/lineage-22.2/src/com/android/launcher3/Utilities.java#L822-L826)
    private fun getViewBounds(v: LayoutCoordinates): Rect {
        val pos = v.positionOnScreen()
        return Rect(pos.x.toInt(), pos.y.toInt(), pos.x.toInt() + v.size.width, pos.y.toInt() + v.size.height)
    }

    /** On the cell: its bounds on the screen, as Utilities.getViewBounds gives a view's. */
    val cell: Modifier = Modifier.onGloballyPositioned { bounds = getViewBounds(it) }

    /** On the cell's icon: the icon's bounds, which the launch grows out of. */
    val iconBounds: Modifier = Modifier.onGloballyPositioned { icon = it.boundsInWindow().toAndroidRect() }

    /** The source of a tap now; null before the cell was laid out. */
    fun getActivityLaunchOptions(): LaunchSource? = if (bounds.isEmpty || icon.isEmpty) null else LaunchSource(v, Rect(bounds), Rect(icon))
}

@Composable
internal fun rememberLaunchSource(): LaunchSourceTracker {
    val view = LocalView.current
    return remember(view) { LaunchSourceTracker(view) }
}

/**
 * [items] in rows of [columns] equal slots, each row at least [rowHeight] high (a minimum: a longer name makes its row taller),
 * the slot's content centred at the top.
 */
@Composable
fun <T> GridRows(items: List<T>, columns: Int, rowHeight: Dp, modifier: Modifier = Modifier, cell: @Composable (index: Int, item: T) -> Unit) {
    Column(modifier) {
        items.chunked(columns).forEachIndexed { r, row ->
            Row(Modifier.fillMaxWidth().heightIn(min = rowHeight)) {
                repeat(columns) { c ->
                    Box(Modifier.weight(1f), contentAlignment = Alignment.TopCenter) {
                        row.getOrNull(c)?.let { cell(r * columns + c, it) }
                    }
                }
            }
        }
    }
}

/** What the panels ask of the screen. */
data class HomeActions(
    val launch: (AppEntry) -> Unit = {},
    /** Starts the app from the tapped cell, where the launch animation starts ([LaunchSource], null when the cell is not laid out); [launch] by default. */
    val launchFrom: (AppEntry, LaunchSource?) -> Unit = { entry, _ -> launch(entry) },
    /**
     * Opens App info of the app. The [LaunchSource] is the app's cell (SystemShortcut.AppInfo.onClick's view: sourceBounds and launch options),
     * null when the cell is not laid out.
     */
    val appInfo: (AppEntry, LaunchSource?) -> Unit = { _, _ -> },
    val uninstall: (AppEntry) -> Unit = {},
    /**
     * What the Uninstall entry of an app's menu may be: SecondaryDropTarget.getButtonType, [AppActions.UNINSTALL] or [AppActions.INVALID] (no entry).
     * Off the main thread.
     */
    val getButtonType: suspend (AppEntry) -> Int = { AppActions.UNINSTALL },
    /** The shortcuts of an app for its long press menu: empty unless this is the default home. Off the main thread. */
    val shortcuts: suspend (AppEntry) -> List<ShortcutInfo> = { emptyList() },
    /**
     * Starts a shortcut from the app's cell: ActivityContext.startActivitySafely's shortcut branch gets the cell's [LaunchSource] (sourceBounds and
     * launch options), null when it is not laid out
     * (https://github.com/LineageOS/android_packages_apps_Trebuchet/blob/lineage-22.2/src/com/android/launcher3/views/ActivityContext.java#startActivitySafely).
     */
    val startShortcut: (ShortcutInfo, LaunchSource?) -> Unit = { _, _ -> },
    val removeFromRecents: (AppEntry) -> Unit = {},
    /** Opens Store (the menu item of an app with an update waiting). */
    val openStore: () -> Unit = {},
    val allowUsage: () -> Unit = {},
    /** Opens the Settings screen (the Apps panel's last row, or the menu of a long press on the wallpaper). */
    val openSettings: () -> Unit = {},
    /** Opens the wallpaper picker (the menu of a long press on the wallpaper). */
    val openWallpaper: () -> Unit = {},
    /** Pauses or locks the profile ([quiet] true), or brings it back (false): the Apps panel's Pause, Resume, Lock and Unlock. */
    val setQuiet: (ProfileInfo, Boolean) -> Unit = { _, _ -> },
    /** Opens the private space's settings (the gear of the unlocked private space). */
    val openPrivateSpaceSettings: () -> Unit = {},
    /** Opens the app market in the private space (the Install apps cell). */
    val installToPrivateSpace: (ProfileInfo) -> Unit = {},
    /** A panel opened: read the usage again. */
    val refresh: () -> Unit = {},
    /** The app leaves the apps panel and Recents (Hide, in the long press menu). */
    val hide: (AppEntry) -> Unit = {},
    /** Opens the Activities screen of an app of the main profile (the panel closes). */
    val openActivities: (AppEntry) -> Unit = {},
    /** Starts a pinned activity of the Apps panel's head. */
    val launchPinned: (PinnedActivity, LaunchSource?) -> Unit = { _, _ -> },
    /** Takes a pinned activity out of the Apps panel. */
    val unpin: (PinnedActivity) -> Unit = {},
    /** Starts the launch activity of a package (the header's missed calls and unread messages). */
    val openPackage: (String) -> Unit = {},
    /** Opens the notification shade (a swipe down on the home screen). */
    val openNotifications: () -> Unit = {}
)

/**
 * AllAppsTransitionController.setProgress and its constructor's mNavScrimFlag (Apache-2.0)
 * https://github.com/LineageOS/android_packages_apps_Trebuchet/blob/lineage-22.2/src/com/android/launcher3/allapps/AllAppsTransitionController.java#setProgress
 * and Launcher.java (UI_STATE_BASE_WINDOW from Themes.getActivityThemeRes's supportsDarkText of the WallpaperColorHints)
 * https://github.com/LineageOS/android_packages_apps_Trebuchet/blob/lineage-22.2/src/com/android/launcher3/Launcher.java#L590
 * The icons of the status and navigation bars: the base window state follows the wallpaper ([supportsDarkText]), and while a panel has a
 * scrim ([hasScrim], the panel out) UI_STATE_ALL_APPS gives the bars the panel's theme, mNavScrimFlag (a dark theme, [isMainColorDark], gets
 * FLAG_DARK_NAV, else FLAG_LIGHT_NAV), 0 when it closes. Differences: the status bar follows the nav bar's flag; the base state is put back
 * to the theme on leaving the home screen; nothing without an activity (previews).
 */
@Composable
internal fun HomeSystemBars(hasScrim: Boolean, isMainColorDark: Boolean) {
    val systemUiController = LocalSystemUiController.current ?: return
    val wallpaperColorHints = LocalWallpaperColorHints.current
    val mNavScrimFlag = if (isMainColorDark) {
        SystemUiController.FLAG_DARK_NAV or SystemUiController.FLAG_DARK_STATUS
    } else {
        SystemUiController.FLAG_LIGHT_NAV or SystemUiController.FLAG_LIGHT_STATUS
    }
    DisposableEffect(wallpaperColorHints, isMainColorDark) {
        systemUiController.updateUiState(SystemUiController.UI_STATE_BASE_WINDOW, supportsDarkText(wallpaperColorHints))
        onDispose { systemUiController.updateUiState(SystemUiController.UI_STATE_BASE_WINDOW, !isMainColorDark) }
    }
    DisposableEffect(hasScrim, mNavScrimFlag) {
        systemUiController.updateUiState(SystemUiController.UI_STATE_ALL_APPS, if (hasScrim) mNavScrimFlag else 0)
        onDispose { systemUiController.updateUiState(SystemUiController.UI_STATE_ALL_APPS, 0) }
    }
}

// A pinned activity with the app it belongs to, whose icon its cell shows: ActivityManager's shortcut is the activity's own
// icon and label (https://github.com/sdex/ActivityManager/blob/main/app/src/main/java/com/sdex/activityrunner/shortcut/ShortcutManager.kt).
class PinnedRow(val pin: PinnedActivity, val app: AppEntry)
