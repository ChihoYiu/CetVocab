package com.cetvocab.app.data.local.dao

import androidx.room.ColumnInfo
import androidx.room.DatabaseView

/**
 * `word` LEFT JOIN `user_word_progress`.
 *
 * Exposing the join as a database *view* keeps every queue/list query short and consistent
 * (one definition of "a word with its progress"), and SQLite views work on API 26.
 * Progress columns are nullable because a freshly imported word has no progress row yet —
 * that state *is* the "new word" state.
 */
@DatabaseView(
    viewName = "word_with_progress",
    value = """
        SELECT
            w.id                AS word_id,
            w.word              AS word,
            w.phonetic          AS phonetic,
            w.part_of_speech    AS part_of_speech,
            w.meanings          AS meanings,
            w.example_sentence  AS example_sentence,
            w.example_translation AS example_translation,
            w.collocations      AS collocations,
            w.level             AS level,
            w.source            AS source,
            w.license           AS license,
            w.audio_uri         AS audio_uri,
            w.pack_id           AS pack_id,
            p.status            AS status,
            p.mastery_level     AS mastery_level,
            p.repetitions       AS repetitions,
            p.interval_days     AS interval_days,
            p.ease_factor       AS ease_factor,
            p.due_at            AS due_at,
            p.last_reviewed_at  AS last_reviewed_at,
            p.last_answer       AS last_answer,
            p.lapse_count       AS lapse_count,
            p.wrong_count       AS wrong_count,
            p.fuzzy_count       AS fuzzy_count,
            p.consecutive_correct_count AS consecutive_correct_count,
            p.is_favorite       AS is_favorite,
            p.introduced_at     AS introduced_at
        FROM word w
        LEFT JOIN user_word_progress p ON p.word_id = w.id
    """,
)
data class WordWithProgressView(
    @ColumnInfo(name = "word_id") val wordId: Long,
    @ColumnInfo(name = "word") val word: String,
    @ColumnInfo(name = "phonetic") val phonetic: String,
    @ColumnInfo(name = "part_of_speech") val partOfSpeech: String,
    @ColumnInfo(name = "meanings") val meanings: String,
    @ColumnInfo(name = "example_sentence") val exampleSentence: String,
    @ColumnInfo(name = "example_translation") val exampleTranslation: String,
    @ColumnInfo(name = "collocations") val collocations: String,
    @ColumnInfo(name = "level") val level: String,
    @ColumnInfo(name = "source") val source: String,
    @ColumnInfo(name = "license") val license: String,
    @ColumnInfo(name = "audio_uri") val audioUri: String?,
    @ColumnInfo(name = "pack_id") val packId: String?,
    @ColumnInfo(name = "status") val status: String?,
    @ColumnInfo(name = "mastery_level") val masteryLevel: Int?,
    @ColumnInfo(name = "repetitions") val repetitions: Int?,
    @ColumnInfo(name = "interval_days") val intervalDays: Int?,
    @ColumnInfo(name = "ease_factor") val easeFactor: Double?,
    @ColumnInfo(name = "due_at") val dueAt: Long?,
    @ColumnInfo(name = "last_reviewed_at") val lastReviewedAt: Long?,
    @ColumnInfo(name = "last_answer") val lastAnswer: String?,
    @ColumnInfo(name = "lapse_count") val lapseCount: Int?,
    @ColumnInfo(name = "wrong_count") val wrongCount: Int?,
    @ColumnInfo(name = "fuzzy_count") val fuzzyCount: Int?,
    @ColumnInfo(name = "consecutive_correct_count") val consecutiveCorrectCount: Int?,
    @ColumnInfo(name = "is_favorite") val isFavorite: Int?,
    @ColumnInfo(name = "introduced_at") val introducedAt: Long?,
)
