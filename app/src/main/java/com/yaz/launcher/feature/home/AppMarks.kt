package com.yaz.launcher.feature.home

// The marks drawn over an app icon, read from [LocalAppMarks] (fed by HomeScreen from the model).
// - The notification dot: a filled circle on the shape's edge at the top end of the icon, after Launcher3's DotRenderer (position, size, kept inside the icon).
//   Launcher3 colours it with the theme attribute notificationDotColor (BubbleTextView.applyDotState), not with the icon's colour: here the theme's primary.
//   Difference: a thin ring of the surface colour instead of the soft shadow, so it reads on any icon. No count.
// - The update mark: a small badge at the bottom start (the profile's badge is at the bottom end), red as Store's warning on its updates
//   (YAZ/Store at HEAD, feature/home/HomeUpdates.kt, colorScheme.error).
import android.graphics.Color as NativeColor
import android.graphics.Paint as NativePaint
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.graphics.drawscope.scale
import kotlin.math.cos
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.foundation.Canvas
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.yaz.launcher.R
import com.yaz.launcher.data.apps.AppEntry
import com.yaz.launcher.data.apps.ProfileKind
import com.yaz.launcher.data.notify.DotKey
import com.yaz.launcher.ui.icon.AppIcons

/** What the icons carry: the apps with a notification dot, and the packages with an update waiting in Store. */
data class AppMarks(val dots: Set<DotKey> = emptySet(), val updates: Set<String> = emptySet()) {
    fun dot(entry: AppEntry): Boolean = DotKey(entry.packageName, entry.user) in dots

    /** Store updates the owner's apps only. */
    fun update(entry: AppEntry): Boolean = entry.profile == ProfileKind.MAIN && entry.packageName in updates
}

val LocalAppMarks = compositionLocalOf { AppMarks() }

// AccelerateDecelerateInterpolator.getInterpolation, ObjectAnimator's default interpolator
// (https://github.com/aosp-mirror/platform_frameworks_base/blob/android-16.0.0_r1/core/java/android/view/animation/AccelerateDecelerateInterpolator.java#getInterpolation).
private val AccelerateDecelerateInterpolator = Easing { input -> (cos((input + 1) * Math.PI) / 2.0f).toFloat() + 0.5f }

/**
 * BubbleTextView.applyDotState and animateDotScale: the dot's scale (mDotParams.scale, the DOT_SCALE_PROPERTY) is 1 with a dot and 0 without.
 * When a dot is first added or removed on an icon that is shown (`animate && (wasDotted ^ isDotted) && isShown()`), `animateDotScale(newDotScale)`
 * runs the scale to the new value with an ObjectAnimator (its default duration, 300 ms, and AccelerateDecelerate); a running one is cancelled first
 * (cancelDotScaleAnim, here the LaunchedEffect's restart). Otherwise (the icon's first composition) the scale is set at once:
 * `mDotParams.scale = newDotScale`. The scale is read in the draw phase.
 * (https://github.com/LineageOS/android_packages_apps_Trebuchet/blob/lineage-22.2/src/com/android/launcher3/BubbleTextView.java#applyDotState,
 * https://github.com/LineageOS/android_packages_apps_Trebuchet/blob/lineage-22.2/src/com/android/launcher3/BubbleTextView.java#animateDotScale)
 */
@Composable
fun applyDotState(isDotted: Boolean): Animatable<Float, AnimationVector1D> {
    val newDotScale = if (isDotted) 1f else 0f
    val mDotParams = remember { Animatable(newDotScale) }
    LaunchedEffect(isDotted) {
        // wasDotted ^ isDotted: the scale is not yet where the dot now puts it (a run cut short by the effect's restart counts as well).
        if (mDotParams.value != newDotScale) mDotParams.animateTo(newDotScale, tween(300, easing = AccelerateDecelerateInterpolator))
    }
    return mDotParams
}

// DotRenderer's SIZE_PERCENTAGE: the dot is 22.8 % of the icon.
// https://github.com/LineageOS/android_frameworks_libs_systemui/blob/lineage-22.2/iconloaderlib/src/com/android/launcher3/icons/DotRenderer.java
private const val DOT_SIZE = 0.228f

/**
 * DotRenderer.draw: the dot centred on the point of the shape nearest its top end corner ([dotPosition]), moved inward when it would leave the
 * icon's bounds (the offsetX and offsetY clamps against the canvas bounds), then the circle. The ring under it is the surface colour.
 */
@Composable
fun NotificationDot(shape: Shape, size: Dp, modifier: Modifier = Modifier, dotScale: () -> Float = { 1f }) {
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val px = with(density) { size.toPx() }
    val rtl = layoutDirection == LayoutDirection.Rtl
    val centre = remember(shape, px, rtl) { dotPosition(shapePath(shape, px, layoutDirection, density), px, if (rtl) -1f else 1f) }
    val dotColor = MaterialTheme.colorScheme.primary
    val ringColor = MaterialTheme.colorScheme.surface
    val description = stringResource(R.string.notification_dot)
    Canvas(modifier.size(size).semantics { contentDescription = description }) {
        val radius = (DOT_SIZE * this.size.width / 2f).coerceAtLeast(1f)
        val ring = 1.5.dp.toPx()
        val outer = radius + ring
        val x = if (rtl) maxOf(centre.x, outer) else minOf(centre.x, this.size.width - outer)
        val y = maxOf(centre.y, outer)
        // DotRenderer.draw scales the canvas by the dot's scale about the dot's centre.
        scale(dotScale(), Offset(x, y)) {
            drawCircle(ringColor, outer, Offset(x, y))
            drawCircle(dotColor, radius, Offset(x, y))
        }
    }
}

// The path of the shape at the size shown (all the app's icon shapes are generic paths); a circle otherwise.
private fun shapePath(shape: Shape, px: Float, direction: LayoutDirection, density: Density): Path =
    (shape.createOutline(Size(px, px), direction, density) as? Outline.Generic)?.path ?: Path().apply { addOval(Rect(0f, 0f, px, px)) }

/**
 * getPathPoint, DotRenderer: the point where the shape's edge meets a thin triangle from the icon's centre towards the top corner on the
 * [direction] side (1 right, -1 left): the triangle is cut with the path (Path.Op.INTERSECT) and the first point of what is left taken
 * with a PathMeasure. Here in pixels, not as a part of the size. A shape that gives nothing falls back to the circle's point at 45 degrees.
 */
internal fun dotPosition(path: Path, size: Float, direction: Float): Offset {
    val half = size / 2
    // A small delta so that the triangle is never flat.
    val delta = 1f
    val x = half + direction * half
    val triangle = Path().apply {
        moveTo(half, half)
        lineTo(x + delta * direction, 0f)
        lineTo(x, -delta)
        close()
    }
    val cut = Path.combine(PathOperation.Intersect, triangle, path)
    if (cut.isEmpty) return Offset(half + direction * half * 0.7071f, half - half * 0.7071f)
    return PathMeasure().apply { setPath(cut, false) }.getPosition(0f)
}

/**
 * The mark of an update waiting in Store, at the bottom start, 36 % of the icon: Material 3's Badge look (androidx's Badge.kt,
 * containerColor = error, contentColor = onError, https://github.com/androidx/androidx/blob/androidx-main/compose/material3/material3/src/commonMain/kotlin/androidx/compose/material3/Badge.kt),
 * a full disc in the error colour with Material Symbols' arrow_upward in onError at 70 % of the disc, over the soft shadow of Launcher3's
 * DotRenderer (the key shadow and the ambient one of ShadowGenerator.Builder.drawShadow: blur a 24th of the disc, key offset a 16th, alphas 7 and 88,
 * https://github.com/LineageOS/android_frameworks_libs_systemui/blob/lineage-22.2/iconloaderlib/src/com/android/launcher3/icons/DotRenderer.java and
 * ShadowGenerator.java). The disc is 5/6 of the box so the shadow stays inside it. Its description is read with the cell's.
 */
@Composable
fun UpdateMark(size: Dp, modifier: Modifier = Modifier) {
    val disc = MaterialTheme.colorScheme.error
    val arrow = MaterialTheme.colorScheme.onError
    val paint = remember { NativePaint(NativePaint.ANTI_ALIAS_FLAG or NativePaint.FILTER_BITMAP_FLAG) }
    Box(
        modifier
            .size(size)
            .drawBehind {
                val diameter = this.size.minDimension * (5f / 6f)
                val blur = diameter / 24f
                val key = diameter / 16f
                drawIntoCanvas { canvas ->
                    paint.color = disc.toArgb()
                    // Builder.drawShadow: the key shadow (below the disc), then the ambient one (all round), then the disc.
                    paint.setShadowLayer(blur, 0f, key, NativeColor.argb(7, 0, 0, 0))
                    canvas.nativeCanvas.drawCircle(center.x, center.y, diameter / 2f, paint)
                    paint.setShadowLayer(blur, 0f, 0f, NativeColor.argb(88, 0, 0, 0))
                    canvas.nativeCanvas.drawCircle(center.x, center.y, diameter / 2f, paint)
                    paint.clearShadowLayer()
                    canvas.nativeCanvas.drawCircle(center.x, center.y, diameter / 2f, paint)
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            AppIcons.ArrowUpward,
            contentDescription = stringResource(R.string.update_waiting),
            tint = arrow,
            modifier = Modifier.size(size * (5f / 6f) * 0.7f)
        )
    }
}
