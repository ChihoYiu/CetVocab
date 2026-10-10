package com.cetvocab.app.data.io

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.cetvocab.app.core.result.AppError
import com.cetvocab.app.core.result.Outcome
import com.cetvocab.app.domain.importer.WordPackParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Reads and writes documents through the Storage Access Framework.
 *
 * The app never asks for a storage permission: the user picks the file, the system grants a
 * per-file URI, and this class only reads text with a hard size limit or writes the text the
 * user asked to export. All IO happens on [Dispatchers.IO].
 */
class DocumentIo(private val context: Context) {

    /** Reads a picked file as UTF-8 text, refusing anything above [maxBytes]. */
    suspend fun readText(uri: Uri, maxBytes: Long = WordPackParser.MAX_FILE_BYTES): Outcome<String> =
        withContext(Dispatchers.IO) {
            try {
                val size = sizeOf(uri)
                if (size > maxBytes) return@withContext Outcome.Failure(AppError.tooLarge())
                val text = context.contentResolver.openInputStream(uri)?.use { stream ->
                    stream.bufferedReader(Charsets.UTF_8).readText()
                } ?: return@withContext Outcome.Failure(AppError.io("无法打开所选文件"))
                if (text.isBlank()) Outcome.Failure(AppError.empty()) else Outcome.Success(text)
            } catch (throwable: Throwable) {
                Outcome.Failure(AppError.io(throwable.javaClass.simpleName))
            }
        }

    /** Writes [text] to a location the user chose in the system save dialog. */
    suspend fun writeText(uri: Uri, text: String): Outcome<Unit> = withContext(Dispatchers.IO) {
        try {
            context.contentResolver.openOutputStream(uri, "wt")?.use { stream ->
                stream.bufferedWriter(Charsets.UTF_8).use { it.write(text) }
            } ?: return@withContext Outcome.Failure(AppError.io("无法写入所选位置"))
            Outcome.Success(Unit)
        } catch (throwable: Throwable) {
            Outcome.Failure(AppError.io(throwable.javaClass.simpleName))
        }
    }

    /** Display name of a picked document, used in the import preview and export messages. */
    fun displayName(uri: Uri): String {
        val fromProvider = runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor ->
                    if (cursor.moveToFirst()) cursor.getString(0) else null
                }
        }.getOrNull()
        return fromProvider?.takeIf { it.isNotBlank() }
            ?: uri.lastPathSegment?.substringAfterLast('/')
            ?: "selected-file"
    }

    private fun sizeOf(uri: Uri): Long = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getLong(0) else 0L
        } ?: 0L
    }.getOrDefault(0L)
}
