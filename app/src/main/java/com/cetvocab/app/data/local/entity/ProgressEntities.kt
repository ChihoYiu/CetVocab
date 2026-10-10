package com.cetvocab.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * The user's spaced-repetition state for one word.
 *
 * The primary key *is* the word id (one row per word), which keeps the "due today" query a
 * single indexed lookup. Enums are stored as stable strings so the database stays readable
 * and forward compatible.
 */
@Entity(
    tableName = "user_word_progress",
    foreignKeys = [
        ForeignKey(
            entity = WordEntity::class,
            parentColumns = ["id"],
            childColumns = ["word_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("word_id"),
        Index("due_at"),
        Index("status"),
        Index("wrong_count"),
        Index("last_reviewed_at"),
        Index("is_favorite"),
    ],
)
data class UserWordProgressEntity(
    @PrimaryKey @ColumnInfo(name = "word_id") val wordId: Long,
    @ColumnInfo(name = "status") val status: String,
    @ColumnInfo(name = "mastery_level") val masteryLevel: Int,
    @ColumnInfo(name = "repetitions") val repetitions: Int,
    @ColumnInfo(name = "interval_days") val intervalDays: Int,
    @ColumnInfo(name = "ease_factor") val easeFactor: Double,
    @ColumnInfo(name = "due_at") val dueAt: Long,
    @ColumnInfo(name = "last_reviewed_at") val lastReviewedAt: Long?,
    @ColumnInfo(name = "last_answer") val lastAnswer: String?,
    @ColumnInfo(name = "lapse_count") val lapseCount: Int,
    @ColumnInfo(name = "wrong_count") val wrongCount: Int,
    @ColumnInfo(name = "fuzzy_count") val fuzzyCount: Int,
    @ColumnInfo(name = "consecutive_correct_count") val consecutiveCorrectCount: Int,
    @ColumnInfo(name = "is_favorite") val isFavorite: Boolean,
    @ColumnInfo(name = "introduced_at") val introducedAt: Long?,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)

/** Immutable log of every answer, used for statistics, trends and the activity calendar. */
@Entity(
    tableName = "review_event",
    foreignKeys = [
        ForeignKey(
            entity = WordEntity::class,
            parentColumns = ["id"],
            childColumns = ["word_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("word_id"),
        Index("answered_at"),
        Index("day_key"),
        Index("grade"),
        Index("source"),
    ],
)
data class ReviewEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    @ColumnInfo(name = "word_id") val wordId: Long,
    @ColumnInfo(name = "session_id") val sessionId: Long? = null,
    /** UNKNOWN / FUZZY / MASTERED */
    @ColumnInfo(name = "grade") val grade: String,
    /** STUDY / IMMEDIATE / REVIEW / EXAM */
    @ColumnInfo(name = "source") val source: String,
    @ColumnInfo(name = "answered_at") val answeredAt: Long,
    /** Study-day bucket (`yyyy-MM-dd`) so daily aggregates need no time-zone maths in SQL. */
    @ColumnInfo(name = "day_key") val dayKey: String,
    @ColumnInfo(name = "level") val level: String,
    @ColumnInfo(name = "previous_interval_days") val previousIntervalDays: Int,
    @ColumnInfo(name = "new_interval_days") val newIntervalDays: Int,
    @ColumnInfo(name = "response_time_ms") val responseTimeMs: Long? = null,
)

/**
 * The wrong-answer book ("错题本").
 *
 * One row per word. `resolved_at` marks a word the user considers re-consolidated; rows are
 * kept afterwards so history filters keep working, and are only deleted after an explicit
 * confirmation in the UI.
 */
@Entity(
    tableName = "wrong_answer",
    foreignKeys = [
        ForeignKey(
            entity = WordEntity::class,
            parentColumns = ["id"],
            childColumns = ["word_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("word_id"),
        Index("last_wrong_at"),
        Index("wrong_count"),
        Index("resolved_at"),
        Index("is_favorite"),
    ],
)
data class WrongAnswerEntity(
    @PrimaryKey @ColumnInfo(name = "word_id") val wordId: Long,
    @ColumnInfo(name = "wrong_count") val wrongCount: Int,
    @ColumnInfo(name = "fuzzy_count") val fuzzyCount: Int,
    @ColumnInfo(name = "first_wrong_at") val firstWrongAt: Long,
    @ColumnInfo(name = "last_wrong_at") val lastWrongAt: Long,
    /** UNKNOWN / FUZZY / EXAM */
    @ColumnInfo(name = "last_source") val lastSource: String,
    /** Consecutive MASTERED answers since the last mistake. */
    @ColumnInfo(name = "correct_since_wrong") val correctSinceWrong: Int,
    @ColumnInfo(name = "resolved_at") val resolvedAt: Long? = null,
    @ColumnInfo(name = "is_favorite") val isFavorite: Boolean = false,
    @ColumnInfo(name = "level") val level: String,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)
