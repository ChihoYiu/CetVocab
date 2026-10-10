package com.cetvocab.app.domain.insight

import com.cetvocab.app.domain.stats.DailyStudyPoint
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.LocalDate

/**
 * Tests for the time-window arithmetic behind the advice.
 *
 * These windows decide whether the app says "your accuracy dropped" or "you have not studied" — two
 * very different messages — so the boundaries (exactly 7 days, no answers at all, a gap in the
 * middle) are pinned here rather than trusted to a SQL query the tests cannot see.
 */
class SignalsCalculatorTest {

    private val today: LocalDate = LocalDate.of(2026, 5, 20)

    private fun day(
        daysAgo: Long,
        reviewEvents: Int = 0,
        correct: Int = 0,
        durationMinutes: Int = 0,
    ) = DailyStudyPoint(
        dayKey = today.minusDays(daysAgo).toString(),
        date = today.minusDays(daysAgo),
        studiedWords = if (reviewEvents > 0) 1 else 0,
        reviewEvents = reviewEvents,
        correct = correct,
        fuzzy = 0,
        wrong = 0,
        durationMillis = durationMinutes * 60_000L,
    )

    @Test
    fun `no history produces null percentages rather than zeros`() {
        val signals = SignalsCalculator.fromTrend(emptyList(), today)

        assertThat(signals.answersLast7Days).isEqualTo(0)
        // 0% would read as "you fail everything"; null is rendered as "暂无数据".
        assertThat(signals.accuracyLast7Days).isNull()
        assertThat(signals.accuracyPrevious7Days).isNull()
        assertThat(signals.activeDaysLast7Days).isEqualTo(0)
        assertThat(signals.medianMinutesPerDayLast7Days).isEqualTo(0)
    }

    @Test
    fun `the last seven days and the previous seven days do not overlap`() {
        val trend = listOf(
            day(daysAgo = 0, reviewEvents = 10, correct = 10),   // this week
            day(daysAgo = 6, reviewEvents = 10, correct = 0),    // this week (oldest day in window)
            day(daysAgo = 7, reviewEvents = 10, correct = 10),   // previous week (newest day)
            day(daysAgo = 13, reviewEvents = 10, correct = 10),  // previous week (oldest day)
        )

        val signals = SignalsCalculator.fromTrend(trend, today)

        assertThat(signals.answersLast7Days).isEqualTo(20)
        assertThat(signals.accuracyLast7Days).isEqualTo(50)
        // 20 answers at 100% in the previous window: the two windows must not share a row.
        assertThat(signals.accuracyPrevious7Days).isEqualTo(100)
    }

    @Test
    fun `active days are counted per window, not per answer`() {
        val trend = listOf(
            day(daysAgo = 0, reviewEvents = 30, correct = 30),
            day(daysAgo = 1, reviewEvents = 5, correct = 5),
            day(daysAgo = 2),                                   // empty day, still inside the window
            day(daysAgo = 8, reviewEvents = 5, correct = 5),
            day(daysAgo = 9, reviewEvents = 5, correct = 5),
        )

        val signals = SignalsCalculator.fromTrend(trend, today)

        assertThat(signals.activeDaysLast7Days).isEqualTo(2)
        assertThat(signals.activeDaysPrevious14Days).isEqualTo(2)
    }

    @Test
    fun `the median uses active days only so one long session is not averaged away`() {
        // Two 60-minute sessions. Averaged over seven days this would read as ~17 minutes, which
        // would hide exactly the "your sessions are too long" case the rule looks for.
        val trend = listOf(
            day(daysAgo = 0, reviewEvents = 10, correct = 8, durationMinutes = 60),
            day(daysAgo = 2, reviewEvents = 10, correct = 8, durationMinutes = 60),
        )

        val signals = SignalsCalculator.fromTrend(trend, today)

        assertThat(signals.medianMinutesPerDayLast7Days).isEqualTo(60)
    }

    @Test
    fun `the median of an even number of active days is the lower middle value`() {
        val trend = listOf(
            day(daysAgo = 0, reviewEvents = 5, correct = 5, durationMinutes = 10),
            day(daysAgo = 1, reviewEvents = 5, correct = 5, durationMinutes = 20),
            day(daysAgo = 2, reviewEvents = 5, correct = 5, durationMinutes = 40),
            day(daysAgo = 3, reviewEvents = 5, correct = 5, durationMinutes = 50),
        )

        val signals = SignalsCalculator.fromTrend(trend, today)

        // (20 + 40) / 2 = 30
        assertThat(signals.medianMinutesPerDayLast7Days).isEqualTo(30)
    }

    @Test
    fun `future days are ignored`() {
        val trend = listOf(
            day(daysAgo = 0, reviewEvents = 10, correct = 5),
            // A clock that was briefly wrong must not let tomorrow's row into this week's window.
            day(daysAgo = -1, reviewEvents = 100, correct = 100),
        )

        val signals = SignalsCalculator.fromTrend(trend, today)

        assertThat(signals.answersLast7Days).isEqualTo(10)
        assertThat(signals.accuracyLast7Days).isEqualTo(50)
    }

    @Test
    fun `a trend shorter than the required window still produces usable numbers`() {
        // The statistics repository may hand over fewer rows right after the first study day.
        val signals = SignalsCalculator.fromTrend(listOf(day(daysAgo = 0, reviewEvents = 4, correct = 3)), today)

        assertThat(signals.answersLast7Days).isEqualTo(4)
        assertThat(signals.accuracyLast7Days).isEqualTo(75)
        assertThat(signals.accuracyPrevious7Days).isNull()
    }

    @Test
    fun `the required window constant covers both comparison periods`() {
        // 21 days = the 7 being judged + the 14 before them.
        assertThat(SignalsCalculator.REQUIRED_TREND_DAYS).isEqualTo(21)
    }
}
