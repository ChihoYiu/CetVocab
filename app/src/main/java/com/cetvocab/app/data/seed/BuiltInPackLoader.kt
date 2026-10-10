package com.cetvocab.app.data.seed

import android.content.Context
import com.cetvocab.app.core.result.AppError
import com.cetvocab.app.core.result.ErrorCode
import com.cetvocab.app.core.result.Outcome
import com.cetvocab.app.domain.importer.ImportPreview
import com.cetvocab.app.domain.importer.WordPackParser
import com.cetvocab.app.domain.model.CetLevel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import com.cetvocab.app.core.json.JsonCodec

/**
 * One bundled pack as described by `assets/wordpacks/manifest.json`.
 *
 * The manifest exists so that adding a vocabulary list is a data change (drop a JSON file and add
 * a line) rather than a code change, and so the app can validate what it ships before it writes
 * anything to the database.
 */
data class BuiltInPackDefinition(
    val id: String,
    val assetPath: String,
    val level: CetLevel,
    val name: String,
    /** Content version from the asset itself; a higher value triggers an upgrade on app start. */
    val version: Int,
    /** Number of words the asset is expected to contain; used to detect a truncated file. */
    val expectedWordCount: Int,
)

/** The shipped manifest file. */
@Serializable
private data class PackManifest(
    val packs: List<PackEntry> = emptyList(),
)

@Serializable
private data class PackEntry(
    val id: String,
    val asset: String,
    val level: String,
    val name: String = "",
    val version: Int = 1,
    val wordCount: Int = 0,
)

/**
 * Source of the built-in packs.
 *
 * Extracted as an interface for one reason: the seeding/upgrade logic in the repository is the part
 * that can destroy learning data if it is wrong, so it must be testable with a deterministic pack
 * source instead of the multi-megabyte shipped assets.
 */
interface PackSource {
    suspend fun definitions(): List<BuiltInPackDefinition>
    suspend fun load(definition: BuiltInPackDefinition): Outcome<ImportPreview>
}

/**
 * Loads the built-in vocabulary from `assets/wordpacks/`.
 *
 * Design decisions worth keeping:
 *  * the shipped JSON is parsed by the **same** [WordPackParser] that validates user imports, so
 *    bundled data can never drift from the documented pack schema;
 *  * a missing or corrupt asset degrades to the small in-code demo packs instead of leaving the
 *    learner with an empty app (and reports the problem as a value, never as an exception);
 *  * parsing happens on [Dispatchers.IO] because the complete CET-4/CET-6 files are megabytes of
 *    JSON and must never be read on the main thread.
 */
class BuiltInPackLoader(
    private val context: Context,
    private val parser: WordPackParser = WordPackParser,
) : PackSource {

    /** Definitions from the manifest, or the in-code demo fallback when there is no asset. */
    override suspend fun definitions(): List<BuiltInPackDefinition> = withContext(Dispatchers.IO) {
        val manifest = readAsset(MANIFEST_PATH)
            ?.let { raw -> runCatching { JsonCodec.json.decodeFromString<PackManifest>(raw) }.getOrNull() }
        val fromManifest = manifest?.packs.orEmpty().mapNotNull { entry ->
            val level = CetLevel.entries.firstOrNull { it.id == entry.level } ?: return@mapNotNull null
            BuiltInPackDefinition(
                id = entry.id,
                assetPath = entry.asset,
                level = level,
                name = entry.name.ifBlank { entry.id },
                version = entry.version,
                expectedWordCount = entry.wordCount,
            )
        }
        fromManifest.ifEmpty { DEMO_FALLBACK }
    }

    /**
     * Reads and validates one pack.
     *
     * Returns [Outcome.Failure] for a missing asset, a size overflow or a JSON document the
     * parser rejects — the caller then falls back to the demo packs for that level.
     */
    override suspend fun load(definition: BuiltInPackDefinition): Outcome<ImportPreview> = withContext(Dispatchers.IO) {
        val raw = readAsset(definition.assetPath)
            ?: return@withContext Outcome.Failure(
                AppError(ErrorCode.IO, "内置词库文件缺失：${definition.assetPath}"),
            )
        if (raw.toByteArray().size.toLong() > WordPackParser.MAX_FILE_BYTES * 4) {
            return@withContext Outcome.Failure(
                AppError(ErrorCode.TOO_LARGE, "内置词库文件过大：${definition.assetPath}"),
            )
        }
        parser.parse(
            fileName = definition.assetPath.substringAfterLast('/'),
            content = raw,
            defaultLevel = definition.level,
            defaultSource = definition.name,
            defaultLicense = BuiltInWordPacks.LICENSE,
        )
    }

    private fun readAsset(path: String): String? = runCatching {
        context.assets.open(path).use { stream -> stream.bufferedReader(Charsets.UTF_8).readText() }
    }.getOrNull()

    companion object {
        const val MANIFEST_PATH = "wordpacks/manifest.json"

        /**
         * Fallback used when no asset pack is present (for example a source checkout before the
         * data step ran). It keeps the app usable instead of starting with an empty word list.
         */
        private val DEMO_FALLBACK = listOf(
            BuiltInPackDefinition(
                id = "builtin-cet4",
                assetPath = "wordpacks/cet4.json",
                level = CetLevel.CET4,
                name = "CET-4 演示词库",
                version = 1,
                expectedWordCount = 0,
            ),
            BuiltInPackDefinition(
                id = "builtin-cet6",
                assetPath = "wordpacks/cet6.json",
                level = CetLevel.CET6,
                name = "CET-6 演示词库",
                version = 1,
                expectedWordCount = 0,
            ),
        )
    }
}
