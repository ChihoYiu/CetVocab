package com.cetvocab.app.data.seed

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.cetvocab.app.core.result.AppError
import com.cetvocab.app.core.result.Outcome
import com.cetvocab.app.core.time.TimeProvider
import com.cetvocab.app.data.local.CetVocabDatabase
import com.cetvocab.app.data.repository.AnswerSource
import com.cetvocab.app.data.repository.DefaultWordRepository
import com.cetvocab.app.data.repository.ProgressRecorder
import com.cetvocab.app.data.repository.SeedProgress
import com.cetvocab.app.domain.importer.ImportIssue
import com.cetvocab.app.domain.importer.ImportPreview
import com.cetvocab.app.domain.importer.PackFormat
import com.cetvocab.app.domain.importer.ParsedWordRecord
import com.cetvocab.app.domain.model.CetLevel
import com.cetvocab.app.domain.model.Word
import com.cetvocab.app.domain.srs.ReviewGrade
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.ZoneId

/**
 * Tests for the built-in vocabulary seeding and **upgrade** path.
 *
 * The upgrade path is the part with real risk: an installed app must receive a bigger/corrected
 * word list without losing the learner's progress. The previous implementation only inserted when
 * the pack was empty, so it could never deliver a new list — and a naive "replace the pack" would
 * have deleted every progress, mistake and exam row that points at those words. These tests pin
 * down the required behaviour with a deterministic pack source.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [26])
class BuiltInPackSeedTest {

    private class FixedTime(private var now: Long = 1_768_442_400_000L) : TimeProvider {
        override fun nowMillis(): Long = now
        override fun zone(): ZoneId = ZoneId.of("Asia/Shanghai")
    }

    /** Deterministic stand-in for the shipped assets. */
    private class FakePackSource(
        private var definitions: List<BuiltInPackDefinition>,
        private val wordsByPack: MutableMap<String, List<ParsedWordRecord>>,
    ) : PackSource {
        var loadCount = 0
        override suspend fun definitions(): List<BuiltInPackDefinition> = definitions
        override suspend fun load(definition: BuiltInPackDefinition): Outcome<ImportPreview> {
            loadCount++
            val records = wordsByPack[definition.id]
                ?: return Outcome.Failure(AppError.io("missing asset"))
            return Outcome.Success(
                ImportPreview(
                    fileName = "${definition.id}.json",
                    format = PackFormat.JSON,
                    packName = definition.name,
                    records = records,
                    issues = emptyList<ImportIssue>(),
                    duplicateRows = 0,
                    totalRows = records.size,
                    levelCounts = records.groupingBy { it.level }.eachCount(),
                ),
            )
        }

        fun upgrade(definitions: List<BuiltInPackDefinition>, wordsByPack: MutableMap<String, List<ParsedWordRecord>>) {
            this.definitions = definitions
            this.wordsByPack.clear()
            this.wordsByPack.putAll(wordsByPack)
        }
    }

    private lateinit var database: CetVocabDatabase
    private lateinit var recorder: ProgressRecorder

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            CetVocabDatabase::class.java,
        ).allowMainThreadQueries().build()
        recorder = ProgressRecorder(database, FixedTime())
    }

    @After
    fun tearDown() = database.close()

    // --- helpers ---------------------------------------------------------------------------

    private fun record(
        word: String,
        meaning: String = "释义",
        level: CetLevel = CetLevel.CET4,
        index: Int = 1,
        phonetic: String = "/test/",
    ) =
        ParsedWordRecord(
            rowNumber = index,
            externalId = null,
            word = word,
            phonetic = phonetic,
            partOfSpeech = "n.",
            meanings = listOf(meaning),
            exampleSentence = "",
            exampleTranslation = "",
            collocations = emptyList(),
            level = level,
            source = "test-pack",
            license = "MIT",
            audioUri = null,
        )

    private fun definition(version: Int, count: Int, level: CetLevel = CetLevel.CET4) = BuiltInPackDefinition(
        id = if (level == CetLevel.CET4) "builtin-cet4" else "builtin-cet6",
        assetPath = "wordpacks/x.json",
        level = level,
        name = if (level == CetLevel.CET4) "CET-4 词汇" else "CET-6 词汇",
        version = version,
        expectedWordCount = count,
    )

    private suspend fun wordIdOf(word: String): Long? =
        database.wordDao().byNormalized(CetLevel.CET4.id, Word.normalizeWordKey(word))?.id

    // --- tests -----------------------------------------------------------------------------

    @Test
    fun `first seed inserts every word and records the pack version`() = runTest {
        val words = listOf(record("apple"), record("banana", index = 2))
        val source = FakePackSource(
            definitions = listOf(definition(version = 2, count = 2)),
            wordsByPack = mutableMapOf("builtin-cet4" to words),
        )
        val repository = DefaultWordRepository(database, FixedTime(), source)

        repository.ensureBuiltInPacks()

        assertThat(database.wordDao().count(CetLevel.CET4.id)).isEqualTo(2)
        val pack = database.wordDao().packById("builtin-cet4")
        assertThat(pack).isNotNull()
        assertThat(pack!!.version).isEqualTo("2")
        assertThat(pack.isBuiltIn).isTrue()
        assertThat(pack.wordCount).isEqualTo(2)
    }

    @Test
    fun `re-running the seed for an up-to-date pack does no work`() = runTest {
        val words = listOf(record("apple"), record("banana", index = 2))
        val source = FakePackSource(listOf(definition(2, 2)), mutableMapOf("builtin-cet4" to words))
        val repository = DefaultWordRepository(database, FixedTime(), source)

        repository.ensureBuiltInPacks()
        val loadsAfterFirst = source.loadCount
        repository.ensureBuiltInPacks()

        // The second run sees the stored version and skips parsing entirely: the 3 MB assets must
        // not be re-parsed on every cold start.
        assertThat(source.loadCount).isEqualTo(loadsAfterFirst)
        assertThat(database.wordDao().count(CetLevel.CET4.id)).isEqualTo(2)
    }

    @Test
    fun `upgrading a pack keeps row ids, learning progress and favourites`() = runTest {
        val v1 = listOf(record("apple", meaning = "旧释义"), record("banana", index = 2))
        val source = FakePackSource(listOf(definition(1, 2)), mutableMapOf("builtin-cet4" to v1))
        val repository = DefaultWordRepository(database, FixedTime(), source)
        repository.ensureBuiltInPacks()

        val appleIdBefore = wordIdOf("apple")!!
        // The learner studies one word and favourites it.
        recorder.record(appleIdBefore, ReviewGrade.MASTERED, AnswerSource.STUDY)
        database.progressDao().setFavorite(appleIdBefore, true, 1_768_442_400_000L)

        // A newer pack arrives: the meaning is corrected and a third word is added.
        val v2 = listOf(
            record("apple", meaning = "新释义"),
            record("banana", index = 2),
            record("cherry", index = 3),
        )
        source.upgrade(listOf(definition(2, 3)), mutableMapOf("builtin-cet4" to v2))

        repository.ensureBuiltInPacks()

        val appleIdAfter = wordIdOf("apple")!!
        assertThat(appleIdAfter).isEqualTo(appleIdBefore)

        val progress = database.progressDao().byWordId(appleIdBefore)
        assertThat("progress must survive a word-list upgrade").isNotNull()
        assertThat(progress!!.isFavorite).isTrue()
        assertThat(progress.consecutiveCorrectCount).isEqualTo(1)

        val events = database.reviewEventDao().countSince(CetLevel.CET4.id, 0L)
        assertThat(events).isEqualTo(1)

        assertThat(database.wordDao().count(CetLevel.CET4.id)).isEqualTo(3)
        assertThat(database.wordDao().packById("builtin-cet4")!!.version).isEqualTo("2")
    }

    @Test
    fun `upgrading updates content in place instead of duplicating the word`() = runTest {
        val source = FakePackSource(
            listOf(definition(1, 1)),
            mutableMapOf("builtin-cet4" to listOf(record("apple", meaning = "旧释义"))),
        )
        val repository = DefaultWordRepository(database, FixedTime(), source)
        repository.ensureBuiltInPacks()

        source.upgrade(
            listOf(definition(2, 1)),
            mutableMapOf("builtin-cet4" to listOf(record("apple", meaning = "新释义", phonetic = "/new/"))),
        )
        repository.ensureBuiltInPacks()

        val rows = database.wordDao().allForLevel(CetLevel.CET4.id)
        assertThat(rows).hasSize(1)
        assertThat(rows.single().meanings).contains("新释义")
        assertThat(rows.single().phonetic).isEqualTo("/new/")
    }

    @Test
    fun `a missing asset falls back to the in-code demo packs`() = runTest {
        val failing = object : PackSource {
            override suspend fun definitions(): List<BuiltInPackDefinition> = listOf(
                BuiltInPackDefinition("builtin-cet4", "wordpacks/cet4.json", CetLevel.CET4, "CET-4", 2, 3849),
            )

            override suspend fun load(definition: BuiltInPackDefinition): Outcome<ImportPreview> =
                Outcome.Failure(AppError.io("asset missing"))
        }
        val repository = DefaultWordRepository(database, FixedTime(), failing)

        repository.ensureBuiltInPacks()

        // The app must still start with a usable list rather than an empty one.
        assertThat(database.wordDao().count(CetLevel.CET4.id)).isGreaterThan(0)
        assertThat(database.wordDao().packById("builtin-cet4")).isNotNull()
    }

    @Test
    fun `seeding reports progress per pack`() = runTest {
        val words = (1..1000).map { index -> record("word$index", index = index) }
        val source = FakePackSource(listOf(definition(2, 1000)), mutableMapOf("builtin-cet4" to words))
        val reported = mutableListOf<SeedProgress>()
        val repository = DefaultWordRepository(database, FixedTime(), source) { reported += it }

        repository.ensureBuiltInPacks()

        assertThat(reported).isNotEmpty()
        assertThat(reported.last().processed).isEqualTo(1000)
        assertThat(reported.last().total).isEqualTo(1000)
        assertThat(reported.last().fraction).isEqualTo(1f)
        // Progress must be reported in several steps, not once at the end: that is what keeps the
        // cold-start screen alive instead of looking frozen.
        assertThat(reported.size).isGreaterThan(1)
    }

    @Test
    fun `search finds words by prefix and by meaning`() = runTest {
        val words = listOf(
            record("abandon", meaning = "放弃；抛弃"),
            record("ability", meaning = "能力", index = 2),
            record("absorb", meaning = "吸收", index = 3),
        )
        val source = FakePackSource(listOf(definition(2, 3)), mutableMapOf("builtin-cet4" to words))
        val repository = DefaultWordRepository(database, FixedTime(), source)
        repository.ensureBuiltInPacks()

        val byPrefix = repository.search(CetLevel.CET4, "ab", 10).map { it.word.word }
        assertThat(byPrefix).containsAtLeast("abandon", "ability", "absorb").inOrder()

        val byMeaning = repository.search(CetLevel.CET4, "放弃", 10).map { it.word.word }
        assertThat(byMeaning).containsExactly("abandon")

        val all = repository.search(CetLevel.CET4, "", 10)
        assertThat(all).hasSize(3)

        // A wildcard typed by the user must be treated literally, not as "match everything".
        assertThat(repository.search(CetLevel.CET4, "%", 10)).isEmpty()
    }
}
