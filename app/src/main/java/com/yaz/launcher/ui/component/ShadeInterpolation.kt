package com.yaz.launcher.ui.component

// The curves of Android's Quick Settings shade, ported as they are from LineageOS android_frameworks_base (Apache-2.0, lineage-22.2):
// https://github.com/LineageOS/android_frameworks_base/blob/lineage-22.2/packages/SystemUI/animation/src/com/android/systemui/animation/ShadeInterpolation.kt
import kotlin.math.cos

object ShadeInterpolation {

    /** The alpha of the scrim and the blur ratio during the shade's expansion, [fraction] 0 to 1 (ShadeInterpolation.getNotificationScrimAlpha). */
    fun getNotificationScrimAlpha(fraction: Float): Float {
        val mappedFraction = constrainedMap(0f, 1f, 0f, 0.5f, fraction)
        return interpolateEaseInOut(mappedFraction)
    }

    /** The alpha of the shade's content during its expansion, [fraction] 0 to 1 (ShadeInterpolation.getContentAlpha). */
    fun getContentAlpha(fraction: Float): Float {
        val mappedFraction = constrainedMap(0f, 1f, 0.3f, 1f, fraction)
        return interpolateEaseInOut(mappedFraction)
    }

    // ShadeInterpolation.interpolateEaseInOut.
    private fun interpolateEaseInOut(fraction: Float): Float {
        val mappedFraction = fraction * 1.2f - 0.2f
        return if (mappedFraction <= 0) {
            0f
        } else {
            val oneMinusFrac = 1f - mappedFraction
            (1f - 0.5f * (1f - cos((3.14159f * oneMinusFrac * oneMinusFrac).toDouble()))).toFloat()
        }
    }

    // android.util.MathUtils.constrainedMap (hidden API), rewritten: lerp(rangeMin, rangeMax, saturate(lerpInv(valueMin, valueMax, value))),
    // https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android16-release/core/java/android/util/MathUtils.java
    private fun constrainedMap(rangeMin: Float, rangeMax: Float, valueMin: Float, valueMax: Float, value: Float): Float {
        val inverse = if (valueMax != valueMin) (value - valueMin) / (valueMax - valueMin) else 0f
        return rangeMin + (rangeMax - rangeMin) * inverse.coerceIn(0f, 1f)
    }
}
