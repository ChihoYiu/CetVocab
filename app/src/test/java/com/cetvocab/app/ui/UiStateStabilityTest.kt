package com.cetvocab.app.ui

import androidx.activity.ComponentActivity
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.cetvocab.app.domain.model.CetLevel
import com.cetvocab.app.domain.srs.MasteryStatus
import com.cetvocab.app.domain.study.StudyCard
import com.cetvocab.app.domain.study.TodayPlan
import com.cetvocab.app.domain.study.QueueReason
import com.cetvocab.app.domain.model.Word
import com.cetvocab.app.domain.srs.WordProgress
import com.cetvocab.app.ui.theme.CetVocabTheme
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * Regression tests for the two animation/jank fixes that are measurable without a device:
 *
 *  * **stable state types** — a UI state that Compose treats as unstable cannot be skipped, so every
 *    emission of the Room/DataStore flows recomposes the whole screen (including long text layout)
 *    even when nothing visible changed. Re-emitting an equal state must therefore not recompose the
 *    screen; a real change must.
 *  * **no time-of-day work during composition** — the dashboard greeting and date used to call
 *    `LocalDate.now()` and build a `DateTimeFormatter` inside property getters that run on every
 *    recomposition. They are now computed once per emission, which these tests pin down.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [26])
class UiStateStabilityTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Composable
    private fun CountingStudyScreen(state: StudyUiState, onComposed: () -> Unit) {
        onComposed()
        Text(state.progressLabel)
    }

    private val card = StudyCard(
        word = Word(
            id = 1,
            word = "abandon",
            phonetic = "/əˈbændən/",
            partOfSpeech = "v.",
            meanings = listOf("放弃；抛弃"),
            level = CetLevel.CET4,
        ),
        progress = WordProgress(wordId = 1, status = MasteryStatus.LEARNING),
        reason = QueueReason.NEW,
        isNew = true,
    )

    @Test
    fun `an equal ui state does not recompose the screen`() {
        val state = mutableStateOf(StudyUiState(loading = false, card = card))
        var compositions = 0

        composeRule.setContent {
            CetVocabTheme {
                CountingStudyScreen(state.value) { compositions++ }
            }
        }
        composeRule.waitForIdle()
        assertThat(compositions).isEqualTo(1)

        // The flows re-emit regularly (Room/DataStore); an equal value must not cost a recomposition.
        composeRule.runOnUiThread { state.value = state.value.copy() }
        composeRule.waitForIdle()
        assertThat(compositions).isEqualTo(1)
    }

    @Test
    fun `a real state change still recomposes the screen`() {
        val state = mutableStateOf(StudyUiState(loading = false, card = card))
        var compositions = 0

        composeRule.setContent {
            CetVocabTheme {
                CountingStudyScreen(state.value) { compositions++ }
            }
        }
        composeRule.waitForIdle()

        composeRule.runOnUiThread { state.value = state.value.copy(saving = true) }
        composeRule.waitForIdle()
        assertThat(compositions).isEqualTo(2)
    }

    @Test
    fun `greeting depends only on the hour`() {
        assertThat(greetingFor(0)).isEqualTo("夜深了，注意休息")
        assertThat(greetingFor(5)).isEqualTo("夜深了，注意休息")
        assertThat(greetingFor(6)).isEqualTo("早上好，今天也要加油")
        assertThat(greetingFor(10)).isEqualTo("早上好，今天也要加油")
        assertThat(greetingFor(11)).isEqualTo("中午好，来背几个词")
        assertThat(greetingFor(14)).isEqualTo("下午好，继续积累")
        assertThat(greetingFor(17)).isEqualTo("下午好，继续积累")
        assertThat(greetingFor(18)).isEqualTo("晚上好，完成今天的目标吧")
        assertThat(greetingFor(23)).isEqualTo("晚上好，完成今天的目标吧")
    }

    @Test
    fun `the date label is formatted in the given zone, not the device zone`() {
        // 2026-01-15T16:30:00Z is still the 15th in UTC but already the 16th in Shanghai.
        val instant = 1_768_494_600_000L

        val utc = HomeDateFormatter.format(instant, ZoneOffset.UTC)
        val shanghai = HomeDateFormatter.format(instant, ZoneId.of("Asia/Shanghai"))

        assertThat(utc).contains("1 月 15 日")
        assertThat(shanghai).contains("1 月 16 日")
    }

    @Test
    fun `today plan exposes a finished state without dividing by zero`() {
        val empty = TodayPlan(
            level = CetLevel.CET4,
            dueCount = 0,
            fragileCount = 0,
            newAvailableCount = 0,
            activeMistakeCount = 0,
            studiedToday = 0,
            answeredToday = 0,
            correctToday = 0,
            dailyNewGoal = 20,
            dailyReviewLimit = 60,
        )
        // A plan with nothing left must read as complete, not as a 0/0 division.
        assertThat(empty.plannedCards).isEqualTo(0)
        assertThat(empty.progressFraction).isEqualTo(1f)
        assertThat(empty.accuracyTodayPercent).isNull()
    }
}
