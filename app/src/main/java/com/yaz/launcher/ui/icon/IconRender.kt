package com.yaz.launcher.ui.icon

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.BlendMode
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import com.yaz.launcher.data.apps.IconLayers
import java.nio.ByteBuffer
import kotlin.math.roundToInt

// The pictures an icon is drawn from, at the size shown (px by px), so the screen only scales nothing and clips to the shape.
// A layer is 108 dp for a 72 dp icon: AdaptiveIconDrawable.getExtraInsetFraction() (1/4) on each side of the visible square, so a layer
// drawn into bounds that start at -extra * px and end at (1 + extra) * px puts the visible part exactly on the bitmap
// (BaseIconFactory.drawIconBitmap and MonoIconThemeController.ClippedMonoDrawable, an InsetDrawable of -extra, do the same;
// https://github.com/LineageOS/android_frameworks_libs_systemui/tree/lineage-22.2/iconloaderlib/src/com/android/launcher3/icons).

/** The icon in colour: background then foreground, [px] wide, cut to [mask] (the shape's path at [px], IconShape.toAndroidPath). */
fun renderIcon(layers: IconLayers, px: Int, mask: Path): Bitmap {
    val bitmap = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    drawLayer(canvas, layers.background, px)
    drawLayer(canvas, layers.foreground, px)
    return masked(bitmap, mask)
}

/**
 * The themed icon, [px] wide, cut to [mask]: the monochrome picture ([renderMonochrome]) tinted with [tint] over a [ground] fill, as
 * ThemedIconDrawable draws it (SRC_IN colour filter on the mono bitmap, https://github.com/LineageOS/android_frameworks_libs_systemui/blob/lineage-22.2/iconloaderlib/src/com/android/launcher3/icons/mono/ThemedIconDrawable.kt).
 * Null if the icon has no monochrome picture.
 */
fun renderThemedIcon(layers: IconLayers, px: Int, mask: Path, ground: Int, tint: Int): Bitmap? {
    val mono = renderMonochrome(layers, px) ?: return null
    val bitmap = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    canvas.drawColor(ground)
    canvas.drawBitmap(mono, 0f, 0f, Paint(Paint.FILTER_BITMAP_FLAG).apply { colorFilter = PorterDuffColorFilter(tint, PorterDuff.Mode.SRC_IN) })
    mono.recycle()
    return masked(bitmap, mask)
}

/**
 * AdaptiveIconDrawable.draw (LineageOS android_frameworks_base lineage-22.2, same file as IconShape.toAndroidPath): the layers are drawn
 * into an offscreen bitmap, which is wrapped in a BitmapShader (CLAMP, CLAMP) set on a Paint(ANTI_ALIAS | DITHER | FILTER_BITMAP) that
 * draws the mask path, so the edge of the shape is anti-aliased (a canvas clipPath is not). Returns a new bitmap; [layers] is recycled.
 */
private fun masked(layers: Bitmap, mask: Path): Bitmap {
    val out = Bitmap.createBitmap(layers.width, layers.height, Bitmap.Config.ARGB_8888)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG or Paint.FILTER_BITMAP_FLAG)
    paint.shader = BitmapShader(layers, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
    Canvas(out).drawPath(mask, paint)
    layers.recycle()
    return out
}

/**
 * The icon's monochrome picture (white with alpha), [px] wide: its own monochrome layer, else the forced one of MonochromeIconFactory
 * ([forcedMonochrome]), as Android 16 QPR2 themes an icon without one. Null if nothing could be made of it.
 */
fun renderMonochrome(layers: IconLayers, px: Int): Bitmap? {
    val mono = layers.monochrome
    if (mono != null) {
        val bitmap = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
        drawLayer(Canvas(bitmap), mono, px)
        return bitmap
    }
    return runCatching { forcedMonochrome(layers, px) }.getOrNull()
}

private fun drawLayer(canvas: Canvas, layer: Drawable, px: Int) {
    val extra = (px * AdaptiveIconDrawable.getExtraInsetFraction()).roundToInt()
    layer.setBounds(-extra, -extra, px + extra, px + extra)
    layer.draw(canvas)
}

/**
 * MonochromeIconFactory.wrap and generateMono (Apache-2.0)
 * https://github.com/LineageOS/android_frameworks_libs_systemui/blob/lineage-22.2/iconloaderlib/src/com/android/launcher3/icons/MonochromeIconFactory.java
 * The icon (background and foreground over black) turned to grey, the average of red, green and blue taken as alpha, the alpha spread
 * over the full range for contrast and flipped when the edge (the part outside the visible square, the icon's background) is the
 * more opaque. Differences: the working bitmap is the whole layer ([px] times 1 + 2 * extra), where the original sizes it from its own
 * icon bitmap; the result is a [px] bitmap holding the visible part only, white with that alpha.
 */
private fun forcedMonochrome(layers: IconLayers, px: Int): Bitmap? {
    val extraFraction = AdaptiveIconDrawable.getExtraInsetFraction()
    val size = (px * (1 + 2 * extraFraction)).roundToInt()
    val pixels = ByteArray(size * size)
    val edgeLength = size * (size - px) / 2

    val flat = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val flatCanvas = Canvas(flat)
    flatCanvas.drawColor(Color.BLACK)
    for (layer in listOf(layers.background, layers.foreground)) {
        layer.setBounds(0, 0, size, size)
        layer.draw(flatCanvas)
    }

    // A matrix which converts the icon to grayscale and then uses the average of the RGB components as alpha.
    val matrix = ColorMatrix().apply { setSaturation(0f) }.array
    matrix[15] = .3333f
    matrix[16] = .3333f
    matrix[17] = .3333f
    matrix[18] = 0f
    matrix[19] = 0f
    val copyPaint = Paint(Paint.FILTER_BITMAP_FLAG).apply {
        blendMode = BlendMode.SRC
        colorFilter = ColorMatrixColorFilter(matrix)
    }
    val alpha = Bitmap.createBitmap(size, size, Bitmap.Config.ALPHA_8)
    Canvas(alpha).drawBitmap(flat, 0f, 0f, copyPaint)
    flat.recycle()

    val buffer = ByteBuffer.wrap(pixels)
    alpha.copyPixelsToBuffer(buffer)
    var min = 0xFF
    var max = 0
    for (b in pixels) {
        min = minOf(min, b.toInt() and 0xFF)
        max = maxOf(max, b.toInt() and 0xFF)
    }
    if (min < max) {
        // Rescale the pixels to increase contrast.
        val range = (max - min).toFloat()
        // To check whether the colours should be flipped, the average of the top and bottom edges, which should be the background
        // colour: when the edge is the more opaque, flip.
        var sum = 0L
        for (i in 0 until edgeLength) {
            sum += pixels[i].toInt() and 0xFF
            sum += pixels[pixels.size - 1 - i].toInt() and 0xFF
        }
        val edgeAverage = if (edgeLength > 0) sum / (edgeLength * 2f) else 0f
        val flip = (edgeAverage - min) / range > .5f
        for (i in pixels.indices) {
            val p = pixels[i].toInt() and 0xFF
            val p2 = ((p - min) * 0xFF / range).roundToInt()
            pixels[i] = (if (flip) 255 - p2 else p2).toByte()
        }
        buffer.rewind()
        alpha.copyPixelsFromBuffer(buffer)
    }

    // The visible part, white with the alpha: the original draws the whole alpha bitmap through an InsetDrawable of -extra.
    val out = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
    val margin = (size - px) / 2f
    val white = Paint(Paint.FILTER_BITMAP_FLAG).apply { color = Color.WHITE }
    Canvas(out).drawBitmap(alpha, Rect(0, 0, size, size), RectF(-margin, -margin, px + margin, px + margin), white)
    alpha.recycle()
    return out
}
