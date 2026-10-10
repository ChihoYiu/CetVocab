package com.cetvocab.app.domain.exam

import kotlin.random.Random

/**
 * Builds an exam paper.
 *
 * The generator is a pure function of the word list, the blueprint and its seed, so:
 *  * the same seed always produces the same paper (reproducible, unit testable);
 *  * distractors are drawn from the same word list, which keeps options plausible and keeps
 *    the app fully offline — no dictionary service is contacted.
 *
 * A question is only emitted when it can be graded unambiguously: choice questions need at
 * least two distinct candidate answers, and a word without meanings can never become a
 * prompt. The generator therefore returns fewer questions than requested on a tiny word
 * list rather than producing an unanswerable paper.
 */
object ExamGenerator {

    /** Ratio of spelling questions; every third question when the list allows it. */
    private const val SPELLING_EVERY = 3

    fun generate(
        words: List<ExamWordSource>,
        blueprint: ExamBlueprint,
    ): List<ExamQuestion> {
        val pool = words
            .filter { it.word.isNotBlank() && it.primaryMeaning.isNotBlank() }
            .distinctBy { it.wordId }
        if (pool.isEmpty()) return emptyList()

        val random = Random(blueprint.seed)
        val selected = pool.shuffled(random).take(blueprint.questionCount.coerceAtLeast(1))

        return selected.mapIndexedNotNull { index, source ->
            val type = chooseType(index, pool.size)
            buildQuestion(index, source, pool, type, random)
        }
    }

    private fun chooseType(index: Int, poolSize: Int): ExamQuestionType = when {
        // Spelling is only meaningful with a reasonably sized list, and every third card.
        poolSize >= 4 && (index + 1) % SPELLING_EVERY == 0 -> ExamQuestionType.SPELLING
        index % 2 == 0 -> ExamQuestionType.EN_TO_ZH
        else -> ExamQuestionType.ZH_TO_EN
    }

    private fun buildQuestion(
        index: Int,
        source: ExamWordSource,
        pool: List<ExamWordSource>,
        type: ExamQuestionType,
        random: Random,
    ): ExamQuestion? = when (type) {
        ExamQuestionType.EN_TO_ZH -> choiceQuestion(
            index = index,
            source = source,
            pool = pool,
            type = type,
            prompt = source.word,
            promptPhonetic = source.phonetic,
            correct = source.meaningLabel,
            candidateAnswers = { it.meaningLabel },
            random = random,
        )

        ExamQuestionType.ZH_TO_EN -> choiceQuestion(
            index = index,
            source = source,
            pool = pool,
            type = type,
            prompt = source.primaryMeaning,
            promptPhonetic = "",
            correct = source.word,
            candidateAnswers = { it.word },
            random = random,
        )

        ExamQuestionType.SPELLING -> ExamQuestion(
            orderIndex = index,
            wordId = source.wordId,
            type = type,
            prompt = source.primaryMeaning,
            promptPhonetic = source.phonetic,
            options = emptyList(),
            correctOptionIndex = -1,
            correctAnswer = source.word,
        )
    }

    private fun choiceQuestion(
        index: Int,
        source: ExamWordSource,
        pool: List<ExamWordSource>,
        type: ExamQuestionType,
        prompt: String,
        promptPhonetic: String,
        correct: String,
        candidateAnswers: (ExamWordSource) -> String,
        random: Random,
    ): ExamQuestion? {
        if (correct.isBlank()) return null
        val distractors = pool
            .asSequence()
            .filter { it.wordId != source.wordId }
            .map(candidateAnswers)
            .filter { it.isNotBlank() && !it.equals(correct, ignoreCase = true) }
            .distinct()
            .shuffled(random)
            .take(MAX_OPTIONS - 1)
            .toList()
        if (distractors.isEmpty()) return null

        val options = (distractors + correct).shuffled(random)
        return ExamQuestion(
            orderIndex = index,
            wordId = source.wordId,
            type = type,
            prompt = prompt,
            promptPhonetic = promptPhonetic,
            options = options,
            correctOptionIndex = options.indexOf(correct),
            correctAnswer = correct,
        )
    }

    /** 四选一 whenever possible. */
    const val MAX_OPTIONS = 4
}
