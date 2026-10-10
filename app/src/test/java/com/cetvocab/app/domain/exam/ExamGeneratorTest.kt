package com.cetvocab.app.domain.exam

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Unit tests for the exam paper generator.
 *
 * The generator is a pure function of the word pool, the blueprint and the seed, so a paper
 * can be regenerated identically inside a test.
 */
class ExamGeneratorTest {

    private fun source(
        id: Long,
        word: String,
        meaning: String = "释义$id",
        partOfSpeech: String = "n.",
        phonetic: String = "/$word/",
    ) = ExamWordSource(
        wordId = id,
        word = word,
        phonetic = phonetic,
        partOfSpeech = partOfSpeech,
        meanings = listOf(meaning),
    )

    private fun pool(size: Int): List<ExamWordSource> =
        (1..size).map { index -> source(index.toLong(), "word$index", "释义$index") }

    private fun blueprint(
        questionCount: Int = 10,
        seed: Long = 42L,
    ) = ExamBlueprint(
        levelId = "CET4",
        questionCount = questionCount,
        durationMinutes = 30,
        seed = seed,
    )

    @Test
    fun `the same seed produces the same paper`() {
        val words = pool(12)

        val first = ExamGenerator.generate(words, blueprint(seed = 7L))
        val second = ExamGenerator.generate(words, blueprint(seed = 7L))

        assertThat(first).isEqualTo(second)
    }

    @Test
    fun `a different seed produces a different order`() {
        val words = pool(12)

        val first = ExamGenerator.generate(words, blueprint(seed = 1L)).map { it.wordId }
        val second = ExamGenerator.generate(words, blueprint(seed = 2L)).map { it.wordId }

        assertThat(first.joinToString()).isNotEqualTo(second.joinToString())
    }

    @Test
    fun `at most question count questions are produced`() {
        val words = pool(30)

        assertThat(ExamGenerator.generate(words, blueprint(questionCount = 10))).hasSize(10)
        assertThat(ExamGenerator.generate(words, blueprint(questionCount = 3))).hasSize(3)
        assertThat(ExamGenerator.generate(words, blueprint(questionCount = 100))).hasSize(30)
    }

    @Test
    fun `an empty pool produces an empty paper`() {
        assertThat(ExamGenerator.generate(emptyList(), blueprint())).isEmpty()
    }

    @Test
    fun `a pool without usable words produces an empty paper`() {
        val unusable = listOf(
            source(1L, word = "   ", meaning = "有释义"),
            source(2L, word = "apple", meaning = ""),
        )

        assertThat(ExamGenerator.generate(unusable, blueprint())).isEmpty()
    }

    @Test
    fun `a single word pool yields no choice question`() {
        val questions = ExamGenerator.generate(listOf(source(1L, "apple")), blueprint(questionCount = 5))

        assertThat(questions).isEmpty()
    }

    @Test
    fun `choice questions always record a usable correct option index`() {
        val words = pool(8)

        val choiceQuestions = ExamGenerator.generate(words, blueprint(questionCount = 6))
            .filter { it.type == ExamQuestionType.EN_TO_ZH || it.type == ExamQuestionType.ZH_TO_EN }

        assertThat(choiceQuestions).isNotEmpty()
        choiceQuestions.forEach { question ->
            assertThat(question.options.size).isAtLeast(2)
            assertThat(question.correctOptionIndex).isAtLeast(0)
            assertThat(question.correctOptionIndex).isLessThan(question.options.size)
            assertThat(question.options[question.correctOptionIndex]).isEqualTo(question.correctAnswer)
            assertThat(question.options.distinct()).hasSize(question.options.size)
        }
    }

    @Test
    fun `choice options are at most four and never fewer than two distinct ones`() {
        val words = pool(20)

        ExamGenerator.generate(words, blueprint(questionCount = 20)).forEach { question ->
            if (question.type != ExamQuestionType.SPELLING) {
                assertThat(question.options.size).isAtMost(ExamGenerator.MAX_OPTIONS)
                assertThat(question.options.size).isAtLeast(2)
                assertThat(question.options.distinct().size).isEqualTo(question.options.size)
            }
        }
    }

    @Test
    fun `english to chinese options are meaning labels and the prompt is the word`() {
        val words = listOf(
            source(1L, "apple", "苹果"),
            source(2L, "banana", "香蕉"),
            source(3L, "cherry", "樱桃"),
        )

        val question = ExamGenerator.generate(words, blueprint(questionCount = 1, seed = 5L)).single()
        assertThat(question.type).isEqualTo(ExamQuestionType.EN_TO_ZH)
        assertThat(question.prompt).isEqualTo("apple")
        assertThat(question.correctAnswer).isEqualTo("n. 苹果")
        assertThat(question.options[question.correctOptionIndex]).isEqualTo("n. 苹果")
    }

    @Test
    fun `chinese to english questions use the word as the answer`() {
        val words = pool(4)

        val question = ExamGenerator.generate(words, blueprint(questionCount = 2, seed = 1L))[1]
        assertThat(question.type).isEqualTo(ExamQuestionType.ZH_TO_EN)
        assertThat(question.prompt).isEqualTo("释义${question.wordId}")
        assertThat(question.correctAnswer).isEqualTo("word${question.wordId}")
        assertThat(question.options[question.correctOptionIndex]).isEqualTo(question.correctAnswer)
    }

    @Test
    fun `spelling questions are included when the pool is large enough`() {
        val words = pool(12)

        val questions = ExamGenerator.generate(words, blueprint(questionCount = 9))

        assertThat(questions.count { it.type == ExamQuestionType.SPELLING }).isAtLeast(1)
        questions.filter { it.type == ExamQuestionType.SPELLING }.forEach { question ->
            assertThat(question.options).isEmpty()
            assertThat(question.correctOptionIndex).isEqualTo(-1)
            assertThat(question.correctAnswer.isNotBlank()).isTrue()
            assertThat(question.prompt.isNotBlank()).isTrue()
        }
    }

    @Test
    fun `spelling questions are never emitted for a tiny pool`() {
        val words = pool(3)

        val questions = ExamGenerator.generate(words, blueprint(questionCount = 9))

        assertThat(questions).isNotEmpty()
        assertThat(questions.none { it.type == ExamQuestionType.SPELLING }).isTrue()
    }

    @Test
    fun `a word without a distinct distractor never becomes an unanswerable question`() {
        // Both entries share one meaning label, so no EN_TO_ZH distractor exists.
        val words = listOf(
            source(1L, "apple", "水果"),
            source(2L, "banana", "水果"),
            source(3L, "cherry", "水果"),
        )

        val questions = ExamGenerator.generate(words, blueprint(questionCount = 5, seed = 3L))

        assertThat(questions).isNotEmpty()
        questions.forEach { question ->
            if (question.type != ExamQuestionType.SPELLING) {
                assertThat(question.correctOptionIndex).isAtLeast(0)
                assertThat(question.options[question.correctOptionIndex])
                    .isEqualTo(question.correctAnswer)
            }
        }
    }

    @Test
    fun `duplicate word ids are collapsed into a single question source`() {
        val words = listOf(
            source(1L, "apple", "苹果"),
            source(1L, "apple", "苹果"),
            source(2L, "banana", "香蕉"),
        )

        val questions = ExamGenerator.generate(words, blueprint(questionCount = 10))

        assertThat(questions.map { it.wordId }.distinct()).hasSize(questions.size)
    }

    @Test
    fun `order index matches the position in the paper`() {
        val questions = ExamGenerator.generate(pool(10), blueprint(questionCount = 6))

        assertThat(questions.map { it.orderIndex }).containsExactly(0, 1, 2, 3, 4, 5).inOrder()
    }

    @Test
    fun `question count is clamped to at least one and never exceeds the pool`() {
        assertThat(ExamGenerator.generate(pool(5), blueprint(questionCount = 0))).hasSize(1)
        assertThat(ExamGenerator.generate(pool(5), blueprint(questionCount = -4))).hasSize(1)
        assertThat(ExamGenerator.generate(pool(5), blueprint(questionCount = 500))).hasSize(5)
    }

    @Test
    fun `question types are chosen from the supported set only`() {
        val questions = ExamGenerator.generate(pool(9), blueprint(questionCount = 9))

        assertThat(questions).isNotEmpty()
        assertThat(questions.all { it.type in ExamQuestionType.entries }).isTrue()
    }

    @Test
    fun `exam word source label joins part of speech and first meaning`() {
        val word = ExamWordSource(
            wordId = 1L,
            word = "apple",
            phonetic = "/ˈæpl/",
            partOfSpeech = "n.",
            meanings = listOf("苹果", "苹果树"),
        )

        assertThat(word.primaryMeaning).isEqualTo("苹果")
        assertThat(word.meaningLabel).isEqualTo("n. 苹果")

        val noPos = word.copy(partOfSpeech = "")
        assertThat(noPos.meaningLabel).isEqualTo("苹果")
    }

    @Test
    fun `exam question type ids round trip`() {
        assertThat(ExamQuestionType.fromId("SPELLING")).isEqualTo(ExamQuestionType.SPELLING)
        assertThat(ExamQuestionType.fromId("ZH_TO_EN")).isEqualTo(ExamQuestionType.ZH_TO_EN)
        assertThat(ExamQuestionType.fromId("nonsense")).isEqualTo(ExamQuestionType.EN_TO_ZH)
        assertThat(ExamQuestionType.fromId(null)).isEqualTo(ExamQuestionType.EN_TO_ZH)
        assertThat(ExamQuestionType.entries.map { it.id })
            .containsExactly("EN_TO_ZH", "ZH_TO_EN", "SPELLING")
    }
}
