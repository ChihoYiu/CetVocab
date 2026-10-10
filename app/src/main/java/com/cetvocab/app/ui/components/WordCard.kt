package com.cetvocab.app.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.cetvocab.app.domain.exam.ExamQuestion
import com.cetvocab.app.domain.exam.ExamQuestionType
import com.cetvocab.app.domain.model.CetLevel
import com.cetvocab.app.domain.model.Word
import com.cetvocab.app.domain.srs.WordProgress
import com.cetvocab.app.ui.speech.SpeechUiState
import com.cetvocab.app.ui.theme.AppTheme
import com.cetvocab.app.ui.theme.CetVocabTheme
import com.cetvocab.app.ui.theme.Spacing
import com.cetvocab.app.ui.theme.phoneticStyle
import com.cetvocab.app.ui.theme.wordStyle

/**
 * The study card.
 *
 * Design rules that matter for learning:
 *  * the meaning is **hidden by default** — the learner must first try to recall it, then tap
 *    to reveal (active recall instead of passive recognition);
 *  * nothing about correctness is displayed, because the learner grades themselves;
 *  * every string can wrap, so a 200% font scale never clips the word or its meaning.
 *
 * Reading is delegated to the caller: the card owns no speech state, it only renders the state it
 * is given, so the same card works on the study screen, the word detail screen and the browser.
 */
@Composable
fun WordCard(
    word: Word,
    revealed: Boolean,
    isFavorite: Boolean,
    onToggleReveal: () -> Unit,
    onToggleFavorite: () -> Unit,
    onSpeakWord: () -> Unit,
    modifier: Modifier = Modifier,
    progress: WordProgress? = null,
    reasonLabel: String? = null,
    speechState: SpeechUiState = SpeechUiState(),
    /** Non-null only when there is an example sentence to read. */
    onSpeakExample: (() -> Unit)? = null,
    onRetrySpeech: () -> Unit = {},
    onOpenSpeechSettings: () -> Unit = {},
    onInstallVoiceData: () -> Unit = {},
) {
    // Resolved once: the motion token decides every animation on this card, including whether
    // the reveal animates at all (the user's "动画效果" switch and the system reduce-motion flag).
    val motion = AppTheme.motion
    AppCard(modifier = modifier, onClick = onToggleReveal) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    if (reasonLabel != null) {
                        ReviewBadge(text = reasonLabel)
                    }
                    progress?.let {
                        ReviewBadge(
                            text = it.status.label,
                            container = if (it.status.isConsolidated) {
                                AppTheme.colors.successContainer
                            } else {
                                AppTheme.colors.warningContainer
                            },
                        )
                    }
                }
                IconButton(
                    onClick = onToggleFavorite,
                    modifier = Modifier
                        .size(48.dp)
                        .semantics {
                            contentDescription = if (isFavorite) "取消收藏" else "收藏单词"
                        },
                ) {
                    Icon(
                        imageVector = if (isFavorite) Icons.Filled.Star else Icons.Outlined.Star,
                        contentDescription = null,
                        tint = if (isFavorite) AppTheme.colors.warning else AppTheme.colors.mutedText,
                    )
                }
            }

            Text(
                text = word.word,
                style = wordStyle,
                modifier = Modifier.fillMaxWidth(),
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                if (word.phonetic.isNotBlank()) {
                    Text(
                        text = word.phonetic,
                        style = phoneticStyle,
                        color = AppTheme.colors.mutedText,
                    )
                }
                Spacer(Modifier.width(Spacing.sm))
                SpeakButton(
                    state = speechState,
                    onClick = onSpeakWord,
                    label = "单词",
                )
                // The example button appears only when there is something to read: the shipped
                // vocabulary has no example sentences, so this stays hidden rather than opening a
                // failure dialog for missing content.
                if (onSpeakExample != null && word.exampleSentence.isNotBlank()) {
                    SpeakButton(
                        state = speechState,
                        onClick = onSpeakExample,
                        label = "例句",
                    )
                }
            }

            SpeechMessage(
                state = speechState,
                onRetry = onRetrySpeech,
                onOpenSettings = onOpenSpeechSettings,
                onInstallVoiceData = onInstallVoiceData,
            )

            HorizontalDivider(color = AppTheme.colors.hairline)

            // The reveal is a real transition, but a cheap one: only the fade and a small rise
            // (both on the graphics layer) are animated, so the card's height changes once instead
            // of being re-measured on every animation frame. A size-animating reveal would relayout
            // this card — including the 40sp word — 60 times a second.
            AnimatedContent(
                targetState = revealed,
                transitionSpec = {
                    if (targetState) {
                        (fadeIn(tween(motion.durationMillis(REVEAL_MILLIS))) +
                            slideInVertically(tween(motion.durationMillis(REVEAL_MILLIS))) { it / 8 })
                            .togetherWith(fadeOut(tween(motion.durationMillis(REVEAL_MILLIS / 2))))
                    } else {
                        (fadeIn(tween(motion.durationMillis(REVEAL_MILLIS))) +
                            slideInVertically(tween(motion.durationMillis(REVEAL_MILLIS))) { -it / 8 })
                            .togetherWith(fadeOut(tween(motion.durationMillis(REVEAL_MILLIS / 2))))
                    }
                },
                label = "word-reveal",
            ) { isRevealed ->
                if (isRevealed) {
                    WordMeaningBlock(word = word)
                } else {
                    Text(
                        text = "先回忆一下，再点击卡片查看释义",
                        style = MaterialTheme.typography.bodyMedium,
                        color = AppTheme.colors.mutedText,
                    )
                }
            }
        }
    }
}

/** Reveal transition duration; short enough to feel instant, long enough to be perceived. */
private const val REVEAL_MILLIS = 240

/** Meanings, example sentence, translation and collocations. */
@Composable
private fun WordMeaningBlock(word: Word) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        if (word.partOfSpeech.isNotBlank()) {
            ReviewBadge(
                text = word.partOfSpeech,
                container = MaterialTheme.colorScheme.surfaceContainerHigh,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        word.meanings.forEach { meaning ->
            Text(text = meaning, style = MaterialTheme.typography.titleSmall)
        }
        if (word.exampleSentence.isNotBlank()) {
            Column {
                Text(
                    text = word.exampleSentence,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (word.exampleTranslation.isNotBlank()) {
                    Text(
                        text = word.exampleTranslation,
                        style = MaterialTheme.typography.bodyMedium,
                        color = AppTheme.colors.mutedText,
                    )
                }
            }
        }
        if (word.collocations.isNotEmpty()) {
            Column {
                Text(
                    text = "常见搭配",
                    style = MaterialTheme.typography.labelMedium,
                    color = AppTheme.colors.mutedText,
                )
                Text(
                    text = word.collocations.joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

/**
 * The three self-assessment actions.
 *
 * Buttons are disabled while an answer is being written to the database, which is what stops
 * a fast double tap from recording the same answer twice.
 */
@Composable
fun AnswerActionBar(
    onUnknown: () -> Unit,
    onFuzzy: () -> Unit,
    onMastered: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        AnswerButton(
            text = "不认识",
            onClick = onUnknown,
            enabled = enabled,
            container = MaterialTheme.colorScheme.errorContainer,
            content = MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier.weight(1f),
        )
        AnswerButton(
            text = "模糊",
            onClick = onFuzzy,
            enabled = enabled,
            container = AppTheme.colors.warningContainer,
            content = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        AnswerButton(
            text = "已掌握",
            onClick = onMastered,
            enabled = enabled,
            container = AppTheme.colors.successContainer,
            content = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun AnswerButton(
    text: String,
    onClick: () -> Unit,
    enabled: Boolean,
    container: Color,
    content: Color,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.heightIn(min = 52.dp),
        shape = MaterialTheme.shapes.medium,
        color = if (enabled) container else MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = if (enabled) content else AppTheme.colors.mutedText,
        onClick = onClick,
        enabled = enabled,
    ) {
        Box(
            modifier = Modifier.padding(horizontal = Spacing.sm, vertical = Spacing.md),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * One exam question.
 *
 * The component is deliberately "dumb": it renders the prompt and collects an answer, and it
 * has **no access to the correct answer**. That is what guarantees a closed-book exam cannot
 * leak a solution through the UI layer: correctness lives only in the repository until
 * submission.
 */
@Composable
fun ExamQuestionCard(
    question: ExamQuestion,
    selectedOptionIndex: Int?,
    spellingInput: String,
    onSelectOption: (Int) -> Unit,
    onSpellingChange: (String) -> Unit,
    onSubmitSpelling: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AppCard(modifier = modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.lg)) {
            ReviewBadge(
                text = question.type.label,
                container = MaterialTheme.colorScheme.surfaceContainerHigh,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Column {
                Text(
                    text = if (question.type == ExamQuestionType.SPELLING) {
                        "请根据释义写出英文单词"
                    } else {
                        "请选择正确的答案"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = AppTheme.colors.mutedText,
                )
                Text(
                    text = question.prompt,
                    style = if (question.type == ExamQuestionType.SPELLING) {
                        MaterialTheme.typography.headlineSmall
                    } else {
                        wordStyle
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                if (question.promptPhonetic.isNotBlank() &&
                    question.type != ExamQuestionType.EN_TO_ZH
                ) {
                    Text(
                        text = question.promptPhonetic,
                        style = phoneticStyle,
                        color = AppTheme.colors.mutedText,
                    )
                }
            }

            when (question.type) {
                ExamQuestionType.SPELLING -> {
                    var localValue by remember(question.id) { mutableStateOf(spellingInput) }
                    OutlinedTextField(
                        value = localValue,
                        onValueChange = { newValue ->
                            localValue = newValue
                            onSpellingChange(newValue)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .semantics { contentDescription = "拼写答案输入框" },
                        label = { Text("填写英文单词") },
                        singleLine = true,
                        shape = MaterialTheme.shapes.medium,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { onSubmitSpelling() }),
                    )
                }

                else -> {
                    Column(
                        modifier = Modifier.selectableGroup(),
                        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                    ) {
                        question.options.forEachIndexed { index, option ->
                            val selected = selectedOptionIndex == index
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .selectable(
                                        selected = selected,
                                        role = Role.RadioButton,
                                        onClick = { onSelectOption(index) },
                                    ),
                                shape = MaterialTheme.shapes.medium,
                                color = if (selected) {
                                    MaterialTheme.colorScheme.primaryContainer
                                } else {
                                    MaterialTheme.colorScheme.surfaceContainerLow
                                },
                            ) {
                                Row(
                                    modifier = Modifier.padding(
                                        horizontal = Spacing.md,
                                        vertical = Spacing.md,
                                    ),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    RadioButton(selected = selected, onClick = null)
                                    Text(
                                        text = option,
                                        style = MaterialTheme.typography.bodyLarge,
                                        modifier = Modifier.padding(start = Spacing.sm),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Small helper used by the result screens to list correct/wrong answers. */
@Composable
fun AnswerResultRow(
    prompt: String,
    correctAnswer: String,
    givenAnswer: String?,
    isCorrect: Boolean,
    modifier: Modifier = Modifier,
) {
    AppCard(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = if (isCorrect) Icons.Filled.Check else Icons.Filled.Close,
                contentDescription = if (isCorrect) "回答正确" else "回答错误",
                tint = if (isCorrect) AppTheme.colors.successGreen else MaterialTheme.colorScheme.error,
                modifier = Modifier.size(20.dp),
            )
            Column(modifier = Modifier.padding(start = Spacing.md)) {
                Text(text = prompt, style = MaterialTheme.typography.titleSmall)
                Text(
                    text = "正确答案：$correctAnswer",
                    style = MaterialTheme.typography.bodySmall,
                    color = AppTheme.colors.mutedText,
                )
                if (givenAnswer.isNullOrBlank()) {
                    Text(
                        text = "你的答案：未作答",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                } else if (!isCorrect) {
                    Text(
                        text = "你的答案：$givenAnswer",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}

/** Plain text row used in headers ("今天还有 12 个词该复习"). */
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = title, style = MaterialTheme.typography.titleMedium)
        if (actionLabel != null && onAction != null) {
            TextButton(onClick = onAction) { Text(actionLabel) }
        }
    }
}

// --- Previews -------------------------------------------------------------------------------

private val previewWord = Word(
    id = 1,
    word = "abandon",
    phonetic = "/əˈbændən/",
    partOfSpeech = "v.",
    meanings = listOf("放弃；抛弃", "遗弃"),
    exampleSentence = "He had to abandon the plan because of the storm.",
    exampleTranslation = "由于暴风雨，他不得不放弃这个计划。",
    collocations = listOf("abandon a plan", "abandon hope"),
    level = CetLevel.CET4,
    source = "demo",
    license = "CC0-1.0",
)

@Preview(showBackground = true)
@Composable
private fun WordCardHiddenPreview() {
    CetVocabTheme {
        WordCard(
            word = previewWord,
            revealed = false,
            isFavorite = false,
            onToggleReveal = {},
            onToggleFavorite = {},
            onSpeakWord = {},
            reasonLabel = "今日新词",
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun WordCardRevealedPreview() {
    CetVocabTheme {
        WordCard(
            word = previewWord,
            revealed = true,
            isFavorite = true,
            onToggleReveal = {},
            onToggleFavorite = {},
            onSpeakWord = {},
            progress = WordProgress(wordId = 1, wrongCount = 2),
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun AnswerBarPreview() {
    CetVocabTheme {
        AnswerActionBar(onUnknown = {}, onFuzzy = {}, onMastered = {})
    }
}

@Preview(showBackground = true)
@Composable
private fun ExamCardPreview() {
    CetVocabTheme {
        ExamQuestionCard(
            question = ExamQuestion(
                id = 1,
                orderIndex = 0,
                wordId = 1,
                type = ExamQuestionType.EN_TO_ZH,
                prompt = "abandon",
                promptPhonetic = "/əˈbændən/",
                options = listOf("放弃；抛弃", "吸收", "接受", "实现"),
                correctOptionIndex = 0,
                correctAnswer = "放弃；抛弃",
            ),
            selectedOptionIndex = 1,
            spellingInput = "",
            onSelectOption = {},
            onSpellingChange = {},
            onSubmitSpelling = {},
        )
    }
}
