package com.cetvocab.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.cetvocab.app.domain.insight.Insight
import com.cetvocab.app.domain.insight.InsightKind
import com.cetvocab.app.domain.motivation.MotivationMessage
import com.cetvocab.app.domain.motivation.MotivationSource
import com.cetvocab.app.ui.theme.AppTheme
import com.cetvocab.app.ui.theme.Spacing

/**
 * One piece of analysed advice.
 *
 * The card always shows three things: the conclusion, the *evidence* (so the learner can judge it
 * rather than trust it), and one action. A suggestion without its numbers is an opinion, and this
 * app deliberately shows no opinions.
 */
@Composable
fun InsightCard(
    insight: Insight,
    onAction: (Insight) -> Unit,
    modifier: Modifier = Modifier,
) {
    AppCard(modifier = modifier.testTag("insight_card")) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Text(
                text = insight.title,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.testTag("insight_title"),
            )
            Text(
                text = insight.detail,
                style = MaterialTheme.typography.bodyMedium,
                color = AppTheme.colors.mutedText,
            )
            if (insight.actionLabel != null && insight.kind != InsightKind.INSUFFICIENT_DATA) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(
                        onClick = { onAction(insight) },
                        modifier = Modifier.testTag("insight_action"),
                    ) {
                        Text(insight.actionLabel)
                    }
                }
            }
        }
    }
}

/**
 * The daily line of encouragement.
 *
 * Rendered as a quiet, borderless note rather than a "card with a button": the product rule is that
 * encouragement must never look like a task, and must never interrupt study. It carries no action at
 * all, and it hides itself when there is nothing to say.
 */
@Composable
fun MotivationLine(
    message: MotivationMessage,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("motivation_line"),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, AppTheme.colors.hairline),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            Text(
                text = message.text,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.testTag("motivation_text"),
            )
            // Attribution is shown whenever a line is not original. Today every shipped line is
            // original, so this renders nothing — but the rule is enforced in the UI, not just in
            // the data, so a future public-domain quote cannot ship unattributed.
            if (message.source != MotivationSource.ORIGINAL && message.attribution != null) {
                Text(
                    text = "—— ${message.attribution}",
                    style = MaterialTheme.typography.labelSmall,
                    color = AppTheme.colors.mutedText,
                )
            }
        }
    }
}

/**
 * The single source of truth for "what does this number mean".
 *
 * Kept as data (not as prose inside a screen) so the same text can be shown in the app and quoted in
 * the documentation, and so a test can assert that every definition exists.
 *
 * Plain text only: the same strings are rendered in the UI, so no markdown emphasis is used.
 */
enum class StatDefinition(val title: String, val body: String) {
    STUDIED_TODAY(
        title = "今日已学",
        body = "今天首次作答过的不同单词数量。同一个词今天答对又答错，只算 1 个。",
    ),
    ACCURACY_TODAY(
        title = "今日正确率",
        body = "今天选「已掌握」的次数 ÷ 今天作答总次数。今天还没作答时显示「暂无数据」，而不是 0%。",
    ),
    DURATION_TODAY(
        title = "今日时长",
        body = "今天在答题上累计的时间。两次答题之间的间隔最多按 2 分钟计入，避免挂着应用把时长刷上去。",
    ),
    MASTERED(
        title = "已掌握",
        body = "连续答对、复习间隔已经拉长到 7 天以上的单词。变成「模糊」或「不认识」会退回「学习中」。",
    ),
    LEARNING(
        title = "学习中",
        body = "已经开始复习但还没达到「已掌握」的单词，包含答错后正在巩固的词。",
    ),
    NOT_STARTED(
        title = "未学习",
        body = "当前词库里从未作答过的单词。",
    ),
    STREAK(
        title = "连续学习天数",
        body = "相邻学习日都有作答的最长连续段。学习日从凌晨 4 点开始算，所以熬夜学习算前一天；昨天学过、今天还没开始，连续天数仍然保留。",
    ),
    STAGE_GOAL(
        title = "阶段目标",
        body = "下一个词汇里程碑（50 / 200 / 500 / 1000 / 2000 词）。里程碑全部达成后，目标变成整个词库。",
    ),
    REVIEW_COMPLETION(
        title = "复习完成率",
        body = "今天复习过的到期词 ÷ 今天到期的词。今天没有到期词时显示「暂无数据」。",
    ),
    NEXT_STEP(
        title = "下一步建议",
        body = "按「先复习到期词 → 再学新词（不超过每日目标）→ 最后巩固错题」的顺序给出的一个动作。时间只是估算：复习约 20 秒一个词，新词约 45 秒一个词。",
    ),
}

/** All definitions, for the sheet and for documentation. */
val allStatDefinitions: List<StatDefinition> = StatDefinition.entries.toList()
