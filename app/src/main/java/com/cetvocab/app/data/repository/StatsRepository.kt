package com.cetvocab.app.data.repository

import com.cetvocab.app.core.time.StudyDay
import com.cetvocab.app.core.time.TimeProvider
import com.cetvocab.app.data.local.CetVocabDatabase
import com.cetvocab.app.data.mapper.toInput
import com.cetvocab.app.data.settings.UserSettings
import com.cetvocab.app.domain.model.CetLevel
import com.cetvocab.app.domain.srs.MasteryStatus
import com.cetvocab.app.domain.stats.DailyStudyPoint
import com.cetvocab.app.domain.stats.DailySummaryInput
import com.cetvocab.app.domain.stats.DailyTrendInput
import com.cetvocab.app.domain.stats.LevelProgress
import com.cetvocab.app.domain.stats.StatsCalculator
import com.cetvocab.app.domain.stats.StatsSnapshot
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.LocalDate

/**
 * Statistics and the learning calendar.
 *
 * Aggregation happens in SQL (`GROUP BY day_key`, `COUNT`, `SUM`) and the *rules* — streaks,
 * percentages, calendar filling — are pure functions in `StatsCalculator`, so the numbers can
 * be verified by unit tests instead of by looking at a screen.
 */
interface StatsRepository {

    fun observeStats(level: CetLevel, settings: UserSettings): Flow<StatsSnapshot>

    /** One shot variant used by export and by tests. */
    suspend fun stats(level: CetLevel, settings: UserSettings): StatsSnapshot
}

class DefaultStatsRepository(
    private val db: CetVocabDatabase,
    private val time: TimeProvider,
) : StatsRepository {

    override fun observeStats(level: CetLevel, settings: UserSettings): Flow<StatsSnapshot> {
        val zone = time.zone()
        val now = time.nowMillis()
        val today = StudyDay.localDate(now, zone)
        val yearAgoKey = today.minusDays(CALENDAR_DAYS - 1).toString()

        val core = combine(
            db.dailySummaryDao().observeSince(level.id, yearAgoKey),
            db.reviewEventDao().observeTrendsSince(level.id, yearAgoKey),
            db.progressDao().observeStatusCounts(level.id),
            db.wordDao().observeCount(level.id),
            db.progressDao().observeDueCount(level.id, now),
        ) { summaries, trends, statuses, wordCount, dueCount ->
            CoreStats(
                summaries = summaries.map { it.toInput() },
                trends = trends.map { DailyTrendInput(it.dayKey, it.total, it.correct, it.fuzzy, it.wrong) },
                statusCounts = statuses.associate { row ->
                    com.cetvocab.app.data.mapper.masteryStatusFromId(row.status) to row.count
                },
                wordCount = wordCount,
                dueCount = dueCount,
            )
        }

        return combine(
            core,
            db.mistakeBookDao().observeActiveCount(level.id),
            db.mistakeBookDao().observeHighWrongCount(level.id, DefaultMistakeRepository.HIGH_WRONG_THRESHOLD),
            db.progressDao().observeFavoriteCount(level.id),
            db.examDao().observeSubmittedCount(),
        ) { coreStats, activeMistakes, highMistakes, favorites, examCount ->
            Partial(
                core = coreStats,
                activeMistakes = activeMistakes,
                highMistakes = highMistakes,
                favorites = favorites,
                examCount = examCount,
                examAverage = null,
            )
        }.combine(db.examDao().observeAverageAccuracy(level.id)) { partial, average ->
            partial.copy(examAverage = StatsCalculator.examAveragePercent(average))
        }.map { partial ->
            build(partial, level, settings, today)
        }
    }

    override suspend fun stats(level: CetLevel, settings: UserSettings): StatsSnapshot {
        val zone = time.zone()
        val now = time.nowMillis()
        val today = StudyDay.localDate(now, zone)
        val yearAgoKey = today.minusDays(CALENDAR_DAYS - 1).toString()

        val core = CoreStats(
            summaries = db.dailySummaryDao().since(level.id, yearAgoKey).map { it.toInput() },
            trends = db.reviewEventDao().trendsSince(level.id, yearAgoKey)
                .map { DailyTrendInput(it.dayKey, it.total, it.correct, it.fuzzy, it.wrong) },
            statusCounts = db.progressDao().observeStatusCounts(level.id).first().associate {
                com.cetvocab.app.data.mapper.masteryStatusFromId(it.status) to it.count
            },
            wordCount = db.wordDao().count(level.id),
            dueCount = db.progressDao().dueCount(level.id, now),
        )
        val partial = Partial(
            core = core,
            activeMistakes = db.mistakeBookDao().activeCount(level.id),
            highMistakes = 0,
            favorites = db.progressDao().observeFavoriteCount(level.id).first(),
            examCount = db.examDao().observeSubmittedCount().first(),
            examAverage = StatsCalculator.examAveragePercent(db.examDao().averageAccuracy(level.id)),
        )
        return build(partial, level, settings, today)
    }

    private fun build(
        partial: Partial,
        level: CetLevel,
        settings: UserSettings,
        today: LocalDate,
    ): StatsSnapshot {
        val core = partial.core
        val calendarFrom = today.minusDays(CALENDAR_DAYS - 1)
        val calendar = StatsCalculator.buildSeries(core.summaries, core.trends, calendarFrom, today)
        val monthly = calendar.takeLast(30)
        val weekly = calendar.takeLast(7)

        val activeDays = calendar.filter { it.hasActivity }.map { it.date }
        val totalReviewEvents = calendar.sumOf { it.reviewEvents }
        val totalCorrect = calendar.sumOf { it.correct }
        val totalMinutes = StatsCalculator.toMinutes(calendar.sumOf { it.durationMillis })
        val todayPoint: DailyStudyPoint = calendar.lastOrNull() ?: DailyStudyPoint(
            dayKey = today.toString(),
            date = today,
            studiedWords = 0,
            reviewEvents = 0,
            correct = 0,
            fuzzy = 0,
            wrong = 0,
            durationMillis = 0L,
        )

        val learned = core.statusCounts.entries
            .filter { it.key != MasteryStatus.NEW }
            .sumOf { it.value }
        val mastered = core.statusCounts[MasteryStatus.MASTERED] ?: 0
        val fragile = (core.statusCounts[MasteryStatus.LEARNING] ?: 0) +
            (core.statusCounts[MasteryStatus.RELEARNING] ?: 0)

        return StatsSnapshot(
            levelProgress = LevelProgress(
                level = level,
                totalWords = core.wordCount,
                learnedWords = learned,
                masteredWords = mastered,
                fragileWords = fragile,
            ),
            totalLearnedWords = learned,
            totalReviewEvents = totalReviewEvents,
            masteredCount = mastered,
            fragileCount = fragile,
            favoriteCount = partial.favorites,
            activeMistakeCount = partial.activeMistakes,
            highMistakeCount = partial.highMistakes,
            streakDays = StatsCalculator.currentStreak(activeDays, today),
            longestStreakDays = StatsCalculator.longestStreak(activeDays),
            todayMinutes = todayPoint.durationMinutes,
            totalMinutes = totalMinutes,
            accuracyPercent = StatsCalculator.percent(totalCorrect, totalReviewEvents),
            reviewCompletionPercent = StatsCalculator.reviewCompletion(
                reviewedToday = todayPoint.reviewEvents,
                stillDue = core.dueCount,
            ),
            examCount = partial.examCount,
            examAveragePercent = partial.examAverage,
            weekly = weekly,
            monthly = monthly,
            calendar = calendar,
            statusCounts = core.statusCounts,
            stageGoal = StatsCalculator.stageGoal(
                level = level,
                totalWords = core.wordCount,
                learnedWords = learned,
                dailyGoal = settings.dailyNewWordGoal,
            ),
        )
    }

    private data class CoreStats(
        val summaries: List<DailySummaryInput>,
        val trends: List<DailyTrendInput>,
        val statusCounts: Map<MasteryStatus, Int>,
        val wordCount: Int,
        val dueCount: Int,
    )

    private data class Partial(
        val core: CoreStats,
        val activeMistakes: Int,
        val highMistakes: Int,
        val favorites: Int,
        val examCount: Int,
        val examAverage: Int?,
    )

    companion object {
        /** 12 weeks of calendar data: enough for a term, small enough to render instantly. */
        const val CALENDAR_DAYS = 84L
    }
}
