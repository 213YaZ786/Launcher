package com.yaz.launcher

// Ported from Store (YAZ/Store at HEAD, StoreApplication.kt), itself from Files and Calculator (https://github.com/213YaZ786/Calculator, CalculatorApplication.kt).
// Left out of Store's version: the install session cleanup and the background check.
import android.app.Application
import com.yaz.launcher.di.appModule
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin
import org.koin.core.logger.Level

class LauncherApplication : Application() {
    // Store's StoreApplication.onCreate.
    override fun onCreate() {
        super.onCreate()
        // Nothing of the app starts in an isolated process.
        if (android.os.Process.isIsolated()) return
        startKoin {
            androidLogger(if (BuildConfig.DEBUG) Level.DEBUG else Level.NONE)
            androidContext(this@LauncherApplication)
            modules(appModule)
        }
    }
}
