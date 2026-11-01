package com.yaz.launcher.ui.component

// The two tabs on the screen edges, each a window of its own that blurs the wallpaper behind it and draws nothing else.
// The wallpaper is drawn by Android behind our window, so a Compose blur cannot reach it: the blur has to come from a window. The recipe is
// AOSP's "Background blur" (https://source.android.com/docs/core/display/window-blurs): a translucent window, Window.setBackgroundBlurRadius, a
// background drawable (a GradientDrawable) whose corners the blur takes as its own outline, and the cross-window blur state to fall back on.
// - The window is a Compose Dialog (androidx.compose.ui.window.Dialog), which has a real Window reached through DialogWindowProvider; it is made
//   not modal and not focusable (FLAG_NOT_TOUCH_MODAL, FLAG_NOT_FOCUSABLE) so Back and every touch outside go to the activity, without dim
//   (https://developer.android.com/reference/android/view/WindowManager.LayoutParams).
// - The cross-window blur state is the app's own, rememberCrossWindowBlur (ZoneDialog.kt, AOSP's addCrossWindowBlurEnabledListener recipe).
// - The window being a tab of the screen edge, like Samsung Edge panels' handle (https://developer.samsung.com/one-ui), only blurred: the user's
//   wish is a patch of the wallpaper made soft, no fill and no rim.
import com.yaz.launcher.feature.home.onSwipeDown
import android.graphics.Color as PlatformColor
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ShapeDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import kotlin.math.roundToInt

private val TAB_WIDTH = 12.dp
private val TAB_HEIGHT = 64.dp

/** The blur at 100 in Settings, in dp (about 79 px on a 420 dpi phone: the doc calls 80 px frosted glass), and the most the doc allows, in px. */
private val FULL_TAB_BLUR = 30.dp
private const val MAX_BLUR_PX = 150

/** The fill that stands in for the blur while the system does not blur windows: white at 4 % at the default 30, in proportion. */
private const val FALLBACK_ALPHA_AT_DEFAULT = 0.04f

/**
 * The tab of an edge, flush against it (12 dp by 64 dp, centred, the screen cutting its outer side so only the inner corners are rounded,
 * Material 3's Small): a window that blurs the wallpaper behind it by [strength] (0 to 100, times a full radius of 30 dp), and a tap on it
 * calls [onClick]. At 0 no window exists: the tap target the caller keeps around the tab still works. The window exists only while [shown],
 * and while the screen is started (an activity that stops takes its dialog windows with it). While Android does not blur windows, the tab is a
 * very faint white fill instead.
 */
@Composable
fun EdgeTab(start: Boolean, strength: Int, label: String, shown: Boolean, onClick: () -> Unit, onSwipeDown: (() -> Unit)? = null) {
    val value = strength.coerceIn(0, 100)
    val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    if (!shown || value == 0 || !lifecycle.isAtLeast(Lifecycle.State.STARTED)) return
    val blur = rememberCrossWindowBlur()
    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false, usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        TabWindowSetup(start, value, blur)
        // The tap target of the window; the 48 dp one around it in the activity carries the description for TalkBack, so this one stays silent.
        // The tab is a window of its own, so a swipe down that starts on it never reaches the activity's gesture: it reads the same swipe
        // here (SwipeDown.kt, Initial pass, nothing consumed); the tab is shown only while no panel is open, so the screen is idle.
        Box(
            Modifier
                .size(TAB_WIDTH, TAB_HEIGHT)
                .clearAndSetSemantics { }
                .onSwipeDown(enabled = onSwipeDown != null, idle = { true }, onSwipeDown = { onSwipeDown?.invoke() })
                .sinkClickable(label, onClick = onClick)
        )
    }
}

// Sets up the dialog's window as the doc's updateWindowForBlurs does: the blur radius on the window, the background drawable that outlines the blur.
@Composable
private fun TabWindowSetup(start: Boolean, value: Int, blur: Boolean) {
    val view = LocalView.current
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val fullPx = with(density) { FULL_TAB_BLUR.toPx() }
    val widthPx = with(density) { TAB_WIDTH.toPx() }
    val heightPx = with(density) { TAB_HEIGHT.toPx() }
    val radius = (value / 100f * fullPx).roundToInt().coerceIn(1, MAX_BLUR_PX)
    val fallback = (FALLBACK_ALPHA_AT_DEFAULT * value / 30f).coerceIn(0f, 1f)
    DisposableEffect(view, start, layoutDirection, blur, radius, fallback, widthPx) {
        val window = (view.parent as? DialogWindowProvider)?.window
        if (window != null) {
            window.addFlags(
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
            )
            window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            // As wide as the tab and no wider: a Dialog window without the platform width fills the screen's width, and even not modal
            // it takes the swipes across the whole middle band. Window.setLayout sets the width and height of the window's attributes
            // (https://developer.android.com/reference/android/view/Window#setLayout(int,%20int), WindowManager.LayoutParams.width).
            window.setLayout(kotlin.math.ceil(widthPx).toInt(), WindowManager.LayoutParams.WRAP_CONTENT)
            window.setGravity((if (start) Gravity.START else Gravity.END) or Gravity.CENTER_VERTICAL)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                window.attributes = window.attributes.apply {
                    layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
                }
            }
            // The outline the blur takes: Material 3's Small on the inner corners, square on the outer ones (the corner order is top left,
            // top right, bottom right, bottom left, an x and a y each). On the left edge the inner side is the right, mirrored in right-to-left.
            val round = ShapeDefaults.Small.topStart.toPx(Size(widthPx, heightPx), density)
            val onLeft = start == (layoutDirection == LayoutDirection.Ltr)
            val left = if (onLeft) 0f else round
            val right = if (onLeft) round else 0f
            val background = GradientDrawable().apply {
                cornerRadii = floatArrayOf(left, left, right, right, right, right, left, left)
                setColor(if (blur) PlatformColor.TRANSPARENT else PlatformColor.argb((fallback * 255).roundToInt(), 255, 255, 255))
            }
            window.setBackgroundDrawable(background)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) window.setBackgroundBlurRadius(if (blur) radius else 0)
        }
        onDispose { }
    }
}
