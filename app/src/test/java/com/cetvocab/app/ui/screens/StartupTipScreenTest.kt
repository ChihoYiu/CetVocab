package com.cetvocab.app.ui.screens

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.cetvocab.app.data.repository.SeedProgress
import com.cetvocab.app.domain.motivation.StartupTip
import com.cetvocab.app.ui.theme.CetVocabTheme
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * The cold-start screen.
 *
 * The screen is deliberately dumb — it renders a tip and reports a tap — so these tests pin the
 * behaviour the product brief asks for: the line is visible on start, a tap continues immediately
 * (the line can never trap the learner), the vocabulary progress still shows on a first launch, and
 * a missing catalogue degrades to a usable screen instead of a blank one.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [26])
class StartupTipScreenTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val tip = StartupTip(
        id = "tip-0007",
        category = "坚持与习惯",
        text = "按时睡觉，是明天继续的底气。",
    )

    @Test
    fun `the tip and its category are on screen`() {
        composeRule.setContent {
            CetVocabTheme {
                StartupTipScreen(tip = tip, seedProgress = null, onContinue = {})
            }
        }

        composeRule.onNodeWithTag("startup_tip").assertIsDisplayed()
        // The whole screen is one big tap target, so it merges the semantics of its children: the
        // inner tags are only addressable in the unmerged tree.
        composeRule.onNodeWithTag("startup_tip_text", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText(tip.text).assertExists()
        composeRule.onNodeWithText(tip.category).assertExists()
        composeRule.onNodeWithTag("startup_tip_continue", useUnmergedTree = true).assertExists()
        // Nothing about the vocabulary is claimed when there is nothing to prepare.
        composeRule.onNodeWithTag("startup_seed_progress", useUnmergedTree = true)
            .assertDoesNotExist()
    }

    @Test
    fun `tapping anywhere continues immediately`() {
        var continued = 0
        composeRule.setContent {
            CetVocabTheme {
                StartupTipScreen(tip = tip, seedProgress = null, onContinue = { continued++ })
            }
        }

        composeRule.onNodeWithTag("startup_tip").performClick()
        composeRule.waitForIdle()

        assertThat(continued).isEqualTo(1)
    }

    @Test
    fun `the seeding progress is reported on a first launch`() {
        composeRule.setContent {
            CetVocabTheme {
                StartupTipScreen(
                    tip = tip,
                    seedProgress = SeedProgress(packName = "CET-4 词库", processed = 800, total = 3_849),
                    onContinue = {},
                )
            }
        }

        composeRule.onNodeWithTag("startup_seed_progress", useUnmergedTree = true)
            .assertIsDisplayed()
        composeRule.onNodeWithText("正在准备词库 CET-4 词库（800 / 3849）").assertExists()
    }

    @Test
    fun `a missing tip still leaves a usable screen`() {
        var continued = false
        composeRule.setContent {
            CetVocabTheme {
                StartupTipScreen(tip = null, seedProgress = null, onContinue = { continued = true })
            }
        }

        // No tip node, but the screen is still there and still dismissible — the fallback path must
        // never become a dead end.
        composeRule.onNodeWithTag("startup_tip_text", useUnmergedTree = true).assertDoesNotExist()
        composeRule.onNodeWithTag("startup_tip").performClick()
        composeRule.waitForIdle()
        assertThat(continued).isTrue()
    }
}
