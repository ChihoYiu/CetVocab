package com.cetvocab.app.domain.exam

/**
 * Question types of the closed-book exam.
 *
 * All four required formats are covered: 英文选中文 ([EN_TO_ZH]), 中文选英文 ([ZH_TO_EN]),
 * 四选一 (either choice type, four options) and 拼写填空 ([SPELLING]).
 */
enum class ExamQuestionType(val id: String, val label: String) {
    EN_TO_ZH("EN_TO_ZH", "英译中"),
    ZH_TO_EN("ZH_TO_EN", "中译英"),
    SPELLING("SPELLING", "拼写填空");

    companion object {
        fun fromId(id: String?): ExamQuestionType =
            entries.firstOrNull { it.id == id } ?: EN_TO_ZH
    }
}

/** Minimal word projection the exam generator needs — keeps the generator pure. */
data class ExamWordSource(
    val wordId: Long,
    val word: String,
    val phonetic: String,
    val partOfSpeech: String,
    val meanings: List<String>,
) {
    val primaryMeaning: String get() = meanings.firstOrNull().orEmpty()
    val meaningLabel: String
        get() = listOf(partOfSpeech.trim(), primaryMeaning).filter { it.isNotEmpty() }.joinToString(" ")
}

/**
 * One frozen exam question.
 *
 * The snapshot contains the prompt, the options and the canonical answer, and it is written
 * to the database before the first question is displayed: editing the word list while an exam
 * is in progress can therefore never change the paper.
 */
data class ExamQuestion(
    val id: Long = 0L,
    val orderIndex: Int,
    val wordId: Long,
    val type: ExamQuestionType,
    val prompt: String,
    val promptPhonetic: String = "",
    val options: List<String> = emptyList(),
    val correctOptionIndex: Int = -1,
    val correctAnswer: String,
    val givenAnswer: String? = null,
    val isCorrect: Boolean? = null,
)

/** Exam configuration chosen on the setup screen. */
data class ExamBlueprint(
    val levelId: String,
    val questionCount: Int,
    val durationMinutes: Int,
    val seed: Long,
)

/** Percentage of correct answers per question type, shown on the result screen. */
data class TypeAccuracy(
    val type: ExamQuestionType,
    val correct: Int,
    val total: Int,
) {
    val percent: Int get() = if (total == 0) 0 else (correct * 100) / total
}

/**
 * The graded result of an exam. Pure data: grading never touches the UI or the database.
 */
data class ExamScore(
    val totalQuestions: Int,
    val correctCount: Int,
    val wrongCount: Int,
    val unansweredCount: Int,
    /** 总分, 0..100, computed as correct / total. */
    val score: Int,
    val accuracyPercent: Int,
    val durationMillis: Long,
    val perType: List<TypeAccuracy>,
    /** Words to add to the mistake book. */
    val wrongWordIds: List<Long>,
    /** Question types the learner should practise: below [WEAK_TYPE_THRESHOLD_PERCENT]. */
    val weakTypes: List<ExamQuestionType>,
    val results: List<QuestionResult>,
) {
    companion object {
        const val WEAK_TYPE_THRESHOLD_PERCENT = 60
    }
}

/** Grading of a single question. */
data class QuestionResult(
    val questionId: Long,
    val orderIndex: Int,
    val wordId: Long,
    val type: ExamQuestionType,
    val prompt: String,
    val correctAnswer: String,
    val givenAnswer: String?,
    val isCorrect: Boolean,
)
