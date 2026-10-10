package com.cetvocab.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A resumable study session.
 *
 * The full queue is persisted as a JSON array of word ids together with the current
 * position, so a session survives process death, an accidental back press or a device
 * reboot without the user losing their place.
 */
@Entity(
    tableName = "study_session",
    indices = [Index("status"), Index("level"), Index("updated_at")],
)
data class StudySessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    @ColumnInfo(name = "level") val level: String,
    /** ACTIVE / COMPLETED / ABANDONED */
    @ColumnInfo(name = "status") val status: String,
    @ColumnInfo(name = "started_at") val startedAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    @ColumnInfo(name = "ended_at") val endedAt: Long? = null,
    @ColumnInfo(name = "planned_new_count") val plannedNewCount: Int,
    @ColumnInfo(name = "planned_review_count") val plannedReviewCount: Int,
    /** JSON array of word ids, in presentation order. */
    @ColumnInfo(name = "queue") val queue: String,
    @ColumnInfo(name = "queue_position") val queuePosition: Int,
    /** JSON array of word ids waiting for an immediate repeat. */
    @ColumnInfo(name = "immediate_queue") val immediateQueue: String,
    /** JSON object: wordId -> how many immediate repeats already happened. */
    @ColumnInfo(name = "immediate_repeat_counts") val immediateRepeatCounts: String,
    @ColumnInfo(name = "answered_count") val answeredCount: Int = 0,
    @ColumnInfo(name = "mastered_count") val masteredCount: Int = 0,
    @ColumnInfo(name = "fuzzy_count") val fuzzyCount: Int = 0,
    @ColumnInfo(name = "unknown_count") val unknownCount: Int = 0,
    @ColumnInfo(name = "new_words_seen") val newWordsSeen: Int = 0,
    @ColumnInfo(name = "review_words_seen") val reviewWordsSeen: Int = 0,
)

/**
 * A closed-book exam. Questions are snapshots: they are written once when the exam starts
 * and never regenerated, so edits to the word list cannot change an exam in progress.
 */
@Entity(
    tableName = "exam_session",
    indices = [Index("status"), Index("level"), Index("created_at")],
)
data class ExamSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    @ColumnInfo(name = "level") val level: String,
    /** DRAFT / SUBMITTED / ABANDONED */
    @ColumnInfo(name = "status") val status: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "started_at") val startedAt: Long,
    @ColumnInfo(name = "submitted_at") val submittedAt: Long? = null,
    @ColumnInfo(name = "question_count") val questionCount: Int,
    @ColumnInfo(name = "duration_minutes") val durationMinutes: Int,
    /** Seconds left when the draft was last persisted (used to resume a paused exam). */
    @ColumnInfo(name = "remaining_seconds") val remainingSeconds: Int,
    @ColumnInfo(name = "score") val score: Int? = null,
    @ColumnInfo(name = "correct_count") val correctCount: Int? = null,
    @ColumnInfo(name = "accuracy_percent") val accuracyPercent: Int? = null,
    @ColumnInfo(name = "time_spent_ms") val timeSpentMs: Long? = null,
    /** JSON array describing per-question-type accuracy. */
    @ColumnInfo(name = "type_breakdown") val typeBreakdown: String? = null,
    @ColumnInfo(name = "seed") val seed: Long,
)

/** One snapshot question of an exam. */
@Entity(
    tableName = "exam_question",
    foreignKeys = [
        ForeignKey(
            entity = ExamSessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["exam_id"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = WordEntity::class,
            parentColumns = ["id"],
            childColumns = ["word_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["exam_id", "order_index"], unique = true),
        Index("word_id"),
        Index("is_correct"),
    ],
)
data class ExamQuestionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    @ColumnInfo(name = "exam_id") val examId: Long,
    @ColumnInfo(name = "order_index") val orderIndex: Int,
    @ColumnInfo(name = "word_id") val wordId: Long,
    /** EN_TO_ZH / ZH_TO_EN / SPELLING */
    @ColumnInfo(name = "type") val type: String,
    /** What the learner sees: an English word or a Chinese meaning. */
    @ColumnInfo(name = "prompt") val prompt: String,
    @ColumnInfo(name = "prompt_phonetic") val promptPhonetic: String = "",
    /** JSON array of options; empty for spelling questions. */
    @ColumnInfo(name = "options") val options: String,
    @ColumnInfo(name = "correct_option_index") val correctOptionIndex: Int,
    /** Canonical correct answer, used for spelling questions and for review. */
    @ColumnInfo(name = "correct_answer") val correctAnswer: String,
    @ColumnInfo(name = "given_answer") val givenAnswer: String? = null,
    @ColumnInfo(name = "is_correct") val isCorrect: Boolean? = null,
    @ColumnInfo(name = "answered_at") val answeredAt: Long? = null,
)

/**
 * Per-day, per-level rollup used by the home dashboard, the statistics screen and the
 * activity calendar. Written inside the same transaction as the answer it belongs to.
 *
 * The primary key is composite because a learner may switch between CET-4 and CET-6 on the
 * same day; the study day starts at 04:00 local time (see `StudyDay`).
 */
@Entity(
    tableName = "daily_study_summary",
    primaryKeys = ["day_key", "level"],
    indices = [Index("day_key"), Index("level")],
)
data class DailyStudySummaryEntity(
    @ColumnInfo(name = "day_key") val dayKey: String,
    @ColumnInfo(name = "level") val level: String,
    @ColumnInfo(name = "studied_words") val studiedWords: Int = 0,
    @ColumnInfo(name = "new_words") val newWords: Int = 0,
    @ColumnInfo(name = "review_words") val reviewWords: Int = 0,
    @ColumnInfo(name = "mastered_count") val masteredCount: Int = 0,
    @ColumnInfo(name = "fuzzy_count") val fuzzyCount: Int = 0,
    @ColumnInfo(name = "unknown_count") val unknownCount: Int = 0,
    @ColumnInfo(name = "review_events") val reviewEvents: Int = 0,
    @ColumnInfo(name = "study_duration_ms") val studyDurationMs: Long = 0L,
    @ColumnInfo(name = "sessions_completed") val sessionsCompleted: Int = 0,
    @ColumnInfo(name = "exams_completed") val examsCompleted: Int = 0,
    @ColumnInfo(name = "exam_correct") val examCorrect: Int = 0,
    @ColumnInfo(name = "exam_total") val examTotal: Int = 0,
    @ColumnInfo(name = "updated_at") val updatedAt: Long = 0L,
)

/** Audit trail of word-list imports and exports, shown in the import screen. */
@Entity(
    tableName = "import_history",
    indices = [Index("imported_at"), Index("status")],
)
data class ImportHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    @ColumnInfo(name = "file_name") val fileName: String,
    /** JSON / CSV */
    @ColumnInfo(name = "format") val format: String,
    @ColumnInfo(name = "imported_at") val importedAt: Long,
    @ColumnInfo(name = "total_rows") val totalRows: Int,
    @ColumnInfo(name = "inserted") val inserted: Int,
    @ColumnInfo(name = "updated") val updated: Int,
    @ColumnInfo(name = "skipped") val skipped: Int,
    @ColumnInfo(name = "failed") val failed: Int,
    @ColumnInfo(name = "level") val level: String,
    @ColumnInfo(name = "pack_id") val packId: String?,
    /** SUCCESS / ROLLED_BACK */
    @ColumnInfo(name = "status") val status: String,
    @ColumnInfo(name = "message") val message: String = "",
)
