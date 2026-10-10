package com.cetvocab.app.ui.screens

import com.cetvocab.app.data.speech.SpeechRateMode

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cetvocab.app.AppContainer
import com.cetvocab.app.data.settings.UserSettings
import com.cetvocab.app.domain.model.CetLevel
import com.cetvocab.app.ui.OnboardingViewModel
import com.cetvocab.app.ui.ViewModelFactories
import com.cetvocab.app.ui.components.AppCard
import com.cetvocab.app.ui.components.FilterChipGroup
import com.cetvocab.app.ui.components.PrimaryButton
import com.cetvocab.app.ui.components.SecondaryButton
import com.cetvocab.app.ui.components.SectionHeader
import com.cetvocab.app.ui.theme.AppTheme
import com.cetvocab.app.ui.theme.CetVocabTheme
import com.cetvocab.app.ui.theme.Spacing

/**
 * First-run flow.
 *
 * Every step is optional: "稍后设置" keeps the defaults and still marks onboarding as done, so
 * a learner can start studying within two taps and refine everything later in settings.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardingScreen(
    container: AppContainer,
    onFinished: () -> Unit,
) {
    val viewModel: OnboardingViewModel = viewModel(factory = ViewModelFactories.onboarding(container))
    val state by viewModel.state.collectAsStateWithLifecycle()

    val notificationPermission = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { /* The switch stays a user preference even if permission is denied. */ }

    // Navigating from a side effect (never from the composition itself) keeps the screen
    // idempotent if it recomposes while the completion flag is being written.
    LaunchedEffect(state.finished) {
        if (state.finished) onFinished()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("欢迎使用") },
                actions = {
                    TextButton(
                        onClick = { viewModel.finish(skip = true) },
                        modifier = Modifier.testTag("onboarding_skip"),
                    ) { Text("稍后设置") }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(Spacing.lg),
        ) {
            AppCard {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    Text("薄荷单词", style = MaterialTheme.typography.headlineMedium)
                    Text(
                        text = "专注 CET-4 / CET-6 的单词记忆、间隔重复与闭卷自测。学习与考试功能全部离线可用，" +
                            "数据只保存在本机（仅发音音频需要联网下载）。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = AppTheme.colors.mutedText,
                    )
                }
            }

            AppCard {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                    SectionHeader(title = "选择词库")
                    FilterChipGroup(
                        options = CetLevel.selectable,
                        selected = state.level,
                        onSelect = viewModel::setLevel,
                        label = { it.label },
                        modifier = Modifier.testTag("onboarding_level"),
                    )
                    Text(
                        text = "内置为自建演示词条，正式备考可导入你拥有使用权的 JSON / CSV 词库。",
                        style = MaterialTheme.typography.bodySmall,
                        color = AppTheme.colors.mutedText,
                    )
                }
            }

            AppCard {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                    SectionHeader(title = "每日学习目标")
                    StepperRow(
                        label = "每日新词",
                        value = "${state.dailyNewWords} 个",
                        onDecrease = { viewModel.setDailyNewWords(state.dailyNewWords - 5) },
                        onIncrease = { viewModel.setDailyNewWords(state.dailyNewWords + 5) },
                        testTagPrefix = "onboarding_new_words",
                    )
                    StepperRow(
                        label = "每日复习上限",
                        value = "${state.dailyReviewLimit} 个",
                        onDecrease = { viewModel.setDailyReviewLimit(state.dailyReviewLimit - 10) },
                        onIncrease = { viewModel.setDailyReviewLimit(state.dailyReviewLimit + 10) },
                        testTagPrefix = "onboarding_review_limit",
                    )
                    Text(
                        text = "目标可以随时在「我的 - 学习设置」里调整。",
                        style = MaterialTheme.typography.bodySmall,
                        color = AppTheme.colors.mutedText,
                    )
                }
            }

            AppCard {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("学习提醒", style = MaterialTheme.typography.titleMedium)
                            Text(
                                text = "每天固定时间提醒复习，可在系统设置中关闭通知。",
                                style = MaterialTheme.typography.bodySmall,
                                color = AppTheme.colors.mutedText,
                            )
                        }
                        Switch(
                            checked = state.reminderEnabled,
                            onCheckedChange = { enabled ->
                                viewModel.setReminder(enabled, state.reminderHour, state.reminderMinute)
                                if (enabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                                }
                            },
                            modifier = Modifier.testTag("onboarding_reminder_switch"),
                        )
                    }
                    if (state.reminderEnabled) {
                        FilterChipGroup(
                            options = REMINDER_PRESETS,
                            selected = state.reminderHour to state.reminderMinute,
                            onSelect = { (hour, minute) -> viewModel.setReminder(true, hour, minute) },
                            label = { (hour, minute) -> "%02d:%02d".format(hour, minute) },
                            modifier = Modifier.testTag("onboarding_reminder_time"),
                        )
                    }
                }
            }

            AppCard {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                    SectionHeader(title = "朗读方式")
                    Text(
                        text = "朗读使用系统自带的文字转语音服务，语言、声音、语速和音调都跟随系统设置，" +
                            "不需要联网，也不会额外申请权限。",
                        style = MaterialTheme.typography.bodySmall,
                        color = AppTheme.colors.mutedText,
                    )
                    FilterChipGroup(
                        options = SpeechRateMode.entries.toList(),
                        selected = state.speechRateMode,
                        onSelect = viewModel::setSpeechRateMode,
                        label = { it.label },
                        modifier = Modifier.testTag("onboarding_speech_rate"),
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("同时朗读例句", style = MaterialTheme.typography.bodyLarge)
                        Switch(
                            checked = state.readExampleSentences,
                            onCheckedChange = viewModel::setReadExampleSentences,
                            modifier = Modifier.testTag("onboarding_read_examples_switch"),
                        )
                    }
                }
            }

            PrimaryButton(
                text = if (state.saving) "正在保存…" else "开始学习",
                onClick = { viewModel.finish(skip = false) },
                loading = state.saving,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("onboarding_finish"),
            )
            SecondaryButton(
                text = "使用默认设置",
                onClick = { viewModel.finish(skip = true) },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.width(Spacing.sm))
        }
    }
}

/** A labelled -/+ control used for the numeric goals. */
@Composable
fun StepperRow(
    label: String,
    value: String,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
    modifier: Modifier = Modifier,
    testTagPrefix: String? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = label, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            SecondaryButton(
                text = "−",
                onClick = onDecrease,
                modifier = Modifier
                    .width(64.dp)
                    .testTag("${testTagPrefix}_decrease"),
            )
            SecondaryButton(
                text = "+",
                onClick = onIncrease,
                modifier = Modifier
                    .width(64.dp)
                    .testTag("${testTagPrefix}_increase"),
            )
        }
    }
}

/** Preset reminder times — a real time picker is offered in settings. */
val REMINDER_PRESETS: List<Pair<Int, Int>> = listOf(
    7 to 30,
    12 to 30,
    19 to 0,
    21 to 30,
)

@Preview(showBackground = true)
@Composable
private fun StepperRowPreview() {
    CetVocabTheme {
        StepperRow(
            label = "每日新词",
            value = "${UserSettings.DEFAULT_NEW_WORDS} 个",
            onDecrease = {},
            onIncrease = {},
        )
    }
}
