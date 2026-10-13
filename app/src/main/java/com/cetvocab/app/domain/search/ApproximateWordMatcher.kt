package com.cetvocab.app.domain.search

/**
 * Approximate ("did you mean…") spelling match for the vocabulary search.
 *
 * Why this exists: the exact search in `WordDao.search` is a `LIKE '%query%'` scan, which finds a
 * word the learner is still typing (`abor` → `absorb`) but **nothing** when they mistype it
 * (`recieve` → `receive`). A misspelling is the most common reason a lookup fails, so the browser
 * falls back to an edit-distance pass.
 *
 * Everything here is pure Kotlin: no Android, no SQL, no I/O. The SQL side only narrows the
 * candidate set to words of a similar length; this class decides which of them are close enough
 * and in which order they are offered. That split keeps the expensive-looking part testable.
 *
 * The distance is the "optimal string alignment" variant of Levenshtein: insertions, deletions,
 * substitutions **and one adjacent transposition** (the `ie`/`ei` mistake) all cost 1.
 */
object ApproximateWordMatcher {

    /** Queries shorter than this are too generic for an edit-distance pass to say anything useful. */
    const val MIN_QUERY_LENGTH = 3

    /**
     * How far a candidate may be from the query, by query length.
     *
     * One edit for short words (a distance of 2 would make most 4-letter words "close" to each
     * other), two for ordinary words, three only for long ones — which is where a typo usually
     * costs more than one keystroke.
     */
    fun maxDistanceFor(queryLength: Int): Int = when {
        queryLength <= 4 -> 1
        queryLength <= 8 -> 2
        else -> 3
    }

    /**
     * Whether a query is worth the fallback at all.
     *
     * Only plain ASCII letter queries qualify: a Chinese query is a meaning search, where an
     * edit distance over the *English* spelling would be meaningless.
     */
    fun isApplicable(query: String): Boolean {
        val trimmed = query.trim()
        return trimmed.length >= MIN_QUERY_LENGTH && trimmed.all { it in 'a'..'z' || it in 'A'..'Z' }
    }

    /** One accepted candidate. */
    data class Match(val word: String, val distance: Int)

    /**
     * Filters and orders [candidates] (already lower-cased normalized spellings) against [query].
     *
     * Ordering is by distance first, then by how close the length is, then alphabetically — so the
     * first row is always the most plausible correction, and the list never depends on row order in
     * the database.
     */
    fun rank(
        query: String,
        candidates: List<String>,
        limit: Int,
        maxDistance: Int = maxDistanceFor(query.trim().length),
    ): List<Match> {
        val needle = query.trim().lowercase()
        if (needle.isEmpty() || limit <= 0) return emptyList()

        val matches = ArrayList<Match>()
        candidates.forEach { candidate ->
            val word = candidate.lowercase()
            if (word == needle) return@forEach
            val distance = distance(needle, word, maxDistance)
            if (distance <= maxDistance) matches += Match(word, distance)
        }

        return matches
            .sortedWith(
                compareBy<Match> { it.distance }
                    .thenBy { kotlin.math.abs(it.word.length - needle.length) }
                    .thenBy { it.word },
            )
            .take(limit)
    }

    /**
     * Optimal string alignment distance, bounded by [maxDistance].
     *
     * Returns `maxDistance + 1` as soon as the value cannot come back under the bound, which is what
     * makes a pass over a few thousand candidates cheap: the rows are banded to `|i - j| <=
     * maxDistance`, and a row whose minimum already exceeds the bound aborts the whole comparison.
     */
    fun distance(a: String, b: String, maxDistance: Int): Int {
        if (a == b) return 0
        if (maxDistance <= 0) return 1
        if (kotlin.math.abs(a.length - b.length) > maxDistance) return maxDistance + 1

        val unreachable = maxDistance + 1
        var prevPrev = IntArray(b.length + 1)
        var prev = IntArray(b.length + 1) { it }
        var current = IntArray(b.length + 1)

        for (i in 1..a.length) {
            current[0] = i
            val from = maxOf(1, i - maxDistance)
            val to = minOf(b.length, i + maxDistance)
            // Everything left of the band is unreachable in this row; stale values from two rows
            // ago must not leak into the transposition lookup.
            for (j in 0 until minOf(from, b.length + 1)) current[j] = unreachable
            current[0] = i

            var rowMinimum = unreachable
            for (j in from..to) {
                val substitution = if (a[i - 1] == b[j - 1]) 0 else 1
                var value = minOf(
                    prev[j] + 1,          // deletion from a
                    current[j - 1] + 1,   // insertion into a
                    prev[j - 1] + substitution,
                )
                if (i > 1 && j > 1 && a[i - 1] == b[j - 2] && a[i - 2] == b[j - 1]) {
                    // Adjacent transposition: "recieve" -> "receive" costs 1, not 2.
                    value = minOf(value, prevPrev[j - 2] + 1)
                }
                current[j] = value
                if (value < rowMinimum) rowMinimum = value
            }
            for (j in (to + 1)..b.length) current[j] = unreachable

            if (rowMinimum > maxDistance) return unreachable

            val recycled = prevPrev
            prevPrev = prev
            prev = current
            current = recycled
        }
        return prev[b.length].coerceAtMost(unreachable)
    }
}
