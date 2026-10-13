package com.cetvocab.app.data.repository

import androidx.room.withTransaction
import com.cetvocab.app.core.json.JsonCodec
import com.cetvocab.app.core.result.AppError
import com.cetvocab.app.core.result.ErrorCode
import com.cetvocab.app.core.result.Outcome
import com.cetvocab.app.core.time.TimeProvider
import com.cetvocab.app.data.local.CetVocabDatabase
import com.cetvocab.app.data.local.dao.WordLiteRow
import com.cetvocab.app.data.local.entity.ImportHistoryEntity
import com.cetvocab.app.data.local.entity.WordEntity
import com.cetvocab.app.data.local.entity.WordPackEntity
import com.cetvocab.app.data.mapper.toDomain
import com.cetvocab.app.data.mapper.toEntity
import com.cetvocab.app.data.mapper.toWordWithProgress
import com.cetvocab.app.data.seed.BuiltInPackDefinition
import com.cetvocab.app.data.seed.BuiltInWordPacks
import com.cetvocab.app.data.seed.PackSource
import com.cetvocab.app.domain.exam.ExamWordSource
import com.cetvocab.app.domain.importer.ImportPreview
import com.cetvocab.app.domain.importer.ImportResult
import com.cetvocab.app.domain.importer.PackFormat
import com.cetvocab.app.domain.model.CetLevel
import com.cetvocab.app.domain.model.Word
import com.cetvocab.app.domain.model.WordWithProgress
import com.cetvocab.app.domain.search.ApproximateWordMatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield

/**
 * Vocabulary content: the built-in demo packs plus everything the user imported.
 *
 * The repository is the only place that knows how a word list is stored, and it is designed
 * around one rule: **importing a list must never destroy study history**. Word rows therefore
 * keep their primary key when their content is updated, and progress rows are never touched
 * by an import.
 */
interface WordRepository {

    /** Seeds the built-in demo packs once; a no-op afterwards. */
    suspend fun ensureBuiltInPacks()

    fun observeWordCount(level: CetLevel): Flow<Int>

    fun observePacks(): Flow<List<WordPackEntity>>

    fun observeImportHistory(limit: Int = 20): Flow<List<ImportHistoryEntity>>

    suspend fun hasWords(level: CetLevel): Boolean

    suspend fun wordDetail(wordId: Long): WordWithProgress?

    /** Snapshot of the words used to build an exam paper. */
    suspend fun examSources(level: CetLevel): List<ExamWordSource>

    /** All words of a level, for export. */
    suspend fun allWords(level: CetLevel): List<Word>

    /**
     * Writes a validated preview.
     *
     * @param packId stable id of the pack (derived from the file name), so importing the same
     *   file twice updates rather than duplicates.
     */
    suspend fun importPack(preview: ImportPreview, packId: String): Outcome<ImportResult>

    /** Removes the words of an imported pack (built-in packs are refused). */
    suspend fun deleteImportedPack(packId: String, level: CetLevel): Outcome<Unit>

    /**
     * Vocabulary lookup for the word browser.
     *
     * Matches the English word and the Chinese meanings; prefix matches on the word sort first so
     * typing "ab" surfaces `abandon` before a word that merely contains "ab" in a meaning.
     */
    suspend fun search(level: CetLevel, query: String, limit: Int = SEARCH_LIMIT): List<WordWithProgress>

    /**
     * Close spellings of [query] — the "你是不是想找" list.
     *
     * Only consulted when the exact search came up short (see [APPROX_STRICT_THRESHOLD]): a learner
     * who already found what they wanted must not be handed a list of unrelated words. Returns an
     * empty list for a query that is not a plain English word, for a level whose length band holds
     * nothing, and for anything the edit distance rejects — never a guess about a word that does
     * not exist in the local vocabulary.
     */
    suspend fun searchApproximate(
        level: CetLevel,
        query: String,
        strictMatchCount: Int,
        limit: Int = APPROX_LIMIT,
    ): List<WordWithProgress>

    companion object {
        /** Bounded result size: the browser pages through the list instead of loading 10k rows. */
        const val SEARCH_LIMIT = 200

        /** At most this many "did you mean" suggestions. */
        const val APPROX_LIMIT = 12

        /**
         * The exact search is considered "good enough" from this many hits upwards.
         *
         * Above it the learner already has something to choose from, and a correction list would
         * only add noise.
         */
        const val APPROX_STRICT_THRESHOLD = 8

        /**
         * Hard cap on the rows the edit-distance pass may see.
         *
         * The length band already keeps this small; the cap is what guarantees that a pathological
         * query cannot turn into a full-table scan in Kotlin.
         */
        const val APPROX_CANDIDATE_LIMIT = 4_000
    }
}

/** Progress of the built-in pack seeding, surfaced on the cold-start screen. */
data class SeedProgress(
    val packName: String,
    val processed: Int,
    val total: Int,
) {
    val fraction: Float get() = if (total <= 0) 0f else (processed.toFloat() / total).coerceIn(0f, 1f)
}

class DefaultWordRepository(
    private val db: CetVocabDatabase,
    private val time: TimeProvider,
    private val packLoader: PackSource,
    /** Reports seeding progress so the cold-start screen can show real numbers. */
    private val onSeedProgress: (SeedProgress) -> Unit = {},
) : WordRepository {

    /**
     * Seeds or upgrades the built-in vocabulary.
     *
     * Why this is not a simple "insert if empty" any more:
     *  * a shipped pack is versioned, and a **newer asset must reach devices that already installed
     *    the app**. The previous implementation only inserted when the pack was empty, so an
     *    existing installation could never receive a bigger word list;
     *  * the word list is now thousands of entries, so the work is chunked (with a `yield()`
     *    between chunks so the loading screen keeps drawing) instead of one long transaction;
     *  * re-seeding must never destroy learning data. Existing rows are matched on
     *    `(level, normalized_word)` and **updated in place**, which keeps the row id every
     *    progress/mistake/event row points at. Nothing is ever deleted here.
     */
    override suspend fun ensureBuiltInPacks() {
        val definitions = runCatching { packLoader.definitions() }.getOrDefault(emptyList())
        if (definitions.isEmpty()) {
            seedInlineDemoPacks()
            return
        }

        definitions.forEach { definition ->
            val existingPack = db.wordDao().packById(definition.id)
            val storedCount = db.wordDao().countInPack(definition.level.id, definition.id)
            val upToDate = existingPack != null &&
                existingPack.version == definition.version.toString() &&
                (definition.expectedWordCount <= 0 || storedCount >= definition.expectedWordCount)
            if (upToDate) return@forEach

            when (val loaded = runCatching { packLoader.load(definition) }.getOrNull()) {
                is Outcome.Success -> seedPack(definition, loaded.value)
                // The asset is missing or invalid: keep the app usable with the in-code demo set
                // for this level rather than leaving the learner with an empty list.
                else -> seedInlineDemoPacks()
            }
        }
    }

    /** Writes one validated pack: insert new words, update existing ones in place. */
    private suspend fun seedPack(definition: BuiltInPackDefinition, preview: ImportPreview) {
        val level = definition.level
        val records = preview.records
        val now = time.nowMillis()

        // One identity lookup for the whole level instead of a query per word.
        val existingIds: Map<String, Long> = db.wordDao().idByNormalized(level.id)
            .associate { it.normalizedWord to it.id }

        var processed = 0
        var inserted = 0
        var updated = 0
        onSeedProgress(SeedProgress(definition.name, 0, records.size))

        records.chunked(SEED_CHUNK_SIZE).forEach { chunk ->
            val toInsert = ArrayList<WordEntity>(chunk.size)
            val toUpdate = ArrayList<WordEntity>(chunk.size)
            chunk.forEach { record ->
                val normalized = Word.normalizeWordKey(record.word)
                val existingId = existingIds[normalized]
                val entity = record.toEntity(definition.id, now)
                if (existingId == null) {
                    toInsert += entity
                } else {
                    toUpdate += entity.copy(id = existingId, createdAt = now)
                }
            }

            db.withTransaction {
                if (toInsert.isNotEmpty()) {
                    db.wordDao().insertAll(toInsert)
                    inserted += toInsert.size
                }
                if (toUpdate.isNotEmpty()) {
                    db.wordDao().updateAll(toUpdate)
                    updated += toUpdate.size
                }
            }

            processed += chunk.size
            onSeedProgress(SeedProgress(definition.name, processed, records.size))
            // Let the loading screen draw between chunks: seeding thousands of rows must not look
            // like a frozen frame.
            yield()
        }

        val existingPack = db.wordDao().packById(definition.id)
        db.wordDao().upsertPack(
            WordPackEntity(
                id = definition.id,
                name = definition.name,
                level = level.id,
                description = "${records.size} 条词汇（$inserted 新增 / $updated 更新）",
                source = records.firstOrNull()?.source.orEmpty().ifBlank { BuiltInWordPacks.SOURCE },
                license = records.firstOrNull()?.license.orEmpty().ifBlank { BuiltInWordPacks.LICENSE },
                version = definition.version.toString(),
                isBuiltIn = true,
                wordCount = db.wordDao().countInPack(level.id, definition.id),
                createdAt = existingPack?.createdAt ?: now,
                updatedAt = now,
            ),
        )
    }

    /**
     * Fallback path: the tiny in-code demo packs.
     *
     * Kept so a build with no bundled asset (or a corrupt one) still starts with a usable word
     * list, and so the demo data that the UI tests rely on keeps working.
     */
    private suspend fun seedInlineDemoPacks() {
        val now = time.nowMillis()
        BuiltInWordPacks.all.forEach { (packId, words) ->
            val level = if (packId.contains("cet6")) CetLevel.CET6 else CetLevel.CET4
            val existingPack = db.wordDao().packById(packId)
            val existingIds: Map<String, Long> = db.wordDao().idByNormalized(level.id)
                .associate { it.normalizedWord to it.id }
            val missing = words.filter { Word.normalizeWordKey(it.word) !in existingIds }
            if (missing.isEmpty() && existingPack != null) return@forEach

            db.withTransaction {
                if (missing.isNotEmpty()) {
                    db.wordDao().insertAll(
                        missing.map { seed ->
                            WordEntity(
                                packId = packId,
                                externalId = null,
                                word = seed.word,
                                normalizedWord = Word.normalizeWordKey(seed.word),
                                phonetic = seed.phonetic,
                                partOfSpeech = seed.partOfSpeech,
                                meanings = JsonCodec.encodeStrings(seed.meanings),
                                exampleSentence = seed.exampleSentence,
                                exampleTranslation = seed.exampleTranslation,
                                collocations = JsonCodec.encodeStrings(seed.collocations),
                                level = level.id,
                                source = BuiltInWordPacks.SOURCE,
                                license = BuiltInWordPacks.LICENSE,
                                audioUri = null,
                                createdAt = now,
                                updatedAt = now,
                            )
                        },
                    )
                }
                db.wordDao().upsertPack(
                    WordPackEntity(
                        id = packId,
                        name = if (level == CetLevel.CET6) "六级演示词库" else "四级演示词库",
                        level = level.id,
                        description = "${words.size} 个自建演示词条",
                        source = BuiltInWordPacks.SOURCE,
                        license = BuiltInWordPacks.LICENSE,
                        version = BuiltInWordPacks.VERSION.toString(),
                        isBuiltIn = true,
                        wordCount = db.wordDao().countInPack(level.id, packId),
                        createdAt = existingPack?.createdAt ?: now,
                        updatedAt = now,
                    ),
                )
            }
        }
    }

    override suspend fun search(
        level: CetLevel,
        query: String,
        limit: Int,
    ): List<WordWithProgress> {
        val trimmed = query.trim()
        val like = likePattern(trimmed)
        val prefix = prefixPattern(trimmed)
        return db.wordDao().search(level.id, trimmed, like, prefix, limit).map { it.toWordWithProgress() }
    }

    override suspend fun searchApproximate(
        level: CetLevel,
        query: String,
        strictMatchCount: Int,
        limit: Int,
    ): List<WordWithProgress> {
        if (strictMatchCount >= WordRepository.APPROX_STRICT_THRESHOLD) return emptyList()
        if (!ApproximateWordMatcher.isApplicable(query)) return emptyList()

        val needle = Word.normalizeWordKey(query)
        if (needle.isEmpty()) return emptyList()
        val maxDistance = ApproximateWordMatcher.maxDistanceFor(needle.length)

        val rows = db.wordDao().byWordLengthBand(
            level = level.id,
            minLength = (needle.length - maxDistance).coerceAtLeast(1),
            maxLength = needle.length + maxDistance,
            limit = WordRepository.APPROX_CANDIDATE_LIMIT,
        )
        if (rows.isEmpty()) return emptyList()

        val byNormalized = rows.associateBy { Word.normalizeWordKey(it.word) }
        val ranked = withContext(Dispatchers.Default) {
            ApproximateWordMatcher.rank(
                query = needle,
                candidates = byNormalized.keys.toList(),
                limit = limit,
                maxDistance = maxDistance,
            )
        }
        return ranked.mapNotNull { match -> byNormalized[match.word]?.toWordWithProgress() }
    }

    /**
     * SQL `LIKE` pattern with `!` as the escape character.
     *
     * A learner typing `%` or `_` must search for those characters literally; without escaping,
     * `%` would match the whole word list.
     */
    private fun likePattern(term: String): String {
        if (term.isEmpty()) return "%"
        val escaped = term.replace("!", "!!").replace("%", "!%").replace("_", "!_")
        return "%$escaped%"
    }

    /** Prefix pattern used to rank word-prefix matches above meaning matches. */
    private fun prefixPattern(term: String): String {
        if (term.isEmpty()) return ""
        val escaped = term.replace("!", "!!").replace("%", "!%").replace("_", "!_")
        return "$escaped%"
    }

    private companion object {
        /**
         * Rows per seeding transaction. Large enough to keep the insert count low, small enough
         * that a `yield()` between chunks lets the loading screen draw and that a cancellation
         * loses at most one chunk.
         */
        const val SEED_CHUNK_SIZE = 400
    }

    override fun observeWordCount(level: CetLevel): Flow<Int> = db.wordDao().observeCount(level.id)

    override fun observePacks(): Flow<List<WordPackEntity>> = db.wordDao().observePacks()

    override fun observeImportHistory(limit: Int): Flow<List<ImportHistoryEntity>> =
        db.importHistoryDao().observeRecent(limit)

    override suspend fun hasWords(level: CetLevel): Boolean = db.wordDao().hasWords(level.id)

    override suspend fun wordDetail(wordId: Long): WordWithProgress? =
        db.progressDao().wordWithProgress(wordId)?.toWordWithProgress()

    override suspend fun examSources(level: CetLevel): List<ExamWordSource> =
        db.wordDao().liteForLevel(level.id).map { it.toExamSource() }

    override suspend fun allWords(level: CetLevel): List<Word> =
        db.wordDao().allForLevel(level.id).map { it.toDomain() }

    override suspend fun importPack(preview: ImportPreview, packId: String): Outcome<ImportResult> {
        val now = time.nowMillis()
        return try {
            val result = db.withTransaction {
                var inserted = 0
                var updated = 0
                preview.records.forEach { record ->
                    val existing = db.wordDao().byNormalized(record.level.id, Word.normalizeWordKey(record.word))
                    if (existing == null) {
                        db.wordDao().insert(record.toEntity(packId, now))
                        inserted++
                    } else {
                        // Content is refreshed, the row id (and with it every progress row,
                        // review event and exam snapshot) is preserved.
                        db.wordDao().update(
                            existing.copy(
                                packId = existing.packId ?: packId,
                                externalId = record.externalId ?: existing.externalId,
                                word = record.word,
                                normalizedWord = Word.normalizeWordKey(record.word),
                                phonetic = record.phonetic,
                                partOfSpeech = record.partOfSpeech,
                                meanings = JsonCodec.encodeStrings(record.meanings),
                                exampleSentence = record.exampleSentence,
                                exampleTranslation = record.exampleTranslation,
                                collocations = JsonCodec.encodeStrings(record.collocations),
                                source = record.source,
                                license = record.license,
                                audioUri = record.audioUri,
                                updatedAt = now,
                            ),
                        )
                        updated++
                    }
                }

                val level = preview.levelCounts.maxByOrNull { it.value }?.key ?: CetLevel.CET4
                val existingPack = db.wordDao().packById(packId)
                db.wordDao().upsertPack(
                    WordPackEntity(
                        id = packId,
                        name = preview.packName,
                        level = level.id,
                        description = "导入自 ${preview.fileName}",
                        source = preview.records.firstOrNull()?.source.orEmpty(),
                        license = preview.records.firstOrNull()?.license.orEmpty(),
                        version = "1.0",
                        isBuiltIn = false,
                        wordCount = db.wordDao().countInPack(level.id, packId),
                        createdAt = existingPack?.createdAt ?: now,
                        updatedAt = now,
                    ),
                )

                ImportResult(
                    fileName = preview.fileName,
                    format = preview.format,
                    packId = packId,
                    inserted = inserted,
                    updated = updated,
                    skipped = preview.duplicateRows,
                    failed = preview.invalidCount,
                    totalRows = preview.totalRows,
                    level = level,
                )
            }

            db.importHistoryDao().insert(
                ImportHistoryEntity(
                    fileName = preview.fileName,
                    format = preview.format.id,
                    importedAt = now,
                    totalRows = result.totalRows,
                    inserted = result.inserted,
                    updated = result.updated,
                    skipped = result.skipped,
                    failed = result.failed,
                    level = result.level.id,
                    packId = packId,
                    status = "SUCCESS",
                    message = "",
                ),
            )
            Outcome.Success(result)
        } catch (throwable: Throwable) {
            // The transaction rolled back, so existing data is untouched. The failure is still
            // recorded, outside the failed transaction, so the user can see what happened.
            runCatching {
                db.importHistoryDao().insert(
                    ImportHistoryEntity(
                        fileName = preview.fileName,
                        format = preview.format.id,
                        importedAt = now,
                        totalRows = preview.totalRows,
                        inserted = 0,
                        updated = 0,
                        skipped = preview.duplicateRows,
                        failed = preview.invalidCount,
                        level = CetLevel.CET4.id,
                        packId = packId,
                        status = "ROLLED_BACK",
                        message = throwable.javaClass.simpleName,
                    ),
                )
            }
            Outcome.Failure(AppError.database(throwable.javaClass.simpleName))
        }
    }

    override suspend fun deleteImportedPack(packId: String, level: CetLevel): Outcome<Unit> = try {
        val pack = db.wordDao().packById(packId)
        when {
            pack == null -> Outcome.Failure(AppError(ErrorCode.DATABASE, "词库不存在或已被删除。"))
            pack.isBuiltIn -> Outcome.Failure(
                AppError(ErrorCode.DATABASE, "内置演示词库不能删除，只能清空学习进度。"),
            )

            else -> {
                db.withTransaction {
                    // Progress, review events and exam snapshots follow the foreign keys.
                    db.wordDao().deletePackWords(level.id, packId)
                }
                Outcome.Success(Unit)
            }
        }
    } catch (throwable: Throwable) {
        Outcome.Failure(AppError.database(throwable.javaClass.simpleName))
    }
}

internal fun WordLiteRow.toExamSource(): ExamWordSource = ExamWordSource(
    wordId = id,
    word = word,
    phonetic = phonetic,
    partOfSpeech = partOfSpeech,
    meanings = JsonCodec.decodeStrings(meanings),
)

/** Format label used by the import history UI. */
internal fun formatLabel(formatId: String): String =
    PackFormat.entries.firstOrNull { it.id == formatId }?.label ?: formatId
