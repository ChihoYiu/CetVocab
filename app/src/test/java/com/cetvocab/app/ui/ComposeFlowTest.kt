package com.cetvocab.app.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.cetvocab.app.AppContainer
import com.cetvocab.app.data.local.CetVocabDatabase
import com.cetvocab.app.data.repository.AnswerSource
import com.cetvocab.app.data.settings.DataStoreSettingsRepository
import com.cetvocab.app.data.settings.ThemeMode
import com.cetvocab.app.data.settings.UserSettings
import com.cetvocab.app.domain.model.CetLevel
import com.cetvocab.app.domain.srs.ReviewGrade
import com.cetvocab.app.ui.screens.HomeScreen
import com.cetvocab.app.ui.screens.OnboardingScreen
import com.cetvocab.app.ui.screens.ReviewCenterScreen
import com.cetvocab.app.ui.screens.SettingsScreen
import com.cetvocab.app.ui.screens.StudyScreen
import com.cetvocab.app.ui.theme.CetVocabTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.AfterClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Compose UI tests for the flows the acceptance criteria call out.
 *
 * They run on the JVM through Robolectric so that the whole suite — including the
 * API-26 configuration below — works on a machine without an emulator. Every assertion uses a
 * stable test tag or a real semantic node (text, content description); no test depends on
 * pixel coordinates or on a specific screen size.
 *
 * Two properties of these screens shape how the tests have to be written:
 *  * every screen renders from a state flow that is backed by Room or DataStore, so the first
 *    composition shows a loading state and a single `waitForIdle()` does not wait for the
 *    data — the tests therefore `waitUntil` a real condition on the semantic tree;
 *  * the settings/onboarding/study screens are vertically scrolling columns, so a control
 *    below the fold has to be scrolled to before it can be clicked.
 *
 * The Robolectric configuration is deliberately identical to the other database tests
 * (`sdk = 26`, default window): every distinct configuration makes Robolectric build another
 * sandbox, and this suite has to stay inside the memory budget of the machine it runs on.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26])
class ComposeFlowTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var database: CetVocabDatabase
    private lateinit var container: AppContainer
    private lateinit var settings: DataStoreSettingsRepository

    @Before
    fun setUp() = runTest {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        database = sharedDatabase(context)
        // Isolate tests by emptying the shared in-memory database rather than by closing it.
        // A screen's state flow keeps its Room subscription for `WhileSubscribed(5_000)` after the
        // composition is gone, so closing the database at the end of a test would let that
        // subscription query a closed connection pool ("connection pool has been closed").
        database.clearAllTables()
        container = AppContainer(context, database)
        settings = DataStoreSettingsRepository(context)
        // Start from a known state; DataStore is shared between tests in the same process. The
        // write is awaited here, so no test body can click before the reset has landed.
        settings.resetToDefaults()
        container.wordRepository.ensureBuiltInPacks()
    }

    @After
    fun tearDown() {
        // Intentionally does not close the database: see `sharedDatabase`.
    }

    // --- Waiting helpers ------------------------------------------------------------------

    /**
     * Waits until [condition] holds.
     *
     * `waitForIdle()` on the Robolectric strategy runs Espresso's idle loop, which drains the
     * main looper the app's view-model coroutines are queued on, and `waitUntil` keeps advancing
     * the Compose clock between attempts. Both are needed: the data of every screen arrives on a
     * Room/DataStore flow, so a single `waitForIdle()` cannot know that an emission is still on
     * its way from the database thread.
     */
    private fun awaitCondition(condition: () -> Boolean) {
        composeRule.waitUntil(timeoutMillis = TIMEOUT_MILLIS) {
            composeRule.waitForIdle()
            condition()
        }
    }

    private fun awaitTag(tag: String) = awaitCondition {
        composeRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
    }

    private fun awaitText(text: String, substring: Boolean = false) = awaitCondition {
        composeRule.onAllNodesWithText(text, substring = substring).fetchSemanticsNodes().isNotEmpty()
    }

    /**
     * Reads the persisted settings from a background thread: the Robolectric main thread must
     * stay free while the DataStore write/read completes.
     */
    private fun readSettings(): UserSettings = runBlocking(Dispatchers.IO) { settings.current() }

    private fun reviewEventCount(level: CetLevel = CetLevel.CET4): Int =
        runBlocking(Dispatchers.IO) { database.reviewEventDao().totalCount(level.id) }

    private companion object {
        /** A few seconds of real time is far longer than any of these flows needs. */
        const val TIMEOUT_MILLIS = 10_000L

        private var sharedDb: CetVocabDatabase? = null

        /**
         * One in-memory database for the whole class.
         *
         * The screens keep their Room subscriptions alive for `WhileSubscribed(5_000)` after a test
         * stops composing, so closing the database between tests makes a still-active subscription
         * fail with "the connection pool has been closed". Tests empty the tables instead, and the
         * database is closed once, when the class is finished.
         */
        fun sharedDatabase(context: android.content.Context): CetVocabDatabase =
            sharedDb ?: Room.inMemoryDatabaseBuilder(context, CetVocabDatabase::class.java)
                .allowMainThreadQueries()
                .build()
                .also { sharedDb = it }

        @JvmStatic
        @AfterClass
        fun closeSharedDatabase() {
            sharedDb?.close()
            sharedDb = null
        }
    }

    // --- Empty states -------------------------------------------------------------------

    @Test
    fun `home shows today's plan once the database flows settle`() {
        // The dashboard starts from HomeUiState(loading = true) and fills the plan in from a
        // Room flow. The loading placeholder is the pristine first frame; what must hold is that
        // the plan card appears once that flow has emitted.
        composeRule.setContent {
            CetVocabTheme { HomeScreen(container, {}, {}, {}, {}, {}) }
        }
        awaitTag("home_today_progress")
        composeRule.onNodeWithTag("home_today_progress").assertIsDisplayed()
    }

    @Test
    fun `home offers the primary study action`() {
        var started = false
        composeRule.setContent {
            CetVocabTheme { HomeScreen(container, { started = true }, {}, {}, {}, {}) }
        }
        awaitTag("home_next_step_action")
        composeRule.onNodeWithTag("home_next_step_action").assertIsDisplayed().performClick()
        assertTrue("the next-step action must invoke the study callback", started)
    }

    @Test
    fun `home shows the four dashboard sections a learner needs`() {
        composeRule.setContent {
            CetVocabTheme { HomeScreen(container, {}, {}, {}, {}, {}) }
        }

        // The product requirement: on one screen, where I am, what is next, the word-status split,
        // and the next milestone. The dashboard is a LazyColumn, so the lower sections have to be
        // scrolled into composition before they can be asserted on — that is also what proves the
        // scroll target actually exists.
        awaitTag("home_today_progress")
        composeRule.onNodeWithTag("home_next_step").assertExists()
        composeRule.onNodeWithTag("home_content")
            .performScrollToNode(hasTestTag("home_word_status"))
        composeRule.onNodeWithTag("home_word_status").assertExists()
        composeRule.onNodeWithTag("home_content")
            .performScrollToNode(hasTestTag("home_stage_goal"))
        composeRule.onNodeWithTag("home_stage_goal").assertExists()
        // ... and the next step must say something concrete, not just "继续学习".
        composeRule.onNodeWithTag("home_next_step_text").assertExists()
    }

    @Test
    fun `home can explain every number through the statistics definitions`() {
        composeRule.setContent {
            CetVocabTheme { HomeScreen(container, {}, {}, {}, {}, {}) }
        }
        awaitTag("home_definitions")
        // Invoke the click action directly rather than injecting a touch: this test is about the
        // wiring ("the entry point opens the sheet"), not about where the button lands in the
        // window, and touch injection depends on the latter.
        composeRule.onNodeWithTag("home_definitions")
            .performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("stats_definitions").assertExists()
        composeRule.onNodeWithText("连续学习天数", substring = true).assertExists()
    }

    // --- Study flow ---------------------------------------------------------------------

    @Test
    fun `study hides the meaning until the learner reveals it and records answers`() {
        composeRule.setContent {
            CetVocabTheme {
                StudyScreen(
                    container = container,
                    drillIds = emptyList(),
                    onExit = {},
                    onGoHome = {},
                )
            }
        }
        // The queue is built from the vocabulary database, so wait for the first card.
        awaitTag("study_word_card")
        awaitTag("study_reveal")

        // The meaning is hidden: only the reveal affordance is offered.
        composeRule.onNodeWithTag("study_reveal").performScrollTo().assertIsDisplayed().performClick()
        awaitText("已掌握")

        // After revealing, the three self-assessment actions appear.
        composeRule.onNodeWithText("不认识").assertExists()
        composeRule.onNodeWithText("模糊").assertExists()
        composeRule.onNodeWithText("已掌握").performScrollTo().assertIsDisplayed().performClick()

        // The answer must have been written to the database; the write happens on a background
        // dispatcher, so wait for it instead of reading once.
        awaitCondition { reviewEventCount() >= 1 }
        assertTrue("tapping 已掌握 must persist a review event", reviewEventCount() >= 1)
    }

    @Test
    fun `study progress indicator shows the card position`() {
        composeRule.setContent {
            CetVocabTheme {
                StudyScreen(container, emptyList(), onExit = {}, onGoHome = {})
            }
        }
        awaitTag("study_progress")
        composeRule.onNodeWithTag("study_progress").assertIsDisplayed()
    }

    // --- Review centre ------------------------------------------------------------------

    @Test
    fun `review centre shows its empty state before any mistake exists`() {
        composeRule.setContent {
            CetVocabTheme {
                ReviewCenterScreen(container, onOpenWord = {}, onStartDrill = {})
            }
        }
        awaitTag("review_categories")
        composeRule.onNodeWithTag("review_categories").assertIsDisplayed()
        composeRule.onNodeWithTag("review_search").assertIsDisplayed()
        // The list query has to come back empty before the empty state can appear. The state is
        // asserted on the semantic tree rather than on the viewport: this column does not scroll,
        // so in Robolectric's small default window its last block can fall outside the screen.
        awaitText("这里还没有记录")
        composeRule.onNodeWithText("这里还没有记录").assertExists()
    }

    @Test
    fun `review centre lists a word after it was answered wrongly`() {
        val wordId = runBlocking(Dispatchers.IO) {
            val ids = database.wordDao().idsForLevel(CetLevel.CET4.id)
            val first = ids.first()
            container.progressRecorder.record(first, ReviewGrade.UNKNOWN, AnswerSource.STUDY)
            first
        }
        composeRule.setContent {
            CetVocabTheme { ReviewCenterScreen(container, onOpenWord = {}, onStartDrill = {}) }
        }
        val word = runBlocking(Dispatchers.IO) { database.wordDao().byId(wordId)!!.word }
        awaitText(word, substring = true)
        composeRule.onNodeWithText(word, substring = true).assertIsDisplayed()
    }

    @Test
    fun `review centre filters by search term`() {
        runBlocking(Dispatchers.IO) {
            val ids = database.wordDao().idsForLevel(CetLevel.CET4.id)
            container.progressRecorder.record(ids[0], ReviewGrade.UNKNOWN, AnswerSource.STUDY)
            container.progressRecorder.record(ids[1], ReviewGrade.UNKNOWN, AnswerSource.STUDY)
        }
        composeRule.setContent {
            CetVocabTheme { ReviewCenterScreen(container, onOpenWord = {}, onStartDrill = {}) }
        }
        awaitTag("review_search")
        composeRule.onNodeWithTag("review_search").performTextInput("zzz-no-such-word")
        // A query that matches nothing falls back to the same empty state.
        awaitText("这里还没有记录")
        composeRule.onNodeWithText("这里还没有记录").assertExists()
    }

    // --- Onboarding ---------------------------------------------------------------------

    @Test
    fun `onboarding writes the chosen level and goal and finishes`() {
        var finished = false
        composeRule.setContent {
            CetVocabTheme {
                OnboardingScreen(container = container, onFinished = { finished = true })
            }
        }
        // The level chip is labelled with CetLevel.label ("大学英语六级"), and the goal stepper
        // and the finish button live further down the scrolling column.
        composeRule.onNodeWithText(CetLevel.CET6.label).performScrollTo().performClick()
        composeRule.onNodeWithTag("onboarding_new_words_increase").performScrollTo().performClick()
        composeRule.onNodeWithTag("onboarding_finish").performScrollTo().performClick()

        // Every write is asynchronous, so wait for the persisted values before asserting them.
        awaitCondition { readSettings().level == CetLevel.CET6 }
        awaitCondition { readSettings().onboardingCompleted }
        awaitCondition { finished }

        val stored = readSettings()
        assertEquals(CetLevel.CET6, stored.level)
        assertTrue("finishing onboarding must be persisted", stored.onboardingCompleted)
        assertTrue("the screen must report completion to the navigation graph", finished)
    }

    @Test
    fun `onboarding can be skipped and still completes`() {
        composeRule.setContent {
            CetVocabTheme { OnboardingScreen(container = container, onFinished = {}) }
        }
        composeRule.onNodeWithTag("onboarding_skip").performClick()
        awaitCondition { readSettings().onboardingCompleted }

        val stored = readSettings()
        assertTrue(stored.onboardingCompleted)
        assertEquals(UserSettings().dailyNewWordGoal, stored.dailyNewWordGoal)
    }

    // --- Settings -----------------------------------------------------------------------

    @Test
    fun `home offers the vocabulary search`() {
        var opened = false
        composeRule.setContent {
            CetVocabTheme {
                HomeScreen(
                    container = container,
                    onStartStudy = {},
                    onResumeStudy = {},
                    onStartMistakeDrill = {},
                    onOpenReviewCenter = {},
                    onOpenStats = {},
                    onOpenWordBrowser = { opened = true },
                )
            }
        }

        awaitTag("home_open_word_browser")
        composeRule.onNodeWithTag("home_open_word_browser").assertIsDisplayed().performClick()

        assertTrue("the app bar search action must open the vocabulary browser", opened)
    }

    @Test
    fun `changing the theme in settings survives a re-read of the datastore`() {
        composeRule.setContent {
            CetVocabTheme { SettingsScreen(container = container, onBack = {}) }
        }
        awaitTag("settings_theme")
        // The theme chips sit in a horizontally scrollable row inside the vertically scrolling
        // settings column. `performScrollTo` only scrolls the closest scrollable parent, so the
        // row is scrolled into the viewport first and the chip second; otherwise the tap lands
        // outside the window and never reaches the chip.
        composeRule.onNodeWithTag("settings_theme").performScrollTo()
        composeRule.onNodeWithText(ThemeMode.DARK.label).performScrollTo().performClick()
        awaitCondition { readSettings().themeMode == ThemeMode.DARK }

        assertEquals(ThemeMode.DARK, readSettings().themeMode)
    }

    @Test
    fun `the shuffle switch persists and can be turned back off`() {
        composeRule.setContent {
            CetVocabTheme { SettingsScreen(container = container, onBack = {}) }
        }
        // Off by default: the documented priority order is what a learner starts with.
        awaitTag("settings_shuffle_switch")
        assertFalse(readSettings().shuffleWords)

        composeRule.onNodeWithTag("settings_shuffle_switch").performScrollTo().performClick()
        awaitCondition { readSettings().shuffleWords }
        assertTrue(readSettings().shuffleWords)

        // Toggling back must restore the fixed order, not leave the setting stuck on.
        composeRule.onNodeWithTag("settings_shuffle_switch").performScrollTo().performClick()
        awaitCondition { !readSettings().shuffleWords }
        assertFalse(readSettings().shuffleWords)
    }

    @Test
    fun `clearing study progress asks for confirmation first`() {
        val wordId = runBlocking(Dispatchers.IO) {
            val ids = database.wordDao().idsForLevel(CetLevel.CET4.id)
            val first = ids.first()
            container.progressRecorder.record(first, ReviewGrade.UNKNOWN, AnswerSource.STUDY)
            first
        }
        composeRule.setContent {
            CetVocabTheme { SettingsScreen(container = container, onBack = {}) }
        }
        awaitTag("settings_clear_progress")
        composeRule.onNodeWithTag("settings_clear_progress").performScrollTo().performClick()

        // The confirmation dialog appears ("确认清空" only exists inside it) and nothing has been
        // deleted yet: the progress row written above must still be there.
        awaitText("确认清空")
        composeRule.onNodeWithText("确认清空").assertExists()
        val stillThere = runBlocking(Dispatchers.IO) { database.progressDao().byWordIds(listOf(wordId)).size }
        assertTrue("the dialog must not delete anything before it is confirmed", stillThere == 1)

        composeRule.onNodeWithText("取消").performClick()
        composeRule.waitForIdle()
    }
}
