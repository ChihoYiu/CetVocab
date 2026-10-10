package com.cetvocab.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cetvocab.app.AppContainer
import com.cetvocab.app.domain.stats.DailyStudyPoint
import com.cetvocab.app.domain.stats.StatsSnapshot
import com.cetvocab.app.ui.StatsViewModel
import com.cetvocab.app.ui.ViewModelFactories
import com.cetvocab.app.ui.components.AppCard
import com.cetvocab.app.ui.components.EmptyState
import com.cetvocab.app.ui.components.FilterChipGroup
import com.cetvocab.app.ui.components.LoadingState
import com.cetvocab.app.ui.components.ReviewBadge
import com.cetvocab.app.ui.components.SectionHeader
import com.cetvocab.app.ui.components.StatCard
import com.cetvocab.app.ui.theme.AppTheme
import com.cetvocab.app.ui.theme.CetVocabTheme
import com.cetvocab.app.ui.theme.Spacing
import java.time.LocalDate

/**
 * Learning statistics.
 *
 * The screen avoids pressure by design: percentages that have no data show "—" instead of 0%,
 * the calendar celebrates activity instead of shaming gaps, and every card has a short,
 * factual caption.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(container: AppContainer) {
    val viewModel: StatsViewModel = viewModel(factory = ViewModelFactories.stats(container))
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("学习统计") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        val snapshot = state.snapshot
        when {
            state.loading || snapshot == null -> LoadingState(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            )

            !snapshot.hasAnyData -> EmptyState(
                title = "还没有学习数据",
                description = "完成第一组单词后，这里会显示累计学习量、正确率、复习完成率和学习日历。" +
                    "现在不需要任何评价，去首页开始第一个单词吧。",
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            )

            else -> LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .testTag("stats_content"),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    start = Spacing.lg,
                    end = Spacing.lg,
                    bottom = Spacing.xl,
                ),
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                item {
                    // Read-only level indicator: switching the word list is a profile setting,
                    // so this screen shows which list the numbers belong to instead of
                    // offering a control that would need to leave the screen.
                    AppCard(modifier = Modifier.testTag("stats_level")) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("当前词库", style = MaterialTheme.typography.bodyMedium)
                            ReviewBadge(
                                text = state.level.label,
                                container = MaterialTheme.colorScheme.primaryContainer,
                            )
                        }
                    }
                }

                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                        StatCard(
                            label = "累计学习单词",
                            value = "${snapshot.totalLearnedWords}",
                            caption = "词库共 ${snapshot.levelProgress.totalWords} 个",
                            modifier = Modifier.weight(1f),
                        )
                        StatCard(
                            label = "累计复习次数",
                            value = "${snapshot.totalReviewEvents}",
                            caption = "含错题与考试",
                            modifier = Modifier.weight(1f),
                        )
                    }
                }

                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                        StatCard(
                            label = "连续学习",
                            value = "${snapshot.streakDays} 天",
                            caption = "最长 ${snapshot.longestStreakDays} 天",
                            accent = AppTheme.colors.successGreen,
                            modifier = Modifier.weight(1f),
                        )
                        StatCard(
                            label = "学习时长",
                            value = "${snapshot.totalMinutes} 分钟",
                            caption = "今天 ${snapshot.todayMinutes} 分钟",
                            modifier = Modifier.weight(1f),
                        )
                    }
                }

                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                        StatCard(
                            label = "正确率",
                            value = snapshot.accuracyPercent?.let { "$it%" } ?: "—",
                            caption = "按作答次数统计",
                            modifier = Modifier.weight(1f),
                        )
                        StatCard(
                            label = "复习完成率",
                            value = snapshot.reviewCompletionPercent?.let { "$it%" } ?: "—",
                            caption = "已复习 / 应复习",
                            modifier = Modifier.weight(1f),
                        )
                    }
                }

                if (snapshot.examCount > 0) {
                    item {
                        StatCard(
                            label = "闭卷考试",
                            value = snapshot.examAveragePercent?.let { "平均 $it%" } ?: "—",
                            caption = "已完成 ${snapshot.examCount} 次",
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }

                item {
                    AppCard {
                        Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                            SectionHeader(title = "词库进度")
                            ProgressLine(
                                label = "已学习",
                                current = snapshot.levelProgress.learnedWords,
                                total = snapshot.levelProgress.totalWords,
                                percent = snapshot.levelProgress.learnedPercent,
                            )
                            ProgressLine(
                                label = "已掌握",
                                current = snapshot.levelProgress.masteredWords,
                                total = snapshot.levelProgress.totalWords,
                                percent = snapshot.levelProgress.masteredPercent,
                                accent = AppTheme.colors.successGreen,
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                                ReviewBadge(text = "待巩固 ${snapshot.fragileCount}", container = AppTheme.colors.warningContainer)
                                ReviewBadge(text = "错题 ${snapshot.activeMistakeCount}", container = MaterialTheme.colorScheme.errorContainer)
                                ReviewBadge(text = "收藏 ${snapshot.favoriteCount}")
                            }
                        }
                    }
                }

                item {
                    AppCard {
                        Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                            SectionHeader(title = "阶段目标")
                            Text(
                                text = snapshot.stageGoal.label,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            LinearProgressIndicator(
                                progress = { snapshot.stageGoal.percent / 100f },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(10.dp)
                                    .clip(RoundedCornerShape(6.dp)),
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                                drawStopIndicator = {},
                            )
                            Text(
                                text = "已完成 ${snapshot.stageGoal.current} / ${snapshot.stageGoal.target}，" +
                                    "还差 ${snapshot.stageGoal.remaining}",
                                style = MaterialTheme.typography.bodySmall,
                                color = AppTheme.colors.mutedText,
                            )
                        }
                    }
                }

                item {
                    AppCard {
                        Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                            SectionHeader(title = "本周趋势")
                            WeeklyTrendChart(points = snapshot.weekly)
                        }
                    }
                }

                item {
                    AppCard {
                        Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                            SectionHeader(title = "学习日历（近 12 周）")
                            CalendarHeatmap(points = snapshot.calendar)
                            Text(
                                text = "颜色越深表示当天作答次数越多。空白只代表那天没有学习，不影响任何评价。",
                                style = MaterialTheme.typography.bodySmall,
                                color = AppTheme.colors.mutedText,
                            )
                        }
                    }
                }

                item {
                    AppCard {
                        Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                            SectionHeader(title = "近 30 天作答趋势")
                            MonthlyBars(points = snapshot.monthly)
                        }
                    }
                }

                item { Spacer(Modifier.height(Spacing.lg)) }
            }
        }
    }
}

@Composable
private fun ProgressLine(
    label: String,
    current: Int,
    total: Int,
    percent: Int?,
    accent: Color = MaterialTheme.colorScheme.primary,
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(text = label, style = MaterialTheme.typography.bodyMedium)
            Text(
                text = if (percent == null) "$current / $total" else "$current / $total（$percent%）",
                style = MaterialTheme.typography.bodySmall,
                color = AppTheme.colors.mutedText,
            )
        }
        LinearProgressIndicator(
            progress = { (percent ?: 0) / 100f },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = Spacing.xs)
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp)),
            color = accent,
            trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            drawStopIndicator = {},
        )
    }
}

/** 12 x 7 grid of study days; intensity encodes the number of answers. */
@Composable
fun CalendarHeatmap(
    points: List<DailyStudyPoint>,
    modifier: Modifier = Modifier,
) {
    val weeks = points.chunked(7)
    val maxEvents = points.maxOfOrNull { it.reviewEvents }?.coerceAtLeast(1) ?: 1
    Column(
        modifier = modifier
            .fillMaxWidth()
            .semantics {
                contentDescription = "学习日历，共 ${points.count { it.hasActivity }} 天有学习记录"
            },
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        weeks.forEach { week ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                week.forEach { day ->
                    val intensity = day.reviewEvents.toFloat() / maxEvents
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(4.dp))
                            .background(heatColor(intensity, day.hasActivity)),
                    )
                }
                // Pad the last, incomplete week so the grid keeps its shape.
                repeat(7 - week.size) {
                    Box(modifier = Modifier.weight(1f).aspectRatio(1f))
                }
            }
        }
    }
}

@Composable
private fun heatColor(intensity: Float, hasActivity: Boolean): Color = when {
    !hasActivity -> MaterialTheme.colorScheme.surfaceContainerHighest
    intensity < 0.25f -> MaterialTheme.colorScheme.primaryContainer
    intensity < 0.5f -> MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
    intensity < 0.75f -> MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
    else -> MaterialTheme.colorScheme.primary
}

/** 30 compact bars; no labels because the exact value is available in the tooltip text below. */
@Composable
fun MonthlyBars(
    points: List<DailyStudyPoint>,
    modifier: Modifier = Modifier,
) {
    val maxEvents = points.maxOfOrNull { it.reviewEvents }?.coerceAtLeast(1) ?: 1
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            points.forEach { point ->
                val fraction = point.reviewEvents.toFloat() / maxEvents
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height((4 + 64 * fraction).dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(
                            if (point.hasActivity) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.surfaceContainerHighest
                            },
                        ),
                )
            }
        }
        Text(
            text = "近 30 天共作答 ${points.sumOf { it.reviewEvents }} 次，" +
                "日均 ${points.sumOf { it.reviewEvents } / points.size.coerceAtLeast(1)} 次",
            style = MaterialTheme.typography.bodySmall,
            color = AppTheme.colors.mutedText,
            modifier = Modifier.padding(top = Spacing.sm),
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun CalendarPreview() {
    CetVocabTheme {
        CalendarHeatmap(
            points = (0 until 84).map { index ->
                DailyStudyPoint(
                    dayKey = LocalDate.now().minusDays((83 - index).toLong()).toString(),
                    date = LocalDate.now().minusDays((83 - index).toLong()),
                    studiedWords = index % 7,
                    reviewEvents = index % 9,
                    correct = index % 5,
                    fuzzy = 1,
                    wrong = 1,
                    durationMillis = 0L,
                )
            },
        )
    }
}
