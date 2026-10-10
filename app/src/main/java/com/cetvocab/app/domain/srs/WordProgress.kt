package com.cetvocab.app.domain.srs

/**
 * The three answers a learner can give on the study card.
 *
 * The names are intentionally about the *learner's* self-assessment, not about correctness
 * of an exam answer: "不认识 / 模糊 / 已掌握".
 */
enum class ReviewGrade {
    /** 不认识 — the word was not recognised at all. */
    UNKNOWN,

    /** 模糊 — recognised but recalled slowly or partially. */
    FUZZY,

    /** 已掌握 — recalled confidently and correctly. */
    MASTERED;

    companion object {
        /** Grade derived from a closed-book exam answer. */
        fun fromExam(correct: Boolean): ReviewGrade = if (correct) MASTERED else UNKNOWN
    }
}

/**
 * Lifecycle of a single word for one user.
 *
 * [NEW] never reviewed · [LEARNING] inside the short-term steps (interval < 1 day)
 * · [REVIEW] graduated, scheduled days ahead · [RELEARNING] lapsed after graduating
 * · [MASTERED] consistently recalled with a long interval.
 */
enum class MasteryStatus {
    NEW,
    LEARNING,
    RELEARNING,
    REVIEW,
    MASTERED;

    /** Human readable Chinese label used across the UI. */
    val label: String
        get() = when (this) {
            NEW -> "未学习"
            LEARNING -> "学习中"
            RELEARNING -> "待巩固"
            REVIEW -> "复习中"
            MASTERED -> "已掌握"
        }

    /** MASTERED and REVIEW count as "consolidated"; everything else is still fragile. */
    val isConsolidated: Boolean get() = this == MASTERED || this == REVIEW
}

/**
 * Pure, storage independent view of one user's progress on one word.
 *
 * This is the input/output type of [SpacedRepetition]: keeping it free of Room annotations
 * means the algorithm can be unit tested without an Android device or a database.
 */
data class WordProgress(
    val wordId: Long,
    val status: MasteryStatus = MasteryStatus.NEW,
    /** 0..5, a coarse "how well do I know this word" bucket used for filtering and sorting. */
    val masteryLevel: Int = 0,
    val repetitions: Int = 0,
    val intervalDays: Int = 0,
    val easeFactor: Double = SpacedRepetition.DEFAULT_EASE,
    val dueAt: Long = 0L,
    val lastReviewedAt: Long? = null,
    val lastAnswer: ReviewGrade? = null,
    val lapseCount: Int = 0,
    val wrongCount: Int = 0,
    val fuzzyCount: Int = 0,
    val consecutiveCorrectCount: Int = 0,
    val isFavorite: Boolean = false,
    /** 1-based order in which this word was first introduced; keeps sessions reproducible. */
    val introducedAt: Long? = null,
)
