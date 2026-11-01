package com.yaz.launcher.feature.home

// The header drawn at the top of the screen while a panel is open or opening, in place of the status bar (the home screen hides it). Rows, each
// line shown only when it has something:
// - the time large, the seconds smaller after it (left), the battery (right), centred on the digits' height;
// - under the battery, while charging, the time left;
// - the date lines and the special Hijri day (left), the round glass Settings button (right);
// - the next alarm (left) and the phone's mode (right);
// - one row of glass capsules, the missed calls and the unread messages, only when there are some.
// Compared: SystemUI's shade header (clock at the start, battery at the end, https://github.com/LineageOS/android_frameworks_base/blob/lineage-22.2/packages/SystemUI/src/com/android/systemui/shade/ShadeHeaderController.kt),
// iOS (the battery pill with the percent inside, the Lock Screen's glanceables), Pixel Launcher (notification dots with counts); winner per piece:
// SystemUI's two-ended header, iOS's battery.
// The time and the battery share a row with their centres aligned (Row, Alignment.CenterVertically); the time's line box is trimmed to the glyphs with
// Compose's LineHeightStyle(Alignment.Center, Trim.Both), the way the text guide trims a line's extra leading
// (https://developer.android.com/develop/ui/compose/text/style-paragraph#line-height), so the digits sit at the middle of their box and the battery
// beside it is centred on them.
// The battery is drawn with Compose's Canvas (Modifier.drawBehind, https://developer.android.com/develop/ui/compose/graphics/draw/overview) after the look of
// iOS's pill and SystemUI's battery meter (BatteryMeterDrawable, a body with a nub and the level as the fill,
// https://github.com/LineageOS/android_frameworks_base/blob/lineage-22.2/packages/SystemUI/src/com/android/systemui/battery/BatteryMeterView.java); the colours: the
// charging green of iOS (system green 34C759), the Low Power Mode yellow of iOS (FFCC00), Material's error at 20 % or less. The number is drawn in two
// clipped layers at the fill's edge (a knockout), so it reads on the fill and on the empty part.
// The counts are the active notifications of the default phone and messages apps (data/notify, Trebuchet's DotInfo totals);
// the readers are data/glance. Everything stops with the header ([shown] false), nothing runs in the background.
// Input: while no panel is open the header takes none at all. A Material Surface swallows the touches over it (it has an empty pointerInput), so
// while the header is not shown the glass capsules and the button are replaced by plain boxes of the same minimum size; the header is also drawn
// under the panels, which start under its measured height.
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import com.yaz.launcher.R
import com.yaz.launcher.data.glance.BatteryState
import com.yaz.launcher.data.glance.GlanceDate
import com.yaz.launcher.data.glance.PhoneMode
import com.yaz.launcher.data.glance.defaultDialerPackage
import com.yaz.launcher.data.glance.defaultSmsPackage
import com.yaz.launcher.data.glance.rememberBattery
import com.yaz.launcher.data.glance.rememberClock
import com.yaz.launcher.data.glance.rememberPhone
import com.yaz.launcher.data.notify.NotificationDots
import com.yaz.launcher.data.settings.DateMode
import com.yaz.launcher.ui.component.BoldIconButton
import com.yaz.launcher.ui.component.ZoneSurface
import com.yaz.launcher.ui.component.rememberHaptics
import com.yaz.launcher.ui.icon.AppIcons
import java.text.NumberFormat

// iOS's system green and Low Power Mode yellow.
private val ChargingGreen = Color(0xFF34C759)
private val SaverYellow = Color(0xFFFFCC00)

/** The least size of a button or capsule of the header. */
private val TARGET = 44.dp

/**
 * The header. [shown] is true while a panel is open or opening: only then does the clock tick, do the receivers listen and the buttons answer a tap
 * (the caller sets its alpha and hides it from accessibility when it is not shown). [onOpen] starts the app of a package, [onSettings] opens Settings.
 */
@Composable
fun PanelHeader(shown: Boolean, dateMode: DateMode, onOpen: (String) -> Unit, onSettings: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]
    val started by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    // Nothing is read while the activity is stopped, even with a panel left open.
    val active = shown && started.isAtLeast(Lifecycle.State.STARTED)
    val now by rememberClock(active)
    val battery by rememberBattery(active)
    val phone by rememberPhone(active)
    val counts by NotificationDots.counts.collectAsState()
    // The default phone and messages apps, asked each time the header appears and kept after (the header does not jump as it closes).
    var dialer by remember { mutableStateOf<String?>(null) }
    var messages by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(active) {
        if (active) {
            dialer = defaultDialerPackage(context)
            messages = defaultSmsPackage(context)
        }
    }
    val missed = dialer?.let { counts[it] } ?: 0
    val unread = messages?.let { counts[it] } ?: 0

    val minute = now / 60_000L
    val clock = remember(minute, active, locale) { GlanceDate.clock(context, now, locale) }
    val period = remember(minute, active, locale) { GlanceDate.dayPeriod(context, now, locale) }
    val gregorian = remember(minute, locale, dateMode) { if (dateMode != DateMode.HIJRI) GlanceDate.gregorian(now, locale) else null }
    val hijri = remember(minute, locale, dateMode) { if (dateMode != DateMode.GREGORIAN) GlanceDate.hijri(now, locale) else null }
    val special = remember(minute, locale, dateMode) { if (dateMode != DateMode.GREGORIAN) GlanceDate.special(now, locale) else null }
    val specialText = special?.let { if (it.number > 0) stringResource(it.name, it.number) else stringResource(it.name) }
    val alarm = if (phone.alarmAt > 0L) stringResource(R.string.header_alarm, GlanceDate.timeOfDay(context, phone.alarmAt, locale)) else null
    val timeLeft = battery?.let { state ->
        when {
            !state.charging -> null
            state.charged || state.percent >= 100 -> stringResource(R.string.header_charged)
            state.remainingMs > 0L -> stringResource(R.string.header_time_left, GlanceDate.duration(state.remainingMs, locale))
            else -> null
        }
    }
    val mode = when (phone.mode) {
        PhoneMode.NORMAL -> null
        PhoneMode.VIBRATE -> AppIcons.Vibrate to stringResource(R.string.header_mode_vibrate)
        PhoneMode.SILENT -> AppIcons.SilentRinger to stringResource(R.string.header_mode_silent)
        PhoneMode.DND -> AppIcons.DoNotDisturb to stringResource(R.string.header_mode_dnd)
    }
    val haptics = rememberHaptics()
    val settingsLabel = stringResource(R.string.apps_settings)

    Column(
        modifier
            .fillMaxWidth()
            // Clear of a cut-out and the system bars at the top and sides, whether the status bar is shown or hidden.
            .windowInsetsPadding(WindowInsets.systemBars.union(WindowInsets.displayCutout).only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
            .padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 12.dp)
    ) {
        // The time and the battery: one row, both centred on the same line.
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            val style = MaterialTheme.typography.displayLarge.copy(
                fontWeight = FontWeight.Medium,
                color = LocalContentColor.current,
                lineHeightStyle = LineHeightStyle(alignment = LineHeightStyle.Alignment.Center, trim = LineHeightStyle.Trim.Both)
            )
            BasicText(
                buildAnnotatedString {
                    append(clock)
                    withStyle(SpanStyle(fontSize = 0.4.em)) {
                        append(GlanceDate.seconds(now, locale))
                        if (period != null) append(" $period")
                    }
                },
                style = style,
                maxLines = 1,
                autoSize = TextAutoSize.StepBased(minFontSize = 24.sp, maxFontSize = style.fontSize, stepSize = 1.sp),
                modifier = Modifier.weight(1f)
            )
            battery?.let { BatteryPill(it, locale) }
        }
        timeLeft?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.align(Alignment.End).padding(top = 4.dp))
        }
        // The date on the left, Settings on the right on the same row.
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(Modifier.weight(1f)) {
                gregorian?.let { Text(it, style = MaterialTheme.typography.titleMedium) }
                hijri?.let { Text(it, style = MaterialTheme.typography.titleMedium) }
                specialText?.let { Text(it, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.tertiary) }
            }
            if (shown) {
                // The suite's round glass icon button, at least 44 dp.
                BoldIconButton(onClick = { haptics.tick(); onSettings() }, modifier = Modifier.sizeIn(minWidth = TARGET, minHeight = TARGET)) {
                    Icon(AppIcons.Settings, contentDescription = settingsLabel)
                }
            } else {
                Spacer(Modifier.size(TARGET))
            }
        }
        // The alarm on the left, the phone's mode on the right.
        if (alarm != null || mode != null) {
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Box(Modifier.weight(1f)) {
                    if (alarm != null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(AppIcons.Alarm, contentDescription = null, modifier = Modifier.size(18.dp))
                            Text(alarm, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = 6.dp))
                        }
                    }
                }
                mode?.let { (icon, words) ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text(words, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = 6.dp))
                    }
                }
            }
        }
        // The capsules fill the width with equal weights, only the ones with something to show; no row at all when there is none.
        val dialerName = dialer
        val messagesName = messages
        val showMissed = missed > 0 && dialerName != null
        val showUnread = unread > 0 && messagesName != null
        if (showMissed || showUnread) {
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (showMissed && dialerName != null) {
                    Capsule(stringResource(R.string.header_missed_calls, missed), shown, Modifier.weight(1f), onClick = { haptics.tick(); onOpen(dialerName) }) {
                        Icon(AppIcons.PhoneMissed, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
                        Text(countText(missed), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(start = 6.dp))
                    }
                }
                if (showUnread && messagesName != null) {
                    Capsule(stringResource(R.string.header_unread_sms, unread), shown, Modifier.weight(1f), onClick = { haptics.tick(); onOpen(messagesName) }) {
                        Icon(AppIcons.Sms, contentDescription = null, modifier = Modifier.size(20.dp))
                        Text(countText(unread), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(start = 6.dp))
                    }
                }
            }
        }
    }
}

// Where the fill of the battery ends across a body [width] px wide: the 3 dp inset, then the level of the inner width (the same as BatteryPill draws);
// SystemUI's BatteryMeterView draws its level as a part of the body in the same way
// (https://github.com/LineageOS/android_frameworks_base/blob/lineage-22.2/packages/SystemUI/src/com/android/systemui/battery/BatteryMeterView.java).
private fun fillEdge(density: androidx.compose.ui.unit.Density, width: Float, percent: Int): Float =
    with(density) { 3.dp.toPx() + (width - 6.dp.toPx()) * percent / 100f }

// 99+ past two digits, so a capsule never grows with a long count.
private fun countText(count: Int): String = if (count > 99) "99+" else count.toString()

// One glass capsule of the row: the pane the app uses for what is tapped (ZoneSurface in a circle shape, a stadium), at least 44 dp high and as
// tall as its words need. It answers a tap only while the header is shown ([enabled]); otherwise it is a plain box of the same size, which takes
// no touch (a Material Surface, even without a click, swallows the touches over it).
@Composable
private fun Capsule(description: String, enabled: Boolean, modifier: Modifier, onClick: () -> Unit, content: @Composable RowScope.() -> Unit) {
    val inner = Modifier.fillMaxWidth().heightIn(min = TARGET).padding(horizontal = 12.dp, vertical = 8.dp)
    if (!enabled) {
        Box(modifier.heightIn(min = TARGET))
        return
    }
    ZoneSurface(
        modifier = modifier.heightIn(min = TARGET).semantics { contentDescription = description },
        shape = CircleShape,
        onClick = onClick
    ) {
        Row(inner, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center, content = content)
    }
}

/**
 * The battery, large, as iOS draws it: a rounded body about 66 by 32 dp with an outline in onSurface and a small nub at the end, the fill as wide as the
 * level over a faint track, the number inside. Charging: a bolt before it and the fill green; else the battery saver is yellow; else 20 % or less is
 * the error colour; else onSurface. The body is a minimum size: it grows with the number's font.
 */
@Composable
private fun BatteryPill(battery: BatteryState, locale: java.util.Locale) {
    val colors = MaterialTheme.colorScheme
    val fill = when {
        battery.charging -> ChargingGreen
        battery.saver -> SaverYellow
        battery.percent <= 20 -> colors.error
        else -> colors.onSurface
    }
    // The number on the fill: dark on green and yellow, the surface on onSurface, onError on the error colour.
    val onFill = when (fill) {
        colors.onSurface -> colors.surface
        colors.error -> colors.onError
        else -> Color.Black
    }
    val outline = colors.onSurface
    val track = outline.copy(alpha = 0.25f)
    val corner = MaterialTheme.shapes.medium.topStart
    val label = NumberFormat.getIntegerInstance(locale).format(battery.percent)
    val numberStyle = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
    val description = stringResource(
        when {
            battery.charging -> R.string.header_battery_charging
            battery.saver -> R.string.header_battery_saver
            else -> R.string.header_battery
        },
        battery.percent
    )
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.semantics { contentDescription = description }) {
        if (battery.charging) Icon(AppIcons.Bolt, contentDescription = null, tint = ChargingGreen, modifier = Modifier.size(24.dp))
        Box(
            Modifier
                .widthIn(min = 66.dp)
                .heightIn(min = 32.dp)
                .drawBehind {
                    val stroke = 2.dp.toPx()
                    val inset = 3.dp.toPx()
                    val radius = corner.toPx(size, this)
                    drawRoundRect(
                        outline,
                        topLeft = Offset(stroke / 2, stroke / 2),
                        size = Size(size.width - stroke, size.height - stroke),
                        cornerRadius = CornerRadius(radius),
                        style = Stroke(stroke)
                    )
                    val inner = Size(size.width - 2 * inset, size.height - 2 * inset)
                    val innerRadius = (radius - inset).coerceAtLeast(2.dp.toPx())
                    val body = Path().apply { addRoundRect(RoundRect(inset, inset, inset + inner.width, inset + inner.height, CornerRadius(innerRadius))) }
                    clipPath(body) {
                        drawRect(track, Offset(inset, inset), inner)
                        drawRect(fill, Offset(inset, inset), Size(inner.width * battery.percent / 100f, inner.height))
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            // The number sizes the body; the two layers over it are cut at the fill's edge.
            Text(label, style = numberStyle, modifier = Modifier.alpha(0f).padding(horizontal = 8.dp))
            Box(
                Modifier.matchParentSize().drawWithContent { clipRect(left = fillEdge(this, size.width, battery.percent)) { this@drawWithContent.drawContent() } },
                contentAlignment = Alignment.Center
            ) { Text(label, style = numberStyle, color = outline) }
            Box(
                Modifier.matchParentSize().drawWithContent { clipRect(right = fillEdge(this, size.width, battery.percent)) { this@drawWithContent.drawContent() } },
                contentAlignment = Alignment.Center
            ) { Text(label, style = numberStyle, color = onFill) }
        }
        Box(Modifier.padding(start = 2.dp).size(width = 4.dp, height = 11.dp).background(outline, CircleShape))
    }
}
