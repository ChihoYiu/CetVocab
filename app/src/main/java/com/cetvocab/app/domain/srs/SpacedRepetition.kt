package com.cetvocab.app.domain.srs

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * The spaced repetition rule set of this app.
 *
 * ## What this is (and what it is not)
 * This is a small, transparent, SM-2 *inspired* heuristic. It is **not** a clinically
 * validated memory model and the app makes no claim that it guarantees better exam scores.
 * Every rule below is a plain function of the previous state, the learner's answer and the
 * current time, so the behaviour is fully explainable and unit tested:
 *
 *  * 不认识 (UNKNOWN) — reset the streak, drop one mastery level, make the word easier to
 *    meet again later (`easeFactor - 0.20`), and schedule it a few minutes out so it appears
 *    again inside the current session (the "即时巩固队列").
 *  * 模糊 (FUZZY) — keep the progress but shrink the next interval to ~60% of the previous
 *    one (never below one day once the word has graduated), with a small ease penalty.
 *  * 已掌握 (MASTERED) — grow the interval through fixed early steps (1, 3, 7, 14 days) and
 *    then geometrically by `interval × easeFactor`, capped at [MAX_INTERVAL_DAYS].
 *  * Repeated failures are handled by [SrsConstants.MAX_IMMEDIATE_REPEATS] and by
 *    [MasteryStatus.RELEARNING], repeated successes by the long interval cap and by
 *    [MASTERED_LEVEL] which lets a word leave the high-frequency queue.
 *
 * ## Time handling
 * All arithmetic is done on epoch milliseconds. Because the schedule is derived from
 * `nowMillis` on every answer, the app survives restarts, time-zone changes and DST
 * switches. Clock *anomalies* are contained here: a due date further away than
 * [MAX_INTERVAL_DAYS] is treated as due (see `StudyDay.isDue`), and a "now" that is older
 * than the last review simply produces a new schedule instead of a negative interval.
 */
object SpacedRepetition {

    /** Starting ease factor, the classic SM-2 value. */
    const val DEFAULT_EASE: Double = 2.5

    /** Ease never drops below this, otherwise intervals can never recover. */
    const val MIN_EASE: Double = 1.3

    /** Ease never rises above this, otherwise intervals explode. */
    const val MAX_EASE: Double = 2.8

    /** Longest interval the app will ever schedule (about six months). */
    const val MAX_INTERVAL_DAYS: Int = 180

    /** Mastery level at which a word is considered 已掌握. */
    const val MASTERED_LEVEL: Int = 5

    /**
     * Applies one answer to one word.
     *
     * @param progress previous state ([WordProgress] with `wordId` only for a brand new word)
     * @param grade the learner's self-assessment
     * @param nowMillis current time
     * @return the new state; never mutates the input
     */
    fun apply(progress: WordProgress, grade: ReviewGrade, nowMillis: Long): WordProgress {
        // Guard against a clock that moved backwards since the last review.
        val effectiveNow = max(nowMillis, progress.lastReviewedAt ?: Long.MIN_VALUE)

        return when (grade) {
            ReviewGrade.UNKNOWN -> applyUnknown(progress, effectiveNow)
            ReviewGrade.FUZZY -> applyFuzzy(progress, effectiveNow)
            ReviewGrade.MASTERED -> applyMastered(progress, effectiveNow)
        }
    }

    private fun applyUnknown(p: WordProgress, now: Long): WordProgress {
        val lapsed = p.status == MasteryStatus.REVIEW || p.status == MasteryStatus.MASTERED
        return p.copy(
            status = if (lapsed || p.status == MasteryStatus.RELEARNING) {
                MasteryStatus.RELEARNING
            } else {
                MasteryStatus.LEARNING
            },
            masteryLevel = max(0, p.masteryLevel - 1),
            repetitions = 0,
            intervalDays = 0,
            easeFactor = max(MIN_EASE, p.easeFactor - EASE_PENALTY_HARD),
            dueAt = now + SrsConstants.IMMEDIATE_REPEAT_DELAY_MILLIS,
            lastReviewedAt = now,
            lastAnswer = ReviewGrade.UNKNOWN,
            lapseCount = p.lapseCount + if (lapsed) 1 else 0,
            wrongCount = p.wrongCount + 1,
            consecutiveCorrectCount = 0,
        )
    }

    private fun applyFuzzy(p: WordProgress, now: Long): WordProgress {
        // "模糊" keeps the word in play but pushes the next meeting forward by at most a day,
        // and shortens the previously earned interval.
        val shortened = when {
            p.intervalDays <= 1 -> 1
            else -> max(1, (p.intervalDays * FUZZY_INTERVAL_FACTOR).roundToInt())
        }
        return p.copy(
            status = if (shortened <= 1) MasteryStatus.LEARNING else MasteryStatus.RELEARNING,
            masteryLevel = max(0, p.masteryLevel),
            repetitions = max(1, p.repetitions),
            intervalDays = shortened,
            easeFactor = max(MIN_EASE, p.easeFactor - EASE_PENALTY_FUZZY),
            dueAt = now + daysToMillis(shortened.toLong()),
            lastReviewedAt = now,
            lastAnswer = ReviewGrade.FUZZY,
            fuzzyCount = p.fuzzyCount + 1,
            consecutiveCorrectCount = 0,
        )
    }

    private fun applyMastered(p: WordProgress, now: Long): WordProgress {
        val repetitions = p.repetitions + 1
        val consecutive = p.consecutiveCorrectCount + 1
        val ease = min(MAX_EASE, p.easeFactor + easeBonus(p.easeFactor, repetitions))
        val interval = nextIntervalDays(repetitions, p.intervalDays, ease)
        val masteryLevel = min(MASTERED_LEVEL, p.masteryLevel + 1)
        return p.copy(
            status = if (masteryLevel >= MASTERED_LEVEL && interval >= MASTERED_MIN_INTERVAL_DAYS) {
                MasteryStatus.MASTERED
            } else {
                MasteryStatus.REVIEW
            },
            masteryLevel = masteryLevel,
            repetitions = repetitions,
            intervalDays = interval,
            easeFactor = ease,
            dueAt = now + daysToMillis(interval.toLong()),
            lastReviewedAt = now,
            lastAnswer = ReviewGrade.MASTERED,
            consecutiveCorrectCount = consecutive,
        )
    }

    /** Slightly smaller ease growth for already easy words, to keep intervals sane. */
    private fun easeBonus(currentEase: Double, repetitions: Int): Double = when {
        repetitions <= 1 -> 0.15
        currentEase >= 2.2 -> 0.05
        else -> 0.10
    }

    /** Fixed early steps, then geometric growth: 1, 3, 7, 14, then interval × ease. */
    fun nextIntervalDays(repetitions: Int, previousIntervalDays: Int, easeFactor: Double): Int {
        val raw = when {
            // Zero or one successful repetition means "the first step": one day.
            repetitions <= 1 -> 1
            repetitions == 2 -> 3
            repetitions == 3 -> 7
            repetitions == 4 -> 14
            else -> ((if (previousIntervalDays <= 0) 14 else previousIntervalDays) * easeFactor).roundToInt()
        }
        return raw.coerceIn(1, MAX_INTERVAL_DAYS)
    }

    /** Days at which a word may be considered fully mastered. */
    const val MASTERED_MIN_INTERVAL_DAYS: Int = 21

    private const val EASE_PENALTY_HARD = 0.20
    private const val EASE_PENALTY_FUZZY = 0.05
    private const val FUZZY_INTERVAL_FACTOR = 0.6

    fun daysToMillis(days: Long): Long = days * MILLIS_PER_DAY

    const val MILLIS_PER_DAY: Long = 24L * 60L * 60L * 1000L
}

/** Tunables that the study engine (not the per-word math) needs. */
object SrsConstants {
    /** An 不认识 word comes back after this delay inside the same session. */
    const val IMMEDIATE_REPEAT_DELAY_MILLIS: Long = 10L * 60L * 1000L

    /** How often a single word may re-enter the immediate queue within one session. */
    const val MAX_IMMEDIATE_REPEATS: Int = 3

    /** Minimum number of other cards shown before an immediate-repeat card may reappear. */
    const val IMMEDIATE_REPEAT_GAP: Int = 4
}
