package com.cetvocab.app.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cetvocab.app.AppContainer
import com.cetvocab.app.data.repository.ExportScope
import com.cetvocab.app.data.seed.BuiltInWordPacks
import com.cetvocab.app.ui.DataTransferViewModel
import com.cetvocab.app.ui.ViewModelFactories
import com.cetvocab.app.ui.components.AppCard
import com.cetvocab.app.ui.components.ConfirmDialog
import com.cetvocab.app.ui.components.ErrorState
import com.cetvocab.app.ui.components.FilterChipGroup
import com.cetvocab.app.ui.components.PrimaryButton
import com.cetvocab.app.ui.components.ReviewBadge
import com.cetvocab.app.ui.components.SecondaryButton
import com.cetvocab.app.ui.components.SectionHeader
import com.cetvocab.app.ui.theme.AppTheme
import com.cetvocab.app.ui.theme.CetVocabTheme
import com.cetvocab.app.ui.theme.Spacing

/**
 * Vocabulary import and data export/restore.
 *
 * Everything goes through the Storage Access Framework: the user picks the source file and the
 * destination, so the app needs no storage permission and never guesses a location. Imports are
 * two-step (validate → preview → confirm) and restores ask for confirmation too.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DataTransferScreen(
    container: AppContainer,
    onBack: () -> Unit,
) {
    val viewModel: DataTransferViewModel = viewModel(factory = ViewModelFactories.dataTransfer(container))
    val state by viewModel.state.collectAsStateWithLifecycle()

    val importPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let(viewModel::pickImportFile) }

    val restorePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let(viewModel::pickRestoreFile) }

    var exportScopeName = remember { arrayOf(ExportScope.WORD_LIST.name) }
    val exportCreator = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument(mimeType = "text/plain"),
    ) { uri ->
        uri?.let {
            val scope = ExportScope.entries.firstOrNull { entry -> entry.name == exportScopeName[0] }
                ?: ExportScope.WORD_LIST
            viewModel.exportTo(it, scope)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("词库与数据") },
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
            if (state.errorMessage != null) {
                ErrorState(
                    message = state.errorMessage!!,
                    onRetry = viewModel::dismissError,
                    retryLabel = "知道了",
                )
            }

            state.importResult?.let { result ->
                AppCard {
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                        SectionHeader(title = "导入完成")
                        Text("文件：${result.fileName}（${result.format.label}）")
                        Text("新增 ${result.inserted} · 更新 ${result.updated} · 跳过重复 ${result.skipped} · 失败 ${result.failed}")
                        Text(
                            text = "同名单词保留了原有的学习进度，只更新了词条内容。",
                            style = MaterialTheme.typography.bodySmall,
                            color = AppTheme.colors.mutedText,
                        )
                    }
                }
            }

            state.restoreResult?.let { result ->
                AppCard {
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                        SectionHeader(title = "恢复完成")
                        Text(result.summary)
                    }
                }
            }

            AppCard {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                    SectionHeader(title = "导入词库")
                    Text(
                        text = "支持 JSON 与 CSV。CSV 首行需包含 word 列（可选 meanings、phonetic、partOfSpeech、" +
                            "exampleSentence、exampleTranslation、collocations、level、source、license）。" +
                            "文件最大 8 MB / 20000 条记录。",
                        style = MaterialTheme.typography.bodySmall,
                        color = AppTheme.colors.mutedText,
                    )
                    PrimaryButton(
                        text = "选择 JSON / CSV 文件",
                        onClick = {
                            importPicker.launch(
                                arrayOf(
                                    "application/json",
                                    "text/csv",
                                    "text/comma-separated-values",
                                    "text/plain",
                                    "*/*",
                                ),
                            )
                        },
                        loading = state.importing,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("import_pick_file"),
                    )
                }
            }

            AppCard {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                    SectionHeader(title = "导出数据")
                    FilterChipGroup(
                        options = ExportScope.entries.toList(),
                        selected = ExportScope.entries.firstOrNull { it.name == exportScopeName[0] }
                            ?: ExportScope.WORD_LIST,
                        onSelect = { exportScopeName[0] = it.name },
                        label = { it.label },
                        modifier = Modifier.testTag("export_scope"),
                    )
                    Text(
                        text = (ExportScope.entries.firstOrNull { it.name == exportScopeName[0] }
                            ?: ExportScope.WORD_LIST).description,
                        style = MaterialTheme.typography.bodySmall,
                        color = AppTheme.colors.mutedText,
                    )
                    PrimaryButton(
                        text = "选择保存位置并导出",
                        onClick = {
                            val scope = ExportScope.entries.firstOrNull { it.name == exportScopeName[0] }
                                ?: ExportScope.WORD_LIST
                            exportCreator.launch(
                                container.backupRepository.suggestedFileName(scope, state.level),
                            )
                        },
                        loading = state.exporting,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("export_pick_location"),
                    )
                    Text(
                        text = "导出的 JSON 包含版本号、导出时间与数据格式说明，可直接用于恢复。",
                        style = MaterialTheme.typography.bodySmall,
                        color = AppTheme.colors.mutedText,
                    )
                }
            }

            AppCard {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                    SectionHeader(title = "从备份恢复")
                    Text(
                        text = "恢复会替换备份中词库的学习数据（掌握状态、错题、统计），词库本身不会删除。",
                        style = MaterialTheme.typography.bodySmall,
                        color = AppTheme.colors.mutedText,
                    )
                    SecondaryButton(
                        text = "选择备份文件",
                        onClick = {
                            restorePicker.launch(arrayOf("application/json", "text/plain", "*/*"))
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("restore_pick_file"),
                    )
                }
            }

            AppCard {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    SectionHeader(title = "已安装词库")
                    Text(
                        text = "内置演示词库：四级 ${BuiltInWordPacks.cet4.size} 词、六级 ${BuiltInWordPacks.cet6.size} 词，" +
                            "许可 ${BuiltInWordPacks.LICENSE}。",
                        style = MaterialTheme.typography.bodySmall,
                        color = AppTheme.colors.mutedText,
                    )
                    Text(
                        text = "导入的词库请在「我的 - 词库来源与许可」中核对来源与许可。",
                        style = MaterialTheme.typography.bodySmall,
                        color = AppTheme.colors.mutedText,
                    )
                }
            }
            Spacer(Modifier.height(Spacing.lg))
        }
    }

    state.preview?.let { preview ->
        val totalValid = preview.validCount
        ConfirmDialog(
            title = "确认导入",
            message = buildString {
                append("文件：${preview.fileName}（${preview.format.label}）\n")
                append("有效记录：$totalValid 条\n")
                append("跳过问题行：${preview.invalidCount} 行\n")
                append("文件内重复：${preview.duplicateRows} 条\n")
                preview.levelCounts.entries.firstOrNull()?.let { entry ->
                    append("词库级别：${entry.key.label} ${entry.value} 条\n")
                }
                append("许可：${preview.licenseSummary.ifBlank { "未标注（建议补充）" }}\n")
                if (preview.invalidCount > 0) {
                    append("\n前几个问题：\n")
                    preview.issues.take(3).forEach { issue ->
                        append("第 ${issue.rowNumber} 行 ${issue.field}：${issue.message}\n")
                    }
                }
                append("\n确认后将在一个事务中写入，失败会自动回滚。")
            },
            confirmLabel = "确认导入",
            onConfirm = viewModel::confirmImport,
            onDismiss = viewModel::cancelPreview,
        )
    }

    state.pendingRestore?.let {
        ConfirmDialog(
            title = "确认恢复备份",
            message = "恢复会替换备份所包含词库的学习数据，并用备份中的统计覆盖当前统计。词库本身不会被删除。",
            confirmLabel = "确认恢复",
            destructive = true,
            onConfirm = viewModel::confirmRestore,
            onDismiss = viewModel::cancelRestore,
        )
    }
}

/** Word-list sources, licences and the privacy statement. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LicensesScreen(onBack: () -> Unit) {
    InfoScaffold(title = "词库来源与许可", onBack = onBack) {
        AppCard {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                SectionHeader(title = "内置演示词库")
                Text(
                    text = "来源：${BuiltInWordPacks.SOURCE}",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = "许可：${BuiltInWordPacks.LICENSE}",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = "版本：${BuiltInWordPacks.VERSION}",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = "内置单词为项目自建的演示数据（自撰释义与例句），仅用于体验完整功能，" +
                        "并非任何官方或商业词库，也不代表考试范围。",
                    style = MaterialTheme.typography.bodySmall,
                    color = AppTheme.colors.mutedText,
                )
            }
        }

        AppCard {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                SectionHeader(title = "用户导入内容")
                Text(
                    text = "导入的词库、例句、音频等内容的版权与使用许可由用户自行负责。每个词条都会保存 " +
                        "source 与 license 字段，导入预览与单词详情中都会显示。",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = "本应用不捆绑来源不明的商业词库、音频或第三方内容，也不与任何考试机构存在合作关系。",
                    style = MaterialTheme.typography.bodySmall,
                    color = AppTheme.colors.mutedText,
                )
            }
        }

        AppCard {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                SectionHeader(title = "开源组件")
                Text(
                    text = "本应用基于 Jetpack Compose、Material 3、Room、DataStore、Navigation Compose、" +
                        "WorkManager 与 Kotlinx Coroutines / Serialization 构建，以上组件均以 Apache-2.0 许可发布。",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

/** About + privacy statement + version information. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(
    container: AppContainer,
    onBack: () -> Unit,
) {
    InfoScaffold(title = "关于", onBack = onBack) {
        AppCard {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Text("薄荷单词", style = MaterialTheme.typography.headlineSmall)
                Text(
                    text = "版本 ${container.appVersionName}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = AppTheme.colors.mutedText,
                )
                Row {
                    ReviewBadge(text = "minSdk 26（Android 8.0）")
                }
                Text(
                    text = "面向中国大陆大学生的 CET-4 / CET-6 单词记忆工具，提供间隔重复复习、错题巩固、" +
                        "闭卷自测和学习统计。",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }

        AppCard {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                SectionHeader(title = "隐私说明")
                Text(
                    text = "· 学习、复习、错题、闭卷考试、统计与设置全部离线可用，不联网也能完成。\n" +
                        "· 唯一的联网用途是发音音频：按单词向 Wikimedia Commons 与 Wiktionary 请求一个音频文件" +
                        "（请求里只有这个英文单词，没有账号、设备标识或学习记录），下载后缓存到本机。" +
                        "关闭发音或断网时，应用会明确提示并给出离线兜底方案。\n" +
                        "· 不接入账号系统、广告、埋点或第三方统计 SDK。\n" +
                        "· 学习数据、错题与设置全部保存在应用私有目录（Room 数据库与 DataStore），不会上传。\n" +
                        "· 日志中不输出用户数据、导入内容或敏感信息，源码中不含任何密钥或 Token。\n" +
                        "· 导入与导出只通过系统文件选择器访问你亲自选择的文件，不申请宽泛存储权限。\n" +
                        "· 删除学习记录、清空错题或重置数据前都会二次确认。",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }

        AppCard {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                SectionHeader(title = "间隔重复算法说明")
                Text(
                    text = "复习间隔由一套透明的 SM-2 风格启发式规则计算：不认识会重置进度并在几分钟后再次出现；" +
                        "模糊会缩短下一次间隔；已掌握按 1、3、7、14 天后按难度系数递增，最长 180 天。" +
                        "这是学习辅助工具，不宣称提分或押题，也不是经过临床验证的记忆模型。",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InfoScaffold(
    title: String,
    onBack: () -> Unit,
    content: @Composable () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
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
            content()
            Spacer(Modifier.height(Spacing.lg))
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ExportScopePreview() {
    CetVocabTheme {
        FilterChipGroup(
            options = ExportScope.entries.toList(),
            selected = ExportScope.WORD_LIST,
            onSelect = {},
            label = { it.label },
        )
    }
}
