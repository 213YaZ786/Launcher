package com.yaz.launcher.data.store

// The apps of ours with an update waiting in Store, for the mark on their icon. Store's door answers the method "pending" with the packages
// under "packages" (YAZ/Store at HEAD, app/src/main/java/com/yaz/store/data/door/Door.kt, PENDING_UPDATES and PACKAGES); a refusal is a Bundle
// without it. Store's package, its door's authority and the check that it is ours are StoreLink's (core/update/StoreLink.kt, read, not copied).
import android.content.Context
import android.os.Process
import com.yaz.launcher.core.update.StoreLink
import com.yaz.launcher.data.apps.AppActions

object StoreMarks {

    private const val PENDING_UPDATES = "pending"
    private const val PACKAGES = "packages"

    /**
     * The packages with an update waiting, empty when Store is missing, not ours, too old to answer or refuses. The call is StoreLink.knock's:
     * the provider's package compared to Store's, then ContentProviderClient.call through an unstable client
     * (https://developer.android.com/reference/android/content/ContentResolver#acquireUnstableContentProviderClient(java.lang.String)),
     * the answer read from the Bundle. Off the main thread.
     */
    fun pending(context: Context): Set<String> = runCatching {
        val app = context.applicationContext
        val pkg = StoreLink.storePackage(app) ?: return@runCatching emptySet<String>()
        val authority = StoreLink.authority(pkg)
        @Suppress("DEPRECATION")
        val owner = app.packageManager.resolveContentProvider(authority, 0)?.packageName
        if (owner != pkg) return@runCatching emptySet<String>()
        val answer = app.contentResolver.acquireUnstableContentProviderClient(authority)?.use { client ->
            client.call(PENDING_UPDATES, null, null)
        }
        answer?.getStringArray(PACKAGES)?.toSet().orEmpty()
    }.getOrDefault(emptySet())

    /**
     * Opens Store. Its manifest exposes only its launcher activity (no link to an app's page), so that is what opens: LauncherApps.startMainActivity
     * through [AppActions.launch], on the component PackageManager.getLaunchIntentForPackage names
     * (https://developer.android.com/reference/android/content/pm/PackageManager#getLaunchIntentForPackage(java.lang.String)).
     */
    fun open(context: Context): Boolean {
        val pkg = StoreLink.storePackage(context) ?: return false
        val component = context.packageManager.getLaunchIntentForPackage(pkg)?.component ?: return false
        return AppActions.launch(context, component, Process.myUserHandle())
    }
}
