package com.yaz.launcher.di

// Ported from Store (YAZ/Store at HEAD, di/AppModule.kt), itself from Files and Calculator (https://github.com/213YaZ786/Calculator, di/AppModule.kt):
// Koin's single as in Koin's Android documentation (https://insert-koin.io/docs/reference/koin-android/start).
// The settings and the model of the apps, with the scope the model works in (off the main thread).
import com.yaz.launcher.data.apps.AppsRepository
import com.yaz.launcher.data.apps.HiddenApps
import com.yaz.launcher.data.apps.LauncherModel
import com.yaz.launcher.data.apps.RemovedRecents
import com.yaz.launcher.data.settings.SettingsStore
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

/** Single composition root. */
val appModule = module {
    single { SettingsStore(androidContext()) }
    single { RemovedRecents(File(androidContext().filesDir, "removed-recents.json")) }
    single { HiddenApps(File(androidContext().filesDir, "hidden-apps.json")) }
    // The model works only between the home screen's start and stop (MainActivity), in this scope.
    single {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        LauncherModel(androidContext(), AppsRepository(androidContext(), scope), get(), get(), scope)
    }
}
