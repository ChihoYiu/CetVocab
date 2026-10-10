package com.cetvocab.app.domain.importer

import com.cetvocab.app.core.result.ErrorCode
import com.cetvocab.app.core.result.Outcome
import com.cetvocab.app.domain.model.CetLevel
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Unit tests for the word-list importer.
 *
 * The contract under test is "never crash": a malformed file must produce an
 * [Outcome.Failure] or a row level issue, never an exception.
 */
class WordPackParserTest {

    private val defaultSource = "unit-test"
    private val defaultLicense = "CC0"

    private fun parse(
        fileName: String,
        content: String,
        defaultLevel: CetLevel = CetLevel.CET4,
    ): Outcome<ImportPreview> = WordPackParser.parse(
        fileName = fileName,
        content = content,
        defaultLevel = defaultLevel,
        defaultSource = defaultSource,
        defaultLicense = defaultLicense,
    )

    private fun preview(fileName: String, content: String): ImportPreview {
        val outcome = parse(fileName, content)
        assertThat(outcome.isSuccess).isTrue()
        return requireNotNull(outcome.getOrNull()) { "expected a successful parse" }
    }

    // --- JSON ------------------------------------------------------------------------------

    @Test
    fun `a wrapped json pack is parsed with its pack level and metadata`() {
        val result = preview(
            "cet4.json",
            """
            {
              "version": 1,
              "name": "四级核心",
              "source": "示例来源",
              "license": "CC BY 4.0",
              "defaultLevel": "CET6",
              "words": [
                {
                  "id": "w1",
                  "word": "apple",
                  "phonetic": "/ˈæpl/",
                  "partOfSpeech": "n.",
                  "meanings": ["苹果", "苹果树"],
                  "exampleSentence": "An apple a day.",
                  "collocations": ["apple pie"]
                }
              ]
            }
            """.trimIndent(),
        )

        assertThat(result.format).isEqualTo(PackFormat.JSON)
        assertThat(result.packName).isEqualTo("四级核心")
        assertThat(result.fileName).isEqualTo("cet4.json")
        assertThat(result.validCount).isEqualTo(1)
        assertThat(result.invalidCount).isEqualTo(0)
        assertThat(result.totalRows).isEqualTo(1)
        assertThat(result.duplicateRows).isEqualTo(0)

        val record = result.records.single()
        assertThat(record.rowNumber).isEqualTo(1)
        assertThat(record.externalId).isEqualTo("w1")
        assertThat(record.word).isEqualTo("apple")
        assertThat(record.phonetic).isEqualTo("/ˈæpl/")
        assertThat(record.partOfSpeech).isEqualTo("n.")
        assertThat(record.meanings).containsExactly("苹果", "苹果树").inOrder()
        assertThat(record.collocations).containsExactly("apple pie")
        assertThat(record.level).isEqualTo(CetLevel.CET6)
        assertThat(record.source).isEqualTo("示例来源")
        assertThat(record.license).isEqualTo("CC BY 4.0")
        assertThat(result.levelCounts).containsExactly(CetLevel.CET6, 1)
    }

    @Test
    fun `a bare json array is accepted`() {
        val result = preview(
            "words.json",
            """
            [
              {"word": "apple", "meaning": "苹果"},
              {"word": "banana", "meanings": ["香蕉"]}
            ]
            """.trimIndent(),
        )

        assertThat(result.records.map { it.word }).containsExactly("apple", "banana").inOrder()
        assertThat(result.records.first().meanings).containsExactly("苹果")
        assertThat(result.records.last().meanings).containsExactly("香蕉")
        assertThat(result.levelCounts).containsExactly(CetLevel.CET4, 2)
        assertThat(result.packName).isEqualTo("words")
    }

    @Test
    fun `a single meaning string is split like a csv cell`() {
        val result = preview(
            "words.json",
            """[{"word": "apple", "meaning": "苹果 | 苹果树"}]""",
        )

        assertThat(result.records.single().meanings).containsExactly("苹果", "苹果树").inOrder()
    }

    @Test
    fun `the level defaults to the caller level and a row may override it`() {
        val result = preview(
            "words.json",
            """
            [
              {"word": "alpha", "meanings": ["甲"]},
              {"word": "beta", "meanings": ["乙"], "level": "CET6"},
              {"word": "gamma", "meanings": ["丙"], "level": "nonsense"}
            ]
            """.trimIndent(),
        )

        assertThat(result.records.single { it.word == "alpha" }.level).isEqualTo(CetLevel.CET4)
        assertThat(result.records.single { it.word == "beta" }.level).isEqualTo(CetLevel.CET6)
        assertThat(result.records.single { it.word == "gamma" }.level).isEqualTo(CetLevel.OTHER)
        assertThat(result.levelCounts).containsExactly(CetLevel.CET4, 1, CetLevel.CET6, 1, CetLevel.OTHER, 1)
    }

    @Test
    fun `the default level argument is used when the file declares none`() {
        val outcome = WordPackParser.parse(
            fileName = "words.json",
            content = """[{"word": "apple", "meanings": ["苹果"]}]""",
            defaultLevel = CetLevel.OTHER,
            defaultSource = defaultSource,
            defaultLicense = defaultLicense,
        )

        assertThat(outcome.getOrNull()!!.records.single().level).isEqualTo(CetLevel.OTHER)
    }

    @Test
    fun `a row without a word becomes an issue instead of a crash`() {
        val result = preview(
            "words.json",
            """
            [
              {"word": "apple", "meanings": ["苹果"]},
              {"meanings": ["没有单词"]},
              {"word": "   ", "meanings": ["空白单词"]},
              {"word": "12345", "meanings": ["没有字母"]}
            ]
            """.trimIndent(),
        )

        assertThat(result.validCount).isEqualTo(1)
        assertThat(result.invalidCount).isEqualTo(3)
        assertThat(result.totalRows).isEqualTo(4)
        assertThat(result.issues.map { it.rowNumber }).containsExactly(2, 3, 4).inOrder()
        assertThat(result.issues.all { it.field == "word" }).isTrue()
    }

    @Test
    fun `a row without meanings becomes an issue instead of a crash`() {
        val result = preview(
            "words.json",
            """
            [
              {"word": "apple", "meanings": ["苹果"]},
              {"word": "banana"},
              {"word": "cherry", "meanings": []},
              {"word": "date", "meanings": ["  "]}
            ]
            """.trimIndent(),
        )

        assertThat(result.validCount).isEqualTo(1)
        assertThat(result.invalidCount).isEqualTo(3)
        assertThat(result.issues.all { it.field == "meanings" }).isTrue()
        assertThat(result.issues.map { it.rowNumber }).containsExactly(2, 3, 4).inOrder()
    }

    @Test
    fun `a file in which every row is bad fails with an empty error`() {
        val outcome = parse("words.json", """[{"word": "12345", "meanings": ["释义"]}]""")

        assertThat(outcome.isSuccess).isFalse()
        assertThat((outcome as Outcome.Failure).error.code).isEqualTo(ErrorCode.EMPTY)
    }

    @Test
    fun `a duplicate word inside one file keeps the richer record and is counted`() {
        val result = preview(
            "words.json",
            """
            [
              {"word": "apple", "meanings": ["苹果"]},
              {
                "word": "Apple",
                "phonetic": "/ˈæpl/",
                "partOfSpeech": "n.",
                "meanings": ["苹果", "苹果树"]
              }
            ]
            """.trimIndent(),
        )

        assertThat(result.validCount).isEqualTo(1)
        assertThat(result.duplicateRows).isEqualTo(1)
        assertThat(result.totalRows).isEqualTo(2)
        assertThat(result.invalidCount).isEqualTo(0)

        val record = result.records.single()
        assertThat(record.phonetic).isEqualTo("/ˈæpl/")
        assertThat(record.partOfSpeech).isEqualTo("n.")
        assertThat(record.meanings).containsExactly("苹果", "苹果树").inOrder()
    }

    @Test
    fun `a sparse duplicate never overwrites the richer record`() {
        val result = preview(
            "words.json",
            """
            [
              {"word": "apple", "phonetic": "/ˈæpl/", "meanings": ["苹果", "苹果树"]},
              {"word": "APPLE", "meanings": ["苹果"]}
            ]
            """.trimIndent(),
        )

        assertThat(result.duplicateRows).isEqualTo(1)
        assertThat(result.records.single().phonetic).isEqualTo("/ˈæpl/")
        assertThat(result.records.single().meanings).containsExactly("苹果", "苹果树").inOrder()
    }

    @Test
    fun `the same word in two levels is not a duplicate`() {
        val result = preview(
            "words.json",
            """
            [
              {"word": "apple", "meanings": ["苹果"], "level": "CET4"},
              {"word": "apple", "meanings": ["苹果"], "level": "CET6"}
            ]
            """.trimIndent(),
        )

        assertThat(result.validCount).isEqualTo(2)
        assertThat(result.duplicateRows).isEqualTo(0)
    }

    @Test
    fun `blank content fails with an empty error`() {
        assertThat((parse("words.json", "") as Outcome.Failure).error.code)
            .isEqualTo(ErrorCode.EMPTY)
        assertThat((parse("words.json", "   \n\t ") as Outcome.Failure).error.code)
            .isEqualTo(ErrorCode.EMPTY)
    }

    @Test
    fun `content larger than the size limit fails with a too large error`() {
        // Built at runtime: it only has to be a few bytes above the 8 MiB limit.
        val hugeMeaning = "x".repeat(8 * 1024 * 1024 + 1_024)
        val outcome = parse("words.json", """[{"word": "apple", "meanings": ["$hugeMeaning"]}]""")

        assertThat(outcome.isSuccess).isFalse()
        assertThat((outcome as Outcome.Failure).error.code).isEqualTo(ErrorCode.TOO_LARGE)
        assertThat(outcome.error.message).isNotEmpty()
    }

    @Test
    fun `truncated json fails without throwing`() {
        val outcome = parse("words.json", """{"words": [{"word": "apple", "meanings": ["苹""")

        assertThat(outcome.isSuccess).isFalse()
        assertThat((outcome as Outcome.Failure).error.code).isEqualTo(ErrorCode.PARSE)
    }

    @Test
    fun `a json array with a wrong element type fails without throwing`() {
        val outcome = parse("words.json", """[1, 2, 3]""")

        assertThat(outcome.isSuccess).isFalse()
        assertThat((outcome as Outcome.Failure).error.code).isEqualTo(ErrorCode.PARSE)
    }

    @Test
    fun `a json object with the wrong field types fails without throwing`() {
        val outcome = parse("words.json", """{"words": "not-an-array"}""")

        assertThat(outcome.isSuccess).isFalse()
        assertThat((outcome as Outcome.Failure).error.code).isEqualTo(ErrorCode.PARSE)
    }

    @Test
    fun `an unknown json field is ignored`() {
        val result = preview(
            "words.json",
            """[{"word": "apple", "meanings": ["苹果"], "futureField": {"nested": true}}]""",
        )

        assertThat(result.validCount).isEqualTo(1)
        assertThat(result.records.single().word).isEqualTo("apple")
    }

    @Test
    fun `source and license fall back to the defaults`() {
        val result = preview("words.json", """[{"word": "apple", "meanings": ["苹果"]}]""")

        assertThat(result.records.single().source).isEqualTo(defaultSource)
        assertThat(result.records.single().license).isEqualTo(defaultLicense)
        assertThat(result.licenseSummary).isEqualTo(defaultLicense)
    }

    @Test
    fun `a row level source overrides the file level source`() {
        val result = preview(
            "words.json",
            """[{"word": "apple", "meanings": ["苹果"], "source": "行来源", "license": "MIT"}]""",
        )

        assertThat(result.records.single().source).isEqualTo("行来源")
        assertThat(result.records.single().license).isEqualTo("MIT")
    }

    @Test
    fun `very long words are rejected and over long meanings are flagged`() {
        val longWord = "a".repeat(WordPackParser.MAX_WORD_LENGTH + 1)
        val longMeaning = "释".repeat(600)
        val result = preview(
            "words.json",
            """
            [
              {"word": "$longWord", "meanings": ["过长单词"]},
              {"word": "apple", "meanings": ["$longMeaning"]}
            ]
            """.trimIndent(),
        )

        assertThat(result.validCount).isEqualTo(1)
        // Two rows are reported: the over-long word is *skipped* (word-level issue) ...
        assertThat(result.issues.single { it.field == "word" }.rowNumber).isEqualTo(1)
        // ... while the over-long meaning is only flagged and truncated, so that row is kept.
        assertThat(result.issues.single { it.field == "meanings" }.rowNumber).isEqualTo(2)
        assertThat(result.records.single().word).isEqualTo("apple")
        assertThat(result.records.single().meanings.single()).startsWith("释")
        assertThat(result.records.single().meanings.single().length).isLessThan(longMeaning.length)
    }

    // --- CSV -------------------------------------------------------------------------------

    @Test
    fun `csv columns are mapped by header name`() {
        val result = preview(
            "words.csv",
            """
            word,meanings,phonetic,partOfSpeech,level,source,id
            apple,"苹果|苹果树",/ˈæpl/,n.,CET6,示例,row-1
            """.trimIndent(),
        )

        assertThat(result.format).isEqualTo(PackFormat.CSV)
        assertThat(result.validCount).isEqualTo(1)

        val record = result.records.single()
        assertThat(record.word).isEqualTo("apple")
        assertThat(record.meanings).containsExactly("苹果", "苹果树").inOrder()
        assertThat(record.phonetic).isEqualTo("/ˈæpl/")
        assertThat(record.partOfSpeech).isEqualTo("n.")
        assertThat(record.level).isEqualTo(CetLevel.CET6)
        assertThat(record.source).isEqualTo("示例")
        assertThat(record.externalId).isEqualTo("row-1")
        assertThat(record.rowNumber).isEqualTo(2)
    }

    @Test
    fun `csv headers accept chinese aliases`() {
        val result = preview(
            "words.csv",
            """
            单词,释义,音标,词性
            apple,苹果,/ˈæpl/,n.
            """.trimIndent(),
        )

        assertThat(result.validCount).isEqualTo(1)
        val record = result.records.single()
        assertThat(record.word).isEqualTo("apple")
        assertThat(record.meanings).containsExactly("苹果")
        assertThat(record.phonetic).isEqualTo("/ˈæpl/")
        assertThat(record.partOfSpeech).isEqualTo("n.")
    }

    @Test
    fun `csv headers accept english and ipa aliases`() {
        val result = preview(
            "words.csv",
            """
            english,translation,ipa,pos
            apple,苹果,/ˈæpl/,n.
            """.trimIndent(),
        )

        assertThat(result.validCount).isEqualTo(1)
        assertThat(result.records.single().word).isEqualTo("apple")
    }

    @Test
    fun `quoted fields keep commas`() {
        val result = preview(
            "words.csv",
            """
            word,meanings
            apple,"苹果, 苹果树"
            """.trimIndent(),
        )

        assertThat(result.validCount).isEqualTo(1)
        assertThat(result.records.single().meanings).containsExactly("苹果, 苹果树")
    }

    @Test
    fun `quoted fields keep embedded newlines`() {
        val result = preview(
            "words.csv",
            "word,meanings,exampleSentence\n" +
                "apple,苹果,\"First line\nSecond line\"\n",
        )

        assertThat(result.validCount).isEqualTo(1)
        val record = result.records.single()
        assertThat(record.word).isEqualTo("apple")
        assertThat(record.exampleSentence).isEqualTo("First line\nSecond line")
    }

    @Test
    fun `escaped quotes are unescaped`() {
        val result = preview(
            "words.csv",
            "word,meanings\n" +
                "apple,\"他说\"\"你好\"\"\"\n",
        )

        assertThat(result.validCount).isEqualTo(1)
        assertThat(result.records.single().meanings).containsExactly("他说\"你好\"")
    }

    @Test
    fun `a csv without a word column fails`() {
        val outcome = parse(
            "words.csv",
            """
            meanings,phonetic
            苹果,/ˈæpl/
            """.trimIndent(),
        )

        assertThat(outcome.isSuccess).isFalse()
        assertThat((outcome as Outcome.Failure).error.code).isEqualTo(ErrorCode.PARSE)
    }

    @Test
    fun `csv meanings are split on pipes semicolons and newlines`() {
        val result = preview(
            "words.csv",
            """
            word,meanings
            apple,苹果|苹果树
            banana,香蕉;芭蕉
            cherry,樱桃
            """.trimIndent(),
        )

        assertThat(result.validCount).isEqualTo(3)
        assertThat(result.records.single { it.word == "apple" }.meanings)
            .containsExactly("苹果", "苹果树").inOrder()
        assertThat(result.records.single { it.word == "banana" }.meanings)
            .containsExactly("香蕉", "芭蕉").inOrder()
    }

    @Test
    fun `csv duplicates keep the richer record`() {
        val result = preview(
            "words.csv",
            """
            word,meanings,phonetic,partOfSpeech
            apple,苹果,,
            Apple,苹果|苹果树,/ˈæpl/,n.
            """.trimIndent(),
        )

        assertThat(result.validCount).isEqualTo(1)
        assertThat(result.duplicateRows).isEqualTo(1)
        assertThat(result.totalRows).isEqualTo(2)
        assertThat(result.records.single().phonetic).isEqualTo("/ˈæpl/")
        assertThat(result.records.single().meanings).containsExactly("苹果", "苹果树").inOrder()
    }

    @Test
    fun `ragged csv rows are skipped without crashing`() {
        val result = preview(
            "words.csv",
            """
            word,meanings,phonetic
            apple,苹果,/ˈæpl/
            banana
            ,,
            cherry,樱桃
            """.trimIndent(),
        )

        assertThat(result.validCount).isEqualTo(2)
        assertThat(result.records.map { it.word }).containsExactly("apple", "cherry").inOrder()
        // A ragged row (cherry has no phonetic cell at all) is still parsed, with the missing
        // trailing cell left empty.
        assertThat(result.records.single { it.word == "cherry" }.phonetic).isEmpty()
        // The ",," line is not a row: CsvCodec drops rows whose cells are all empty, so it is
        // neither counted nor reported as a problem — only banana's missing meanings is.
        assertThat(result.totalRows).isEqualTo(3)
        assertThat(result.issues.map { it.rowNumber }).containsExactly(3).inOrder()
        assertThat(result.issues.single().field).isEqualTo("meanings")
    }

    @Test
    fun `a csv with only bad rows fails with an empty error`() {
        val outcome = parse(
            "words.csv",
            """
            word,meanings
            12345,没有字母
            """.trimIndent(),
        )

        assertThat(outcome.isSuccess).isFalse()
        assertThat((outcome as Outcome.Failure).error.code).isEqualTo(ErrorCode.EMPTY)
    }

    @Test
    fun `an unknown file extension is parsed as json`() {
        val result = preview("words.txt", """[{"word": "apple", "meanings": ["苹果"]}]""")

        assertThat(result.format).isEqualTo(PackFormat.JSON)
        assertThat(result.validCount).isEqualTo(1)
    }

    @Test
    fun `the csv format is detected case insensitively`() {
        val result = preview(
            "WORDS.CSV",
            "word,meanings\napple,苹果\n",
        )

        assertThat(result.format).isEqualTo(PackFormat.CSV)
        assertThat(result.packName).isEqualTo("WORDS")
    }

    @Test
    fun `preview flags are consistent with the parsed records`() {
        val result = preview(
            "words.json",
            """
            [
              {"word": "apple", "meanings": ["苹果"]},
              {"word": "apple", "meanings": ["苹果", "苹果树"]},
              {"meanings": ["坏行"]}
            ]
            """.trimIndent(),
        )

        assertThat(result.isEmpty).isFalse()
        assertThat(result.validCount).isEqualTo(result.records.size)
        assertThat(result.invalidCount).isEqualTo(result.issues.size)
        assertThat(result.records.size + result.duplicateRows + result.issues.size)
            .isEqualTo(result.totalRows)
    }
}
