package com.cetvocab.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.cetvocab.app.R
import com.cetvocab.app.data.repository.SeedProgress
import com.cetvocab.app.domain.motivation.StartupTip
import com.cetvocab.app.ui.components.ReviewBadge
import com.cetvocab.app.ui.theme.AppTheme
import com.cetvocab.app.ui.theme.CetVocabTheme
import com.cetvocab.app.ui.theme.Spacing

/**
 * The cold-start screen: one encouragement line, plus the vocabulary preparation progress on a
 * first launch.
 *
 * Why it is a full screen rather than a card on the home screen: the brief asks for the line at
 * app start, and this is the only moment the app already owns — the built-in vocabulary is being
 * written to the database here. Folding the line into that existing wait means the feature adds no
 * new step to the launch sequence, and the screen never goes blank when the asset is missing
 * (the caller passes the built-in fallback line in that case).
 *
 * A tap anywhere continues immediately, so the line can never hold a learner hostage; the caller
 * also dismisses it on a timer.
 */
@Composable
fun StartupTipScreen(
    tip: StartupTip?,
    seedProgress: SeedProgress?,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onContinue,
            )
            .semantics { contentDescription = "开屏提示语，轻触继续" }
            .testTag("startup_tip"),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.xl),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )

            if (tip != null) {
                ReviewBadge(
                    text = tip.category,
                    container = MaterialTheme.colorScheme.surfaceContainerHigh,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = tip.text,
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onBackground,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.testTag("startup_tip_text"),
                )
            }

            Spacer(Modifier.height(Spacing.xl))

            if (seedProgress != null) {
                // Unchanged wording from the loading state this screen replaces: the numbers come
                // from the real seeding loop, not from a fake timer.
                Text(
                    text = "正在准备词库 ${seedProgress.packName}（${seedProgress.processed} / ${seedProgress.total}）",
                    style = MaterialTheme.typography.bodySmall,
                    color = AppTheme.colors.mutedText,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.testTag("startup_seed_progress"),
                )
                LinearProgressIndicator(
                    progress = { seedProgress.fraction },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 6.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    strokeCap = StrokeCap.Round,
                    drawStopIndicator = {},
                )
            } else {
                Text(
                    text = "轻触继续",
                    style = MaterialTheme.typography.bodySmall,
                    color = AppTheme.colors.mutedText,
                    modifier = Modifier.testTag("startup_tip_continue"),
                )
            }
        }
    }
}

/** Placeholder used before the catalogue has been read; the caller never renders it for long. */
private val previewTip = StartupTip(
    id = "t001",
    category = "坚持与习惯",
    text = "把今天该做的一点点做完，就已经在往前走了。",
)

@Preview(showBackground = true)
@Composable
private fun StartupTipScreenPreview() {
    CetVocabTheme {
        StartupTipScreen(tip = previewTip, seedProgress = null, onContinue = {})
    }
}

@Preview(showBackground = true)
@Composable
private fun StartupTipScreenSeedingPreview() {
    CetVocabTheme {
        StartupTipScreen(
            tip = previewTip,
            seedProgress = SeedProgress(packName = "CET-4 词库", processed = 1_200, total = 3_849),
            onContinue = {},
        )
    }
}
