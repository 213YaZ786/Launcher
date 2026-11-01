package com.yaz.launcher.ui.component

// Ported from Store (YAZ/Store at HEAD, ui/component/LoadingMark.kt), itself from Files and Calculator (https://github.com/213YaZ786/Calculator, ui/component/LoadingMark.kt):
// same signature and the same infinite clock, drawing the placeholder icon's glyph (Material Symbols "apps", rounded,
// Apache License 2.0, https://github.com/google/material-design-icons, symbols/android/apps; the path is that of
// marella/material-symbols svg/400/rounded/apps-fill.svg, on a grid running from y -960 to 0) instead of Store's storefront.
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.yaz.launcher.R
import kotlin.math.PI
import kotlin.math.sin

/**
 * Launcher's loading mark, the launcher icon in motion: the glyph breathes
 * while [running], and rests otherwise. [progress] is not drawn.
 */
// Store's LoadingMark.
@Composable
fun LoadingMark(
    modifier: Modifier = Modifier,
    size: Dp = 32.dp,
    running: Boolean = true,
    @Suppress("UNUSED_PARAMETER") progress: Float = 0f
) {
    // The launcher icon's own tone (ic_launcher_glyph), so the mark is the icon by day and by night.
    val tone = colorResource(R.color.ic_launcher_glyph)
    val glyph = remember { PathParser().parsePathString(APPS).toPath() }
    val transition = rememberInfiniteTransition(label = "launcher")
    val clock by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(LOOP_MILLIS, easing = LinearEasing), RepeatMode.Restart),
        label = "clock"
    )

    Canvas(modifier.size(size)) {
        val breath = if (running) 1f + BREATH * sin(2f * PI.toFloat() * clock) else 1f
        val factor = this.size.minDimension * PLATE / GLYPH_WIDTH * breath
        val middle = center
        withTransform({
            // The glyph's centre, (480, -480) on its grid, to the canvas centre.
            translate(middle.x, middle.y)
            scale(factor, factor, androidx.compose.ui.geometry.Offset.Zero)
            translate(-480f, 480f)
        }) {
            drawPath(glyph, tone)
        }
    }
}

/** Material Symbols "apps", rounded, filled, on its grid (x 0 to 960, y -960 to 0). */
private const val APPS =
    "M179-179q-19-19-19-47t19-47q19-19 47-19t47 19q19 19 19 47t-19 47q-19 19-47 19t-47-19Zm254 0q-19-19-19-47t19-47q19-19 47-19t47 19q19 19 19 47t-19 47q-19 19-47 19t-47-19Zm254 0q-19-19-19-47t19-47q19-19 47-19t47 19q19 19 19 47t-19 47q-19 19-47 19t-47-19ZM179-433q-19-19-19-47t19-47q19-19 47-19t47 19q19 19 19 47t-19 47q-19 19-47 19t-47-19Zm254 0q-19-19-19-47t19-47q19-19 47-19t47 19q19 19 19 47t-19 47q-19 19-47 19t-47-19Zm254 0q-19-19-19-47t19-47q19-19 47-19t47 19q19 19 19 47t-19 47q-19 19-47 19t-47-19ZM179-687q-19-19-19-47t19-47q19-19 47-19t47 19q19 19 19 47t-19 47q-19 19-47 19t-47-19Zm254 0q-19-19-19-47t19-47q19-19 47-19t47 19q19 19 19 47t-19 47q-19 19-47 19t-47-19Zm254 0q-19-19-19-47t19-47q19-19 47-19t47 19q19 19 19 47t-19 47q-19 19-47 19t-47-19Z"

/** The glyph's width on its grid (160 to 800). */
private const val GLYPH_WIDTH = 640f
/** The glyph's share of the mark. */
private const val PLATE = 0.94f
private const val BREATH = 0.05f
private const val LOOP_MILLIS = 1800
