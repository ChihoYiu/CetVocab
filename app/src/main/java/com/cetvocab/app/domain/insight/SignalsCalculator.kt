package com.cetvocab.app.domain.insight

import com.cetvocab.app.domain.stats.DailyStudyPoint
import java.time.LocalDate

/**
 * Derives the time-window numbers the insight rules need from the already-computed daily trend.
 *
 * Why a separate pure object: the alternative is SQL for every window, which would duplicate the
 * study-day boundary logic (04:00) that `StudyDay` and the daily summaries already implement. The
 * trend rows are exactly those summaries, so reusing them keeps one definition of "a day" — and it
 * makes the arithmetic unit-testable without a database.
 *
 * Every percentage is nullable: "no answers in the window" must not be reported as 0%, because the
 * difference between "you are failing everything" and "you have not studied" is the difference
 * between a useful app and an anxiety machine.
 */
data class TrendSignals(
    val answersLast7Days: Int,
    val accuracyLast7Days: Int?,
    val accuracyPrevious7Days: Int?,
    val activeDaysLast7Days: Int,
    val activeDaysPrevious14Days: Int,
    val medianMinutesPerDayLast7Days: Int,
)

object SignalsCalculator {

    /** The window the trend list must cover for [fromTrend] to be meaningful. */
    const val REQUIRED_TREND_DAYS = 21

    /**
     * @param trend daily points, any order, with empty days already filled in as zeros
     * @param today the current study day; points after it are ignored
     */
    fun fromTrend(trend: List<DailyStudyPoint>, today: LocalDate): TrendSignals {
        val usable = trend.filter { !it.date.isAfter(today) }
        val last7 = window(usable, today, fromDaysAgo = 6, toDaysAgo = 0)
        val previous7 = window(usable, today, fromDaysAgo = 13, toDaysAgo = 7)
        // Days 7..20 ago: a full fortnight that does not overlap the week being judged.
        val previous14 = window(usable, today, fromDaysAgo = 20, toDaysAgo = 7)

        return TrendSignals(
            answersLast7Days = last7.sumOf { it.reviewEvents },
            accuracyLast7Days = accuracyOf(last7),
            accuracyPrevious7Days = accuracyOf(previous7),
            activeDaysLast7Days = last7.count { it.hasActivity },
            activeDaysPrevious14Days = previous14.count { it.hasActivity },
            medianMinutesPerDayLast7Days = medianMinutes(last7),
        )
    }

    private fun window(
        points: List<DailyStudyPoint>,
        today: LocalDate,
        fromDaysAgo: Long,
        toDaysAgo: Long,
    ): List<DailyStudyPoint> {
        val from = today.minusDays(fromDaysAgo)
        val to = today.minusDays(toDaysAgo)
        return points.filter { !it.date.isBefore(from) && !it.date.isAfter(to) }
    }

    /** Correct answers as a percentage of answer events, or null when nothing was answered. */
    private fun accuracyOf(points: List<DailyStudyPoint>): Int? {
        val answers = points.sumOf { it.reviewEvents }
        if (answers <= 0) return null
        return (points.sumOf { it.correct } * 100) / answers
    }

    /**
     * Median minutes over the **active** days only.
     *
     * Averaging over all seven days would report a learner who studied twice for 40 minutes as
     * "11 minutes a day", which would make the "sessions are too long" rule unreachable. The median
     * over active days answers the question the rule actually asks: when you sit down, how long do
     * you stay?
     */
    /** Rounds a median to whole minutes; the UI always labels these as approximate. */
    private fun medianMinutes(points: List<DailyStudyPoint>): Int {
        val minutes = points.filter { it.hasActivity }.map { it.durationMinutes }.sorted()
        if (minutes.isEmpty()) return 0
        val middle = minutes.size / 2
        return if (minutes.size % 2 == 1) {
            minutes[middle]
        } else {
            (minutes[middle - 1] + minutes[middle]) / 2
        }
    }
}
