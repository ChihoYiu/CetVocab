package com.cetvocab.app.domain.insight

/**
 * The kind of learning observation an [Insight] carries.
 *
 * Every kind maps to one rule in [InsightEngine] and to one actionable message. The
 * enumeration is the stable identity of a rule: the wording, thresholds and severities
 * may be tuned over time, but a kind never changes meaning, so callers can filter or
 * de-duplicate insights without matching on Chinese text.
 */
enum class InsightKind {
    /** R1: almost all recent answers were brand new words while reviews are piling up. */
    NEW_WORDS_WITHOUT_REVIEW,

    /** R2: accuracy over the last week fell noticeably against the week before. */
    ACCURACY_DROP,

    /** R3: one exam question type is clearly weaker than all the others. */
    WEAK_QUESTION_TYPE,

    /** R4: too many mastered words slipped back to fuzzy/unknown. */
    MEMORY_LAPSE,

    /** R5: study days became sparse even though the learner was recently consistent. */
    FREQUENCY_DROP,

    /** R6: long daily sessions that are no longer producing better accuracy. */
    SESSION_TOO_LONG,

    /** R7: one group of words keeps coming back as mistakes. */
    WEAK_WORD_GROUP,

    /** R8: not enough history yet for any of the rules above. */
    INSUFFICIENT_DATA,
}

/**
 * A concrete, actionable observation. Never shown without evidence.
 *
 * The layer deliberately separates the three text fields from the numbers:
 * [detail] must repeat the real measured values so the learner can verify the claim, and
 * [evidence] carries the same numbers in machine-readable form so the UI can show a
 * breakdown, sort, or deep-link without re-parsing Chinese sentences.
 */
data class Insight(
    /** Which rule produced this insight. */
    val kind: InsightKind,
    /** One short sentence, <= 20 chars, e.g. "只学新词，缺少复习". */
    val title: String,
    /** The sentence with real numbers, e.g. "你连续 3 天只学新词，建议今天先花 10 分钟复习 12 个到期词。". */
    val detail: String,
    /** e.g. "去复习这 12 个词"; null when the insight has no single obvious action. */
    val actionLabel: String?,
    /** The measured numbers behind it, e.g. mapOf("dueToday" to 12). */
    val evidence: Map<String, Int>,
    /** Higher = more important; used for ordering. */
    val severity: Int,
)

/** Per-question-type exam accuracy, already computed by the caller. */
data class ExamTypeAccuracy(
    /** Human readable question-type name, e.g. "听力". */
    val label: String,
    /** How many questions of this type were answered in the window. */
    val attempts: Int,
    /** Integer percentage in 0..100. */
    val accuracyPercent: Int,
)

/**
 * The weakest group of repeatedly-wrong words.
 *
 * [wordIds] exists so the action button can open exactly these words; the count is kept
 * separate because the UI shows it while the ids are only used for navigation.
 */
data class MistakeGroup(
    /** Human readable group name, e.g. "高频错词". */
    val label: String,
    val wordCount: Int,
    val wordIds: List<Long>,
)

/**
 * Everything the rules may look at. All values are already computed; this layer does no I/O.
 *
 * The caller (a ViewModel) owns the database queries and the windowing; this layer only
 * interprets the resulting numbers. That split is what makes every rule here a pure
 * function that can be tested with plain literals and no clock, database or Android
 * framework. Optional fields are nullable on purpose: `null` means "not measurable yet",
 * which is different from `0`.
 */
data class LearningSignals(
    /** Lifetime answer events; the only signal that says "we have enough history at all". */
    val totalAnswerEvents: Int,
    /** Lifetime distinct study days. */
    val totalActiveDays: Int,
    /** All answer events in the last 7 days (new words and reviews). */
    val answersLast7Days: Int,
    /** Percent, null when no answers. */
    val accuracyLast7Days: Int?,
    /** Percent, null when no answers in the previous week. */
    val accuracyPrevious7Days: Int?,
    /** Of [answersLast7Days], how many were first-time answers on a new word. */
    val newWordAnswersLast7Days: Int,
    /** Distinct study days in the last 7 days. */
    val activeDaysLast7Days: Int,
    /** Distinct study days in the previous 14 days (excluding the last 7). */
    val activeDaysPrevious14Days: Int,
    /** Median minutes per study day over the last 7 days. */
    val medianMinutesPerDayLast7Days: Int,
    /** Words scheduled for review today. */
    val dueToday: Int,
    /** Words currently in a fragile (fuzzy/unknown) state. */
    val fragileCount: Int,
    /** Words currently considered mastered. */
    val masteredCount: Int,
    /** Mastered words that fell back to fuzzy/unknown in the last 14 days. */
    val masteredLapsedLast14Days: Int,
    /** Per-question-type exam accuracy for the last 30 days. */
    val examTypesLast30Days: List<ExamTypeAccuracy> = emptyList(),
    /** The group of repeatedly-wrong words, when one exists. */
    val weakestMistakeGroup: MistakeGroup? = null,
)
