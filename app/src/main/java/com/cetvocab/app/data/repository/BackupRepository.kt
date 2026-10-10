package com.cetvocab.app.data.repository

import androidx.room.withTransaction
import com.cetvocab.app.core.json.JsonCodec
import com.cetvocab.app.core.result.AppError
import com.cetvocab.app.core.result.ErrorCode
import com.cetvocab.app.core.result.Outcome
import com.cetvocab.app.core.time.TimeProvider
import com.cetvocab.app.data.local.CetVocabDatabase
import com.cetvocab.app.data.local.entity.DailyStudySummaryEntity
import com.cetvocab.app.data.mapper.toDomain
import com.cetvocab.app.domain.importer.CsvCodec
import com.cetvocab.app.domain.model.CetLevel
import com.cetvocab.app.domain.model.Word
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Which data a user wants to export. */
enum class ExportScope(val label: String, val description: String) {
    WORD_LIST("当前词库", "仅导出词条：单词、音标、释义、例句、搭配"),
    STUDY_RECORDS("学习记录", "导出掌握状态、复习计划、错题与每日统计"),
    FULL_BACKUP("完整备份", "词库 + 学习记录 + 错题 + 答题日志，可完整恢复"),
}

/** Counts reported after a restore. */
data class RestoreResult(
    val restoredWords: Int,
    val restoredProgress: Int,
    val restoredMistakes: Int,
    val restoredEvents: Int,
    val restoredDays: Int,
    val skippedWords: Int,
) {
    val summary: String
        get() = buildString {
            append("已恢复 $restoredProgress 条学习进度、$restoredMistakes 条错题、$restoredDays 天统计")
            if (skippedWords > 0) append("；$skippedWords 个单词在当前词库中不存在，已跳过")
        }
}

/**
 * Export and restore.
 *
 * Writing is always done through the Storage Access Framework by the caller: this class only
 * produces and consumes *text*, so it never needs a storage permission and never decides
 * where a file goes. Restoring replaces study data for the levels present in the backup,
 * which is stated in the confirmation dialog before it runs.
 */
interface BackupRepository {

    suspend fun export(scope: ExportScope, level: CetLevel): Outcome<String>

    /** File name suggestion shown in the system save dialog. */
    fun suggestedFileName(scope: ExportScope, level: CetLevel): String

    suspend fun restore(content: String): Outcome<RestoreResult>

    /** Deletes every learning result but keeps vocabulary and settings. */
    suspend fun clearStudyProgress()

    /** Deletes everything, including imported vocabulary (built-in packs are re-seeded). */
    suspend fun clearAllData()
}

class DefaultBackupRepository(
    private val db: CetVocabDatabase,
    private val time: TimeProvider,
) : BackupRepository {

    override fun suggestedFileName(scope: ExportScope, level: CetLevel): String {
        val stamp = DateTimeFormatter.ofPattern("yyyyMMdd-HHmm")
            .withZone(ZoneId.systemDefault())
            .format(Instant.ofEpochMilli(time.nowMillis()))
        return when (scope) {
            ExportScope.WORD_LIST -> "cetvocab-${level.id.lowercase()}-words-$stamp.csv"
            ExportScope.STUDY_RECORDS -> "cetvocab-${level.id.lowercase()}-records-$stamp.json"
            ExportScope.FULL_BACKUP -> "cetvocab-backup-$stamp.json"
        }
    }

    override suspend fun export(scope: ExportScope, level: CetLevel): Outcome<String> = try {
        val now = time.nowMillis()
        val payload = when (scope) {
            ExportScope.WORD_LIST -> exportWordListCsv(level)
            ExportScope.STUDY_RECORDS -> JsonCodec.prettyJson.encodeToString(
                StudyRecordsExport.serializer(),
                buildStudyRecords(level, now),
            )

            ExportScope.FULL_BACKUP -> JsonCodec.prettyJson.encodeToString(
                FullBackup.serializer(),
                buildFullBackup(now),
            )
        }
        Outcome.Success(payload)
    } catch (throwable: Throwable) {
        Outcome.Failure(AppError.io(throwable.javaClass.simpleName))
    }

    private suspend fun exportWordListCsv(level: CetLevel): String {
        val header = listOf(
            "id", "word", "phonetic", "partOfSpeech", "meanings",
            "exampleSentence", "exampleTranslation", "collocations",
            "level", "source", "license", "audioUri",
        )
        val rows = mutableListOf(header)
        db.wordDao().allForLevel(level.id).forEach { entity ->
            val word = entity.toDomain()
            rows += listOf(
                word.id.toString(),
                word.word,
                word.phonetic,
                word.partOfSpeech,
                word.meanings.joinToString(" | "),
                word.exampleSentence,
                word.exampleTranslation,
                word.collocations.joinToString(" | "),
                word.level.id,
                word.source,
                word.license,
                word.audioUri.orEmpty(),
            )
        }
        return CsvCodec.write(rows)
    }

    private suspend fun buildStudyRecords(level: CetLevel, now: Long): StudyRecordsExport {
        val progress = db.progressDao().wordsByIds(db.wordDao().idsForLevel(level.id))
            .filter { it.status != null }
            .map { row ->
                ProgressExport(
                    word = row.word,
                    level = row.level,
                    status = row.status.orEmpty(),
                    masteryLevel = row.masteryLevel ?: 0,
                    repetitions = row.repetitions ?: 0,
                    intervalDays = row.intervalDays ?: 0,
                    easeFactor = row.easeFactor ?: 0.0,
                    dueAt = row.dueAt ?: 0L,
                    lastReviewedAt = row.lastReviewedAt,
                    lastAnswer = row.lastAnswer,
                    lapseCount = row.lapseCount ?: 0,
                    wrongCount = row.wrongCount ?: 0,
                    fuzzyCount = row.fuzzyCount ?: 0,
                    consecutiveCorrectCount = row.consecutiveCorrectCount ?: 0,
                    isFavorite = (row.isFavorite ?: 0) == 1,
                )
            }
        val mistakes = db.wordDao().idsForLevel(level.id).mapNotNull { wordId ->
            val entity = db.mistakeBookDao().byWordId(wordId) ?: return@mapNotNull null
            MistakeExport(
                wrongCount = entity.wrongCount,
                fuzzyCount = entity.fuzzyCount,
                firstWrongAt = entity.firstWrongAt,
                lastWrongAt = entity.lastWrongAt,
                lastSource = entity.lastSource,
                correctSinceWrong = entity.correctSinceWrong,
                resolvedAt = entity.resolvedAt,
                isFavorite = entity.isFavorite,
                word = db.wordDao().byId(wordId)?.word.orEmpty(),
                level = entity.level,
            )
        }
        val days = db.dailySummaryDao().allForLevel(level.id).map { it.toDayExport() }
        return StudyRecordsExport(
            format = FORMAT_STUDY_RECORDS,
            version = BACKUP_VERSION,
            exportedAtMillis = now,
            exportedAtIso = Instant.ofEpochMilli(now).toString(),
            level = level.id,
            progress = progress,
            mistakes = mistakes,
            days = days,
        )
    }

    private suspend fun buildFullBackup(now: Long): FullBackup {
        val levels = db.wordDao().observeLevels().first()
        val words = mutableListOf<WordExport>()
        levels.forEach { levelId ->
            db.wordDao().allForLevel(levelId).forEach { entity ->
                val word = entity.toDomain()
                words += WordExport(
                    word = word.word,
                    phonetic = word.phonetic,
                    partOfSpeech = word.partOfSpeech,
                    meanings = word.meanings,
                    exampleSentence = word.exampleSentence,
                    exampleTranslation = word.exampleTranslation,
                    collocations = word.collocations,
                    level = word.level.id,
                    source = word.source,
                    license = word.license,
                    audioUri = word.audioUri,
                )
            }
        }

        val progress = mutableListOf<ProgressExport>()
        val mistakes = mutableListOf<MistakeExport>()
        levels.forEach { levelId ->
            val records = buildStudyRecords(CetLevel.fromId(levelId), now)
            progress += records.progress
            mistakes += records.mistakes
        }

        val events = db.reviewEventDao().let { dao ->
            levels.flatMap { levelId -> dao.allForLevel(levelId) }
        }.map { entity ->
            EventExport(
                word = db.wordDao().byId(entity.wordId)?.word.orEmpty(),
                level = entity.level,
                grade = entity.grade,
                source = entity.source,
                answeredAt = entity.answeredAt,
                dayKey = entity.dayKey,
                previousIntervalDays = entity.previousIntervalDays,
                newIntervalDays = entity.newIntervalDays,
            )
        }

        val days = levels.flatMap { levelId -> db.dailySummaryDao().allForLevel(levelId) }
            .map { it.toDayExport() }

        return FullBackup(
            format = FORMAT_FULL_BACKUP,
            version = BACKUP_VERSION,
            exportedAtMillis = now,
            exportedAtIso = Instant.ofEpochMilli(now).toString(),
            note = "CetVocab 完整备份：词库、学习进度、错题、答题日志与每日统计。恢复时会替换对应词库的学习数据。",
            words = words,
            progress = progress,
            mistakes = mistakes,
            events = events,
            days = days,
        )
    }

    override suspend fun restore(content: String): Outcome<RestoreResult> {
        if (content.isBlank()) return Outcome.Failure(AppError.empty())
        val backup = runCatching { JsonCodec.json.decodeFromString<FullBackup>(content) }.getOrNull()
        if (backup == null) {
            // Also accept a study-records-only file, which contains everything but the words.
            val records = runCatching {
                JsonCodec.json.decodeFromString<StudyRecordsExport>(content)
            }.getOrNull() ?: return Outcome.Failure(AppError.parse("无法识别的备份文件"))
            return restoreRecords(records)
        }

        val now = time.nowMillis()
        return try {
            val result = db.withTransaction {
                // 1. Words: matched by level + normalised text so ids stay stable.
                backup.words.forEach { word ->
                    val level = CetLevel.fromId(word.level)
                    val normalized = Word.normalizeWordKey(word.word)
                    if (db.wordDao().byNormalized(level.id, normalized) == null) {
                        db.wordDao().insert(
                            com.cetvocab.app.data.local.entity.WordEntity(
                                packId = "restored-${level.id.lowercase()}",
                                externalId = null,
                                word = word.word,
                                normalizedWord = normalized,
                                phonetic = word.phonetic,
                                partOfSpeech = word.partOfSpeech,
                                meanings = JsonCodec.encodeStrings(word.meanings),
                                exampleSentence = word.exampleSentence,
                                exampleTranslation = word.exampleTranslation,
                                collocations = JsonCodec.encodeStrings(word.collocations),
                                level = level.id,
                                source = word.source,
                                license = word.license,
                                audioUri = word.audioUri,
                                createdAt = now,
                                updatedAt = now,
                            ),
                        )
                    }
                }

                // 2. Replace study data for the levels present in the backup so a restore is
                //    idempotent (restoring twice does not double the counters).
                val levelsInBackup = (backup.progress.map { it.level } + backup.mistakes.map { it.level })
                    .distinct()
                levelsInBackup.forEach { levelId ->
                    db.mistakeBookDao().deleteEverythingForLevel(levelId)
                    db.reviewEventDao().deleteLevel(levelId)
                    db.progressDao().deleteForLevel(levelId)
                }

                var restoredProgress = 0
                var skipped = 0
                backup.progress.forEach { record ->
                    val wordId = db.wordDao()
                        .byNormalized(CetLevel.fromId(record.level).id, Word.normalizeWordKey(record.word))
                        ?.id
                    if (wordId == null) {
                        skipped++
                    } else {
                        db.progressDao().upsert(
                            com.cetvocab.app.data.local.entity.UserWordProgressEntity(
                                wordId = wordId,
                                status = record.status,
                                masteryLevel = record.masteryLevel,
                                repetitions = record.repetitions,
                                intervalDays = record.intervalDays,
                                easeFactor = record.easeFactor,
                                dueAt = record.dueAt,
                                lastReviewedAt = record.lastReviewedAt,
                                lastAnswer = record.lastAnswer,
                                lapseCount = record.lapseCount,
                                wrongCount = record.wrongCount,
                                fuzzyCount = record.fuzzyCount,
                                consecutiveCorrectCount = record.consecutiveCorrectCount,
                                isFavorite = record.isFavorite,
                                introducedAt = record.lastReviewedAt,
                                updatedAt = now,
                            ),
                        )
                        restoredProgress++
                    }
                }

                var restoredMistakes = 0
                backup.mistakes.forEach { record ->
                    val wordId = db.wordDao()
                        .byNormalized(CetLevel.fromId(record.level).id, Word.normalizeWordKey(record.word))
                        ?.id ?: return@forEach
                    db.mistakeBookDao().upsert(
                        com.cetvocab.app.data.local.entity.WrongAnswerEntity(
                            wordId = wordId,
                            wrongCount = record.wrongCount,
                            fuzzyCount = record.fuzzyCount,
                            firstWrongAt = record.firstWrongAt,
                            lastWrongAt = record.lastWrongAt,
                            lastSource = record.lastSource,
                            correctSinceWrong = record.correctSinceWrong,
                            resolvedAt = record.resolvedAt,
                            isFavorite = record.isFavorite,
                            level = record.level,
                            updatedAt = now,
                        ),
                    )
                    restoredMistakes++
                }

                var restoredEvents = 0
                backup.events.forEach { event ->
                    val wordId = db.wordDao()
                        .byNormalized(CetLevel.fromId(event.level).id, Word.normalizeWordKey(event.word))
                        ?.id ?: return@forEach
                    db.reviewEventDao().insert(
                        com.cetvocab.app.data.local.entity.ReviewEventEntity(
                            wordId = wordId,
                            sessionId = null,
                            grade = event.grade,
                            source = event.source,
                            answeredAt = event.answeredAt,
                            dayKey = event.dayKey,
                            level = event.level,
                            previousIntervalDays = event.previousIntervalDays,
                            newIntervalDays = event.newIntervalDays,
                            responseTimeMs = null,
                        ),
                    )
                    restoredEvents++
                }

                backup.days.forEach { day ->
                    db.dailySummaryDao().upsert(
                        DailyStudySummaryEntity(
                            dayKey = day.dayKey,
                            level = day.level,
                            studiedWords = day.studiedWords,
                            newWords = day.newWords,
                            reviewWords = day.reviewWords,
                            masteredCount = day.masteredCount,
                            fuzzyCount = day.fuzzyCount,
                            unknownCount = day.unknownCount,
                            reviewEvents = day.reviewEvents,
                            studyDurationMs = day.durationMillis,
                            sessionsCompleted = day.sessionsCompleted,
                            examsCompleted = day.examsCompleted,
                            examCorrect = day.examCorrect,
                            examTotal = day.examTotal,
                            updatedAt = now,
                        ),
                    )
                }

                RestoreResult(
                    restoredWords = backup.words.size,
                    restoredProgress = restoredProgress,
                    restoredMistakes = restoredMistakes,
                    restoredEvents = restoredEvents,
                    restoredDays = backup.days.size,
                    skippedWords = skipped,
                )
            }
            Outcome.Success(result)
        } catch (throwable: Throwable) {
            Outcome.Failure(AppError.database(throwable.javaClass.simpleName))
        }
    }

    private suspend fun restoreRecords(records: StudyRecordsExport): Outcome<RestoreResult> {
        val now = time.nowMillis()
        return try {
            val result = db.withTransaction {
                var restoredProgress = 0
                var skipped = 0
                var restoredMistakes = 0

                db.progressDao().deleteForLevel(records.level)
                db.mistakeBookDao().deleteEverythingForLevel(records.level)

                records.progress.forEach { record ->
                    val wordId = db.wordDao()
                        .byNormalized(records.level, Word.normalizeWordKey(record.word))?.id
                    if (wordId == null) {
                        skipped++
                    } else {
                        db.progressDao().upsert(
                            com.cetvocab.app.data.local.entity.UserWordProgressEntity(
                                wordId = wordId,
                                status = record.status,
                                masteryLevel = record.masteryLevel,
                                repetitions = record.repetitions,
                                intervalDays = record.intervalDays,
                                easeFactor = record.easeFactor,
                                dueAt = record.dueAt,
                                lastReviewedAt = record.lastReviewedAt,
                                lastAnswer = record.lastAnswer,
                                lapseCount = record.lapseCount,
                                wrongCount = record.wrongCount,
                                fuzzyCount = record.fuzzyCount,
                                consecutiveCorrectCount = record.consecutiveCorrectCount,
                                isFavorite = record.isFavorite,
                                introducedAt = record.lastReviewedAt,
                                updatedAt = now,
                            ),
                        )
                        restoredProgress++
                    }
                }
                records.mistakes.forEach { record ->
                    val wordId = db.wordDao()
                        .byNormalized(records.level, Word.normalizeWordKey(record.word))?.id ?: return@forEach
                    db.mistakeBookDao().upsert(
                        com.cetvocab.app.data.local.entity.WrongAnswerEntity(
                            wordId = wordId,
                            wrongCount = record.wrongCount,
                            fuzzyCount = record.fuzzyCount,
                            firstWrongAt = record.firstWrongAt,
                            lastWrongAt = record.lastWrongAt,
                            lastSource = record.lastSource,
                            correctSinceWrong = record.correctSinceWrong,
                            resolvedAt = record.resolvedAt,
                            isFavorite = record.isFavorite,
                            level = records.level,
                            updatedAt = now,
                        ),
                    )
                    restoredMistakes++
                }
                records.days.forEach { day ->
                    db.dailySummaryDao().upsert(
                        DailyStudySummaryEntity(
                            dayKey = day.dayKey,
                            level = day.level,
                            studiedWords = day.studiedWords,
                            newWords = day.newWords,
                            reviewWords = day.reviewWords,
                            masteredCount = day.masteredCount,
                            fuzzyCount = day.fuzzyCount,
                            unknownCount = day.unknownCount,
                            reviewEvents = day.reviewEvents,
                            studyDurationMs = day.durationMillis,
                            sessionsCompleted = day.sessionsCompleted,
                            examsCompleted = day.examsCompleted,
                            examCorrect = day.examCorrect,
                            examTotal = day.examTotal,
                            updatedAt = now,
                        ),
                    )
                }
                RestoreResult(
                    restoredWords = 0,
                    restoredProgress = restoredProgress,
                    restoredMistakes = restoredMistakes,
                    restoredEvents = 0,
                    restoredDays = records.days.size,
                    skippedWords = skipped,
                )
            }
            Outcome.Success(result)
        } catch (throwable: Throwable) {
            Outcome.Failure(AppError.database(throwable.javaClass.simpleName))
        }
    }

    override suspend fun clearStudyProgress() {
        db.withTransaction {
            db.mistakeBookDao().deleteEverything()
            db.reviewEventDao().deleteAll()
            db.progressDao().deleteAll()
            db.studySessionDao().deleteAll()
            db.examDao().deleteAll()
            db.dailySummaryDao().deleteAll()
        }
    }

    override suspend fun clearAllData() {
        db.withTransaction {
            clearStudyProgress()
            db.wordDao().let { dao ->
                listOf(CetLevel.CET4, CetLevel.CET6, CetLevel.OTHER).forEach { level ->
                    dao.deleteLevel(level.id)
                }
            }
        }
    }

    private fun DailyStudySummaryEntity.toDayExport(): DayExport = DayExport(
        dayKey = dayKey,
        level = level,
        studiedWords = studiedWords,
        newWords = newWords,
        reviewWords = reviewWords,
        masteredCount = masteredCount,
        fuzzyCount = fuzzyCount,
        unknownCount = unknownCount,
        reviewEvents = reviewEvents,
        durationMillis = studyDurationMs,
        sessionsCompleted = sessionsCompleted,
        examsCompleted = examsCompleted,
        examCorrect = examCorrect,
        examTotal = examTotal,
    )

    // --- Serialised shapes ------------------------------------------------------------------

    @Serializable
    private data class WordExport(
        val word: String,
        val phonetic: String = "",
        val partOfSpeech: String = "",
        val meanings: List<String> = emptyList(),
        val exampleSentence: String = "",
        val exampleTranslation: String = "",
        val collocations: List<String> = emptyList(),
        val level: String = CetLevel.CET4.id,
        val source: String = "",
        val license: String = "",
        val audioUri: String? = null,
    )

    @Serializable
    private data class ProgressExport(
        val word: String,
        val level: String,
        val status: String,
        val masteryLevel: Int,
        val repetitions: Int,
        val intervalDays: Int,
        val easeFactor: Double,
        val dueAt: Long,
        val lastReviewedAt: Long?,
        val lastAnswer: String?,
        val lapseCount: Int,
        val wrongCount: Int,
        val fuzzyCount: Int,
        val consecutiveCorrectCount: Int,
        val isFavorite: Boolean,
    )

    @Serializable
    private data class MistakeExport(
        /** Filled while exporting; ignored when restoring (the word text is used instead). */
        val wordId: Long = 0L,
        val wrongCount: Int,
        val fuzzyCount: Int,
        val firstWrongAt: Long,
        val lastWrongAt: Long,
        val lastSource: String = "STUDY",
        val correctSinceWrong: Int = 0,
        val resolvedAt: Long? = null,
        val isFavorite: Boolean = false,
        val word: String = "",
        val level: String = CetLevel.CET4.id,
    )

    @Serializable
    private data class EventExport(
        val word: String,
        val level: String,
        val grade: String,
        val source: String,
        val answeredAt: Long,
        val dayKey: String,
        val previousIntervalDays: Int = 0,
        val newIntervalDays: Int = 0,
    )

    @Serializable
    private data class DayExport(
        val dayKey: String,
        val level: String,
        val studiedWords: Int = 0,
        val newWords: Int = 0,
        val reviewWords: Int = 0,
        val masteredCount: Int = 0,
        val fuzzyCount: Int = 0,
        val unknownCount: Int = 0,
        val reviewEvents: Int = 0,
        val durationMillis: Long = 0L,
        val sessionsCompleted: Int = 0,
        val examsCompleted: Int = 0,
        val examCorrect: Int = 0,
        val examTotal: Int = 0,
    )

    @Serializable
    private data class StudyRecordsExport(
        val format: String,
        val version: Int,
        val exportedAtMillis: Long,
        val exportedAtIso: String,
        val level: String,
        val progress: List<ProgressExport> = emptyList(),
        val mistakes: List<MistakeExport> = emptyList(),
        val days: List<DayExport> = emptyList(),
    )

    @Serializable
    private data class FullBackup(
        val format: String,
        val version: Int,
        val exportedAtMillis: Long,
        val exportedAtIso: String,
        val note: String = "",
        val words: List<WordExport> = emptyList(),
        val progress: List<ProgressExport> = emptyList(),
        val mistakes: List<MistakeExport> = emptyList(),
        val events: List<EventExport> = emptyList(),
        val days: List<DayExport> = emptyList(),
    )

    companion object {
        const val BACKUP_VERSION = 1
        const val FORMAT_STUDY_RECORDS = "CetVocab 学习记录 (JSON)"
        const val FORMAT_FULL_BACKUP = "CetVocab 完整备份 (JSON)"
    }
}
