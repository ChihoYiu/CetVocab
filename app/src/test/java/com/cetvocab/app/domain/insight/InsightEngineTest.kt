package com.cetvocab.app.domain.insight

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Unit tests for the pure insight rules.
 *
 * Every test builds [LearningSignals] from literals and asserts on the produced [Insight]s,
 * so the rules are verified without a clock, database or Android framework. Most tests start
 * from [quietSignals] — a neutral baseline where no rule fires — and then move exactly one
 * value to the boundary under test, which keeps a failure pointing at a single rule.
 */
class InsightEngineTest {

    // --- fixtures ---------------------------------------------------------------------------

    /**
     * High enough above the R8 floor that rules are evaluated, and neutral for every rule:
     * a quarter of the answers are new words, accuracy is flat, sessions are short, and no
     * exam types or mistake group are supplied.
     */
    private fun quietSignals() = LearningSignals(
        totalAnswerEvents = 300,
        totalActiveDays = 20,
        answersLast7Days = 100,
        accuracyLast7Days = 50,
        accuracyPrevious7Days = 50,
        newWordAnswersLast7Days = 25,
        activeDaysLast7Days = 5,
        activeDaysPrevious14Days = 10,
        medianMinutesPerDayLast7Days = 20,
        dueToday = 0,
        fragileCount = 0,
        masteredCount = 40,
        masteredLapsedLast14Days = 0,
        examTypesLast30Days = emptyList(),
        weakestMistakeGroup = null,
    )

    private fun LearningSignals.kinds(): List<InsightKind> = InsightEngine.analyze(this).map { it.kind }

    /** Kinds of an already-analysed result; the counterpart of [kinds] for the list form. */
    private fun List<Insight>.kinds(): List<InsightKind> = map { it.kind }

    private fun List<Insight>.of(kind: InsightKind): Insight =
        firstOrNull { it.kind == kind } ?: error("expected an insight of kind $kind in $this")

    private fun group(wordCount: Int, label: String = "高频错词") = MistakeGroup(
        label = label,
        wordCount = wordCount,
        wordIds = (1L..wordCount.toLong()).toList(),
    )

    private fun examType(label: String, attempts: Int, accuracyPercent: Int) =
        ExamTypeAccuracy(label = label, attempts = attempts, accuracyPercent = accuracyPercent)

    // --- R8 INSUFFICIENT_DATA ----------------------------------------------------------------

    @Test
    fun `not enough answer events produces only the insufficient data insight`() {
        val signals = quietSignals().copy(totalAnswerEvents = 19)

        val insights = InsightEngine.analyze(signals)

        assertThat(insights).hasSize(1)
        assertThat(insights.single().kind).isEqualTo(InsightKind.INSUFFICIENT_DATA)
        assertThat(insights.single().severity).isEqualTo(0)
        assertThat(insights.single().actionLabel).isNull()
    }

    @Test
    fun `insufficient data states how much more is needed`() {
        val signals = quietSignals().copy(totalAnswerEvents = 12, totalActiveDays = 2)

        val insight = InsightEngine.analyze(signals).single()

        assertThat(insight.detail).contains("目前数据有限")
        assertThat(insight.detail).contains("8")
        assertThat(insight.detail).contains("1")
        assertThat(insight.evidence["answersNeeded"]).isEqualTo(8)
        assertThat(insight.evidence["daysNeeded"]).isEqualTo(1)
    }

    @Test
    fun `twenty answer events clears the insufficient data gate while nineteen does not`() {
        val low = quietSignals().copy(totalAnswerEvents = 19)
        val floor = quietSignals().copy(totalAnswerEvents = 20)

        assertThat(low.kinds()).containsExactly(InsightKind.INSUFFICIENT_DATA)
        assertThat(floor.kinds()).doesNotContain(InsightKind.INSUFFICIENT_DATA)
    }

    @Test
    fun `two active days is insufficient and three active days is enough`() {
        val twoDays = quietSignals().copy(totalActiveDays = 2)
        val threeDays = quietSignals().copy(totalActiveDays = 3)

        assertThat(twoDays.kinds()).containsExactly(InsightKind.INSUFFICIENT_DATA)
        assertThat(threeDays.kinds()).doesNotContain(InsightKind.INSUFFICIENT_DATA)
    }

    @Test
    fun `insufficient data suppresses every other rule that would otherwise fire`() {
        val signals = quietSignals().copy(
            totalAnswerEvents = 10,
            totalActiveDays = 1,
            // Every rule below is individually satisfied by these values.
            answersLast7Days = 100,
            newWordAnswersLast7Days = 95,
            dueToday = 30,
            accuracyLast7Days = 40,
            accuracyPrevious7Days = 70,
            activeDaysLast7Days = 1,
            activeDaysPrevious14Days = 12,
            medianMinutesPerDayLast7Days = 90,
            masteredLapsedLast14Days = 20,
            examTypesLast30Days = listOf(examType("听力", attempts = 20, accuracyPercent = 30)),
            weakestMistakeGroup = group(12),
        )

        val insights = InsightEngine.analyze(signals)

        assertThat(insights).hasSize(1)
        assertThat(insights.single().kind).isEqualTo(InsightKind.INSUFFICIENT_DATA)
        // containsExactly, not just hasSize: none of the seven seeded rules leaked through.
        assertThat(insights.map { it.kind }).containsExactly(InsightKind.INSUFFICIENT_DATA)
    }

    @Test
    fun `empty signals report only insufficient data`() {
        val insights = InsightEngine.analyze(
            LearningSignals(
                totalAnswerEvents = 0,
                totalActiveDays = 0,
                answersLast7Days = 0,
                accuracyLast7Days = null,
                accuracyPrevious7Days = null,
                newWordAnswersLast7Days = 0,
                activeDaysLast7Days = 0,
                activeDaysPrevious14Days = 0,
                medianMinutesPerDayLast7Days = 0,
                dueToday = 0,
                fragileCount = 0,
                masteredCount = 0,
                masteredLapsedLast14Days = 0,
            ),
        )

        assertThat(insights).hasSize(1)
        assertThat(insights.single().kind).isEqualTo(InsightKind.INSUFFICIENT_DATA)
        assertThat(insights.single().detail).isNotEmpty()
    }

    @Test
    fun `a hostile all zero input does not throw and produces no negative evidence`() {
        val signals = quietSignals().copy(
            totalAnswerEvents = 0,
            totalActiveDays = 0,
            answersLast7Days = 0,
            accuracyLast7Days = 0,
            accuracyPrevious7Days = 0,
            newWordAnswersLast7Days = 0,
            activeDaysLast7Days = 0,
            activeDaysPrevious14Days = 0,
            medianMinutesPerDayLast7Days = 0,
            dueToday = 0,
            masteredCount = 0,
            masteredLapsedLast14Days = 0,
            examTypesLast30Days = listOf(examType("", attempts = 0, accuracyPercent = 0)),
            weakestMistakeGroup = group(0, label = ""),
        )

        val insights = InsightEngine.analyze(signals)

        assertThat(insights).hasSize(1)
        assertThat(insights.single().evidence.values.all { it >= 0 }).isTrue()
    }

    @Test
    fun `a neutral baseline produces no insights at all`() {
        assertThat(InsightEngine.analyze(quietSignals())).isEmpty()
    }

    // --- R1 NEW_WORDS_WITHOUT_REVIEW ---------------------------------------------------------

    @Test
    fun `four of five answers being new words with ten due fires the new word rule`() {
        val signals = quietSignals().copy(
            newWordAnswersLast7Days = 80,
            answersLast7Days = 100,
            dueToday = 10,
        )

        val insights = InsightEngine.analyze(signals)

        assertThat(insights.kinds()).contains(InsightKind.NEW_WORDS_WITHOUT_REVIEW)
        val insight = insights.of(InsightKind.NEW_WORDS_WITHOUT_REVIEW)
        assertThat(insight.severity).isEqualTo(InsightEngine.SEVERITY_NEW_WORDS_WITHOUT_REVIEW)
        assertThat(insight.detail).contains("80%")
        assertThat(insight.detail).contains("10")
        assertThat(insight.actionLabel).isEqualTo("去复习这 10 个词")
        assertThat(insight.evidence["newWordSharePercent"]).isEqualTo(80)
        assertThat(insight.evidence["dueToday"]).isEqualTo(10)
    }

    @Test
    fun `just under the new word share does not fire the new word rule`() {
        val signals = quietSignals().copy(
            newWordAnswersLast7Days = 79,
            answersLast7Days = 100,
            dueToday = 30,
        )

        assertThat(signals.kinds()).doesNotContain(InsightKind.NEW_WORDS_WITHOUT_REVIEW)
    }

    @Test
    fun `just under the due today floor does not fire the new word rule`() {
        val signals = quietSignals().copy(
            newWordAnswersLast7Days = 95,
            answersLast7Days = 100,
            dueToday = 9,
        )

        assertThat(signals.kinds()).doesNotContain(InsightKind.NEW_WORDS_WITHOUT_REVIEW)
    }

    @Test
    fun `the new word share is not computed when nothing was answered last week`() {
        val signals = quietSignals().copy(
            answersLast7Days = 0,
            newWordAnswersLast7Days = 0,
            dueToday = 50,
        )

        assertThat(signals.kinds()).doesNotContain(InsightKind.NEW_WORDS_WITHOUT_REVIEW)
    }

    // --- R2 ACCURACY_DROP -------------------------------------------------------------------

    @Test
    fun `an eight point week over week fall fires the accuracy drop rule`() {
        val signals = quietSignals().copy(accuracyLast7Days = 52, accuracyPrevious7Days = 60)

        val insight = InsightEngine.analyze(signals).of(InsightKind.ACCURACY_DROP)

        assertThat(insight.severity).isEqualTo(InsightEngine.SEVERITY_ACCURACY_DROP)
        assertThat(insight.detail).contains("52%")
        assertThat(insight.detail).contains("60%")
        assertThat(insight.detail).contains("8")
        assertThat(insight.actionLabel).isEqualTo("去复习错题")
        assertThat(insight.evidence["dropPoints"]).isEqualTo(8)
    }

    @Test
    fun `a seven point fall does not fire the accuracy drop rule`() {
        val signals = quietSignals().copy(accuracyLast7Days = 53, accuracyPrevious7Days = 60)

        assertThat(signals.kinds()).doesNotContain(InsightKind.ACCURACY_DROP)
    }

    @Test
    fun `fewer than twenty answers last week does not fire the accuracy drop rule`() {
        val signals = quietSignals().copy(
            answersLast7Days = 19,
            accuracyLast7Days = 30,
            accuracyPrevious7Days = 90,
        )

        assertThat(signals.kinds()).doesNotContain(InsightKind.ACCURACY_DROP)
    }

    @Test
    fun `a missing previous week accuracy does not fire the accuracy drop rule`() {
        val signals = quietSignals().copy(accuracyLast7Days = 30, accuracyPrevious7Days = null)

        assertThat(signals.kinds()).doesNotContain(InsightKind.ACCURACY_DROP)
    }

    // --- R3 WEAK_QUESTION_TYPE ---------------------------------------------------------------

    @Test
    fun `a question type fifteen points below the others fires the weak type rule`() {
        val signals = quietSignals().copy(
            examTypesLast30Days = listOf(
                examType("听力", attempts = 10, accuracyPercent = 60),
                examType("阅读", attempts = 10, accuracyPercent = 75),
            ),
        )

        val insight = InsightEngine.analyze(signals).of(InsightKind.WEAK_QUESTION_TYPE)

        assertThat(insight.severity).isEqualTo(InsightEngine.SEVERITY_WEAK_QUESTION_TYPE)
        assertThat(insight.detail).contains("听力")
        assertThat(insight.detail).contains("60%")
        assertThat(insight.detail).contains("75%")
        assertThat(insight.actionLabel).isEqualTo("去练听力")
        assertThat(insight.evidence["othersMeanPercent"]).isEqualTo(75)
    }

    @Test
    fun `a question type only fourteen points below the others does not fire the weak type rule`() {
        val signals = quietSignals().copy(
            examTypesLast30Days = listOf(
                examType("听力", attempts = 10, accuracyPercent = 60),
                examType("阅读", attempts = 10, accuracyPercent = 74),
            ),
        )

        assertThat(signals.kinds()).doesNotContain(InsightKind.WEAK_QUESTION_TYPE)
    }

    @Test
    fun `fewer than ten attempts does not fire the weak type rule`() {
        val signals = quietSignals().copy(
            examTypesLast30Days = listOf(
                examType("听力", attempts = 9, accuracyPercent = 20),
                examType("阅读", attempts = 50, accuracyPercent = 95),
            ),
        )

        assertThat(signals.kinds()).doesNotContain(InsightKind.WEAK_QUESTION_TYPE)
    }

    @Test
    fun `a sole question type needs fifty percent or less to fire the weak type rule`() {
        val weak = quietSignals().copy(
            examTypesLast30Days = listOf(examType("翻译", attempts = 12, accuracyPercent = 50)),
        )
        val notWeak = quietSignals().copy(
            examTypesLast30Days = listOf(examType("翻译", attempts = 12, accuracyPercent = 51)),
        )

        val insight = InsightEngine.analyze(weak).of(InsightKind.WEAK_QUESTION_TYPE)
        assertThat(insight.detail).contains("翻译")
        assertThat(insight.evidence).doesNotContainKey("othersMeanPercent")
        assertThat(notWeak.kinds()).doesNotContain(InsightKind.WEAK_QUESTION_TYPE)
    }

    @Test
    fun `a question type above sixty percent never fires the weak type rule`() {
        val signals = quietSignals().copy(
            examTypesLast30Days = listOf(
                examType("听力", attempts = 20, accuracyPercent = 61),
                examType("阅读", attempts = 20, accuracyPercent = 95),
            ),
        )

        assertThat(signals.kinds()).doesNotContain(InsightKind.WEAK_QUESTION_TYPE)
    }

    // --- R4 MEMORY_LAPSE --------------------------------------------------------------------

    @Test
    fun `a quarter of mastered words lapsing fires the memory lapse rule`() {
        val signals = quietSignals().copy(masteredCount = 40, masteredLapsedLast14Days = 10)

        val insight = InsightEngine.analyze(signals).of(InsightKind.MEMORY_LAPSE)

        assertThat(insight.severity).isEqualTo(InsightEngine.SEVERITY_MEMORY_LAPSE)
        assertThat(insight.detail).contains("10")
        assertThat(insight.detail).contains("25%")
        assertThat(insight.detail).contains("40")
        assertThat(insight.actionLabel).isNotNull()
        assertThat(insight.evidence["lapsedPercent"]).isEqualTo(25)
    }

    @Test
    fun `just under a quarter of mastered words lapsing does not fire the memory lapse rule`() {
        val signals = quietSignals().copy(masteredCount = 40, masteredLapsedLast14Days = 9)

        assertThat(signals.kinds()).doesNotContain(InsightKind.MEMORY_LAPSE)
    }

    @Test
    fun `fewer than twenty mastered words does not fire the memory lapse rule`() {
        val signals = quietSignals().copy(masteredCount = 19, masteredLapsedLast14Days = 19)

        assertThat(signals.kinds()).doesNotContain(InsightKind.MEMORY_LAPSE)
    }

    @Test
    fun `lapses without any mastered words cannot divide by zero`() {
        val signals = quietSignals().copy(masteredCount = 0, masteredLapsedLast14Days = 30)

        assertThat(signals.kinds()).doesNotContain(InsightKind.MEMORY_LAPSE)
    }

    // --- R5 FREQUENCY_DROP ------------------------------------------------------------------

    @Test
    fun `three active days after a consistent fortnight fires the frequency drop rule`() {
        val signals = quietSignals().copy(
            activeDaysLast7Days = 3,
            activeDaysPrevious14Days = 8,
        )

        val insight = InsightEngine.analyze(signals).of(InsightKind.FREQUENCY_DROP)

        assertThat(insight.severity).isEqualTo(InsightEngine.SEVERITY_FREQUENCY_DROP)
        assertThat(insight.detail).contains("3")
        assertThat(insight.detail).contains("8")
        assertThat(insight.actionLabel).isNotNull()
        assertThat(insight.evidence["activeDaysLast7Days"]).isEqualTo(3)
        assertThat(insight.evidence["activeDaysPrevious14Days"]).isEqualTo(8)
    }

    @Test
    fun `four active days does not fire the frequency drop rule`() {
        val signals = quietSignals().copy(activeDaysLast7Days = 4, activeDaysPrevious14Days = 8)

        assertThat(signals.kinds()).doesNotContain(InsightKind.FREQUENCY_DROP)
    }

    @Test
    fun `seven active days in the previous fortnight does not fire the frequency drop rule`() {
        val signals = quietSignals().copy(activeDaysLast7Days = 3, activeDaysPrevious14Days = 7)

        assertThat(signals.kinds()).doesNotContain(InsightKind.FREQUENCY_DROP)
    }

    // --- R6 SESSION_TOO_LONG ----------------------------------------------------------------

    @Test
    fun `a forty five minute median with falling accuracy fires the long session rule`() {
        val signals = quietSignals().copy(
            medianMinutesPerDayLast7Days = 45,
            accuracyLast7Days = 50,
            accuracyPrevious7Days = 60,
        )

        val insight = InsightEngine.analyze(signals).of(InsightKind.SESSION_TOO_LONG)

        assertThat(insight.severity).isEqualTo(InsightEngine.SEVERITY_SESSION_TOO_LONG)
        assertThat(insight.detail).contains("45")
        assertThat(insight.detail).contains("50%")
        assertThat(insight.detail).contains("60%")
        assertThat(insight.actionLabel).isNotNull()
        assertThat(insight.evidence["medianMinutesPerDayLast7Days"]).isEqualTo(45)
    }

    @Test
    fun `forty four minutes does not fire the long session rule`() {
        val signals = quietSignals().copy(
            medianMinutesPerDayLast7Days = 44,
            accuracyLast7Days = 50,
            accuracyPrevious7Days = 60,
        )

        assertThat(signals.kinds()).doesNotContain(InsightKind.SESSION_TOO_LONG)
    }

    @Test
    fun `long sessions with steady accuracy do not fire the long session rule`() {
        val signals = quietSignals().copy(
            medianMinutesPerDayLast7Days = 90,
            accuracyLast7Days = 60,
            accuracyPrevious7Days = 60,
        )

        assertThat(signals.kinds()).doesNotContain(InsightKind.SESSION_TOO_LONG)
    }

    @Test
    fun `long sessions without a previous week baseline do not fire the long session rule`() {
        val signals = quietSignals().copy(
            medianMinutesPerDayLast7Days = 90,
            accuracyLast7Days = 60,
            accuracyPrevious7Days = null,
        )

        assertThat(signals.kinds()).doesNotContain(InsightKind.SESSION_TOO_LONG)
    }

    // --- R7 WEAK_WORD_GROUP -----------------------------------------------------------------

    @Test
    fun `a group of eight repeatedly wrong words fires the weak word group rule`() {
        val signals = quietSignals().copy(weakestMistakeGroup = group(8))

        val insight = InsightEngine.analyze(signals).of(InsightKind.WEAK_WORD_GROUP)

        assertThat(insight.severity).isEqualTo(InsightEngine.SEVERITY_WEAK_WORD_GROUP)
        assertThat(insight.detail).contains("8")
        assertThat(insight.actionLabel).isEqualTo("复习这 8 个词")
        assertThat(insight.evidence["groupWordCount"]).isEqualTo(8)
    }

    @Test
    fun `a group of seven words does not fire the weak word group rule`() {
        val signals = quietSignals().copy(weakestMistakeGroup = group(7))

        assertThat(signals.kinds()).doesNotContain(InsightKind.WEAK_WORD_GROUP)
    }

    @Test
    fun `no mistake group does not fire the weak word group rule`() {
        assertThat(quietSignals().kinds()).doesNotContain(InsightKind.WEAK_WORD_GROUP)
    }

    // --- ordering, limits and content ---------------------------------------------------------

    @Test
    fun `insights are ordered by severity descending`() {
        val signals = quietSignals().copy(
            newWordAnswersLast7Days = 90,
            dueToday = 15,
            accuracyLast7Days = 40,
            accuracyPrevious7Days = 60,
            activeDaysLast7Days = 2,
            medianMinutesPerDayLast7Days = 60,
            masteredLapsedLast14Days = 20,
            weakestMistakeGroup = group(9),
        )

        val insights = InsightEngine.analyze(signals)

        assertThat(insights.map { it.severity })
            .containsExactly(90, 80, 65, 60, 55, 50)
            .inOrder()
        assertThat(insights.map { it.kind }).containsExactly(
            InsightKind.NEW_WORDS_WITHOUT_REVIEW,
            InsightKind.ACCURACY_DROP,
            InsightKind.MEMORY_LAPSE,
            InsightKind.FREQUENCY_DROP,
            InsightKind.SESSION_TOO_LONG,
            InsightKind.WEAK_WORD_GROUP,
        ).inOrder()
    }

    @Test
    fun `forHome keeps at most two insights in severity order`() {
        val signals = quietSignals().copy(
            newWordAnswersLast7Days = 90,
            dueToday = 15,
            accuracyLast7Days = 40,
            accuracyPrevious7Days = 60,
            weakestMistakeGroup = group(9),
        )

        val home = InsightEngine.forHome(InsightEngine.analyze(signals))

        assertThat(home).hasSize(InsightEngine.MAX_HOME_INSIGHTS)
        assertThat(home).hasSize(2)
        assertThat(home.map { it.kind }).containsExactly(
            InsightKind.NEW_WORDS_WITHOUT_REVIEW,
            InsightKind.ACCURACY_DROP,
        ).inOrder()
    }

    @Test
    fun `forHome returns everything when there is less than the home limit`() {
        val signals = quietSignals().copy(weakestMistakeGroup = group(8))

        val insights = InsightEngine.analyze(signals)

        assertThat(insights).hasSize(1)
        assertThat(InsightEngine.forHome(insights)).isEqualTo(insights)
        assertThat(InsightEngine.forHome(emptyList())).isEmpty()
    }

    @Test
    fun `analyze is deterministic for the same signals`() {
        val signals = quietSignals().copy(newWordAnswersLast7Days = 90, dueToday = 15)

        assertThat(InsightEngine.analyze(signals)).isEqualTo(InsightEngine.analyze(signals))
    }

    @Test
    fun `every insight carries a short title and at least one number in its detail`() {
        val signals = quietSignals().copy(
            newWordAnswersLast7Days = 90,
            dueToday = 15,
            accuracyLast7Days = 40,
            accuracyPrevious7Days = 60,
            activeDaysLast7Days = 2,
            medianMinutesPerDayLast7Days = 60,
            masteredLapsedLast14Days = 20,
            weakestMistakeGroup = group(9),
        )

        val insights = InsightEngine.analyze(signals)

        assertThat(insights).hasSize(6)
        insights.forEach { insight ->
            assertThat(insight.title.length).isAtMost(20)
            assertThat(insight.title).isNotEmpty()
            assertThat(insight.detail).isNotEmpty()
            assertThat(insight.detail).containsMatch("[0-9]")
            assertThat(insight.evidence).isNotEmpty()
        }
    }

    @Test
    fun `wording never promises an exam score`() {
        val signals = quietSignals().copy(
            newWordAnswersLast7Days = 90,
            dueToday = 15,
            accuracyLast7Days = 40,
            accuracyPrevious7Days = 60,
            activeDaysLast7Days = 2,
            medianMinutesPerDayLast7Days = 60,
            masteredLapsedLast14Days = 20,
            examTypesLast30Days = listOf(
                examType("听力", attempts = 20, accuracyPercent = 30),
                examType("阅读", attempts = 20, accuracyPercent = 90),
            ),
            weakestMistakeGroup = group(9),
        )

        val allText = InsightEngine.analyze(signals)
            .flatMap { listOfNotNull(it.title, it.detail, it.actionLabel) }
            .joinToString(" ")

        listOf("分数", "提分", "必过", "保过", "考试", "还差", "剩余", "倒数").forEach { forbidden ->
            assertThat(allText).doesNotContain(forbidden)
        }
    }

    @Test
    fun `a complete signal set fires every rule in severity order`() {
        val signals = quietSignals().copy(
            newWordAnswersLast7Days = 90,
            dueToday = 15,
            accuracyLast7Days = 40,
            accuracyPrevious7Days = 60,
            activeDaysLast7Days = 2,
            medianMinutesPerDayLast7Days = 60,
            masteredLapsedLast14Days = 20,
            examTypesLast30Days = listOf(
                examType("听力", attempts = 20, accuracyPercent = 30),
                examType("阅读", attempts = 20, accuracyPercent = 90),
            ),
            weakestMistakeGroup = group(9),
        )

        val insights = InsightEngine.analyze(signals)

        assertThat(insights).hasSize(7)
        assertThat(insights.map { it.severity })
            .containsExactly(90, 80, 70, 65, 60, 55, 50)
            .inOrder()
        // analyze() reports every rule that fires; MAX_STATS_INSIGHTS is the caller's display
        // cap, and because the list is already severity-sorted the cap keeps the gravest ones.
        assertThat(insights.take(InsightEngine.MAX_STATS_INSIGHTS)).hasSize(5)
        assertThat(insights.first().kind).isEqualTo(InsightKind.NEW_WORDS_WITHOUT_REVIEW)
    }
}
