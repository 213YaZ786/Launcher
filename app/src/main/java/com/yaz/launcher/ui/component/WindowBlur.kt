package com.yaz.launcher.ui.component

// The wallpaper blurred behind the panels, in proportion to how far a panel is open.
// - The window API is the official recipe (https://source.android.com/docs/core/display/window-blurs, "Blur behind" and "Guidelines to use
//   window blur in apps"): Window.setBackgroundBlurRadius (the doc's "Background blur", API 31: the blur is drawn under the window surface by the
//   RenderThread with the frame, with no WindowManager relayout per step as `window.attributes = ...` with blurBehindRadius made, which showed
//   the blur late and closed it with lag; the window must be translucent, see themes.xml), WindowManager.addCrossWindowBlurEnabledListener
//   added when the decor view attaches and removed when it detaches, and a fallback while blurs are off (the home screen's scrim is then at
//   least 85 % strong). The doc warns above 150 px, kept as the cap. minSdk is 31: no SDK guard.
// - The radius is the Quick Settings shade's (LineageOS android_frameworks_base, Apache-2.0, lineage-22.2): NotificationShadeDepthController gives
//   blurUtils.blurRadiusOfRatio(ShadeInterpolation.getNotificationScrimAlpha(shadeExpansion))
//   (https://github.com/LineageOS/android_frameworks_base/blob/lineage-22.2/packages/SystemUI/src/com/android/systemui/shade/NotificationShadeDepthController.kt),
//   BlurUtils.blurRadiusOfRatio is 0 at 0, else lerp(min_window_blur_radius, max_shade_window_blur_radius, ratio), 1 px to 60 dp
//   (https://github.com/LineageOS/android_frameworks_base/blob/lineage-22.2/packages/SystemUI/src/com/android/systemui/statusbar/BlurUtils.kt and
//   packages/SystemUI/res/values/dimens.xml).
// - Driving the blur from the state's progress is Trebuchet's DepthController (applyDepthAndBlur from the launcher state's progress, with the
//   cross-window blur listener on the launcher), which uses hidden SurfaceControl APIs: only the public window API is used here
//   (https://github.com/LineageOS/android_packages_apps_Trebuchet/blob/lineage-22.2/quickstep/src/com/android/launcher3/statehandlers/DepthController.java).
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import java.util.function.Consumer
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.distinctUntilChanged

/** The blur at full open (SystemUI's max_shade_window_blur_radius) and the least (min_window_blur_radius, 1 px); the most the doc allows, in px. */
private val FULL_BLUR = 60.dp
private const val MIN_BLUR_PX = 1f
private const val MAX_BLUR_PX = 150

/**
 * Blurs the wallpaper behind the window by the shade's curve of [progress] (0 closed to 1 open), and returns whether the system
 * blurs windows now (false: the caller keeps its scrim strong enough to read). Updates the window only when the whole radius changes.
 */
@Composable
fun rememberWindowBlur(progress: () -> Float): Boolean {
    val view = LocalView.current
    val window = view.context.findActivity()?.window
    val manager = view.context.findActivity()?.windowManager
    var enabled by remember(manager) { mutableStateOf(runCatching { manager?.isCrossWindowBlurEnabled }.getOrNull() ?: false) }
    val full = with(LocalDensity.current) { FULL_BLUR.toPx() }
    val progressNow by rememberUpdatedState(progress)

    // The listener follows the decor view's attachment, as in the doc's MainActivity.
    DisposableEffect(window, manager) {
        if (window == null || manager == null) return@DisposableEffect onDispose {}
        val listener = Consumer<Boolean> { on -> enabled = on }
        var registered = false
        fun register() {
            if (!registered) {
                registered = true
                manager.addCrossWindowBlurEnabledListener(listener)
                enabled = manager.isCrossWindowBlurEnabled
            }
        }
        fun unregister() {
            if (registered) {
                registered = false
                manager.removeCrossWindowBlurEnabledListener(listener)
            }
        }
        val decor = window.decorView
        val attach = object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) = register()
            override fun onViewDetachedFromWindow(v: View) = unregister()
        }
        decor.addOnAttachStateChangeListener(attach)
        if (decor.isAttachedToWindow) register()
        onDispose {
            decor.removeOnAttachStateChangeListener(attach)
            unregister()
            window.setBackgroundBlurRadius(0)
        }
    }

    LaunchedEffect(window, enabled, full) {
        if (window == null) return@LaunchedEffect
        snapshotFlow {
            if (enabled) blurRadiusOfRatio(ShadeInterpolation.getNotificationScrimAlpha(progressNow().coerceIn(0f, 1f)), full) else 0
        }
            .distinctUntilChanged()
            .collect { radius -> window.setBackgroundBlurRadius(radius) }
    }
    return enabled
}

// SystemUI's BlurUtils.blurRadiusOfRatio (https://github.com/LineageOS/android_frameworks_base/blob/lineage-22.2/packages/SystemUI/src/com/android/systemui/statusbar/BlurUtils.kt):
// 0 stays 0, else lerp(min, max, ratio); capped at the doc's limit.
private fun blurRadiusOfRatio(ratio: Float, full: Float): Int {
    if (ratio == 0f) return 0
    return (MIN_BLUR_PX + (full - MIN_BLUR_PX) * ratio).roundToInt().coerceAtMost(MAX_BLUR_PX)
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
