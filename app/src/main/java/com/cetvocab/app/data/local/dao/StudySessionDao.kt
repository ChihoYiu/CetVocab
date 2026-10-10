package com.cetvocab.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.cetvocab.app.data.local.entity.StudySessionEntity
import kotlinx.coroutines.flow.Flow

/** Persisted study sessions, so an interrupted session can always be resumed. */
@Dao
interface StudySessionDao {

    @Insert
    suspend fun insert(session: StudySessionEntity): Long

    @Update
    suspend fun update(session: StudySessionEntity)

    @Query("SELECT * FROM study_session WHERE id = :id")
    suspend fun byId(id: Long): StudySessionEntity?

    @Query("SELECT * FROM study_session WHERE status = 'ACTIVE' ORDER BY updated_at DESC LIMIT 1")
    fun observeActive(): Flow<StudySessionEntity?>

    @Query("SELECT * FROM study_session WHERE status = 'ACTIVE' ORDER BY updated_at DESC LIMIT 1")
    suspend fun active(): StudySessionEntity?

    @Query(
        """
        SELECT * FROM study_session
        WHERE level = :level AND status = 'ACTIVE'
        ORDER BY updated_at DESC LIMIT 1
        """,
    )
    suspend fun activeForLevel(level: String): StudySessionEntity?

    @Query("UPDATE study_session SET status = :status, ended_at = :endedAt, updated_at = :endedAt WHERE id = :id")
    suspend fun close(id: Long, status: String, endedAt: Long)

    @Query("UPDATE study_session SET status = 'ABANDONED', ended_at = :now, updated_at = :now WHERE status = 'ACTIVE'")
    suspend fun abandonActive(now: Long)

    @Query("SELECT COUNT(*) FROM study_session WHERE status = 'COMPLETED'")
    suspend fun completedCount(): Int

    @Query("SELECT * FROM study_session ORDER BY started_at DESC LIMIT :limit")
    suspend fun history(limit: Int): List<StudySessionEntity>

    @Query("DELETE FROM study_session")
    suspend fun deleteAll()
}
