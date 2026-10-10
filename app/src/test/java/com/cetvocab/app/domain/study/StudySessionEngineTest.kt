package com.cetvocab.app.domain.study

import com.cetvocab.app.domain.srs.ReviewGrade
import com.cetvocab.app.domain.srs.SrsConstants
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Unit tests for the study queue builder and the session bookkeeping engine.
 *
 * All word ids and timestamps are fixed literals so that a session can be replayed exactly.
 */
class StudySessionEngineTest {

    private val startedAt = 1_768_450_800_000L
    private val others = listOf(2L, 3L, 4L, 5L, 6L, 7L, 8L)

    private fun items(vararg wordIds: Long): List<StudyQueueItem> =
        wordIds.map { StudyQueueItem(it, QueueReason.DUE) }

    private fun newSession(
        queue: List<Long>,
        newWordIds: Set<Long> = emptySet(),
        reasons: Map<Long, QueueReason> = emptyMap(),
    ) = StudySessionState(
        sessionId = 1L,
        levelId = "CET4",
        queue = queue,
        reasons = reasons,
        newWordIds = newWordIds,
        position = 0,
        repeatCounts = emptyMap(),
        startedAt = startedAt,
        updatedAt = startedAt,
    )

    private fun StudySessionState.answer(grade: ReviewGrade, now: Long = startedAt) =
        StudySessionEngine.applyAnswer(this, grade, now)

    // --- create --------------------------------------------------------------------------

    @Test
    fun `create maps queue items and starts at the first card`() {
        val state = StudySessionEngine.create(
            sessionId = 7L,
            levelId = "CET4",
            items = listOf(
                StudyQueueItem(11L, QueueReason.DUE),
                StudyQueueItem(12L, QueueReason.FRAGILE),
                StudyQueueItem(13L, QueueReason.NEW),
            ),
            newWordIds = setOf(13L),
            startedAt = startedAt,
        )

        assertThat(state.sessionId).isEqualTo(7L)
        assertThat(state.levelId).isEqualTo("CET4")
        assertThat(state.queue).containsExactly(11L, 12L, 13L).inOrder()
        assertThat(state.position).isEqualTo(0)
        assertThat(state.currentWordId).isEqualTo(11L)
        assertThat(state.reason).isEqualTo(QueueReason.DUE)
        assertThat(state.repeatCounts).isEmpty()
        assertThat(state.answered).isEqualTo(0)
        assertThat(state.startedAt).isEqualTo(startedAt)
        assertThat(state.updatedAt).isEqualTo(startedAt)
        assertThat(state.isFinished).isFalse()
        assertThat(state.totalCards).isEqualTo(3)
    }

    @Test
    fun `create keeps the reason of every word`() {
        val state = StudySessionEngine.create(
            sessionId = 1L,
            levelId = "CET4",
            items = listOf(
                StudyQueueItem(1L, QueueReason.DUE),
                StudyQueueItem(2L, QueueReason.MISTAKE),
                StudyQueueItem(3L, QueueReason.NEW),
            ),
            newWordIds = setOf(3L),
            startedAt = startedAt,
        )

        assertThat(state.reasons).isEqualTo(
            mapOf(
                1L to QueueReason.DUE,
                2L to QueueReason.MISTAKE,
                3L to QueueReason.NEW,
            ),
        )
    }

    @Test
    fun `progress label counts from one and never exceeds the queue size`() {
        val state = newSession(queue = listOf(1L, 2L, 3L))
        val second = state.answer(ReviewGrade.MASTERED)
        val third = second.answer(ReviewGrade.MASTERED)
        val finished = third.answer(ReviewGrade.MASTERED)

        assertThat(state.progressLabel).isEqualTo("1 / 3")
        assertThat(second.progressLabel).isEqualTo("2 / 3")
        assertThat(third.progressLabel).isEqualTo("3 / 3")
        assertThat(finished.progressLabel).isEqualTo("3 / 3")
    }

    @Test
    fun `progress fraction advances by one card and is clamped`() {
        val state = newSession(queue = listOf(1L, 2L, 3L, 4L))
        val second = state.answer(ReviewGrade.MASTERED)
        val third = second.answer(ReviewGrade.MASTERED)
        val fourth = third.answer(ReviewGrade.MASTERED)

        assertThat(state.progressFraction).isEqualTo(0f)
        assertThat(second.progressFraction).isEqualTo(0.25f)
        assertThat(third.progressFraction).isEqualTo(0.5f)
        assertThat(fourth.progressFraction).isEqualTo(0.75f)
    }

    @Test
    fun `an empty queue is finished from the start`() {
        val state = StudySessionEngine.create(
            sessionId = 1L,
            levelId = "CET4",
            items = emptyList(),
            newWordIds = emptySet(),
            startedAt = startedAt,
        )

        assertThat(state.isFinished).isTrue()
        assertThat(state.totalCards).isEqualTo(0)
        assertThat(state.currentWordId).isNull()
        assertThat(state.progressFraction).isEqualTo(0f)
        // There is no card 1 in an empty queue, so the indicator reads "0 / 0".
        assertThat(state.progressLabel).isEqualTo("0 / 0")
    }

    // --- Immediate repeats ----------------------------------------------------------------

    @Test
    fun `unknown inserts the same word again four cards later`() {
        val state = newSession(queue = listOf(1L) + others)

        val next = state.answer(ReviewGrade.UNKNOWN)

        assertThat(next.answered).isEqualTo(1)
        assertThat(next.position).isEqualTo(1)
        assertThat(next.repeatCounts).containsExactly(1L, 1)
        assertThat(next.queue).hasSize(state.queue.size + 1)
        assertThat(next.queue[1 + SrsConstants.IMMEDIATE_REPEAT_GAP]).isEqualTo(1L)
    }

    @Test
    fun `a second unknown on the same word repeats it again`() {
        // The repeat lands at index 5, so answering the first five cards in order reaches it.
        var state = newSession(queue = listOf(1L) + others)
        state = state.answer(ReviewGrade.UNKNOWN)
        repeat(4) { state = state.answer(ReviewGrade.MASTERED) }

        assertThat(state.currentWordId).isEqualTo(1L)
        assertThat(state.repeatCounts[1L]).isEqualTo(1)

        state = state.answer(ReviewGrade.UNKNOWN)

        assertThat(state.repeatCounts[1L]).isEqualTo(2)
        assertThat(state.queue.count { it == 1L }).isEqualTo(3)
    }

    @Test
    fun `the number of immediate repeats is capped`() {
        var state = newSession(queue = listOf(1L) + others)

        // Drive the session: 不认识 every time word 1 comes back, 已掌握 for every other card.
        // The loop is bounded so that a regression which never re-queues the word fails the
        // test instead of hanging the run.
        var answers = 0
        while (!state.isFinished && answers < 50) {
            val grade = if (state.currentWordId == 1L) ReviewGrade.UNKNOWN else ReviewGrade.MASTERED
            state = state.answer(grade)
            answers++
        }

        assertThat(state.isFinished).isTrue()
        // The word was re-queued exactly MAX_IMMEDIATE_REPEATS times ...
        assertThat(state.repeatCounts[1L]).isEqualTo(SrsConstants.MAX_IMMEDIATE_REPEATS)
        // ... so it appears once as the original card plus one card per repeat, and the session
        // ends after that instead of looping on the same word forever.
        assertThat(state.queue.count { it == 1L }).isEqualTo(1 + SrsConstants.MAX_IMMEDIATE_REPEATS)
        assertThat(state.unknownCount).isEqualTo(1 + SrsConstants.MAX_IMMEDIATE_REPEATS)
        assertThat(state.answered).isEqualTo(state.queue.size)
    }

    @Test
    fun `should repeat immediately encodes the repeat policy`() {
        assertThat(StudySessionEngine.shouldRepeatImmediately(ReviewGrade.UNKNOWN, 0)).isTrue()
        assertThat(StudySessionEngine.shouldRepeatImmediately(ReviewGrade.UNKNOWN, 2)).isTrue()
        assertThat(
            StudySessionEngine.shouldRepeatImmediately(
                ReviewGrade.UNKNOWN,
                SrsConstants.MAX_IMMEDIATE_REPEATS,
            ),
        ).isFalse()
        assertThat(StudySessionEngine.shouldRepeatImmediately(ReviewGrade.FUZZY, 0)).isTrue()
        assertThat(StudySessionEngine.shouldRepeatImmediately(ReviewGrade.FUZZY, 1)).isFalse()
        assertThat(StudySessionEngine.shouldRepeatImmediately(ReviewGrade.MASTERED, 0)).isFalse()
    }

    @Test
    fun `a word already pending later in the queue is not inserted twice`() {
        // The guard is about *adding* a duplicate: word 2 is current and a second copy is still
        // waiting at index 3, so answering 不认识 must not queue a third copy. (Every other word
        // in the fixture appears once.)
        val state = newSession(queue = listOf(2L, 7L, 8L, 2L, 9L, 10L))

        val next = state.answer(ReviewGrade.UNKNOWN)

        assertThat(next.queue).isEqualTo(state.queue)
        assertThat(next.queue.count { it == 2L }).isEqualTo(2)
        assertThat(next.repeatCounts[2L]).isNull()
        assertThat(next.position).isEqualTo(1)
        assertThat(next.answered).isEqualTo(1)
    }

    @Test
    fun `mastered never re-inserts a word`() {
        val state = newSession(queue = listOf(1L) + others)

        val next = state.answer(ReviewGrade.MASTERED)

        assertThat(next.queue).isEqualTo(state.queue)
        assertThat(next.repeatCounts).isEmpty()
        assertThat(next.masteredCount).isEqualTo(1)
        assertThat(next.unknownCount).isEqualTo(0)
    }

    // --- Counters -------------------------------------------------------------------------

    @Test
    fun `counters accumulate per grade`() {
        var state = newSession(
            queue = listOf(1L, 2L, 3L, 4L, 5L),
            newWordIds = setOf(1L, 2L),
        )

        state = state.answer(ReviewGrade.MASTERED)
        state = state.answer(ReviewGrade.FUZZY)
        state = state.answer(ReviewGrade.UNKNOWN)
        state = state.answer(ReviewGrade.MASTERED)

        assertThat(state.answered).isEqualTo(4)
        assertThat(state.masteredCount).isEqualTo(2)
        assertThat(state.fuzzyCount).isEqualTo(1)
        assertThat(state.unknownCount).isEqualTo(1)
        assertThat(state.newSeen).isEqualTo(2)
        assertThat(state.reviewSeen).isEqualTo(2)
        assertThat(state.updatedAt).isEqualTo(startedAt)
    }

    @Test
    fun `counters keep counting immediate repeats of a new word as new cards`() {
        var state = newSession(queue = listOf(1L) + others, newWordIds = setOf(1L))

        state = state.answer(ReviewGrade.UNKNOWN)
        repeat(4) { state = state.answer(ReviewGrade.MASTERED) }
        state = state.answer(ReviewGrade.UNKNOWN)

        assertThat(state.answered).isEqualTo(6)
        assertThat(state.newSeen).isEqualTo(2)
        assertThat(state.reviewSeen).isEqualTo(4)
        assertThat(state.unknownCount).isEqualTo(2)
    }

    @Test
    fun `a finished session is returned unchanged`() {
        val finished = newSession(queue = listOf(1L)).answer(ReviewGrade.MASTERED)

        assertThat(finished.isFinished).isTrue()
        assertThat(finished.answer(ReviewGrade.UNKNOWN)).isEqualTo(finished)
    }

    @Test
    fun `skip advances the cursor without recording an answer`() {
        val state = newSession(queue = listOf(1L, 2L, 3L))
        val skipped = StudySessionEngine.skip(state, startedAt + 1_000L)

        assertThat(skipped.position).isEqualTo(1)
        assertThat(skipped.currentWordId).isEqualTo(2L)
        assertThat(skipped.answered).isEqualTo(0)
        assertThat(skipped.updatedAt).isEqualTo(startedAt + 1_000L)
    }

    // --- StudyQueueBuilder -----------------------------------------------------------------

    @Test
    fun `build orders due then fragile then new`() {
        val queue = StudyQueueBuilder.build(
            dueWordIds = listOf(1L, 2L),
            fragileWordIds = listOf(3L, 4L),
            newWordIds = listOf(5L),
            dailyNewWordGoal = 10,
            dailyReviewLimit = 10,
        )

        assertThat(queue).containsExactly(
            StudyQueueItem(1L, QueueReason.DUE),
            StudyQueueItem(2L, QueueReason.DUE),
            StudyQueueItem(3L, QueueReason.FRAGILE),
            StudyQueueItem(4L, QueueReason.FRAGILE),
            StudyQueueItem(5L, QueueReason.NEW),
        ).inOrder()
    }

    @Test
    fun `build respects the daily review limit across due and fragile words`() {
        val queue = StudyQueueBuilder.build(
            dueWordIds = listOf(1L, 2L, 3L),
            fragileWordIds = listOf(4L, 5L),
            newWordIds = listOf(6L, 7L),
            dailyNewWordGoal = 2,
            dailyReviewLimit = 4,
        )

        assertThat(queue.map { it.wordId }).containsExactly(1L, 2L, 3L, 4L, 6L, 7L).inOrder()
        assertThat(queue.filter { it.reason == QueueReason.DUE }).hasSize(3)
        assertThat(queue.filter { it.reason == QueueReason.FRAGILE }).hasSize(1)
    }

    @Test
    fun `build respects the daily new word goal`() {
        val queue = StudyQueueBuilder.build(
            dueWordIds = emptyList(),
            fragileWordIds = emptyList(),
            newWordIds = listOf(1L, 2L, 3L, 4L),
            dailyNewWordGoal = 2,
            dailyReviewLimit = 40,
        )

        assertThat(queue.map { it.wordId }).containsExactly(1L, 2L).inOrder()
        assertThat(queue.all { it.reason == QueueReason.NEW }).isTrue()
    }

    @Test
    fun `build never drops reviews in favour of new words`() {
        val queue = StudyQueueBuilder.build(
            dueWordIds = listOf(1L, 2L, 3L, 4L, 5L),
            fragileWordIds = emptyList(),
            newWordIds = listOf(6L, 7L),
            dailyNewWordGoal = 2,
            dailyReviewLimit = 3,
        )

        assertThat(queue.map { it.wordId }).containsExactly(1L, 2L, 3L, 6L, 7L).inOrder()
    }

    @Test
    fun `build never duplicates a word across the sections`() {
        val queue = StudyQueueBuilder.build(
            dueWordIds = listOf(1L, 1L, 2L),
            fragileWordIds = listOf(2L, 3L, 3L),
            newWordIds = listOf(3L, 4L, 4L),
            dailyNewWordGoal = 10,
            dailyReviewLimit = 10,
        )

        assertThat(queue.map { it.wordId }).containsExactly(1L, 2L, 3L, 4L).inOrder()
        assertThat(queue.map { it.wordId }.distinct()).hasSize(queue.size)
    }

    @Test
    fun `build clamps negative limits to an empty queue`() {
        val queue = StudyQueueBuilder.build(
            dueWordIds = listOf(1L),
            fragileWordIds = listOf(2L),
            newWordIds = listOf(3L),
            dailyNewWordGoal = -3,
            dailyReviewLimit = -1,
        )

        assertThat(queue).isEmpty()
    }

    @Test
    fun `drill queue marks every word as a mistake and de-duplicates`() {
        val drill = StudyQueueBuilder.drillQueue(listOf(9L, 9L, 4L))

        assertThat(drill).containsExactly(
            StudyQueueItem(9L, QueueReason.MISTAKE),
            StudyQueueItem(4L, QueueReason.MISTAKE),
        ).inOrder()
        assertThat(StudyQueueBuilder.drillQueue(emptyList())).isEmpty()
    }
}
