package com.cetvocab.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.cetvocab.app.data.speech.SpeechRecovery
import com.cetvocab.app.ui.speech.SpeechUiState
import com.cetvocab.app.ui.theme.AppTheme
import com.cetvocab.app.ui.theme.Spacing

/** testTags used by the UI tests; kept as constants so the tests cannot drift from the UI. */
const val TAG_SPEAK = "speak_button"
const val TAG_SPEAK_FAILED = "speak_button_failed"
const val TAG_SPEECH_MESSAGE = "speech_message"

/**
 * The one speak control used everywhere.
 *
 * It exists so every call site gets the same four states, the same wording and the same recovery
 * affordances — the previous implementation had three ad-hoc play buttons, which is exactly how
 * inconsistent failure behaviour appears.
 *
 * The control is a single icon (optionally followed by a short [label]): the failure text is
 * rendered separately by [SpeechMessage] underneath, so a card's layout does not jump when an error
 * suddenly appears.
 */
@Composable
fun SpeakButton(
    state: SpeechUiState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    enabled: Boolean = true,
) {
    val description = when {
        state.isLoading -> "正在准备朗读"
        state.isPlaying -> "停止朗读"
        state.hasFailed -> "朗读失败，点击重试"
        label != null -> "朗读$label"
        else -> "朗读"
    }
    Row(
        modifier = modifier.semantics { contentDescription = description },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(
            onClick = onClick,
            enabled = enabled && !state.isLoading,
            modifier = Modifier
                .size(48.dp)
                .testTag(if (state.hasFailed) TAG_SPEAK_FAILED else TAG_SPEAK),
        ) {
            when {
                state.isLoading -> CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary,
                )

                state.hasFailed -> Icon(
                    imageVector = Icons.Filled.Refresh,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                )

                // The core icon set has no dedicated stop icon; Close is the available, recognisable
                // one, and the accessibility description already says "停止朗读".
                state.isPlaying -> Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )

                else -> Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
        if (label != null) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = AppTheme.colors.mutedText,
            )
        }
    }
}

/**
 * Failure text plus its recovery actions.
 *
 * Renders nothing in every non-failed state, so callers can place it unconditionally. Every failure
 * always offers at least one action — the product rule is "no dead buttons".
 */
@Composable
fun SpeechMessage(
    state: SpeechUiState,
    onRetry: () -> Unit,
    onOpenSettings: () -> Unit,
    onInstallVoiceData: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!state.hasFailed) return
    val message = state.message ?: return

    Column(
        modifier = modifier.testTag(TAG_SPEECH_MESSAGE),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.error,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            when (state.recovery) {
                SpeechRecovery.OPEN_SYSTEM_SETTINGS -> {
                    TextButton(onClick = onOpenSettings) { Text("打开系统语音设置") }
                }

                SpeechRecovery.INSTALL_VOICE_DATA -> {
                    TextButton(onClick = onInstallVoiceData) { Text("安装语音包") }
                    TextButton(onClick = onOpenSettings) { Text("系统设置") }
                }

                // RETRY and null both mean "the same request can simply be tried again".
                SpeechRecovery.RETRY, null -> {
                    TextButton(onClick = onRetry) { Text("重试") }
                }
            }
        }
    }
}
