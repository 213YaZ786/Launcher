package com.yaz.launcher.feature.home

// Fixed data for the screenshot tests: apps with plain coloured icons and names of every length, as Store's Fixtures
// (https://github.com/213YaZ786/Store/blob/main/app/src/test/java/com/yaz/store/feature/home/Fixtures.kt) gives its catalogue.
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import com.yaz.launcher.data.apps.AppEntry
import com.yaz.launcher.data.apps.IconLayers
import com.yaz.launcher.data.apps.LauncherState
import com.yaz.launcher.data.apps.ProfileInfo
import com.yaz.launcher.data.apps.ProfileKind

object Fixtures {

    private val names = listOf(
        "Calculator", "Calendar", "Camera", "Clock", "Contacts", "Dialer", "Files", "Gallery", "Keyboard", "Maps",
        "Messages", "Music", "Notes", "Passwords", "Recorder", "SMS", "Settings", "Store", "Voice", "Browser",
        "Weather", "Translate", "Authenticator for work accounts", "Podcasts", "Wallet", "Health", "Reader", "Radio",
        "Tasks", "Scanner", "Drive", "Docs", "Sheets", "Slides", "Mail", "Chat", "Meet", "Photos", "Books", "Games"
    )

    private val colours = intArrayOf(0xFF6750A4.toInt(), 0xFF0B6A6A.toInt(), 0xFFB3261E.toInt(), 0xFF1D6FA3.toInt(), 0xFF7D5700.toInt(), 0xFF386A20.toInt())

    // A plain colour as the background layer of an adaptive icon, nothing in front.
    private fun icon(index: Int): IconLayers =
        IconLayers(ColorDrawable(colours[index % colours.size]), ColorDrawable(Color.TRANSPARENT), null, null)

    val apps: List<AppEntry> = names.mapIndexed { i, name ->
        AppEntry(
            packageName = "com.example.app$i",
            className = "com.example.app$i.Main",
            label = name,
            user = android.os.Process.myUserHandle(),
            userSerial = 0L,
            profile = ProfileKind.MAIN,
            icon = icon(i)
        )
    }

    /** Usage access granted, nine recent apps. */
    val full = LauncherState(loaded = true, usageGranted = true, apps = apps, recents = apps.shuffled(java.util.Random(7)).take(9))

    // Apps of a work profile (serial 10) and of a private space (serial 11), made as the apps above are, as Store's Fixtures makes its catalogue
    // (https://github.com/213YaZ786/Store/blob/main/app/src/test/java/com/yaz/store/feature/home/Fixtures.kt); AppEntry has a serial and a kind for another profile.
    private fun other(index: Int, name: String, kind: ProfileKind, serial: Long) = AppEntry(
        packageName = "com.example.other$index",
        className = "com.example.other$index.Main",
        label = name,
        user = android.os.Process.myUserHandle(),
        userSerial = serial,
        profile = kind,
        icon = icon(index)
    )

    private val workApps = listOf("Work mail", "Work chat", "Work calendar").mapIndexed { i, n -> other(i, n, ProfileKind.WORK, 10L) }
    private val privateApps = listOf("Vault", "Private notes").mapIndexed { i, n -> other(10 + i, n, ProfileKind.PRIVATE, 11L) }

    /** A work profile (running) and a private space (locked, so its apps are not listed). */
    val withProfiles = LauncherState(
        loaded = true,
        usageGranted = true,
        profiles = listOf(
            ProfileInfo(android.os.Process.myUserHandle(), ProfileKind.MAIN, 0L, quiet = false, unlocked = true),
            ProfileInfo(android.os.Process.myUserHandle(), ProfileKind.WORK, 10L, quiet = false, unlocked = true),
            ProfileInfo(android.os.Process.myUserHandle(), ProfileKind.PRIVATE, 11L, quiet = true, unlocked = true)
        ),
        apps = apps,
        others = workApps,
        recents = apps.shuffled(java.util.Random(7)).take(9)
    )

    /** The work profile paused and the private space unlocked, with their apps. */
    val withPausedWork = withProfiles.copy(
        profiles = withProfiles.profiles.map { if (it.kind == ProfileKind.WORK) it.copy(quiet = true) else it.copy(quiet = false) },
        others = workApps + privateApps
    )

    /** Usage access not granted: A to Z, no Recents. */
    val noUsage = LauncherState(loaded = true, usageGranted = false, apps = apps.sortedBy { it.label })

    /** Usage granted, nothing used yet. */
    val noRecents = LauncherState(loaded = true, usageGranted = true, apps = apps)
}
