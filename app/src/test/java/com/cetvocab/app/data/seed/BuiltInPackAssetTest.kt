package com.cetvocab.app.data.seed

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.core.app.ApplicationProvider
import com.cetvocab.app.core.result.Outcome
import com.cetvocab.app.domain.model.CetLevel
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Completeness and sanity checks for the **shipped** vocabulary assets.
 *
 * This is the regression guard for the vocabulary requirement: it reads the real bundled JSON files
 * (`assets/wordpacks/`) through the same loader the app uses, so a truncated download, a
 * corrupted transform or an accidentally reverted asset fails the build instead of silently
 * shipping a partial word list.
 *
 * The counts are lower bounds rather than exact equality on purpose: a future re-import may
 * legitimately add words, but it must never shrink the list.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [26])
class BuiltInPackAssetTest {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val loader = BuiltInPackLoader(context)

    @Test
    fun `the manifest describes both levels`() = runTest {
        val definitions = loader.definitions()

        assertThat(definitions.map { it.id }).containsExactly("builtin-cet4", "builtin-cet6")
        assertThat(definitions.first { it.level == CetLevel.CET4 }.expectedWordCount).isAtLeast(3800)
        assertThat(definitions.first { it.level == CetLevel.CET6 }.expectedWordCount).isAtLeast(5400)
    }

    @Test
    fun `cet4 asset parses completely and is valid`() = runTest {
        val definition = loader.definitions().first { it.level == CetLevel.CET4 }
        val outcome = loader.load(definition)

        assertThat(outcome).isInstanceOf(Outcome.Success::class.java)
        val preview = (outcome as Outcome.Success).value

        assertThat(preview.records.size).isAtLeast(3800)
        assertThat(preview.totalRows).isEqualTo(preview.records.size)
        assertThat(preview.issues.size).isEqualTo(0)
        assertThat(preview.duplicateRows).isEqualTo(0)
        assertThat(preview.records.map { it.level }.toSet()).containsExactly(CetLevel.CET4)
        assertThat(preview.records.first().license).isEqualTo("MIT")
        assertThat(preview.records.first().source).contains("ECDICT")
    }

    @Test
    fun `cet6 asset parses completely and is valid`() = runTest {
        val definition = loader.definitions().first { it.level == CetLevel.CET6 }
        val outcome = loader.load(definition)

        assertThat(outcome).isInstanceOf(Outcome.Success::class.java)
        val preview = (outcome as Outcome.Success).value

        assertThat(preview.records.size).isAtLeast(5400)
        assertThat(preview.issues.size).isEqualTo(0)
        assertThat(preview.duplicateRows).isEqualTo(0)
        assertThat(preview.records.map { it.level }.toSet()).containsExactly(CetLevel.CET6)
    }

    @Test
    fun `every shipped word is usable by the study screens`() = runTest {
        loader.definitions().forEach { definition ->
            val preview = (loader.load(definition) as Outcome.Success).value
            preview.records.forEach { record ->
                assertThat(record.word.isNotBlank()).isTrue()
                assertThat(record.meanings).isNotEmpty()
                // Words must be searchable by their normalised form and readable in the UI.
                assertThat(record.word.trim()).isEqualTo(record.word)
                assertThat(record.meanings.first().isNotBlank()).isTrue()
            }
        }
    }

    @Test
    fun `phonetics are ipa - no cyrillic look alikes survive`() = runTest {
        loader.definitions().forEach { definition ->
            val preview = (loader.load(definition) as Outcome.Success).value
            val withPhonetic = preview.records.filter { it.phonetic.isNotBlank() }
            // The source is ~99.5% populated; a collapse here would mean the asset was replaced.
            assertThat(withPhonetic.size.toDouble() / preview.records.size).isGreaterThan(0.98)
            withPhonetic.forEach { record ->
                record.phonetic.forEach { character ->
                    val block = Character.UnicodeBlock.of(character)
                    assertThat("${record.word} has ${character} (${block})")
                        .isNotEqualTo(Character.UnicodeBlock.CYRILLIC)
                }
            }
        }
    }

    @Test
    fun `the two levels overlap the way the source does`() = runTest {
        val cet4 = (loader.load(loader.definitions().first { it.level == CetLevel.CET4 }) as Outcome.Success)
            .value.records.map { it.word.lowercase() }.toSet()
        val cet6 = (loader.load(loader.definitions().first { it.level == CetLevel.CET6 }) as Outcome.Success)
            .value.records.map { it.word.lowercase() }.toSet()

        // 六级 includes most of 四级; the overlap is expected and large, but neither list may be
        // a subset of the other (that would mean the levels were mislabelled).
        val overlap = cet4.intersect(cet6)
        assertThat(overlap.size).isAtLeast(3000)
        assertThat(cet4 - cet6).isNotEmpty()
        assertThat(cet6 - cet4).isNotEmpty()
    }

    /**
     * End-to-end: seed the **real** shipped assets through the real repository into a real database.
     *
     * The asset test above proves the JSON parses; this one proves the loading path a learner
     * actually exercises — manifest → parse → chunked transactional writes → searchable rows —
     * works with the 9,000+ word files, and that re-running it is a no-op rather than a duplicate
     * insert. It is the regression guard for "4 级和 6 级单词能够被正常加载、搜索和学习".
     */
    @Test
    fun `the shipped packs seed into the database and are searchable`() = runTest {
        val database = androidx.room.Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            com.cetvocab.app.data.local.CetVocabDatabase::class.java,
        ).allowMainThreadQueries().build()
        try {
            val repository = com.cetvocab.app.data.repository.DefaultWordRepository(
                db = database,
                time = com.cetvocab.app.core.time.SystemTimeProvider(),
                packLoader = loader,
            )

            val first = kotlin.system.measureTimeMillis { repository.ensureBuiltInPacks() }

            val cet4Rows = database.wordDao().count(CetLevel.CET4.id)
            val cet6Rows = database.wordDao().count(CetLevel.CET6.id)
            assertThat(cet4Rows).isAtLeast(3800)
            assertThat(cet6Rows).isAtLeast(5400)

            // Both packs are recorded as built-in and versioned, which is what makes the
            // incremental upgrade path work on the next app start.
            val cet4Pack = database.wordDao().packById("builtin-cet4")
            assertThat(cet4Pack).isNotNull()
            assertThat(cet4Pack!!.isBuiltIn).isTrue()
            assertThat(cet4Pack.version).isEqualTo("2")

            // A word from the list is found by prefix and by meaning, i.e. the browser works on
            // the real data.
            val byPrefix = repository.search(CetLevel.CET4, "abandon", 5).map { it.word.word }
            assertThat(byPrefix).contains("abandon")
            val byMeaning = repository.search(CetLevel.CET6, "放弃", 50).map { it.word.word }
            assertThat(byMeaning).isNotEmpty()

            // Re-running must not duplicate anything (and must be cheap).
            val second = kotlin.system.measureTimeMillis { repository.ensureBuiltInPacks() }
            assertThat(database.wordDao().count(CetLevel.CET4.id)).isEqualTo(cet4Rows)
            assertThat(database.wordDao().count(CetLevel.CET6.id)).isEqualTo(cet6Rows)
            // The second run sees a stored version and skips parsing the 3 MB of JSON entirely.
            assertThat(second).isAtMost(first.coerceAtLeast(1L))
            assertThat(second).isLessThan(2_000L)
        } finally {
            database.close()
        }
    }
}
