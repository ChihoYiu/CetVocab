package com.cetvocab.app.domain.exam

/**
 * Grading of a closed-book exam.
 *
 * Everything here is a pure function, which is what makes the exam result trustworthy and
 * unit testable independently of the UI:
 *
 *  * a choice question is correct only when the selected index equals the recorded correct
 *    index — the answer is never inferred from the visible text;
 *  * a spelling question is correct when the typed word matches ignoring case, surrounding
 *    whitespace and repeated spaces; hyphens and apostrophes are compared exactly, because
 *    `well-being` and `wellbeing` are different words in a spelling test;
 *  * an unanswered question counts as wrong and is reported separately so the result screen
 *    can be honest about it.
 */
object ExamScoring {

    /** Normalises a free-text answer for a spelling question. */
    fun normalizeSpelling(answer: String?): String = answer
        ?.trim()
        ?.lowercase()
        ?.replace(Regex("\\s+"), " ")
        .orEmpty()

    /** True when [givenAnswer] answers [question] correctly. */
    fun isCorrect(question: ExamQuestion, givenAnswer: String?): Boolean = when (question.type) {
        ExamQuestionType.SPELLING -> {
            val normalized = normalizeSpelling(givenAnswer)
            normalized.isNotEmpty() && normalized == normalizeSpelling(question.correctAnswer)
        }

        else -> {
            val index = givenAnswer?.toIntOrNull() ?: return false
            index >= 0 && index == question.correctOptionIndex
        }
    }

    /**
     * Grades the whole paper.
     *
     * @param questions the frozen snapshots, in display order
     * @param answers questionId -> given answer (option index as text, or the typed word)
     * @param startedAtMillis when the learner started (used for the elapsed time)
     * @param submittedAtMillis when the paper was handed in
     */
    fun score(
        questions: List<ExamQuestion>,
        answers: Map<Long, String>,
        startedAtMillis: Long,
        submittedAtMillis: Long,
    ): ExamScore {
        val results = questions.map { question ->
            val given = answers[question.id]?.takeIf { it.isNotBlank() }
            QuestionResult(
                questionId = question.id,
                orderIndex = question.orderIndex,
                wordId = question.wordId,
                type = question.type,
                prompt = question.prompt,
                correctAnswer = question.correctAnswer,
                givenAnswer = given,
                isCorrect = isCorrect(question, given),
            )
        }

        val total = results.size
        val correct = results.count { it.isCorrect }
        val answered = results.count { it.givenAnswer != null }
        val unanswered = total - answered
        val wrong = total - correct
        val accuracy = if (total == 0) 0 else correct * 100 / total

        val perType = ExamQuestionType.entries.mapNotNull { type ->
            val ofType = results.filter { it.type == type }
            if (ofType.isEmpty()) {
                null
            } else {
                TypeAccuracy(type = type, correct = ofType.count { it.isCorrect }, total = ofType.size)
            }
        }

        return ExamScore(
            totalQuestions = total,
            correctCount = correct,
            wrongCount = wrong,
            unansweredCount = unanswered,
            score = accuracy,
            accuracyPercent = accuracy,
            durationMillis = (submittedAtMillis - startedAtMillis).coerceAtLeast(0L),
            perType = perType,
            wrongWordIds = results.filter { !it.isCorrect }.map { it.wordId }.distinct(),
            weakTypes = perType
                .filter { it.percent < ExamScore.WEAK_TYPE_THRESHOLD_PERCENT }
                .map { it.type },
            results = results,
        )
    }
}
