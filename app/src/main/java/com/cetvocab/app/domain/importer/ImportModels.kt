package com.cetvocab.app.domain.importer

import com.cetvocab.app.domain.model.CetLevel

/** Supported word-list file formats. */
enum class PackFormat(val id: String, val label: String, val extension: String) {
    JSON("JSON", "JSON 文件", "json"),
    CSV("CSV", "CSV 表格", "csv"),
    ;

    companion object {
        fun fromFileName(fileName: String): PackFormat =
            if (fileName.substringAfterLast('.', "").equals("csv", ignoreCase = true)) CSV else JSON
    }
}

/** One parsed and validated word. */
data class ParsedWordRecord(
    val rowNumber: Int,
    val externalId: String?,
    val word: String,
    val phonetic: String,
    val partOfSpeech: String,
    val meanings: List<String>,
    val exampleSentence: String,
    val exampleTranslation: String,
    val collocations: List<String>,
    val level: CetLevel,
    val source: String,
    val license: String,
    val audioUri: String?,
)

/** A problem found in one row. Import never aborts because of a single bad row. */
data class ImportIssue(
    val rowNumber: Int,
    val field: String,
    val message: String,
)

/** What the user is shown *before* anything is written to the database. */
data class ImportPreview(
    val fileName: String,
    val format: PackFormat,
    val packName: String,
    val records: List<ParsedWordRecord>,
    val issues: List<ImportIssue>,
    /** Rows dropped because the same word appeared again in the file. */
    val duplicateRows: Int,
    /** Rows actually present in the file. */
    val totalRows: Int,
    val levelCounts: Map<CetLevel, Int>,
) {
    val validCount: Int get() = records.size
    val invalidCount: Int get() = issues.size
    val isEmpty: Boolean get() = records.isEmpty()
    val licenseSummary: String
        get() = records.firstOrNull()?.license.orEmpty()
}

/** Result of a completed import, shown as a summary and stored in the import history. */
data class ImportResult(
    val fileName: String,
    val format: PackFormat,
    val packId: String,
    val inserted: Int,
    val updated: Int,
    val skipped: Int,
    val failed: Int,
    val totalRows: Int,
    val level: CetLevel,
)

/**
 * How duplicates are resolved.
 *
 *  * Inside one file: the record with more filled fields wins; the dropped row is counted in
 *    [ImportPreview.duplicateRows] and never silently overwrites the better record.
 *  * Against existing data: the *word row id is kept*, only its content is updated. Study
 *    progress, mistake history and exam snapshots therefore survive a re-import — that is a
 *    hard requirement of this app, because re-importing a corrected list must not wipe a
 *    learner's history.
 */
enum class DuplicatePolicy(val label: String) {
    KEEP_ROW_ID_UPDATE_CONTENT("保留学习进度，更新词条内容"),
}
