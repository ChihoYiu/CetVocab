package com.cetvocab.app.ui.screens

import androidx.compose.ui.platform.LocalContext
import com.cetvocab.app.data.speech.SpeechRateMode
import com.cetvocab.app.data.speech.SystemTtsSpeechService
import com.cetvocab.app.ui.components.SpeechMessage
import com.cetvocab.app.ui.components.SpeechStatusCard
import com.cetvocab.app.ui.speech.rememberSpeechActions

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cetvocab.app.AppContainer
import com.cetvocab.app.data.settings.ThemeMode
import com.cetvocab.app.domain.model.CetLevel
import com.cetvocab.app.ui.ProfileViewModel
import com.cetvocab.app.ui.ViewModelFactories
import com.cetvocab.app.ui.components.AppCard
import com.cetvocab.app.ui.components.ConfirmDialog
import com.cetvocab.app.ui.components.EmptyState
import com.cetvocab.app.ui.components.EqualHeightTileRow
import com.cetvocab.app.ui.components.FilterChipGroup
import com.cetvocab.app.ui.components.SecondaryButton
import com.cetvocab.app.ui.components.SectionHeader
import com.cetvocab.app.ui.components.SettingToggleRow
import com.cetvocab.app.ui.components.StatCard
import com.cetvocab.app.ui.theme.AppTheme
import com.cetvocab.app.ui.theme.CetVocabTheme
import com.cetvocab.app.ui.theme.Spacing

/** Profile: word list, quick links, tidy metrics and the entries to data management. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    container: AppContainer,
    onOpenSettings: () -> Unit,
    onOpenDataTransfer: () -> Unit,
    onOpenWordBrowser: () -> Unit,
    onOpenAbout: () -> Unit,
    onOpenLicenses: () -> Unit,
) {
    val viewModel: ProfileViewModel = viewModel(factory = ViewModelFactories.profile(container))
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("我的") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .testTag("profile_content"),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = Spacing.lg,
                end = Spacing.lg,
                bottom = Spacing.xl,
            ),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            item {
                AppCard {
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                        SectionHeader(title = "词库级别")
                        FilterChipGroup(
                            options = CetLevel.selectable,
                            selected = state.settings.level,
                            onSelect = viewModel::setLevel,
                            label = { it.label },
                            modifier = Modifier.testTag("profile_level"),
                        )
                        Text(
                            text = "当前词库共 ${state.wordCount} 个单词；切换级别会同时切换首页计划与统计。",
                            style = MaterialTheme.typography.bodySmall,
                            color = AppTheme.colors.mutedText,
                        )
                    }
                }
            }

            item {
                // EqualHeightTileRow (instead of a bare Row) because only the second tile has a
                // caption: without it the two 色块 come out at different heights and the row looks
                // misaligned. `fillMaxHeight()` makes whichever tile is shorter match the taller one.
                EqualHeightTileRow {
                    StatCard(
                        label = "连续学习",
                        value = "${state.stats?.streakDays ?: 0} 天",
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                    )
                    StatCard(
                        label = "已学习",
                        value = "${state.stats?.totalLearnedWords ?: 0}",
                        caption = "共 ${state.wordCount} 个",
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                    )
                }
            }

            item { SettingsEntry(title = "学习设置", subtitle = "每日目标、提醒、发音、主题与动画", onClick = onOpenSettings) }
            item {
                SettingsEntry(
                    title = "浏览 / 搜索词库",
                    subtitle = "按单词或中文释义查找当前词库中的任意单词",
                    onClick = onOpenWordBrowser,
                    testTag = "profile_word_browser",
                )
            }
            item { SettingsEntry(title = "词库导入与数据导出", subtitle = "导入 JSON / CSV 词库，导出学习记录或完整备份", onClick = onOpenDataTransfer) }
            item { SettingsEntry(title = "词库来源与许可", subtitle = "内置演示词库、用户导入内容的版权说明", onClick = onOpenLicenses) }
            item { SettingsEntry(title = "关于", subtitle = "应用版本 ${state.appVersion}　·　隐私说明", onClick = onOpenAbout) }

            item {
                AppCard {
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                        SectionHeader(title = "词库列表")
                        if (state.packs.isEmpty()) {
                            Text(
                                text = "还没有词库。内置演示词库会在首次启动时自动创建。",
                                style = MaterialTheme.typography.bodyMedium,
                                color = AppTheme.colors.mutedText,
                            )
                        } else {
                            state.packs.forEach { pack ->
                                // No trailing spacer: the gap between rows comes from `spacedBy`, so
                                // the last row is not followed by a stray gap before the card padding.
                                Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text(
                                            text = pack.name,
                                            style = MaterialTheme.typography.titleSmall,
                                            modifier = Modifier.weight(1f),
                                        )
                                        Text(
                                            text = if (pack.isBuiltIn) "内置" else "已导入",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = AppTheme.colors.mutedText,
                                        )
                                    }
                                    Text(
                                        text = "${CetLevel.fromId(pack.level).label} · ${pack.wordCount} 词 · ${pack.license}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = AppTheme.colors.mutedText,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            item {
                AppCard {
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                        SectionHeader(title = "最近导入记录")
                        if (state.importHistory.isEmpty()) {
                            Text(
                                text = "还没有导入或导出记录。",
                                style = MaterialTheme.typography.bodyMedium,
                                color = AppTheme.colors.mutedText,
                            )
                        } else {
                            state.importHistory.take(5).forEach { record ->
                                Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                                    Text(
                                        text = "${record.fileName}（${record.format}）",
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                    Text(
                                        text = if (record.status == "SUCCESS") {
                                            "新增 ${record.inserted} · 更新 ${record.updated} · 跳过 ${record.skipped} · 失败 ${record.failed}"
                                        } else {
                                            "导入失败，数据已回滚（${record.message}）"
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (record.status == "SUCCESS") {
                                            AppTheme.colors.mutedText
                                        } else {
                                            MaterialTheme.colorScheme.error
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsEntry(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    testTag: String? = null,
) {
    AppCard(onClick = onClick, modifier = if (testTag != null) Modifier.testTag(testTag) else Modifier) {
        // `spacedBy` instead of two flush Texts: the title/subtitle gap is then identical to every
        // other two-line block in the app (which all use Spacing.xs).
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = AppTheme.colors.mutedText,
            )
        }
    }
}

/** All user settings, each one writing straight through to DataStore. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    container: AppContainer,
    onBack: () -> Unit,
) {
    val viewModel: ProfileViewModel = viewModel(factory = ViewModelFactories.profile(container))
    val state by viewModel.state.collectAsStateWithLifecycle()
    val settings = state.settings
    val context = LocalContext.current
    // The sample-reading button speaks through the shared engine, using the live rate setting.
    val speech = rememberSpeechActions(container.speechService) { state.settings.speechRateMode }
    val speechState by speech.state.collectAsStateWithLifecycle()

    var pendingReset by remember { mutableStateOf(false) }
    var pendingClearProgress by remember { mutableStateOf(false) }
    var pendingClearAll by remember { mutableStateOf(false) }
    var infoMessage by remember { mutableStateOf<String?>(null) }

    // Android 13+ needs an explicit runtime permission for notifications; Android 8-12 do not,
    // so the request is only launched on the newer versions and never affects older devices.
    val notificationPermission = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        infoMessage = if (granted) "已开启通知权限。" else "未获得通知权限，提醒不会显示通知，可稍后在系统设置中开启。"
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("学习设置") },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.semantics { contentDescription = "返回" },
                    ) { Icon(Icons.Filled.ArrowBack, contentDescription = null) }
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
                .padding(horizontal = Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            if (infoMessage != null) {
                AppCard { Text(infoMessage!!, style = MaterialTheme.typography.bodyMedium) }
            }

            AppCard {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                    SectionHeader(title = "每日目标")
                    StepperRow(
                        label = "每日新词",
                        value = "${settings.dailyNewWordGoal} 个",
                        onDecrease = { viewModel.setDailyNewWordGoal(settings.dailyNewWordGoal - 5) },
                        onIncrease = { viewModel.setDailyNewWordGoal(settings.dailyNewWordGoal + 5) },
                        testTagPrefix = "settings_new_words",
                    )
                    StepperRow(
                        label = "每日复习上限",
                        value = "${settings.dailyReviewLimit} 个",
                        onDecrease = { viewModel.setDailyReviewLimit(settings.dailyReviewLimit - 10) },
                        onIncrease = { viewModel.setDailyReviewLimit(settings.dailyReviewLimit + 10) },
                        testTagPrefix = "settings_review_limit",
                    )
                }
            }

            AppCard {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                    SectionHeader(title = "学习顺序")
                    SettingToggleRow(
                        label = "乱序刷单词",
                        description = "开启后，每次开始学习时会把整个队列随机打乱（到期词、"
                            + "未巩固词与新词混在一起）；关闭时按「先复习到期词、再由难到易、最后新词」"
                            + "的固定顺序。进行中的学习不会被中途打乱。",
                        checked = settings.shuffleWords,
                        onCheckedChange = viewModel::setShuffleWords,
                        testTag = "settings_shuffle_switch",
                    )
                }
            }

            AppCard {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                    SectionHeader(title = "学习提醒")
                    SettingToggleRow(
                        label = "开启每日提醒",
                        checked = settings.reminderEnabled,
                        onCheckedChange = { enabled ->
                            viewModel.setReminder(enabled, settings.reminderHour, settings.reminderMinute)
                            infoMessage = if (enabled) {
                                "已开启提醒。"
                            } else {
                                "已关闭提醒。"
                            }
                            if (enabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }
                        },
                        testTag = "settings_reminder_switch",
                    )
                    if (settings.reminderEnabled) {
                        FilterChipGroup(
                            options = REMINDER_PRESETS,
                            selected = settings.reminderHour to settings.reminderMinute,
                            onSelect = { (hour, minute) -> viewModel.setReminder(true, hour, minute) },
                            label = { (hour, minute) -> "%02d:%02d".format(hour, minute) },
                            modifier = Modifier.testTag("settings_reminder_time"),
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                            SecondaryButton(
                                text = "提前 30 分钟",
                                onClick = {
                                    val total = (settings.reminderHour * 60 + settings.reminderMinute - 30 + 1440) % 1440
                                    viewModel.setReminder(true, total / 60, total % 60)
                                },
                                modifier = Modifier.weight(1f),
                            )
                            SecondaryButton(
                                text = "推后 30 分钟",
                                onClick = {
                                    val total = (settings.reminderHour * 60 + settings.reminderMinute + 30) % 1440
                                    viewModel.setReminder(true, total / 60, total % 60)
                                },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }

            AppCard {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                    SectionHeader(title = "朗读（系统语音）")
                    SpeechStatusCard(
                        status = state.speechStatus,
                        diagnostics = state.speechDiagnostics,
                        onTrySample = { speech.speakSample() },
                        onOpenSystemSettings = { SystemTtsSpeechService.openSystemSpeechSettings(context) },
                        onInstallVoiceData = { SystemTtsSpeechService.requestVoiceDataInstall(context) },
                        onResetCounters = viewModel::resetSpeechDiagnostics,
                    )
                    SpeechMessage(
                        state = speechState,
                        onRetry = speech::retry,
                        onOpenSettings = speech::openSystemSpeechSettings,
                        onInstallVoiceData = speech::installVoiceData,
                    )
                    Text(
                        text = "朗读速度",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    FilterChipGroup(
                        options = SpeechRateMode.entries.toList(),
                        selected = settings.speechRateMode,
                        onSelect = viewModel::setSpeechRateMode,
                        label = { it.label },
                        modifier = Modifier.testTag("settings_speech_rate"),
                    )
                    Text(
                        text = "选择「跟随系统」时，语速与音调由系统的文字转语音设置决定，" +
                            "应用不会覆盖它们。",
                        style = MaterialTheme.typography.bodySmall,
                        color = AppTheme.colors.mutedText,
                    )
                    SettingToggleRow(
                        label = "卡片出现时自动朗读",
                        checked = settings.autoPlayPronunciation,
                        onCheckedChange = viewModel::setAutoPlayPronunciation,
                        testTag = "settings_autoplay_switch",
                    )
                    SettingToggleRow(
                        label = "同时朗读例句",
                        checked = settings.readExampleSentences,
                        onCheckedChange = viewModel::setReadExampleSentences,
                        testTag = "settings_read_examples_switch",
                    )
                    Text(
                        text = "朗读使用系统自带的文字转语音服务：单词与例句用英语，" +
                            "「朗读释义」用中文。应用不联网、不下载音频，也不需要额外的权限。" +
                            "如果系统没有安装语音引擎或缺少英语语音包，朗读会给出提示并提供设置入口。",
                        style = MaterialTheme.typography.bodySmall,
                        color = AppTheme.colors.mutedText,
                    )
                }
            }

            AppCard {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                    SectionHeader(title = "外观")
                    FilterChipGroup(
                        options = ThemeMode.entries.toList(),
                        selected = settings.themeMode,
                        onSelect = viewModel::setThemeMode,
                        label = { it.label },
                        modifier = Modifier.testTag("settings_theme"),
                    )
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        SettingToggleRow(
                            label = "动态颜色",
                            description = "使用系统壁纸配色（Android 12+），关闭后使用薄荷绿品牌色。",
                            checked = settings.dynamicColor,
                            onCheckedChange = viewModel::setDynamicColor,
                            testTag = "settings_dynamic_color_switch",
                        )
                    } else {
                        Text(
                            text = "动态颜色需要 Android 12 及以上，本机使用薄荷绿品牌配色。",
                            style = MaterialTheme.typography.bodySmall,
                            color = AppTheme.colors.mutedText,
                        )
                    }
                    SettingToggleRow(
                        label = "动画效果",
                        description = "关闭后所有过渡立即完成，也会尊重系统的「移除动画」设置。",
                        checked = settings.animationsEnabled,
                        onCheckedChange = viewModel::setAnimationsEnabled,
                        testTag = "settings_animations_switch",
                    )
                }
            }

            AppCard {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                    SectionHeader(title = "数据管理")
                    SecondaryButton(
                        text = "恢复默认设置",
                        onClick = { pendingReset = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("settings_reset"),
                    )
                    SecondaryButton(
                        text = "清空学习进度",
                        onClick = { pendingClearProgress = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("settings_clear_progress"),
                    )
                    SecondaryButton(
                        text = "重置全部数据（含词库）",
                        onClick = { pendingClearAll = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("settings_clear_all"),
                    )
                    Text(
                        text = "「清空学习进度」会删除掌握状态、错题、答题日志与统计；" +
                            "「重置全部数据」还会删除所有已导入词库（内置演示词库会恢复）。",
                        style = MaterialTheme.typography.bodySmall,
                        color = AppTheme.colors.mutedText,
                    )
                }
            }
            Spacer(Modifier.height(Spacing.lg))
        }
    }

    if (pendingReset) {
        ConfirmDialog(
            title = "恢复默认设置",
            message = "每日目标、提醒、发音与外观设置都会恢复为默认值。学习数据不会受影响。",
            confirmLabel = "恢复默认",
            destructive = true,
            onConfirm = {
                viewModel.resetSettings()
                pendingReset = false
                infoMessage = "已恢复默认设置。"
            },
            onDismiss = { pendingReset = false },
        )
    }

    if (pendingClearProgress) {
        ConfirmDialog(
            title = "清空学习进度",
            message = "将永久删除全部掌握状态、错题记录、答题日志与学习统计。词库本身会保留，此操作无法撤销。",
            confirmLabel = "确认清空",
            destructive = true,
            onConfirm = {
                viewModel.clearStudyProgress()
                pendingClearProgress = false
                infoMessage = "学习进度已清空。"
            },
            onDismiss = { pendingClearProgress = false },
        )
    }

    if (pendingClearAll) {
        ConfirmDialog(
            title = "重置全部数据",
            message = "将删除所有词库（包括导入的词库）与全部学习数据，并恢复内置演示词库。此操作无法撤销。",
            confirmLabel = "确认重置",
            destructive = true,
            onConfirm = {
                viewModel.clearAllData()
                pendingClearAll = false
                infoMessage = "全部数据已重置。"
            },
            onDismiss = { pendingClearAll = false },
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun SettingsEntryPreview() {
    CetVocabTheme {
        SettingsEntry(title = "学习设置", subtitle = "每日目标、提醒、发音、主题与动画", onClick = {})
    }
}

/** Preview-only empty state usage keeps the import of EmptyState meaningful in this file. */
@Preview(showBackground = true)
@Composable
private fun ProfileEmptyPreview() {
    CetVocabTheme {
        EmptyState(title = "还没有词库", description = "内置演示词库会在首次启动时自动创建。")
    }
}
