package com.yaz.launcher.feature.common

// The pieces of the Hidden apps and Activities screens.
// - ListFrame: the screen of Settings (this app's feature/settings/SettingsScreen.kt): the home is the wallpaper, so the screen brings its own ground
//   (glassGround), FloatingFrame with the title centred (large, folding as the list scrolls) and a round glass Back at the start. The list
//   scrolls under the header, which takes the drag too (FloatingFrame's `scroll`).
// - ListRow: a row of the suite's lists, Passwords' EntryLine (YAZ/Passwords at HEAD, feature/common/EntryParts.kt): a zone with the leading picture,
//   the title and a second line, its whole width the tap target and long press, sinking under the finger (Pop.kt); the corners are Material 3's
//   ExtraLarge (the pane/card role, never a hand-written radius). A row that cannot be used is greyed and takes no tap.
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ShapeDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.yaz.launcher.R
import com.yaz.launcher.feature.settings.SettingsIcons
import com.yaz.launcher.ui.component.FloatingAction
import com.yaz.launcher.ui.component.FloatingFrame
import com.yaz.launcher.ui.component.FloatingTop
import com.yaz.launcher.ui.component.LargeTitle
import com.yaz.launcher.ui.component.ZoneSurface
import com.yaz.launcher.ui.component.sinkClickable
import com.yaz.launcher.ui.glass.LocalGlass
import com.yaz.launcher.ui.glass.glassGround

/** The widest a row grows on a tablet, as Passwords' lines. */
val ListWidth = 640.dp

// Settings' frame, https://developer.android.com/develop/ui/compose/lists for the list inside it.
@Composable
fun ListFrame(title: String, scroll: ScrollableState, onBack: () -> Unit, content: @Composable (PaddingValues) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize().glassGround(LocalGlass.current, MaterialTheme.colorScheme.background)) {
        FloatingFrame(
            bottom = 0.dp,
            scroll = scroll,
            top = { FloatingTop(title, leading = { FloatingAction(SettingsIcons.ArrowBack, stringResource(R.string.back), onBack) }) },
            largeTitle = LargeTitle(title),
            content = content
        )
    }
}

/**
 * One row. [leading] is the picture at the start, [second] and [third] the lines under the title (words never cut: they wrap), [trailing] a
 * mark at the end. [enabled] false greys the row and removes its tap and long press.
 * Passwords' EntryLine, https://developer.android.com/develop/ui/compose/lists
 */
@Composable
fun ListRow(
    title: String,
    modifier: Modifier = Modifier,
    second: String? = null,
    third: String? = null,
    enabled: Boolean = true,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit = {}
) {
    val shape = ShapeDefaults.ExtraLarge
    ZoneSurface(
        shape = shape,
        modifier = modifier
            .widthIn(max = ListWidth)
            .fillMaxWidth()
            .alpha(if (enabled) 1f else 0.5f)
            .clip(shape)
            .then(if (enabled) Modifier.sinkClickable(title, depth = 0.97f, onLongClick = onLongClick, onClick = onClick) else Modifier)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.heightIn(min = 64.dp).padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            leading?.invoke()
            Column(Modifier.weight(1f).padding(start = if (leading != null) 14.dp else 0.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                if (second != null) Text(second, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (third != null) Text(third, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            trailing?.invoke()
        }
    }
}
