package com.cetvocab.app.domain.srs

import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit tests for the spaced repetition rule set.
 *
 * Every assertion is derived from fixed epoch constants; the tests never read the system
 * clock, so they cannot become flaky over time.
 */
class SpacedRepetitionTest {

    // 2026-01-15T04:00:00Z — an arbitrary but fixed reference instant.
    private val now = 1_768_450_800_000L
    private val oneDay = SpacedRepetition.MILLIS_PER_DAY

    private fun newWord(wordId: Long = 1L) = WordProgress(wordId = wordId)

    private fun graduatedWord(
        wordId: Long = 1L,
        masteryLevel: Int = 2,
        repetitions: Int = 3,
        intervalDays: Int = 7,
        ease: Double = SpacedRepetition.DEFAULT_EASE,
    ) = WordProgress(
        wordId = wordId,
        status = MasteryStatus.REVIEW,
        masteryLevel = masteryLevel,
        repetitions = repetitions,
        intervalDays = intervalDays,
        easeFactor = ease,
        dueAt = now,
        lastReviewedAt = now - oneDay,
        lastAnswer = ReviewGrade.MASTERED,
        consecutiveCorrectCount = repetitions,
    )

    // --- 不认识 --------------------------------------------------------------------------

    @Test
    fun `unknown lowers mastery and resets the repetition streak`() {
        val before = graduatedWord(masteryLevel = 3, repetitions = 4, intervalDays = 14)
        val after = SpacedRepetition.apply(before, ReviewGrade.UNKNOWN, now)

        assertThat(after.masteryLevel).isEqualTo(2)
        assertThat(after.repetitions).isEqualTo(0)
        assertThat(after.intervalDays).isEqualTo(0)
        assertThat(after.status).isEqualTo(MasteryStatus.RELEARNING)
        assertThat(after.lastAnswer).isEqualTo(ReviewGrade.UNKNOWN)
        assertThat(after.consecutiveCorrectCount).isEqualTo(0)
    }

    @Test
    fun `unknown increments wrong count and lapse count for a graduated word`() {
        val before = graduatedWord()
        val after = SpacedRepetition.apply(before, ReviewGrade.UNKNOWN, now)

        assertThat(after.wrongCount).isEqualTo(before.wrongCount + 1)
        assertThat(after.lapseCount).isEqualTo(before.lapseCount + 1)
    }

    @Test
    fun `unknown of a word that never graduated does not count as a lapse`() {
        val after = SpacedRepetition.apply(newWord(), ReviewGrade.UNKNOWN, now)

        assertThat(after.wrongCount).isEqualTo(1)
        assertThat(after.lapseCount).isEqualTo(0)
        assertThat(after.status).isEqualTo(MasteryStatus.LEARNING)
    }

    @Test
    fun `unknown never drops mastery below zero`() {
        val before = newWord().copy(masteryLevel = 0, status = MasteryStatus.LEARNING)
        val after = SpacedRepetition.apply(before, ReviewGrade.UNKNOWN, now)

        assertThat(after.masteryLevel).isEqualTo(0)
    }

    @Test
    fun `unknown schedules a short same-session delay`() {
        val after = SpacedRepetition.apply(newWord(), ReviewGrade.UNKNOWN, now)

        assertThat(after.dueAt).isEqualTo(now + SrsConstants.IMMEDIATE_REPEAT_DELAY_MILLIS)
        assertThat(after.dueAt - now).isLessThan(oneDay)
        assertThat(after.lastReviewedAt).isEqualTo(now)
    }

    @Test
    fun `unknown clamps the ease factor at the minimum`() {
        val alreadyAtFloor = graduatedWord(ease = SpacedRepetition.MIN_EASE)
        val after = SpacedRepetition.apply(alreadyAtFloor, ReviewGrade.UNKNOWN, now)

        assertThat(after.easeFactor).isEqualTo(SpacedRepetition.MIN_EASE)
    }

    @Test
    fun `unknown subtracts a fixed ease penalty`() {
        val before = graduatedWord(ease = 2.0)
        val after = SpacedRepetition.apply(before, ReviewGrade.UNKNOWN, now)

        assertEquals(1.8, after.easeFactor, 0.0001)
    }

    // --- 模糊 ----------------------------------------------------------------------------

    @Test
    fun `fuzzy shortens the previous interval and keeps the word in play`() {
        val before = graduatedWord(intervalDays = 10, repetitions = 4)
        val after = SpacedRepetition.apply(before, ReviewGrade.FUZZY, now)

        assertThat(after.intervalDays).isEqualTo(6)
        assertThat(after.repetitions).isEqualTo(4)
        assertThat(after.masteryLevel).isEqualTo(2)
        assertThat(after.status).isEqualTo(MasteryStatus.RELEARNING)
        assertThat(after.dueAt).isEqualTo(now + SpacedRepetition.daysToMillis(6))
        assertThat(after.lastAnswer).isEqualTo(ReviewGrade.FUZZY)
        assertThat(after.fuzzyCount).isEqualTo(before.fuzzyCount + 1)
        assertThat(after.consecutiveCorrectCount).isEqualTo(0)
    }

    @Test
    fun `fuzzy of a new word uses the one day step`() {
        val after = SpacedRepetition.apply(newWord(), ReviewGrade.FUZZY, now)

        assertThat(after.intervalDays).isEqualTo(1)
        assertThat(after.status).isEqualTo(MasteryStatus.LEARNING)
        assertThat(after.repetitions).isEqualTo(1)
        assertThat(after.dueAt).isEqualTo(now + oneDay)
    }

    @Test
    fun `fuzzy never schedules less than one day once the word has graduated`() {
        val before = graduatedWord(intervalDays = 1, repetitions = 1)
        val after = SpacedRepetition.apply(before, ReviewGrade.FUZZY, now)

        assertThat(after.intervalDays).isAtLeast(1)
        assertThat(after.dueAt - now).isAtLeast(oneDay)
    }

    @Test
    fun `fuzzy never falls below one day even with a zero interval`() {
        val zeroInterval = WordProgress(
            wordId = 1L,
            status = MasteryStatus.REVIEW,
            intervalDays = 0,
            repetitions = 2,
            dueAt = now,
        )
        val after = SpacedRepetition.apply(zeroInterval, ReviewGrade.FUZZY, now)

        assertThat(after.intervalDays).isEqualTo(1)
    }

    @Test
    fun `fuzzy clamps the ease factor at the minimum`() {
        val atFloor = graduatedWord(ease = SpacedRepetition.MIN_EASE)
        val after = SpacedRepetition.apply(atFloor, ReviewGrade.FUZZY, now)

        assertThat(after.easeFactor).isEqualTo(SpacedRepetition.MIN_EASE)
    }

    @Test
    fun `fuzzy keeps the ease penalty smaller than a hard failure`() {
        val before = graduatedWord(ease = 2.0)
        val fuzzy = SpacedRepetition.apply(before, ReviewGrade.FUZZY, now)
        val unknown = SpacedRepetition.apply(before, ReviewGrade.UNKNOWN, now)

        assertEquals(1.95, fuzzy.easeFactor, 0.0001)
        assertThat(fuzzy.easeFactor).isGreaterThan(unknown.easeFactor)
    }

    // --- 已掌握 --------------------------------------------------------------------------

    @Test
    fun `mastered follows the fixed 1 3 7 14 day steps`() {
        var progress = newWord()

        progress = SpacedRepetition.apply(progress, ReviewGrade.MASTERED, now)
        assertThat(progress.intervalDays).isEqualTo(1)
        assertThat(progress.repetitions).isEqualTo(1)

        progress = SpacedRepetition.apply(progress, ReviewGrade.MASTERED, now)
        assertThat(progress.intervalDays).isEqualTo(3)

        progress = SpacedRepetition.apply(progress, ReviewGrade.MASTERED, now)
        assertThat(progress.intervalDays).isEqualTo(7)

        progress = SpacedRepetition.apply(progress, ReviewGrade.MASTERED, now)
        assertThat(progress.intervalDays).isEqualTo(14)
    }

    @Test
    fun `mastered grows geometrically after the fixed steps`() {
        val before = graduatedWord(masteryLevel = 4, repetitions = 4, intervalDays = 14)
        val after = SpacedRepetition.apply(before, ReviewGrade.MASTERED, now)

        // The documented ease rule: +0.15 for the very first success, +0.05 once the word is
        // already easy (>= 2.2), never above MAX_EASE. A word at 2.5 therefore lands on 2.55,
        // and the next interval is the previous one multiplied by the new ease.
        val expectedEase = minOf(SpacedRepetition.MAX_EASE, before.easeFactor + 0.05)
        assertEquals(expectedEase, after.easeFactor, 0.0001)
        assertThat(after.repetitions).isEqualTo(5)
        assertThat(after.intervalDays).isGreaterThan(before.intervalDays)
        assertThat(after.intervalDays).isEqualTo(Math.round(before.intervalDays * expectedEase).toInt())
    }

    @Test
    fun `mastered never exceeds the maximum interval`() {
        var progress = graduatedWord(masteryLevel = 5, repetitions = 9, intervalDays = 170)
        repeat(6) { progress = SpacedRepetition.apply(progress, ReviewGrade.MASTERED, now) }

        assertThat(progress.intervalDays).isEqualTo(SpacedRepetition.MAX_INTERVAL_DAYS)
        assertThat(progress.intervalDays).isAtMost(SpacedRepetition.MAX_INTERVAL_DAYS)
    }

    @Test
    fun `mastered raises the ease factor but caps it at the maximum`() {
        var progress = graduatedWord(ease = SpacedRepetition.DEFAULT_EASE)
        repeat(10) { progress = SpacedRepetition.apply(progress, ReviewGrade.MASTERED, now) }

        assertThat(progress.easeFactor).isAtMost(SpacedRepetition.MAX_EASE)
        assertThat(progress.easeFactor).isEqualTo(SpacedRepetition.MAX_EASE)
    }

    @Test
    fun `mastered reaches the mastered status only at the mastered level with a long interval`() {
        var progress = newWord()
        repeat(4) { progress = SpacedRepetition.apply(progress, ReviewGrade.MASTERED, now) }

        // Two steps away: level 4 and a 14 day interval are not enough yet.
        assertThat(progress.masteryLevel).isEqualTo(4)
        assertThat(progress.intervalDays).isEqualTo(14)
        assertThat(progress.status).isEqualTo(MasteryStatus.REVIEW)

        progress = SpacedRepetition.apply(progress, ReviewGrade.MASTERED, now)
        assertThat(progress.masteryLevel).isEqualTo(SpacedRepetition.MASTERED_LEVEL)
        assertThat(progress.intervalDays)
            .isAtLeast(SpacedRepetition.MASTERED_MIN_INTERVAL_DAYS)
        assertThat(progress.status).isEqualTo(MasteryStatus.MASTERED)
    }

    @Test
    fun `mastered keeps counting consecutive correct answers`() {
        var progress = newWord()
        progress = SpacedRepetition.apply(progress, ReviewGrade.MASTERED, now)
        progress = SpacedRepetition.apply(progress, ReviewGrade.MASTERED, now)
        progress = SpacedRepetition.apply(progress, ReviewGrade.MASTERED, now)
        assertThat(progress.consecutiveCorrectCount).isEqualTo(3)

        progress = SpacedRepetition.apply(progress, ReviewGrade.UNKNOWN, now)
        assertThat(progress.consecutiveCorrectCount).isEqualTo(0)
    }

    // --- Purity and clock anomalies --------------------------------------------------------

    @Test
    fun `apply is a pure function and never mutates the input`() {
        val before = graduatedWord(masteryLevel = 3, repetitions = 4, intervalDays = 14)
        val snapshot = before.copy()

        val unknown = SpacedRepetition.apply(before, ReviewGrade.UNKNOWN, now)
        val fuzzy = SpacedRepetition.apply(before, ReviewGrade.FUZZY, now)
        val mastered = SpacedRepetition.apply(before, ReviewGrade.MASTERED, now)

        assertThat(before).isEqualTo(snapshot)
        assertThat(before.wrongCount).isEqualTo(snapshot.wrongCount)
        assertThat(before.easeFactor).isEqualTo(snapshot.easeFactor)
        assertThat(before.intervalDays).isEqualTo(snapshot.intervalDays)
        assertThat(unknown).isNotSameInstanceAs(before)
        assertThat(fuzzy.wordId).isEqualTo(before.wordId)
        assertThat(mastered.wordId).isEqualTo(before.wordId)
    }

    @Test
    fun `a now earlier than the last review never produces a negative interval`() {
        val futureNow = now + 10 * oneDay
        val before = WordProgress(
            wordId = 1L,
            status = MasteryStatus.REVIEW,
            masteryLevel = 4,
            repetitions = 4,
            intervalDays = 14,
            dueAt = futureNow,
            lastReviewedAt = futureNow,
        )
        val earlier = now

        val after = SpacedRepetition.apply(before, ReviewGrade.MASTERED, earlier)

        assertThat(after.intervalDays).isAtLeast(1)
        assertThat(after.dueAt).isAtLeast(futureNow)
        assertThat(after.dueAt - earlier).isGreaterThan(0L)
        assertThat(after.lastReviewedAt).isEqualTo(futureNow)
    }

    @Test
    fun `a backwards clock still schedules a short delay instead of a negative one`() {
        val before = graduatedWord()
        val after = SpacedRepetition.apply(before, ReviewGrade.UNKNOWN, now - 5 * oneDay)

        assertThat(after.dueAt).isAtLeast(before.lastReviewedAt!!)
        assertThat(after.dueAt - (now - 5 * oneDay)).isGreaterThan(0L)
        assertThat(after.intervalDays).isEqualTo(0)
    }

    @Test
    fun `next interval days is clamped between one and the maximum`() {
        assertThat(SpacedRepetition.nextIntervalDays(0, 0, SpacedRepetition.DEFAULT_EASE))
            .isEqualTo(1)
        assertThat(SpacedRepetition.nextIntervalDays(5, 0, SpacedRepetition.DEFAULT_EASE))
            .isEqualTo(35)
        assertThat(SpacedRepetition.nextIntervalDays(9, 180, SpacedRepetition.MAX_EASE))
            .isEqualTo(SpacedRepetition.MAX_INTERVAL_DAYS)
    }

    @Test
    fun `days to millis converts whole days`() {
        assertThat(SpacedRepetition.daysToMillis(3)).isEqualTo(3 * oneDay)
        assertThat(SpacedRepetition.daysToMillis(0)).isEqualTo(0L)
    }

    @Test
    fun `exam answers map to mastered or unknown grades`() {
        assertThat(ReviewGrade.fromExam(true)).isEqualTo(ReviewGrade.MASTERED)
        assertThat(ReviewGrade.fromExam(false)).isEqualTo(ReviewGrade.UNKNOWN)
    }

    @Test
    fun `mastery status labels and consolidation flags are stable`() {
        assertThat(MasteryStatus.MASTERED.isConsolidated).isTrue()
        assertThat(MasteryStatus.REVIEW.isConsolidated).isTrue()
        assertThat(MasteryStatus.LEARNING.isConsolidated).isFalse()
        assertThat(MasteryStatus.RELEARNING.isConsolidated).isFalse()
        assertThat(MasteryStatus.NEW.isConsolidated).isFalse()
        assertThat(MasteryStatus.NEW.label).isEqualTo("未学习")
        assertThat(MasteryStatus.MASTERED.label).isEqualTo("已掌握")
    }
}
