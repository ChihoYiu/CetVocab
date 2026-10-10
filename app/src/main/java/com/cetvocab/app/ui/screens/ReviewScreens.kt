package com.cetvocab.app.ui.screens

import androidx.compose.foundation.clickable
import com.cetvocab.app.data.speech.SpeechRateMode
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cetvocab.app.AppContainer
import com.cetvocab.app.domain.mistake.MistakeCategory
import com.cetvocab.app.domain.mistake.MistakeItem
import com.cetvocab.app.domain.mistake.MistakeSort
import com.cetvocab.app.domain.model.CetLevel
import com.cetvocab.app.domain.model.Word
import com.cetvocab.app.domain.srs.MasteryStatus
import com.cetvocab.app.domain.srs.WordProgress
import com.cetvocab.app.ui.ReviewCenterViewModel
import com.cetvocab.app.ui.ViewModelFactories
import com.cetvocab.app.ui.components.AppCard
import com.cetvocab.app.ui.components.ConfirmDialog
import com.cetvocab.app.ui.components.EmptyState
import com.cetvocab.app.ui.components.FilterChipGroup
import com.cetvocab.app.ui.components.LoadingState
import com.cetvocab.app.ui.components.PrimaryButton
import com.cetvocab.app.ui.components.ReviewBadge
import com.cetvocab.app.ui.components.SecondaryButton
import com.cetvocab.app.ui.components.StatCard
import com.cetvocab.app.ui.components.WordSearchBar
import com.cetvocab.app.ui.theme.AppTheme
import com.cetvocab.app.ui.theme.CetVocabTheme
import com.cetvocab.app.ui.theme.Spacing
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * The review centre (错题本 / 复习中心).
 *
 * The six categories map to six indexed queries; search, "minimum mistakes" and the favourites
 * switch are bound parameters. Rows can be drilled one by one or in batch, marked as
 * re-consolidated, or deleted — and deletion always goes through a confirmation dialog.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReviewCenterScreen(
    container: AppContainer,
    onOpenWord: (Long) -> Unit,
    onStartDrill: (List<Long>) -> Unit,
) {
    val viewModel: ReviewCenterViewModel = viewModel(factory = ViewModelFactories.review(container))
    val state by viewModel.state.collectAsStateWithLifecycle()
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    var batchMode by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<List<Long>?>(null) }

    val selectedIds = state.selectedIds
    val message = state.message
    LaunchedEffect(message) {
        if (message != null) {
            kotlinx.coroutines.delay(2_000)
            viewModel.consumeMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (batchMode) "已选择 ${selectedIds.size} 个" else "错题本") },
                actions = {
                    if (batchMode) {
                        TextButton(onClick = {
                            batchMode = false
                            viewModel.clearSelection()
                        }) { Text("完成") }
                    } else {
                        TextButton(
                            onClick = {
                                viewModel.clearSelection()
                                batchMode = true
                            },
                            modifier = Modifier.testTag("review_batch_toggle"),
                        ) { Text("批量") }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        bottomBar = {
            if (batchMode && selectedIds.isNotEmpty()) {
                BatchActionBar(
                    onDrill = {
                        scope.launch { onStartDrill(viewModel.drillIds()) }
                    },
                    onResolve = { viewModel.markResolved(selectedIds.toList()) },
                    onDelete = { pendingDelete = selectedIds.toList() },
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            Column(
                modifier = Modifier.padding(horizontal = Spacing.lg),
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                if (message != null) {
                    AppCard { Text(message, style = MaterialTheme.typography.bodyMedium) }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                    StatCard(
                        label = "待巩固",
                        value = "${state.overview.activeCount}",
                        caption = "今日待复习 ${state.overview.dueTodayCount}",
                        modifier = Modifier.weight(1f),
                    )
                    StatCard(
                        label = "高错误",
                        value = "${state.overview.highWrongCount}",
                        caption = "收藏 ${state.overview.favoriteCount}",
                        modifier = Modifier.weight(1f),
                    )
                }

                FilterChipGroup(
                    options = MistakeCategory.entries.toList(),
                    selected = state.filter.category,
                    onSelect = { viewModel.selectCategory(it, state.filter.level) },
                    label = { it.label },
                    modifier = Modifier.testTag("review_categories"),
                )

                WordSearchBar(
                    query = state.filter.search,
                    onQueryChange = viewModel::setSearch,
                    modifier = Modifier.testTag("review_search"),
                )

                FilterChipGroup(
                    options = WRONG_THRESHOLDS,
                    selected = state.filter.minWrongCount,
                    onSelect = viewModel::setMinWrongCount,
                    label = { if (it == 0) "错误次数不限" else "${it} 次以上" },
                    modifier = Modifier.testTag("review_wrong_threshold"),
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SecondaryButton(
                        text = if (state.filter.favoritesOnly) "只看收藏 ✓" else "只看收藏",
                        onClick = viewModel::toggleFavoritesOnly,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("review_favorites_filter"),
                    )
                    SecondaryButton(
                        text = if (state.sort == MistakeSort.RECENT) "按时间排序" else "按错误次数排序",
                        onClick = {
                            viewModel.setSort(
                                if (state.sort == MistakeSort.RECENT) MistakeSort.WRONG_COUNT else MistakeSort.RECENT,
                            )
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("review_sort"),
                    )
                }

                Text(
                    text = state.filter.category.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = AppTheme.colors.mutedText,
                )
            }

            Spacer(Modifier.height(Spacing.md))

            when {
                state.loading -> LoadingState(modifier = Modifier.fillMaxSize())
                state.items.isEmpty() -> EmptyState(
                    title = "这里还没有记录",
                    description = emptyHintFor(state.filter.category),
                    modifier = Modifier.fillMaxSize(),
                    actionLabel = "开始学习",
                    onAction = { onStartDrill(emptyList()) },
                )

                else -> LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("review_list"),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        start = Spacing.lg,
                        end = Spacing.lg,
                        bottom = Spacing.xl,
                    ),
                    verticalArrangement = Arrangement.spacedBy(Spacing.md),
                ) {
                    items(items = state.items, key = { it.word.id }) { item ->
                        MistakeRow(
                            item = item,
                            batchMode = batchMode,
                            selected = item.word.id in selectedIds,
                            onToggleSelection = { viewModel.toggleSelection(item.word.id) },
                            onToggleFavorite = { viewModel.toggleFavorite(item.word.id, !item.isFavorite) },
                            onClick = {
                                if (batchMode) {
                                    viewModel.toggleSelection(item.word.id)
                                } else {
                                    onOpenWord(item.word.id)
                                }
                            },
                            // Animates insertion/removal/reordering caused by filtering, batch
                            // deletion and "mark as consolidated" — the item animation runs on
                            // the graphics layer of the row only.
                            modifier = Modifier.animateItem(),
                        )
                    }
                }
            }
        }
    }

    pendingDelete?.let { ids ->
        ConfirmDialog(
            title = "删除错题记录",
            message = "将删除 ${ids.size} 条错题记录。单词本身和学习进度不会受影响，但这个操作无法撤销。",
            confirmLabel = "删除",
            destructive = true,
            onConfirm = {
                viewModel.deleteRecords(ids)
                batchMode = false
                pendingDelete = null
            },
            onDismiss = { pendingDelete = null },
        )
    }
}

private val WRONG_THRESHOLDS = listOf(0, 3, 5)

private fun emptyHintFor(category: MistakeCategory): String = when (category) {
    MistakeCategory.DUE_TODAY -> "今天没有到期的复习。到期的单词会按记忆曲线自动出现在这里。"
    MistakeCategory.UNCONSOLIDATED -> "没有处于「学习中 / 待巩固」状态的单词，说明当前进度很稳。"
    MistakeCategory.HIGH_WRONG -> "还没有错 3 次以上的单词，保持下去。"
    MistakeCategory.RECENT -> "最近 7 天没有产生新的错题。"
    MistakeCategory.HISTORY -> "错题历史是空的。答错的单词会自动进入错题本。"
    MistakeCategory.FAVORITES -> "还没有收藏单词。在单词卡片上点击星标即可收藏。"
}

@Composable
private fun BatchActionBar(
    onDrill: () -> Unit,
    onResolve: () -> Unit,
    onDelete: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        PrimaryButton(
            text = "重新练习所选单词",
            onClick = onDrill,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("review_batch_drill"),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            SecondaryButton(
                text = "标记已巩固",
                onClick = onResolve,
                modifier = Modifier
                    .weight(1f)
                    .testTag("review_batch_resolve"),
            )
            SecondaryButton(
                text = "删除记录",
                onClick = onDelete,
                modifier = Modifier
                    .weight(1f)
                    .testTag("review_batch_delete"),
            )
        }
    }
}

@Composable
private fun MistakeRow(
    item: MistakeItem,
    batchMode: Boolean,
    selected: Boolean,
    onToggleSelection: () -> Unit,
    onToggleFavorite: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AppCard(modifier = modifier.clickable(onClick = onClick)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (batchMode) {
                Checkbox(
                    checked = selected,
                    onCheckedChange = { onToggleSelection() },
                    modifier = Modifier.semantics { contentDescription = "选择 ${item.word.word}" },
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = item.word.word, style = MaterialTheme.typography.titleMedium)
                    if (item.word.phonetic.isNotBlank()) {
                        Text(
                            text = "  ${item.word.phonetic}",
                            style = MaterialTheme.typography.bodySmall,
                            color = AppTheme.colors.mutedText,
                        )
                    }
                }
                Text(
                    text = item.word.meaningLabel,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(Spacing.xs))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ReviewBadge(
                        text = "错 ${item.wrongCount} 次",
                        container = if (item.wrongCount >= 3) {
                            MaterialTheme.colorScheme.errorContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerHigh
                        },
                    )
                    ReviewBadge(
                        text = item.statusLabel,
                        container = if (item.progress.status.isConsolidated) {
                            AppTheme.colors.successContainer
                        } else {
                            AppTheme.colors.warningContainer
                        },
                    )
                    if (item.isResolved) {
                        ReviewBadge(text = "已巩固", container = AppTheme.colors.successContainer)
                    }
                }
                Text(
                    text = buildString {
                        append("最近错误：")
                        append(formatTimestamp(item.lastWrongAt))
                        append(" · 下次复习：")
                        append(
                            if (item.progress.dueAt <= 0L) {
                                "未排期"
                            } else {
                                formatTimestamp(item.progress.dueAt)
                            },
                        )
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = AppTheme.colors.mutedText,
                )
            }
            IconButton(
                onClick = onToggleFavorite,
                modifier = Modifier.semantics {
                    contentDescription = if (item.isFavorite) "取消收藏" else "收藏单词"
                },
            ) {
                Icon(
                    imageVector = if (item.isFavorite) Icons.Filled.Star else Icons.Outlined.Star,
                    contentDescription = null,
                    tint = if (item.isFavorite) AppTheme.colors.warning else AppTheme.colors.mutedText,
                )
            }
        }
    }
}

/**
 * Timestamp label for mistake rows.
 *
 * A single immutable `DateTimeFormatter` is shared instead of building a `SimpleDateFormat` per
 * call: this runs for every visible row on every recomposition, so the old version allocated a
 * formatter (and a `Date`) continuously while the list scrolled.
 */
private val TIMESTAMP_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("MM-dd HH:mm")

private fun formatTimestamp(millis: Long): String {
    if (millis <= 0L) return "—"
    return TIMESTAMP_FORMATTER.format(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()))
}

/** Single word view with its full learning state. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WordDetailScreen(
    container: AppContainer,
    wordId: Long,
    onBack: () -> Unit,
    onPractice: (Long) -> Unit,
) {
    var detail by remember { mutableStateOf<com.cetvocab.app.domain.model.WordWithProgress?>(null) }
    var pendingDelete by remember { mutableStateOf(false) }
    var rateMode by remember { mutableStateOf(SpeechRateMode.SYSTEM) }
    val speech = com.cetvocab.app.ui.speech.rememberSpeechActions(container.speechService) { rateMode }
    val speechState by speech.state.collectAsStateWithLifecycle()
    // The screen owns its own coroutine scope: no GlobalScope, and every write is cancelled
    // automatically when the user leaves the screen.
    val scope = rememberCoroutineScope()

    LaunchedEffect(wordId) {
        detail = container.mistakeRepository.detail(wordId)
        rateMode = container.settingsRepository.current().speechRateMode
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("单词详情") },
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
        val current = detail
        if (current == null) {
            LoadingState(modifier = Modifier.fillMaxSize().padding(padding))
            return@Scaffold
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.lg),
        ) {
            com.cetvocab.app.ui.components.WordCard(
                word = current.word,
                progress = current.progress,
                revealed = true,
                isFavorite = current.progress.isFavorite,
                onToggleReveal = {},
                onToggleFavorite = {
                    scope.launch {
                        container.mistakeRepository.setFavorite(wordId, !current.progress.isFavorite)
                        detail = container.mistakeRepository.detail(wordId)
                    }
                },
                onSpeakWord = { speech.speakWord(current.word) },
                speechState = speechState,
                // The detail screen is the one place where reading every field is useful, so both
                // the example and the Chinese meaning get their own button (each hidden when empty).
                onSpeakExample = ({ speech.speakExample(current.word) })
                    .takeIf { current.word.exampleSentence.isNotBlank() },
                onRetrySpeech = speech::retry,
                onOpenSpeechSettings = speech::openSystemSpeechSettings,
                onInstallVoiceData = speech::installVoiceData,
                modifier = Modifier.testTag("word_detail_card"),
            )

            SecondaryButton(
                text = "朗读释义",
                onClick = { speech.speakMeaning(current.word) },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("word_detail_speak_meaning"),
            )

            AppCard {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    KeyValueRow("掌握状态", current.progress.status.label)
                    KeyValueRow("掌握等级", "${current.progress.masteryLevel} / 5")
                    KeyValueRow("错误次数", "${current.progress.wrongCount}")
                    KeyValueRow("模糊次数", "${current.progress.fuzzyCount}")
                    KeyValueRow("连续答对", "${current.progress.consecutiveCorrectCount}")
                    KeyValueRow("复习间隔", "${current.progress.intervalDays} 天")
                    KeyValueRow("下次复习", formatTimestamp(current.progress.dueAt))
                    if (current.word.source.isNotBlank()) {
                        KeyValueRow("来源", current.word.source)
                    }
                    if (current.word.license.isNotBlank()) {
                        KeyValueRow("许可", current.word.license)
                    }
                }
            }

            PrimaryButton(
                text = "单独练习这个单词",
                onClick = { onPractice(wordId) },
                modifier = Modifier.fillMaxWidth(),
            )
            SecondaryButton(
                text = "删除这条错题记录",
                onClick = { pendingDelete = true },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(Spacing.lg))
        }
    }

    if (pendingDelete) {
        ConfirmDialog(
            title = "删除错题记录",
            message = "删除后这个单词不再出现在错题本中，学习进度会保留。",
            confirmLabel = "删除",
            destructive = true,
            onConfirm = {
                scope.launch {
                    container.mistakeRepository.deleteRecords(listOf(wordId))
                    onBack()
                }
                pendingDelete = false
            },
            onDismiss = { pendingDelete = false },
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun MistakeRowPreview() {
    CetVocabTheme {
        MistakeRow(
            item = MistakeItem(
                word = Word(
                    id = 1,
                    word = "abandon",
                    phonetic = "/əˈbændən/",
                    partOfSpeech = "v.",
                    meanings = listOf("放弃；抛弃"),
                    level = CetLevel.CET4,
                ),
                progress = WordProgress(wordId = 1, status = MasteryStatus.RELEARNING, wrongCount = 3),
                wrongCount = 3,
                fuzzyCount = 1,
                firstWrongAt = 0L,
                lastWrongAt = System.currentTimeMillis(),
                lastSource = "STUDY",
                isResolved = false,
            ),
            batchMode = false,
            selected = false,
            onToggleSelection = {},
            onToggleFavorite = {},
            onClick = {},
        )
    }
}
