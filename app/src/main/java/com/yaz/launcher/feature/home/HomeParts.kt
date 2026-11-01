package com.yaz.launcher.feature.home

// The pieces the panels share.
// - AppIcon: an app's icon in a pane of glass of the chosen shape (the pane as the base kit's GlassIcon, a ZoneSurface), the layers made into
//   bitmaps at the size shown as Store's AppIcon does (YAZ/Store at HEAD, feature/home/AppIcon.kt), cut to the shape, themed if asked.
// - GridRows: rows of equal columns, each slot of equal weight, so a short last row keeps the columns; Compose's Row and Modifier.weight
//   (https://developer.android.com/develop/ui/compose/layouts/basics). The Recents panel is made of it.
// - HomeActions: what the panels ask of the screen; the stateful HomeScreen answers with AppActions and AppShortcuts.
import android.content.pm.ShortcutInfo
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import com.yaz.launcher.data.apps.AppEntry
import com.yaz.launcher.data.apps.ProfileInfo
import com.yaz.launcher.data.settings.IconShape
import com.yaz.launcher.data.settings.PinnedActivity
import com.yaz.launcher.ui.component.ZoneSurface
import com.yaz.launcher.ui.icon.renderIcon
import com.yaz.launcher.ui.icon.renderThemedIcon
import com.yaz.launcher.ui.icon.toAndroidPath
import com.yaz.launcher.ui.icon.toComposeShape

/** How the app icons are drawn: the shape the user chose, and whether the icons with a monochrome layer take the theme's colours. */
data class IconStyle(val shape: IconShape = IconShape.CIRCLE, val themed: Boolean = false)

/** Fed from the settings by HomeContent; the panels' [AppIcon] read it. */
val LocalIconStyle = compositionLocalOf { IconStyle() }

/**
 * The icon of [entry], [size] wide, in a pane of glass of the chosen shape. The two layers of the icon are cut to the shape (the
 * pictures are made at the size shown, [renderIcon]); themed, the monochrome picture is tinted with onPrimaryContainer on a
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
    // The shape is cut into the bitmap with an anti-aliased mask (AdaptiveIconDrawable.draw, see IconRender.kt), so no Compose clip is left on
    // the picture; a new shape, theme or colour makes a new bitmap.
    val picture = remember(entry.icon, px, style.shape, style.themed, ground, tint) {
        val mask = style.shape.toAndroidPath(px)
        val themed = if (style.themed) renderThemedIcon(entry.icon, px, mask, ground, tint) else null
        (themed ?: renderIcon(entry.icon, px, mask)).asImageBitmap()
    }
    val badge = remember(entry.icon) { entry.icon.badge?.asImageBitmap() }
    val marks = LocalAppMarks.current
    // The marks sit over the pane: the notification dot on the shape's edge, the update mark at the bottom start.
    Box(modifier.size(size)) {
        // The pane alone: the picture is drawn over it, not inside it, because the pane's surface clips its content to the shape with a
        // hard edge, which would cut the anti-aliased one again.
        ZoneSurface(shape = shape, modifier = Modifier.size(size)) { }
        Image(picture, contentDescription = null, modifier = Modifier.size(size))
        if (badge != null) Image(badge, contentDescription = null, modifier = Modifier.size(size))
        if (marks.update(entry)) UpdateMark(size * 0.36f, Modifier.align(Alignment.BottomStart))
        if (marks.dot(entry)) NotificationDot(shape, size)
    }
}

/** The key of an app in a list: its activity in its profile. */
fun AppEntry.listKey(): String = "$packageName/$className/$userSerial"

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
    val appInfo: (AppEntry) -> Unit = {},
    val uninstall: (AppEntry) -> Unit = {},
    /** The shortcuts of an app for its long press menu: empty unless this is the default home. Off the main thread. */
    val shortcuts: suspend (AppEntry) -> List<ShortcutInfo> = { emptyList() },
    val startShortcut: (ShortcutInfo) -> Unit = {},
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
    /** A panel opened: read the usage again. */
    val refresh: () -> Unit = {},
    /** The app leaves the apps panel and Recents (Hide, in the long press menu). */
    val hide: (AppEntry) -> Unit = {},
    /** Opens the Activities screen of an app of the main profile (the panel closes). */
    val openActivities: (AppEntry) -> Unit = {},
    /** Starts a pinned activity of the Apps panel's head. */
    val launchPinned: (PinnedActivity) -> Unit = {},
    /** Takes a pinned activity out of the Apps panel. */
    val unpin: (PinnedActivity) -> Unit = {}
)

// A pinned activity with the app it belongs to, whose icon its cell shows: ActivityManager's shortcut is the activity's own
// icon and label (https://github.com/sdex/ActivityManager/blob/main/app/src/main/java/com/sdex/activityrunner/shortcut/ShortcutManager.kt).
class PinnedRow(val pin: PinnedActivity, val app: AppEntry)
