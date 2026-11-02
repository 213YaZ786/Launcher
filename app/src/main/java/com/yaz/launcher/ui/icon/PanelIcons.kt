package com.yaz.launcher.ui.icon

// The icons of Hide, Hidden apps, Activities and the pinned shortcuts: Google's Material Icons, filled style, 24dp grid, Apache License 2.0
// (https://github.com/google/material-design-icons, action/visibility, action/visibility_off, action/code, action/push_pin... paths as
// YAZ/Dialer's ui/icon/AppIcons.kt (Visibility, VisibilityOff, Code) and YAZ/SMS's ui/icon/AppIcons.kt (PushPin) have them). In their own file,
// beside AppIcons, because AppIcons.kt is being changed by another task; they move into it afterwards.
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

object PanelIcons {

    /** Unhide, and the Hidden apps screen. */
    val Visibility: ImageVector by lazy {
        build("Visibility", "M12 4.5C7 4.5 2.73 7.61 1 12c1.73 4.39 6 7.5 11 7.5s9.27-3.11 11-7.5c-1.73-4.39-6-7.5-11-7.5zM12 17c-2.76 0-5-2.24-5-5s2.24-5 5-5 5 2.24 5 5-2.24 5-5 5zm0-8c-1.66 0-3 1.34-3 3s1.34 3 3 3 3-1.34 3-3-1.34-3-3-3z")
    }

    /** Hide, and Settings' Hidden apps. */
    val VisibilityOff: ImageVector by lazy {
        build(
            "VisibilityOff",
            "M12 7c2.76 0 5 2.24 5 5 0 .65-.13 1.26-.36 1.83l2.92 2.92c1.51-1.26 2.7-2.89 3.43-4.75-1.73-4.39-6-7.5-11-7.5-1.4 0-2.74.25-3.98.7l2.16 2.16C10.74 7.13 11.35 7 12 7zM2 4.27l2.28 2.28.46.46C3.08 8.3 1.78 10.02 1 12c1.73 4.39 6 7.5 11 7.5 1.55 0 3.03-.3 4.38-.84l.42.42L19.73 22 21 20.73 3.27 3 2 4.27zM7.53 9.8l1.55 1.55c-.05.21-.08.43-.08.65 0 1.66 1.34 3 3 3 .22 0 .44-.03.65-.08l1.55 1.55c-.67.33-1.41.53-2.2.53-2.76 0-5-2.24-5-5 0-.79.2-1.53.53-2.2zm4.31-.78l3.15 3.15.02-.16c0-1.66-1.34-3-3-3l-.17.01z"
        )
    }

    /** Activities. */
    val Code: ImageVector by lazy { build("Code", "M9.4 16.6L4.8 12l4.6-4.6L8 6l-6 6 6 6 1.4-1.4zm5.2 0l4.6-4.6-4.6-4.6L16 6l6 6-6 6-1.4-1.4z") }

    /** Add to Apps. */
    val PushPin: ImageVector by lazy {
        build(
            "PushPin",
            "M16,9V4l1,0c0.55,0,1-0.45,1-1v0c0-0.55-0.45-1-1-1H7C6.45,2,6,2.45,6,3v0 c0,0.55,0.45,1,1,1l1,0v5" +
                "c0,1.66-1.34,3-3,3h0v2h5.97v7l1,1l1-1v-7H19v-2h0C17.34,12,16,10.66,16,9z"
        )
    }

    // The private space and the work profile on their tile of the Apps grid. Lock and Unlock: Material Symbols, rounded, filled, 400 weight, Apache License 2.0
    // (https://github.com/google/material-design-icons, symbols; paths from marella/material-symbols svg/400/rounded/lock-fill.svg and lock_open_right-fill.svg,
    // https://github.com/marella/material-symbols/blob/main/svg/400/rounded/lock-fill.svg, 960 grid with its y origin at -960).

    /** The private space locked (a tap unlocks it). */
    val Lock: ImageVector by lazy {
        buildSymbol(
            "Lock",
            "M220-80q-24.75 0-42.37-17.63Q160-115.25 160-140v-434q0-24.75 17.63-42.38Q195.25-634 220-634h70v-96q0-78.85 55.61-134.42Q401.21-920 480.11-920q78.89 0 134.39 55.58Q670-808.85 670-730v96h70q24.75 0 42.38 17.62Q800-598.75 800-574v434q0 24.75-17.62 42.37Q764.75-80 740-80H220Zm314.5-222.03Q557-324.06 557-355q0-30-22.67-54.5t-54.5-24.5q-31.83 0-54.33 24.5t-22.5 55q0 30.5 22.67 52.5t54.5 22q31.83 0 54.33-22.03ZM350-634h260v-96q0-54.17-37.88-92.08-37.88-37.92-92-37.92T388-822.08q-38 37.91-38 92.08v96Z"
        )
    }

    /** The private space unlocked (a tap locks it). */
    val LockOpen: ImageVector by lazy {
        buildSymbol(
            "LockOpen",
            "M534.5-302.03Q557-324.06 557-355q0-30-22.67-54.5t-54.5-24.5q-31.83 0-54.33 24.5t-22.5 55q0 30.5 22.67 52.5t54.5 22q31.83 0 54.33-22.03ZM220-80q-24.75 0-42.37-17.63Q160-115.25 160-140v-434q0-24.75 17.63-42.38Q195.25-634 220-634h330v-96q0-78.85 55.58-134.42Q661.15-920 740-920q73 0 126 47.5T928-755q2 10.16-6.59 17.58-8.6 7.42-21.5 7.42-12.91 0-21.41-6T868-755q-9-45-44.7-75T740-860q-54.17 0-92.08 37.92Q610-784.17 610-730v96h130q24.75 0 42.38 17.62Q800-598.75 800-574v434q0 24.75-17.62 42.37Q764.75-80 740-80H220Z"
        )
    }

    /**
     * The Install apps cell of the unlocked private space. Material Symbols "add", rounded, filled, 400 weight, Apache License 2.0
     * (https://github.com/marella/material-symbols/blob/main/svg/400/rounded/add-fill.svg).
     */
    val Add: ImageVector by lazy {
        buildSymbol(
            "Add",
            "M450-450H230q-12.75 0-21.37-8.68-8.63-8.67-8.63-21.5 0-12.82 8.63-21.32 8.62-8.5 21.37-8.5h220v-220q0-12.75 8.68-21.38 8.67-8.62 21.5-8.62 12.82 0 21.32 8.62 8.5 8.63 8.5 21.38v220h220q12.75 0 21.38 8.68 8.62 8.67 8.62 21.5 0 12.82-8.62 21.32-8.63 8.5-21.38 8.5H510v220q0 12.75-8.68 21.37-8.67 8.63-21.5 8.63-12.82 0-21.32-8.63-8.5-8.62-8.5-21.37v-220Z"
        )
    }

    // Work and Work off: Material Icons Round (filled, 24dp grid), Apache License 2.0, the same family as the Material Symbols above; Symbols has no
    // work_off (https://github.com/google/material-design-icons, src/action/work/materialiconsround/24px.svg and src/action/work_off/materialiconsround/24px.svg).

    /** The work profile running (a tap pauses it). */
    val Work: ImageVector by lazy {
        build("Work", "M20 6h-4V4c0-1.11-.89-2-2-2h-4c-1.11 0-2 .89-2 2v2H4c-1.11 0-1.99.89-1.99 2L2 19c0 1.11.89 2 2 2h16c1.11 0 2-.89 2-2V8c0-1.11-.89-2-2-2zm-6 0h-4V4h4v2z")
    }

    /** The work profile paused (a tap resumes it). */
    val WorkOff: ImageVector by lazy {
        build(
            "WorkOff",
            "M4.11 2.54c-.39-.39-1.02-.39-1.41 0-.39.39-.39 1.02 0 1.41L4.74 6H4c-1.11 0-1.99.89-1.99 2L2 19c0 1.11.89 2 2 2h15.74l1.29 1.29c.39.39 1.02.39 1.41 0 .39-.39.39-1.02 0-1.41L4.11 2.54zM10 4h4v2h-3.6L22 17.6V8c0-1.11-.89-2-2-2h-4V4c0-1.11-.89-2-2-2h-4c-.99 0-1.8.7-1.96 1.64L10 5.6V4z"
        )
    }

    // A Material Symbols path (960 grid, y from -960 to 0): the group moves it down into the 0 to 960 viewport.
    private fun buildSymbol(name: String, pathData: String): ImageVector =
        ImageVector.Builder(
            name = name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 960f,
            viewportHeight = 960f
        ).apply {
            addGroup(translationY = 960f)
            addPath(
                pathData = PathParser().parsePathString(pathData).toNodes(),
                fill = SolidColor(Color.Black)
            )
            clearGroup()
        }.build()

    // Store's AppIcons.build.
    private fun build(name: String, pathData: String): ImageVector =
        ImageVector.Builder(
            name = name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            addPath(
                pathData = PathParser().parsePathString(pathData).toNodes(),
                fill = SolidColor(Color.Black)
            )
        }.build()
}
