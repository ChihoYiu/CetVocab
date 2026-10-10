package com.cetvocab.app.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.cetvocab.app.data.local.CetVocabDatabase
import com.cetvocab.app.data.local.entity.DailyStudySummaryEntity
import com.cetvocab.app.data.local.entity.ReviewEventEntity
import com.cetvocab.app.data.local.entity.UserWordProgressEntity
import com.cetvocab.app.data.local.entity.WordEntity
import com.cetvocab.app.data.local.entity.WordPackEntity
import com.cetvocab.app.data.local.entity.WrongAnswerEntity
import com.cetvocab.app.domain.model.CetLevel
import com.cetvocab.app.domain.model.Word
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Room database tests.
 *
 * The database is built in memory and the API-26 code path (the app's minSdk) is pinned with
 * [Config] so that the SQL used by the DAOs is checked against the oldest supported SQLite.
 * Foreign key enforcement is enabled explicitly because Android only turns it on for
 * foreign keys declared with `deferred = true`.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26])
class DatabaseTest {

    private lateinit var database: CetVocabDatabase

    private val now = 1_768_450_800_000L
    private val dayKey = "2026-01-15"

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            CetVocabDatabase::class.java,
        )
            .setJournalMode(androidx.room.RoomDatabase.JournalMode.TRUNCATE)
            .build()

        // Room enables foreign keys on every connection it opens; the pragma is issued here as
        // well so the constraint behaviour does not depend on that generated code.
        database.openHelper.writableDatabase.execSQL("PRAGMA foreign_keys=ON")
    }

    @After
    fun tearDown() {
        database.close()
    }

    // --- Helpers ---------------------------------------------------------------------------

    private fun wordEntity(
        word: String,
        level: CetLevel = CetLevel.CET4,
        packId: String? = "test-pack",
        meanings: String = """["释义"]""",
    ) = WordEntity(
        packId = packId,
        externalId = "ext-$word",
        word = word,
        normalizedWord = Word.normalizeWordKey(word),
        phonetic = "/$word/",
        partOfSpeech = "n.",
        meanings = meanings,
        exampleSentence = "An $word a day.",
        exampleTranslation = "每天一个$word。",
        collocations = """["${word} pie"]""",
        level = level.id,
        source = "unit-test",
        license = "CC0",
        createdAt = now,
        updatedAt = now,
    )

    private fun progressEntity(
        wordId: Long,
        status: String = "REVIEW",
        masteryLevel: Int = 3,
        dueAt: Long = now,
        wrongCount: Int = 0,
    ) = UserWordProgressEntity(
        wordId = wordId,
        status = status,
        masteryLevel = masteryLevel,
        repetitions = 3,
        intervalDays = 7,
        easeFactor = 2.5,
        dueAt = dueAt,
        lastReviewedAt = now - 86_400_000L,
        lastAnswer = "MASTERED",
        lapseCount = 0,
        wrongCount = wrongCount,
        fuzzyCount = 0,
        consecutiveCorrectCount = 3,
        isFavorite = false,
        introducedAt = now - 86_400_000L,
        updatedAt = now,
    )

    private fun reviewEvent(wordId: Long, grade: String = "UNKNOWN") = ReviewEventEntity(
        wordId = wordId,
        sessionId = null,
        grade = grade,
        source = "STUDY",
        answeredAt = now,
        dayKey = dayKey,
        level = CetLevel.CET4.id,
        previousIntervalDays = 0,
        newIntervalDays = 0,
    )

    private fun wrongAnswer(wordId: Long) = WrongAnswerEntity(
        wordId = wordId,
        wrongCount = 1,
        fuzzyCount = 0,
        firstWrongAt = now,
        lastWrongAt = now,
        lastSource = "UNKNOWN",
        correctSinceWrong = 0,
        resolvedAt = null,
        isFavorite = false,
        level = CetLevel.CET4.id,
        updatedAt = now,
    )

    // --- Words and packs -------------------------------------------------------------------

    @Test
    fun `an inserted word is found by level and by normalized word`() = runTest {
        val id = database.wordDao().insert(wordEntity("Apple"))

        assertThat(id).isGreaterThan(0L)
        // `normalized_word` holds Word.normalizeWordKey(word) and the DAO compares that key
        // literally, so every caller normalises its lookup value the same way — that contract
        // is what keeps "Apple", "apple" and " apple " the same entry.
        assertThat(database.wordDao().byNormalized(CetLevel.CET4.id, Word.normalizeWordKey("Apple"))?.id)
            .isEqualTo(id)
        assertThat(database.wordDao().byNormalized(CetLevel.CET4.id, Word.normalizeWordKey(" Apple "))?.id)
            .isEqualTo(id)
        // A raw (unnormalised) value is simply a miss; normalisation is the caller's job.
        assertThat(database.wordDao().byNormalized(CetLevel.CET4.id, " Apple ")).isNull()
        assertThat(database.wordDao().allForLevel(CetLevel.CET4.id).map { it.word })
            .containsExactly("Apple")
        assertThat(database.wordDao().count(CetLevel.CET4.id)).isEqualTo(1)
        assertThat(database.wordDao().hasWords(CetLevel.CET4.id)).isTrue()
        assertThat(database.wordDao().hasWords(CetLevel.CET6.id)).isFalse()
        assertThat(database.wordDao().count(CetLevel.CET6.id)).isEqualTo(0)
    }

    @Test
    fun `the unique index prevents a duplicate level and normalized word`() = runTest {
        val first = database.wordDao().insert(wordEntity("Apple"))
        val duplicate = database.wordDao().insert(wordEntity("apple"))

        assertThat(first).isGreaterThan(0L)
        assertThat(duplicate).isEqualTo(-1L)
        assertThat(database.wordDao().allForLevel(CetLevel.CET4.id)).hasSize(1)
    }

    @Test
    fun `the same word may exist once per level`() = runTest {
        val cet4 = database.wordDao().insert(wordEntity("apple", CetLevel.CET4))
        val cet6 = database.wordDao().insert(wordEntity("apple", CetLevel.CET6))

        assertThat(cet4).isGreaterThan(0L)
        assertThat(cet6).isGreaterThan(0L)
        assertThat(database.wordDao().count(CetLevel.CET4.id)).isEqualTo(1)
        assertThat(database.wordDao().count(CetLevel.CET6.id)).isEqualTo(1)
    }

    @Test
    fun `packs are stored and listed with built in packs last`() = runTest {
        database.wordDao().upsertPack(
            WordPackEntity(id = "user-pack", name = "用户词库", level = CetLevel.CET4.id, isBuiltIn = false),
        )
        database.wordDao().upsertPack(
            WordPackEntity(id = "built-in", name = "演示词库", level = CetLevel.CET4.id, isBuiltIn = true),
        )

        val packs = database.wordDao().observePacks().first()

        assertThat(packs.map { it.id }).containsExactly("user-pack", "built-in").inOrder()
        assertThat(database.wordDao().packById("user-pack")?.name).isEqualTo("用户词库")
        assertThat(database.wordDao().importedPackCount()).isEqualTo(1)

        database.wordDao().updatePackWordCount("user-pack", 7, now)
        assertThat(database.wordDao().packById("user-pack")?.wordCount).isEqualTo(7)
    }

    @Test
    fun `the exam source projection reads the stored meanings`() = runTest {
        val id = database.wordDao().insert(wordEntity("apple", meanings = """["苹果","苹果树"]"""))

        val lite = database.wordDao().liteForLevel(CetLevel.CET4.id).single()

        assertThat(lite.id).isEqualTo(id)
        assertThat(lite.word).isEqualTo("apple")
        assertThat(lite.meanings).isEqualTo("""["苹果","苹果树"]""")
    }

    @Test
    fun `delete pack words removes only that pack of that level`() = runTest {
        database.wordDao().insert(wordEntity("apple", packId = "pack-a"))
        database.wordDao().insert(wordEntity("banana", packId = "pack-b"))

        database.wordDao().deletePackWords(CetLevel.CET4.id, "pack-a")

        assertThat(database.wordDao().allForLevel(CetLevel.CET4.id).map { it.word })
            .containsExactly("banana")
    }

    // --- Progress --------------------------------------------------------------------------

    @Test
    fun `progress is upserted and observed through a flow`() = runTest {
        val wordId = database.wordDao().insert(wordEntity("apple"))

        database.progressDao().upsert(progressEntity(wordId, status = "LEARNING", masteryLevel = 1))

        val stored = database.progressDao().byWordId(wordId)
        assertThat(stored).isNotNull()
        assertThat(stored!!.status).isEqualTo("LEARNING")
        assertThat(stored.masteryLevel).isEqualTo(1)

        // Room delivers flow emissions from its own query executor, so the flow is collected on
        // a real dispatcher: under runTest's virtual clock the timeout would elapse before the
        // first emission could ever arrive.
        val emitted = withContext(Dispatchers.Default) {
            withTimeout(5_000L) {
                database.progressDao().observeDueWords(CetLevel.CET4.id, now, limit = 10).first()
            }
        }
        assertThat(emitted.map { it.wordId }).containsExactly(wordId)

        database.progressDao().upsert(progressEntity(wordId, status = "MASTERED", masteryLevel = 5))

        val updated = database.progressDao().byWordId(wordId)
        assertThat(updated!!.status).isEqualTo("MASTERED")
        assertThat(updated.masteryLevel).isEqualTo(5)
        assertThat(database.progressDao().byWordIds(listOf(wordId))).hasSize(1)

        val emittedAgain = withContext(Dispatchers.Default) {
            withTimeout(5_000L) {
                database.progressDao().observeDueWords(CetLevel.CET4.id, now, limit = 10).first()
            }
        }
        assertThat(emittedAgain.single().status).isEqualTo("MASTERED")
    }

    @Test
    fun `the view returns null progress columns for a word without progress`() = runTest {
        val wordId = database.wordDao().insert(wordEntity("apple"))

        val view = database.progressDao().wordWithProgress(wordId)

        assertThat(view).isNotNull()
        assertThat(view!!.word).isEqualTo("apple")
        assertThat(view.wordId).isEqualTo(wordId)
        assertThat(view.status).isNull()
        assertThat(view.masteryLevel).isNull()
        assertThat(view.repetitions).isNull()
        assertThat(view.intervalDays).isNull()
        assertThat(view.easeFactor).isNull()
        assertThat(view.dueAt).isNull()
        assertThat(view.lastReviewedAt).isNull()
        assertThat(view.lastAnswer).isNull()
        assertThat(view.lapseCount).isNull()
        assertThat(view.wrongCount).isNull()
        assertThat(view.fuzzyCount).isNull()
        assertThat(view.consecutiveCorrectCount).isNull()
        assertThat(view.isFavorite).isNull()
        assertThat(view.introducedAt).isNull()
    }

    @Test
    fun `the view exposes the progress of a studied word`() = runTest {
        val wordId = database.wordDao().insert(wordEntity("apple"))
        database.progressDao().upsert(progressEntity(wordId, status = "REVIEW", masteryLevel = 4))

        val view = database.progressDao().wordWithProgress(wordId)!!

        assertThat(view.status).isEqualTo("REVIEW")
        assertThat(view.masteryLevel).isEqualTo(4)
        assertThat(view.dueAt).isEqualTo(now)
        assertThat(view.easeFactor).isEqualTo(2.5)
        assertThat(view.isFavorite).isEqualTo(0)
    }

    @Test
    fun `new words are the words whose view status is null`() = runTest {
        val studied = database.wordDao().insert(wordEntity("apple"))
        database.wordDao().insert(wordEntity("banana"))
        database.progressDao().upsert(progressEntity(studied))

        val newWords = database.progressDao().newWords(CetLevel.CET4.id, limit = 10)

        assertThat(newWords.map { it.word }).containsExactly("banana")
        assertThat(database.progressDao().newCount(CetLevel.CET4.id)).isEqualTo(1)
        assertThat(database.progressDao().dueCount(CetLevel.CET4.id, now)).isEqualTo(1)
    }

    @Test
    fun `favorite flags are written through the dao`() = runTest {
        val wordId = database.wordDao().insert(wordEntity("apple"))
        database.progressDao().upsert(progressEntity(wordId))

        database.progressDao().setFavorite(wordId, true, now)

        assertThat(database.progressDao().byWordId(wordId)!!.isFavorite).isTrue()
        assertThat(database.progressDao().favorites(CetLevel.CET4.id).map { it.wordId })
            .containsExactly(wordId)
    }

    // --- Mistake book ----------------------------------------------------------------------

    @Test
    fun `the mistake book is upserted resolved and deleted`() = runTest {
        val wordId = database.wordDao().insert(wordEntity("apple"))

        database.mistakeBookDao().upsert(wrongAnswer(wordId))
        assertThat(database.mistakeBookDao().byWordId(wordId)!!.wrongCount).isEqualTo(1)
        assertThat(database.mistakeBookDao().byWordId(wordId)!!.resolvedAt).isNull()
        assertThat(database.mistakeBookDao().activeCount(CetLevel.CET4.id)).isEqualTo(1)

        database.mistakeBookDao().upsert(wrongAnswer(wordId).copy(wrongCount = 4, lastWrongAt = now + 1_000))
        assertThat(database.mistakeBookDao().byWordId(wordId)!!.wrongCount).isEqualTo(4)

        database.mistakeBookDao().markResolved(wordId, now + 2_000)
        val resolved = database.mistakeBookDao().byWordId(wordId)!!
        assertThat(resolved.resolvedAt).isEqualTo(now + 2_000)
        assertThat(resolved.updatedAt).isEqualTo(now + 2_000)
        assertThat(database.mistakeBookDao().activeCount(CetLevel.CET4.id)).isEqualTo(0)

        database.mistakeBookDao().markUnresolved(wordId, now + 3_000)
        assertThat(database.mistakeBookDao().byWordId(wordId)!!.resolvedAt).isNull()

        database.mistakeBookDao().delete(wordId)
        assertThat(database.mistakeBookDao().byWordId(wordId)).isNull()
        assertThat(database.mistakeBookDao().activeCount(CetLevel.CET4.id)).isEqualTo(0)
    }

    @Test
    fun `the mistake list joins the word content and hides resolved rows by default`() = runTest {
        val wordId = database.wordDao().insert(wordEntity("apple", meanings = """["苹果"]"""))
        database.mistakeBookDao().upsert(wrongAnswer(wordId))

        val active = database.mistakeBookDao()
            .observeMistakes(
                level = CetLevel.CET4.id,
                includeResolved = 0,
                favoriteOnly = 0,
                minWrongCount = 0,
                sinceMillis = 0,
                search = null,
                like = "%%",
            )
            .first()
        assertThat(active.map { it.wordId }).containsExactly(wordId)
        assertThat(active.single().word).isEqualTo("apple")
        assertThat(active.single().meanings).isEqualTo("""["苹果"]""")
        assertThat(active.single().wrongCount).isEqualTo(1)

        database.mistakeBookDao().markResolved(wordId, now + 1_000)

        val withoutResolved = database.mistakeBookDao()
            .observeMistakes(CetLevel.CET4.id, 0, 0, 0, 0, null, "%%")
            .first()
        val withResolved = database.mistakeBookDao()
            .observeMistakes(CetLevel.CET4.id, 1, 0, 0, 0, null, "%%")
            .first()

        assertThat(withoutResolved).isEmpty()
        assertThat(withResolved.map { it.wordId }).containsExactly(wordId)
    }

    // --- Cascades --------------------------------------------------------------------------

    @Test
    fun `deleting a word cascades to progress review events and wrong answers`() = runTest {
        val wordId = database.wordDao().insert(wordEntity("apple"))
        database.progressDao().upsert(progressEntity(wordId))
        database.reviewEventDao().insert(reviewEvent(wordId))
        database.mistakeBookDao().upsert(wrongAnswer(wordId))

        assertThat(database.progressDao().byWordId(wordId)).isNotNull()
        assertThat(database.reviewEventDao().countForWordOnDay(wordId, dayKey)).isEqualTo(1)
        assertThat(database.mistakeBookDao().byWordId(wordId)).isNotNull()

        database.wordDao().deleteLevel(CetLevel.CET4.id)

        assertThat(database.wordDao().byId(wordId)).isNull()
        assertThat(database.progressDao().byWordId(wordId)).isNull()
        assertThat(database.reviewEventDao().countForWordOnDay(wordId, dayKey)).isEqualTo(0)
        assertThat(database.reviewEventDao().totalCount(CetLevel.CET4.id)).isEqualTo(0)
        assertThat(database.mistakeBookDao().byWordId(wordId)).isNull()
        assertThat(database.progressDao().wordWithProgress(wordId)).isNull()
    }

    @Test
    fun `progress for a missing word is rejected by the foreign key`() = runTest {
        val thrown = runCatching { database.progressDao().upsert(progressEntity(9_999L)) }
            .exceptionOrNull()

        assertThat(thrown).isNotNull()
        assertThat(thrown!!.javaClass.name).contains("SQLiteConstraintException")
        assertThat(database.progressDao().byWordId(9_999L)).isNull()
    }

    @Test
    fun `a review event for a missing word is rejected by the foreign key`() = runTest {
        val thrown = runCatching { database.reviewEventDao().insert(reviewEvent(9_999L)) }
            .exceptionOrNull()

        assertThat(thrown).isNotNull()
        assertThat(thrown!!.javaClass.name).contains("SQLiteConstraintException")
        assertThat(database.reviewEventDao().totalCount(CetLevel.CET4.id)).isEqualTo(0)
    }

    // --- Daily summaries -------------------------------------------------------------------

    @Test
    fun `the daily summary composite key keeps the levels apart`() = runTest {
        database.dailySummaryDao().upsert(
            DailyStudySummaryEntity(dayKey = dayKey, level = CetLevel.CET4.id, studiedWords = 3, reviewEvents = 4),
        )
        database.dailySummaryDao().upsert(
            DailyStudySummaryEntity(dayKey = dayKey, level = CetLevel.CET6.id, studiedWords = 1, reviewEvents = 2),
        )

        assertThat(database.dailySummaryDao().get(dayKey, CetLevel.CET4.id)!!.studiedWords).isEqualTo(3)
        assertThat(database.dailySummaryDao().get(dayKey, CetLevel.CET6.id)!!.studiedWords).isEqualTo(1)
        assertThat(database.dailySummaryDao().allForLevel(CetLevel.CET4.id)).hasSize(1)
        assertThat(database.dailySummaryDao().since(CetLevel.CET4.id, "2026-01-01")).hasSize(1)

        // The same (day, level) pair is replaced, not duplicated.
        database.dailySummaryDao().upsert(
            DailyStudySummaryEntity(dayKey = dayKey, level = CetLevel.CET4.id, studiedWords = 9, reviewEvents = 9),
        )
        assertThat(database.dailySummaryDao().allForLevel(CetLevel.CET4.id)).hasSize(1)
        assertThat(database.dailySummaryDao().get(dayKey, CetLevel.CET4.id)!!.studiedWords).isEqualTo(9)
    }

    @Test
    fun `the daily summary totals aggregate the rows of a level`() = runTest {
        database.dailySummaryDao().upsert(
            DailyStudySummaryEntity(
                dayKey = "2026-01-14",
                level = CetLevel.CET4.id,
                studiedWords = 3,
                reviewEvents = 4,
                studyDurationMs = 600_000L,
                masteredCount = 2,
            ),
        )
        database.dailySummaryDao().upsert(
            DailyStudySummaryEntity(
                dayKey = dayKey,
                level = CetLevel.CET4.id,
                studiedWords = 5,
                reviewEvents = 6,
                studyDurationMs = 300_000L,
                masteredCount = 1,
            ),
        )
        database.dailySummaryDao().upsert(
            DailyStudySummaryEntity(dayKey = dayKey, level = CetLevel.CET6.id, studiedWords = 100),
        )

        val totals = database.dailySummaryDao().totals(CetLevel.CET4.id, "2026-01-01")

        assertThat(totals.studiedWords).isEqualTo(8)
        assertThat(totals.reviewEvents).isEqualTo(10)
        assertThat(totals.studyDurationMs).isEqualTo(900_000L)
        assertThat(totals.masteredCount).isEqualTo(3)
        assertThat(totals.activeDays).isEqualTo(2)
    }

    @Test
    fun `active day keys only list days with activity`() = runTest {
        database.dailySummaryDao().upsert(
            DailyStudySummaryEntity(dayKey = "2026-01-13", level = CetLevel.CET4.id, studiedWords = 0, reviewEvents = 0),
        )
        database.dailySummaryDao().upsert(
            DailyStudySummaryEntity(dayKey = "2026-01-14", level = CetLevel.CET4.id, studiedWords = 2),
        )
        database.dailySummaryDao().upsert(
            DailyStudySummaryEntity(dayKey = dayKey, level = CetLevel.CET4.id, examsCompleted = 1),
        )

        assertThat(database.dailySummaryDao().activeDayKeys(CetLevel.CET4.id))
            .containsExactly(dayKey, "2026-01-14").inOrder()
    }

    // --- Review events ---------------------------------------------------------------------

    @Test
    fun `review events are counted per word per day and per level`() = runTest {
        val wordId = database.wordDao().insert(wordEntity("apple"))

        database.reviewEventDao().insert(reviewEvent(wordId, grade = "UNKNOWN"))
        database.reviewEventDao().insert(reviewEvent(wordId, grade = "FUZZY"))
        database.reviewEventDao().insert(reviewEvent(wordId, grade = "MASTERED"))

        assertThat(database.reviewEventDao().countForWordOnDay(wordId, dayKey)).isEqualTo(3)
        assertThat(database.reviewEventDao().countForWordOnDay(wordId, "2026-01-14")).isEqualTo(0)
        assertThat(database.reviewEventDao().totalCount(CetLevel.CET4.id)).isEqualTo(3)
        assertThat(database.reviewEventDao().countSince(CetLevel.CET4.id, now - 1)).isEqualTo(3)
        assertThat(database.reviewEventDao().countSince(CetLevel.CET4.id, now + 1)).isEqualTo(0)

        val trends = database.reviewEventDao().trendsSince(CetLevel.CET4.id, "2026-01-01")
        assertThat(trends).hasSize(1)
        assertThat(trends.single().dayKey).isEqualTo(dayKey)
        assertThat(trends.single().total).isEqualTo(3)
        assertThat(trends.single().correct).isEqualTo(1)
        assertThat(trends.single().fuzzy).isEqualTo(1)
        assertThat(trends.single().wrong).isEqualTo(1)
    }

    @Test
    fun `recent review events join the word content`() = runTest {
        val wordId = database.wordDao().insert(wordEntity("apple", meanings = """["苹果"]"""))
        database.reviewEventDao().insert(reviewEvent(wordId, grade = "MASTERED"))

        val recent = database.reviewEventDao().recent(CetLevel.CET4.id, limit = 10)

        assertThat(recent).hasSize(1)
        assertThat(recent.single().word).isEqualTo("apple")
        assertThat(recent.single().grade).isEqualTo("MASTERED")
        assertThat(recent.single().level).isEqualTo(CetLevel.CET4.id)
    }

    // --- Schema sanity ---------------------------------------------------------------------

    @Test
    fun `the database reports version one and its declared journal mode`() = runTest {
        assertThat(database.openHelper.readableDatabase.version).isEqualTo(1)
        assertThat(database.wordDao().hasWords(CetLevel.CET4.id)).isFalse()
    }
}
