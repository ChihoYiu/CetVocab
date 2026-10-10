package com.cetvocab.app.domain.stats

import com.cetvocab.app.domain.model.CetLevel
import java.time.LocalDate

/**
 * Derives the statistics shown in the app.
 *
 * Everything is a pure function over already aggregated rows, which keeps the numbers
 * testable (including the streak and the day-boundary cases) and keeps the UI free of
 * calculation logic.
 *
 * Tone matters here: percentages are nullable so that "no data" is never rendered as a
 * discouraging 0%, and the completion rate is expressed as "reviewed / (reviewed + still
 * due)" rather than as a failure count.
 */
object StatsCalculator {

    /**
     * Consecutive study days ending today or yesterday.
     *
     * Yesterday still counts as "current streak" so that opening the app in the morning
     * before studying does not reset the number the learner is proud of.
     */
    fun currentStreak(activeDays: Collection<LocalDate>, today: LocalDate): Int {
        val days = activeDays.toSortedSet()
        if (days.isEmpty()) return 0
        var cursor = when {
            days.contains(today) -> today
            days.contains(today.minusDays(1)) -> today.minusDays(1)
            else -> return 0
        }
        var streak = 0
        while (days.contains(cursor)) {
            streak++
            cursor = cursor.minusDays(1)
        }
        return streak
    }

    /** Longest run of consecutive study days in the whole history. */
    fun longestStreak(activeDays: Collection<LocalDate>): Int {
        val days = activeDays.toSortedSet().toList()
        if (days.isEmpty()) return 0
        var best = 1
        var run = 1
        for (index in 1 until days.size) {
            run = if (days[index] == days[index - 1].plusDays(1)) run + 1 else 1
            if (run > best) best = run
        }
        return best
    }

    /** Null when there is nothing to divide by. */
    fun percent(numerator: Int, denominator: Int): Int? =
        if (denominator <= 0) null else (numerator * 100 / denominator).coerceIn(0, 100)

    /**
     * Review completion rate for the current day: reviews finished against everything that
     * was on the table (finished + still due). Returns null before any review happened.
     */
    fun reviewCompletion(reviewedToday: Int, stillDue: Int): Int? {
        val total = reviewedToday + stillDue
        return if (total <= 0) null else (reviewedToday * 100 / total).coerceIn(0, 100)
    }

    /**
     * Merges daily summaries and review-event counts into a continuous range ending today,
     * filling days without activity with zeros so charts and the calendar have no gaps.
     */
    fun buildSeries(
        summaries: List<DailySummaryInput>,
        trends: List<DailyTrendInput>,
        from: LocalDate,
        to: LocalDate,
    ): List<DailyStudyPoint> {
        val summaryByDay = summaries.associateBy { it.dayKey }
        val trendByDay = trends.associateBy { it.dayKey }
        val points = ArrayList<DailyStudyPoint>()
        var cursor = from
        while (!cursor.isAfter(to)) {
            val key = cursor.toString()
            val summary = summaryByDay[key]
            val trend = trendByDay[key]
            points += DailyStudyPoint(
                dayKey = key,
                date = cursor,
                studiedWords = summary?.studiedWords ?: 0,
                reviewEvents = trend?.total ?: summary?.reviewEvents ?: 0,
                correct = trend?.correct ?: (summary?.masteredCount ?: 0),
                fuzzy = trend?.fuzzy ?: (summary?.fuzzyCount ?: 0),
                wrong = trend?.wrong ?: (summary?.unknownCount ?: 0),
                durationMillis = summary?.durationMillis ?: 0L,
            )
            cursor = cursor.plusDays(1)
        }
        return points
    }

    /**
     * A small, achievable goal: the next vocabulary milestone, or the whole list once every
     * milestone has been passed. It is a plan, never a promise about exam results.
     *
     * Milestones replace "the whole list" because 3849 words is a number nobody can act on, while
     * the next 50 or 200 is. Exactly at a milestone the goal moves to the *next* one, so the number
     * on screen always names something still ahead of the learner.
     *
     * A milestone **beyond the end of the list is never chosen**: a 10-word imported list must not
     * be told to reach 50. In that case the list itself is the goal. `current` is clamped to
     * `target`, so `percent` stays in 0..100 and `remaining` is never negative even when more words
     * were learned than the list holds.
     */
    fun stageGoal(level: CetLevel, totalWords: Int, learnedWords: Int, dailyGoal: Int): StageGoal {
        val effectiveDaily = dailyGoal.coerceAtLeast(1)
        if (totalWords <= 0) {
            // Empty/absent list: unchanged behaviour, the goal is the daily pace itself.
            val target = effectiveDaily
            return StageGoal(
                label = "完成${level.shortLabel}词库",
                current = learnedWords.coerceAtMost(target),
                target = target,
                isReached = learnedWords >= target,
            )
        }
        // Only milestones this list can actually reach; otherwise fall back to the list itself.
        val nextMilestone = VOCABULARY_MILESTONES.firstOrNull { it > learnedWords && it <= totalWords }
        val target = nextMilestone ?: totalWords
        return StageGoal(
            label = if (nextMilestone == null) {
                "完成${level.shortLabel}词库"
            } else {
                "完成${level.shortLabel}词库 · 下一个里程碑 $nextMilestone 词"
            },
            current = learnedWords.coerceAtMost(target),
            target = target,
            nextMilestone = nextMilestone,
            isReached = learnedWords >= target,
        )
    }

    /** Average exam accuracy as an integer percentage, or null when no exam was taken. */
    fun examAveragePercent(average: Double?): Int? =
        average?.takeIf { !it.isNaN() }?.let { it.toInt().coerceIn(0, 100) }

    /** Total minutes studied, from summed milliseconds. */
    fun toMinutes(millis: Long): Int = (millis / 60_000L).toInt()
}
