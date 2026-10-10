package com.cetvocab.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A vocabulary pack: a named, attributed group of words (the built-in demo packs or a
 * file the user imported).
 *
 * `isBuiltIn` distinguishes content shipped with the app from user content, which matters
 * for the licence screen and for "clear imported word books" operations.
 */
@Entity(
    tableName = "word_pack",
    indices = [Index("level"), Index("is_built_in")],
)
data class WordPackEntity(
    @PrimaryKey val id: String,
    val name: String,
    @ColumnInfo(name = "level") val level: String,
    @ColumnInfo(name = "description") val description: String = "",
    @ColumnInfo(name = "source") val source: String = "",
    @ColumnInfo(name = "license") val license: String = "",
    @ColumnInfo(name = "version") val version: String = "1.0",
    @ColumnInfo(name = "is_built_in") val isBuiltIn: Boolean = false,
    @ColumnInfo(name = "word_count") val wordCount: Int = 0,
    @ColumnInfo(name = "created_at") val createdAt: Long = 0L,
    @ColumnInfo(name = "updated_at") val updatedAt: Long = 0L,
)

/**
 * One vocabulary entry.
 *
 * Design notes:
 *  * `normalized_word` carries the de-duplication identity together with `level`; a unique
 *    index on that pair makes re-import idempotent *and* keeps the row id stable so the
 *    user's progress (which points at the row id) survives a re-import of the same pack.
 *  * Vocabulary content and learning progress live in separate tables, so replacing a word
 *    list never destroys study history.
 */
@Entity(
    tableName = "word",
    indices = [
        Index(value = ["level", "normalized_word"], unique = true),
        Index("pack_id"),
        Index("word"),
        Index("level"),
    ],
)
data class WordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    @ColumnInfo(name = "pack_id") val packId: String?,
    /** Identifier supplied by the imported file, kept for round-tripping exports. */
    @ColumnInfo(name = "external_id") val externalId: String? = null,
    @ColumnInfo(name = "word") val word: String,
    @ColumnInfo(name = "normalized_word") val normalizedWord: String,
    @ColumnInfo(name = "phonetic") val phonetic: String = "",
    @ColumnInfo(name = "part_of_speech") val partOfSpeech: String = "",
    /** JSON array of strings. */
    @ColumnInfo(name = "meanings") val meanings: String = "[]",
    @ColumnInfo(name = "example_sentence") val exampleSentence: String = "",
    @ColumnInfo(name = "example_translation") val exampleTranslation: String = "",
    /** JSON array of strings. */
    @ColumnInfo(name = "collocations") val collocations: String = "[]",
    @ColumnInfo(name = "level") val level: String,
    @ColumnInfo(name = "source") val source: String = "",
    @ColumnInfo(name = "license") val license: String = "",
    @ColumnInfo(name = "audio_uri") val audioUri: String? = null,
    @ColumnInfo(name = "created_at") val createdAt: Long = 0L,
    @ColumnInfo(name = "updated_at") val updatedAt: Long = 0L,
)
