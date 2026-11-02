package com.yaz.launcher

// The start of the home, on the JVM. Since 0.1.10 the model's start read Settings.Secure "hide_privatespace_entry_point", a hidden key: on the
// phone Android refuses a third-party app with a SecurityException ("Settings key ... is not readable"), at MainActivity.onCreate. Robolectric's
// Settings shadow never refuses, so the test makes the refusal: a Context whose content resolver fails as Android's does, as Robolectric's own
// tests wrap a Context (ContextWrapper, https://developer.android.com/reference/android/content/ContextWrapper). The start and stop of the
// model's pieces must not touch the resolver. Activity start: Robolectric's ActivityScenarioTest
// (https://github.com/robolectric/robolectric/blob/master/integration_tests/androidx_test/src/test/java/org/robolectric/integrationtests/axt/ActivityScenarioTest.java#launch).
import android.content.ContentResolver
import android.content.Context
import android.content.ContextWrapper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.yaz.launcher.data.apps.HideWhenLocked
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [36], qualifiers = "w411dp-h914dp-420dpi")
class LaunchTest {

    // Android 12 and later: a hidden Settings key is not readable by an app that is not a system app.
    private class RefusingContext(base: Context) : ContextWrapper(base) {
        override fun getApplicationContext(): Context = this
        override fun getContentResolver(): ContentResolver =
            throw SecurityException("Settings key: <hide_privatespace_entry_point> is not readable.")
    }

    @Test
    fun theHideWhenLockedSettingStartsWithoutReadingAHiddenSettingsKey() {
        val hide = HideWhenLocked(RefusingContext(ApplicationProvider.getApplicationContext()))
        hide.start()
        assertFalse(hide.value.value)
        hide.stop()
    }
}
