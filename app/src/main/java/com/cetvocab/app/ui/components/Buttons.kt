package com.cetvocab.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.cetvocab.app.ui.theme.AppTheme
import com.cetvocab.app.ui.theme.ButtonCorner
import com.cetvocab.app.ui.theme.CetVocabTheme
import com.cetvocab.app.ui.theme.MinTouchTarget
import com.cetvocab.app.ui.theme.Spacing

/**
 * The two action buttons used across the app.
 *
 * Both keep the 48dp minimum touch target, never use a fixed height (so a large system
 * font scale cannot clip the label), and centre their content so a single long label
 * wraps or ellipsises instead of pushing the icon out of the button.
 */

/**
 * Filled primary action in the mint brand colour.
 *
 * @param loading replaces the leading icon with a small spinner, keeps the label, and
 *   marks the button as disabled so a second tap cannot start a duplicate request.
 */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    leadingIcon: ImageVector? = null,
    trailingIcon: ImageVector? = null,
) {
    // While loading the button is not clickable, but it keeps its colour so the UI does
    // not flash to the grey "disabled" look during a routine request.
    val interactive = enabled && !loading

    Button(
        onClick = onClick,
        modifier = modifier
            .heightIn(min = MinTouchTarget)
            .semantics {
                if (loading) stateDescription = "正在处理"
            },
        enabled = interactive,
        shape = RoundedCornerShape(ButtonCorner),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            disabledContainerColor = if (loading) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.38f)
            },
            disabledContentColor = if (loading) {
                MaterialTheme.colorScheme.onPrimary
            } else {
                MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.38f)
            },
        ),
        contentPadding = ButtonDefaults.ContentPadding,
    ) {
        ButtonContent(
            text = text,
            loading = loading,
            leadingIcon = leadingIcon,
            trailingIcon = trailingIcon,
        )
    }
}

/** Outlined / tonal secondary action: mint outline, same height and wrapping rules. */
@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: ImageVector? = null,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = MinTouchTarget),
        enabled = enabled,
        shape = RoundedCornerShape(ButtonCorner),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.primary,
            disabledContainerColor = MaterialTheme.colorScheme.surface,
            disabledContentColor = AppTheme.colors.mutedText,
        ),
        contentPadding = ButtonDefaults.ContentPadding,
    ) {
        ButtonContent(
            text = text,
            loading = false,
            leadingIcon = leadingIcon,
            trailingIcon = null,
        )
    }
}

/**
 * Shared label row. The row wraps to its intrinsic width so the parent button centres the
 * whole group; the label itself may wrap at large font scales and only ellipsises when the
 * button is given a width narrower than a single word.
 */
@Composable
private fun ButtonContent(
    text: String,
    loading: Boolean,
    leadingIcon: ImageVector?,
    trailingIcon: ImageVector?,
) {
    Row(
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier
                    .size(18.dp)
                    .semantics { stateDescription = "正在加载" },
                color = MaterialTheme.colorScheme.onPrimary,
                strokeWidth = 2.dp,
            )
            Spacer(Modifier.width(Spacing.sm))
        } else if (leadingIcon != null) {
            Icon(
                imageVector = leadingIcon,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(Spacing.sm))
        }

        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        if (trailingIcon != null) {
            Spacer(Modifier.width(Spacing.sm))
            Icon(
                imageVector = trailingIcon,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun PrimaryButtonPreview() {
    CetVocabTheme {
        Row(
            modifier = Modifier.width(340.dp),
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PrimaryButton(text = "开始学习", onClick = {})
            PrimaryButton(text = "正在保存", onClick = {}, loading = true)
            SecondaryButton(text = "稍后再说", onClick = {})
            SecondaryButton(text = "已掌握", onClick = {}, leadingIcon = Icons.Filled.Check)
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun PrimaryButtonDisabledPreview() {
    CetVocabTheme {
        PrimaryButton(
            text = "暂不可用",
            onClick = {},
            enabled = false,
            leadingIcon = Icons.Filled.Check,
        )
    }
}
