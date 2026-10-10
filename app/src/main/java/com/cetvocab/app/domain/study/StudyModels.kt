package com.cetvocab.app.domain.study

import com.cetvocab.app.domain.model.CetLevel
import com.cetvocab.app.domain.model.Word
import com.cetvocab.app.domain.srs.ReviewGrade
import com.cetvocab.app.domain.srs.WordProgress

/** Everything the study card renders, loaded in one round trip. */
data class StudyCard(
    val word: Word,
    val progress: WordProgress,
    val reason: QueueReason,
    val isNew: Boolean,
)

/** One line of the "最近学习记录" list. */
data class RecentEvent(
    val word: String,
    val meaning: String,
    val grade: ReviewGrade,
    val answeredAt: Long,
)

/**
 * Today's plan, derived from the schedule plus the user's goals.
 *
 * "已学习" counts distinct answer events of the day, and [remainingNew] is derived from the
 * daily new-word goal so the home screen can show an honest remaining number instead of a
 * generic "继续学习" hint.
 */
data class TodayPlan(
    val level: CetLevel,
    val dueCount: Int,
    val fragileCount: Int,
    val newAvailableCount: Int,
    val activeMistakeCount: Int,
    val studiedToday: Int,
    val answeredToday: Int,
    val correctToday: Int,
    val dailyNewGoal: Int,
    val dailyReviewLimit: Int,
) {
    /** New words still available inside today's goal. */
    val remainingNew: Int get() = (dailyNewGoal - studiedToday).coerceAtLeast(0)

    /** How many cards "开始背单词" would queue right now. */
    val plannedCards: Int get() = dueCount.coerceAtMost(dailyReviewLimit) + remainingNew.coerceAtMost(newAvailableCount)

    val doneCards: Int get() = answeredToday.coerceAtMost(plannedCards)

    /** 0f..1f ring progress; 1f when the plan is empty so the ring never looks broken. */
    val progressFraction: Float
        get() = when {
            plannedCards <= 0 -> 1f
            else -> (doneCards.toFloat() / plannedCards).coerceIn(0f, 1f)
        }

    val isFinished: Boolean get() = plannedCards > 0 && doneCards >= plannedCards

    val accuracyTodayPercent: Int?
        get() = if (answeredToday <= 0) null else (correctToday * 100 / answeredToday).coerceIn(0, 100)

    val hasNothingToDo: Boolean
        get() = plannedCards <= 0 && activeMistakeCount <= 0
}

/** Result screen of a finished session. */
data class SessionSummary(
    val sessionId: Long,
    val level: CetLevel,
    val answeredCards: Int,
    val newCards: Int,
    val reviewCards: Int,
    val masteredCards: Int,
    val fuzzyCards: Int,
    val unknownCards: Int,
    val distinctWords: Int,
    val durationMillis: Long,
    val mistakeWordIds: List<Long>,
) {
    val accuracyPercent: Int?
        get() = if (answeredCards <= 0) null else (masteredCards * 100 / answeredCards).coerceIn(0, 100)

    val durationMinutes: Int get() = (durationMillis / 60_000L).toInt()

    val encouragement: String
        get() = when {
            answeredCards == 0 -> "本次没有作答记录"
            accuracyPercent == null -> "已完成本次学习"
            accuracyPercent!! >= 90 -> "表现很好，继续保持"
            accuracyPercent!! >= 70 -> "掌握得不错，再把错词过一遍"
            accuracyPercent!! >= 40 -> "有进步空间，错题已加入错题本"
            else -> "这批词还比较陌生，建议先巩固错题"
        }
}
