package com.cetvocab.app.ui.theme

import androidx.activity.ComponentActivity
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import android.view.View
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.cetvocab.app.data.settings.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Regression tests for the status-bar contrast defect.
 *
 * The bug: `enableEdgeToEdge()` derives the bar icon tint from the **system** night-mode flag at
 * `Activity.onCreate`, while the in-app theme is a separate DataStore-backed choice. Selecting the
 * dark theme on a light-mode device therefore left dark icons on a dark bar, and switching the
 * theme while the app was running did not update the bars at all.
 *
 * Two levels are covered:
 *  * the mapping from theme to bar appearance, through an injected [SystemBarStyler] (pure, fast,
 *    and it fails with a readable message if the mapping regresses);
 *  * the real window path on API 26, asserting the actual system UI flag the platform uses below
 *    API 30 — that is the code path a real Android 8.0 device takes.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [26])
class SystemBarsTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    /** Records what the theme asked for instead of touching a window. */
    private class RecordingStyler : SystemBarStyler {
        val applied = mutableListOf<SystemBarIconAppearance>()
        override fun apply(appearance: SystemBarIconAppearance) {
            applied += appearance
        }
    }

    @Test
    fun `light theme asks for dark status bar icons`() {
        val styler = RecordingStyler()
        composeRule.setContent {
            androidx.compose.runtime.CompositionLocalProvider(LocalSystemBarStyler provides styler) {
                CetVocabTheme(themeMode = ThemeMode.LIGHT) { Text("light") }
            }
        }
        composeRule.waitForIdle()

        assertTrue(
            "light theme must request dark icons, got ${styler.applied}",
            styler.applied.isNotEmpty() && styler.applied.last() == SystemBarIconAppearance.DARK_ICONS,
        )
    }

    @Test
    fun `dark theme asks for light status bar icons`() {
        val styler = RecordingStyler()
        composeRule.setContent {
            androidx.compose.runtime.CompositionLocalProvider(LocalSystemBarStyler provides styler) {
                CetVocabTheme(themeMode = ThemeMode.DARK) { Text("dark") }
            }
        }
        composeRule.waitForIdle()

        assertEquals(SystemBarIconAppearance.LIGHT_ICONS, styler.applied.last())
    }

    @Test
    fun `switching the theme re-applies the bar appearance`() {
        val styler = RecordingStyler()
        val mode = mutableStateOf(ThemeMode.LIGHT)
        composeRule.setContent {
            val current by mode
            androidx.compose.runtime.CompositionLocalProvider(LocalSystemBarStyler provides styler) {
                CetVocabTheme(themeMode = current) { Text("toggle") }
            }
        }
        composeRule.waitForIdle()
        assertEquals(SystemBarIconAppearance.DARK_ICONS, styler.applied.last())

        // The actual scenario from the bug report: the user switches to the dark theme in settings.
        composeRule.runOnUiThread { mode.value = ThemeMode.DARK }
        composeRule.waitForIdle()

        assertEquals(SystemBarIconAppearance.LIGHT_ICONS, styler.applied.last())
        assertTrue(
            "the appearance must be re-applied on every theme change",
            styler.applied.size >= 2,
        )
    }

    @Test
    fun `the real window receives the light-status-bar flag on api 26`() {
        composeRule.setContent {
            CetVocabTheme(themeMode = ThemeMode.LIGHT) { Text("window") }
        }
        composeRule.waitForIdle()

        val decorView = composeRule.activity.window.decorView
        val lightStatusBar = (decorView.systemUiVisibility and View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR) != 0
        assertTrue("light theme must set SYSTEM_UI_FLAG_LIGHT_STATUS_BAR", lightStatusBar)
    }

    @Test
    fun `the real window clears the light-status-bar flag for the dark theme`() {
        composeRule.setContent {
            CetVocabTheme(themeMode = ThemeMode.DARK) { Text("window") }
        }
        composeRule.waitForIdle()

        val decorView = composeRule.activity.window.decorView
        val lightStatusBar = (decorView.systemUiVisibility and View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR) != 0
        assertFalse("dark theme must clear SYSTEM_UI_FLAG_LIGHT_STATUS_BAR", lightStatusBar)
    }
}
