package com.cetvocab.app.domain.importer

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Unit tests for the hand written RFC 4180 style CSV reader/writer.
 */
class CsvCodecTest {

    @Test
    fun `parse splits rows and cells`() {
        val rows = CsvCodec.parse("a,b,c\n1,2,3\n")

        assertThat(rows).containsExactly(
            listOf("a", "b", "c"),
            listOf("1", "2", "3"),
        ).inOrder()
    }

    @Test
    fun `parse accepts both LF and CRLF line endings`() {
        val lf = CsvCodec.parse("a,b\n1,2")
        val crlf = CsvCodec.parse("a,b\r\n1,2")

        assertThat(lf).isEqualTo(crlf)
        assertThat(crlf).hasSize(2)
    }

    @Test
    fun `a trailing newline does not create an extra row`() {
        assertThat(CsvCodec.parse("a,b\n1,2\n")).hasSize(2)
        assertThat(CsvCodec.parse("a,b\n1,2\n\n")).hasSize(2)
    }

    @Test
    fun `empty and blank input produces no rows`() {
        assertThat(CsvCodec.parse("")).isEmpty()
        assertThat(CsvCodec.parse("\n\n")).isEmpty()
    }

    @Test
    fun `quoted cells may contain commas`() {
        val rows = CsvCodec.parse("""a,"b,c",d""")

        assertThat(rows.single()).containsExactly("a", "b,c", "d").inOrder()
    }

    @Test
    fun `quoted cells may contain embedded newlines`() {
        val rows = CsvCodec.parse("a,\"line1\nline2\",c\n")

        assertThat(rows).hasSize(1)
        assertThat(rows.single()).containsExactly("a", "line1\nline2", "c").inOrder()
    }

    @Test
    fun `doubled quotes inside a quoted cell become one quote`() {
        // Written with escapes rather than a raw string: the content itself contains `"""`.
        val rows = CsvCodec.parse("a,\"he said \"\"hi\"\"\",c")

        assertThat(rows.single()).containsExactly("a", "he said \"hi\"", "c").inOrder()
    }

    @Test
    fun `an unclosed quote is tolerated`() {
        val rows = CsvCodec.parse("a,\"unclosed")

        assertThat(rows).hasSize(1)
        assertThat(rows.single()).containsExactly("a", "unclosed").inOrder()
    }

    @Test
    fun `ragged rows keep their own width`() {
        // A ragged row (fewer cells than the header) is still a row; a row whose cells are all
        // empty is not data at all and is dropped — that is what makes blank lines in real
        // files harmless (see `a row of empty cells is not a row`).
        val rows = CsvCodec.parse("a,b,c\n1\n,,\nx,,y\n")

        assertThat(rows).containsExactly(
            listOf("a", "b", "c"),
            listOf("1"),
            listOf("x", "", "y"),
        ).inOrder()
    }

    @Test
    fun `a row of empty cells is not a row`() {
        // Blank lines and ",," lines carry no data, so they never become records.
        assertThat(CsvCodec.parse("a,b,c\n,,\n")).containsExactly(listOf("a", "b", "c"))
        assertThat(CsvCodec.parse("a,b,c\n,,\n\n")).containsExactly(listOf("a", "b", "c"))
        assertThat(CsvCodec.parse(",,")).isEmpty()
    }

    @Test
    fun `write uses CRLF and quotes only when needed`() {
        val text = CsvCodec.write(
            listOf(
                listOf("word", "meanings"),
                listOf("apple", "苹果, 苹果树"),
            ),
        )

        assertThat(text).isEqualTo("word,meanings\r\napple,\"苹果, 苹果树\"")
    }

    @Test
    fun `write escapes quotes and newlines`() {
        val text = CsvCodec.write(listOf(listOf("a\"b", "line1\nline2")))

        assertThat(text).isEqualTo("\"a\"\"b\",\"line1\nline2\"")
    }

    @Test
    fun `write then parse round trips`() {
        val rows = listOf(
            listOf("word", "meanings", "example"),
            listOf("apple", "苹果, 苹果树", "First line\nSecond line"),
            listOf("banana", "香蕉", "plain"),
        )

        assertThat(CsvCodec.parse(CsvCodec.write(rows))).isEqualTo(rows)
    }

    @Test
    fun `header keys are normalised`() {
        assertThat(CsvCodec.headerKeys(listOf("Word", "Part Of Speech", "part_of_speech", "音标")))
            .containsExactly("word", "partofspeech", "partofspeech", "音标")
            .inOrder()
        assertThat(CsvCodec.normalizeKey("  Example-Sentence  ")).isEqualTo("examplesentence")
    }

    @Test
    fun `split values handles pipes semicolons and newlines`() {
        assertThat(CsvCodec.splitValues("苹果 | 苹果树")).containsExactly("苹果", "苹果树").inOrder()
        assertThat(CsvCodec.splitValues("苹果;苹果树")).containsExactly("苹果", "苹果树").inOrder()
        assertThat(CsvCodec.splitValues("苹果；苹果树")).containsExactly("苹果", "苹果树").inOrder()
        assertThat(CsvCodec.splitValues("苹果\n苹果树")).containsExactly("苹果", "苹果树").inOrder()
        assertThat(CsvCodec.splitValues("  苹果  ")).containsExactly("苹果")
    }

    @Test
    fun `split values drops empty entries and handles null`() {
        assertThat(CsvCodec.splitValues(null)).isEmpty()
        assertThat(CsvCodec.splitValues("")).isEmpty()
        assertThat(CsvCodec.splitValues("   ")).isEmpty()
        assertThat(CsvCodec.splitValues("苹果||苹果树|")).containsExactly("苹果", "苹果树").inOrder()
    }
}
