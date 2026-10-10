package com.cetvocab.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.cetvocab.app.data.local.entity.ExamQuestionEntity
import com.cetvocab.app.data.local.entity.ExamSessionEntity
import kotlinx.coroutines.flow.Flow

/**
 * Closed-book exams: sessions plus their frozen question snapshots.
 *
 * A draft exam is written to the database *before* the first question is displayed, so an
 * exam interrupted by a phone call, a crash or a reboot can be resumed with the same
 * questions and the same remaining time.
 */
@Dao
interface ExamDao {

    @Insert
    suspend fun insertSession(session: ExamSessionEntity): Long

    @Update
    suspend fun updateSession(session: ExamSessionEntity)

    @Insert
    suspend fun insertQuestions(questions: List<ExamQuestionEntity>)

    @Update
    suspend fun updateQuestion(question: ExamQuestionEntity)

    @Query("SELECT * FROM exam_session WHERE id = :id")
    suspend fun sessionById(id: Long): ExamSessionEntity?

    @Query("SELECT * FROM exam_session WHERE status = 'DRAFT' ORDER BY created_at DESC LIMIT 1")
    suspend fun draft(): ExamSessionEntity?

    @Query("SELECT * FROM exam_session WHERE status = 'DRAFT' ORDER BY created_at DESC LIMIT 1")
    fun observeDraft(): Flow<ExamSessionEntity?>

    @Query("SELECT * FROM exam_question WHERE exam_id = :examId ORDER BY order_index ASC")
    suspend fun questions(examId: Long): List<ExamQuestionEntity>

    @Query("SELECT * FROM exam_question WHERE exam_id = :examId AND order_index = :orderIndex")
    suspend fun question(examId: Long, orderIndex: Int): ExamQuestionEntity?

    @Query("SELECT COUNT(*) FROM exam_question WHERE exam_id = :examId AND given_answer IS NOT NULL")
    suspend fun answeredCount(examId: Long): Int

    @Query(
        """
        SELECT * FROM exam_session
        WHERE status = 'SUBMITTED'
        ORDER BY submitted_at DESC LIMIT :limit
        """,
    )
    fun observeHistory(limit: Int): Flow<List<ExamSessionEntity>>

    @Query(
        """
        SELECT * FROM exam_session
        WHERE status = 'SUBMITTED'
        ORDER BY submitted_at DESC LIMIT :limit
        """,
    )
    suspend fun history(limit: Int): List<ExamSessionEntity>

    @Query("SELECT COUNT(*) FROM exam_session WHERE status = 'SUBMITTED'")
    fun observeSubmittedCount(): Flow<Int>

    /**
     * Per-question-type accuracy over the recent exams, used to find a weak question type.
     *
     * Only answered questions count (`is_correct IS NOT NULL`), so an abandoned draft cannot drag a
     * type's accuracy down. The window is passed in as a timestamp and covers submitted exams only.
     */
    @Query(
        """
        SELECT q.type AS type,
               COUNT(*) AS attempts,
               SUM(CASE WHEN q.is_correct = 1 THEN 1 ELSE 0 END) AS correct
        FROM exam_question q
        INNER JOIN exam_session s ON s.id = q.exam_id
        WHERE s.level = :level AND s.status = 'SUBMITTED'
          AND q.is_correct IS NOT NULL AND q.answered_at >= :since
        GROUP BY q.type
        """,
    )
    suspend fun typeAccuracySince(level: String, since: Long): List<ExamTypeAccuracyRow>

    @Query("SELECT AVG(accuracy_percent) FROM exam_session WHERE status = 'SUBMITTED' AND level = :level")
    fun observeAverageAccuracy(level: String): Flow<Double?>

    @Query("SELECT AVG(accuracy_percent) FROM exam_session WHERE status = 'SUBMITTED' AND level = :level")
    suspend fun averageAccuracy(level: String): Double?

    @Query("UPDATE exam_session SET status = 'ABANDONED', submitted_at = :now WHERE id = :id")
    suspend fun abandon(id: Long, now: Long)

    @Query("UPDATE exam_session SET status = 'ABANDONED', submitted_at = :now WHERE status = 'DRAFT'")
    suspend fun abandonAllDrafts(now: Long)

    @Query("DELETE FROM exam_session WHERE status = 'ABANDONED' AND created_at < :before")
    suspend fun purgeOldAbandoned(before: Long): Int

    @Query("DELETE FROM exam_session")
    suspend fun deleteAll()
}
