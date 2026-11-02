package com.yaz.launcher.data.apps

// The numbers of a legacy icon's scale: IconNormalizer.getScale(float, float, float) and convertToConvexArray (LineageOS
// android_frameworks_libs_systemui lineage-22.2, iconloaderlib/.../IconNormalizer.java), checked on values whose answer follows from its
// constants. The picture measuring needs a real ALPHA_8 bitmap, which Robolectric's legacy graphics cannot copy.
import kotlin.math.sqrt
import org.junit.Assert.assertEquals
import org.junit.Test

class IconNormalizerTest {

    @Test
    fun aSquareFillingItsPictureIsScaledDownToTheSquareAreaFactor() {
        assertEquals(sqrt(375.0f / 576), IconNormalizer.getScale(10000f, 10000f, 10000f), 0.0001f)
    }

    @Test
    fun aShapeSmallerThanTheFactorIsLeftAlone() {
        assertEquals(1f, IconNormalizer.getScale(2500f, 2500f, 10000f), 0f)
    }

    @Test
    fun aHullEmptierThanACircleKeepsTheCircleFactor() {
        // hull / rectangle under pi / 4: scaleRequired is MAX_CIRCLE_AREA_FACTOR.
        assertEquals(sqrt((380.0f / 576) / 0.7f), IconNormalizer.getScale(70f, 100f, 100f), 0.0001f)
    }

    @Test
    fun theBorderOfAHollowRowIsFilledToTheHull() {
        // A left border with a notch in the middle becomes straight, as convertToConvexArray makes it.
        val border = floatArrayOf(0f, 0f, 5f, 0f, 0f)
        IconNormalizer.convertToConvexArray(border, 1, 0, 4)
        assertEquals(listOf(0f, 0f, 0f, 0f, 0f), border.toList())
    }
}
