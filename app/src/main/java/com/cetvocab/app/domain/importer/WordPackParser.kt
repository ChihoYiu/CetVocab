package com.cetvocab.app.domain.importer

import com.cetvocab.app.core.json.JsonCodec
import com.cetvocab.app.core.result.AppError
import com.cetvocab.app.core.result.Outcome
import com.cetvocab.app.domain.model.CetLevel
import com.cetvocab.app.domain.model.Word
import kotlinx.serialization.Serializable

/**
 * Parses and validates a user supplied word list.
 *
 * Design goals, in order:
 *  1. **Never crash.** A malformed file, a truncated JSON document or a row with the wrong
 *     type produces a [Outcome.Failure] or a row level [ImportIssue] — never an exception
 *     that reaches the UI.
 *  2. **Never trust the file.** Size, row count, field count and field length are all
 *     bounded, and every value that reaches SQL goes through a bound DAO parameter.
 *  3. **Be honest about problems.** The preview shows exactly which rows will be skipped and
 *     why, before anything is written.
 */
object WordPackParser {

    /** 8 MiB is far more than any realistic word list and keeps memory bounded. */
    const val MAX_FILE_BYTES: Long = 8L * 1024 * 1024

    /** Hard cap on records per file. */
    const val MAX_RECORDS: Int = 20_000

    /** Maximum length of a single word; longer rows are rejected with an issue. */
    const val MAX_WORD_LENGTH: Int = 64

    private const val MAX_MEANINGS = 12
    private const val MAX_TEXT_LENGTH = 512
    private const val MAX_COLLOCATIONS = 12

    /**
     * Parses [content] (already read from the picked file).
     *
     * @param fileName shown in the preview and stored in the import history
     * @param defaultLevel used when the file does not declare a level
     * @param defaultSource attribution applied when a row has none
     * @param defaultLicense licence applied when a row has none
     */
    fun parse(
        fileName: String,
        content: String,
        defaultLevel: CetLevel,
        defaultSource: String,
        defaultLicense: String,
    ): Outcome<ImportPreview> {
        if (content.isBlank()) return Outcome.Failure(AppError.empty())
        if (content.toByteArray().size.toLong() > MAX_FILE_BYTES) return Outcome.Failure(AppError.tooLarge())

        val format = PackFormat.fromFileName(fileName)
        return when (format) {
            PackFormat.JSON -> parseJson(fileName, content, defaultLevel, defaultSource, defaultLicense)
            PackFormat.CSV -> parseCsv(fileName, content, defaultLevel, defaultSource, defaultLicense)
        }
    }

    // --- JSON ---------------------------------------------------------------------------

    @Serializable
    private data class JsonWordDto(
        val id: String? = null,
        val word: String? = null,
        val phonetic: String? = null,
        val partOfSpeech: String? = null,
        val meanings: List<String>? = null,
        val meaning: String? = null,
        val exampleSentence: String? = null,
        val exampleTranslation: String? = null,
        val collocations: List<String>? = null,
        val level: String? = null,
        val source: String? = null,
        val license: String? = null,
        val audioUri: String? = null,
        val packName: String? = null,
    )

    @Serializable
    private data class JsonPackDto(
        val version: Int = 1,
        val name: String? = null,
        val packName: String? = null,
        val source: String? = null,
        val license: String? = null,
        val defaultLevel: String? = null,
        val words: List<JsonWordDto> = emptyList(),
    )

    private fun parseJson(
        fileName: String,
        content: String,
        defaultLevel: CetLevel,
        defaultSource: String,
        defaultLicense: String,
    ): Outcome<ImportPreview> {
        val trimmed = content.trimStart()
        val json = JsonCodec.json

        val pack: JsonPackDto
        val words: List<JsonWordDto>
        if (trimmed.startsWith("[")) {
            val list = runCatching { json.decodeFromString<List<JsonWordDto>>(content) }.getOrNull()
                ?: return Outcome.Failure(AppError.parse("JSON 数组解析失败"))
            pack = JsonPackDto()
            words = list
        } else {
            pack = runCatching { json.decodeFromString<JsonPackDto>(content) }.getOrNull()
                ?: return Outcome.Failure(AppError.parse("JSON 对象解析失败"))
            words = pack.words
        }

        if (words.size > MAX_RECORDS) {
            return Outcome.Failure(
                AppError(
                    code = com.cetvocab.app.core.result.ErrorCode.TOO_LARGE,
                    message = "文件包含 ${words.size} 条记录，超过上限 $MAX_RECORDS 条，请拆分后再导入。",
                ),
            )
        }

        val packSource = pack.source?.takeIf { it.isNotBlank() } ?: defaultSource
        val packLicense = pack.license?.takeIf { it.isNotBlank() } ?: defaultLicense
        val packLevel = CetLevel.fromId(pack.defaultLevel ?: defaultLevel.id)

        val builder = PreviewBuilder(
            fileName = fileName,
            format = PackFormat.JSON,
            packName = (pack.name ?: pack.packName)?.takeIf { it.isNotBlank() } ?: fileName.substringBeforeLast('.'),
            fallbackLevel = packLevel,
        )

        words.forEachIndexed { index, dto ->
            builder.add(
                rowNumber = index + 1,
                externalId = dto.id,
                word = dto.word,
                phonetic = dto.phonetic,
                partOfSpeech = dto.partOfSpeech,
                meanings = dto.meanings ?: CsvCodec.splitValues(dto.meaning),
                exampleSentence = dto.exampleSentence,
                exampleTranslation = dto.exampleTranslation,
                collocations = dto.collocations ?: emptyList(),
                level = dto.level,
                source = dto.source ?: packSource,
                license = dto.license ?: packLicense,
                audioUri = dto.audioUri,
            )
        }
        return builder.finish()
    }

    // --- CSV ----------------------------------------------------------------------------

    private val WORD_KEYS = setOf("word", "english", "单词", "英文")
    private val MEANING_KEYS = setOf("meanings", "meaning", "translation", "chinese", "释义", "中文", "意思")
    private val PHONETIC_KEYS = setOf("phonetic", "ipa", "pronunciation", "音标")
    private val POS_KEYS = setOf("partofspeech", "pos", "part", "词性")
    private val EXAMPLE_KEYS = setOf("examplesentence", "example", "sentence", "例句")
    private val EXAMPLE_TRANSLATION_KEYS =
        setOf("exampletranslation", "sentencetranslation", "examplezh", "例句翻译", "例句释义")
    private val COLLOCATION_KEYS = setOf("collocations", "collocation", "phrases", "搭配")
    private val LEVEL_KEYS = setOf("level", "book", "级别", "词库")
    private val SOURCE_KEYS = setOf("source", "来源")
    private val LICENSE_KEYS = setOf("license", "licence", "版权", "许可")
    private val AUDIO_KEYS = setOf("audiourl", "audiourn", "audio", "音频")
    private val ID_KEYS = setOf("id", "externalid", "编号")

    private fun parseCsv(
        fileName: String,
        content: String,
        defaultLevel: CetLevel,
        defaultSource: String,
        defaultLicense: String,
    ): Outcome<ImportPreview> {
        val rows = CsvCodec.parse(content)
        if (rows.isEmpty()) return Outcome.Failure(AppError.empty())

        val header = CsvCodec.headerKeys(rows.first())
        val wordColumn = header.indexOfFirst { it in WORD_KEYS }
        if (wordColumn < 0) {
            return Outcome.Failure(
                AppError(
                    code = com.cetvocab.app.core.result.ErrorCode.PARSE,
                    message = "CSV 缺少 word（单词）列，请在首行添加表头。",
                ),
            )
        }
        val dataRows = rows.drop(1)
        if (dataRows.size > MAX_RECORDS) {
            return Outcome.Failure(
                AppError(
                    code = com.cetvocab.app.core.result.ErrorCode.TOO_LARGE,
                    message = "文件包含 ${dataRows.size} 行，超过上限 $MAX_RECORDS 行，请拆分后再导入。",
                ),
            )
        }

        fun column(keys: Set<String>): Int = header.indexOfFirst { it in keys }
        val meaningColumn = column(MEANING_KEYS)
        val phoneticColumn = column(PHONETIC_KEYS)
        val posColumn = column(POS_KEYS)
        val exampleColumn = column(EXAMPLE_KEYS)
        val exampleTranslationColumn = column(EXAMPLE_TRANSLATION_KEYS)
        val collocationColumn = column(COLLOCATION_KEYS)
        val levelColumn = column(LEVEL_KEYS)
        val sourceColumn = column(SOURCE_KEYS)
        val licenseColumn = column(LICENSE_KEYS)
        val audioColumn = column(AUDIO_KEYS)
        val idColumn = column(ID_KEYS)

        fun cell(row: List<String>, column: Int): String? =
            if (column >= 0 && column < row.size) row[column].trim().ifEmpty { null } else null

        val builder = PreviewBuilder(
            fileName = fileName,
            format = PackFormat.CSV,
            packName = fileName.substringBeforeLast('.'),
            fallbackLevel = defaultLevel,
        )

        dataRows.forEachIndexed { index, row ->
            builder.add(
                rowNumber = index + 2, // +2: 1-based and the header row
                externalId = cell(row, idColumn),
                word = cell(row, wordColumn),
                phonetic = cell(row, phoneticColumn),
                partOfSpeech = cell(row, posColumn),
                meanings = CsvCodec.splitValues(cell(row, meaningColumn)),
                exampleSentence = cell(row, exampleColumn),
                exampleTranslation = cell(row, exampleTranslationColumn),
                collocations = CsvCodec.splitValues(cell(row, collocationColumn)),
                level = cell(row, levelColumn),
                source = cell(row, sourceColumn) ?: defaultSource,
                license = cell(row, licenseColumn) ?: defaultLicense,
                audioUri = cell(row, audioColumn),
            )
        }
        return builder.finish()
    }

    // --- Shared validation --------------------------------------------------------------

    /** Collects valid records, issues and duplicates while rows stream in. */
    private class PreviewBuilder(
        private val fileName: String,
        private val format: PackFormat,
        private val packName: String,
        private val fallbackLevel: CetLevel,
    ) {
        private val records = LinkedHashMap<String, ParsedWordRecord>()
        private val issues = mutableListOf<ImportIssue>()
        private var totalRows = 0
        private var duplicates = 0

        fun add(
            rowNumber: Int,
            externalId: String?,
            word: String?,
            phonetic: String?,
            partOfSpeech: String?,
            meanings: List<String>,
            exampleSentence: String?,
            exampleTranslation: String?,
            collocations: List<String>,
            level: String?,
            source: String?,
            license: String?,
            audioUri: String?,
        ) {
            totalRows++
            val cleanWord = word?.trim().orEmpty()
            if (cleanWord.isEmpty()) {
                issues += ImportIssue(rowNumber, "word", "缺少单词，已跳过")
                return
            }
            if (cleanWord.length > MAX_WORD_LENGTH) {
                issues += ImportIssue(rowNumber, "word", "单词长度超过 $MAX_WORD_LENGTH 个字符，已跳过")
                return
            }
            if (!cleanWord.any { it.isLetter() }) {
                issues += ImportIssue(rowNumber, "word", "单词必须包含字母，已跳过")
                return
            }

            val cleanMeanings = meanings
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .distinct()
                .take(MAX_MEANINGS)
            if (cleanMeanings.isEmpty()) {
                issues += ImportIssue(rowNumber, "meanings", "缺少中文释义，已跳过")
                return
            }
            if (cleanMeanings.any { it.length > MAX_TEXT_LENGTH }) {
                issues += ImportIssue(rowNumber, "meanings", "释义过长，已截断")
            }

            val resolvedLevel = CetLevel.fromId(level ?: fallbackLevel.id)
            val normalized = Word.normalizeWordKey(cleanWord)
            val record = ParsedWordRecord(
                rowNumber = rowNumber,
                externalId = externalId?.take(128),
                word = cleanWord,
                phonetic = phonetic.orEmpty().trim().take(MAX_TEXT_LENGTH),
                partOfSpeech = partOfSpeech.orEmpty().trim().take(32),
                meanings = cleanMeanings.map { it.take(MAX_TEXT_LENGTH) },
                exampleSentence = exampleSentence.orEmpty().trim().take(MAX_TEXT_LENGTH),
                exampleTranslation = exampleTranslation.orEmpty().trim().take(MAX_TEXT_LENGTH),
                collocations = collocations.map { it.trim() }.filter { it.isNotEmpty() }
                    .distinct().take(MAX_COLLOCATIONS),
                level = resolvedLevel,
                source = source.orEmpty().trim().take(160),
                license = license.orEmpty().trim().take(160),
                audioUri = audioUri?.trim()?.take(512)?.ifEmpty { null },
            )

            val key = "${resolvedLevel.id}|$normalized"
            val existing = records[key]
            if (existing == null) {
                records[key] = record
            } else {
                duplicates++
                // Keep the richer record; never let a sparse duplicate overwrite real content.
                if (richness(record) > richness(existing)) records[key] = record
            }
        }

        private fun richness(record: ParsedWordRecord): Int =
            record.meanings.size * 3 +
                listOf(
                    record.phonetic,
                    record.partOfSpeech,
                    record.exampleSentence,
                    record.exampleTranslation,
                ).count { it.isNotBlank() } +
                record.collocations.size

        fun finish(): Outcome<ImportPreview> {
            val levelCounts = records.values.groupingBy { it.level }.eachCount()
            val preview = ImportPreview(
                fileName = fileName,
                format = format,
                packName = packName.ifBlank { "导入词库" },
                records = records.values.toList(),
                issues = issues.toList(),
                duplicateRows = duplicates,
                totalRows = totalRows,
                levelCounts = levelCounts,
            )
            return if (preview.records.isEmpty()) {
                Outcome.Failure(
                    AppError(
                        code = com.cetvocab.app.core.result.ErrorCode.EMPTY,
                        message = "没有可导入的有效记录（共 $totalRows 行，其中 ${issues.size} 行有问题）。",
                    ),
                )
            } else {
                Outcome.Success(preview)
            }
        }
    }
}
