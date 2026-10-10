package com.cetvocab.app.data.seed

/**
 * One entry of the built-in demonstration word list.
 *
 * The built-in lists are **self-authored demo data**: short definitions and example
 * sentences written for this project, released under CC0-1.0 (public domain dedication).
 * They are deliberately small samples that make every feature testable offline; they are
 * *not* a copy of any commercial CET word list. Users who own the rights to a real CET list
 * can import it as JSON/CSV, and every imported record carries its own `source`/`license`
 * fields so attribution travels with the data.
 */
data class SeedWord(
    val word: String,
    val phonetic: String,
    val partOfSpeech: String,
    val meanings: List<String>,
    val exampleSentence: String,
    val exampleTranslation: String,
    val collocations: List<String> = emptyList(),
)
