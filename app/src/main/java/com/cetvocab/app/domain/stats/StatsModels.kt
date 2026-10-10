package com.cetvocab.app.domain.stats

import com.cetvocab.app.domain.model.CetLevel
import com.cetvocab.app.domain.srs.MasteryStatus
import java.time.LocalDate

/** One day of the study calendar / trend chart, with empty days filled in as zeros. */
data class DailyStudyPoint(
    val dayKey: String,
    val date: LocalDate,
    val studiedWords: Int,
    val reviewEvents: Int,
    val correct: Int,
    val fuzzy: Int,
    val wrong: Int,
    val durationMillis: Long,
) {
    val durationMinutes: Int get() = (durationMillis / 60_000L).toInt()
    val hasActivity: Boolean get() = studiedWords > 0 || reviewEvents > 0
    val accuracyPercent: Int?
        get() = if (reviewEvents == 0) null else (correct * 100) / reviewEvents
}

/** Per-day aggregate straight from the database (daily summary rows). */
data class DailySummaryInput(
    val dayKey: String,
    val studiedWords: Int,
    val newWords: Int,
    val reviewWords: Int,
    val masteredCount: Int,
    val fuzzyCount: Int,
    val unknownCount: Int,
    val reviewEvents: Int,
    val durationMillis: Long,
    val sessionsCompleted: Int,
    val examsCompleted: Int,
    val examCorrect: Int,
    val examTotal: Int,
)

/** Per-day answer counts straight from the review event log. */
data class DailyTrendInput(
    val dayKey: String,
    val total: Int,
    val correct: Int,
    val fuzzy: Int,
    val wrong: Int,
)

/** How far the learner is through the selected vocabulary list. */
data class LevelProgress(
    val level: CetLevel,
    val totalWords: Int,
    val learnedWords: Int,
    val masteredWords: Int,
    val fragileWords: Int,
) {
    /** Null when the list is empty, so the UI can show an empty state instead of "0%". */
    val learnedPercent: Int? get() = if (totalWords == 0) null else learnedWords * 100 / totalWords
    val masteredPercent: Int? get() = if (totalWords == 0) null else masteredWords * 100 / totalWords
}

/** Vocabulary milestones a learner can actually reach. */
val VOCABULARY_MILESTONES = listOf(50, 200, 500, 1000, 2000)

/**
 * A gentle, non-pressuring stage goal.
 *
 * The goal is normally the next [VOCABULARY_MILESTONES] value rather than the whole list, so that
 * a learner at 320 words sees "还差 180 词" instead of "还差 3529 词". When every milestone has
 * been passed the goal falls back to the whole list ([nextMilestone] is then null), which keeps the
 * long-run "finish the list" idea without ever showing the whole list as a beginner's first step.
 */
data class StageGoal(
    val label: String,
    val current: Int,
    val target: Int,
    /** The next milestone value, or null when every milestone has been passed. */
    val nextMilestone: Int? = null,
    /** True when [current] has reached or passed `target`. */
    val isReached: Boolean = false,
) {
    val percent: Int get() = if (target <= 0) 0 else (current * 100 / target).coerceIn(0, 100)
    val remaining: Int get() = (target - current).coerceAtLeast(0)
}

/**
 * Everything the statistics screen renders.
 *
 * Percentage fields are nullable on purpose: "no data yet" and "0%" are very different
 * messages, and the UI never shows a discouraging zero when nothing has been studied.
 */
data class StatsSnapshot(
    val levelProgress: LevelProgress,
    val totalLearnedWords: Int,
    val totalReviewEvents: Int,
    val masteredCount: Int,
    val fragileCount: Int,
    val favoriteCount: Int,
    val activeMistakeCount: Int,
    val highMistakeCount: Int,
    val streakDays: Int,
    val longestStreakDays: Int,
    val todayMinutes: Int,
    val totalMinutes: Int,
    val accuracyPercent: Int?,
    val reviewCompletionPercent: Int?,
    val examCount: Int,
    val examAveragePercent: Int?,
    val weekly: List<DailyStudyPoint>,
    val monthly: List<DailyStudyPoint>,
    val calendar: List<DailyStudyPoint>,
    val statusCounts: Map<MasteryStatus, Int>,
    val stageGoal: StageGoal,
) {
    val hasAnyData: Boolean
        get() = totalReviewEvents > 0 || totalLearnedWords > 0 || examCount > 0
}
