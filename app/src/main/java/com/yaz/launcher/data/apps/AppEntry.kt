package com.yaz.launcher.data.apps

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.InsetDrawable
import android.os.Build
import android.os.UserHandle

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
     * the FLAG_DISABLED_* bits that hold now (a suspended package, a paused or locked profile).
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
         * LEGACY_ICON_SCALE and centred (the longer side keeps the aspect). Difference: the IconNormalizer's own scale of
         * the picture (it measures the picture's real bounds) is left out, so 1 is used.
         */
        fun of(icon: Drawable, badge: Bitmap? = null): IconLayers {
            if (icon is AdaptiveIconDrawable) {
                // getMonochrome is API 33; below, no themed layer.
                val mono = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) icon.monochrome else null
                return IconLayers(icon.background ?: ColorDrawable(Color.TRANSPARENT), icon.foreground ?: ColorDrawable(Color.TRANSPARENT), mono, badge)
            }
            // LEGACY_ICON_SCALE = 0.7f * (1 / (1 + 2 * extraInsetFraction)).
            val scale = 0.7f / (1 + 2 * AdaptiveIconDrawable.getExtraInsetFraction())
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
