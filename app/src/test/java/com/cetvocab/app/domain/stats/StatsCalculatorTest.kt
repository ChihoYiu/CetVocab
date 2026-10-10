package com.cetvocab.app.domain.stats

import com.cetvocab.app.domain.model.CetLevel
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.LocalDate

/**
 * Unit tests for the pure statistics helpers.
 *
 * All dates are fixed literals so that "today" never depends on the machine running the test.
 */
class StatsCalculatorTest {

    private val today = LocalDate.of(2026, 1, 15)

    private fun day(offset: Int): LocalDate = today.plusDays(offset.toLong())

    private fun summary(
        date: LocalDate,
        studiedWords: Int = 5,
        masteredCount: Int = 3,
        fuzzyCount: Int = 1,
        unknownCount: Int = 1,
        reviewEvents: Int = 5,
        durationMillis: Long = 600_000L,
    ) = DailySummaryInput(
        dayKey = date.toString(),
        studiedWords = studiedWords,
        newWords = 2,
        reviewWords = studiedWords - 2,
        masteredCount = masteredCount,
        fuzzyCount = fuzzyCount,
        unknownCount = unknownCount,
        reviewEvents = reviewEvents,
        durationMillis = durationMillis,
        sessionsCompleted = 1,
        examsCompleted = 0,
        examCorrect = 0,
        examTotal = 0,
    )

    private fun trend(
        date: LocalDate,
        total: Int,
        correct: Int,
        fuzzy: Int,
        wrong: Int,
    ) = DailyTrendInput(
        dayKey = date.toString(),
        total = total,
        correct = correct,
        fuzzy = fuzzy,
        wrong = wrong,
    )

    // --- currentStreak ---------------------------------------------------------------------

    @Test
    fun `current streak counts today and the days before it`() {
        val days = listOf(day(-2), day(-1), today)

        assertThat(StatsCalculator.currentStreak(days, today)).isEqualTo(3)
    }

    @Test
    fun `a streak that ends yesterday still counts as current`() {
        val days = listOf(day(-3), day(-2), day(-1))

        assertThat(StatsCalculator.currentStreak(days, today)).isEqualTo(3)
    }

    @Test
    fun `a gap breaks the current streak`() {
        val days = listOf(day(-3), day(-2), today)

        assertThat(StatsCalculator.currentStreak(days, today)).isEqualTo(1)
    }

    @Test
    fun `no activity today or yesterday means no current streak`() {
        assertThat(StatsCalculator.currentStreak(listOf(day(-2), day(-3)), today)).isEqualTo(0)
        assertThat(StatsCalculator.currentStreak(emptyList(), today)).isEqualTo(0)
    }

    @Test
    fun `a future day does not count towards the current streak`() {
        assertThat(StatsCalculator.currentStreak(listOf(day(1)), today)).isEqualTo(0)
        assertThat(StatsCalculator.currentStreak(listOf(today, day(1)), today)).isEqualTo(1)
    }

    @Test
    fun `duplicate days are counted once`() {
        assertThat(StatsCalculator.currentStreak(listOf(today, today, day(-1), day(-1)), today))
            .isEqualTo(2)
    }

    @Test
    fun `longest streak finds the best run in history`() {
        val days = listOf(day(-10), day(-9), day(-8), day(-5), today, day(-1))

        assertThat(StatsCalculator.longestStreak(days)).isEqualTo(3)
    }

    @Test
    fun `longest streak of a single day is one and of nothing is zero`() {
        assertThat(StatsCalculator.longestStreak(listOf(day(-4)))).isEqualTo(1)
        assertThat(StatsCalculator.longestStreak(emptyList())).isEqualTo(0)
    }

    @Test
    fun `longest streak ignores the order the days arrive in`() {
        assertThat(StatsCalculator.longestStreak(listOf(today, day(-2), day(-1), day(-4))))
            .isEqualTo(3)
    }

    // --- percent / review completion ---------------------------------------------------------

    @Test
    fun `percent is null on a zero denominator`() {
        assertThat(StatsCalculator.percent(5, 0)).isNull()
        assertThat(StatsCalculator.percent(0, 0)).isNull()
        assertThat(StatsCalculator.percent(5, -3)).isNull()
    }

    @Test
    fun `percent is an integer and is clamped`() {
        assertThat(StatsCalculator.percent(1, 3)).isEqualTo(33)
        assertThat(StatsCalculator.percent(2, 4)).isEqualTo(50)
        assertThat(StatsCalculator.percent(7, 3)).isEqualTo(100)
        assertThat(StatsCalculator.percent(-5, 10)).isEqualTo(0)
    }

    @Test
    fun `review completion is null when there is nothing to review`() {
        assertThat(StatsCalculator.reviewCompletion(0, 0)).isNull()
        assertThat(StatsCalculator.reviewCompletion(0, -2)).isNull()
    }

    @Test
    fun `review completion compares finished reviews with everything on the table`() {
        assertThat(StatsCalculator.reviewCompletion(3, 1)).isEqualTo(75)
        assertThat(StatsCalculator.reviewCompletion(10, 0)).isEqualTo(100)
        assertThat(StatsCalculator.reviewCompletion(0, 5)).isEqualTo(0)
    }

    // --- buildSeries ------------------------------------------------------------------------

    @Test
    fun `build series fills days without activity with zeros`() {
        val series = StatsCalculator.buildSeries(
            summaries = listOf(summary(today)),
            trends = emptyList(),
            from = day(-2),
            to = today,
        )

        assertThat(series.map { it.dayKey }).containsExactly(
            day(-2).toString(),
            day(-1).toString(),
            today.toString(),
        ).inOrder()
        assertThat(series.map { it.date }).containsExactly(day(-2), day(-1), today).inOrder()

        val empty = series.first()
        assertThat(empty.studiedWords).isEqualTo(0)
        assertThat(empty.reviewEvents).isEqualTo(0)
        assertThat(empty.correct).isEqualTo(0)
        assertThat(empty.fuzzy).isEqualTo(0)
        assertThat(empty.wrong).isEqualTo(0)
        assertThat(empty.durationMillis).isEqualTo(0L)
        assertThat(empty.hasActivity).isFalse()
        assertThat(empty.accuracyPercent).isNull()
    }

    @Test
    fun `build series merges summary and trend rows for the same day`() {
        val series = StatsCalculator.buildSeries(
            summaries = listOf(summary(today, studiedWords = 7, durationMillis = 900_000L)),
            trends = listOf(trend(today, total = 4, correct = 2, fuzzy = 1, wrong = 1)),
            from = today,
            to = today,
        )

        val point = series.single()
        assertThat(point.studiedWords).isEqualTo(7)
        assertThat(point.durationMillis).isEqualTo(900_000L)
        assertThat(point.durationMinutes).isEqualTo(15)
        assertThat(point.reviewEvents).isEqualTo(4)
        assertThat(point.correct).isEqualTo(2)
        assertThat(point.fuzzy).isEqualTo(1)
        assertThat(point.wrong).isEqualTo(1)
        assertThat(point.hasActivity).isTrue()
        assertThat(point.accuracyPercent).isEqualTo(50)
    }

    @Test
    fun `build series falls back to the summary when no trend row exists`() {
        val series = StatsCalculator.buildSeries(
            summaries = listOf(summary(today, masteredCount = 6, fuzzyCount = 2, unknownCount = 1, reviewEvents = 9)),
            trends = emptyList(),
            from = today,
            to = today,
        )

        val point = series.single()
        assertThat(point.reviewEvents).isEqualTo(9)
        assertThat(point.correct).isEqualTo(6)
        assertThat(point.fuzzy).isEqualTo(2)
        assertThat(point.wrong).isEqualTo(1)
    }

    @Test
    fun `build series uses the trend when the summary is missing`() {
        val series = StatsCalculator.buildSeries(
            summaries = emptyList(),
            trends = listOf(trend(today, total = 5, correct = 5, fuzzy = 0, wrong = 0)),
            from = today,
            to = today,
        )

        assertThat(series.single().reviewEvents).isEqualTo(5)
        assertThat(series.single().correct).isEqualTo(5)
        assertThat(series.single().studiedWords).isEqualTo(0)
    }

    @Test
    fun `build series returns one point per day of the range`() {
        val series = StatsCalculator.buildSeries(
            summaries = emptyList(),
            trends = emptyList(),
            from = LocalDate.of(2025, 12, 30),
            to = LocalDate.of(2026, 1, 5),
        )

        assertThat(series).hasSize(7)
        assertThat(series.first().dayKey).isEqualTo("2025-12-30")
        assertThat(series.last().dayKey).isEqualTo("2026-01-05")
    }

    @Test
    fun `build series over a single day returns one point`() {
        assertThat(StatsCalculator.buildSeries(emptyList(), emptyList(), today, today)).hasSize(1)
    }

    @Test
    fun `build series over an inverted range is empty`() {
        assertThat(
            StatsCalculator.buildSeries(emptyList(), emptyList(), today, today.minusDays(1)),
        ).isEmpty()
    }

    // --- stageGoal ----------------------------------------------------------------------------

    @Test
    fun `stage goal points at the first milestone when nothing is learned yet`() {
        val goal = StatsCalculator.stageGoal(CetLevel.CET4, totalWords = 3849, learnedWords = 0, dailyGoal = 20)

        assertThat(goal.label).isEqualTo("完成四级词库 · 下一个里程碑 50 词")
        assertThat(goal.nextMilestone).isEqualTo(50)
        assertThat(goal.current).isEqualTo(0)
        assertThat(goal.target).isEqualTo(50)
        assertThat(goal.percent).isEqualTo(0)
        assertThat(goal.remaining).isEqualTo(50)
        assertThat(goal.isReached).isFalse()
    }

    @Test
    fun `stage goal moves to the next milestone when one is reached exactly`() {
        val atFifty = StatsCalculator.stageGoal(CetLevel.CET4, totalWords = 3849, learnedWords = 50, dailyGoal = 20)
        val atFiveHundred = StatsCalculator.stageGoal(CetLevel.CET4, totalWords = 3849, learnedWords = 500, dailyGoal = 20)
        val atLast = StatsCalculator.stageGoal(CetLevel.CET4, totalWords = 3849, learnedWords = 2000, dailyGoal = 20)

        assertThat(atFifty.nextMilestone).isEqualTo(200)
        assertThat(atFifty.target).isEqualTo(200)
        assertThat(atFifty.current).isEqualTo(50)
        assertThat(atFifty.isReached).isFalse()

        assertThat(atFiveHundred.nextMilestone).isEqualTo(1000)
        assertThat(atFiveHundred.target).isEqualTo(1000)

        // Reaching the last milestone means the goal becomes the whole list again.
        assertThat(atLast.nextMilestone).isNull()
        assertThat(atLast.target).isEqualTo(3849)
    }

    @Test
    fun `stage goal picks the smallest milestone above the learned count`() {
        fun target(learned: Int) =
            StatsCalculator.stageGoal(CetLevel.CET4, totalWords = 3849, learnedWords = learned, dailyGoal = 20)

        assertThat(target(1).target).isEqualTo(50)
        assertThat(target(49).target).isEqualTo(50)
        assertThat(target(51).target).isEqualTo(200)
        assertThat(target(199).target).isEqualTo(200)
        assertThat(target(201).target).isEqualTo(500)
        assertThat(target(700).target).isEqualTo(1000)
        assertThat(target(1500).target).isEqualTo(2000)
    }

    @Test
    fun `stage goal between two milestones reports progress towards the next one`() {
        val goal = StatsCalculator.stageGoal(CetLevel.CET4, totalWords = 3849, learnedWords = 120, dailyGoal = 20)

        assertThat(goal.label).isEqualTo("完成四级词库 · 下一个里程碑 200 词")
        assertThat(goal.current).isEqualTo(120)
        assertThat(goal.target).isEqualTo(200)
        assertThat(goal.percent).isEqualTo(60)
        assertThat(goal.remaining).isEqualTo(80)
        assertThat(goal.nextMilestone).isEqualTo(200)
        assertThat(goal.isReached).isFalse()
    }

    @Test
    fun `stage goal falls back to the whole list once every milestone is passed`() {
        val allPassed = StatsCalculator.stageGoal(CetLevel.CET6, totalWords = 3849, learnedWords = 2000, dailyGoal = 20)
        val finished = StatsCalculator.stageGoal(CetLevel.CET6, totalWords = 3849, learnedWords = 3849, dailyGoal = 20)

        assertThat(allPassed.nextMilestone).isNull()
        assertThat(allPassed.label).isEqualTo("完成六级词库")
        assertThat(allPassed.current).isEqualTo(2000)
        assertThat(allPassed.target).isEqualTo(3849)
        assertThat(allPassed.percent).isEqualTo(51)
        assertThat(allPassed.remaining).isEqualTo(1849)
        assertThat(allPassed.isReached).isFalse()

        assertThat(finished.nextMilestone).isNull()
        assertThat(finished.current).isEqualTo(3849)
        assertThat(finished.target).isEqualTo(3849)
        assertThat(finished.percent).isEqualTo(100)
        assertThat(finished.remaining).isEqualTo(0)
        assertThat(finished.isReached).isTrue()
    }

    @Test
    fun `stage goal never names a milestone the list cannot reach`() {
        // A 10-word imported list must not be told to reach the 50-word milestone: the goal would be
        // unreachable and "还差 50 词" would be false. The list itself becomes the goal instead.
        val goal = StatsCalculator.stageGoal(CetLevel.CET4, totalWords = 10, learnedWords = 0, dailyGoal = 5)
        val negativeLearned = StatsCalculator.stageGoal(CetLevel.CET6, totalWords = 10, learnedWords = -3, dailyGoal = 5)

        assertThat(goal.target).isEqualTo(10)
        assertThat(goal.nextMilestone).isNull()
        assertThat(goal.remaining).isEqualTo(10)
        assertThat(goal.isReached).isFalse()

        // A negative learned count (a corrupted import) must not produce a negative remaining either.
        assertThat(negativeLearned.current).isEqualTo(-3)
        assertThat(negativeLearned.target).isEqualTo(10)
        assertThat(negativeLearned.remaining).isEqualTo(13)
    }

    @Test
    fun `stage goal clamps the daily goal to at least one and keeps the empty list behaviour`() {
        val zeroDaily = StatsCalculator.stageGoal(CetLevel.CET4, totalWords = 0, learnedWords = 0, dailyGoal = 0)
        val negativeDaily = StatsCalculator.stageGoal(CetLevel.CET4, totalWords = 0, learnedWords = 0, dailyGoal = -5)
        val progressed = StatsCalculator.stageGoal(CetLevel.CET4, totalWords = 0, learnedWords = 5, dailyGoal = 3)

        assertThat(zeroDaily.target).isEqualTo(1)
        assertThat(zeroDaily.label).isEqualTo("完成四级词库")
        assertThat(zeroDaily.nextMilestone).isNull()
        assertThat(zeroDaily.isReached).isFalse()
        assertThat(negativeDaily.target).isEqualTo(1)

        assertThat(progressed.target).isEqualTo(3)
        assertThat(progressed.current).isEqualTo(3)
        assertThat(progressed.remaining).isEqualTo(0)
        assertThat(progressed.isReached).isTrue()
    }

    @Test
    fun `stage goal never produces a negative remaining when the list is exhausted`() {
        val over = StatsCalculator.stageGoal(CetLevel.CET4, totalWords = 3849, learnedWords = 5000, dailyGoal = 20)
        val beyondTinyList = StatsCalculator.stageGoal(CetLevel.CET4, totalWords = 100, learnedWords = 250, dailyGoal = 20)

        assertThat(over.nextMilestone).isNull()
        assertThat(over.current).isEqualTo(3849)
        assertThat(over.target).isEqualTo(3849)
        assertThat(over.percent).isEqualTo(100)
        assertThat(over.remaining).isEqualTo(0)
        assertThat(over.isReached).isTrue()

        // Learned more words than a 100-word list holds. The goal must stay inside the list (a
        // 500-word milestone is unreachable here) and nothing may go negative.
        assertThat(beyondTinyList.target).isEqualTo(100)
        assertThat(beyondTinyList.nextMilestone).isNull()
        assertThat(beyondTinyList.current).isEqualTo(100)
        assertThat(beyondTinyList.remaining).isEqualTo(0)
        assertThat(beyondTinyList.isReached).isTrue()
    }

    @Test
    fun `stage goal label names the level and the milestone list is the documented one`() {
        val cet6 = StatsCalculator.stageGoal(CetLevel.CET6, totalWords = 3849, learnedWords = 700, dailyGoal = 20)
        val custom = StatsCalculator.stageGoal(CetLevel.OTHER, totalWords = 300, learnedWords = 60, dailyGoal = 20)

        assertThat(cet6.label).isEqualTo("完成六级词库 · 下一个里程碑 1000 词")
        assertThat(custom.label).isEqualTo("完成自定义词库 · 下一个里程碑 200 词")

        assertThat(VOCABULARY_MILESTONES).containsExactly(50, 200, 500, 1000, 2000).inOrder()
    }

    @Test
    fun `stage goal stays coherent across the whole learned range`() {
        val samples = listOf(0, 1, 49, 50, 51, 199, 200, 201, 499, 500, 501, 999, 1000, 1001, 1999, 2000, 2001, 3849, 5000)

        for (learned in samples) {
            val goal = StatsCalculator.stageGoal(CetLevel.CET4, totalWords = 3849, learnedWords = learned, dailyGoal = 20)

            assertThat(goal.target).isAtLeast(1)
            assertThat(goal.remaining).isAtLeast(0)
            assertThat(goal.percent in 0..100).isTrue()
            assertThat(goal.current).isAtMost(goal.target)
            assertThat(goal.label).startsWith("完成四级词库")
            assertThat(goal.isReached).isEqualTo(learned >= goal.target)
            goal.nextMilestone?.let { milestone ->
                assertThat(milestone).isGreaterThan(learned)
                assertThat(goal.target).isEqualTo(milestone)
            }
        }
    }

    // --- small helpers ------------------------------------------------------------------------

    @Test
    fun `exam average is null without exams and clamped otherwise`() {
        assertThat(StatsCalculator.examAveragePercent(null)).isNull()
        assertThat(StatsCalculator.examAveragePercent(Double.NaN)).isNull()
        assertThat(StatsCalculator.examAveragePercent(72.4)).isEqualTo(72)
        assertThat(StatsCalculator.examAveragePercent(120.0)).isEqualTo(100)
        assertThat(StatsCalculator.examAveragePercent(-5.0)).isEqualTo(0)
    }

    @Test
    fun `millis are converted to whole minutes`() {
        assertThat(StatsCalculator.toMinutes(0L)).isEqualTo(0)
        assertThat(StatsCalculator.toMinutes(59_999L)).isEqualTo(0)
        assertThat(StatsCalculator.toMinutes(60_000L)).isEqualTo(1)
        assertThat(StatsCalculator.toMinutes(3_600_000L)).isEqualTo(60)
    }

    @Test
    fun `level progress percentages are null for an empty list`() {
        val empty = LevelProgress(CetLevel.CET4, totalWords = 0, learnedWords = 0, masteredWords = 0, fragileWords = 0)
        val half = LevelProgress(CetLevel.CET4, totalWords = 200, learnedWords = 100, masteredWords = 50, fragileWords = 10)

        assertThat(empty.learnedPercent).isNull()
        assertThat(empty.masteredPercent).isNull()
        assertThat(half.learnedPercent).isEqualTo(50)
        assertThat(half.masteredPercent).isEqualTo(25)
    }

    @Test
    fun `a day point accuracy is null without review events`() {
        val point = DailyStudyPoint(
            dayKey = today.toString(),
            date = today,
            studiedWords = 3,
            reviewEvents = 0,
            correct = 0,
            fuzzy = 0,
            wrong = 0,
            durationMillis = 0L,
        )

        assertThat(point.accuracyPercent).isNull()
        assertThat(point.hasActivity).isTrue()
    }
}
