package com.cetvocab.app.domain.model

/**
 * A vocabulary entry as the UI and domain layers see it — free of Room annotations.
 *
 * [meanings] and [collocations] are ordered lists; the first meaning is the "简要释义"
 * shown in list rows. [source] and [license] travel with every word so imported content
 * can always be attributed.
 */
data class Word(
    val id: Long = 0L,
    val word: String,
    val phonetic: String = "",
    val partOfSpeech: String = "",
    val meanings: List<String> = emptyList(),
    val exampleSentence: String = "",
    val exampleTranslation: String = "",
    val collocations: List<String> = emptyList(),
    val level: CetLevel = CetLevel.CET4,
    val source: String = "",
    val license: String = "",
    val audioUri: String? = null,
    val packId: String? = null,
) {
    val primaryMeaning: String get() = meanings.firstOrNull().orEmpty()

    /** `n. 苹果；苹果树` style label used inside option buttons. */
    val meaningLabel: String
        get() = buildString {
            if (partOfSpeech.isNotBlank()) {
                append(partOfSpeech.trim())
                append(' ')
            }
            append(primaryMeaning)
        }.trim()

    /** Normalised key used for de-duplication during import. */
    val normalized: String get() = normalizeWordKey(word)

    companion object {
        /** Case/space/punctuation insensitive key; keeps `Apple` and `apple` the same word. */
        fun normalizeWordKey(raw: String): String =
            raw.trim().lowercase().replace(Regex("[\\s\\u00A0]+"), " ").trim()
    }
}

/** A word together with the current user's progress, used by list screens and the queue. */
data class WordWithProgress(
    val word: Word,
    val progress: com.cetvocab.app.domain.srs.WordProgress,
)
