package com.cetvocab.app.ui.components

import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.cetvocab.app.ui.theme.AppTheme
import com.cetvocab.app.ui.theme.CetVocabTheme
import com.cetvocab.app.ui.theme.Spacing

/**
 * The app's one confirmation dialog.
 *
 * Both actions are real Material 3 [TextButton]s, so TalkBack reaches each of them in turn
 * and the 48dp minimum touch target applies. Dismissal is wired to `onDismissRequest`, which
 * covers the back gesture and the tap outside the scrim as well as the dismiss button.
 */
@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    confirmLabel: String = "确认",
    dismissLabel: String = "取消",
    destructive: Boolean = false,
) {
    // A short fade-in for the warning glyph, driven by the shared motion token so the
    // "reduce motion" setting collapses it to 0ms. `updateTransition` starts at its current
    // target, so nothing animates on the dialog's very first frame.
    val transition = updateTransition(targetState = destructive, label = "confirm-dialog")
    val glyphAlpha by transition.animateFloat(
        transitionSpec = {
            tween(durationMillis = AppTheme.motion.durationMillis(180))
        },
        label = "confirm-dialog-glyph-alpha",
    ) { isDestructive -> if (isDestructive) 1f else 0f }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = modifier,
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
        },
        text = {
            if (destructive) {
                Icon(
                    imageVector = Icons.Filled.Warning,
                    contentDescription = "警告",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier
                        .size(24.dp)
                        .alpha(glyphAlpha),
                )
            }
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = AppTheme.colors.mutedText,
                modifier = Modifier.padding(top = if (destructive) Spacing.sm else 0.dp),
            )
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = if (destructive) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                ),
            ) {
                Text(text = confirmLabel, style = MaterialTheme.typography.labelLarge)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = dismissLabel,
                    style = MaterialTheme.typography.labelLarge,
                    color = AppTheme.colors.mutedText,
                )
            }
        },
    )
}

@Preview(showBackground = true)
@Composable
private fun ConfirmDialogPreview() {
    CetVocabTheme {
        ConfirmDialog(
            title = "清空学习记录？",
            message = "所有学习进度和统计都会被删除，此操作无法撤销。",
            onConfirm = {},
            onDismiss = {},
            confirmLabel = "清空",
            dismissLabel = "取消",
            destructive = true,
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ConfirmDialogNeutralPreview() {
    CetVocabTheme {
        ConfirmDialog(
            title = "加入生词本？",
            message = "这个词会出现在你的生词本里，方便之后复习。",
            onConfirm = {},
            onDismiss = {},
        )
    }
}
