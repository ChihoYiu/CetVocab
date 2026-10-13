package com.cetvocab.app.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.cetvocab.app.AppContainer
import com.cetvocab.app.data.local.CetVocabDatabase
import com.cetvocab.app.data.settings.DataStoreSettingsRepository
import com.cetvocab.app.domain.model.CetLevel
import com.cetvocab.app.ui.screens.WordBrowserScreen
import com.cetvocab.app.ui.theme.CetVocabTheme
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * UI tests for the vocabulary browser.
 *
 * The complete built-in lists hold thousands of words, so "can a learner actually find a word?" is
 * a functional requirement rather than a nicety. These tests cover the three states the screen can
 * be in — results, empty, and a search that matches nothing — plus the debounced query path.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [26])
class WordBrowserScreenTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private lateinit var database: CetVocabDatabase
    private lateinit var container: AppContainer
    private lateinit var settings: DataStoreSettingsRepository

    @Before
    fun setUp() {
        // A block body, not `= runBlocking { … }`: the last statement returns a value, and JUnit
        // rejects a `@Before` method whose return type is not void (it fails while validating the
        // class, which surfaces as "Failed to instantiate test runner").
        runBlocking {
            val context = ApplicationProvider.getApplicationContext<android.content.Context>()
            database = Room.inMemoryDatabaseBuilder(context, CetVocabDatabase::class.java)
                .allowMainThreadQueries()
                .build()
            container = AppContainer(context, database)
            settings = DataStoreSettingsRepository(context)
            settings.resetToDefaults()
            settings.setLevel(CetLevel.CET4)
            // A deterministic vocabulary instead of the multi-megabyte shipped assets.
            database.wordDao().insertAll(
                listOf(
                    wordEntity("abandon", "放弃；抛弃", 1),
                    wordEntity("ability", "能力", 2),
                    wordEntity("absorb", "吸收", 3),
                    wordEntity("apple", "苹果", 4),
                    // Used by the "did you mean" test: one transposed pair away from "recieve".
                    wordEntity("receive", "收到；接收", 5),
                ),
            )
        }
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun wordEntity(word: String, meaning: String, index: Int) =
        com.cetvocab.app.data.local.entity.WordEntity(
            packId = "test-pack",
            word = word,
            normalizedWord = com.cetvocab.app.domain.model.Word.normalizeWordKey(word),
            phonetic = "/test/",
            partOfSpeech = "n.",
            meanings = """["$meaning"]""",
            level = CetLevel.CET4.id,
            source = "test",
            license = "MIT",
            createdAt = index.toLong(),
            updatedAt = index.toLong(),
        )

    private fun awaitText(text: String) {
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty()
        }
    }

    /** Waits until no node contains [text] — used to prove that a query really filtered. */
    private fun awaitTextGone(text: String) {
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isEmpty()
        }
    }

    @Test
    fun `the browser lists the vocabulary when the query is empty`() {
        composeRule.setContent {
            CetVocabTheme { WordBrowserScreen(container, onBack = {}, onOpenWord = {}) }
        }

        awaitText("abandon")
        composeRule.onNodeWithTag("browser_list").assertExists()
        // The summary proves the whole vocabulary was counted, not just the visible rows.
        composeRule.onNodeWithTag("browser_summary").assertExists()
        composeRule.onNodeWithText("abandon").assertExists()
        // Later rows may be below the fold in Robolectric's small window, so existence is what is
        // asserted here; visibility is covered by the first row.
        composeRule.onNodeWithText("apple", substring = true).assertExists()
    }

    @Test
    fun `typing a prefix filters the list`() {
        composeRule.setContent {
            CetVocabTheme { WordBrowserScreen(container, onBack = {}, onOpenWord = {}) }
        }
        awaitText("abandon")

        composeRule.onNodeWithTag("browser_search").performTextInput("ab")

        // The unfiltered list already contains the matches, so waiting for one of them would pass
        // immediately; the meaningful wait is for the non-matching row to disappear.
        awaitTextGone("apple")
        composeRule.onNodeWithText("abandon").assertExists()
        composeRule.onNodeWithText("absorb").assertExists()
    }

    @Test
    fun `searching a meaning finds the word`() {
        composeRule.setContent {
            CetVocabTheme { WordBrowserScreen(container, onBack = {}, onOpenWord = {}) }
        }
        awaitText("abandon")

        composeRule.onNodeWithTag("browser_search").performTextInput("苹果")

        awaitTextGone("abandon")
        composeRule.onNodeWithText("apple", substring = true).assertExists()
    }

    @Test
    fun `a query without matches shows the empty state`() {
        composeRule.setContent {
            CetVocabTheme { WordBrowserScreen(container, onBack = {}, onOpenWord = {}) }
        }
        awaitText("abandon")

        composeRule.onNodeWithTag("browser_search").performTextInput("zzzz-no-such-word")

        awaitText("没有找到匹配的单词")
        composeRule.onNodeWithTag("browser_empty").assertExists()
    }

    @Test
    fun `tapping a result opens that word`() {
        var opened: Long? = null
        composeRule.setContent {
            CetVocabTheme { WordBrowserScreen(container, onBack = {}, onOpenWord = { opened = it }) }
        }
        awaitText("abandon")

        composeRule.onNodeWithText("abandon").performScrollTo().performClick()
        composeRule.waitForIdle()

        val expectedId = runBlocking {
            database.wordDao().byNormalized(CetLevel.CET4.id, "abandon")!!.id
        }
        org.junit.Assert.assertEquals(expectedId, opened)
    }

    @Test
    fun `a misspelled query suggests the close spelling`() {
        composeRule.setContent {
            CetVocabTheme { WordBrowserScreen(container, onBack = {}, onOpenWord = {}) }
        }
        awaitText("abandon")

        // "recieve" is one transposed pair away from "receive" and matches nothing literally, so
        // the exact pass returns nothing and the edit-distance pass has to answer.
        composeRule.onNodeWithTag("browser_search").performTextInput("recieve")

        awaitText("拼写相近的单词")
        composeRule.onNodeWithTag("browser_approximation_note").assertExists()
        composeRule.onNodeWithText("receive").assertExists()
        // The suggestion is labelled as a suggestion, never as an exact hit. The row is a clickable
        // card, so the badge is only addressable in the unmerged tree.
        org.junit.Assert.assertTrue(
            composeRule.onAllNodesWithTag("browser_approximation_badge", useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty(),
        )
    }

    @Test
    fun `an exact hit does not show the correction section`() {
        composeRule.setContent {
            CetVocabTheme { WordBrowserScreen(container, onBack = {}, onOpenWord = {}) }
        }
        awaitText("abandon")

        composeRule.onNodeWithTag("browser_search").performTextInput("receive")
        // Wait for the debounced query to actually land (the summary is rendered from the results,
        // while the search field itself already contains the typed text).
        awaitText("找到 1 个结果")

        composeRule.onNodeWithTag("browser_approximation_note").assertDoesNotExist()
    }

    @Test
    fun `the result row shows the phonetic and the example sentence when the word has one`() {
        runBlocking {
            val stored = database.wordDao().byNormalized(CetLevel.CET4.id, "receive")!!
            database.wordDao().update(
                stored.copy(
                    phonetic = "/rɪˈsiːv/",
                    exampleSentence = "I will receive the letter tomorrow.",
                    exampleTranslation = "我明天会收到这封信。",
                ),
            )
        }
        composeRule.setContent {
            CetVocabTheme { WordBrowserScreen(container, onBack = {}, onOpenWord = {}) }
        }
        awaitText("abandon")

        composeRule.onNodeWithTag("browser_search").performTextInput("receive")

        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithText("I will receive the letter tomorrow.", substring = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("/rɪˈsiːv/", substring = true).assertExists()
    }
}
