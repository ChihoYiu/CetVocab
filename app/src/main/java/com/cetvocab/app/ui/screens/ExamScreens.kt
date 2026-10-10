package com.cetvocab.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cetvocab.app.AppContainer
import com.cetvocab.app.domain.exam.ExamQuestion
import com.cetvocab.app.domain.exam.ExamQuestionType
import com.cetvocab.app.domain.exam.ExamScore
import com.cetvocab.app.domain.exam.ExamScoring
import com.cetvocab.app.domain.exam.TypeAccuracy
import com.cetvocab.app.ui.ExamPhase
import com.cetvocab.app.ui.ExamViewModel
import com.cetvocab.app.ui.components.AnswerResultRow
import com.cetvocab.app.ui.components.AppCard
import com.cetvocab.app.ui.components.ConfirmDialog
import com.cetvocab.app.ui.components.EmptyState
import com.cetvocab.app.ui.components.ErrorState
import com.cetvocab.app.ui.components.ExamQuestionCard
import com.cetvocab.app.ui.components.FilterChipGroup
import com.cetvocab.app.ui.components.LoadingState
import com.cetvocab.app.ui.components.PrimaryButton
import com.cetvocab.app.ui.components.ReviewBadge
import com.cetvocab.app.ui.components.SecondaryButton
import com.cetvocab.app.ui.components.SectionHeader
import com.cetvocab.app.ui.components.StatCard
import com.cetvocab.app.ui.components.StudyProgressIndicator
import com.cetvocab.app.ui.theme.AppTheme
import com.cetvocab.app.ui.theme.CetVocabTheme
import com.cetvocab.app.ui.theme.Spacing

/**
 * Exam set-up.
 *
 * The paper is created and stored *before* the exam screen opens, so the questions are frozen
 * the moment the learner presses "开始考试".
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExamSetupScreen(
    viewModel: ExamViewModel,
    onOpenExam: (Long) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(state.phase, state.examId) {
        val examId = state.examId
        if (state.phase == ExamPhase.RUNNING && examId != null) {
            onOpenExam(examId)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("闭卷考试") },
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
            verticalArrangement = Arrangement.spacedBy(Spacing.lg),
        ) {
            AppCard {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    Text("考试说明", style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = "考试过程中不会显示中文释义、例句、搭配或答案提示。题目在开始时会生成快照，" +
                            "中途退出可以保存草稿并继续。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = AppTheme.colors.mutedText,
                    )
                }
            }

            if (state.errorMessage != null) {
                ErrorState(
                    message = state.errorMessage!!,
                    onRetry = viewModel::dismissError,
                    retryLabel = "知道了",
                )
            }

            if (state.draftAvailable) {
                AppCard {
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                        Text("发现未提交的考试草稿", style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = "可以继续上次的试卷与剩余时间，或直接开始一场新的考试（新考试会放弃旧草稿）。",
                            style = MaterialTheme.typography.bodyMedium,
                            color = AppTheme.colors.mutedText,
                        )
                        PrimaryButton(
                            text = "继续上次考试",
                            onClick = viewModel::resumeDraft,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("exam_resume_draft"),
                        )
                    }
                }
            }

            AppCard {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                    SectionHeader(title = "考试设置")
                    StepperRow(
                        label = "题目数量",
                        value = "${state.questionCount} 题",
                        onDecrease = { viewModel.setQuestionCount(state.questionCount - 5) },
                        onIncrease = { viewModel.setQuestionCount(state.questionCount + 5) },
                        testTagPrefix = "exam_question_count",
                    )
                    StepperRow(
                        label = "考试时长",
                        value = "${state.durationMinutes} 分钟",
                        onDecrease = { viewModel.setDurationMinutes(state.durationMinutes - 5) },
                        onIncrease = { viewModel.setDurationMinutes(state.durationMinutes + 5) },
                        testTagPrefix = "exam_duration",
                    )
                    KeyValueRow("当前词库", "${state.level.label} · ${state.availableWords} 个单词")
                    Text(
                        text = "题型包含英译中、中译英、四选一与拼写填空，由程序随机组合。",
                        style = MaterialTheme.typography.bodySmall,
                        color = AppTheme.colors.mutedText,
                    )
                }
            }

            PrimaryButton(
                text = if (state.loading) "正在生成试卷…" else "开始考试",
                onClick = viewModel::startExam,
                enabled = state.availableWords > 0,
                loading = state.loading,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("exam_start"),
            )
            if (state.availableWords == 0) {
                Text(
                    text = "当前词库为空，无法生成试卷。请先在「我的 - 词库导入」中导入词库。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            Spacer(Modifier.height(Spacing.lg))
        }
    }
}

/**
 * The exam itself: a focus mode with no bottom bar, no meanings and no feedback.
 *
 * Leaving is guarded by a dialog offering "保存草稿" or "放弃考试", and the timer submits the
 * paper automatically when it reaches zero.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExamRunScreen(
    viewModel: ExamViewModel,
    onExit: () -> Unit,
    onSubmitted: (Long) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    BackHandler(onBack = viewModel::requestExit)

    LaunchedEffect(state.phase) {
        val examId = state.examId
        if (state.phase == ExamPhase.RESULT && examId != null) onSubmitted(examId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("第 ${state.progressLabel} 题")
                        Text(
                            text = "剩余时间 ${state.timeLabel} · 已作答 ${state.answeredCount}",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (state.remainingSeconds <= 60) {
                                MaterialTheme.colorScheme.error
                            } else {
                                AppTheme.colors.mutedText
                            },
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = viewModel::requestExit,
                        modifier = Modifier.semantics { contentDescription = "退出考试" },
                    ) { Icon(Icons.Filled.ArrowBack, contentDescription = null) }
                },
                actions = {
                    TextButton(
                        onClick = viewModel::submit,
                        enabled = !state.loading,
                        modifier = Modifier.testTag("exam_submit"),
                    ) { Text("交卷") }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        val question = state.currentQuestion
        when {
            state.loading -> LoadingState(
                text = "正在提交试卷…",
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            )

            question == null -> EmptyState(
                title = "试卷已失效",
                description = "这份试卷没有可用的题目，请重新开始一场考试。",
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                actionLabel = "返回",
                onAction = onExit,
            )

            else -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = Spacing.lg),
                verticalArrangement = Arrangement.spacedBy(Spacing.lg),
            ) {
                StudyProgressIndicator(
                    current = state.currentIndex + 1,
                    total = state.questions.size,
                    reasonLabel = question.type.label,
                )

                ExamQuestionCard(
                    question = question,
                    selectedOptionIndex = state.answers[question.id]?.toIntOrNull(),
                    spellingInput = state.answers[question.id].orEmpty(),
                    onSelectOption = viewModel::selectOption,
                    onSpellingChange = viewModel::setSpellingInput,
                    onSubmitSpelling = viewModel::next,
                    modifier = Modifier.testTag("exam_question_card"),
                )

                LazyRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    itemsIndexed(state.questions) { index, item ->
                        val answered = !state.answers[item.id].isNullOrBlank()
                        SecondaryButton(
                            text = "${index + 1}",
                            onClick = { viewModel.goTo(index) },
                            modifier = Modifier.semantics {
                                contentDescription = "第 ${index + 1} 题${if (answered) "，已作答" else "，未作答"}"
                            },
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    SecondaryButton(
                        text = "上一题",
                        onClick = viewModel::previous,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("exam_previous"),
                        leadingIcon = Icons.Filled.ArrowBack,
                    )
                    PrimaryButton(
                        text = "下一题",
                        onClick = viewModel::next,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("exam_next"),
                        trailingIcon = Icons.Filled.ArrowForward,
                    )
                }
                Spacer(Modifier.height(Spacing.lg))
            }
        }
    }

    if (state.showExitDialog) {
        ConfirmDialog(
            title = "退出考试",
            message = "可以保存草稿，稍后继续这份试卷；也可以放弃本次考试（题目与作答将被清除）。",
            confirmLabel = "保存草稿",
            dismissLabel = "放弃考试",
            onConfirm = {
                viewModel.saveDraftAndExit()
                onExit()
            },
            onDismiss = {
                viewModel.discardAndExit()
                onExit()
            },
        )
    }
}

/** Result screen: score, per-type accuracy, wrong words and the next actions. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExamResultScreen(
    viewModel: ExamViewModel,
    examId: Long,
    onRetryWrongWords: (List<Long>) -> Unit,
    onGoHome: () -> Unit,
    onBackToSetup: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(examId) { viewModel.loadResult(examId) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("考试结果") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        val score = state.score
        if (score == null) {
            LoadingState(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            )
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .testTag("exam_result"),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = Spacing.lg,
                end = Spacing.lg,
                bottom = Spacing.xl,
            ),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            item {
                AppCard {
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        Text("总分", style = MaterialTheme.typography.labelMedium, color = AppTheme.colors.mutedText)
                        Text(
                            text = "${score.score}",
                            style = MaterialTheme.typography.displaySmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            text = "正确率 ${score.accuracyPercent}% · 答对 ${score.correctCount} / ${score.totalQuestions}",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            text = "用时 ${(score.durationMillis / 60_000L)} 分 ${(score.durationMillis / 1000L) % 60} 秒",
                            style = MaterialTheme.typography.bodyMedium,
                            color = AppTheme.colors.mutedText,
                        )
                        if (score.unansweredCount > 0) {
                            Text(
                                text = "未作答 ${score.unansweredCount} 题（计为错误，但不影响你的复习计划）",
                                style = MaterialTheme.typography.bodySmall,
                                color = AppTheme.colors.mutedText,
                            )
                        }
                    }
                }
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                    StatCard(
                        label = "答对",
                        value = "${score.correctCount}",
                        accent = AppTheme.colors.successGreen,
                        modifier = Modifier.weight(1f),
                    )
                    StatCard(
                        label = "答错",
                        value = "${score.wrongCount}",
                        accent = MaterialTheme.colorScheme.error,
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            item { SectionHeader(title = "各题型正确率") }
            items(score.perType) { accuracy: TypeAccuracy ->
                AppCard {
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(accuracy.type.label, style = MaterialTheme.typography.titleSmall)
                            ReviewBadge(
                                text = "${accuracy.percent}%",
                                container = if (accuracy.percent >= ExamScore.WEAK_TYPE_THRESHOLD_PERCENT) {
                                    AppTheme.colors.successContainer
                                } else {
                                    AppTheme.colors.warningContainer
                                },
                            )
                        }
                        Text(
                            text = "答对 ${accuracy.correct} / ${accuracy.total}",
                            style = MaterialTheme.typography.bodySmall,
                            color = AppTheme.colors.mutedText,
                        )
                    }
                }
            }

            if (score.weakTypes.isNotEmpty()) {
                item {
                    AppCard {
                        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                            SectionHeader(title = "薄弱词汇分类")
                            Text(
                                text = "建议优先练习：" + score.weakTypes.joinToString("、") { it.label },
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Text(
                                text = "答错的单词已经加入错题本，会按复习计划再次出现。",
                                style = MaterialTheme.typography.bodySmall,
                                color = AppTheme.colors.mutedText,
                            )
                        }
                    }
                }
            }

            item { SectionHeader(title = "错题（${score.wrongWordIds.size}）") }
            if (score.results.isEmpty()) {
                item {
                    Text(
                        text = "本次考试的错题已写入错题本，可在「错题本」中查看详情。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = AppTheme.colors.mutedText,
                    )
                }
            } else {
                items(score.results.filter { !it.isCorrect }) { result ->
                    AnswerResultRow(
                        prompt = result.prompt,
                        correctAnswer = result.correctAnswer,
                        givenAnswer = result.givenAnswer,
                        isCorrect = result.isCorrect,
                    )
                }
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    val retryIds = score.results
                        .filter { !it.isCorrect && it.givenAnswer != null }
                        .map { it.wordId }
                        .distinct()
                    if (retryIds.isNotEmpty()) {
                        PrimaryButton(
                            text = "再次练习错词（${retryIds.size}）",
                            onClick = { onRetryWrongWords(retryIds) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("exam_retry_wrong"),
                        )
                    }
                    SecondaryButton(
                        text = "再考一次",
                        onClick = onBackToSetup,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    SecondaryButton(
                        text = "返回首页",
                        onClick = onGoHome,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("exam_go_home"),
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ExamResultPreviewCard() {
    CetVocabTheme {
        AppCard {
            Column {
                Text("总分", style = MaterialTheme.typography.labelMedium)
                Text("82", style = MaterialTheme.typography.displaySmall)
                Text("正确率 82% · 答对 16 / 20", style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
