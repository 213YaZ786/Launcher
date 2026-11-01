package com.yaz.launcher.ui.icon

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/**
 * The app's own icon set, as in the other apps: Google's Material Icons,
 * filled style, 24dp grid, Apache License 2.0
 * (https://github.com/google/material-design-icons).
 * Ported from Store (YAZ/Store at HEAD, ui/icon/AppIcons.kt), only the icons the shared panes kit (SearchPill) uses.
 */
object AppIcons {

    /** Close. */
    val Close: ImageVector by lazy { build("Close", "M19 6.41L17.59 5 12 10.59 6.41 5 5 6.41 10.59 12 5 17.59 6.41 19 12 13.41 17.59 19 19 17.59 13.41 12z") }

    /** Search. */
    val Search: ImageVector by lazy { build("Search", "M15.5 14h-.79l-.28-.27C15.41 12.59 16 11.11 16 9.5 16 5.91 13.09 3 9.5 3S3 5.91 3 9.5 5.91 16 9.5 16c1.61 0 3.09-.59 4.23-1.57l.27.28v.79l5 4.99L20.49 19l-4.99-5zm-6 0C7.01 14 5 11.99 5 9.5S7.01 5 9.5 5 14 7.01 14 9.5 11.99 14 9.5 14z") }

    // The icons of the panels and the guide: paths as Store's and SMS's AppIcons have them (YAZ/Store at HEAD and YAZ/SMS at HEAD,
    // ui/icon/AppIcons.kt), Material Icons (Apache-2.0, https://github.com/google/material-design-icons).

    /** The apps panel's tab, and the guide's home step. */
    val Apps: ImageVector by lazy { build("Apps", "M4 8h4V4H4v4zm6 12h4v-4h-4v4zm-6 0h4v-4H4v4zm0-6h4v-4H4v4zm6 0h4v-4h-4v4zm6-10v4h4V4h-4zm-6 4h4V4h-4v4zm6 6h4v-4h-4v4zm0 6h4v-4h-4v4z") }

    /** A step done. */
    val CheckCircle: ImageVector by lazy { build("CheckCircle", "M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zM9.29 16.29 5.7 12.7c-.39-.39-.39-1.02 0-1.41.39-.39 1.02-.39 1.41 0L10 14.17l6.88-6.88c.39-.39 1.02-.39 1.41 0 .39.39.39 1.02 0 1.41l-7.59 7.59c-.38.39-1.02.39-1.41 0z") }

    /** The usage access step, Recents' tab. */
    val Update: ImageVector by lazy { build("Update", "M21,10.12h-6.78l2.74-2.82c-2.73-2.7-7.15-2.8-9.88-0.1c-2.73,2.71-2.73,7.08,0,9.79s7.15,2.71,9.88,0 C18.32,15.65,19,14.08,19,12.1h2c0,1.98-0.88,4.55-2.64,6.29c-3.51,3.48-9.21,3.48-12.72,0c-3.5-3.47-3.53-9.11-0.02-12.58 s9.14-3.47,12.65,0L21,3V10.12z M12.5,8v4.25l3.5,2.08l-0.72,1.21L11,13V8H12.5z") }

    /**
     * The notification dots step of the guide. Material Icons Round "notifications" (Apache-2.0,
     * https://github.com/google/material-design-icons/blob/master/src/social/notifications/materialiconsround/24px.svg).
     */
    val Notifications: ImageVector by lazy {
        build(
            "Notifications",
            "M12 22c1.1 0 2-.9 2-2h-4c0 1.1.89 2 2 2zm6-6v-5c0-3.07-1.64-5.64-4.5-6.32V4c0-.83-.67-1.5-1.5-1.5s-1.5.67-1.5 1.5v.68C7.63 5.36 6 7.92 6 11v5l-1.29 1.29c-.63.63-.19 1.71.7 1.71h13.17c.89 0 1.34-1.08.71-1.71L18 16z"
        )
    }

    /**
     * The mark of an app with an update waiting in Store, and its menu item. Material Icons Round "arrow_circle_up" (Apache-2.0,
     * https://github.com/google/material-design-icons/blob/master/src/action/arrow_circle_up/materialiconsround/24px.svg).
     */
    val ArrowCircleUp: ImageVector by lazy {
        build(
            "ArrowCircleUp",
            "M12,20c-4.41,0-8-3.59-8-8s3.59-8,8-8s8,3.59,8,8S16.41,20,12,20 M12,22c5.52,0,10-4.48,10-10c0-5.52-4.48-10-10-10 C6.48,2,2,6.48,2,12C2,17.52,6.48,22,12,22L12,22z " +
                "M11,12l0,3c0,0.55,0.45,1,1,1h0c0.55,0,1-0.45,1-1l0-3h1.79 c0.45,0,0.67-0.54,0.35-0.85l-2.79-2.79c-0.2-0.2-0.51-0.2-0.71,0l-2.79,2.79C8.54,11.46,8.76,12,9.21,12H11z"
        )
    }

    /** Settings, and the Apps panel's Settings row. */
    val Settings: ImageVector by lazy { build("Settings", "M19.14,12.94c0.04-0.3,0.06-0.61,0.06-0.94c0-0.32-0.02-0.64-0.07-0.94l2.03-1.58c0.18-0.14,0.23-0.41,0.12-0.61 l-1.92-3.32c-0.12-0.22-0.37-0.29-0.59-0.22l-2.39,0.96c-0.5-0.38-1.03-0.7-1.62-0.94L14.4,2.81c-0.04-0.24-0.24-0.41-0.48-0.41 h-3.84c-0.24,0-0.43,0.17-0.47,0.41L9.25,5.35C8.66,5.59,8.12,5.92,7.63,6.29L5.24,5.33c-0.22-0.08-0.47,0-0.59,0.22L2.74,8.87 C2.62,9.08,2.66,9.34,2.86,9.48l2.03,1.58C4.84,11.36,4.8,11.69,4.8,12s0.02,0.64,0.07,0.94l-2.03,1.58 c-0.18,0.14-0.23,0.41-0.12,0.61l1.92,3.32c0.12,0.22,0.37,0.29,0.59,0.22l2.39-0.96c0.5,0.38,1.03,0.7,1.62,0.94l0.36,2.54 c0.05,0.24,0.24,0.41,0.48,0.41h3.84c0.24,0,0.44-0.17,0.47-0.41l0.36-2.54c0.59-0.24,1.13-0.56,1.62-0.94l2.39,0.96 c0.22,0.08,0.47,0,0.59-0.22l1.92-3.32c0.12-0.22,0.07-0.47-0.12-0.61L19.14,12.94z M12,15.6c-1.98,0-3.6-1.62-3.6-3.6 s1.62-3.6,3.6-3.6s3.6,1.62,3.6,3.6S13.98,15.6,12,15.6z") }

    /** App info. */
    val Info: ImageVector by lazy {
        build(
            "Info",
            "M11,7h2v2h-2zM11,11h2v6h-2zM12,2C6.48,2 2,6.48 2,12s4.48,10 10,10 10,-4.48 10,-10" +
                "S17.52,2 12,2zM12,20c-4.41,0 -8,-3.59 -8,-8s3.59,-8 8,-8 8,3.59 8,8 -3.59,8 -8,8z"
        )
    }

    /** Uninstall. */
    val Delete: ImageVector by lazy { build("Delete", "M6 19c0 1.1.9 2 2 2h8c1.1 0 2-.9 2-2V7H6v12zM19 4h-3.5l-1-1h-5l-1 1H5v2h14V4z") }

    /** Force stop. */
    val Warning: ImageVector by lazy { build("Warning", "M4.47 21h15.06c1.54 0 2.5-1.67 1.73-3L13.73 4.99c-.77-1.33-2.69-1.33-3.46 0L2.74 18c-.77 1.33.19 3 1.73 3zM12 14c-.55 0-1-.45-1-1v-2c0-.55.45-1 1-1s1 .45 1 1v2c0 .55-.45 1-1 1zm1 4h-2v-2h2v2z") }

    /** An app's shortcut. */
    val Play: ImageVector by lazy { build("Play", "M8 5v14l11-7z") }

    /** The guide, shown again. */
    val AutoAwesome: ImageVector by lazy { build("AutoAwesome", "M19 9l1.25-2.75L23 5l-2.75-1.25L19 1l-1.25 2.75L15 5l2.75 1.25L19 9zm-7.5.5L9 4 6.5 9.5 1 12l5.5 2.5L9 20l2.5-5.5L17 12l-5.5-2.5zM19 15l-1.25 2.75L15 19l2.75 1.25L19 23l1.25-2.75L23 19l-2.75-1.25L19 15z") }

    // The icons of the update mark and the panel header: Material Symbols, Rounded, filled, weight 400 (Apache-2.0,
    // https://github.com/google/material-design-icons/tree/master/symbols/android, as the @material-symbols/svg-400 package ships them),
    // drawn on the symbols' own 960 grid (viewBox "0 -960 960 960").

    /** The arrow of the update mark. Material Symbols arrow_upward. */
    val ArrowUpward: ImageVector by lazy {
        symbol("ArrowUpward", "M450-686 223-459q-9 9-21 9t-21-9q-9-9-9-21t9-21l278-278q5-5 10-7t11-2q6 0 11 2t10 7l278 278q9 9 9 21t-9 21q-9 9-21 9t-21-9L510-686v496q0 13-8.5 21.5T480-160q-13 0-21.5-8.5T450-190v-496Z")
    }

    /** The next alarm. Material Symbols alarm. */
    val Alarm: ImageVector by lazy {
        symbol(
            "Alarm",
            "M512-450v-160q0-13-8.5-21.5T482-640q-13 0-21.5 8.5T452-610v172q0 6 2 11t7 10l118 118q9 9 21 9t21-9q9-9 9-21t-9-21L512-450ZM339.5-110q-65.5-28-114-77t-77-114Q120-366 120-441q0-74 28.5-139.5t77-114.5q48.5-49 114-77T479-800q74 0 139.5 28T733-695q49 49 77 114.5T838-441q0 75-28 140t-77 114q-49 49-114.5 77T479-82q-74 0-139.5-28ZM71-688q-9-9-8.5-21t9.5-21l121-117q9-8 21.5-7.5T235-846q9 9 8.5 21t-9.5 21L113-687q-9 8-21.5 7.5T71-688Zm816 0q-8 8-20.5 8.5T845-687L724-804q-9-8-9.5-20.5T723-846q8-8 20.5-8.5T765-847l121 117q9 8 9.5 20.5T887-688Z"
        )
    }

    /** Charging. Material Symbols bolt. */
    val Bolt: ImageVector by lazy {
        symbol("Bolt", "M360-360H217q-18 0-26.5-16t2.5-31l338-488q8-11 20-15t24 1q12 5 19 16t5 24l-39 309h176q19 0 27 17t-4 32L388-66q-8 10-20.5 13T344-55q-11-5-17.5-16T322-95l38-265Z")
    }

    /** Missed calls. Material Symbols phone_missed. */
    val PhoneMissed: ImageVector by lazy {
        symbol(
            "PhoneMissed",
            "m136-146-94-94q-9-9-8.5-21t8.5-22q82-96 197-146.5T480-480q126 0 241 50.5T918-283q8 10 8.5 22t-8.5 21l-94 94q-8 8-23 9t-24-6l-114-85q-6-5-9-11t-3-13v-139q-42-16-85.5-22.5T480-420q-42 0-85.5 6.5T309-391v139q0 7-3 13t-9 11l-114 85q-12 9-24.5 8T136-146Zm80.5-477.5Q208-632 208-645v-165q0-13 8.5-21.5T238-840h165q13 0 21.5 8.5T433-810q0 13-8.5 21.5T403-780h-96l173 171 210-210q9-9 21.5-9.5T733-820q9 9 9 21.5t-9 21.5L522-566q-18 18-43 18t-42-18L268-737v92q0 13-8.5 21.5T238-615q-13 0-21.5-8.5Z"
        )
    }

    /** Unread messages. Material Symbols sms. */
    val Sms: ImageVector by lazy {
        symbol(
            "Sms",
            "M240-240 131-131q-14 14-32.5 6.34Q80-132.31 80-152v-668q0-24 18-42t42-18h680q24 0 42 18t18 42v520q0 24-18 42t-42 18H240Zm94.5-294.5Q346-546 346-563t-11.5-28.5Q323-603 306-603t-28.5 11.5Q266-580 266-563t11.5 28.5Q289-523 306-523t28.5-11.5Zm177 0Q523-546 523-563t-11.5-28.5Q500-603 483-603t-28.5 11.5Q443-580 443-563t11.5 28.5Q466-523 483-523t28.5-11.5Zm170 0Q693-546 693-563t-11.5-28.5Q670-603 653-603t-28.5 11.5Q613-580 613-563t11.5 28.5Q636-523 653-523t28.5-11.5Z"
        )
    }

    /** The ringer on vibrate. Material Symbols mobile_vibrate. */
    val Vibrate: ImageVector by lazy {
        symbol(
            "Vibrate",
            "M301-120q-24.75 0-42.37-17.63Q241-155.25 241-180v-600q0-24.75 17.63-42.38Q276.25-840 301-840h359q24.75 0 42.38 17.62Q720-804.75 720-780v600q0 24.75-17.62 42.37Q684.75-120 660-120H301Zm199-529q9-9 9-21t-9-21q-9-9-21-9t-21 9q-9 9-9 21t9 21q9 9 21 9t21-9ZM0-395v-170q0-12.75 8.68-21.38 8.67-8.62 21.5-8.62 12.82 0 21.32 8.62Q60-577.75 60-565v170q0 12.75-8.68 21.37-8.67 8.63-21.5 8.63Q17-365 8.5-373.63 0-382.25 0-395Zm120 88v-346q0-12.75 8.68-21.38 8.67-8.62 21.5-8.62 12.82 0 21.32 8.62 8.5 8.63 8.5 21.38v346q0 12.75-8.68 21.37-8.67 8.63-21.5 8.63-12.82 0-21.32-8.63-8.5-8.62-8.5-21.37Zm780-88v-170q0-12.75 8.68-21.38 8.67-8.62 21.5-8.62 12.82 0 21.32 8.62 8.5 8.63 8.5 21.38v170q0 12.75-8.68 21.37-8.67 8.63-21.5 8.63-12.82 0-21.32-8.63-8.5-8.62-8.5-21.37Zm-120 88v-346q0-12.75 8.68-21.38 8.67-8.62 21.5-8.62 12.82 0 21.32 8.62 8.5 8.63 8.5 21.38v346q0 12.75-8.68 21.37-8.67 8.63-21.5 8.63-12.82 0-21.32-8.63-8.5-8.62-8.5-21.37Z"
        )
    }

    /** The ringer silent. Material Symbols volume_off. */
    val SilentRinger: ImageVector by lazy {
        symbol(
            "SilentRinger",
            "M681-188q-17 12-35.5 22T607-148q-12 5-24.5 0T565-165q-5-11 .5-22t17.5-16q15-5 28.5-12t26.5-17L473-397v165q0 20-18.5 27.5T422-211L273-360H143q-13 0-21.5-8.5T113-390v-180q0-13 8.5-21.5T143-600h126L70-799q-9-9-8.5-21.5T71-842q9-9 21.5-9t21.5 9l721 721q9 9 9 21.5T835-78q-9 9-22 9t-22-9L681-188Zm92-293q0-93-52.5-168.5T583-759q-12-5-17-16t0-22q5-12 17.5-16.5t25.5.5q101 41 162.5 130.5T833-481q0 38-7.5 75T802-334q-8 17-19.5 20.5T760-315q-11-5-16.5-14.5t.5-20.5q15-30 22-63t7-68ZM576-628q38 23 57.5 63.5T653-480v15q0 7-2 15-2 10-11 13t-17-5l-61-61q-5-5-7-10t-2-11v-91q0-9 7.5-13.5t15.5.5Zm-196-57q-5-5-5-11t5-11l42-42q14-14 32.5-6.5T473-728v100q0 10-9.5 13.5T447-618l-67-67Z"
        )
    }

    /** Do not disturb. Material Symbols do_not_disturb_on. */
    val DoNotDisturb: ImageVector by lazy {
        symbol(
            "DoNotDisturb",
            "M310-453h340q12.75 0 21.38-8.68 8.62-8.67 8.62-21.5 0-12.82-8.62-21.32-8.63-8.5-21.38-8.5H310q-12.75 0-21.37 8.68-8.63 8.67-8.63 21.5 0 12.82 8.63 21.32 8.62 8.5 21.37 8.5ZM480.27-80q-82.74 0-155.5-31.5Q252-143 197.5-197.5t-86-127.34Q80-397.68 80-480.5t31.5-155.66Q143-709 197.5-763t127.34-85.5Q397.68-880 480.5-880t155.66 31.5Q709-817 763-763t85.5 127Q880-563 880-480.27q0 82.74-31.5 155.5Q817-252 763-197.68q-54 54.31-127 86Q563-80 480.27-80Z"
        )
    }

    // A Material Symbols icon: Store's builder on the symbols' 960 grid, whose paths run from y -960 to 0 (a group moves them down).
    private fun symbol(name: String, pathData: String): ImageVector =
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
