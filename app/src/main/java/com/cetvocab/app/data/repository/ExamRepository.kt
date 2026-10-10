package com.cetvocab.app.data.repository

import androidx.room.withTransaction
import com.cetvocab.app.core.result.AppError
import com.cetvocab.app.core.result.Outcome
import com.cetvocab.app.core.time.TimeProvider
import com.cetvocab.app.data.local.CetVocabDatabase
import com.cetvocab.app.data.local.entity.ExamSessionEntity
import com.cetvocab.app.data.mapper.toDomain
import com.cetvocab.app.data.mapper.toEntity
import com.cetvocab.app.data.mapper.toJson
import com.cetvocab.app.data.mapper.toScore
import com.cetvocab.app.domain.exam.ExamBlueprint
import com.cetvocab.app.domain.exam.ExamGenerator
import com.cetvocab.app.domain.exam.ExamQuestion
import com.cetvocab.app.domain.exam.ExamScore
import com.cetvocab.app.domain.exam.ExamScoring
import com.cetvocab.app.domain.exam.ExamWordSource
import com.cetvocab.app.domain.exam.QuestionResult
import com.cetvocab.app.domain.srs.ReviewGrade
import kotlinx.coroutines.flow.Flow

/** A draft or submitted exam together with its frozen questions. */
data class ExamDraft(
    val session: ExamSessionEntity,
    val questions: List<ExamQuestion>,
)

/** Exam status values stored in `exam_session.status`. */
object ExamStatus {
    const val DRAFT = "DRAFT"
    const val SUBMITTED = "SUBMITTED"
    const val ABANDONED = "ABANDONED"
}

/**
 * Closed-book exams.
 *
 * The paper is generated once (from a seed) and written to the database *before* the exam is
 * shown, which gives three guarantees the spec asks for:
 *  * the questions cannot change while the exam is in progress, even if the word list is
 *    edited in another process;
 *  * an interrupted exam (phone call, process death, reboot) resumes as a draft with the same
 *    questions and the same remaining time;
 *  * submitting twice is impossible — the status is checked inside the transaction.
 */
interface ExamRepository {

    /** Generates and stores a new paper. Fails when the word list is too small. */
    suspend fun createDraft(blueprint: ExamBlueprint, words: List<ExamWordSource>): Outcome<ExamDraft>

    suspend fun draft(): ExamDraft?

    suspend fun draftById(examId: Long): ExamDraft?

    suspend fun saveAnswer(examId: Long, questionId: Long, answer: String?)

    suspend fun saveRemainingSeconds(examId: Long, seconds: Int)

    /** Grades the paper, stores the result and feeds the schedule + mistake book. */
    suspend fun submit(examId: Long, answers: Map<Long, String>): Outcome<ExamScore>

    suspend fun abandon(examId: Long)

    suspend fun scoreFor(examId: Long): ExamScore?

    suspend fun questions(examId: Long): List<ExamQuestion>

    fun observeHistory(limit: Int = 20): Flow<List<ExamSessionEntity>>

    suspend fun history(limit: Int = 20): List<ExamSessionEntity>

    /** Abandons stale drafts (used during "清空学习进度"). */
    suspend fun abandonAllDrafts()
}

class DefaultExamRepository(
    private val db: CetVocabDatabase,
    private val time: TimeProvider,
    private val recorder: ProgressRecorder,
) : ExamRepository {

    override suspend fun createDraft(
        blueprint: ExamBlueprint,
        words: List<ExamWordSource>,
    ): Outcome<ExamDraft> {
        val questions = ExamGenerator.generate(words, blueprint)
        if (questions.isEmpty()) {
            return Outcome.Failure(
                AppError(
                    code = com.cetvocab.app.core.result.ErrorCode.EMPTY,
                    message = "当前词库中的可用单词太少，无法生成闭卷考试。请先导入词库。",
                ),
            )
        }
        val now = time.nowMillis()
        return try {
            val draft = db.withTransaction {
                // A single active draft at a time keeps "恢复考试" unambiguous.
                db.examDao().abandonAllDrafts(now)
                val sessionId = db.examDao().insertSession(
                    ExamSessionEntity(
                        level = blueprint.levelId,
                        status = ExamStatus.DRAFT,
                        createdAt = now,
                        startedAt = now,
                        submittedAt = null,
                        questionCount = questions.size,
                        durationMinutes = blueprint.durationMinutes,
                        remainingSeconds = blueprint.durationMinutes * 60,
                        score = null,
                        correctCount = null,
                        accuracyPercent = null,
                        timeSpentMs = null,
                        typeBreakdown = null,
                        seed = blueprint.seed,
                    ),
                )
                db.examDao().insertQuestions(questions.map { it.toEntity(sessionId) })
                val stored = db.examDao().questions(sessionId).map { it.toDomain() }
                ExamDraft(session = db.examDao().sessionById(sessionId)!!, questions = stored)
            }
            Outcome.Success(draft)
        } catch (throwable: Throwable) {
            Outcome.Failure(AppError.database(throwable.javaClass.simpleName))
        }
    }

    override suspend fun draft(): ExamDraft? {
        val session = db.examDao().draft() ?: return null
        return ExamDraft(session = session, questions = db.examDao().questions(session.id).map { it.toDomain() })
    }

    override suspend fun draftById(examId: Long): ExamDraft? {
        val session = db.examDao().sessionById(examId) ?: return null
        return ExamDraft(session = session, questions = db.examDao().questions(session.id).map { it.toDomain() })
    }

    override suspend fun saveAnswer(examId: Long, questionId: Long, answer: String?) {
        val entity = db.examDao().questions(examId).firstOrNull { it.id == questionId } ?: return
        val normalized = answer?.trim()?.ifEmpty { null }
        db.examDao().updateQuestion(
            entity.copy(
                givenAnswer = normalized,
                // Correctness is deliberately *not* evaluated here: nothing about the answer
                // key may be written where a UI layer could leak it before submission.
                isCorrect = null,
                answeredAt = if (normalized == null) null else time.nowMillis(),
            ),
        )
    }

    override suspend fun saveRemainingSeconds(examId: Long, seconds: Int) {
        val session = db.examDao().sessionById(examId) ?: return
        if (session.status != ExamStatus.DRAFT) return
        db.examDao().updateSession(session.copy(remainingSeconds = seconds.coerceAtLeast(0)))
    }

    override suspend fun submit(examId: Long, answers: Map<Long, String>): Outcome<ExamScore> {
        val session = db.examDao().sessionById(examId)
            ?: return Outcome.Failure(AppError.database("考试不存在"))
        if (session.status != ExamStatus.DRAFT) {
            // Guards against a double submit from a fast double tap or a restored fragment.
            return Outcome.Failure(
                AppError(
                    code = com.cetvocab.app.core.result.ErrorCode.DATABASE,
                    message = "这份试卷已经交卷，不能重复提交。",
                ),
            )
        }

        val now = time.nowMillis()
        val questions = db.examDao().questions(examId).map { it.toDomain() }
        val merged = questions.associate { question ->
            question.id to (answers[question.id] ?: question.givenAnswer ?: "")
        }
        val score = ExamScoring.score(questions, merged, session.startedAt, now)

        return try {
            db.withTransaction {
                score.results.forEach { result ->
                    val entity = db.examDao().questions(examId).first { it.id == result.questionId }
                    db.examDao().updateQuestion(
                        entity.copy(
                            givenAnswer = result.givenAnswer,
                            isCorrect = result.isCorrect,
                            answeredAt = if (result.givenAnswer == null) null else now,
                        ),
                    )
                }
                db.examDao().updateSession(
                    session.copy(
                        status = ExamStatus.SUBMITTED,
                        submittedAt = now,
                        remainingSeconds = 0,
                        score = score.score,
                        correctCount = score.correctCount,
                        accuracyPercent = score.accuracyPercent,
                        timeSpentMs = score.durationMillis,
                        typeBreakdown = score.perType.toJson(),
                    ),
                )

                // Only *attempted* questions change the schedule: leaving a question blank is
                // recorded in the result but does not pretend the learner studied that word.
                score.results
                    .filter { it.givenAnswer != null }
                    .forEach { result ->
                        recorder.record(
                            wordId = result.wordId,
                            grade = ReviewGrade.fromExam(result.isCorrect),
                            source = AnswerSource.EXAM,
                            sessionId = null,
                            responseTimeMs = null,
                        )
                    }

                recorder.addExamResult(
                    submittedAtMillis = now,
                    levelId = session.level,
                    correct = score.correctCount,
                    total = score.totalQuestions,
                    durationMillis = score.durationMillis,
                )
            }
            Outcome.Success(score)
        } catch (throwable: Throwable) {
            Outcome.Failure(AppError.database(throwable.javaClass.simpleName))
        }
    }

    override suspend fun abandon(examId: Long) {
        db.examDao().abandon(examId, time.nowMillis())
    }

    override suspend fun scoreFor(examId: Long): ExamScore? {
        val session = db.examDao().sessionById(examId) ?: return null
        val questions = db.examDao().questions(examId).map { it.toDomain() }
        val base = session.toScore(questions) ?: return null
        // Keep the full question list in the result so the result screen survives a process
        // restart and can still show exactly which words were answered wrongly.
        val results = questions.map { question ->
            QuestionResult(
                questionId = question.id,
                orderIndex = question.orderIndex,
                wordId = question.wordId,
                type = question.type,
                prompt = question.prompt,
                correctAnswer = question.correctAnswer,
                givenAnswer = question.givenAnswer,
                isCorrect = question.isCorrect == true,
            )
        }
        return base.copy(results = results)
    }

    override suspend fun questions(examId: Long): List<ExamQuestion> =
        db.examDao().questions(examId).map { it.toDomain() }

    override fun observeHistory(limit: Int): Flow<List<ExamSessionEntity>> =
        db.examDao().observeHistory(limit)

    override suspend fun history(limit: Int): List<ExamSessionEntity> = db.examDao().history(limit)

    override suspend fun abandonAllDrafts() {
        db.examDao().abandonAllDrafts(time.nowMillis())
    }
}
