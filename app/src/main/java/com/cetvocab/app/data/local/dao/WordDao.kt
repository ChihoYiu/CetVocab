package com.cetvocab.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.cetvocab.app.data.local.entity.WordEntity
import com.cetvocab.app.data.local.entity.WordPackEntity
import kotlinx.coroutines.flow.Flow

/**
 * Vocabulary access.
 *
 * Every statement uses bound parameters; no SQL is ever assembled from user input, which is
 * what keeps file imports safe. `LIKE` searches escape the user's wildcards explicitly.
 */
@Dao
interface WordDao {

    // --- Packs ---------------------------------------------------------------------------

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPack(pack: WordPackEntity)

    /** User-imported packs first (that is the content the learner added), built-in packs last. */
    @Query("SELECT * FROM word_pack ORDER BY is_built_in ASC, name ASC")
    fun observePacks(): Flow<List<WordPackEntity>>

    @Query("SELECT * FROM word_pack WHERE id = :id")
    suspend fun packById(id: String): WordPackEntity?

    @Query("UPDATE word_pack SET word_count = :count, updated_at = :now WHERE id = :packId")
    suspend fun updatePackWordCount(packId: String, count: Int, now: Long)

    @Query("SELECT COUNT(*) FROM word_pack WHERE is_built_in = 0")
    suspend fun importedPackCount(): Int

    // --- Words ---------------------------------------------------------------------------

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(word: WordEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(words: List<WordEntity>): List<Long>

    @Update
    suspend fun update(word: WordEntity)

    /**
     * Batched update used by the built-in pack upgrade path.
     *
     * `@Update` writes by primary key, so a word that the learner already has keeps its row id —
     * and with it every progress row, review event, mistake record and exam snapshot that points
     * at it. `@Insert(onConflict = REPLACE)` would delete and re-insert the row and silently
     * destroy all of that, which is why it is never used here.
     */
    @Update
    suspend fun updateAll(words: List<WordEntity>)

    /**
     * Identity map of one level: `normalized_word -> id`.
     *
     * Read once before a pack upgrade so the seeding loop can decide insert-vs-update for
     * thousands of words without a per-word query.
     */
    @Query("SELECT id, normalized_word FROM word WHERE level = :level")
    suspend fun idByNormalized(level: String): List<WordIdKey>

    /** Search the built-in/imported vocabulary by word or meaning (used by the word browser). */
    @Query(
        """
        SELECT * FROM word_with_progress
        WHERE level = :level
          AND (:search = '' OR word LIKE :like ESCAPE '!' OR meanings LIKE :like ESCAPE '!')
        ORDER BY CASE WHEN word LIKE :prefix ESCAPE '!' THEN 0 ELSE 1 END, word ASC
        LIMIT :limit
        """,
    )
    suspend fun search(level: String, search: String, like: String, prefix: String, limit: Int): List<WordWithProgressView>

    /**
     * Candidate rows for the approximate ("你是不是想找…") search.
     *
     * The band is deliberately coarse — only a length window, ordered so the result never depends
     * on row order — because the real filtering is the edit distance computed in
     * `ApproximateWordMatcher`. A `LIKE` cannot do that job: it cannot ask for "within two edits".
     * The window is bounded by [limit], and a typical level has far fewer words in any one window.
     */
    @Query(
        """
        SELECT * FROM word_with_progress
        WHERE level = :level AND length(word) BETWEEN :minLength AND :maxLength
        ORDER BY word ASC
        LIMIT :limit
        """,
    )
    suspend fun byWordLengthBand(
        level: String,
        minLength: Int,
        maxLength: Int,
        limit: Int,
    ): List<WordWithProgressView>

    @Query("SELECT * FROM word WHERE id = :id")
    suspend fun byId(id: Long): WordEntity?

    @Query("SELECT * FROM word WHERE id IN (:ids)")
    suspend fun byIds(ids: List<Long>): List<WordEntity>

    @Query("SELECT * FROM word WHERE level = :level AND normalized_word = :normalized LIMIT 1")
    suspend fun byNormalized(level: String, normalized: String): WordEntity?

    @Query("SELECT * FROM word WHERE level = :level AND normalized_word IN (:normalizedWords)")
    suspend fun byNormalizedWords(level: String, normalizedWords: List<String>): List<WordEntity>

    @Query("SELECT COUNT(*) FROM word WHERE level = :level")
    fun observeCount(level: String): Flow<Int>

    @Query("SELECT COUNT(*) FROM word WHERE level = :level")
    suspend fun count(level: String): Int

    @Query("SELECT COUNT(*) FROM word WHERE level = :level AND pack_id = :packId")
    suspend fun countInPack(level: String, packId: String): Int

    @Query("SELECT id FROM word WHERE level = :level ORDER BY id ASC")
    suspend fun idsForLevel(level: String): List<Long>

    @Query("SELECT * FROM word WHERE level = :level ORDER BY id ASC")
    suspend fun allForLevel(level: String): List<WordEntity>

    @Query("SELECT DISTINCT level FROM word ORDER BY level")
    fun observeLevels(): Flow<List<String>>

    /**
     * Cheap "is there anything to work with" probe used to distinguish an empty state
     * (no words imported yet) from a genuinely finished day.
     */
    @Query("SELECT EXISTS(SELECT 1 FROM word WHERE level = :level LIMIT 1)")
    suspend fun hasWords(level: String): Boolean

    /** Word rows for the exam generator, newest packs included. */
    @Query(
        """
        SELECT id, word, phonetic, part_of_speech, meanings, example_sentence, level
        FROM word WHERE level = :level ORDER BY id ASC
        """,
    )
    suspend fun liteForLevel(level: String): List<WordLiteRow>

    @Query("DELETE FROM word WHERE level = :level AND pack_id = :packId")
    suspend fun deletePackWords(level: String, packId: String)

    @Query("DELETE FROM word WHERE level = :level")
    suspend fun deleteLevel(level: String)
}
