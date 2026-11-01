package com.yaz.launcher.feature.settings

// Settings as tiles, ported from Store's SettingsScreen.kt (our own app, /home/yghouar/Claude Code/APK/YAZ/Store at
// app/src/main/java/com/yaz/store/feature/settings/SettingsScreen.kt: SettingsContent, the sections folded on a phone and as list and detail on a wide
// window, FloatingFrame with a round glass Back action and a large title, ChoiceDialog for the theme), itself Contacts' and SMS's
// (YAZ/SMS, feature/settings/SettingsScreen.kt). The tiles are Modules' SettingsTiles (ui/component), Android Quick Settings' tile, AOSP SystemUI
// QSTileViewImpl (https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/packages/SystemUI/src/com/android/systemui/qs/tileimpl/QSTileViewImpl.kt).
// The kit has no slider tile, as SMS's and Store's screens have none: a value that runs on a scale is an action tile showing its value, opening a
// dialog with the glass slider, as Keyboard's SliderDialog does (YAZ/Keyboard, settings/dialogs/SliderDialog.kt: the slider and the value under it).
// The icon shape is the same: a tile opening the five samples (the Pixel Launcher's Wallpaper & style picks the shape the same way).
// The Layout section is Kvaesitso's gesture settings, the idea only (GPL-3.0, one row per swipe, each picks its action from a list:
// https://github.com/MM2-0/Kvaesitso/blob/main/app/ui/src/main/java/de/mm20/launcher2/ui/settings/gestures/GestureSettingsScreen.kt): a tile
// per swipe showing its panel, opening the suite's ChoiceDialog.
// Compared: Pixel Launcher, iOS, Kvaesitso (Settings > Gestures), Niagara; winner for the layout: Kvaesitso's one row per swipe.
// Launcher's own settings are read and written through SettingsStore, with Koin's koinInject as Store's screen does; a slider changes the setting as
// it moves, so the panels and the tabs show it at once.
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.yaz.launcher.BuildConfig
import com.yaz.launcher.R
import com.yaz.launcher.data.settings.IconShape
import com.yaz.launcher.data.settings.PanelKind
import com.yaz.launcher.data.settings.Settings
import com.yaz.launcher.data.settings.SettingsStore
import com.yaz.launcher.data.settings.Swipe
import com.yaz.launcher.data.settings.ThemeMode
import com.yaz.launcher.navigation.LocalReadableInset
import com.yaz.launcher.navigation.WidthClass
import com.yaz.launcher.ui.component.BoldButton
import com.yaz.launcher.ui.component.ChoiceDialog
import com.yaz.launcher.ui.component.FloatingAction
import com.yaz.launcher.ui.component.FloatingFrame
import com.yaz.launcher.ui.component.FloatingTop
import com.yaz.launcher.ui.component.GlassSlider
import com.yaz.launcher.ui.component.LargeTitle
import com.yaz.launcher.ui.component.SettingsActionTile
import com.yaz.launcher.ui.component.SettingsFoldedSections
import com.yaz.launcher.ui.component.SettingsHeaderSpacer
import com.yaz.launcher.ui.component.SettingsListDetail
import com.yaz.launcher.ui.component.SettingsSection
import com.yaz.launcher.ui.component.SettingsSectionTitle
import com.yaz.launcher.ui.component.SettingsSwitchTile
import com.yaz.launcher.ui.component.SettingsTile
import com.yaz.launcher.ui.component.SettingsTileDialogs
import com.yaz.launcher.ui.component.SettingsTileGrid
import com.yaz.launcher.ui.component.ZoneAlertDialog
import com.yaz.launcher.ui.component.ZoneSurface
import com.yaz.launcher.ui.component.rememberHaptics
import com.yaz.launcher.ui.glass.LocalGlass
import com.yaz.launcher.ui.glass.glassGround
import com.yaz.launcher.ui.icon.AppIcons
import com.yaz.launcher.ui.icon.PanelIcons
import com.yaz.launcher.ui.icon.toComposeShape
import kotlin.math.roundToInt
import org.koin.compose.koinInject

private enum class OpenDialog { NONE, THEME, SHAPE, OPACITY, EDGE_TABS, SWIPE_LEFT, SWIPE_RIGHT, SWIPE_UP }

/** The groups of settings: folded one under another on a phone, a list and the open one's page on a wide window. */
private const val APPEARANCE = "appearance"
private const val LAYOUT = "layout"
private const val ABOUT = "about"

@Composable
fun SettingsScreen(onBack: () -> Unit, onGuide: () -> Unit, onHidden: () -> Unit) {
    val store: SettingsStore = koinInject()
    val settings by store.settings.collectAsState()
    SettingsContent(
        settings = settings,
        onBack = onBack,
        onTheme = { mode -> store.update { it.copy(themeMode = mode) } },
        onIconShape = { shape -> store.update { it.copy(iconShape = shape) } },
        onThemedIcons = { on -> store.update { it.copy(themedIcons = on) } },
        onPanelOpacity = { percent -> store.update { it.copy(panelOpacity = percent.coerceIn(0, 100)) } },
        onEdgeTabs = { percent -> store.update { it.copy(edgeTabs = percent.coerceIn(0, 100)) } },
        onPanel = { swipe, kind -> store.update { it.withPanel(swipe, kind) } },
        onHidden = onHidden,
        onGuide = onGuide
    )
}

/**
 * Stateless, so a test draws it with fixed data. A large title that folds as the page scrolls, every setting a tile with its value, a tap
 * opens or toggles, a long press explains, the sections side by side on a wide window and folded on a phone.
 */
@Composable
fun SettingsContent(
    settings: Settings,
    onBack: () -> Unit,
    onTheme: (ThemeMode) -> Unit = {},
    onIconShape: (IconShape) -> Unit = {},
    onThemedIcons: (Boolean) -> Unit = {},
    onPanelOpacity: (Int) -> Unit = {},
    onEdgeTabs: (Int) -> Unit = {},
    onPanel: (Swipe, PanelKind) -> Unit = { _, _ -> },
    onHidden: () -> Unit = {},
    onGuide: () -> Unit = {}
) {
    var dialog by rememberSaveable { mutableStateOf(OpenDialog.NONE) }
    // The tile whose long press is open (its id), "" when none.
    var tileId by rememberSaveable { mutableStateOf("") }
    var sectionId by rememberSaveable { mutableStateOf(APPEARANCE) }
    // On a phone every section starts folded; the one open stays open across a turn of the screen.
    var openId by rememberSaveable { mutableStateOf("") }
    // The page's scroll, held here so a drag on the header drives it (FloatingFrame's `scroll`): the folded list on a phone, the chosen
    // section's page on a wide window (it starts at the top again for each section).
    val compactScroll = rememberScrollState()
    val detailScroll = rememberSaveable(sectionId, saver = ScrollState.Saver) { ScrollState(0) }

    val settingsTitle = stringResource(R.string.settings)
    val theme = stringResource(R.string.settings_theme)
    val themeName = themeLabel(settings.themeMode)
    val shape = stringResource(R.string.settings_icon_shape)
    val shapeName = iconShapeLabel(settings.iconShape)
    val themed = stringResource(R.string.settings_themed_icons)
    val opacity = stringResource(R.string.settings_panel_opacity)
    val opacityValue = stringResource(R.string.settings_panel_opacity_value, settings.panelOpacity)
    val tabs = stringResource(R.string.settings_edge_tabs)
    val tabsValue = stringResource(R.string.settings_panel_opacity_value, settings.edgeTabs)
    val swipeLeft = stringResource(R.string.settings_swipe_left)
    val swipeRight = stringResource(R.string.settings_swipe_right)
    val swipeUp = stringResource(R.string.settings_swipe_up)
    val hidden = stringResource(R.string.settings_hidden)
    val guide = stringResource(R.string.settings_guide)
    val version = stringResource(R.string.settings_version, BuildConfig.VERSION_NAME)

    val sections = listOf(
        SettingsSection(APPEARANCE, stringResource(R.string.settings_appearance), SettingsIcons.Palette),
        SettingsSection(LAYOUT, stringResource(R.string.settings_layout), AppIcons.Apps),
        SettingsSection(ABOUT, stringResource(R.string.settings_about), AppIcons.Info)
    )
    val titled = sections.first { it.id == sectionId }
    val openSection = sections.firstOrNull { it.id == openId }

    val tiles: Map<String, List<SettingsTile>> = mapOf(
        APPEARANCE to listOf(
            SettingsActionTile(
                id = "theme", name = theme, title = theme, icon = SettingsIcons.Brightness,
                value = themeName, summary = stringResource(R.string.settings_theme_summary),
                onClick = { dialog = OpenDialog.THEME }
            ),
            SettingsActionTile(
                id = "shape", name = shape, title = shape, icon = AppIcons.Apps,
                value = shapeName, summary = stringResource(R.string.settings_icon_shape_summary),
                onClick = { dialog = OpenDialog.SHAPE }
            ),
            SettingsSwitchTile(
                id = "themed", name = themed, title = themed, icon = SettingsIcons.Palette,
                summary = stringResource(R.string.settings_themed_icons_summary),
                checked = settings.themedIcons, onChange = onThemedIcons
            ),
            SettingsActionTile(
                id = "opacity", name = opacity, title = opacity, icon = SettingsIcons.Opacity,
                value = opacityValue, summary = stringResource(R.string.settings_panel_opacity_summary),
                onClick = { dialog = OpenDialog.OPACITY }
            ),
            SettingsActionTile(
                id = "tabs", name = tabs, title = tabs, icon = AppIcons.AutoAwesome,
                value = tabsValue, summary = stringResource(R.string.settings_edge_tabs_summary),
                onClick = { dialog = OpenDialog.EDGE_TABS }
            )
        ),
        // One tile per swipe with its panel, as Kvaesitso's gesture rows show their action; the pick goes through Settings.withPanel.
        LAYOUT to listOf(
            SettingsActionTile(
                id = "swipe_left", name = swipeLeft, title = swipeLeft, icon = AppIcons.Apps,
                value = panelLabel(settings.panelOf(Swipe.LEFT)), summary = stringResource(R.string.settings_swipe_left_summary),
                onClick = { dialog = OpenDialog.SWIPE_LEFT }
            ),
            SettingsActionTile(
                id = "swipe_right", name = swipeRight, title = swipeRight, icon = AppIcons.Apps,
                value = panelLabel(settings.panelOf(Swipe.RIGHT)), summary = stringResource(R.string.settings_swipe_right_summary),
                onClick = { dialog = OpenDialog.SWIPE_RIGHT }
            ),
            SettingsActionTile(
                id = "swipe_up", name = swipeUp, title = swipeUp, icon = AppIcons.Apps,
                value = panelLabel(settings.panelOf(Swipe.UP)), summary = stringResource(R.string.settings_swipe_up_summary),
                onClick = { dialog = OpenDialog.SWIPE_UP }
            ),
            // The Hidden apps screen (Trebuchet's trust apps list), opened from this section.
            SettingsActionTile(
                id = "hidden", name = hidden, title = hidden, icon = PanelIcons.VisibilityOff,
                value = stringResource(R.string.settings_hidden_value), summary = stringResource(R.string.settings_hidden_summary),
                onClick = onHidden
            )
        ),
        ABOUT to listOf(
            // The welcome pages shown again, as Store's About offers (YAZ/Store, feature/settings/SettingsScreen.kt, "guide").
            SettingsActionTile(
                id = "guide", name = guide, title = guide, icon = AppIcons.Play,
                value = stringResource(R.string.settings_guide_value), summary = stringResource(R.string.settings_guide_summary),
                onClick = onGuide
            ),
            SettingsActionTile(
                id = "version", name = version, title = version, icon = AppIcons.Info,
                value = BuildConfig.VERSION_NAME, summary = stringResource(R.string.settings_version_summary)
            )
        )
    )

    // One section's page: its name, then its tiles.
    val page: @Composable ColumnScope.(SettingsSection) -> Unit = { which ->
        SettingsSectionTitle(which.title)
        val list = tiles[which.id].orEmpty()
        if (list.isNotEmpty()) SettingsTileGrid(list) { tileId = it.id }
    }

    // The window decides: a list and a page side by side from Medium up, as a phone held upright keeps folded sections. The panes take the
    // whole width, so the readable inset is dropped.
    // The home is the wallpaper, so the screen brings its own ground (the glass kit's glassGround, as the guide does).
    BoxWithConstraints(Modifier.fillMaxSize().glassGround(LocalGlass.current, MaterialTheme.colorScheme.background)) {
        val wide = WidthClass.of(maxWidth) != WidthClass.COMPACT
        CompositionLocalProvider(LocalReadableInset provides if (wide) 0.dp else LocalReadableInset.current) {
            FloatingFrame(
                bottom = 0.dp,
                scroll = if (wide) detailScroll else compactScroll,
                top = { FloatingTop(settingsTitle, leading = { FloatingAction(SettingsIcons.ArrowBack, stringResource(R.string.back), onBack) }) },
                largeTitle = LargeTitle(settingsTitle)
            ) { padding ->
                if (wide) {
                    SettingsListDetail(
                        padding = padding,
                        sections = sections,
                        section = titled,
                        onSection = { sectionId = it.id },
                        detailScroll = detailScroll,
                        detail = { page(titled) }
                    )
                } else {
                    // Scrolls at full width, rows pushed in by the readable inset, so the margins of a tablet scroll like the rest.
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(compactScroll)
                            .padding(horizontal = LocalReadableInset.current)
                    ) {
                        SettingsHeaderSpacer(padding)
                        Spacer(Modifier.height(24.dp))
                        SettingsFoldedSections(
                            sections, open = openSection,
                            onOpen = { openId = it?.id.orEmpty() }
                        ) { which ->
                            val list = tiles[which.id].orEmpty()
                            if (list.isNotEmpty()) SettingsTileGrid(list) { tileId = it.id }
                        }
                        Spacer(Modifier.height(24.dp))
                        Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
                    }
                }
            }
        }
    }

    tiles.values.flatten().firstOrNull { it.id == tileId }?.let { tile ->
        SettingsTileDialogs(tile, onDismiss = { tileId = "" })
    }

    val close = { dialog = OpenDialog.NONE }
    when (dialog) {
        OpenDialog.THEME -> ChoiceDialog(
            title = theme,
            options = ThemeMode.entries.map { it to themeLabel(it) },
            selected = settings.themeMode,
            onPick = onTheme,
            onDismiss = close
        )
        OpenDialog.SHAPE -> ShapeDialog(shape, settings.iconShape, onIconShape, close)
        OpenDialog.OPACITY -> PercentDialog(opacity, settings.panelOpacity, onPanelOpacity, close)
        OpenDialog.EDGE_TABS -> PercentDialog(tabs, settings.edgeTabs, onEdgeTabs, close)
        OpenDialog.SWIPE_LEFT -> PanelDialog(swipeLeft, settings.panelOf(Swipe.LEFT), { onPanel(Swipe.LEFT, it) }, close)
        OpenDialog.SWIPE_RIGHT -> PanelDialog(swipeRight, settings.panelOf(Swipe.RIGHT), { onPanel(Swipe.RIGHT, it) }, close)
        OpenDialog.SWIPE_UP -> PanelDialog(swipeUp, settings.panelOf(Swipe.UP), { onPanel(Swipe.UP, it) }, close)
        OpenDialog.NONE -> Unit
    }
}

// The panel a swipe opens: Apps, Recents or Nothing, in the suite's ChoiceDialog like the theme dialog above. The list of actions per gesture is
// Kvaesitso's (idea only, GPL-3.0, https://github.com/MM2-0/Kvaesitso/blob/main/app/ui/src/main/java/de/mm20/launcher2/ui/settings/gestures/GestureSettingsScreen.kt).
@Composable
private fun PanelDialog(title: String, chosen: PanelKind, onPick: (PanelKind) -> Unit, onDismiss: () -> Unit) {
    ChoiceDialog(
        title = title,
        options = PanelKind.entries.map { it to panelLabel(it) },
        selected = chosen,
        onPick = onPick,
        onDismiss = onDismiss
    )
}

// The name of a panel, a string resource per value as themeLabel and iconShapeLabel below do (the same Kvaesitso file names its actions the same way).
@Composable
private fun panelLabel(kind: PanelKind): String = stringResource(
    when (kind) {
        PanelKind.APPS -> R.string.settings_panel_apps
        PanelKind.RECENTS -> R.string.settings_panel_recents
        PanelKind.NONE -> R.string.settings_panel_none
    }
)

// A value from 0 to 100 %: the glass slider and the value under it, Keyboard's SliderDialog (YAZ/Keyboard, settings/dialogs/SliderDialog.kt), in
// the suite's dialog. The setting changes as the thumb moves; Done closes.
@Composable
private fun PercentDialog(title: String, percent: Int, onChange: (Int) -> Unit, onDismiss: () -> Unit) {
    ZoneAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                GlassSlider(
                    value = percent.toFloat(),
                    onValueChange = { onChange(it.roundToInt().coerceIn(0, 100)) },
                    valueRange = 0f..100f
                )
                Text(
                    stringResource(R.string.settings_panel_opacity_value, percent),
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center
                )
            }
        },
        confirmButton = { BoldButton(onClick = onDismiss) { Text(stringResource(R.string.done)) } }
    )
}

// The icon shape, as the Pixel Launcher's Wallpaper & style picks it (Android 16 QPR2): the five shapes as samples in one row, the chosen one
// marked. Each sample is a tap target of 48 dp (Material's minimum), a ZoneSurface with the chosen look of ChoiceDialog (accent = true,
// CircleShape); Compose's FlowRow spreads them over the width and wraps them when it is narrow
// (https://developer.android.com/develop/ui/compose/layouts/flow). The name is the content description.@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ShapeDialog(title: String, chosen: IconShape, onPick: (IconShape) -> Unit, onDismiss: () -> Unit) {
    val haptics = rememberHaptics()
    ZoneAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalArrangement = Arrangement.spacedBy(8.dp),
                maxItemsInEachRow = IconShape.entries.size
            ) {
                IconShape.entries.forEach { shape ->
                    val name = iconShapeLabel(shape)
                    val picked = shape == chosen
                    ZoneSurface(
                        modifier = Modifier.size(48.dp).semantics { contentDescription = name; selected = picked },
                        shape = CircleShape,
                        accent = picked,
                        // Without glass the accent wash does not show: a ring in the accent marks the chosen one (ZoneSurface draws it only then).
                        border = if (picked) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
                        onClick = {
                            haptics.tick()
                            onPick(shape)
                        }
                    ) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Box(Modifier.size(26.dp).background(LocalContentColor.current, shape.toComposeShape()))
                        }
                    }
                }
            }
        },
        confirmButton = { BoldButton(onClick = onDismiss) { Text(stringResource(R.string.done)) } }
    )
}

@Composable
private fun iconShapeLabel(shape: IconShape): String = stringResource(
    when (shape) {
        IconShape.CIRCLE -> R.string.settings_shape_circle
        IconShape.SQUARE -> R.string.settings_shape_square
        IconShape.COOKIE_4 -> R.string.settings_shape_cookie_4
        IconShape.COOKIE_7 -> R.string.settings_shape_cookie_7
        IconShape.ARCH -> R.string.settings_shape_arch
    }
)

// Store's themeLabel (YAZ/Store, feature/settings/SettingsScreen.kt).
@Composable
private fun themeLabel(mode: ThemeMode): String = stringResource(
    when (mode) {
        ThemeMode.SYSTEM -> R.string.settings_theme_system
        ThemeMode.LIGHT -> R.string.settings_theme_light
        ThemeMode.DARK -> R.string.settings_theme_dark
    }
)
