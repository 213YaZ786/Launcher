package com.yaz.launcher.data.settings

// Robolectric for the app's files directory (https://robolectric.org/). The cases follow kotlinx.serialization's Json { ignoreUnknownKeys, coerceInputValues }
// (https://github.com/Kotlin/kotlinx.serialization/blob/master/formats/json/commonMain/src/kotlinx/serialization/json/Json.kt#coerceInputValues): an unknown enum
// member takes its field's default and the other fields are kept; and LauncherPrefs.put's way of writing: the value in memory at once, the file written after.
import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(application = Application::class, sdk = [36])
class SettingsStoreTest {

    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val file get() = File(context.filesDir, "settings.json")

    @Before
    fun clean() {
        file.delete()
    }

    @Test
    fun anUnknownEnumValueKeepsTheOtherFields() {
        file.writeText("""{"themeMode":"PURPLE","panelOpacity":70,"iconShape":"ARCH","futureKey":1}""")
        val loaded = SettingsStore(context).current
        assertEquals(ThemeMode.SYSTEM, loaded.themeMode)
        assertEquals(70, loaded.panelOpacity)
        assertEquals(IconShape.ARCH, loaded.iconShape)
    }

    @Test
    fun aNullForANonNullFieldKeepsTheOtherFields() {
        file.writeText("""{"panelOpacity":null,"edgeTabs":10}""")
        val loaded = SettingsStore(context).current
        assertEquals(54, loaded.panelOpacity)
        assertEquals(10, loaded.edgeTabs)
    }

    @Test
    fun aDamagedFileGivesTheDefaults() {
        file.writeText("{not json")
        assertEquals(Settings(), SettingsStore(context).current)
    }

    @Test
    fun anUpdateIsInMemoryAtOnceAndOnDiskAfter() {
        val store = SettingsStore(context)
        store.update { it.copy(panelOpacity = 12) }
        assertEquals(12, store.current.panelOpacity)
        // The write is queued on the writer's thread: the latest value is in the file soon.
        val deadline = System.currentTimeMillis() + 5000
        while (System.currentTimeMillis() < deadline && !(file.exists() && file.readText().contains("12"))) Thread.sleep(20)
        assertTrue(file.exists())
        assertEquals(12, SettingsStore(context).current.panelOpacity)
    }

    @Test
    fun theLatestOfManyUpdatesIsTheOneWritten() {
        val store = SettingsStore(context)
        for (opacity in 1..50) store.update { it.copy(panelOpacity = opacity) }
        val deadline = System.currentTimeMillis() + 5000
        while (System.currentTimeMillis() < deadline && !(file.exists() && file.readText().contains("50"))) Thread.sleep(20)
        assertEquals(50, SettingsStore(context).current.panelOpacity)
    }
}
