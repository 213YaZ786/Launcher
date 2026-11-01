package com.yaz.launcher.ui.icon

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.center
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.graphics.shapes.CornerRounding
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.circle
import androidx.graphics.shapes.rectangle
import androidx.graphics.shapes.star
import androidx.graphics.shapes.toPath
import androidx.graphics.shapes.transformed
import com.yaz.launcher.data.settings.IconShape
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

// The five shapes of the Pixel Launcher (Android 16 QPR2), each the polygon androidx's MaterialShapes defines
// (https://github.com/androidx/androidx/blob/androidx-main/compose/material3/material3/src/commonMain/kotlin/androidx/compose/material3/MaterialShapes.kt),
// built here from androidx graphics-shapes (Apache-2.0) because the material3 of this app does not expose MaterialShapes (its opt-in annotation is internal).

/** The Compose shape of an [IconShape], scaled to the size it is given. */
fun IconShape.toComposeShape(): Shape = shapes.getValue(this)

private val polygons: Map<IconShape, RoundedPolygon> by lazy { IconShape.entries.associateWith { polygonOf(it).normalized() } }

private val shapes: Map<IconShape, Shape> by lazy { IconShape.entries.associateWith { PolygonShape(polygons.getValue(it)) } }

private val masks = java.util.concurrent.ConcurrentHashMap<Pair<IconShape, Int>, android.graphics.Path>()

/**
 * The shape as an android.graphics.Path [px] by [px], the mask an icon is drawn through (AdaptiveIconDrawable's mMask, drawn with an
 * anti-aliased paint: https://github.com/LineageOS/android_frameworks_base/blob/lineage-22.2/graphics/java/android/graphics/drawable/AdaptiveIconDrawable.java).
 * The same polygon as [toComposeShape], scaled then centred as [PolygonShape] does. RoundedPolygon.toPath, androidx graphics-shapes
 * (https://developer.android.com/reference/kotlin/androidx/graphics/shapes/package-summary). Cached per shape and size; the path is only read.
 */
fun IconShape.toAndroidPath(px: Int): android.graphics.Path = masks.getOrPut(this to px) {
    val path = polygons.getValue(this).toPath()
    path.transform(android.graphics.Matrix().apply { setScale(px.toFloat(), px.toFloat()) })
    val bounds = android.graphics.RectF()
    path.computeBounds(bounds, true)
    path.offset(px / 2f - bounds.centerX(), px / 2f - bounds.centerY())
    path
}

private val round15 = CornerRounding(radius = .15f)
private val round20 = CornerRounding(radius = .2f)
private val round30 = CornerRounding(radius = .3f)
private val round50 = CornerRounding(radius = .5f)
private val round100 = CornerRounding(radius = 1f)

/** MaterialShapes.circle, square, arch, cookie4 and cookie7 (before their normalized()). */
private fun polygonOf(shape: IconShape): RoundedPolygon = when (shape) {
    IconShape.CIRCLE -> RoundedPolygon.circle(numVertices = 10)
    IconShape.SQUARE -> RoundedPolygon.rectangle(width = 1f, height = 1f, rounding = round30)
    IconShape.ARCH -> RoundedPolygon(numVertices = 4, perVertexRounding = listOf(round100, round100, round20, round20))
        .transformed(rotation(-135f))
    IconShape.COOKIE_4 -> customPolygon(
        listOf(PointNRound(Offset(1.237f, 1.236f), CornerRounding(0.258f)), PointNRound(Offset(0.500f, 0.918f), CornerRounding(0.233f))),
        reps = 4
    )
    IconShape.COOKIE_7 -> RoundedPolygon.star(numVerticesPerRadius = 7, innerRadius = .75f, rounding = round50)
        .transformed(rotation(-90f))
}

private fun rotation(degrees: Float) = android.graphics.Matrix().apply { setRotate(degrees) }

private data class PointNRound(val o: Offset, val r: CornerRounding = CornerRounding.Unrounded)

/** MaterialShapes.customPolygon and doRepeat (the branch without mirroring): the points turned about the centre, [reps] times. */
private fun customPolygon(points: List<PointNRound>, reps: Int, center: Offset = Offset(0.5f, 0.5f)): RoundedPolygon {
    val np = points.size
    val actual = (0 until np * reps).map {
        PointNRound(points[it % np].o.rotateDegrees((it / np) * 360f / reps, center), points[it % np].r)
    }
    return RoundedPolygon(
        vertices = FloatArray(actual.size * 2) { ix -> actual[ix / 2].o.let { if (ix % 2 == 0) it.x else it.y } },
        perVertexRounding = actual.map { it.r },
        centerX = center.x,
        centerY = center.y
    )
}

private fun Offset.rotateDegrees(angle: Float, center: Offset): Offset {
    val a = angle / 360f * 2 * PI.toFloat()
    val off = this - center
    return Offset(off.x * cos(a) - off.y * sin(a), off.x * sin(a) + off.y * cos(a)) + center
}

/**
 * RoundedPolygon.toShape, material3 MaterialShapes.kt (same URL): the normalized polygon's path scaled to the size, then centred in it.
 */
private class PolygonShape(polygon: RoundedPolygon) : Shape {
    private val base: Path = polygon.toPath().asComposePath()

    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val path = Path()
        path.addPath(base)
        path.transform(Matrix().apply { scale(x = size.width, y = size.height) })
        path.translate(size.center - path.getBounds().center)
        return Outline.Generic(path)
    }
}
