package com.cetvocab.app.domain.search

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Unit tests for the approximate-spelling matcher.
 *
 * The interesting cases are the ones a `LIKE` cannot express: a transposed pair of letters, a
 * doubled letter, a missing one — and the negative cases, where a "close enough" answer would be
 * worse than no answer at all.
 */
class ApproximateWordMatcherTest {

    @Test
    fun `identical words are at distance zero`() {
        assertThat(ApproximateWordMatcher.distance("abandon", "abandon", 2)).isEqualTo(0)
    }

    @Test
    fun `common typos cost one edit`() {
        // Adjacent transposition (the classic "i before e" mistake).
        assertThat(ApproximateWordMatcher.distance("recieve", "receive", 2)).isEqualTo(1)
        // Substitution, deletion, insertion.
        assertThat(ApproximateWordMatcher.distance("abandom", "abandon", 2)).isEqualTo(1)
        assertThat(ApproximateWordMatcher.distance("abandn", "abandon", 2)).isEqualTo(1)
        assertThat(ApproximateWordMatcher.distance("abanddon", "abandon", 2)).isEqualTo(1)
        // Case is the caller's problem: the matcher compares what it is given, and a wholly
        // different string is reported as "beyond the bound" rather than with an exact distance.
        assertThat(ApproximateWordMatcher.distance("RECEIVE", "receive", 2)).isEqualTo(3)
    }

    @Test
    fun `distance is symmetric`() {
        val pairs = listOf(
            "recieve" to "receive",
            "abandom" to "abandon",
            "belive" to "believe",
            "cat" to "cats",
            "environment" to "enviroment",
        )
        pairs.forEach { (left, right) ->
            assertThat(ApproximateWordMatcher.distance(left, right, 3))
                .isEqualTo(ApproximateWordMatcher.distance(right, left, 3))
        }
    }

    @Test
    fun `words further apart than the bound are rejected without a full comparison`() {
        val unreachable = ApproximateWordMatcher.maxDistanceFor(4) + 1
        assertThat(ApproximateWordMatcher.distance("cat", "elephant", 1)).isEqualTo(unreachable)
        // A length difference beyond the bound short-circuits immediately.
        assertThat(ApproximateWordMatcher.distance("ab", "abandon", 2)).isEqualTo(3)
    }

    @Test
    fun `two edits are accepted for ordinary words but not for short ones`() {
        // "tommorow" -> "tomorrow" is two substitutions, not one transposition.
        assertThat(ApproximateWordMatcher.distance("tommorow", "tomorrow", 2)).isEqualTo(2)
        assertThat(ApproximateWordMatcher.maxDistanceFor(4)).isEqualTo(1)
        assertThat(ApproximateWordMatcher.maxDistanceFor(5)).isEqualTo(2)
        assertThat(ApproximateWordMatcher.maxDistanceFor(8)).isEqualTo(2)
        assertThat(ApproximateWordMatcher.maxDistanceFor(9)).isEqualTo(3)
    }

    @Test
    fun `only plain english queries are worth an edit-distance pass`() {
        assertThat(ApproximateWordMatcher.isApplicable("ab")).isFalse()
        assertThat(ApproximateWordMatcher.isApplicable("  ")).isFalse()
        assertThat(ApproximateWordMatcher.isApplicable("abc")).isTrue()
        assertThat(ApproximateWordMatcher.isApplicable("  receive  ")).isTrue()
        // A Chinese query is a meaning search; an edit distance over spellings means nothing there.
        assertThat(ApproximateWordMatcher.isApplicable("放弃")).isFalse()
        assertThat(ApproximateWordMatcher.isApplicable("ab1")).isFalse()
    }

    @Test
    fun `ranking puts the most likely correction first`() {
        val ranked = ApproximateWordMatcher.rank(
            query = "recieve",
            candidates = listOf("receive", "relieve", "believe", "receiver", "recollect", "recieve"),
            limit = 10,
        )

        assertThat(ranked.first().word).isEqualTo("receive")
        // The exact spelling is never suggested back to the learner.
        assertThat(ranked.map { it.word }).doesNotContain("recieve")
        // Everything returned really is within the bound.
        assertThat(ranked.all { it.distance <= 2 }).isTrue()
    }

    @Test
    fun `ranking is deterministic and bounded`() {
        val candidates = listOf("believe", "relieve", "receive", "relive", "beloved", "relieved")
        val first = ApproximateWordMatcher.rank("relieve", candidates, limit = 2)
        val second = ApproximateWordMatcher.rank("relieve", candidates, limit = 2)

        assertThat(first).isEqualTo(second)
        assertThat(first.size).isAtMost(2)
    }

    @Test
    fun `an empty query or a zero limit yields nothing`() {
        assertThat(ApproximateWordMatcher.rank("", listOf("receive"), limit = 5)).isEmpty()
        assertThat(ApproximateWordMatcher.rank("receive", listOf("receive"), limit = 0)).isEmpty()
    }
}
