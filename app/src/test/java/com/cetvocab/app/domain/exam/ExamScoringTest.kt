package com.cetvocab.app.domain.exam

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Unit tests for the exam grading rules.
 *
 * The questions are hand-built so that every expected number (for example the 25% and 75%
 * accuracy cases) can be verified by reading the test.
 */
class ExamScoringTest {

    private val startedAt = 1_768_450_800_000L
    private val submittedAt = startedAt + 300_000L

    private fun choiceQuestion(
        id: Long,
        orderIndex: Int,
        wordId: Long,
        type: ExamQuestionType = ExamQuestionType.EN_TO_ZH,
        prompt: String = "word$wordId",
        correctAnswer: String = "释义$wordId",
        options: List<String> = listOf("释义$wordId", "别的释义", "另一个释义", "第四个释义"),
    ): ExamQuestion = ExamQuestion(
        id = id,
        orderIndex = orderIndex,
        wordId = wordId,
        type = type,
        prompt = prompt,
        options = options,
        correctOptionIndex = options.indexOf(correctAnswer),
        correctAnswer = correctAnswer,
    )

    private fun spellingQuestion(
        id: Long,
        orderIndex: Int,
        wordId: Long,
        prompt: String = "释义$wordId",
        correctAnswer: String = "word$wordId",
    ): ExamQuestion = ExamQuestion(
        id = id,
        orderIndex = orderIndex,
        wordId = wordId,
        type = ExamQuestionType.SPELLING,
        prompt = prompt,
        options = emptyList(),
        correctOptionIndex = -1,
        correctAnswer = correctAnswer,
    )

    private fun score(questions: List<ExamQuestion>, answers: Map<Long, String>) =
        ExamScoring.score(questions, answers, startedAt, submittedAt)

    // --- Choice questions ------------------------------------------------------------------

    @Test
    fun `a choice question is correct only for the recorded option index`() {
        val question = choiceQuestion(1L, 0, 11L, options = listOf("a", "b", "c", "d"), correctAnswer = "c")

        assertThat(ExamScoring.isCorrect(question, "2")).isTrue()
        assertThat(ExamScoring.isCorrect(question, "0")).isFalse()
        assertThat(ExamScoring.isCorrect(question, "c")).isFalse()
        assertThat(ExamScoring.isCorrect(question, null)).isFalse()
        assertThat(ExamScoring.isCorrect(question, "")).isFalse()
    }

    @Test
    fun `an out of range or malformed choice answer is wrong`() {
        val question = choiceQuestion(1L, 0, 11L)

        assertThat(ExamScoring.isCorrect(question, "-1")).isFalse()
        assertThat(ExamScoring.isCorrect(question, "99")).isFalse()
        assertThat(ExamScoring.isCorrect(question, "abc")).isFalse()
    }

    @Test
    fun `choice answers are graded for the whole paper`() {
        val questions = listOf(
            choiceQuestion(1L, 0, 11L),
            choiceQuestion(2L, 1, 12L),
        )

        val paper = score(questions, mapOf(1L to "0", 2L to "1"))

        assertThat(paper.totalQuestions).isEqualTo(2)
        assertThat(paper.correctCount).isEqualTo(1)
        assertThat(paper.wrongCount).isEqualTo(1)
        assertThat(paper.unansweredCount).isEqualTo(0)
        assertThat(paper.score).isEqualTo(50)
        assertThat(paper.accuracyPercent).isEqualTo(50)
        assertThat(paper.wrongWordIds).containsExactly(12L)
        assertThat(paper.results.map { it.isCorrect }).containsExactly(true, false).inOrder()
    }

    // --- Spelling questions ----------------------------------------------------------------

    @Test
    fun `spelling ignores case and surrounding whitespace`() {
        val question = spellingQuestion(1L, 0, 11L, correctAnswer = "apple")

        assertThat(ExamScoring.isCorrect(question, "apple")).isTrue()
        assertThat(ExamScoring.isCorrect(question, "APPLE")).isTrue()
        assertThat(ExamScoring.isCorrect(question, "  Apple  ")).isTrue()
        assertThat(ExamScoring.isCorrect(question, "\tapple\n")).isTrue()
    }

    @Test
    fun `spelling ignores repeated inner spaces`() {
        val question = spellingQuestion(1L, 0, 11L, correctAnswer = "look after")

        assertThat(ExamScoring.isCorrect(question, "look   after")).isTrue()
        assertThat(ExamScoring.isCorrect(question, "  LOOK    AFTER ")).isTrue()
    }

    @Test
    fun `spelling is wrong for a different word`() {
        val question = spellingQuestion(1L, 0, 11L, correctAnswer = "apple")

        assertThat(ExamScoring.isCorrect(question, "apples")).isFalse()
        assertThat(ExamScoring.isCorrect(question, "banana")).isFalse()
        assertThat(ExamScoring.isCorrect(question, "appl")).isFalse()
        assertThat(ExamScoring.isCorrect(question, "app le")).isFalse()
        assertThat(ExamScoring.isCorrect(question, null)).isFalse()
        assertThat(ExamScoring.isCorrect(question, "   ")).isFalse()
    }

    @Test
    fun `spelling compares hyphens exactly`() {
        val question = spellingQuestion(1L, 0, 11L, correctAnswer = "well-being")

        assertThat(ExamScoring.isCorrect(question, "well-being")).isTrue()
        assertThat(ExamScoring.isCorrect(question, "wellbeing")).isFalse()
    }

    @Test
    fun `normalize spelling trims lowercases and collapses spaces`() {
        assertThat(ExamScoring.normalizeSpelling("  Look   After ")).isEqualTo("look after")
        assertThat(ExamScoring.normalizeSpelling(null)).isEmpty()
        assertThat(ExamScoring.normalizeSpelling("")).isEmpty()
    }

    // --- Unanswered ------------------------------------------------------------------------

    @Test
    fun `an unanswered question counts as wrong and is reported separately`() {
        val questions = listOf(
            choiceQuestion(1L, 0, 11L),
            spellingQuestion(2L, 1, 12L),
        )

        val paper = score(questions, mapOf(1L to "0"))

        assertThat(paper.unansweredCount).isEqualTo(1)
        assertThat(paper.correctCount).isEqualTo(1)
        assertThat(paper.wrongCount).isEqualTo(1)
        assertThat(paper.results.single { it.questionId == 2L }.givenAnswer).isNull()
        assertThat(paper.results.single { it.questionId == 2L }.isCorrect).isFalse()
    }

    @Test
    fun `a blank answer is treated as unanswered`() {
        val questions = listOf(choiceQuestion(1L, 0, 11L), choiceQuestion(2L, 1, 12L))

        val paper = score(questions, mapOf(1L to "   ", 2L to "0"))

        assertThat(paper.unansweredCount).isEqualTo(1)
        assertThat(paper.results.single { it.questionId == 1L }.givenAnswer).isNull()
        assertThat(paper.accuracyPercent).isEqualTo(50)
    }

    @Test
    fun `an empty paper scores zero and does not divide by zero`() {
        val paper = score(emptyList(), emptyMap())

        assertThat(paper.totalQuestions).isEqualTo(0)
        assertThat(paper.score).isEqualTo(0)
        assertThat(paper.accuracyPercent).isEqualTo(0)
        assertThat(paper.wrongCount).isEqualTo(0)
        assertThat(paper.unansweredCount).isEqualTo(0)
        assertThat(paper.perType).isEmpty()
        assertThat(paper.weakTypes).isEmpty()
        assertThat(paper.wrongWordIds).isEmpty()
    }

    // --- Aggregates ------------------------------------------------------------------------

    @Test
    fun `score and accuracy are percentages of the whole paper`() {
        val questions = listOf(
            choiceQuestion(1L, 0, 11L),
            choiceQuestion(2L, 1, 12L),
            choiceQuestion(3L, 2, 13L),
            choiceQuestion(4L, 3, 14L),
        )

        val quarter = score(questions, mapOf(1L to "0"))
        val threeQuarters = score(questions, mapOf(1L to "0", 2L to "0", 3L to "0"))

        assertThat(quarter.score).isEqualTo(25)
        assertThat(quarter.accuracyPercent).isEqualTo(25)
        assertThat(quarter.wrongWordIds).containsExactly(12L, 13L, 14L)
        assertThat(threeQuarters.score).isEqualTo(75)
        assertThat(threeQuarters.accuracyPercent).isEqualTo(75)
    }

    @Test
    fun `wrong word ids are de-duplicated and keep the paper order`() {
        val questions = listOf(
            choiceQuestion(1L, 0, 11L),
            choiceQuestion(2L, 1, 11L),
            choiceQuestion(3L, 2, 12L),
            choiceQuestion(4L, 3, 13L),
        )

        val paper = score(questions, mapOf(1L to "0", 4L to "0"))

        assertThat(paper.wrongWordIds).containsExactly(11L, 12L).inOrder()
    }

    @Test
    fun `per type accuracy is reported only for the types present`() {
        val questions = listOf(
            choiceQuestion(1L, 0, 11L, type = ExamQuestionType.EN_TO_ZH),
            choiceQuestion(2L, 1, 12L, type = ExamQuestionType.EN_TO_ZH),
            choiceQuestion(3L, 2, 13L, type = ExamQuestionType.ZH_TO_EN),
            spellingQuestion(4L, 3, 14L, correctAnswer = "apple"),
        )

        val paper = score(
            questions,
            // Question 3 is a ZH_TO_EN item whose correct option is index 0, so answering "1"
            // makes it the one wrong question of its type; question 2 is left unanswered.
            mapOf(1L to "0", 3L to "1", 4L to "Apple"),
        )

        assertThat(paper.perType.map { it.type }).containsExactly(
            ExamQuestionType.EN_TO_ZH,
            ExamQuestionType.ZH_TO_EN,
            ExamQuestionType.SPELLING,
        ).inOrder()
        assertThat(paper.perType.single { it.type == ExamQuestionType.EN_TO_ZH }.percent).isEqualTo(50)
        assertThat(paper.perType.single { it.type == ExamQuestionType.ZH_TO_EN }.percent).isEqualTo(0)
        assertThat(paper.perType.single { it.type == ExamQuestionType.SPELLING }.percent).isEqualTo(100)
        assertThat(paper.perType.single { it.type == ExamQuestionType.EN_TO_ZH }.total).isEqualTo(2)
        assertThat(paper.perType.single { it.type == ExamQuestionType.EN_TO_ZH }.correct).isEqualTo(1)

        // A type the paper never asked about is not reported at all (it is not an accuracy of
        // 0%), which is what lets the result screen show only the skills that were tested.
        val singleTypePaper = score(
            listOf(choiceQuestion(9L, 0, 99L, type = ExamQuestionType.ZH_TO_EN)),
            mapOf(9L to "0"),
        )
        assertThat(singleTypePaper.perType.map { it.type }).containsExactly(ExamQuestionType.ZH_TO_EN)
    }

    @Test
    fun `weak types are the ones below sixty percent`() {
        val questions = listOf(
            choiceQuestion(1L, 0, 11L, type = ExamQuestionType.EN_TO_ZH),
            choiceQuestion(2L, 1, 12L, type = ExamQuestionType.EN_TO_ZH),
            choiceQuestion(3L, 2, 13L, type = ExamQuestionType.EN_TO_ZH),
            choiceQuestion(4L, 3, 14L, type = ExamQuestionType.ZH_TO_EN),
            choiceQuestion(5L, 4, 15L, type = ExamQuestionType.ZH_TO_EN),
        )

        // EN_TO_ZH: 1/3 = 33% (weak), ZH_TO_EN: 2/2 = 100% (not weak).
        val paper = score(questions, mapOf(1L to "0", 4L to "0", 5L to "0"))

        assertThat(paper.weakTypes).containsExactly(ExamQuestionType.EN_TO_ZH)
        assertThat(ExamScore.WEAK_TYPE_THRESHOLD_PERCENT).isEqualTo(60)
    }

    @Test
    fun `a type exactly at sixty percent is not weak`() {
        val questions = listOf(
            choiceQuestion(1L, 0, 11L, type = ExamQuestionType.ZH_TO_EN),
            choiceQuestion(2L, 1, 12L, type = ExamQuestionType.ZH_TO_EN),
            choiceQuestion(3L, 2, 13L, type = ExamQuestionType.ZH_TO_EN),
            choiceQuestion(4L, 3, 14L, type = ExamQuestionType.ZH_TO_EN),
            choiceQuestion(5L, 4, 15L, type = ExamQuestionType.ZH_TO_EN),
        )

        val paper = score(questions, mapOf(1L to "0", 2L to "0", 3L to "0"))

        assertThat(paper.perType.single().percent).isEqualTo(60)
        assertThat(paper.weakTypes).isEmpty()
    }

    @Test
    fun `the per type percent of an empty type is zero`() {
        assertThat(TypeAccuracy(ExamQuestionType.SPELLING, correct = 0, total = 0).percent).isEqualTo(0)
        assertThat(TypeAccuracy(ExamQuestionType.SPELLING, correct = 2, total = 4).percent).isEqualTo(50)
    }

    @Test
    fun `duration is the elapsed time and never negative`() {
        val questions = listOf(choiceQuestion(1L, 0, 11L))

        assertThat(score(questions, mapOf(1L to "0")).durationMillis).isEqualTo(300_000L)
        assertThat(
            ExamScoring.score(questions, mapOf(1L to "0"), startedAt, startedAt - 5_000L).durationMillis,
        ).isEqualTo(0L)
    }

    @Test
    fun `results carry the question snapshot and the given answer`() {
        // "苹果" must be one of the offered options: a choice question is graded by option
        // index, so a correct answer that is not in the list could never be selected.
        val question = choiceQuestion(
            id = 1L,
            orderIndex = 0,
            wordId = 11L,
            prompt = "apple",
            correctAnswer = "苹果",
            options = listOf("苹果", "苹果树", "苹果汁", "苹果派"),
        )
        val paper = score(listOf(question), mapOf(1L to "0"))

        val result: QuestionResult = paper.results.single()
        assertThat(result.questionId).isEqualTo(1L)
        assertThat(result.orderIndex).isEqualTo(0)
        assertThat(result.wordId).isEqualTo(11L)
        assertThat(result.type).isEqualTo(ExamQuestionType.EN_TO_ZH)
        assertThat(result.prompt).isEqualTo("apple")
        assertThat(result.correctAnswer).isEqualTo("苹果")
        assertThat(result.givenAnswer).isEqualTo("0")
        assertThat(result.isCorrect).isTrue()
    }
}
