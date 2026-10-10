package com.cetvocab.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.cetvocab.app.data.speech.SpeechDiagnostics
import com.cetvocab.app.data.speech.SpeechRecovery
import com.cetvocab.app.data.speech.SpeechStatus
import com.cetvocab.app.ui.theme.AppTheme
import com.cetvocab.app.ui.theme.Spacing

/**
 * "Is reading working on this device?" — the diagnosability panel.
 *
 * It answers three questions a learner (or a support request) actually has:
 *  1. which engine is in use, and does it support the language;
 *  2. are the rate/pitch coming from the system settings or from the engine default
 *     (this is what makes "the app ignores my settings" verifiable rather than a guess);
 *  3. has reading been succeeding on this device (the local success/failure counters).
 *
 * Nothing here is uploaded: the counters live in memory for the current run only.
 */
@Composable
fun SpeechStatusCard(
    status: SpeechStatus,
    diagnostics: SpeechDiagnostics,
    onTrySample: () -> Unit,
    onOpenSystemSettings: () -> Unit,
    onInstallVoiceData: () -> Unit,
    onResetCounters: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("speech_status"),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Text(
            text = statusHeadline(status),
            style = MaterialTheme.typography.bodyLarge,
            color = if (status.recovery == null) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.error
            },
        )
        statusDetail(status)?.let { detail ->
            Text(
                text = detail,
                style = MaterialTheme.typography.bodySmall,
                color = AppTheme.colors.mutedText,
            )
        }

        // Recovery actions, matching the same vocabulary as the inline speak button.
        when (status.recovery) {
            SpeechRecovery.OPEN_SYSTEM_SETTINGS -> Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                SecondaryButton(text = "打开系统语音设置", onClick = onOpenSystemSettings)
            }

            SpeechRecovery.INSTALL_VOICE_DATA -> Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                SecondaryButton(text = "安装语音包", onClick = onInstallVoiceData)
                SecondaryButton(text = "系统设置", onClick = onOpenSystemSettings)
            }

            SpeechRecovery.RETRY, null -> Unit
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SecondaryButton(
                text = "试听英文",
                onClick = onTrySample,
                modifier = Modifier
                    .weight(1f)
                    .testTag("speech_try_sample"),
            )
            SecondaryButton(
                text = if (diagnostics.hasData) "清空统计" else "暂无统计",
                onClick = onResetCounters,
                enabled = diagnostics.hasData,
                modifier = Modifier
                    .weight(1f)
                    .testTag("speech_reset_counters"),
            )
        }

        Text(
            text = diagnostics.summary,
            style = MaterialTheme.typography.labelSmall,
            color = AppTheme.colors.mutedText,
            modifier = Modifier.testTag("speech_success_summary"),
        )
        diagnostics.successPercent?.let { percent ->
            Text(
                text = "朗读成功率 $percent%（本机统计，仅本次运行，不会上传）",
                style = MaterialTheme.typography.labelSmall,
                color = AppTheme.colors.mutedText,
                modifier = Modifier.testTag("speech_success_percent"),
            )
        }
    }
}

/** One-line status, phrased for a learner rather than for a log file. */
private fun statusHeadline(status: SpeechStatus): String = when (status) {
    SpeechStatus.Initialising -> "正在连接系统语音服务…"
    is SpeechStatus.Ready -> "系统语音服务可用"
    SpeechStatus.LanguageMissing -> "缺少语音数据"
    SpeechStatus.LanguageUnsupported -> "当前引擎不支持该语言"
    is SpeechStatus.EngineUnavailable -> "系统语音服务不可用"
    is SpeechStatus.Failed -> "朗读出现异常"
}

/** The evidence behind the headline, or null when there is nothing useful to add. */
private fun statusDetail(status: SpeechStatus): String? = when (status) {
    SpeechStatus.Initialising -> null
    is SpeechStatus.Ready -> buildString {
        status.engine?.let { append("引擎：$it") }
        status.language?.let { if (isNotEmpty()) append(" · "); append("默认语言：$it") }
        if (isNotEmpty()) append("\n")
        append(
            if (status.rateFromSystem) {
                "语速来自系统设置"
            } else {
                "未读到系统语速，使用引擎默认语速"
            },
        )
        append(
            if (status.pitchFromSystem) {
                "；音调来自系统设置"
            } else {
                "；未读到系统音调，使用引擎默认音调"
            },
        )
    }

    SpeechStatus.LanguageMissing -> "系统没有安装英语语音数据，安装后即可朗读。"
    SpeechStatus.LanguageUnsupported -> "这个引擎不支持英语，可以在系统设置里换一个引擎。"
    is SpeechStatus.EngineUnavailable -> status.detail ?: "未检测到可用的语音引擎。"
    is SpeechStatus.Failed -> "错误码 ${status.code}。可以重试，或在系统设置中检查语音引擎。"
}
