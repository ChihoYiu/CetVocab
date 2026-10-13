package com.cetvocab.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.cetvocab.app.ui.components.InsightCard
import com.cetvocab.app.ui.components.MotivationLine
import com.cetvocab.app.ui.components.NextStepCard
import com.cetvocab.app.ui.components.StageGoalCard
import com.cetvocab.app.ui.components.StatDefinition
import com.cetvocab.app.ui.components.TodayProgressCard
import com.cetvocab.app.ui.components.WordStatusCard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cetvocab.app.AppContainer
import com.cetvocab.app.domain.srs.ReviewGrade
import com.cetvocab.app.domain.stats.DailyStudyPoint
import com.cetvocab.app.domain.study.RecentEvent
import com.cetvocab.app.domain.study.TodayPlan
import com.cetvocab.app.ui.HomeViewModel
import com.cetvocab.app.ui.ViewModelFactories
import com.cetvocab.app.ui.components.AppCard
import com.cetvocab.app.ui.components.EmptyState
import com.cetvocab.app.ui.components.ErrorState
import com.cetvocab.app.ui.components.LoadingState
import com.cetvocab.app.ui.components.PrimaryButton
import com.cetvocab.app.ui.components.ProgressCard
import com.cetvocab.app.ui.components.ReviewBadge
import com.cetvocab.app.ui.components.SecondaryButton
import com.cetvocab.app.ui.components.SectionHeader
import com.cetvocab.app.ui.components.StatCard
import com.cetvocab.app.ui.theme.AppTheme
import com.cetvocab.app.ui.theme.CetVocabTheme
import com.cetvocab.app.ui.theme.Spacing
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * The learning dashboard.
 *
 * The order of the sections is the product decision on this screen, and it is fixed:
 *  1. an unfinished session (the only thing that must be resumed);
 *  2. **today's progress** — "where am I right now";
 *  3. **the next action** — "what do I do next", visible without scrolling;
 *  4. the word-status distribution and the next milestone;
 *  5. at most two pieces of **advice**, each with its evidence;
 *  6. one line of encouragement, at most once a day;
 *  7. the weekly trend, then recent history last — both are "looking back", not decisions.
 *
 * Nothing on this screen is a placeholder: every number comes from the schedule, the answer log or
 * the analysis rules, and every number can be explained through 「统计口径」.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    container: AppContainer,
    onStartStudy: () -> Unit,
    onResumeStudy: () -> Unit,
    onStartMistakeDrill: () -> Unit,
    onOpenReviewCenter: () -> Unit,
    onOpenStats: () -> Unit,
    onOpenInsightAction: (com.cetvocab.app.domain.insight.Insight) -> Unit = {},
    /** Opens the vocabulary search; the entry lives in the app bar so the dashboard stays as it is. */
    onOpenWordBrowser: () -> Unit = {},
) {
    val viewModel: HomeViewModel = viewModel(factory = ViewModelFactories.home(container))
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showDefinitions by remember { mutableStateOf(false) }

    if (showDefinitions) {
        StatisticsDefinitionsDialog(onDismiss = { showDefinitions = false })
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(state.greeting, style = MaterialTheme.typography.titleLarge)
                        Text(
                            text = state.dateLabel,
                            style = MaterialTheme.typography.bodySmall,
                            color = AppTheme.colors.mutedText,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
                actions = {
                    // Vocabulary search. A single icon here — rather than another card in the
                    // dashboard — keeps the home screen's section order (and its "one next action"
                    // rule) exactly as it was, while making the feature reachable from the screen
                    // the learner opens first. It stays available offline: it queries the same
                    // local database the study queue uses.
                    IconButton(
                        onClick = onOpenWordBrowser,
                        modifier = Modifier
                            .testTag("home_open_word_browser")
                            .semantics { contentDescription = "词库搜索" },
                    ) {
                        Icon(Icons.Filled.Search, contentDescription = null)
                    }
                },
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        val plan = state.plan
        when {
            state.loading || plan == null -> LoadingState(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            )

            state.errorMessage != null -> ErrorState(
                message = state.errorMessage!!,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            )

            plan.newAvailableCount == 0 && plan.dueCount == 0 && plan.fragileCount == 0 &&
                plan.activeMistakeCount == 0 && plan.answeredToday == 0 -> EmptyState(
                title = "还没有可学习的单词",
                description = "当前词库为空。可以到「我的 - 词库导入」导入你自己的 JSON / CSV 词库，或切换词库级别。",
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                actionLabel = "查看统计",
                onAction = onOpenStats,
            )

            // A LazyColumn instead of a vertical scroll: only the sections near the viewport are
            // composed and measured, which keeps the first frame short and the scroll smooth even
            // though the dashboard has six sections and a chart.
            else -> LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .testTag("home_content"),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    start = Spacing.lg,
                    end = Spacing.lg,
                    bottom = Spacing.xl,
                ),
                verticalArrangement = Arrangement.spacedBy(Spacing.lg),
            ) {
                // 1. Unfinished session prompt — the one thing a returning learner needs first.
                if (state.resumableSessionId != null) {
                    item(key = "resume") {
                        AppCard {
                            Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                                Text("上次的学习还没有结束", style = MaterialTheme.typography.titleMedium)
                                Text(
                                    text = "可以继续上次的进度，也可以放弃这次会话重新开始。",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = AppTheme.colors.mutedText,
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                                    PrimaryButton(
                                        text = "继续学习",
                                        onClick = onResumeStudy,
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("home_resume_session"),
                                    )
                                    SecondaryButton(
                                        text = "放弃",
                                        onClick = viewModel::abandonResumableSession,
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                            }
                        }
                    }
                }

                // 2. Today's progress: the first thing the eye should land on.
                item(key = "today") {
                    TodayProgressCard(
                        plan = plan,
                        todayMinutes = state.stats?.todayMinutes ?: 0,
                        onShowDefinitions = { showDefinitions = true },
                    )
                }

                // 3. The single next action. This is the card that answers "下一步做什么", and it is
                // deliberately placed above the secondary numbers so it is visible without scrolling.
                state.nextStep?.let { step ->
                    item(key = "next_step") {
                        NextStepCard(
                            step = step,
                            onStudy = onStartStudy,
                            onReview = if (plan.activeMistakeCount > 0 && plan.dueCount == 0) {
                                onStartMistakeDrill
                            } else {
                                onStartStudy
                            },
                        )
                    }
                }

                // 4. Word status distribution — one bar, three numbers, no chart to interpret.
                state.stats?.let { stats ->
                    item(key = "word_status") {
                        WordStatusCard(
                            statusCounts = stats.statusCounts,
                            totalWords = stats.levelProgress.totalWords,
                        )
                    }

                    // 5. The next reachable milestone.
                    item(key = "stage_goal") {
                        StageGoalCard(goal = stats.stageGoal)
                    }
                }

                // 6. Advice: at most two, most severe first (the engine caps the list).
                state.insights.forEachIndexed { index, insight ->
                    item(key = "insight_$index") {
                        InsightCard(insight = insight, onAction = onOpenInsightAction)
                    }
                }

                // 7. One line of encouragement, at most once a day, with no action attached.
                state.motivation?.let { message ->
                    item(key = "motivation") {
                        MotivationLine(message = message)
                    }
                }

                // 8. Weekly trend: the only chart, kept because it is the evidence behind the numbers
                // above ("最近表现如何"). It is a compact bar row, not a full chart component.
                if (state.weekTrend.isNotEmpty()) {
                    item(key = "trend") {
                        AppCard {
                            Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                                SectionHeader(title = "本周学习趋势", actionLabel = "查看统计", onAction = onOpenStats)
                                WeeklyTrendChart(points = state.weekTrend)
                            }
                        }
                    }
                }

                // 9. Recent activity stays last: it is history, not a decision, so it must not push
                // the next action below the fold.
                item(key = "recent") {
                    AppCard {
                        Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                            SectionHeader(
                                title = "最近学习记录",
                                actionLabel = "错题本",
                                onAction = onOpenReviewCenter,
                            )
                            if (state.recentEvents.isEmpty()) {
                                Text(
                                    text = "还没有学习记录，完成第一组单词后这里会显示最近的作答。",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = AppTheme.colors.mutedText,
                                )
                            } else {
                                state.recentEvents.forEach { event -> RecentEventRow(event) }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Weekday label shared by every bar in the trend chart.
 *
 * Hoisted to one instance: building a `DateTimeFormatter` inside the chart created a new one for
 * each of the seven bars on every recomposition.
 */
private val WEEKDAY_FORMATTER: DateTimeFormatter =
    DateTimeFormatter.ofPattern("E", Locale.CHINA)

private fun TodayPlan.planSummary(): String {
    val parts = buildList {
        if (remainingNew > 0) add("新词 ${remainingNew}")
        if (dueCount > 0) add("复习 ${dueCount.coerceAtMost(dailyReviewLimit)}")
        if (fragileCount > 0) add("未巩固 ${fragileCount}")
    }
    return if (parts.isEmpty()) "今天没有待办，可以回顾错题" else parts.joinToString(" · ")
}

@Composable
private fun RecentEventRow(event: RecentEvent) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = event.word, style = MaterialTheme.typography.titleSmall)
            Text(
                text = event.meaning,
                style = MaterialTheme.typography.bodySmall,
                color = AppTheme.colors.mutedText,
            )
        }
        ReviewBadge(
            text = when (event.grade) {
                ReviewGrade.MASTERED -> "已掌握"
                ReviewGrade.FUZZY -> "模糊"
                ReviewGrade.UNKNOWN -> "不认识"
            },
            container = when (event.grade) {
                ReviewGrade.MASTERED -> AppTheme.colors.successContainer
                ReviewGrade.FUZZY -> AppTheme.colors.warningContainer
                ReviewGrade.UNKNOWN -> MaterialTheme.colorScheme.errorContainer
            },
        )
    }
}

/**
 * Seven-day bar chart.
 *
 * Hand-drawn with plain layout boxes rather than a charting dependency: it needs no library,
 * it inherits the theme colours, and the bars carry a content description so the data is also
 * available to a screen reader.
 */
@Composable
fun WeeklyTrendChart(
    points: List<DailyStudyPoint>,
    modifier: Modifier = Modifier,
) {
    val maxValue = points.maxOfOrNull { it.reviewEvents }?.coerceAtLeast(1) ?: 1
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 120.dp)
            .semantics {
                contentDescription = "本周学习趋势：" + points.joinToString("，") { point ->
                    "${point.date.dayOfWeek.getDisplayName(java.time.format.TextStyle.SHORT, Locale.CHINA)} ${point.reviewEvents} 次"
                }
            },
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom,
    ) {
        points.forEach { point ->
            val fraction = point.reviewEvents.toFloat() / maxValue
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Bottom,
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    text = if (point.reviewEvents > 0) "${point.reviewEvents}" else "",
                    style = MaterialTheme.typography.labelSmall,
                    color = AppTheme.colors.mutedText,
                )
                Box(
                    modifier = Modifier
                        .padding(horizontal = 3.dp)
                        .height((8 + 56 * fraction).dp)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(
                            if (point.hasActivity) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.surfaceContainerHighest
                            },
                        ),
                )
                Text(
                    text = WEEKDAY_FORMATTER.format(point.date),
                    style = MaterialTheme.typography.labelSmall,
                    color = AppTheme.colors.mutedText,
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun WeeklyTrendChartPreview() {
    CetVocabTheme {
        WeeklyTrendChart(
            points = (0..6).map { offset ->
                DailyStudyPoint(
                    dayKey = LocalDate.now().minusDays((6 - offset).toLong()).toString(),
                    date = LocalDate.now().minusDays((6 - offset).toLong()),
                    studiedWords = offset * 3,
                    reviewEvents = offset * 4,
                    correct = offset * 3,
                    fuzzy = offset,
                    wrong = offset,
                    durationMillis = offset * 60_000L,
                )
            },
        )
    }
}

/** Small helper used by other screens to render a "label: value" line. */
@Composable
fun KeyValueRow(label: String, value: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = AppTheme.colors.mutedText,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
        )
    }
}


/**
 * The "统计口径" sheet: every number on the dashboard, explained in one place.
 *
 * It is a dialog rather than another screen so the learner never loses their position, and the text
 * comes from [StatDefinition] so the app and the documentation cannot drift apart.
 */
@Composable
private fun StatisticsDefinitionsDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("知道了") }
        },
        title = { Text("这些数字是怎么算的") },
        text = {
            androidx.compose.foundation.layout.Column(
                modifier = androidx.compose.ui.Modifier
                    .verticalScroll(rememberScrollState())
                    .testTag("stats_definitions"),
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                Text(
                    text = "所有统计都只保存在本机，不会上传。分母为 0 时一律显示「暂无数据」，" +
                        "而不是 0%，避免把「还没学」显示成「学得很差」。",
                    style = MaterialTheme.typography.bodySmall,
                    color = AppTheme.colors.mutedText,
                )
                StatDefinition.entries.forEach { definition ->
                    androidx.compose.foundation.layout.Column(
                        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                    ) {
                        Text(definition.title, style = MaterialTheme.typography.titleSmall)
                        Text(
                            text = definition.body,
                            style = MaterialTheme.typography.bodySmall,
                            color = AppTheme.colors.mutedText,
                        )
                    }
                }
            }
        },
    )
}
