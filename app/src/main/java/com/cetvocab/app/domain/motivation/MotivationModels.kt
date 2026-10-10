package com.cetvocab.app.domain.motivation

/** The situation the learner is in right now. */
enum class MotivationMoment {
    SESSION_COMPLETED,      // just finished a study session
    NEW_STREAK_RECORD,      // the current streak just became a personal best
    RETURNED_AFTER_BREAK,   // came back after >= 3 days away
    ACCURACY_IMPROVED,      // this week's accuracy is clearly better than last week's
    MILESTONE_REACHED,      // just crossed a vocabulary milestone
    STRUGGLING,             // low accuracy / many mistakes right now
    STEADY,                 // no notable event: the everyday case
}

/**
 * Where a line came from. Everything shipped today is [ORIGINAL].
 *
 * [PUBLIC_DOMAIN] is a reservation, not a feature: a quotation may only be used when its author
 * died more than 70 years ago (or the work is otherwise provably free) and the line names the
 * author and the work. Until such a curated, verifiable collection exists, the app ships no
 * external text at all, so the label of the source is always 原创.
 */
enum class MotivationSource(val label: String) {
    ORIGINAL("原创"),
    PUBLIC_DOMAIN("公共领域");   // reserved: only for authors dead > 70 years, with attribution
}

/**
 * One encouragement line plus the facts needed to render and audit it.
 *
 * [attribution] is non-null only for [MotivationSource.PUBLIC_DOMAIN], where it must read like
 * "作者 · 作品（年份）"; [MotivationEngine.isValid] rejects a public-domain line without one.
 */
data class MotivationMessage(
    val text: String,
    val moment: MotivationMoment,
    val source: MotivationSource = MotivationSource.ORIGINAL,
    /** Non-null only for PUBLIC_DOMAIN: e.g. "作者 · 作品（年份）". */
    val attribution: String? = null,
)

/**
 * Everything the choice may depend on.
 *
 * The fields are deliberately plain counts rather than timestamps: the engine derives its choice
 * from them only, which is what makes the same situation always produce the same line. A null
 * [accuracyPercent] / [accuracyDeltaPercent] means "not measured", not "zero", and a zero
 * [streakDays], [daysSinceLastStudy], [milestoneValue] or [answeredToday] means "nothing to say
 * about it", so the engine quietly uses a line without numbers instead.
 */
data class MotivationContext(
    val moment: MotivationMoment,
    val streakDays: Int = 0,
    val longestStreakDays: Int = 0,
    val daysSinceLastStudy: Int = 0,
    val accuracyPercent: Int? = null,
    val accuracyDeltaPercent: Int? = null,   // this period minus the previous period
    val milestoneValue: Int = 0,             // e.g. 500 words mastered
    val answeredToday: Int = 0,
)
