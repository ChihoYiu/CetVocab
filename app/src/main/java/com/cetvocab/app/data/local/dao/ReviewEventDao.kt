package com.cetvocab.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.cetvocab.app.data.local.entity.ReviewEventEntity
import kotlinx.coroutines.flow.Flow

/** Answer log: powers statistics, trends, the activity calendar and data export. */
@Dao
interface ReviewEventDao {

    @Insert
    suspend fun insert(event: ReviewEventEntity): Long

    @Insert
    suspend fun insertAll(events: List<ReviewEventEntity>)

    @Query(
        """
        SELECT e.answered_at AS answered_at, e.grade AS grade, e.source AS source,
               w.word AS word, w.meanings AS meanings, w.level AS level
        FROM review_event e JOIN word w ON w.id = e.word_id
        WHERE w.level = :level
        ORDER BY e.answered_at DESC
        LIMIT :limit
        """,
    )
    fun observeRecent(level: String, limit: Int): Flow<List<RecentEventRow>>

    @Query(
        """
        SELECT e.answered_at AS answered_at, e.grade AS grade, e.source AS source,
               w.word AS word, w.meanings AS meanings, w.level AS level
        FROM review_event e JOIN word w ON w.id = e.word_id
        WHERE w.level = :level
        ORDER BY e.answered_at DESC
        LIMIT :limit
        """,
    )
    suspend fun recent(level: String, limit: Int): List<RecentEventRow>

    @Query(
        """
        SELECT day_key AS day_key,
               COUNT(*) AS total,
               SUM(CASE WHEN grade = 'MASTERED' THEN 1 ELSE 0 END) AS correct,
               SUM(CASE WHEN grade = 'FUZZY' THEN 1 ELSE 0 END) AS fuzzy,
               SUM(CASE WHEN grade = 'UNKNOWN' THEN 1 ELSE 0 END) AS wrong
        FROM review_event
        WHERE level = :level AND day_key >= :fromDay
        GROUP BY day_key
        ORDER BY day_key ASC
        """,
    )
    fun observeTrendsSince(level: String, fromDay: String): Flow<List<DailyTrendRow>>

    @Query(
        """
        SELECT day_key AS day_key,
               COUNT(*) AS total,
               SUM(CASE WHEN grade = 'MASTERED' THEN 1 ELSE 0 END) AS correct,
               SUM(CASE WHEN grade = 'FUZZY' THEN 1 ELSE 0 END) AS fuzzy,
               SUM(CASE WHEN grade = 'UNKNOWN' THEN 1 ELSE 0 END) AS wrong
        FROM review_event
        WHERE level = :level AND day_key >= :fromDay
        GROUP BY day_key
        ORDER BY day_key ASC
        """,
    )
    suspend fun trendsSince(level: String, fromDay: String): List<DailyTrendRow>

    @Query("SELECT COUNT(*) FROM review_event WHERE level = :level AND answered_at >= :since")
    suspend fun countSince(level: String, since: Long): Int

    /**
     * How many times this word was already answered on this study day. Used to count
     * *distinct* words studied today without a second aggregation table.
     */
    @Query("SELECT COUNT(*) FROM review_event WHERE word_id = :wordId AND day_key = :dayKey")
    suspend fun countForWordOnDay(wordId: Long, dayKey: String): Int

    @Query("SELECT COUNT(*) FROM review_event WHERE level = :level")
    suspend fun totalCount(level: String): Int

    /**
     * Lapses of **already mastered** words: answers since [since] that were not "已掌握" for a word
     * whose progress row is mastered right now.
     *
     * This is the measurable form of "铭记不牢": a mastered word that comes back as 模糊/不认识 is
     * exactly the forgetting the spaced-repetition schedule is meant to prevent, and it is the
     * signal behind the memory-lapse advice.
     *
     * Note it is a *current-status* join, not a historical one: the database keeps one progress row
     * per word, so a word that lapsed and was later mastered again does not count. That makes this a
     * conservative (never inflated) measure — documented here because the advice text depends on it.
     */
    @Query(
        """
        SELECT COUNT(*) FROM review_event e
        INNER JOIN user_word_progress p ON p.word_id = e.word_id
        WHERE e.level = :level AND e.answered_at >= :since AND e.grade != 'MASTERED'
          AND p.status = 'MASTERED'
        """,
    )
    suspend fun countLapsesOfMasteredWords(level: String, since: Long): Int

    /**
     * Answers since [since] for words that had **no previous interval** — i.e. the first time the
     * learner ever answered that word.
     *
     * `previous_interval_days = 0` is the stored marker for "this word had never been scheduled
     * before", so no extra column is needed. It is a first-exposure count, which is exactly what the
     * "you only study new words and never review" check needs as its numerator.
     */
    @Query(
        """
        SELECT COUNT(*) FROM review_event
        WHERE level = :level AND answered_at >= :since AND previous_interval_days = 0
        """,
    )
    suspend fun countFirstExposureAnswersSince(level: String, since: Long): Int

    @Query("SELECT * FROM review_event WHERE level = :level ORDER BY answered_at ASC")
    suspend fun allForLevel(level: String): List<ReviewEventEntity>

    @Query("DELETE FROM review_event")
    suspend fun deleteAll()

    @Query("DELETE FROM review_event WHERE level = :level")
    suspend fun deleteLevel(level: String)
}
