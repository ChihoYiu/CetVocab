package com.cetvocab.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cetvocab.app.AppContainer
import com.cetvocab.app.domain.model.CetLevel
import com.cetvocab.app.domain.model.Word
import com.cetvocab.app.domain.model.WordWithProgress
import com.cetvocab.app.domain.srs.WordProgress
import com.cetvocab.app.ui.ViewModelFactories
import com.cetvocab.app.ui.WordBrowserViewModel
import com.cetvocab.app.ui.components.AppCard
import com.cetvocab.app.ui.components.EmptyState
import com.cetvocab.app.ui.components.LoadingState
import com.cetvocab.app.ui.components.ReviewBadge
import com.cetvocab.app.ui.components.SectionHeader
import com.cetvocab.app.ui.components.WordSearchBar
import com.cetvocab.app.ui.theme.AppTheme
import com.cetvocab.app.ui.theme.CetVocabTheme
import com.cetvocab.app.ui.theme.Spacing

/**
 * Vocabulary browser.
 *
 * Answers the "can I actually look a word up?" question for a list of thousands of entries: the
 * query is debounced, the result set is bounded, the list is lazy, and a tap opens the same word
 * detail screen the rest of the app uses. Progress badges come from the identical
 * `word_with_progress` view, so a word found here shows exactly the mastery state the study
 * screens would show.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WordBrowserScreen(
    container: AppContainer,
    onBack: () -> Unit,
    onOpenWord: (Long) -> Unit,
) {
    val viewModel: WordBrowserViewModel = viewModel(factory = ViewModelFactories.wordBrowser(container))
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("词库浏览") },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.semantics { contentDescription = "返回" },
                    ) { Icon(Icons.Filled.ArrowBack, contentDescription = null) }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            WordSearchBar(
                query = state.query,
                onQueryChange = viewModel::setQuery,
                modifier = Modifier.testTag("browser_search"),
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ReviewBadge(
                    text = state.level.label,
                    container = MaterialTheme.colorScheme.primaryContainer,
                )
                Text(
                    text = state.resultSummary,
                    style = MaterialTheme.typography.bodySmall,
                    color = AppTheme.colors.mutedText,
                    modifier = Modifier.testTag("browser_summary"),
                )
            }

            when {
                state.loading -> LoadingState(
                    text = "正在读取词库…",
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("browser_loading"),
                )

                state.nothingFound -> EmptyState(
                    title = "没有找到匹配的单词",
                    description = "试试只输入单词的前几个字母，或者换一个中文释义关键词；" +
                        "只记得大概拼写时，写出你记得的那部分也可以。",
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("browser_empty"),
                )

                else -> LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("browser_list"),
                    contentPadding = PaddingValues(bottom = Spacing.xl),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    items(items = state.results, key = { it.word.id }) { item ->
                        WordBrowserRow(
                            item = item,
                            onClick = { onOpenWord(item.word.id) },
                            modifier = Modifier.animateItem(),
                        )
                    }

                    // The correction list is shown as its own labelled section: it answers a
                    // different question ("did you mean") and must never be mistaken for an exact
                    // hit. It only exists when the exact search found almost nothing.
                    if (state.approximations.isNotEmpty()) {
                        item(key = "approximation_header") {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = Spacing.md),
                                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                            ) {
                                SectionHeader(title = "拼写相近的单词")
                                Text(
                                    text = "下面这些是拼写最接近的词条，请确认是不是你要找的词；" +
                                        "结果与本地词库一一对应，不存在词库以外的词。",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = AppTheme.colors.mutedText,
                                    modifier = Modifier.testTag("browser_approximation_note"),
                                )
                            }
                        }
                        items(
                            items = state.approximations,
                            key = { "approximation-${it.word.id}" },
                        ) { item ->
                            WordBrowserRow(
                                item = item,
                                isApproximation = true,
                                onClick = { onOpenWord(item.word.id) },
                                modifier = Modifier.animateItem(),
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * One result row: word, phonetic, part of speech, Chinese meaning and — when the vocabulary has one
 * — the English example sentence.
 *
 * Everything shown comes from the local database; a field the source data does not have is simply
 * left out rather than filled with a guess. The example is clamped to one line here (the full
 * sentence and its translation are on the word detail screen), so a long sentence cannot push the
 * next result off the fold.
 */
@Composable
private fun WordBrowserRow(
    item: WordWithProgress,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isApproximation: Boolean = false,
) {
    AppCard(modifier = modifier.fillMaxWidth(), onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = item.word.word, style = MaterialTheme.typography.titleMedium)
                    if (item.word.phonetic.isNotBlank()) {
                        Text(
                            text = "  ${item.word.phonetic}",
                            style = MaterialTheme.typography.bodySmall,
                            color = AppTheme.colors.mutedText,
                        )
                    }
                    if (item.word.partOfSpeech.isNotBlank()) {
                        Text(
                            text = "  ${item.word.partOfSpeech}",
                            style = MaterialTheme.typography.bodySmall,
                            color = AppTheme.colors.mutedText,
                        )
                    }
                    if (isApproximation) {
                        Text(
                            text = "  拼写相近",
                            style = MaterialTheme.typography.labelSmall,
                            color = AppTheme.colors.warning,
                            modifier = Modifier.testTag("browser_approximation_badge"),
                        )
                    }
                }
                Text(
                    // Up to three senses rather than only the first: a search by Chinese meaning can
                    // match a sense that is not the primary one, and showing just the primary would
                    // hide the very text the learner searched for.
                    text = item.word.meanings.take(3).joinToString("；")
                        .ifBlank { item.word.primaryMeaning },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (item.word.exampleSentence.isNotBlank()) {
                    Text(
                        text = item.word.exampleSentence,
                        style = MaterialTheme.typography.bodySmall,
                        color = AppTheme.colors.mutedText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (item.progress.status != com.cetvocab.app.domain.srs.MasteryStatus.NEW) {
                ReviewBadge(
                    text = item.progress.status.label,
                    container = if (item.progress.status.isConsolidated) {
                        AppTheme.colors.successContainer
                    } else {
                        AppTheme.colors.warningContainer
                    },
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun WordBrowserRowPreview() {
    CetVocabTheme {
        WordBrowserRow(
            item = WordWithProgress(
                word = Word(
                    id = 1,
                    word = "abandon",
                    phonetic = "/əˈbændən/",
                    partOfSpeech = "v.",
                    meanings = listOf("放弃；抛弃"),
                    level = CetLevel.CET4,
                ),
                progress = WordProgress(wordId = 1, wrongCount = 2),
            ),
            onClick = {},
        )
    }
}
