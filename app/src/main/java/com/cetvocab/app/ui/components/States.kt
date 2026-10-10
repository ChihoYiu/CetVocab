package com.cetvocab.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.cetvocab.app.ui.theme.AppTheme
import com.cetvocab.app.ui.theme.CardCorner
import com.cetvocab.app.ui.theme.CetVocabTheme
import com.cetvocab.app.ui.theme.Spacing

/**
 * The three full-screen states: nothing here yet, working on it, and something failed.
 *
 * Every state is a centred column built from [Arrangement.spacedBy] rather than fixed
 * padding on each child, so the spacing scales with the content instead of fighting it at
 * large font scales. Nothing has a fixed height and no text is limited to one line.
 */

/**
 * Empty state: soft mint badge for the icon, a title, a muted explanation and an optional
 * secondary action.
 *
 * @param actionLabel shown only when both this and [onAction] are non-null.
 */
@Composable
fun EmptyState(
    title: String,
    description: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier.padding(horizontal = Spacing.xl, vertical = Spacing.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        if (icon != null) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .background(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = CircleShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    // Decorative: the title and description below already carry the meaning.
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp),
                )
            }
            Spacer(Modifier.height(Spacing.xs))
        }

        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )

        Text(
            text = description,
            style = MaterialTheme.typography.bodyMedium,
            color = AppTheme.colors.mutedText,
            textAlign = TextAlign.Center,
        )

        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(Spacing.xs))
            SecondaryButton(text = actionLabel, onClick = onAction)
        }
    }
}

/** Centred spinner with a status line; the spinner announces itself as a busy state. */
@Composable
fun LoadingState(
    modifier: Modifier = Modifier,
    text: String = "正在加载…",
) {
    Column(
        modifier = modifier.padding(horizontal = Spacing.xl, vertical = Spacing.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        CircularProgressIndicator(
            modifier = Modifier
                .size(32.dp)
                .semantics { stateDescription = text },
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = AppTheme.colors.mutedText,
            textAlign = TextAlign.Center,
        )
    }
}

/** Soft coral card that explains a failure and offers a retry when one is possible. */
@Composable
fun ErrorState(
    message: String,
    modifier: Modifier = Modifier,
    title: String = "出了点问题",
    onRetry: (() -> Unit)? = null,
    retryLabel: String = "重试",
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(CardCorner),
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
    ) {
        Column(
            modifier = Modifier.padding(Spacing.lg),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Icon(
                imageVector = Icons.Outlined.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(28.dp),
            )

            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
                textAlign = TextAlign.Center,
            )

            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
                textAlign = TextAlign.Center,
            )

            if (onRetry != null) {
                Spacer(Modifier.height(Spacing.xs))
                PrimaryButton(
                    text = retryLabel,
                    onClick = onRetry,
                    leadingIcon = Icons.Filled.Refresh,
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun EmptyStatePreview() {
    CetVocabTheme {
        EmptyState(
            title = "还没有生词",
            description = "在练习中把不认识的单词加入生词本，之后就能在这里复习了。",
            icon = Icons.Filled.Search,
            actionLabel = "去练习",
            onAction = {},
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun LoadingStatePreview() {
    CetVocabTheme {
        LoadingState(modifier = Modifier.fillMaxWidth())
    }
}

@Preview(showBackground = true)
@Composable
private fun ErrorStatePreview() {
    CetVocabTheme {
        Box(modifier = Modifier.padding(Spacing.lg)) {
            ErrorState(
                message = "无法连接到服务器，请检查网络后重试。",
                onRetry = {},
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
