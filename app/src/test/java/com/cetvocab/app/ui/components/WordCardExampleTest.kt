package com.cetvocab.app.ui.components

import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.cetvocab.app.domain.model.CetLevel
import com.cetvocab.app.domain.model.Word
import com.cetvocab.app.ui.theme.CetVocabTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * The pre-answer example hint ("先给语境，再让用户作答，最后揭晓答案").
 *
 * The point of the feature is the **order**: before the reveal the learner sees the English
 * sentence and nothing about the answer, and only after the reveal does the Chinese meaning (and
 * the sentence's translation) appear. Both halves are asserted here, because a hint that leaks the
 * meaning early would defeat the recall step the card exists for.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [26])
class WordCardExampleTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val word = Word(
        id = 1,
        word = "abandon",
        phonetic = "/əˈbændən/",
        partOfSpeech = "v.",
        meanings = listOf("放弃；抛弃"),
        exampleSentence = "He had to abandon the plan because of the storm.",
        exampleTranslation = "由于暴风雨，他不得不放弃这个计划。",
        level = CetLevel.CET4,
    )

    private fun render(word: Word, revealed: androidx.compose.runtime.MutableState<Boolean>) {
        composeRule.setContent {
            CetVocabTheme {
                WordCard(
                    word = word,
                    revealed = revealed.value,
                    isFavorite = false,
                    onToggleReveal = { revealed.value = !revealed.value },
                    onToggleFavorite = {},
                    onSpeakWord = {},
                    modifier = Modifier.testTag("card"),
                )
            }
        }
    }

    @Test
    fun `the example sentence is shown before the answer is revealed`() {
        val revealed = mutableStateOf(false)

        render(word, revealed)

        // The card is a clickable container, so it merges its children's semantics; the hint's test
        // tag is only reachable in the unmerged tree.
        composeRule.onNodeWithTag("study_example_hint", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithText(word.exampleSentence).assertExists()
        // The answer is not on screen yet: neither the meaning nor the sentence's translation.
        composeRule.onNodeWithText("放弃；抛弃").assertDoesNotExist()
        composeRule.onNodeWithText(word.exampleTranslation).assertDoesNotExist()
    }

    @Test
    fun `revealing adds the meaning and the translation without losing the sentence`() {
        val revealed = mutableStateOf(false)

        render(word, revealed)
        composeRule.onNodeWithTag("card").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("放弃；抛弃").assertExists()
        composeRule.onNodeWithText(word.exampleTranslation).assertExists()
        composeRule.onNodeWithText(word.exampleSentence).assertExists()
        // The hint block itself is gone: after the reveal the sentence belongs to the meaning block.
        composeRule.onNodeWithTag("study_example_hint", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun `a word without an example falls back to the original prompt`() {
        val withoutExample = word.copy(exampleSentence = "", exampleTranslation = "")

        render(withoutExample, mutableStateOf(false))

        composeRule.onNodeWithTag("study_example_hint", useUnmergedTree = true).assertDoesNotExist()
        composeRule.onNodeWithText("先回忆一下，再点击卡片查看释义").assertExists()
    }
}
