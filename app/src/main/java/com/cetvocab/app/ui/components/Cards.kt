package com.cetvocab.app.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.cetvocab.app.ui.theme.AppTheme
import com.cetvocab.app.ui.theme.CardCorner
import com.cetvocab.app.ui.theme.CetVocabTheme
import com.cetvocab.app.ui.theme.Spacing

/** The soft card container used everywhere: white surface, 20dp corners, hairline border. */
@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val shape = MaterialTheme.shapes.large
    val border = AppTheme.colors.hairline
    Surface(
        modifier = modifier
            .clip(shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .border(1.dp, border, shape),
        color = MaterialTheme.colorScheme.surface,
        shape = shape,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Box(Modifier.padding(Spacing.lg)) { content() }
    }
}

/** Small coloured pill used for queue reasons, mastery state and counts. */
@Composable
fun ReviewBadge(
    text: String,
    modifier: Modifier = Modifier,
    container: Color = MaterialTheme.colorScheme.primaryContainer,
    contentColor: Color = MaterialTheme.colorScheme.onPrimaryContainer,
) {
    Surface(
        modifier = modifier,
        color = container,
        contentColor = contentColor,
        shape = CircleShape,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.xs),
        )
    }
}

/**
 * Circular progress ring used as the home dashboard's "today" indicator.
 *
 * Performance notes (these are the reason the code looks the way it does):
 *  * the **sweep** is animated, not the colour — animating the colour of a ring whose colour
 *    never changes would run an animator that draws identical frames;
 *  * [animatedFraction] is read *inside* the `Canvas` draw lambda, so an animation frame
 *    invalidates only the draw phase. Reading it during composition would recompose (and
 *    re-measure) the whole ring — and with it the label text — 60 times a second;
 *  * the ring owns a fixed square, so the arc never depends on the card width and the layout
 *    is a single measure per frame.
 */
@Composable
fun ProgressRing(
    fraction: Float,
    modifier: Modifier = Modifier,
    label: String,
    caption: String? = null,
    trackColor: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
    progressColor: Color = MaterialTheme.colorScheme.primary,
) {
    val safeFraction = fraction.coerceIn(0f, 1f)
    val animatedFraction by animateFloatAsState(
        targetValue = safeFraction,
        animationSpec = tween(
            durationMillis = AppTheme.motion.durationMillis(RING_ANIMATION_MILLIS),
            easing = FastOutSlowInEasing,
        ),
        label = "ring-sweep",
    )
    Box(
        modifier = modifier.describeAs("$label ${(safeFraction * 100).toInt()}%"),
        contentAlignment = Alignment.Center,
    ) {
        val strokeWidth = 12.dp
        Canvas(modifier = Modifier.size(RING_SIZE_DP.dp)) {
            val stroke = strokeWidth.toPx()
            val diameter = size.minDimension - stroke
            val topLeft = Offset(
                x = (size.width - diameter) / 2f,
                y = (size.height - diameter) / 2f,
            )
            val arcSize = Size(diameter, diameter)
            val style = Stroke(width = stroke, cap = StrokeCap.Round)
            drawArc(
                color = trackColor,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = style,
            )
            drawArc(
                color = progressColor,
                // Deferred read: only the draw phase re-runs while the sweep animates.
                startAngle = -90f,
                sweepAngle = 360f * animatedFraction,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = style,
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = label, style = MaterialTheme.typography.headlineSmall)
            if (caption != null) {
                Text(
                    text = caption,
                    style = MaterialTheme.typography.labelMedium,
                    color = AppTheme.colors.mutedText,
                )
            }
        }
    }
}

/** Diameter of the ring; a fixed square keeps the arc independent of the card width. */
private const val RING_SIZE_DP = 156

/** Material 3 "long" duration for a progress change; still respects the reduce-motion switch. */
private const val RING_ANIMATION_MILLIS = 520

/** Linear variant of the same idea, used inside lists and the study screen. */
@Composable
fun StudyProgressIndicator(
    current: Int,
    total: Int,
    modifier: Modifier = Modifier,
    reasonLabel: String? = null,
    accent: Color = MaterialTheme.colorScheme.primary,
) {
    val safeTotal = total.coerceAtLeast(0)
    val fraction = if (safeTotal == 0) 0f else (current.toFloat() / safeTotal).coerceIn(0f, 1f)
    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (reasonLabel != null) {
                ReviewBadge(text = reasonLabel, container = MaterialTheme.colorScheme.primaryContainer)
            } else {
                Box(Modifier)
            }
            Text(
                text = "$current / $safeTotal",
                style = MaterialTheme.typography.labelLarge,
                color = AppTheme.colors.mutedText,
            )
        }
        LinearProgressIndicator(
            progress = { fraction },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = Spacing.sm)
                .heightIn(min = 8.dp)
                .clearAndSetSemantics {
                    progressBarRangeInfo = ProgressBarRangeInfo(fraction, 0f..1f)
                },
            color = accent,
            trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            strokeCap = StrokeCap.Round,
            drawStopIndicator = {},
        )
    }
}

/** Compact metric tile. Deliberately smaller than [ProgressCard] so the home screen keeps hierarchy. */
@Composable
fun StatCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    caption: String? = null,
    accent: Color = MaterialTheme.colorScheme.primary,
) {
    AppCard(modifier = modifier) {
        Column {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = AppTheme.colors.mutedText,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                color = accent,
                fontWeight = FontWeight.SemiBold,
            )
            if (caption != null) {
                Text(
                    text = caption,
                    style = MaterialTheme.typography.bodySmall,
                    color = AppTheme.colors.mutedText,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** The single prominent card on the home dashboard. */
@Composable
fun ProgressCard(
    title: String,
    subtitle: String,
    fraction: Float,
    modifier: Modifier = Modifier,
    centerLabel: String? = null,
    centerCaption: String? = null,
    footer: @Composable (() -> Unit)? = null,
) {
    AppCard(modifier = modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            Column {
                Text(text = title, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = AppTheme.colors.mutedText,
                )
            }
            ProgressRing(
                fraction = fraction,
                label = centerLabel ?: "${(fraction.coerceIn(0f, 1f) * 100).toInt()}%",
                caption = centerCaption,
                modifier = Modifier.fillMaxWidth(),
            )
            footer?.invoke()
        }
    }
}

/** Wraps content with the mint-tinted accent background, used for tips and empty hints. */
@Composable
fun AccentPanel(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.primaryContainer,
        shape = MaterialTheme.shapes.medium,
    ) {
        Box(Modifier.padding(Spacing.md)) { content() }
    }
}

/** Shared modifier helper: a circular icon button container that respects the 48dp target. */
@Composable
fun IconBubble(
    modifier: Modifier = Modifier,
    container: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = modifier.size(48.dp),
        shape = CircleShape,
        color = container,
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(Spacing.sm)) { content() }
    }
}

/** Adds a semantics description without changing the visual tree. */
private fun Modifier.describeAs(description: String): Modifier =
    this.semantics { contentDescription = description }

// --- Previews -------------------------------------------------------------------------------

@Preview(showBackground = true)
@Composable
private fun StatCardPreview() {
    CetVocabTheme {
        StatCard(label = "连续学习", value = "12 天", caption = "最长 21 天")
    }
}

@Preview(showBackground = true)
@Composable
private fun ProgressCardPreview() {
    CetVocabTheme {
        ProgressCard(
            title = "今日计划",
            subtitle = "新词 12 · 复习 18",
            fraction = 0.45f,
            centerLabel = "45%",
            centerCaption = "已完成 13 / 30",
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun StudyProgressPreview() {
    CetVocabTheme {
        StudyProgressIndicator(current = 12, total = 50, reasonLabel = "今日新词")
    }
}

@Preview(showBackground = true)
@Composable
private fun BadgePreview() {
    CetVocabTheme {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            ReviewBadge(text = "到期复习")
            ReviewBadge(text = "未巩固", container = AppTheme.colors.warningContainer)
            ReviewBadge(text = "已掌握", container = AppTheme.colors.successContainer)
        }
    }
}
