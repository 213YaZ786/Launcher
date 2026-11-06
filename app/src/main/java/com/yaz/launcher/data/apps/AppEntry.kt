package com.yaz.launcher.data.apps

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.InsetDrawable
import android.os.Build
import android.os.UserHandle
import java.nio.ByteBuffer
import kotlin.math.sqrt

/**
 * What the pure parts of the model (the list, the ranking) need to know about an app: its place (package,
 * activity, profile) and its label. Kept apart from [AppEntry] so they run on the JVM with plain test classes.
 */
interface Listed {
    val packageName: String
    val className: String
    /** The profile's serial number (UserManager.getSerialNumberForUser), 0 for the owner. */
    val userSerial: Long
    val label: String
}

/** The place of an app in the list: one launcher activity of one package in one profile. */
data class AppKey(val packageName: String, val className: String, val userSerial: Long)

val Listed.key: AppKey get() = AppKey(packageName, className, userSerial)

/**
 * One launcher activity of an app, as the apps panel shows it. After Trebuchet's AppInfo
 * (https://github.com/LineageOS/android_packages_apps_Trebuchet/blob/lineage-22.2/src/com/android/launcher3/model/data/AppInfo.java):
 * component, user, title, icon; the profile's kind stands for its UserIconInfo.
 */
data class AppEntry(
    override val packageName: String,
    override val className: String,
    override val label: String,
    val user: UserHandle,
    override val userSerial: Long,
    val profile: ProfileKind,
    /** The icon's layers, already loaded; the profile's badge is apart, drawn by the screen over the shape. */
    val icon: IconLayers,
    /**
     * What the icon was made from (the package's apk path, version, flags, the screen density): the same stamp means the same icon, so
     * the entry is kept and its layers with it (the caches of the screen read the layers by identity).
     */
    val stamp: String = "",
    /**
     * ItemInfoWithIcon.runtimeStatusFlags (https://github.com/LineageOS/android_packages_apps_Trebuchet/blob/lineage-22.2/src/com/android/launcher3/model/data/ItemInfoWithIcon.java):
     * the FLAG_DISABLED_* bits that hold now (a suspended package, a paused or locked profile), and FLAG_SYSTEM_YES or FLAG_SYSTEM_NO, set when the
     * entry is loaded (AppInfo.updateRuntimeFlagsForActivityTarget).
     */
    val runtimeStatusFlags: Int = 0,
    /**
     * ItemInfo.contentDescription: the label TalkBack reads, with the profile named ("Chrome, Work" style), made once when the entry loads
     * (IconCache sets it from getUserBadgedLabel). [label] is what is drawn.
     */
    val contentDescription: String = label
) : Listed {
    // The icon counts (by identity): a package updated with a new icon must reach the flow that shows it. So do the disabled flags, so that
    // a suspended app reaches the screen.
    override fun equals(other: Any?): Boolean =
        other is AppEntry && key == other.key && label == other.label && icon === other.icon &&
            runtimeStatusFlags == other.runtimeStatusFlags && contentDescription == other.contentDescription
    override fun hashCode(): Int = 31 * key.hashCode() + label.hashCode()

    // ItemInfoWithIcon.isDisabled (https://github.com/LineageOS/android_packages_apps_Trebuchet/blob/lineage-22.2/src/com/android/launcher3/model/data/ItemInfoWithIcon.java#L160-L162).
    fun isDisabled(): Boolean = (runtimeStatusFlags and FLAG_DISABLED_MASK) != 0

    companion object {
        // ItemInfoWithIcon's FLAG_DISABLED_SUSPENDED and FLAG_DISABLED_QUIET_USER, FLAG_DISABLED_MASK reduced to the two an app of the list can have
        // (https://github.com/LineageOS/android_packages_apps_Trebuchet/blob/lineage-22.2/src/com/android/launcher3/model/data/ItemInfoWithIcon.java#L56-L111).
        /** Indicates that the icon is disabled as the app is suspended. */
        const val FLAG_DISABLED_SUSPENDED = 1 shl 2

        /** Indicates that the icon is disabled as the user is in quiet mode. */
        const val FLAG_DISABLED_QUIET_USER = 1 shl 3

        const val FLAG_DISABLED_MASK = FLAG_DISABLED_SUSPENDED or FLAG_DISABLED_QUIET_USER

        // ItemInfoWithIcon's FLAG_SYSTEM_YES, FLAG_SYSTEM_NO and FLAG_SYSTEM_MASK, the same bits
        // (https://github.com/LineageOS/android_packages_apps_Trebuchet/blob/lineage-22.2/src/com/android/launcher3/model/data/ItemInfoWithIcon.java#L79-L86).
        /** Indicates that the app is a system app. */
        const val FLAG_SYSTEM_YES = 1 shl 6

        /** Indicates that the app is not a system app. */
        const val FLAG_SYSTEM_NO = 1 shl 7

        const val FLAG_SYSTEM_MASK = FLAG_SYSTEM_YES or FLAG_SYSTEM_NO
    }
}

/**
 * An app's icon in layers, so the screen can draw it in the shape the user chose: [background] and [foreground] of the adaptive
 * icon (a legacy icon wrapped as BaseIconFactory.wrapToAdaptiveIcon does), its [monochrome] layer when it has one (API 33), and
 * the profile's [badge] (a transparent picture with the badge in its bottom end corner) for an app of another profile.
 * The layers are 108 dp wide for a 72 dp icon (AdaptiveIconDrawable's viewport): only the middle 72/108 shows in the shape.
 */
class IconLayers(val background: Drawable, val foreground: Drawable, val monochrome: Drawable?, val badge: Bitmap?) {
    companion object {
        /**
         * wrapToAdaptiveIcon and createScaledDrawable, BaseIconFactory (Apache-2.0)
         * https://github.com/LineageOS/android_frameworks_libs_systemui/blob/lineage-22.2/iconloaderlib/src/com/android/launcher3/icons/BaseIconFactory.java
         * An adaptive icon keeps its layers; any other drawable becomes the foreground over a white background, scaled by
         * the IconNormalizer's scale of its real shape (the convex hull of its opaque pixels, [IconNormalizer.getScale]) times
         * LEGACY_ICON_SCALE and centred (the longer side keeps the aspect). Difference: the mask shape detection (outShape) is left out,
         * the user's shape being one of five, so the icon is always scaled; without a [normalizer] (previews, tests) the scale is 1.
         */
        fun of(icon: Drawable, badge: Bitmap? = null, normalizer: IconNormalizer? = null): IconLayers {
            if (icon is AdaptiveIconDrawable) {
                // getMonochrome is API 33; below, no themed layer.
                val mono = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) icon.monochrome else null
                return IconLayers(icon.background ?: ColorDrawable(Color.TRANSPARENT), icon.foreground ?: ColorDrawable(Color.TRANSPARENT), mono, badge)
            }
            // LEGACY_ICON_SCALE = 0.7f * (1 / (1 + 2 * extraInsetFraction)).
            val scale = (normalizer?.getScale(icon) ?: 1f) * 0.7f / (1 + 2 * AdaptiveIconDrawable.getExtraInsetFraction())
            val h = icon.intrinsicHeight.toFloat()
            val w = icon.intrinsicWidth.toFloat()
            var scaleX = scale
            var scaleY = scale
            if (h > w && w > 0) scaleX *= w / h else if (w > h && h > 0) scaleY *= h / w
            val insetX = (1 - scaleX) / 2
            val insetY = (1 - scaleY) / 2
            return IconLayers(ColorDrawable(Color.WHITE), InsetDrawable(icon, insetX, insetY, insetX, insetY), null, badge)
        }
    }
}

/**
 * IconNormalizer (Apache-2.0)
 * https://github.com/LineageOS/android_frameworks_libs_systemui/blob/lineage-22.2/iconloaderlib/src/com/android/launcher3/icons/IconNormalizer.java
 * Measures a legacy icon's visible shape so it can be scaled to fit the adaptive icon's frame: the picture is drawn into an ALPHA_8 bitmap
 * of twice the icon size, the first and last visible pixel of each row make the borders, which are made convex (the hull), and the hull's
 * area against its bounding rectangle's says how close the shape is to a circle or a square. Differences: the adaptive branch
 * (normalizeAdaptiveIcon), the optional out bounds, the mask path and isShape's shape detection are left out; the constructor takes no Context
 * (only isShape's outline stroke used it). One instance serves one thread at a time ([getScale] is synchronized, as the original).
 */
class IconNormalizer(iconBitmapSize: Int) {

    // Use twice the icon size as maximum size to avoid scaling down twice.
    private val mMaxSize = iconBitmapSize * 2
    private val mBitmap: Bitmap = Bitmap.createBitmap(mMaxSize, mMaxSize, Bitmap.Config.ALPHA_8)
    private val mCanvas = Canvas(mBitmap)
    private val mPixels = ByteArray(mMaxSize * mMaxSize)

    // for each y, stores the position of the leftmost x and the rightmost x
    private val mLeftBorder = FloatArray(mMaxSize)
    private val mRightBorder = FloatArray(mMaxSize)

    /**
     * IconNormalizer.getScale(Drawable, RectF, Path, boolean[]): the amount by which [d] should be scaled (in both dimensions) so that
     * it matches the design guidelines for a launcher icon. We first calculate the convex hull of the visible portion of the icon. This
     * hull then compared with the bounding rectangle of the hull to find how closely it resembles a circle and a square, by comparing the
     * ratio of the areas. Refer [MAX_CIRCLE_AREA_FACTOR] and [MAX_SQUARE_AREA_FACTOR].
     */
    @Synchronized
    fun getScale(d: Drawable): Float {
        var width = d.intrinsicWidth
        var height = d.intrinsicHeight
        if (width <= 0 || height <= 0) {
            width = if (width <= 0 || width > mMaxSize) mMaxSize else width
            height = if (height <= 0 || height > mMaxSize) mMaxSize else height
        } else if (width > mMaxSize || height > mMaxSize) {
            val max = maxOf(width, height)
            width = mMaxSize * width / max
            height = mMaxSize * height / max
        }

        mBitmap.eraseColor(Color.TRANSPARENT)
        d.setBounds(0, 0, width, height)
        d.draw(mCanvas)

        val buffer = ByteBuffer.wrap(mPixels)
        buffer.rewind()
        mBitmap.copyPixelsToBuffer(buffer)

        // Overall bounds of the visible icon.
        var topY = -1
        var bottomY = -1
        var leftX = mMaxSize + 1
        var rightX = -1

        // Create border by going through all pixels one row at a time and for each row find the first and the last non-transparent pixel.
        // Set those values to mLeftBorder and mRightBorder and use -1 if there are no visible pixel in the row.

        // buffer position
        var index = 0
        // buffer shift after every row, width of buffer = mMaxSize
        val rowSizeDiff = mMaxSize - width
        // first and last position for any row.
        var firstX: Int
        var lastX: Int

        for (y in 0 until height) {
            firstX = -1
            lastX = -1
            for (x in 0 until width) {
                if ((mPixels[index].toInt() and 0xFF) > MIN_VISIBLE_ALPHA) {
                    if (firstX == -1) {
                        firstX = x
                    }
                    lastX = x
                }
                index++
            }
            index += rowSizeDiff

            mLeftBorder[y] = firstX.toFloat()
            mRightBorder[y] = lastX.toFloat()

            // If there is at least one visible pixel, update the overall bounds.
            if (firstX != -1) {
                bottomY = y
                if (topY == -1) {
                    topY = y
                }

                leftX = minOf(leftX, firstX)
                rightX = maxOf(rightX, lastX)
            }
        }

        if (topY == -1 || rightX == -1) {
            // No valid pixels found. Do not scale.
            return 1f
        }

        convertToConvexArray(mLeftBorder, 1, topY, bottomY)
        convertToConvexArray(mRightBorder, -1, topY, bottomY)

        // Area of the convex hull
        var area = 0f
        for (y in 0 until height) {
            if (mLeftBorder[y] <= -1) {
                continue
            }
            area += mRightBorder[y] - mLeftBorder[y] + 1
        }

        // Area of the rectangle required to fit the convex hull
        val rectArea = ((bottomY + 1 - topY) * (rightX + 1 - leftX)).toFloat()
        return getScale(area, rectArea, (width * height).toFloat())
    }

    companion object {
        // Ratio of icon visible area to full icon size for a square shaped icon
        private const val MAX_SQUARE_AREA_FACTOR = 375.0f / 576

        // Ratio of icon visible area to full icon size for a circular shaped icon
        private const val MAX_CIRCLE_AREA_FACTOR = 380.0f / 576

        private const val CIRCLE_AREA_BY_RECT = Math.PI.toFloat() / 4

        // Slope used to calculate icon visible area to full icon size for any generic shaped icon.
        private const val LINEAR_SCALE_SLOPE = (MAX_CIRCLE_AREA_FACTOR - MAX_SQUARE_AREA_FACTOR) / (1 - CIRCLE_AREA_BY_RECT)

        private const val MIN_VISIBLE_ALPHA = 40

        /** IconNormalizer.getScale(float, float, float): the scale from the hull's area, its bounding rectangle's and the picture's. */
        internal fun getScale(hullArea: Float, boundingArea: Float, fullArea: Float): Float {
            val hullByRect = hullArea / boundingArea
            val scaleRequired = if (hullByRect < CIRCLE_AREA_BY_RECT) {
                MAX_CIRCLE_AREA_FACTOR
            } else {
                MAX_SQUARE_AREA_FACTOR + LINEAR_SCALE_SLOPE * (1 - hullByRect)
            }

            val areaScale = hullArea / fullArea
            // Use sqrt of the final ratio as the images is scaled across both width and height.
            return if (areaScale > scaleRequired) sqrt(scaleRequired / areaScale) else 1f
        }

        /**
         * IconNormalizer.convertToConvexArray: modifies [xCoordinates] to represent a convex border. Fills in all missing values (except on
         * either ends) with appropriate values. [direction] is 1 for the left border and -1 for the right border, [topY] the first Y position
         * (inclusive) with a valid value and [bottomY] the last one.
         */
        internal fun convertToConvexArray(xCoordinates: FloatArray, direction: Int, topY: Int, bottomY: Int) {
            val total = xCoordinates.size
            // The tangent at each pixel.
            val angles = FloatArray(total - 1)

            val first = topY // First valid y coordinate
            var last = -1 // Last valid y coordinate which didn't have a missing value

            var lastAngle = Float.MAX_VALUE

            for (i in topY + 1..bottomY) {
                if (xCoordinates[i] <= -1) {
                    continue
                }
                var start: Int

                if (lastAngle == Float.MAX_VALUE) {
                    start = first
                } else {
                    var currentAngle = (xCoordinates[i] - xCoordinates[last]) / (i - last)
                    start = last
                    // If this position creates a concave angle, keep moving up until we find a position which creates a convex angle.
                    if ((currentAngle - lastAngle) * direction < 0) {
                        while (start > first) {
                            start--
                            currentAngle = (xCoordinates[i] - xCoordinates[start]) / (i - start)
                            if ((currentAngle - angles[start]) * direction >= 0) {
                                break
                            }
                        }
                    }
                }

                // Reset from last check
                lastAngle = (xCoordinates[i] - xCoordinates[start]) / (i - start)
                // Update all the points from start.
                for (j in start until i) {
                    angles[j] = lastAngle
                    xCoordinates[j] = xCoordinates[start] + lastAngle * (j - start)
                }
                last = i
            }
        }
    }
}
