package com.cetvocab.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.cetvocab.app.domain.stats.StageGoal
import com.cetvocab.app.domain.study.NextStep
import com.cetvocab.app.domain.study.NextStepDestination
import com.cetvocab.app.domain.study.NextStepPlanner
import com.cetvocab.app.domain.study.TodayPlan
import com.cetvocab.app.domain.srs.MasteryStatus
import com.cetvocab.app.ui.theme.AppTheme
import com.cetvocab.app.ui.theme.Spacing

/**
 * The four dashboard cards.
 *
 * They are separate from `HomeScreen` so each one can be reviewed (and screenshot-tested) in
 * isolation, and so the home screen itself stays readable: its job is ordering, not layout detail.
 *
 * Every card carries a `statistics definition` hook: the product requirement is that the numbers are
 * trustworthy, and a number nobody can explain is not trustworthy. The definition text lives in
 * `StatsDefinitions` so the docs and the UI cannot drift apart.
 */

/** ① Today's progress: the ring plus the three numbers a learner actually tracks. */
@Composable
fun TodayProgressCard(
    plan: TodayPlan,
    todayMinutes: Int,
    onShowDefinitions: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AppCard(modifier = modifier.testTag("home_today_progress")) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SectionHeader(title = "今日进度")
                TextButton(onClick = onShowDefinitions, modifier = Modifier.testTag("home_definitions")) {
                    Text("统计口径")
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.lg),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ProgressRing(
                    fraction = plan.progressFraction,
                    label = "${plan.doneCards}",
                    caption = "/ ${plan.plannedCards}",
                    modifier = Modifier.semantics {
                        contentDescription = "今日进度：已完成 ${plan.doneCards} 个，计划 ${plan.plannedCards} 个"
                    },
                )
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    MetricLine(label = "今日已学", value = "${plan.studiedToday} 个词")
                    MetricLine(
                        label = "今日正确率",
                        // Null means "no answers yet" — never shown as 0%, which would read as failure.
                        value = plan.accuracyTodayPercent?.let { "$it%" } ?: "暂无数据",
                    )
                    MetricLine(label = "今日时长", value = if (todayMinutes > 0) "$todayMinutes 分钟" else "暂无数据")
                }
            }
        }
    }
}

/** ② The one thing to do next, with a concrete button. */
@Composable
fun NextStepCard(
    step: NextStep,
    onStudy: () -> Unit,
    onReview: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val action = step.actionLabel
    AppCard(modifier = modifier.testTag("home_next_step")) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            SectionHeader(title = "下一步")
            Text(
                text = NextStepPlanner.describe(step),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.testTag("home_next_step_text"),
            )
            when (step) {
                is NextStep.Review -> PrimaryButton(
                    text = action ?: "开始复习",
                    onClick = onReview,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("home_next_step_action"),
                )

                is NextStep.LearnNew -> PrimaryButton(
                    text = action ?: "开始背新词",
                    onClick = onStudy,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("home_next_step_action"),
                )

                is NextStep.PracticeMistakes -> PrimaryButton(
                    text = action ?: "巩固错题",
                    onClick = onReview,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("home_next_step_action"),
                )

                // Finished and "nothing loaded" have no primary action: offering one would imply
                // there is work left when there is none.
                NextStep.AllDone, is NextStep.NoVocabulary -> Unit
            }
            if (step.destination == NextStepDestination.NONE && step !is NextStep.NoVocabulary) {
                Text(
                    text = "今天的学习目标已经完成，可以休息一下。",
                    style = MaterialTheme.typography.bodySmall,
                    color = AppTheme.colors.mutedText,
                )
            }
        }
    }
}

/** ③ Word-status distribution: one bar, three numbers, no chart library. */
@Composable
fun WordStatusCard(
    statusCounts: Map<MasteryStatus, Int>,
    totalWords: Int,
    modifier: Modifier = Modifier,
) {
    val mastered = statusCounts[MasteryStatus.MASTERED] ?: 0
    val learning = statusCounts[MasteryStatus.LEARNING] ?: 0
    val untouched = (totalWords - mastered - learning).coerceAtLeast(0)

    AppCard(modifier = modifier.testTag("home_word_status")) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            SectionHeader(title = "词汇状态")
            if (totalWords <= 0) {
                Text(
                    text = "词库还没有加载完成。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = AppTheme.colors.mutedText,
                )
                return@Column
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(12.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .semantics {
                        contentDescription = "已掌握 $mastered 个，学习中 $learning 个，未学习 $untouched 个"
                    },
            ) {
                StatusSegment(weight = mastered, color = AppTheme.colors.successGreen)
                StatusSegment(weight = learning, color = MaterialTheme.colorScheme.primary)
                StatusSegment(weight = untouched, color = MaterialTheme.colorScheme.surfaceContainerHigh)
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.lg),
            ) {
                LegendItem(color = AppTheme.colors.successGreen, label = "已掌握", count = mastered)
                LegendItem(color = MaterialTheme.colorScheme.primary, label = "学习中", count = learning)
                LegendItem(
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    label = "未学习",
                    count = untouched,
                )
            }
        }
    }
}

/** ④ The next vocabulary milestone — a goal that is actually reachable this month. */
@Composable
fun StageGoalCard(
    goal: StageGoal,
    modifier: Modifier = Modifier,
) {
    AppCard(modifier = modifier.testTag("home_stage_goal")) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            SectionHeader(title = "阶段目标")
            Text(text = goal.label, style = MaterialTheme.typography.bodyLarge)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            ) {
                if (goal.percent > 0) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(goal.percent / 100f)
                            .height(10.dp)
                            .clip(RoundedCornerShape(5.dp))
                            .background(MaterialTheme.colorScheme.primary),
                    )
                }
            }
            Text(
                text = if (goal.isReached) {
                    "已达成 ${goal.target} 词，继续下一个目标。"
                } else {
                    "${goal.current} / ${goal.target} 词 · 还差 ${goal.remaining} 词"
                },
                style = MaterialTheme.typography.bodySmall,
                color = AppTheme.colors.mutedText,
                modifier = Modifier.testTag("home_stage_goal_text"),
            )
        }
    }
}

@Composable
private fun MetricLine(label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(text = label, style = MaterialTheme.typography.bodySmall, color = AppTheme.colors.mutedText)
        Spacer(Modifier.width(Spacing.sm))
        Text(text = value, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun StatusSegment(weight: Int, color: androidx.compose.ui.graphics.Color) {
    if (weight <= 0) return
    Box(
        modifier = Modifier
            .fillMaxWidth(weight.toFloat())
            .height(12.dp)
            .background(color),
    )
}

@Composable
private fun LegendItem(color: androidx.compose.ui.graphics.Color, label: String, count: Int) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color),
        )
        Spacer(Modifier.width(Spacing.xs))
        Text(
            text = "$label $count",
            style = MaterialTheme.typography.labelSmall,
            color = AppTheme.colors.mutedText,
        )
    }
}
