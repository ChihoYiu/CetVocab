package com.cetvocab.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.cetvocab.app.data.local.entity.ImportHistoryEntity
import kotlinx.coroutines.flow.Flow

/** Audit trail of imports/exports shown on the data-management screen. */
@Dao
interface ImportHistoryDao {

    @Insert
    suspend fun insert(history: ImportHistoryEntity): Long

    @Query("SELECT * FROM import_history ORDER BY imported_at DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<ImportHistoryEntity>>

    @Query("SELECT * FROM import_history ORDER BY imported_at DESC LIMIT :limit")
    suspend fun recent(limit: Int): List<ImportHistoryEntity>

    @Query("DELETE FROM import_history")
    suspend fun deleteAll()
}
