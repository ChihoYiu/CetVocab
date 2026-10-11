package com.cetvocab.app.data.repository

import com.cetvocab.app.core.json.JsonCodec
import com.cetvocab.app.core.time.StudyDay
import com.cetvocab.app.core.time.TimeProvider
import com.cetvocab.app.data.local.CetVocabDatabase
import com.cetvocab.app.data.local.entity.StudySessionEntity
import com.cetvocab.app.data.mapper.toWordWithProgress
import com.cetvocab.app.data.mapper.toWord
import com.cetvocab.app.data.mapper.toProgress
import com.cetvocab.app.data.mapper.toReviewEventDisplay
import com.cetvocab.app.data.settings.UserSettings
import com.cetvocab.app.domain.model.CetLevel
import com.cetvocab.app.domain.srs.MasteryStatus
import com.cetvocab.app.domain.srs.ReviewGrade
import com.cetvocab.app.domain.srs.SpacedRepetition
import com.cetvocab.app.domain.study.QueueReason
import com.cetvocab.app.domain.study.RecentEvent
import com.cetvocab.app.domain.study.SessionSummary
import com.cetvocab.app.domain.study.StudyCard
import com.cetvocab.app.domain.study.StudyQueueBuilder
import com.cetvocab.app.domain.study.StudyQueueItem
import com.cetvocab.app.domain.study.StudySessionEngine
import com.cetvocab.app.domain.study.StudySessionState
import com.cetvocab.app.domain.study.TodayPlan
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

/** Session status values stored in `study_session.status`. */
object SessionStatus {
    const val ACTIVE = "ACTIVE"
    const val COMPLETED = "COMPLETED"
    const val ABANDONED = "ABANDONED"
}

/**
 * The study loop: what to study today, where the learner left off, and what an answer does.
 *
 * All writes go through [ProgressRecorder], so a session result is persisted the moment the
 * learner taps a button — closing the app mid-session can never lose an answer.
 */
interface StudyRepository {

    /** Reactive home dashboard data. */
    fun observeTodayPlan(settings: UserSettings): Flow<TodayPlan>

    /** Builds today's queue from due reviews, unconsolidated words and new words. */
    suspend fun buildQueue(settings: UserSettings): List<StudyQueueItem>

    /** Queue for a targeted drill (mistake book / review centre "重新练习"). */
    fun buildDrillQueue(wordIds: List<Long>, shuffle: Boolean = false): List<StudyQueueItem>

    /** The unfinished session, if any, rebuilt from the database. */
    suspend fun resumeActiveSession(): StudySessionState?

    suspend fun createSession(state: StudySessionState): StudySessionState

    /** Persists the whole session state (queue, cursor, counters). */
    suspend fun persist(state: StudySessionState)

    suspend fun closeSession(state: StudySessionState, status: String)

    /** Loads the cards of a queue in one round trip. */
    suspend fun loadCards(wordIds: List<Long>, reason: QueueReason): Map<Long, StudyCard>

    /** Records one answer and returns the updated progress. */
    suspend fun recordAnswer(
        card: StudyCard,
        grade: ReviewGrade,
        source: AnswerSource,
        sessionId: Long?,
        responseTimeMs: Long?,
    )

    suspend fun recentEvents(level: CetLevel, limit: Int = 8): List<RecentEvent>

    fun observeRecentEvents(level: CetLevel, limit: Int): Flow<List<RecentEvent>>

    /** Summary shown when a session finishes. */
    suspend fun summarize(
        state: StudySessionState,
        finishedAtMillis: Long,
    ): SessionSummary

    /** Abandons every active session (used by "清空学习进度" and by the resume dialog). */
    suspend fun abandonActiveSessions()
}

class DefaultStudyRepository(
    private val db: CetVocabDatabase,
    private val time: TimeProvider,
    private val recorder: ProgressRecorder,
) : StudyRepository {

    override fun observeTodayPlan(settings: UserSettings): Flow<TodayPlan> {
        val level = settings.level
        val now = time.nowMillis()
        val dayKey = StudyDay.dayKey(now, time.zone())
        return combine(
            db.progressDao().observeDueCount(level.id, now),
            db.progressDao().observeFragileCount(level.id),
            db.progressDao().observeNewCount(level.id),
            db.mistakeBookDao().observeActiveCount(level.id),
            db.dailySummaryDao().observe(dayKey, level.id),
        ) { due, fragile, newAvailable, mistakes, summary ->
            TodayPlan(
                level = level,
                dueCount = due,
                fragileCount = fragile,
                newAvailableCount = newAvailable,
                activeMistakeCount = mistakes,
                studiedToday = summary?.studiedWords ?: 0,
                answeredToday = summary?.reviewEvents ?: 0,
                correctToday = summary?.masteredCount ?: 0,
                dailyNewGoal = settings.dailyNewWordGoal,
                dailyReviewLimit = settings.dailyReviewLimit,
            )
        }
    }

    override suspend fun buildQueue(settings: UserSettings): List<StudyQueueItem> {
        val level = settings.level
        val now = time.nowMillis()
        val due = db.progressDao().dueWords(level.id, now, settings.dailyReviewLimit).map { it.wordId }
        val fragile = db.progressDao().fragileWords(level.id, now, settings.dailyReviewLimit).map { it.wordId }
        val newWords = db.progressDao().newWords(level.id, settings.dailyNewWordGoal).map { it.wordId }
        return StudyQueueBuilder.build(
            dueWordIds = due,
            fragileWordIds = fragile,
            newWordIds = newWords,
            dailyNewWordGoal = settings.dailyNewWordGoal,
            dailyReviewLimit = settings.dailyReviewLimit,
            // "乱序" preference: shuffle the whole queue once, when the session starts.
            shuffle = settings.shuffleWords,
        )
    }

    override fun buildDrillQueue(wordIds: List<Long>, shuffle: Boolean): List<StudyQueueItem> =
        StudyQueueBuilder.drillQueue(wordIds, shuffle = shuffle)

    override suspend fun resumeActiveSession(): StudySessionState? {
        val entity = db.studySessionDao().active() ?: return null
        val wordIds = JsonCodec.decodeLongs(entity.queue)
        if (wordIds.isEmpty()) {
            closeSessionEntity(entity.id, SessionStatus.ABANDONED)
            return null
        }
        val cards = loadCards(wordIds, QueueReason.DUE)
        val reasons = wordIds.associateWith { id ->
            val card = cards[id]
            when {
                card == null || card.isNew -> QueueReason.NEW
                card.progress.status == MasteryStatus.RELEARNING -> QueueReason.FRAGILE
                else -> QueueReason.DUE
            }
        }
        val newIds = cards.filterValues { it.isNew }.keys
        return StudySessionState(
            sessionId = entity.id,
            levelId = entity.level,
            queue = wordIds,
            reasons = reasons,
            newWordIds = newIds,
            position = entity.queuePosition.coerceIn(0, wordIds.size),
            repeatCounts = JsonCodec.decodeIntCounts(entity.immediateRepeatCounts),
            answered = entity.answeredCount,
            masteredCount = entity.masteredCount,
            fuzzyCount = entity.fuzzyCount,
            unknownCount = entity.unknownCount,
            newSeen = entity.newWordsSeen,
            reviewSeen = entity.reviewWordsSeen,
            startedAt = entity.startedAt,
            updatedAt = entity.updatedAt,
        )
    }

    override suspend fun createSession(state: StudySessionState): StudySessionState {
        val id = db.studySessionDao().insert(state.toEntity(SessionStatus.ACTIVE, time.nowMillis()))
        return state.copy(sessionId = id)
    }

    override suspend fun persist(state: StudySessionState) {
        if (state.sessionId <= 0) return
        db.studySessionDao().update(state.toEntity(SessionStatus.ACTIVE, time.nowMillis(), existingId = state.sessionId))
    }

    override suspend fun closeSession(state: StudySessionState, status: String) {
        if (state.sessionId <= 0) return
        val now = time.nowMillis()
        db.studySessionDao().update(
            state.toEntity(status, now, existingId = state.sessionId, endedAt = now),
        )
        if (status == SessionStatus.COMPLETED) {
            recorder.addStudyDuration(
                startedAtMillis = state.startedAt,
                durationMillis = (now - state.startedAt).coerceAtLeast(0L),
                levelId = state.levelId,
            )
        }
    }

    override suspend fun loadCards(
        wordIds: List<Long>,
        reason: QueueReason,
    ): Map<Long, StudyCard> {
        if (wordIds.isEmpty()) return emptyMap()
        return db.progressDao().wordsByIds(wordIds).associate { row ->
            val progress = row.toProgress()
            row.wordId to StudyCard(
                word = row.toWord(),
                progress = progress,
                reason = reason,
                isNew = row.status == null || progress.status == MasteryStatus.NEW,
            )
        }
    }

    override suspend fun recordAnswer(
        card: StudyCard,
        grade: ReviewGrade,
        source: AnswerSource,
        sessionId: Long?,
        responseTimeMs: Long?,
    ) {
        recorder.record(
            wordId = card.word.id,
            grade = grade,
            source = source,
            sessionId = sessionId,
            responseTimeMs = responseTimeMs,
        )
    }

    override suspend fun recentEvents(level: CetLevel, limit: Int): List<RecentEvent> =
        db.reviewEventDao().recent(level.id, limit).map { it.toReviewEventDisplay() }

    override fun observeRecentEvents(level: CetLevel, limit: Int): Flow<List<RecentEvent>> =
        db.reviewEventDao().observeRecent(level.id, limit)
            .map { rows -> rows.map { it.toReviewEventDisplay() } }

    override suspend fun summarize(state: StudySessionState, finishedAtMillis: Long): SessionSummary {
        val answeredWordIds = state.queue.take(state.position.coerceAtMost(state.queue.size))
        val mistakeIds = db.progressDao()
            .byWordIds(answeredWordIds.distinct())
            .filter { it.lastAnswer == ReviewGrade.UNKNOWN.name }
            .map { it.wordId }
        return SessionSummary(
            sessionId = state.sessionId,
            level = CetLevel.fromId(state.levelId),
            answeredCards = state.answered,
            newCards = state.newSeen,
            reviewCards = state.reviewSeen,
            masteredCards = state.masteredCount,
            fuzzyCards = state.fuzzyCount,
            unknownCards = state.unknownCount,
            distinctWords = answeredWordIds.distinct().size,
            durationMillis = (finishedAtMillis - state.startedAt).coerceAtLeast(0L),
            mistakeWordIds = mistakeIds,
        )
    }

    override suspend fun abandonActiveSessions() {
        db.studySessionDao().abandonActive(time.nowMillis())
    }

    private suspend fun closeSessionEntity(id: Long, status: String) {
        db.studySessionDao().close(id, status, time.nowMillis())
    }

    private fun StudySessionState.toEntity(
        status: String,
        now: Long,
        existingId: Long = 0L,
        endedAt: Long? = null,
    ): StudySessionEntity = StudySessionEntity(
        id = existingId,
        level = levelId,
        status = status,
        startedAt = startedAt,
        updatedAt = now,
        endedAt = endedAt,
        plannedNewCount = newWordIds.size,
        plannedReviewCount = (queue.size - newWordIds.size).coerceAtLeast(0),
        queue = JsonCodec.encodeLongs(queue),
        queuePosition = position,
        immediateQueue = JsonCodec.encodeLongs(repeatCounts.filterValues { it > 0 }.keys.toList()),
        immediateRepeatCounts = JsonCodec.encodeIntCounts(repeatCounts),
        answeredCount = answered,
        masteredCount = masteredCount,
        fuzzyCount = fuzzyCount,
        unknownCount = unknownCount,
        newWordsSeen = newSeen,
        reviewWordsSeen = reviewSeen,
    )

    /** New words introduced in this session, used to label the progress "新词" chip. */
    fun isNewWord(state: StudySessionState, wordId: Long): Boolean = wordId in state.newWordIds

    /** Remaining whole days until the next review, for the card footer. */
    fun nextReviewLabel(dueAt: Long, now: Long): String {
        val diff = dueAt - now
        return when {
            diff <= 0 -> "现在复习"
            diff < SpacedRepetition.MILLIS_PER_DAY -> "今天复习"
            else -> "${diff / SpacedRepetition.MILLIS_PER_DAY} 天后复习"
        }
    }
}

/** Kept for symmetry with the session engine used by the ViewModel. */
internal fun StudySessionEngine.answerLabel(grade: ReviewGrade): String = when (grade) {
    ReviewGrade.UNKNOWN -> "不认识"
    ReviewGrade.FUZZY -> "模糊"
    ReviewGrade.MASTERED -> "已掌握"
}
