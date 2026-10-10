package com.cetvocab.app.data.local.dao

import androidx.room.ColumnInfo

/**
 * Flat projections returned by the joined queries.
 *
 * Room maps them by column alias, which keeps filtering, ordering and paging inside SQLite
 * (no N+1 queries from the UI) while still exposing plain Kotlin data to the repositories.
 */

/** `wrong_answer` joined with `word` and the current progress — the 错题本 row shape. */
data class WrongAnswerRow(
    @ColumnInfo(name = "word_id") val wordId: Long,
    @ColumnInfo(name = "word") val word: String,
    @ColumnInfo(name = "phonetic") val phonetic: String,
    @ColumnInfo(name = "part_of_speech") val partOfSpeech: String,
    @ColumnInfo(name = "meanings") val meanings: String,
    @ColumnInfo(name = "example_sentence") val exampleSentence: String,
    @ColumnInfo(name = "level") val level: String,
    @ColumnInfo(name = "wrong_count") val wrongCount: Int,
    @ColumnInfo(name = "fuzzy_count") val fuzzyCount: Int,
    @ColumnInfo(name = "first_wrong_at") val firstWrongAt: Long,
    @ColumnInfo(name = "last_wrong_at") val lastWrongAt: Long,
    @ColumnInfo(name = "last_source") val lastSource: String,
    @ColumnInfo(name = "correct_since_wrong") val correctSinceWrong: Int,
    @ColumnInfo(name = "resolved_at") val resolvedAt: Long?,
    @ColumnInfo(name = "favorite") val isFavorite: Int?,
    @ColumnInfo(name = "status") val status: String?,
    @ColumnInfo(name = "mastery_level") val masteryLevel: Int?,
    @ColumnInfo(name = "due_at") val dueAt: Long?,
    @ColumnInfo(name = "last_reviewed_at") val lastReviewedAt: Long?,
    @ColumnInfo(name = "last_answer") val lastAnswer: String?,
)

/** One row of the "最近学习记录" list on the home screen. */
data class RecentEventRow(
    @ColumnInfo(name = "answered_at") val answeredAt: Long,
    @ColumnInfo(name = "grade") val grade: String,
    @ColumnInfo(name = "source") val source: String,
    @ColumnInfo(name = "word") val word: String,
    @ColumnInfo(name = "meanings") val meanings: String,
    @ColumnInfo(name = "level") val level: String,
)

/** Aggregated answer counts for one study day, used for trends and the calendar. */
data class DailyTrendRow(
    @ColumnInfo(name = "day_key") val dayKey: String,
    @ColumnInfo(name = "total") val total: Int,
    @ColumnInfo(name = "correct") val correct: Int,
    @ColumnInfo(name = "fuzzy") val fuzzy: Int,
    @ColumnInfo(name = "wrong") val wrong: Int,
)

/** Totals over a set of daily summaries. */
data class SummaryTotalsRow(
    @ColumnInfo(name = "studied_words") val studiedWords: Int?,
    @ColumnInfo(name = "review_events") val reviewEvents: Int?,
    @ColumnInfo(name = "study_duration_ms") val studyDurationMs: Long?,
    @ColumnInfo(name = "mastered_count") val masteredCount: Int?,
    @ColumnInfo(name = "fuzzy_count") val fuzzyCount: Int?,
    @ColumnInfo(name = "unknown_count") val unknownCount: Int?,
    @ColumnInfo(name = "sessions_completed") val sessionsCompleted: Int?,
    @ColumnInfo(name = "exams_completed") val examsCompleted: Int?,
    @ColumnInfo(name = "exam_correct") val examCorrect: Int?,
    @ColumnInfo(name = "exam_total") val examTotal: Int?,
    @ColumnInfo(name = "active_days") val activeDays: Int?,
)

/** Lightweight word row used by the exam generator and the import preview. */
data class WordLiteRow(
    @ColumnInfo(name = "id") val id: Long,
    @ColumnInfo(name = "word") val word: String,
    @ColumnInfo(name = "phonetic") val phonetic: String,
    @ColumnInfo(name = "part_of_speech") val partOfSpeech: String,
    @ColumnInfo(name = "meanings") val meanings: String,
    @ColumnInfo(name = "example_sentence") val exampleSentence: String,
    @ColumnInfo(name = "level") val level: String,
)

/** Counts grouped by mastery status, used by the statistics screen. */
data class StatusCountRow(
    @ColumnInfo(name = "status") val status: String,
    @ColumnInfo(name = "count") val count: Int,
)

/**
 * Minimal `word` row used to build the `normalized_word -> id` identity map before a built-in
 * pack upgrade, so the seeding loop never issues a query per word.
 */
data class WordIdKey(
    @ColumnInfo(name = "id") val id: Long,
    @ColumnInfo(name = "normalized_word") val normalizedWord: String,
)

/** Per-question-type exam accuracy over a time window, used by the weak-type advice. */
data class ExamTypeAccuracyRow(
    @ColumnInfo(name = "type") val type: String,
    @ColumnInfo(name = "attempts") val attempts: Int,
    @ColumnInfo(name = "correct") val correct: Int?,
)
