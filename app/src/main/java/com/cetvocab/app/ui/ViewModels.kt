package com.cetvocab.app.ui

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.cetvocab.app.AppContainer
import com.cetvocab.app.core.result.Outcome
import com.cetvocab.app.core.time.StudyDay
import com.cetvocab.app.data.repository.AnswerSource
import com.cetvocab.app.data.repository.ExamDraft
import com.cetvocab.app.data.repository.ExamStatus
import com.cetvocab.app.data.repository.ExportScope
import com.cetvocab.app.data.repository.RestoreResult
import com.cetvocab.app.data.repository.SeedProgress
import com.cetvocab.app.data.repository.SessionStatus
import com.cetvocab.app.data.repository.WordRepository
import com.cetvocab.app.data.speech.SpeechRateMode
import com.cetvocab.app.domain.insight.Insight
import com.cetvocab.app.domain.insight.InsightEngine
import com.cetvocab.app.domain.motivation.MotivationContext
import com.cetvocab.app.domain.motivation.MotivationEngine
import com.cetvocab.app.domain.motivation.MotivationMessage
import com.cetvocab.app.domain.motivation.MotivationMoment
import com.cetvocab.app.domain.stats.StatsSnapshot
import com.cetvocab.app.domain.study.NextStep
import com.cetvocab.app.domain.study.NextStepPlanner
import com.cetvocab.app.data.settings.ThemeMode
import com.cetvocab.app.data.settings.UserSettings
import com.cetvocab.app.domain.exam.ExamBlueprint
import com.cetvocab.app.domain.exam.ExamQuestion
import com.cetvocab.app.domain.exam.ExamScore
import com.cetvocab.app.domain.importer.ImportPreview
import com.cetvocab.app.domain.importer.ImportResult
import com.cetvocab.app.domain.mistake.MistakeCategory
import com.cetvocab.app.domain.mistake.MistakeFilter
import com.cetvocab.app.domain.mistake.MistakeItem
import com.cetvocab.app.domain.mistake.MistakeOverview
import com.cetvocab.app.domain.mistake.MistakeSort
import com.cetvocab.app.domain.model.CetLevel
import com.cetvocab.app.domain.model.Word
import com.cetvocab.app.domain.model.WordWithProgress
import com.cetvocab.app.domain.srs.MasteryStatus
import com.cetvocab.app.domain.srs.ReviewGrade
import com.cetvocab.app.domain.srs.SpacedRepetition
import com.cetvocab.app.domain.srs.WordProgress
import com.cetvocab.app.domain.stats.DailyStudyPoint
import com.cetvocab.app.domain.study.QueueReason
import com.cetvocab.app.domain.study.RecentEvent
import com.cetvocab.app.domain.study.SessionSummary
import com.cetvocab.app.domain.study.StudyCard
import com.cetvocab.app.domain.study.StudyQueueItem
import com.cetvocab.app.domain.study.StudySessionEngine
import com.cetvocab.app.domain.study.StudySessionState
import com.cetvocab.app.domain.study.TodayPlan
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
// Every UI state class below is annotated `@Immutable`: all fields are `val` and hold only
// values or immutable collections, so Compose may safely skip a screen when an unrelated field
// changes. Without the annotation the compiler infers `List`/`Map`/`Set` fields as unstable and
// recomposes the whole screen (including long text layout) on every state tick.
import androidx.compose.runtime.Immutable
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Exhaustive list of factory functions for the app's ViewModels.
 *
 * Grouping them in one file makes the dependency of every screen on the object graph explicit
 * and keeps `AppContainer` out of the UI layer: screens only ever see a ViewModel.
 */
object ViewModelFactories {

    fun main(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
        initializer { MainViewModel(container) }
    }

    fun onboarding(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
        initializer { OnboardingViewModel(container) }
    }

    fun home(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
        initializer { HomeViewModel(container) }
    }

    fun study(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
        initializer { StudyViewModel(container) }
    }

    fun review(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
        initializer { ReviewCenterViewModel(container) }
    }

    fun exam(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
        initializer { ExamViewModel(container) }
    }

    fun stats(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
        initializer { StatsViewModel(container) }
    }

    fun profile(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
        initializer { ProfileViewModel(container) }
    }

    fun dataTransfer(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
        initializer { DataTransferViewModel(container) }
    }

    fun wordBrowser(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
        initializer { WordBrowserViewModel(container) }
    }
}

// ---------------------------------------------------------------------------------------------
// Main / theme host
// ---------------------------------------------------------------------------------------------

@Immutable
data class MainUiState(
    val settings: UserSettings = UserSettings(),
    val ready: Boolean = false,
    /** Non-null while the built-in vocabulary is being written to the database. */
    val seedProgress: SeedProgress? = null,
)

/** Holds the settings that drive the theme, and runs one-time start-up work. */
class MainViewModel(private val container: AppContainer) : ViewModel() {

    private val ready = MutableStateFlow(false)

    val state: StateFlow<MainUiState> = combine(
        container.settingsRepository.settings,
        ready,
        container.seedProgress,
    ) { settings, isReady, progress ->
        MainUiState(settings = settings, ready = isReady, seedProgress = progress)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, MainUiState())

    init {
        viewModelScope.launch {
            // Seeding the built-in packs (and upgrading them when the shipped data changed) and
            // re-syncing the reminder schedule are both idempotent.
            runCatching { container.prepare() }
            ready.value = true
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Onboarding
// ---------------------------------------------------------------------------------------------

@Immutable
data class OnboardingUiState(
    val level: CetLevel = CetLevel.CET4,
    val dailyNewWords: Int = UserSettings.DEFAULT_NEW_WORDS,
    val dailyReviewLimit: Int = UserSettings.DEFAULT_REVIEW_LIMIT,
    val reminderEnabled: Boolean = false,
    val reminderHour: Int = 20,
    val reminderMinute: Int = 0,
    val readExampleSentences: Boolean = false,
    val speechRateMode: SpeechRateMode = SpeechRateMode.SYSTEM,
    val saving: Boolean = false,
    val finished: Boolean = false,
)

/** First-run flow: level, daily goal, reminder, reading — every step skippable. */
class OnboardingViewModel(private val container: AppContainer) : ViewModel() {

    private val _state = MutableStateFlow(OnboardingUiState())
    val state: StateFlow<OnboardingUiState> = _state.asStateFlow()

    fun setLevel(level: CetLevel) = _state.update { it.copy(level = level) }

    fun setDailyNewWords(count: Int) = _state.update { it.copy(dailyNewWords = UserSettings.clampNewWords(count)) }

    fun setDailyReviewLimit(limit: Int) =
        _state.update { it.copy(dailyReviewLimit = UserSettings.clampReviewLimit(limit)) }

    fun setReminder(enabled: Boolean, hour: Int, minute: Int) =
        _state.update { it.copy(reminderEnabled = enabled, reminderHour = hour, reminderMinute = minute) }

    fun setReadExampleSentences(enabled: Boolean) =
        _state.update { it.copy(readExampleSentences = enabled) }

    fun setSpeechRateMode(mode: SpeechRateMode) = _state.update { it.copy(speechRateMode = mode) }

    /**
     * Persists the choices and marks onboarding as done.
     *
     * @param skip true for "稍后设置": defaults are kept, only the completion flag is written.
     */
    fun finish(skip: Boolean = false) {
        if (_state.value.saving) return
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            val current = _state.value
            val repository = container.settingsRepository
            if (!skip) {
                repository.setLevel(current.level)
                repository.setDailyNewWordGoal(current.dailyNewWords)
                repository.setDailyReviewLimit(current.dailyReviewLimit)
                repository.setReadExampleSentences(current.readExampleSentences)
                repository.setSpeechRateMode(current.speechRateMode)
                repository.setReminder(current.reminderEnabled, current.reminderHour, current.reminderMinute)
            }
            repository.setOnboardingCompleted(true)
            val settings = repository.current()
            // Scheduling the reminder is best effort, exactly like the start-up path in
            // `AppContainer.prepare`: the choices above are already persisted, so a device where
            // WorkManager cannot be reached must still leave onboarding instead of keeping the
            // learner on a screen stuck at "正在保存…".
            runCatching {
                container.reminderScheduler.sync(
                    enabled = settings.reminderEnabled,
                    hour = settings.reminderHour,
                    minute = settings.reminderMinute,
                )
            }
            _state.update { it.copy(saving = false, finished = true) }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Home
// ---------------------------------------------------------------------------------------------

/**
 * Dashboard state.
 *
 * The greeting and the date are computed once per emission by the ViewModel rather than in
 * property getters: a getter would call `LocalDate.now()` and build a `DateTimeFormatter`
 * during composition (on every recomposition, i.e. on every scroll frame), and the value could
 * change mid-frame. Computing them here also makes the datum explicit in tests.
 */
@Immutable
data class HomeUiState(
    val loading: Boolean = true,
    val settings: UserSettings = UserSettings(),
    val plan: TodayPlan? = null,
    val stats: StatsSnapshot? = null,
    val weekTrend: List<DailyStudyPoint> = emptyList(),
    val recentEvents: List<RecentEvent> = emptyList(),
    val resumableSessionId: Long? = null,
    val errorMessage: String? = null,
    val greeting: String = "",
    val dateLabel: String = "",
    /** The concrete next action; null only while the first emission is still loading. */
    val nextStep: NextStep? = null,
    /** At most [InsightEngine.MAX_HOME_INSIGHTS] suggestions, most severe first. */
    val insights: List<Insight> = emptyList(),
    /**
     * Today's line of encouragement, or null when it has already been shown today. Never an empty
     * string: the card hides itself on null.
     */
    val motivation: MotivationMessage? = null,
)

/** Time-of-day greeting. Pure function of the hour so it can be unit tested. */
internal fun greetingFor(hour: Int): String = when {
    hour < 6 -> "夜深了，注意休息"
    hour < 11 -> "早上好，今天也要加油"
    hour < 14 -> "中午好，来背几个词"
    hour < 18 -> "下午好，继续积累"
    else -> "晚上好，完成今天的目标吧"
}

/**
 * The dashboard's date label.
 *
 * The formatter is created once instead of per call: `DateTimeFormatter.ofPattern` is a
 * relatively expensive call and this label is read on every recomposition of the app bar.
 */
internal object HomeDateFormatter {
    private val pattern = DateTimeFormatter.ofPattern("M 月 d 日 EEEE", Locale.CHINA)

    fun format(epochMillis: Long, zone: java.time.ZoneId): String =
        pattern.format(Instant.ofEpochMilli(epochMillis).atZone(zone))
}

/**
 * The dashboard.
 *
 * It reads the plan from [TodayPlan] (computed by the repository from the schedule and the
 * user's goals) and the trend from the statistics repository, so the numbers on this screen
 * are the same numbers the statistics screen shows.
 */
class HomeViewModel(private val container: AppContainer) : ViewModel() {

    private val resumable = MutableStateFlow<Long?>(null)

    /**
     * The signals, insights and motivation are computed from the statistics snapshot rather than
     * fetched separately, so the dashboard and the statistics screen can never disagree about the
     * same number.
     *
     * They are produced inside `map` (not inside the `combine` lambda) because building the signals
     * touches the database *suspendingly*; doing that work in a `combine` transform would block the
     * flow's dispatcher with I/O.
     */
    val state: StateFlow<HomeUiState> = combine(
        container.settingsRepository.settings,
        container.settingsRepository.settings.flatMapLatest { container.studyRepository.observeTodayPlan(it) },
        container.settingsRepository.settings.flatMapLatest { settings ->
            container.statsRepository.observeStats(settings.level, settings)
        },
        container.settingsRepository.settings.flatMapLatest { settings ->
            container.studyRepository.observeRecentEvents(settings.level, 6)
        },
        resumable,
    ) { settings, plan, stats, recent, resumableId ->
        HomeSnapshot(settings, plan, stats, recent, resumableId)
    }.map { snapshot -> describeHome(snapshot) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    private data class HomeSnapshot(
        val settings: UserSettings,
        val plan: TodayPlan,
        val stats: StatsSnapshot,
        val recent: List<RecentEvent>,
        val resumableId: Long?,
    )

    private suspend fun describeHome(snapshot: HomeSnapshot): HomeUiState {
        val now = container.timeProvider.nowMillis()
        val zone = container.timeProvider.zone()
        val settings = snapshot.settings
        val stats = snapshot.stats

        val signals = runCatching {
            container.insightRepository.signals(settings.level, stats)
        }.getOrNull()
        val insights = signals?.let { InsightEngine.analyze(it) }.orEmpty()

        return HomeUiState(
            loading = false,
            settings = settings,
            plan = snapshot.plan,
            stats = stats,
            weekTrend = stats.weekly,
            recentEvents = snapshot.recent,
            resumableSessionId = snapshot.resumableId,
            errorMessage = null,
            greeting = greetingFor(Instant.ofEpochMilli(now).atZone(zone).hour),
            dateLabel = HomeDateFormatter.format(now, zone),
            nextStep = NextStepPlanner.plan(snapshot.plan),
            insights = InsightEngine.forHome(insights),
            motivation = motivationFor(settings, snapshot.plan, stats, signals, now, zone),
        )
    }

    /**
     * Today's encouragement, or null when it should stay quiet.
     *
     * Two product rules are enforced here rather than in the UI, because they must survive any
     * screen that shows them:
     *  * **at most one line per day** — once shown, the day key is stored, so a recomposition, a
     *    rotation or a return to the dashboard cannot repeat it;
     *  * **the moment is derived from the data**, not chosen at random: a learner returning after a
     *    break gets a different message from one who just beat their own record.
     */
    private suspend fun motivationFor(
        settings: UserSettings,
        plan: TodayPlan,
        stats: StatsSnapshot,
        signals: com.cetvocab.app.domain.insight.LearningSignals?,
        now: Long,
        zone: java.time.ZoneId,
    ): MotivationMessage? {
        val todayKey = StudyDay.dayKey(now, zone)
        if (settings.lastMotivationDayKey == todayKey) return null

        // Derived from the same weekly windows the advice uses, so "improving" means one thing.
        val accuracyDelta = when {
            signals?.accuracyLast7Days == null || signals.accuracyPrevious7Days == null -> null
            else -> signals.accuracyLast7Days - signals.accuracyPrevious7Days
        }
        val daysSinceLastStudy = daysSinceLastStudy(stats, now, zone)

        val moment = when {
            stats.streakDays > settings.celebratedStreak && stats.streakDays >= 2 ->
                MotivationMoment.NEW_STREAK_RECORD

            stats.stageGoal.isReached && stats.stageGoal.target > settings.celebratedMilestone ->
                MotivationMoment.MILESTONE_REACHED

            daysSinceLastStudy >= 3 -> MotivationMoment.RETURNED_AFTER_BREAK

            // Read once into a local: `accuracyTodayPercent` is a computed property, so Kotlin cannot
            // smart-cast it inside the comparison.
            (plan.accuracyTodayPercent?.takeIf { it < STRUGGLING_ACCURACY } != null) ->
                MotivationMoment.STRUGGLING

            accuracyDelta != null && accuracyDelta >= 5 -> MotivationMoment.ACCURACY_IMPROVED

            plan.isFinished -> MotivationMoment.SESSION_COMPLETED

            else -> MotivationMoment.STEADY
        }

        val message = MotivationEngine.message(
            MotivationContext(
                moment = moment,
                streakDays = stats.streakDays,
                longestStreakDays = stats.longestStreakDays,
                daysSinceLastStudy = daysSinceLastStudy,
                accuracyPercent = stats.accuracyPercent,
                accuracyDeltaPercent = accuracyDelta,
                milestoneValue = stats.stageGoal.target,
                answeredToday = plan.answeredToday,
            ),
        )

        // Persist "shown today" and the celebrated values together, so an achievement is announced
        // exactly once even if the app is killed right after this emission.
        runCatching {
            container.settingsRepository.setLastMotivationDayKey(todayKey)
            if (moment == MotivationMoment.NEW_STREAK_RECORD) {
                container.settingsRepository.setCelebratedStreak(stats.streakDays)
            }
            if (moment == MotivationMoment.MILESTONE_REACHED) {
                container.settingsRepository.setCelebratedMilestone(stats.stageGoal.target)
            }
        }
        return message
    }

    /**
     * Whole days since the last study day, or 0 when the learner studied today.
     *
     * Reads the calendar the statistics screen already shows, so the two agree by construction.
     * Returns 0 when there is no history at all — a brand-new learner has not "come back".
     */
    private fun daysSinceLastStudy(stats: StatsSnapshot, now: Long, zone: java.time.ZoneId): Int {
        val lastActive = stats.calendar.lastOrNull { it.hasActivity }?.date ?: return 0
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        return java.time.temporal.ChronoUnit.DAYS.between(lastActive, today).toInt().coerceAtLeast(0)
    }

    /** Called when the learner chooses "继续上次学习" or dismisses the prompt. */
    fun clearResumableSession() {
        resumable.value = null
    }

    fun abandonResumableSession() {
        viewModelScope.launch {
            container.studyRepository.abandonActiveSessions()
            resumable.value = null
        }
    }

    private companion object {
        /** Below this, the app offers support instead of a score. */
        const val STRUGGLING_ACCURACY = 40
    }
}

// ---------------------------------------------------------------------------------------------
// Study session
// ---------------------------------------------------------------------------------------------

@Immutable
data class StudyUiState(
    val loading: Boolean = true,
    val session: StudySessionState? = null,
    val card: StudyCard? = null,
    val revealed: Boolean = false,
    val saving: Boolean = false,
    val finished: Boolean = false,
    val summary: SessionSummary? = null,
    val emptyMessage: String? = null,
    val errorMessage: String? = null,
    val lastAnswer: ReviewGrade? = null,
    /** Reading preferences, mirrored into state so the card can speak without touching DataStore. */
    val speechRateMode: SpeechRateMode = SpeechRateMode.SYSTEM,
    val readExampleSentences: Boolean = false,
    val autoPlayPronunciation: Boolean = false,
) {
    val progressLabel: String get() = session?.progressLabel.orEmpty()
    val totalCards: Int get() = session?.totalCards ?: 0
    val answeredCards: Int get() = session?.answered ?: 0
    val reasonLabel: String? get() = session?.reason?.label
}

/**
 * The study loop.
 *
 * Responsibilities that are easy to get wrong and are handled here rather than in the UI:
 *  * resume an unfinished session after a process restart;
 *  * write every answer immediately (via the repository) and remember the in-progress session
 *    row so a crash loses at most the current card;
 *  * ignore taps that arrive while the previous answer is still being written;
 *  * keep the immediate-repeat queue bounded through [StudySessionEngine].
 */
class StudyViewModel(private val container: AppContainer) : ViewModel() {

    private val _state = MutableStateFlow(StudyUiState())
    val state: StateFlow<StudyUiState> = _state.asStateFlow()

    private var cardStartedAt: Long = 0L
    private var pendingDrillIds: List<Long> = emptyList()

    /** Starts (or resumes) a session. [drillIds] empty means today's normal queue. */
    fun start(drillIds: List<Long> = emptyList()) {
        if (_state.value.session != null && drillIds == pendingDrillIds) return
        pendingDrillIds = drillIds
        _state.update { it.copy(loading = true, errorMessage = null, emptyMessage = null) }
        viewModelScope.launch {
            runCatching {
                val settings = container.settingsRepository.current()
                val items = if (drillIds.isEmpty()) {
                    val existing = container.studyRepository.resumeActiveSession()
                    if (existing != null) {
                        loadSession(existing)
                        return@runCatching
                    }
                    container.studyRepository.buildQueue(settings)
                } else {
                    container.studyRepository.buildDrillQueue(drillIds)
                }
                if (items.isEmpty()) {
                    _state.update {
                        it.copy(
                            loading = false,
                            emptyMessage = if (drillIds.isEmpty()) {
                                "今天的计划已经完成，去错题本巩固一下，或到个人中心提高每日新词数量。"
                            } else {
                                "没有可练习的单词。"
                            },
                            // Refresh the reading settings even when there is no card to show.
                            // `loadSession` does this on the normal path; without it an empty queue
                            // would leave the ViewModel holding stale preferences, and the state
                            // would disagree with the settings the learner just changed.
                            speechRateMode = settings.speechRateMode,
                            readExampleSentences = settings.readExampleSentences,
                            autoPlayPronunciation = settings.autoPlayPronunciation,
                        )
                    }
                    return@runCatching
                }
                val now = container.timeProvider.nowMillis()
                val newIds = container.studyRepository
                    .loadCards(items.map { item -> item.wordId }, QueueReason.NEW)
                    .filterValues { card -> card.isNew }
                    .keys
                val created = container.studyRepository.createSession(
                    StudySessionEngine.create(
                        sessionId = 0L,
                        levelId = settings.level.id,
                        items = items,
                        newWordIds = newIds,
                        startedAt = now,
                    ),
                )
                loadSession(created)
            }.onFailure { throwable ->
                _state.update {
                    it.copy(loading = false, errorMessage = "无法开始学习：${throwable.javaClass.simpleName}")
                }
            }
        }
    }

    private suspend fun loadSession(session: StudySessionState) {
        val settings = container.settingsRepository.current()
        val card = loadCard(session)
        cardStartedAt = container.timeProvider.nowMillis()
        _state.update {
            it.copy(
                loading = false,
                session = session,
                card = card,
                revealed = false,
                finished = card == null,
                errorMessage = null,
                speechRateMode = settings.speechRateMode,
                readExampleSentences = settings.readExampleSentences,
                autoPlayPronunciation = settings.autoPlayPronunciation,
                emptyMessage = if (card == null) "这个队列里的单词已不存在，请重新开始。" else null,
            )
        }
    }

    private suspend fun loadCard(session: StudySessionState): StudyCard? {
        val wordId = session.currentWordId ?: return null
        val reason = session.reasons[wordId] ?: QueueReason.DUE
        return container.studyRepository.loadCards(listOf(wordId), reason)[wordId]
    }

    fun reveal() {
        if (_state.value.saving) return
        _state.update { it.copy(revealed = true) }
    }

    /**
     * Changes the reading speed from the study card.
     *
     * Persisted to the same setting the settings screen edits, so a quick switch on the card and the
     * settings entry can never disagree.
     */
    fun setSpeechRateMode(mode: SpeechRateMode) {
        if (_state.value.speechRateMode == mode) return
        _state.update { it.copy(speechRateMode = mode) }
        viewModelScope.launch {
            runCatching { container.settingsRepository.setSpeechRateMode(mode) }
        }
    }

    /**
     * Favourite toggle for the current card.
     *
     * It deliberately does **not** go through `answer()`: bookmarking a word is not a review
     * answer and must not move the schedule.
     */
    fun toggleFavorite() {
        val card = _state.value.card ?: return
        val newValue = !card.progress.isFavorite
        _state.update { it.copy(card = card.copy(progress = card.progress.copy(isFavorite = newValue))) }
        viewModelScope.launch {
            runCatching { container.mistakeRepository.setFavorite(card.word.id, newValue) }
                .onFailure {
                    // Roll back the optimistic update if the write failed.
                    _state.update { current ->
                        current.copy(
                            card = current.card?.copy(
                                progress = current.card.progress.copy(isFavorite = !newValue),
                            ),
                            errorMessage = "收藏状态保存失败",
                        )
                    }
                }
        }
    }

    /** Records an answer; repeated taps while saving are ignored. */
    fun answer(grade: ReviewGrade) {
        val snapshot = _state.value
        val session = snapshot.session ?: return
        val card = snapshot.card ?: return
        if (snapshot.saving) return

        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            runCatching {
                val now = container.timeProvider.nowMillis()
                val source = when (session.reason) {
                    QueueReason.IMMEDIATE -> AnswerSource.IMMEDIATE
                    QueueReason.MISTAKE -> AnswerSource.DRILL
                    else -> AnswerSource.STUDY
                }
                container.studyRepository.recordAnswer(
                    card = card,
                    grade = grade,
                    source = source,
                    sessionId = session.sessionId,
                    responseTimeMs = (now - cardStartedAt).coerceAtLeast(0L),
                )
                val advanced = StudySessionEngine.applyAnswer(session, grade, now)
                container.studyRepository.persist(advanced)
                val nextCard = loadCard(advanced)
                cardStartedAt = now
                if (nextCard == null) {
                    finishSession(advanced)
                } else {
                    _state.update {
                        it.copy(
                            saving = false,
                            session = advanced,
                            card = nextCard,
                            revealed = false,
                            lastAnswer = grade,
                        )
                    }
                }
            }.onFailure { throwable ->
                _state.update {
                    it.copy(saving = false, errorMessage = "保存学习结果失败：${throwable.javaClass.simpleName}")
                }
            }
        }
    }

    private suspend fun finishSession(session: StudySessionState) {
        val finishedAt = container.timeProvider.nowMillis()
        container.studyRepository.closeSession(session, SessionStatus.COMPLETED)
        val summary = container.studyRepository.summarize(session, finishedAt)
        _state.update {
            it.copy(saving = false, session = session, card = null, finished = true, summary = summary)
        }
    }

    /** Leaves the session, keeping the progress already written. */
    fun pauseAndExit() {
        val session = _state.value.session ?: return
        viewModelScope.launch {
            runCatching { container.studyRepository.persist(session) }
        }
    }

    /** Gives up on the current session explicitly. */
    fun abandon() {
        val session = _state.value.session ?: return
        viewModelScope.launch {
            container.studyRepository.closeSession(session, SessionStatus.ABANDONED)
            _state.update { it.copy(finished = true, card = null, summary = null) }
        }
    }

    fun retryFromSummary() {
        val ids = _state.value.summary?.mistakeWordIds.orEmpty()
        _state.value = StudyUiState()
        start(ids)
    }

    fun dismissError() = _state.update { it.copy(errorMessage = null) }
}

// ---------------------------------------------------------------------------------------------
// Review centre
// ---------------------------------------------------------------------------------------------

@Immutable
data class ReviewUiState(
    val filter: MistakeFilter = MistakeFilter(),
    val sort: MistakeSort = MistakeSort.RECENT,
    val overview: MistakeOverview = MistakeOverview.Empty,
    val items: List<MistakeItem> = emptyList(),
    val loading: Boolean = true,
    val selectedIds: Set<Long> = emptySet(),
    val message: String? = null,
)

/**
 * Review centre state.
 *
 * Selecting a category, typing a search term or toggling a filter re-subscribes the same
 * reactive query, so the list is always a database result rather than a filtered copy that
 * could drift out of date.
 */
class ReviewCenterViewModel(private val container: AppContainer) : ViewModel() {

    private val _state = MutableStateFlow(ReviewUiState())
    val state: StateFlow<ReviewUiState> = _state.asStateFlow()

    private var itemsJob: Job? = null
    private var overviewJob: Job? = null

    init {
        viewModelScope.launch {
            val settings = container.settingsRepository.current()
            selectCategory(MistakeCategory.RECENT, settings.level)
        }
    }

    /** Points every query at the given level and refreshes both lists. */
    fun selectCategory(category: MistakeCategory, level: CetLevel) {
        _state.update { it.copy(filter = it.filter.copy(category = category, level = level), loading = true) }
        observeList()
        observeOverview(level)
    }

    fun setLevel(level: CetLevel) {
        _state.update { it.copy(filter = it.filter.copy(level = level), loading = true) }
        observeList()
        observeOverview(level)
    }

    fun setSearch(query: String) {
        _state.update { it.copy(filter = it.filter.copy(search = query), loading = true) }
        observeList()
    }

    fun setMinWrongCount(minCount: Int) {
        _state.update { it.copy(filter = it.filter.copy(minWrongCount = minCount), loading = true) }
        observeList()
    }

    fun toggleFavoritesOnly() {
        _state.update { it.copy(filter = it.filter.copy(favoritesOnly = !it.filter.favoritesOnly), loading = true) }
        observeList()
    }

    fun setSort(sort: MistakeSort) {
        _state.update { it.copy(sort = sort, loading = true) }
        observeList()
    }

    fun toggleSelection(wordId: Long) {
        _state.update { current ->
            val selection = current.selectedIds.toMutableSet()
            if (!selection.add(wordId)) selection.remove(wordId)
            current.copy(selectedIds = selection)
        }
    }

    fun clearSelection() = _state.update { it.copy(selectedIds = emptySet()) }

    fun toggleFavorite(wordId: Long, favorite: Boolean) {
        viewModelScope.launch {
            container.mistakeRepository.setFavorite(wordId, favorite)
            _state.update { it.copy(message = if (favorite) "已收藏" else "已取消收藏") }
        }
    }

    fun markResolved(wordIds: List<Long>) {
        if (wordIds.isEmpty()) return
        viewModelScope.launch {
            container.mistakeRepository.markResolved(wordIds)
            _state.update { it.copy(selectedIds = emptySet(), message = "已标记为已巩固") }
        }
    }

    /** Deletes records; the screen asks for confirmation before calling this. */
    fun deleteRecords(wordIds: List<Long>) {
        if (wordIds.isEmpty()) return
        viewModelScope.launch {
            container.mistakeRepository.deleteRecords(wordIds)
            _state.update { it.copy(selectedIds = emptySet(), message = "已删除所选错题记录") }
        }
    }

    fun consumeMessage() = _state.update { it.copy(message = null) }

    /** Ids for "批量重新练习" / "重新练习" of the current selection. */
    suspend fun drillIds(): List<Long> {
        val current = _state.value
        val selected = current.selectedIds
        return if (selected.isNotEmpty()) {
            current.items.map { it.word.id }.filter { it in selected }
        } else {
            container.mistakeRepository.drillIds(current.filter, current.sort)
        }
    }

    private fun observeList() {
        itemsJob?.cancel()
        val current = _state.value
        itemsJob = viewModelScope.launch {
            container.mistakeRepository.observeItems(current.filter, current.sort)
                .collect { items -> _state.update { it.copy(items = items, loading = false) } }
        }
    }

    private fun observeOverview(level: CetLevel) {
        overviewJob?.cancel()
        overviewJob = viewModelScope.launch {
            container.mistakeRepository.observeOverview(level)
                .collect { overview -> _state.update { it.copy(overview = overview) } }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Exam
// ---------------------------------------------------------------------------------------------

enum class ExamPhase { SETUP, RUNNING, RESULT }

@Immutable
data class ExamUiState(
    val phase: ExamPhase = ExamPhase.SETUP,
    val loading: Boolean = false,
    val level: CetLevel = CetLevel.CET4,
    val questionCount: Int = 20,
    val durationMinutes: Int = 15,
    val availableWords: Int = 0,
    val draftAvailable: Boolean = false,
    val examId: Long? = null,
    val questions: List<ExamQuestion> = emptyList(),
    val answers: Map<Long, String> = emptyMap(),
    val currentIndex: Int = 0,
    val remainingSeconds: Int = 0,
    val score: ExamScore? = null,
    val errorMessage: String? = null,
    val showExitDialog: Boolean = false,
) {
    val currentQuestion: ExamQuestion? get() = questions.getOrNull(currentIndex)
    val answeredCount: Int get() = answers.count { it.value.isNotBlank() }
    val progressLabel: String get() = "${(currentIndex + 1).coerceAtMost(questions.size.coerceAtLeast(1))} / ${questions.size}"
    val timeLabel: String
        get() {
            val minutes = remainingSeconds / 60
            val seconds = remainingSeconds % 60
            return "%02d:%02d".format(minutes, seconds)
        }
}

/**
 * Closed-book exam.
 *
 * The correct answers are never part of the UI state: [ExamUiState.questions] carries prompts
 * and options, and the answers map only ever holds what the learner typed or picked. Grading
 * happens in the repository when the paper is submitted.
 */
class ExamViewModel(private val container: AppContainer) : ViewModel() {

    private val _state = MutableStateFlow(ExamUiState())
    val state: StateFlow<ExamUiState> = _state.asStateFlow()

    private var timerJob: Job? = null

    init {
        refreshSetup()
    }

    fun refreshSetup() {
        viewModelScope.launch {
            val settings = container.settingsRepository.current()
            val wordCount = container.wordRepository.examSources(settings.level).size
            val draft = container.examRepository.draft()
            _state.update {
                it.copy(
                    level = settings.level,
                    questionCount = settings.examQuestionCount,
                    durationMinutes = settings.examDurationMinutes,
                    availableWords = wordCount,
                    draftAvailable = draft != null,
                )
            }
        }
    }

    fun setQuestionCount(count: Int) = _state.update { it.copy(questionCount = UserSettings.clampExamQuestions(count)) }

    fun setDurationMinutes(minutes: Int) =
        _state.update { it.copy(durationMinutes = UserSettings.clampExamDuration(minutes)) }

    fun startExam() {
        if (_state.value.loading) return
        _state.update { it.copy(loading = true, errorMessage = null) }
        viewModelScope.launch {
            val current = _state.value
            // Persist the chosen shape so the next exam opens with the same settings.
            container.settingsRepository.setExamPreferences(current.questionCount, current.durationMinutes)
            val words = container.wordRepository.examSources(current.level)
            val blueprint = ExamBlueprint(
                levelId = current.level.id,
                questionCount = current.questionCount,
                durationMinutes = current.durationMinutes,
                // A fresh seed per exam, stored on the row so the paper stays reproducible.
                seed = System.currentTimeMillis(),
            )
            when (val result = container.examRepository.createDraft(blueprint, words)) {
                is Outcome.Success -> openDraft(result.value)
                is Outcome.Failure -> _state.update {
                    it.copy(loading = false, errorMessage = result.error.message)
                }
            }
        }
    }

    /** Resumes a stored draft with the same questions and the remaining time. */
    fun resumeDraft() {
        if (_state.value.loading) return
        _state.update { it.copy(loading = true) }
        viewModelScope.launch {
            val draft = container.examRepository.draft()
            if (draft == null) {
                _state.update { it.copy(loading = false, draftAvailable = false, errorMessage = "没有可恢复的考试草稿") }
            } else {
                openDraft(draft)
            }
        }
    }

    private fun openDraft(draft: ExamDraft) {
        val answers = draft.questions.mapNotNull { question ->
            question.givenAnswer?.let { question.id to it }
        }.toMap()
        _state.update {
            it.copy(
                loading = false,
                phase = ExamPhase.RUNNING,
                examId = draft.session.id,
                questions = draft.questions,
                answers = answers,
                currentIndex = 0,
                remainingSeconds = draft.session.remainingSeconds.coerceAtLeast(0),
                errorMessage = null,
            )
        }
        startTimer()
    }

    fun selectOption(index: Int) {
        val question = _state.value.currentQuestion ?: return
        answer(questionId = question.id, value = index.toString())
    }

    fun setSpellingInput(value: String) {
        val question = _state.value.currentQuestion ?: return
        answer(questionId = question.id, value = value)
    }

    private fun answer(questionId: Long, value: String) {
        val examId = _state.value.examId ?: return
        _state.update { it.copy(answers = it.answers + (questionId to value)) }
        viewModelScope.launch {
            runCatching { container.examRepository.saveAnswer(examId, questionId, value) }
        }
    }

    fun goTo(index: Int) {
        val size = _state.value.questions.size
        if (size == 0) return
        _state.update { it.copy(currentIndex = index.coerceIn(0, size - 1)) }
    }

    fun next() = goTo(_state.value.currentIndex + 1)

    fun previous() = goTo(_state.value.currentIndex - 1)

    fun requestExit() = _state.update { it.copy(showExitDialog = true) }

    fun dismissExitDialog() = _state.update { it.copy(showExitDialog = false) }

    /** Saves the draft and leaves: the exam can be resumed later. */
    fun saveDraftAndExit() {
        val examId = _state.value.examId
        timerJob?.cancel()
        viewModelScope.launch {
            if (examId != null) {
                container.examRepository.saveRemainingSeconds(examId, _state.value.remainingSeconds)
            }
            _state.update { it.copy(showExitDialog = false, phase = ExamPhase.SETUP, draftAvailable = examId != null) }
            refreshSetup()
        }
    }

    /** Abandons the draft: the paper cannot be resumed. */
    fun discardAndExit() {
        val examId = _state.value.examId
        timerJob?.cancel()
        viewModelScope.launch {
            if (examId != null) container.examRepository.abandon(examId)
            _state.value = ExamUiState()
            refreshSetup()
        }
    }

    fun submit() {
        val examId = _state.value.examId ?: return
        if (_state.value.loading) return
        timerJob?.cancel()
        _state.update { it.copy(loading = true) }
        viewModelScope.launch {
            when (val result = container.examRepository.submit(examId, _state.value.answers)) {
                is Outcome.Success -> _state.update {
                    it.copy(loading = false, phase = ExamPhase.RESULT, score = result.value)
                }

                is Outcome.Failure -> _state.update {
                    it.copy(loading = false, errorMessage = result.error.message, phase = ExamPhase.SETUP)
                }
            }
        }
    }

    /** Re-runs a practice drill over the words that were answered wrong. */
    fun wrongWordIdsForRetry(): List<Long> = _state.value.score?.results
        ?.filter { !it.isCorrect && it.givenAnswer != null }
        ?.map { it.wordId }
        ?.distinct()
        .orEmpty()

    /**
     * Loads a stored result (used when the result screen is opened directly, for example after
     * the process was restarted while an exam result was on screen).
     */
    fun loadResult(examId: Long) {
        if (_state.value.examId == examId && _state.value.score != null) return
        viewModelScope.launch {
            val score = container.examRepository.scoreFor(examId)
            val questions = container.examRepository.questions(examId)
            _state.update {
                it.copy(
                    examId = examId,
                    phase = ExamPhase.RESULT,
                    questions = questions,
                    score = score,
                    loading = false,
                    errorMessage = if (score == null) "找不到这场考试的记录。" else null,
                )
            }
        }
    }

    fun backToSetup() {
        _state.value = ExamUiState()
        refreshSetup()
    }

    fun dismissError() = _state.update { it.copy(errorMessage = null) }

    private fun startTimer() {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            while (_state.value.phase == ExamPhase.RUNNING && _state.value.remainingSeconds > 0) {
                delay(1_000)
                _state.update { it.copy(remainingSeconds = (it.remainingSeconds - 1).coerceAtLeast(0)) }
                val seconds = _state.value.remainingSeconds
                if (seconds % 5 == 0) {
                    _state.value.examId?.let { examId ->
                        runCatching { container.examRepository.saveRemainingSeconds(examId, seconds) }
                    }
                }
            }
            // Time is up: hand the paper in automatically, exactly once.
            if (_state.value.phase == ExamPhase.RUNNING && _state.value.remainingSeconds == 0) {
                submit()
            }
        }
    }

    override fun onCleared() {
        timerJob?.cancel()
        super.onCleared()
    }
}

// ---------------------------------------------------------------------------------------------
// Statistics
// ---------------------------------------------------------------------------------------------

@Immutable
data class StatsUiState(
    val loading: Boolean = true,
    val level: CetLevel = CetLevel.CET4,
    val snapshot: StatsSnapshot? = null,
    val recentEvents: List<RecentEvent> = emptyList(),
)

/** Statistics are derived in the repository; this class only forwards the level choice. */
class StatsViewModel(private val container: AppContainer) : ViewModel() {

    val state: StateFlow<StatsUiState> = container.settingsRepository.settings
        .flatMapLatest { settings ->
            combine(
                container.statsRepository.observeStats(settings.level, settings),
                container.studyRepository.observeRecentEvents(settings.level, 10),
            ) { snapshot, recent ->
                StatsUiState(
                    loading = false,
                    level = settings.level,
                    snapshot = snapshot,
                    recentEvents = recent,
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StatsUiState())
}

// ---------------------------------------------------------------------------------------------
// Word browser (vocabulary search)
// ---------------------------------------------------------------------------------------------

@Immutable
data class WordBrowserUiState(
    val loading: Boolean = true,
    val level: CetLevel = CetLevel.CET4,
    val query: String = "",
    val totalWords: Int = 0,
    val results: List<WordWithProgress> = emptyList(),
    val truncated: Boolean = false,
) {
    val resultSummary: String
        get() = when {
            query.isBlank() -> "共 $totalWords 个单词"
            truncated -> "结果较多，仅显示前 ${results.size} 个"
            else -> "找到 ${results.size} 个结果"
        }
}

/**
 * Vocabulary lookup over the whole built-in list.
 *
 * The query is debounced before it reaches SQLite: the complete CET-4/CET-6 tables hold thousands
 * of rows, and running a fresh `LIKE` scan on every keystroke is exactly the kind of work that
 * makes typing feel sluggish. Debouncing also keeps the result list stable while the learner types.
 */
@OptIn(kotlinx.coroutines.FlowPreview::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class WordBrowserViewModel(private val container: AppContainer) : ViewModel() {

    private val query = MutableStateFlow("")

    val state: StateFlow<WordBrowserUiState> = combine(
        container.settingsRepository.settings,
        query,
    ) { settings, text -> settings.level to text }
        .debounce(QUERY_DEBOUNCE_MILLIS)
        .flatMapLatest { (level, text) ->
            combine(
                container.wordRepository.observeWordCount(level),
                flow { emit(container.wordRepository.search(level, text, WordRepository.SEARCH_LIMIT)) },
            ) { total, results ->
                WordBrowserUiState(
                    loading = false,
                    level = level,
                    query = text,
                    totalWords = total,
                    results = results,
                    truncated = results.size >= WordRepository.SEARCH_LIMIT,
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WordBrowserUiState())

    fun setQuery(text: String) {
        query.value = text
    }

    private companion object {
        /**
         * Long enough to skip intermediate keystrokes, short enough that the list still feels
         * immediate (Material's recommended search debounce window).
         */
        const val QUERY_DEBOUNCE_MILLIS = 220L
    }
}

// ---------------------------------------------------------------------------------------------
// Profile / settings
// ---------------------------------------------------------------------------------------------

@Immutable
data class ProfileUiState(
    val settings: UserSettings = UserSettings(),
    val wordCount: Int = 0,
    val packs: List<com.cetvocab.app.data.local.entity.WordPackEntity> = emptyList(),
    val importHistory: List<com.cetvocab.app.data.local.entity.ImportHistoryEntity> = emptyList(),
    val stats: StatsSnapshot? = null,
    val message: String? = null,
    val appVersion: String = "",
    /** Current state of the system text-to-speech engine, shown in the reading section. */
    val speechStatus: com.cetvocab.app.data.speech.SpeechStatus =
        com.cetvocab.app.data.speech.SpeechStatus.Initialising,
    /** Local (never uploaded) reading success/failure counters. */
    val speechDiagnostics: com.cetvocab.app.data.speech.SpeechDiagnostics =
        com.cetvocab.app.data.speech.SpeechDiagnostics(),
)

/** Settings, data management and the "about" information. */
class ProfileViewModel(private val container: AppContainer) : ViewModel() {

    val state: StateFlow<ProfileUiState> = combine(
        container.settingsRepository.settings,
        container.settingsRepository.settings.flatMapLatest { container.wordRepository.observeWordCount(it.level) },
        container.wordRepository.observePacks(),
        container.wordRepository.observeImportHistory(),
    ) { settings, count, packs, history ->
        ProfileUiState(settings = settings, wordCount = count, packs = packs, importHistory = history)
    }.combine(
        container.settingsRepository.settings.flatMapLatest { settings ->
            container.statsRepository.observeStats(settings.level, settings)
        },
    ) { base, stats ->
        base.copy(stats = stats, appVersion = container.appVersionName)
    }.combine(container.speechService.status) { base, speechStatus ->
        base.copy(speechStatus = speechStatus)
    }.combine(container.speechDiagnostics.state) { base, diagnostics ->
        base.copy(speechDiagnostics = diagnostics)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProfileUiState())

    /** Clears the local speech counters (they are diagnostics, not study data). */
    fun resetSpeechDiagnostics() = container.speechDiagnostics.reset()

    fun setLevel(level: CetLevel) = launchSettings { setLevel(level) }

    fun setDailyNewWordGoal(value: Int) = launchSettings { setDailyNewWordGoal(value) }

    fun setDailyReviewLimit(value: Int) = launchSettings { setDailyReviewLimit(value) }

    fun setReminder(enabled: Boolean, hour: Int, minute: Int) = launchSettings {
        setReminder(enabled, hour, minute)
        val settings = current()
        container.reminderScheduler.sync(settings.reminderEnabled, settings.reminderHour, settings.reminderMinute)
    }

    fun setReadExampleSentences(enabled: Boolean) = launchSettings { setReadExampleSentences(enabled) }

    fun setSpeechRateMode(mode: SpeechRateMode) = launchSettings { setSpeechRateMode(mode) }

    fun setThemeMode(mode: ThemeMode) = launchSettings { setThemeMode(mode) }

    fun setDynamicColor(enabled: Boolean) = launchSettings { setDynamicColor(enabled) }

    fun setAnimationsEnabled(enabled: Boolean) = launchSettings { setAnimationsEnabled(enabled) }

    fun setAutoPlayPronunciation(enabled: Boolean) = launchSettings { setAutoPlayPronunciation(enabled) }

    /** Restores default settings only; study data is untouched. */
    fun resetSettings() {
        viewModelScope.launch {
            container.settingsRepository.resetToDefaults()
            container.reminderScheduler.cancel()
        }
    }

    /** Clears every learning result. The screen confirms before calling this. */
    fun clearStudyProgress(onDone: () -> Unit = {}) {
        viewModelScope.launch {
            container.backupRepository.clearStudyProgress()
            onDone()
        }
    }

    /** Deletes vocabulary and study data. The screen confirms before calling this. */
    fun clearAllData(onDone: () -> Unit = {}) {
        viewModelScope.launch {
            container.backupRepository.clearAllData()
            container.wordRepository.ensureBuiltInPacks()
            onDone()
        }
    }

    private fun launchSettings(block: suspend com.cetvocab.app.data.settings.SettingsRepository.() -> Unit) {
        viewModelScope.launch { container.settingsRepository.block() }
    }
}

// ---------------------------------------------------------------------------------------------
// Import / export
// ---------------------------------------------------------------------------------------------

@Immutable
data class DataTransferUiState(
    val importing: Boolean = false,
    val exporting: Boolean = false,
    val preview: ImportPreview? = null,
    val importResult: ImportResult? = null,
    val restoreResult: RestoreResult? = null,
    val errorMessage: String? = null,
    val level: CetLevel = CetLevel.CET4,
    val pendingRestore: String? = null,
)

/**
 * Word-list import and data export.
 *
 * The flow is deliberately two-step for imports: pick a file → validate → show a preview with
 * counts → only then write, inside a transaction. A rejected file therefore cannot touch
 * existing data at all.
 */
class DataTransferViewModel(private val container: AppContainer) : ViewModel() {

    private val _state = MutableStateFlow(DataTransferUiState())
    val state: StateFlow<DataTransferUiState> = _state.asStateFlow()

    private var pendingFileName: String = ""

    init {
        viewModelScope.launch {
            _state.update { it.copy(level = container.settingsRepository.current().level) }
        }
    }

    /** Step 1: read and validate the picked file. Nothing is written yet. */
    fun pickImportFile(uri: Uri) {
        _state.update { it.copy(importing = true, errorMessage = null, preview = null, importResult = null) }
        viewModelScope.launch {
            val name = container.documentIo.displayName(uri)
            pendingFileName = name
            when (val read = container.documentIo.readText(uri)) {
                is Outcome.Failure -> _state.update {
                    it.copy(importing = false, errorMessage = read.error.message)
                }

                is Outcome.Success -> {
                    val settings = container.settingsRepository.current()
                    when (
                        val parsed = com.cetvocab.app.domain.importer.WordPackParser.parse(
                            fileName = name,
                            content = read.value,
                            defaultLevel = settings.level,
                            defaultSource = "用户导入：$name",
                            defaultLicense = "由用户提供，请自行确认使用许可",
                        )
                    ) {
                        is Outcome.Success -> _state.update {
                            it.copy(importing = false, preview = parsed.value)
                        }

                        is Outcome.Failure -> _state.update {
                            it.copy(importing = false, errorMessage = parsed.error.message)
                        }
                    }
                }
            }
        }
    }

    /** Step 2: after the user confirms the preview, write everything in one transaction. */
    fun confirmImport() {
        val preview = _state.value.preview ?: return
        _state.update { it.copy(importing = true, errorMessage = null) }
        viewModelScope.launch {
            val packId = "imported-" + preview.fileName
                .substringBeforeLast('.')
                .lowercase()
                .replace(Regex("[^a-z0-9]+"), "-")
                .trim('-')
                .take(40)
                .ifBlank { "imported-pack" }
            when (val result = container.wordRepository.importPack(preview, packId)) {
                is Outcome.Success -> _state.update {
                    it.copy(importing = false, preview = null, importResult = result.value)
                }

                is Outcome.Failure -> _state.update {
                    it.copy(importing = false, errorMessage = result.error.message)
                }
            }
        }
    }

    fun cancelPreview() = _state.update { it.copy(preview = null) }

    /** Deletes an imported pack (built-in packs are refused by the repository). */
    fun deletePack(packId: String) {
        viewModelScope.launch {
            val settings = container.settingsRepository.current()
            when (val result = container.wordRepository.deleteImportedPack(packId, settings.level)) {
                is Outcome.Success -> _state.update { it.copy(importResult = null) }
                is Outcome.Failure -> _state.update { it.copy(errorMessage = result.error.message) }
            }
        }
    }

    /** Export writes to the URI the user picked in the system save dialog. */
    fun exportTo(uri: Uri, scope: ExportScope) {
        _state.update { it.copy(exporting = true, errorMessage = null) }
        viewModelScope.launch {
            val level = container.settingsRepository.current().level
            when (val payload = container.backupRepository.export(scope, level)) {
                is Outcome.Success -> {
                    when (val write = container.documentIo.writeText(uri, payload.value)) {
                        is Outcome.Success -> _state.update { it.copy(exporting = false) }
                        is Outcome.Failure -> _state.update {
                            it.copy(exporting = false, errorMessage = write.error.message)
                        }
                    }
                }

                is Outcome.Failure -> _state.update {
                    it.copy(exporting = false, errorMessage = payload.error.message)
                }
            }
        }
    }

    /** Step 1 of a restore: validate the file and ask for confirmation. */
    fun pickRestoreFile(uri: Uri) {
        _state.update { it.copy(importing = true, errorMessage = null, restoreResult = null) }
        viewModelScope.launch {
            when (val read = container.documentIo.readText(uri, maxBytes = RESTORE_LIMIT_BYTES)) {
                is Outcome.Success -> _state.update { it.copy(importing = false, pendingRestore = read.value) }
                is Outcome.Failure -> _state.update {
                    it.copy(importing = false, errorMessage = read.error.message)
                }
            }
        }
    }

    /** Step 2: applies a validated backup. */
    fun confirmRestore() {
        val content = _state.value.pendingRestore ?: return
        _state.update { it.copy(importing = true) }
        viewModelScope.launch {
            when (val result = container.backupRepository.restore(content)) {
                is Outcome.Success -> _state.update {
                    it.copy(importing = false, pendingRestore = null, restoreResult = result.value)
                }

                is Outcome.Failure -> _state.update {
                    it.copy(importing = false, pendingRestore = null, errorMessage = result.error.message)
                }
            }
        }
    }

    fun cancelRestore() = _state.update { it.copy(pendingRestore = null) }

    fun dismissError() = _state.update { it.copy(errorMessage = null, importResult = null, restoreResult = null) }

    companion object {
        /** Backups are small text files; 32 MiB is a generous, still bounded limit. */
        const val RESTORE_LIMIT_BYTES = 32L * 1024 * 1024
    }
}
