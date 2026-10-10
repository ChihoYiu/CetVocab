package com.cetvocab.app.data.repository

import androidx.room.withTransaction
import com.cetvocab.app.core.time.StudyDay
import com.cetvocab.app.core.time.TimeProvider
import com.cetvocab.app.data.local.CetVocabDatabase
import com.cetvocab.app.data.local.entity.DailyStudySummaryEntity
import com.cetvocab.app.data.local.entity.ReviewEventEntity
import com.cetvocab.app.data.local.entity.WrongAnswerEntity
import com.cetvocab.app.data.mapper.newProgressFor
import com.cetvocab.app.data.mapper.toDomain
import com.cetvocab.app.data.mapper.toEntity
import com.cetvocab.app.data.mapper.toId
import com.cetvocab.app.domain.model.CetLevel
import com.cetvocab.app.domain.srs.ReviewGrade
import com.cetvocab.app.domain.srs.SpacedRepetition
import com.cetvocab.app.domain.srs.WordProgress

/** Where an answer came from. Stored on every review event and used by the statistics. */
enum class AnswerSource(val id: String) {
    STUDY("STUDY"),
    IMMEDIATE("IMMEDIATE"),
    DRILL("DRILL"),
    EXAM("EXAM"),
}

/**
 * Writes one answer to the database.
 *
 * This is the single place where a learner's answer changes the world, and it runs inside a
 * Room transaction so that progress, the answer log, the mistake book and the daily rollup
 * can never drift apart — even if the process is killed in the middle of a study session.
 *
 * The schedule itself is computed by [SpacedRepetition]; this class only persists its
 * decision.
 */
class ProgressRecorder(
    private val db: CetVocabDatabase,
    private val time: TimeProvider,
) {

    /**
     * @param wordId the word being answered
     * @param grade the learner's self-assessment (an exam answer is mapped by the caller)
     * @param source which screen produced the answer
     * @param sessionId study session this answer belongs to, if any
     * @param responseTimeMs how long the card was on screen, when known
     */
    suspend fun record(
        wordId: Long,
        grade: ReviewGrade,
        source: AnswerSource,
        sessionId: Long? = null,
        responseTimeMs: Long? = null,
    ): WordProgress = db.withTransaction {
        val now = time.nowMillis()
        val zone = time.zone()
        val dayKey = StudyDay.dayKey(now, zone)

        val existingEntity = db.progressDao().byWordId(wordId)
        val previous = existingEntity?.toDomain() ?: newProgressFor(wordId, now)
        val isFirstAnswerForWordToday = db.reviewEventDao().countForWordOnDay(wordId, dayKey) == 0
        // A word counts as "new" only when this is the very first time it is answered at all:
        // the progress row did not exist before this call. Deriving it from the row instead of
        // from the grade is what keeps one word from being booked as both a new word and a
        // review word on the same day.
        val isNewWord = existingEntity == null

        val updated = SpacedRepetition.apply(previous, grade, now)

        db.progressDao().upsert(updated.toEntity(now))
        db.reviewEventDao().insert(
            ReviewEventEntity(
                wordId = wordId,
                sessionId = sessionId,
                grade = grade.toId(),
                source = source.id,
                answeredAt = now,
                dayKey = dayKey,
                level = levelIdFor(wordId),
                previousIntervalDays = previous.intervalDays,
                newIntervalDays = updated.intervalDays,
                responseTimeMs = responseTimeMs,
            ),
        )

        updateMistakeBook(wordId, grade, source, now)
        bumpDailySummary(
            dayKey = dayKey,
            levelId = levelIdFor(wordId),
            grade = grade,
            firstTouchToday = isFirstAnswerForWordToday,
            isNewWord = isNewWord,
            now = now,
        )
        updated
    }

    private suspend fun levelIdFor(wordId: Long): String {
        val entity = db.wordDao().byId(wordId) ?: return CetLevel.CET4.id
        return entity.level
    }

    /**
     * Mistake book rules:
     *  * 不认识 / exam mistakes increment the counter and re-open a resolved row;
     *  * 模糊 is tracked separately so "hard but known" is distinguishable from "wrong";
     *  * three consecutive 已掌握 answers auto-mark a row as re-consolidated (the row is kept
     *    for history, and the user can still do it manually from the review centre).
     */
    private suspend fun updateMistakeBook(
        wordId: Long,
        grade: ReviewGrade,
        source: AnswerSource,
        now: Long,
    ) {
        val existing = db.mistakeBookDao().byWordId(wordId)
        val levelId = levelIdFor(wordId)
        when (grade) {
            ReviewGrade.UNKNOWN -> {
                val wrong = existing?.copy(
                    wrongCount = existing.wrongCount + 1,
                    lastWrongAt = now,
                    lastSource = source.id,
                    correctSinceWrong = 0,
                    resolvedAt = null,
                    level = levelId,
                    updatedAt = now,
                ) ?: WrongAnswerEntity(
                    wordId = wordId,
                    wrongCount = 1,
                    fuzzyCount = 0,
                    firstWrongAt = now,
                    lastWrongAt = now,
                    lastSource = source.id,
                    correctSinceWrong = 0,
                    resolvedAt = null,
                    isFavorite = false,
                    level = levelId,
                    updatedAt = now,
                )
                db.mistakeBookDao().upsert(wrong)
            }

            ReviewGrade.FUZZY -> {
                if (existing != null) {
                    db.mistakeBookDao().upsert(
                        existing.copy(
                            fuzzyCount = existing.fuzzyCount + 1,
                            lastWrongAt = now,
                            lastSource = source.id,
                            correctSinceWrong = 0,
                            updatedAt = now,
                        ),
                    )
                }
            }

            ReviewGrade.MASTERED -> {
                if (existing != null && existing.resolvedAt == null) {
                    val streak = existing.correctSinceWrong + 1
                    db.mistakeBookDao().upsert(
                        existing.copy(
                            correctSinceWrong = streak,
                            resolvedAt = if (streak >= AUTO_RESOLVE_STREAK) now else null,
                            updatedAt = now,
                        ),
                    )
                }
            }
        }
    }

    /**
     * Read-modify-write of one day bucket; safe because the caller holds the transaction.
     *
     * Counting rules (each number means exactly one thing, so the statistics screen can be
     * honest about them):
     *  * `studiedWords` — distinct words whose first answer of the day this was;
     *  * `newWords` — of those, the ones that had never been answered before today's card;
     *  * `reviewWords` — of those, the ones that already had progress (a genuine review);
     *  * the grade counters count *answers*, not words, and therefore may exceed the above.
     */
    private suspend fun bumpDailySummary(
        dayKey: String,
        levelId: String,
        grade: ReviewGrade,
        firstTouchToday: Boolean,
        isNewWord: Boolean,
        now: Long,
    ) {
        val existing = db.dailySummaryDao().get(dayKey, levelId) ?: DailyStudySummaryEntity(
            dayKey = dayKey,
            level = levelId,
        )
        db.dailySummaryDao().upsert(
            existing.copy(
                studiedWords = existing.studiedWords + if (firstTouchToday) 1 else 0,
                newWords = existing.newWords + if (firstTouchToday && isNewWord) 1 else 0,
                reviewWords = existing.reviewWords + if (firstTouchToday && !isNewWord) 1 else 0,
                masteredCount = existing.masteredCount + if (grade == ReviewGrade.MASTERED) 1 else 0,
                fuzzyCount = existing.fuzzyCount + if (grade == ReviewGrade.FUZZY) 1 else 0,
                unknownCount = existing.unknownCount + if (grade == ReviewGrade.UNKNOWN) 1 else 0,
                reviewEvents = existing.reviewEvents + 1,
                updatedAt = now,
            ),
        )
    }

    /** Adds the time spent in a finished session to the day it started on. */
    suspend fun addStudyDuration(startedAtMillis: Long, durationMillis: Long, levelId: String) {
        if (durationMillis <= 0) return
        db.withTransaction {
            val now = time.nowMillis()
            val dayKey = StudyDay.dayKey(startedAtMillis, time.zone())
            val existing = db.dailySummaryDao().get(dayKey, levelId) ?: DailyStudySummaryEntity(
                dayKey = dayKey,
                level = levelId,
            )
            db.dailySummaryDao().upsert(
                existing.copy(
                    studyDurationMs = existing.studyDurationMs + durationMillis,
                    sessionsCompleted = existing.sessionsCompleted + 1,
                    updatedAt = now,
                ),
            )
        }
    }

    /** Adds an exam to the daily rollup (completed count and raw question score). */
    suspend fun addExamResult(
        submittedAtMillis: Long,
        levelId: String,
        correct: Int,
        total: Int,
        durationMillis: Long,
    ) {
        db.withTransaction {
            val dayKey = StudyDay.dayKey(submittedAtMillis, time.zone())
            val existing = db.dailySummaryDao().get(dayKey, levelId) ?: DailyStudySummaryEntity(
                dayKey = dayKey,
                level = levelId,
            )
            db.dailySummaryDao().upsert(
                existing.copy(
                    examsCompleted = existing.examsCompleted + 1,
                    examCorrect = existing.examCorrect + correct,
                    examTotal = existing.examTotal + total,
                    studyDurationMs = existing.studyDurationMs + durationMillis.coerceAtLeast(0),
                    updatedAt = submittedAtMillis,
                ),
            )
        }
    }

    companion object {
        /** Consecutive 已掌握 answers that retire a mistake-book row automatically. */
        const val AUTO_RESOLVE_STREAK = 3
    }
}
