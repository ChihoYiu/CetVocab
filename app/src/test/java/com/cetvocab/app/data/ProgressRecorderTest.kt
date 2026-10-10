package com.cetvocab.app.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.cetvocab.app.core.time.StudyDay
import com.cetvocab.app.core.time.TimeProvider
import com.cetvocab.app.data.local.CetVocabDatabase
import com.cetvocab.app.data.local.entity.WordEntity
import com.cetvocab.app.data.repository.AnswerSource
import com.cetvocab.app.data.repository.ProgressRecorder
import com.cetvocab.app.domain.model.CetLevel
import com.cetvocab.app.domain.model.Word
import com.cetvocab.app.domain.srs.MasteryStatus
import com.cetvocab.app.domain.srs.ReviewGrade
import com.cetvocab.app.domain.srs.SrsConstants
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.ZoneId

/**
 * Integration tests for [ProgressRecorder], the single place where an answer is written.
 *
 * The clock is a fixed [FakeTimeProvider] so every row lands in a known study day and the
 * *distinct word per day* accounting can be asserted exactly.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26])
class ProgressRecorderTest {

    /** Fixed clock: 2026-01-15T10:00:00+08:00, a morning session of the 2026-01-15 study day. */
    private class FakeTimeProvider(
        private var nowMillis: Long,
        private val zoneId: ZoneId = ZoneId.of("Asia/Shanghai"),
    ) : TimeProvider {
        override fun nowMillis(): Long = nowMillis

        override fun zone(): ZoneId = zoneId

        fun advanceBy(millis: Long) {
            nowMillis += millis
        }
    }

    private lateinit var database: CetVocabDatabase
    private lateinit var time: FakeTimeProvider
    private lateinit var recorder: ProgressRecorder

    private val start = 1_768_442_400_000L
    private val dayKey = "2026-01-15"

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            CetVocabDatabase::class.java,
        )
            .setJournalMode(androidx.room.RoomDatabase.JournalMode.TRUNCATE)
            .build()
        database.openHelper.writableDatabase.execSQL("PRAGMA foreign_keys=ON")

        time = FakeTimeProvider(start)
        recorder = ProgressRecorder(database, time)

        assertThat(StudyDay.dayKey(start, time.zone())).isEqualTo(dayKey)
    }

    @After
    fun tearDown() {
        database.close()
    }

    private suspend fun insertWord(word: String, level: CetLevel = CetLevel.CET4): Long =
        database.wordDao().insert(
            WordEntity(
                packId = "test-pack",
                word = word,
                normalizedWord = Word.normalizeWordKey(word),
                meanings = """["释义"]""",
                level = level.id,
                createdAt = start,
                updatedAt = start,
            ),
        )

    private suspend fun summary(level: CetLevel = CetLevel.CET4) =
        database.dailySummaryDao().get(dayKey, level.id)

    // --- One answer ------------------------------------------------------------------------

    @Test
    fun `recording unknown creates progress an event a mistake row and one studied word`() = runTest {
        val wordId = insertWord("apple")

        val progress = recorder.record(wordId, ReviewGrade.UNKNOWN, AnswerSource.STUDY)

        assertThat(progress.wordId).isEqualTo(wordId)
        assertThat(progress.status).isEqualTo(MasteryStatus.LEARNING)
        assertThat(progress.wrongCount).isEqualTo(1)
        assertThat(progress.intervalDays).isEqualTo(0)
        assertThat(progress.lastAnswer).isEqualTo(ReviewGrade.UNKNOWN)
        assertThat(progress.dueAt).isEqualTo(start + SrsConstants.IMMEDIATE_REPEAT_DELAY_MILLIS)

        val stored = database.progressDao().byWordId(wordId)
        assertThat(stored).isNotNull()
        assertThat(stored!!.status).isEqualTo("LEARNING")
        assertThat(stored.wrongCount).isEqualTo(1)

        val events = database.reviewEventDao().allForLevel(CetLevel.CET4.id)
        assertThat(events).hasSize(1)
        assertThat(events.single().wordId).isEqualTo(wordId)
        assertThat(events.single().grade).isEqualTo("UNKNOWN")
        assertThat(events.single().source).isEqualTo(AnswerSource.STUDY.id)
        assertThat(events.single().dayKey).isEqualTo(dayKey)

        val wrong = database.mistakeBookDao().byWordId(wordId)
        assertThat(wrong).isNotNull()
        assertThat(wrong!!.wrongCount).isEqualTo(1)
        assertThat(wrong.resolvedAt).isNull()
        assertThat(wrong.level).isEqualTo(CetLevel.CET4.id)

        val day = summary()!!
        assertThat(day.studiedWords).isEqualTo(1)
        assertThat(day.reviewEvents).isEqualTo(1)
        assertThat(day.unknownCount).isEqualTo(1)
        assertThat(day.masteredCount).isEqualTo(0)
    }

    @Test
    fun `recording mastered creates progress without a mistake row`() = runTest {
        val wordId = insertWord("apple")

        recorder.record(wordId, ReviewGrade.MASTERED, AnswerSource.STUDY)

        assertThat(database.progressDao().byWordId(wordId)!!.status).isEqualTo("REVIEW")
        assertThat(database.mistakeBookDao().byWordId(wordId)).isNull()

        val day = summary()!!
        assertThat(day.studiedWords).isEqualTo(1)
        assertThat(day.masteredCount).isEqualTo(1)
        assertThat(day.reviewEvents).isEqualTo(1)
    }

    @Test
    fun `recording fuzzy only tracks the fuzzy counter of an existing mistake row`() = runTest {
        val wordId = insertWord("apple")

        recorder.record(wordId, ReviewGrade.FUZZY, AnswerSource.STUDY)

        assertThat(database.mistakeBookDao().byWordId(wordId)).isNull()

        recorder.record(wordId, ReviewGrade.UNKNOWN, AnswerSource.STUDY)
        recorder.record(wordId, ReviewGrade.FUZZY, AnswerSource.STUDY)

        val wrong = database.mistakeBookDao().byWordId(wordId)!!
        assertThat(wrong.wrongCount).isEqualTo(1)
        assertThat(wrong.fuzzyCount).isEqualTo(1)
        assertThat(summary()!!.fuzzyCount).isEqualTo(2)
    }

    @Test
    fun `the response time and session id are stored on the event`() = runTest {
        val wordId = insertWord("apple")

        recorder.record(
            wordId = wordId,
            grade = ReviewGrade.MASTERED,
            source = AnswerSource.STUDY,
            sessionId = 77L,
            responseTimeMs = 4_200L,
        )

        val event = database.reviewEventDao().allForLevel(CetLevel.CET4.id).single()
        assertThat(event.sessionId).isEqualTo(77L)
        assertThat(event.responseTimeMs).isEqualTo(4_200L)
        assertThat(event.answeredAt).isEqualTo(start)
    }

    // --- Distinct word counting -------------------------------------------------------------

    @Test
    fun `the studied word count only counts the word once per day`() = runTest {
        val wordId = insertWord("apple")

        recorder.record(wordId, ReviewGrade.UNKNOWN, AnswerSource.STUDY)
        time.advanceBy(60_000L)
        recorder.record(wordId, ReviewGrade.UNKNOWN, AnswerSource.IMMEDIATE)
        time.advanceBy(60_000L)
        recorder.record(wordId, ReviewGrade.MASTERED, AnswerSource.STUDY)

        val day = summary()!!
        assertThat(day.studiedWords).isEqualTo(1)
        assertThat(day.reviewEvents).isEqualTo(3)
        assertThat(day.unknownCount).isEqualTo(2)
        assertThat(day.masteredCount).isEqualTo(1)
        // The word had no progress row before this session, so its first answer of the study
        // day introduces it as a *new* word. A word can never be counted as both a new word and
        // a review word on the same day — that is the rule bumpDailySummary implements.
        assertThat(day.newWords).isEqualTo(1)
        assertThat(day.reviewWords).isEqualTo(0)
        // Nothing was recorded under the other level, so the per-(day, level) accounting of the
        // same word cannot leak across levels.
        assertThat(database.dailySummaryDao().get(dayKey, CetLevel.CET6.id)).isNull()
    }

    @Test
    fun `two different words count as two studied words`() = runTest {
        val apple = insertWord("apple")
        val banana = insertWord("banana")

        recorder.record(apple, ReviewGrade.MASTERED, AnswerSource.STUDY)
        recorder.record(banana, ReviewGrade.MASTERED, AnswerSource.STUDY)
        recorder.record(apple, ReviewGrade.MASTERED, AnswerSource.STUDY)

        val day = summary()!!
        assertThat(day.studiedWords).isEqualTo(2)
        assertThat(day.reviewEvents).isEqualTo(3)
        assertThat(database.reviewEventDao().countForWordOnDay(apple, dayKey)).isEqualTo(2)
        assertThat(database.reviewEventDao().countForWordOnDay(banana, dayKey)).isEqualTo(1)
    }

    @Test
    fun `the same word counts again on the next study day`() = runTest {
        val wordId = insertWord("apple")

        recorder.record(wordId, ReviewGrade.MASTERED, AnswerSource.STUDY)
        time.advanceBy(SpacedRepetitionDays.ONE_DAY + 1_000L)
        recorder.record(wordId, ReviewGrade.MASTERED, AnswerSource.STUDY)

        assertThat(summary()!!.studiedWords).isEqualTo(1)
        assertThat(StudyDay.dayKey(time.nowMillis(), time.zone())).isEqualTo("2026-01-16")
        assertThat(database.dailySummaryDao().get("2026-01-16", CetLevel.CET4.id)!!.studiedWords)
            .isEqualTo(1)
        assertThat(database.dailySummaryDao().allForLevel(CetLevel.CET4.id)).hasSize(2)
    }

    @Test
    fun `the first mastered answer of a new word is counted as a new word`() = runTest {
        val wordId = insertWord("apple")

        recorder.record(wordId, ReviewGrade.MASTERED, AnswerSource.STUDY)
        recorder.record(wordId, ReviewGrade.MASTERED, AnswerSource.STUDY)

        val day = summary()!!
        assertThat(day.studiedWords).isEqualTo(1)
        assertThat(day.newWords).isEqualTo(1)
        assertThat(day.reviewWords).isEqualTo(0)
    }

    @Test
    fun `a new word is counted as a new word whatever the answer was`() = runTest {
        val wordId = insertWord("apple")

        recorder.record(wordId, ReviewGrade.UNKNOWN, AnswerSource.STUDY)

        val day = summary()!!
        // "New" describes the word's history (it had never been answered before today), not the
        // quality of the answer: answering 不认识 on a first encounter still introduces the word.
        assertThat(day.newWords).isEqualTo(1)
        assertThat(day.reviewWords).isEqualTo(0)
        assertThat(day.unknownCount).isEqualTo(1)
    }

    @Test
    fun `the second study day of the same word counts as a review`() = runTest {
        val wordId = insertWord("apple")
        recorder.record(wordId, ReviewGrade.MASTERED, AnswerSource.STUDY)

        // Move to the next study day (2026-01-16) and answer the same word again: it now has a
        // progress row, so this day books it as a review rather than as a new word.
        time.advanceBy(24L * 60 * 60 * 1000)
        recorder.record(wordId, ReviewGrade.MASTERED, AnswerSource.STUDY)

        val secondDay = database.dailySummaryDao().get("2026-01-16", CetLevel.CET4.id)!!
        assertThat(secondDay.studiedWords).isEqualTo(1)
        assertThat(secondDay.newWords).isEqualTo(0)
        assertThat(secondDay.reviewWords).isEqualTo(1)
    }

    // --- Mistake book auto resolve -----------------------------------------------------------

    @Test
    fun `three mastered answers in a row auto resolve the mistake row`() = runTest {
        val wordId = insertWord("apple")
        recorder.record(wordId, ReviewGrade.UNKNOWN, AnswerSource.STUDY)

        val afterMistake = database.mistakeBookDao().byWordId(wordId)!!
        assertThat(afterMistake.correctSinceWrong).isEqualTo(0)
        assertThat(afterMistake.resolvedAt).isNull()

        recorder.record(wordId, ReviewGrade.MASTERED, AnswerSource.STUDY)
        assertThat(database.mistakeBookDao().byWordId(wordId)!!.correctSinceWrong).isEqualTo(1)
        assertThat(database.mistakeBookDao().byWordId(wordId)!!.resolvedAt).isNull()

        recorder.record(wordId, ReviewGrade.MASTERED, AnswerSource.STUDY)
        assertThat(database.mistakeBookDao().byWordId(wordId)!!.correctSinceWrong).isEqualTo(2)
        assertThat(database.mistakeBookDao().byWordId(wordId)!!.resolvedAt).isNull()

        recorder.record(wordId, ReviewGrade.MASTERED, AnswerSource.STUDY)

        val resolved = database.mistakeBookDao().byWordId(wordId)!!
        assertThat(resolved.correctSinceWrong).isEqualTo(ProgressRecorder.AUTO_RESOLVE_STREAK)
        assertThat(resolved.resolvedAt).isEqualTo(start)
        assertThat(database.mistakeBookDao().activeCount(CetLevel.CET4.id)).isEqualTo(0)
        assertThat(database.mistakeBookDao().byWordId(wordId)).isNotNull()
    }

    @Test
    fun `a fuzzy or unknown answer resets the auto resolve streak`() = runTest {
        val wordId = insertWord("apple")
        recorder.record(wordId, ReviewGrade.UNKNOWN, AnswerSource.STUDY)
        recorder.record(wordId, ReviewGrade.MASTERED, AnswerSource.STUDY)
        recorder.record(wordId, ReviewGrade.MASTERED, AnswerSource.STUDY)

        assertThat(database.mistakeBookDao().byWordId(wordId)!!.correctSinceWrong).isEqualTo(2)

        recorder.record(wordId, ReviewGrade.FUZZY, AnswerSource.STUDY)
        assertThat(database.mistakeBookDao().byWordId(wordId)!!.correctSinceWrong).isEqualTo(0)

        recorder.record(wordId, ReviewGrade.MASTERED, AnswerSource.STUDY)
        recorder.record(wordId, ReviewGrade.MASTERED, AnswerSource.STUDY)
        recorder.record(wordId, ReviewGrade.UNKNOWN, AnswerSource.STUDY)

        val wrong = database.mistakeBookDao().byWordId(wordId)!!
        assertThat(wrong.correctSinceWrong).isEqualTo(0)
        assertThat(wrong.resolvedAt).isNull()
        assertThat(wrong.wrongCount).isEqualTo(2)
    }

    @Test
    fun `an unknown answer reopens a resolved mistake row`() = runTest {
        val wordId = insertWord("apple")
        recorder.record(wordId, ReviewGrade.UNKNOWN, AnswerSource.STUDY)
        repeat(ProgressRecorder.AUTO_RESOLVE_STREAK) {
            recorder.record(wordId, ReviewGrade.MASTERED, AnswerSource.STUDY)
        }
        assertThat(database.mistakeBookDao().byWordId(wordId)!!.resolvedAt).isNotNull()

        time.advanceBy(60_000L)
        recorder.record(wordId, ReviewGrade.UNKNOWN, AnswerSource.STUDY)

        val reopened = database.mistakeBookDao().byWordId(wordId)!!
        assertThat(reopened.resolvedAt).isNull()
        assertThat(reopened.wrongCount).isEqualTo(2)
        assertThat(reopened.correctSinceWrong).isEqualTo(0)
        assertThat(reopened.lastSource).isEqualTo(AnswerSource.STUDY.id)
    }

    // --- Per level and per day ----------------------------------------------------------------

    @Test
    fun `the daily summary is kept per day key and per level`() = runTest {
        val cet4 = insertWord("apple", CetLevel.CET4)
        val cet6 = insertWord("banana", CetLevel.CET6)

        recorder.record(cet4, ReviewGrade.UNKNOWN, AnswerSource.STUDY)
        recorder.record(cet6, ReviewGrade.MASTERED, AnswerSource.STUDY)
        time.advanceBy(60_000L)
        recorder.record(cet4, ReviewGrade.MASTERED, AnswerSource.STUDY)

        val cet4Summary = summary(CetLevel.CET4)!!
        val cet6Summary = summary(CetLevel.CET6)!!

        assertThat(cet4Summary.studiedWords).isEqualTo(1)
        assertThat(cet4Summary.reviewEvents).isEqualTo(2)
        assertThat(cet4Summary.unknownCount).isEqualTo(1)
        assertThat(cet4Summary.masteredCount).isEqualTo(1)

        assertThat(cet6Summary.studiedWords).isEqualTo(1)
        assertThat(cet6Summary.reviewEvents).isEqualTo(1)
        assertThat(cet6Summary.unknownCount).isEqualTo(0)
        assertThat(cet6Summary.masteredCount).isEqualTo(1)

        assertThat(database.dailySummaryDao().allForLevel(CetLevel.CET4.id)).hasSize(1)
        assertThat(database.dailySummaryDao().allForLevel(CetLevel.CET6.id)).hasSize(1)
    }

    @Test
    fun `a second day gets its own summary row`() = runTest {
        val wordId = insertWord("apple")

        recorder.record(wordId, ReviewGrade.UNKNOWN, AnswerSource.STUDY)
        time.advanceBy(SpacedRepetitionDays.ONE_DAY + 60_000L)
        recorder.record(wordId, ReviewGrade.MASTERED, AnswerSource.STUDY)

        assertThat(database.dailySummaryDao().allForLevel(CetLevel.CET4.id).map { it.dayKey })
            .containsExactly("2026-01-15", "2026-01-16").inOrder()
        assertThat(database.dailySummaryDao().get("2026-01-16", CetLevel.CET4.id)!!.masteredCount)
            .isEqualTo(1)
    }

    @Test
    fun `an exam sourced answer is recorded like any other answer`() = runTest {
        val wordId = insertWord("apple")

        val progress = recorder.record(wordId, ReviewGrade.MASTERED, AnswerSource.EXAM)

        assertThat(progress.wordId).isEqualTo(wordId)
        assertThat(progress.status).isEqualTo(MasteryStatus.REVIEW)
        assertThat(database.reviewEventDao().allForLevel(CetLevel.CET4.id).single().source)
            .isEqualTo(AnswerSource.EXAM.id)
        assertThat(summary(CetLevel.CET4)!!.reviewEvents).isEqualTo(1)
    }

    // --- Session and exam rollups -------------------------------------------------------------

    @Test
    fun `study duration and session count are added to the day the session started on`() = runTest {
        recorder.addStudyDuration(startedAtMillis = start, durationMillis = 600_000L, levelId = CetLevel.CET4.id)
        recorder.addStudyDuration(startedAtMillis = start, durationMillis = 300_000L, levelId = CetLevel.CET4.id)

        val day = summary()!!
        assertThat(day.studyDurationMs).isEqualTo(900_000L)
        assertThat(day.sessionsCompleted).isEqualTo(2)
    }

    @Test
    fun `a zero or negative duration is ignored`() = runTest {
        recorder.addStudyDuration(start, 0L, CetLevel.CET4.id)
        recorder.addStudyDuration(start, -1_000L, CetLevel.CET4.id)

        assertThat(summary()).isNull()
    }

    @Test
    fun `exam results are added to the daily rollup`() = runTest {
        recorder.addExamResult(
            submittedAtMillis = start,
            levelId = CetLevel.CET4.id,
            correct = 7,
            total = 10,
            durationMillis = 420_000L,
        )

        val day = summary()!!
        assertThat(day.examsCompleted).isEqualTo(1)
        assertThat(day.examCorrect).isEqualTo(7)
        assertThat(day.examTotal).isEqualTo(10)
        assertThat(day.studyDurationMs).isEqualTo(420_000L)
    }

    // --- Progress carry over ------------------------------------------------------------------

    @Test
    fun `progress carries over between answers`() = runTest {
        val wordId = insertWord("apple")

        val first = recorder.record(wordId, ReviewGrade.MASTERED, AnswerSource.STUDY)
        time.advanceBy(SpacedRepetitionDays.ONE_DAY + 1_000L)
        val second = recorder.record(wordId, ReviewGrade.MASTERED, AnswerSource.STUDY)

        assertThat(first.repetitions).isEqualTo(1)
        assertThat(first.intervalDays).isEqualTo(1)
        assertThat(second.repetitions).isEqualTo(2)
        assertThat(second.intervalDays).isEqualTo(3)
        assertThat(second.masteryLevel).isEqualTo(2)
        assertThat(database.progressDao().byWordId(wordId)!!.intervalDays).isEqualTo(3)

        val events = database.reviewEventDao().allForLevel(CetLevel.CET4.id)
        assertThat(events.map { it.newIntervalDays }).containsExactly(1, 3).inOrder()
    }

    private object SpacedRepetitionDays {
        const val ONE_DAY: Long = 24L * 60 * 60 * 1000
    }
}
