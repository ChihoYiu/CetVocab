package com.cetvocab.app.domain.study

import com.cetvocab.app.domain.srs.ReviewGrade
import com.cetvocab.app.domain.srs.SrsConstants

/** Why a word is in today's queue. Shown to the learner so the plan is never a black box. */
enum class QueueReason(val label: String) {
    /** 到期复习 — the schedule said it is time. */
    DUE("到期复习"),

    /** 未巩固 — failed or fuzzy before its due date. */
    FRAGILE("未巩固"),

    /** 错题巩固 — pulled in by the mistake drill. */
    MISTAKE("错题巩固"),

    /** 新词 — never studied. */
    NEW("今日新词"),

    /** 即时重复 — answered 不认识 minutes ago. */
    IMMEDIATE("即时重复"),
}

/** One card of a study session. */
data class StudyQueueItem(
    val wordId: Long,
    val reason: QueueReason,
)

/**
 * Builds today's queue.
 *
 * Ordering rules (all of them are visible in the UI through [QueueReason]):
 *  1. words that are due, hardest first (they carry mistakes) then by due date;
 *  2. "unconsolidated" words that are not due yet but keep coming back;
 *  3. new words, last, and only up to the daily goal.
 *
 * Due and fragile words are never dropped in favour of new words — a learner who is behind
 * on reviews should not be pushed further ahead.
 */
object StudyQueueBuilder {

    fun build(
        dueWordIds: List<Long>,
        fragileWordIds: List<Long>,
        newWordIds: List<Long>,
        dailyNewWordGoal: Int,
        dailyReviewLimit: Int,
    ): List<StudyQueueItem> {
        val reviewQuota = dailyReviewLimit.coerceAtLeast(0)
        val due = dueWordIds.distinct().take(reviewQuota).map { StudyQueueItem(it, QueueReason.DUE) }
        val remainingReview = (reviewQuota - due.size).coerceAtLeast(0)
        val fragile = fragileWordIds
            .distinct()
            .filter { id -> due.none { it.wordId == id } }
            .take(remainingReview)
            .map { StudyQueueItem(it, QueueReason.FRAGILE) }
        val news = newWordIds
            .distinct()
            .filter { id -> due.none { it.wordId == id } && fragile.none { it.wordId == id } }
            .take(dailyNewWordGoal.coerceAtLeast(0))
            .map { StudyQueueItem(it, QueueReason.NEW) }
        return due + fragile + news
    }

    /** Queue for "重新练习" from the mistake book / review centre. */
    fun drillQueue(wordIds: List<Long>): List<StudyQueueItem> =
        wordIds.distinct().map { StudyQueueItem(it, QueueReason.MISTAKE) }
}

/**
 * Session bookkeeping.
 *
 * The state is a plain value object so that it can be persisted as a row, restored after a
 * process death and unit tested without Android. The queue is stored as a list of word ids
 * plus a cursor; immediate repeats are *inserted* into the same list so that "第 12 / 50 个"
 * always matches what the learner actually sees.
 */
data class StudySessionState(
    val sessionId: Long,
    val levelId: String,
    val queue: List<Long>,
    val reasons: Map<Long, QueueReason>,
    val newWordIds: Set<Long>,
    val position: Int,
    val repeatCounts: Map<Long, Int>,
    val answered: Int = 0,
    val masteredCount: Int = 0,
    val fuzzyCount: Int = 0,
    val unknownCount: Int = 0,
    val newSeen: Int = 0,
    val reviewSeen: Int = 0,
    val startedAt: Long,
    val updatedAt: Long,
) {
    val currentWordId: Long? get() = queue.getOrNull(position)

    val isFinished: Boolean get() = position >= queue.size

    val totalCards: Int get() = queue.size

    /** "12 / 50" label for the progress indicator. */
    val progressLabel: String get() = "${(position + 1).coerceAtMost(totalCards)} / $totalCards"

    val reason: QueueReason
        get() = currentWordId?.let { reasons[it] } ?: QueueReason.DUE

    /** 0f..1f completion, safe when the queue is empty. */
    val progressFraction: Float
        get() = if (totalCards == 0) 0f else (position.toFloat() / totalCards).coerceIn(0f, 1f)
}

/**
 * Advances a session.
 *
 * Rules for immediate repeats ("即时巩固队列"):
 *  * a word answered 不认识 comes back after [SrsConstants.IMMEDIATE_REPEAT_GAP] other cards;
 *  * it may come back at most [SrsConstants.MAX_IMMEDIATE_REPEATS] times per session, so a
 *    session can never loop forever on one word;
 *  * it is never inserted twice into the pending part of the queue.
 */
object StudySessionEngine {

    fun create(
        sessionId: Long,
        levelId: String,
        items: List<StudyQueueItem>,
        newWordIds: Set<Long>,
        startedAt: Long,
    ): StudySessionState = StudySessionState(
        sessionId = sessionId,
        levelId = levelId,
        queue = items.map { it.wordId },
        reasons = items.associate { it.wordId to it.reason },
        newWordIds = newWordIds,
        position = 0,
        repeatCounts = emptyMap(),
        startedAt = startedAt,
        updatedAt = startedAt,
    )

    /**
     * Records one answer and returns the next state.
     *
     * @param grade the learner's self-assessment
     * @param nowMillis answer time
     */
    fun applyAnswer(state: StudySessionState, grade: ReviewGrade, nowMillis: Long): StudySessionState {
        val wordId = state.currentWordId ?: return state
        val isNew = wordId in state.newWordIds

        var queue = state.queue
        var repeatCounts = state.repeatCounts

        if (shouldRepeatImmediately(grade, state.repeatCounts[wordId] ?: 0)) {
            val insertAt = (state.position + 1 + SrsConstants.IMMEDIATE_REPEAT_GAP)
                .coerceAtMost(queue.size)
            // Only schedule a repeat if this word is not already pending later in the queue,
            // which is what stops a session from looping on the same card forever.
            val pendingAfterCursor = queue.subList(state.position + 1, queue.size)
            if (pendingAfterCursor.none { it == wordId }) {
                queue = queue.toMutableList().apply { add(insertAt, wordId) }
                repeatCounts = repeatCounts + (wordId to (repeatCounts[wordId] ?: 0) + 1)
            }
        }

        return state.copy(
            queue = queue,
            repeatCounts = repeatCounts,
            position = state.position + 1,
            answered = state.answered + 1,
            masteredCount = state.masteredCount + if (grade == ReviewGrade.MASTERED) 1 else 0,
            fuzzyCount = state.fuzzyCount + if (grade == ReviewGrade.FUZZY) 1 else 0,
            unknownCount = state.unknownCount + if (grade == ReviewGrade.UNKNOWN) 1 else 0,
            // These two counters count *cards* answered, including immediate repeats; the
            // session summary labels them accordingly.
            newSeen = state.newSeen + if (isNew) 1 else 0,
            reviewSeen = state.reviewSeen + if (isNew) 0 else 1,
            updatedAt = nowMillis,
        )
    }

    /** Skips the current card without recording an answer (used when a word was deleted). */
    fun skip(state: StudySessionState, nowMillis: Long): StudySessionState =
        state.copy(position = state.position + 1, updatedAt = nowMillis)

    /**
     * 不认识 always repeats; 模糊 repeats only if it has already been repeated once, so that
     * "fuzzy" words get a second chance without filling the session with the same card.
     */
    fun shouldRepeatImmediately(grade: ReviewGrade, repeatsSoFar: Int): Boolean = when {
        repeatsSoFar >= SrsConstants.MAX_IMMEDIATE_REPEATS -> false
        grade == ReviewGrade.UNKNOWN -> true
        grade == ReviewGrade.FUZZY && repeatsSoFar >= 1 -> false
        grade == ReviewGrade.FUZZY -> true
        else -> false
    }
}
