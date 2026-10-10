package com.cetvocab.app.domain.insight

/**
 * Turns already-computed [LearningSignals] into at most a handful of actionable insights.
 *
 * Why this exists: raw statistics tell the learner *what happened*, but not *what to do
 * next*. This layer encodes the product's tutoring judgement in one pure, testable place
 * that the UI and the statistics screen can both reuse.
 *
 * Design rules that the rest of the file obeys:
 * - **Pure.** No I/O, no clock, no randomness: the same signals always produce the same
 *   list, so the rules can be unit-tested with plain literals.
 * - **Never empty-handed.** When a rule does not apply it is simply absent; every rule that
 *   does apply states at least one real measured number in [Insight.detail].
 * - **Kind, not judgemental.** The wording never shames, never compares the learner with
 *   other users, never counts down and never promises an exam score. Insights describe a
 *   pattern and offer one small next step.
 *
 * All thresholds are `const val`s on this object rather than magic numbers inside the
 * rules. They are **product-tunable**: they were chosen from observed behaviour, they will
 * be revisited as real usage data arrives, and tests read the constants instead of
 * duplicating the numbers.
 */
object InsightEngine {

    // --- Display limits ---------------------------------------------------------------------

    /** At most this many are surfaced on the home screen; the rest live on the statistics page. */
    const val MAX_HOME_INSIGHTS = 2

    /** Upper bound for the statistics page, so the list stays readable. */
    const val MAX_STATS_INSIGHTS = 5

    // --- R8 INSUFFICIENT_DATA thresholds ----------------------------------------------------

    /**
     * Below this many lifetime answer events any percentage is noise, so every other rule is
     * suppressed and the learner is told how much more data is needed.
     */
    const val MIN_TOTAL_ANSWER_EVENTS = 20

    /** Fewer than this many distinct study days cannot show a habit, only a single sitting. */
    const val MIN_TOTAL_ACTIVE_DAYS = 3

    // --- R1 NEW_WORDS_WITHOUT_REVIEW thresholds ---------------------------------------------

    /**
     * This share (percent) of the last week's answers being brand new words means reviews are
     * being skipped; spaced repetition only works when due words are revisited.
     */
    const val R1_NEW_WORD_SHARE_PERCENT = 80

    /** Below this many due words a review session is not worth interrupting the flow for. */
    const val R1_MIN_DUE_TODAY = 10

    // --- R2 ACCURACY_DROP thresholds --------------------------------------------------------

    /** With fewer answers than this, a weekly percentage is too jumpy to call a trend. */
    const val R2_MIN_ANSWERS_LAST_7_DAYS = 20

    /**
     * A week-over-week fall of this many percentage points is outside normal day-to-day
     * variation, so it is worth explaining rather than ignoring.
     */
    const val R2_MIN_ACCURACY_DROP = 8

    // --- R3 WEAK_QUESTION_TYPE thresholds ---------------------------------------------------

    /** Question types answered fewer times than this are excluded: the sample is too small. */
    const val R3_MIN_ATTEMPTS = 10

    /** A type at or below this accuracy is weak in absolute terms, not only relatively. */
    const val R3_MAX_WEAK_ACCURACY = 60

    /** How far (percentage points) the weakest type must sit below the mean of the others. */
    const val R3_MIN_GAP_BELOW_OTHERS = 15

    /** When there is only one question type there is no "others"; use this absolute bar. */
    const val R3_SOLO_MAX_ACCURACY = 50

    // --- R4 MEMORY_LAPSE thresholds ---------------------------------------------------------

    /** Below this many mastered words a single lapse swings the ratio too much to trust. */
    const val R4_MIN_MASTERED = 20

    /** A quarter of the mastered set lapsing within two weeks means intervals grew too fast. */
    const val R4_LAPSE_PERCENT = 25

    // --- R5 FREQUENCY_DROP thresholds -------------------------------------------------------

    /** Three or fewer study days in a week is a sparse rhythm, not a routine. */
    const val R5_MAX_ACTIVE_DAYS_LAST_7 = 3

    /**
     * Only call it a *drop* when the learner really was consistent before (8+ days across the
     * previous fortnight), so a beginner with a naturally light start is not scolded.
     */
    const val R5_MIN_ACTIVE_DAYS_PREVIOUS_14 = 8

    // --- R6 SESSION_TOO_LONG thresholds -----------------------------------------------------

    /** Past this many minutes in a typical day, attention (and thus retention) usually falls. */
    const val R6_MEDIAN_MINUTES_THRESHOLD = 45

    // --- R7 WEAK_WORD_GROUP thresholds ------------------------------------------------------

    /** A group this large is a shared pattern worth one focused session, not a single mistake. */
    const val R7_MIN_GROUP_WORDS = 8

    // --- Rule severities --------------------------------------------------------------------

    /** Highest: the learner is learning new words while due reviews are skipped entirely. */
    const val SEVERITY_NEW_WORDS_WITHOUT_REVIEW = 90

    /** A real decline is more urgent than a stable weakness. */
    const val SEVERITY_ACCURACY_DROP = 80

    /** A specific skill gap is more actionable than a general dip. */
    const val SEVERITY_WEAK_QUESTION_TYPE = 70

    /** Forgetting mastered words costs previously earned progress. */
    const val SEVERITY_MEMORY_LAPSE = 65

    /** Rhythm problems are easier to fix, so they rank below content problems. */
    const val SEVERITY_FREQUENCY_DROP = 60

    /** Session length is a comfort hint, not a correctness problem. */
    const val SEVERITY_SESSION_TOO_LONG = 55

    /** Lowest above R8: a fixed word list is already visible elsewhere in the app. */
    const val SEVERITY_WEAK_WORD_GROUP = 50

    /** Informational only, and the only rule that can appear alone. */
    const val SEVERITY_INSUFFICIENT_DATA = 0

    /**
     * Analyses the signals. Returns [] only when there is nothing to say.
     *
     * Result ordering is severity descending; the rule order documented in the KDoc of
     * [InsightKind] breaks ties, because sorting is stable and the rules are evaluated in
     * that order.
     *
     * When [InsightKind.INSUFFICIENT_DATA] fires it is the only insight returned: every other
     * rule would be reading percentages built from a handful of answers, and acting on noise
     * is worse than saying nothing.
     */
    fun analyze(signals: LearningSignals): List<Insight> {
        if (signals.totalAnswerEvents < MIN_TOTAL_ANSWER_EVENTS ||
            signals.totalActiveDays < MIN_TOTAL_ACTIVE_DAYS
        ) {
            return listOf(insufficientData(signals))
        }

        val found = listOfNotNull(
            newWordsWithoutReview(signals),
            accuracyDrop(signals),
            weakQuestionType(signals),
            memoryLapse(signals),
            frequencyDrop(signals),
            sessionTooLong(signals),
            weakWordGroup(signals),
        )
        return found.sortedByDescending { it.severity }
    }

    /** Home-screen slice: the most severe [MAX_HOME_INSIGHTS]. */
    fun forHome(insights: List<Insight>): List<Insight> = insights.take(MAX_HOME_INSIGHTS)

    // --- R8 ---------------------------------------------------------------------------------

    /**
     * "Not enough data yet", phrased as what is *coming*, never as a countdown.
     *
     * The message counts up ("再作答 8 次左右") instead of down ("还差 8 次") so the sentence
     * reads as progress towards advice rather than a debt to pay off.
     */
    private fun insufficientData(signals: LearningSignals): Insight {
        val answersNeeded = (MIN_TOTAL_ANSWER_EVENTS - signals.totalAnswerEvents).coerceAtLeast(0)
        val daysNeeded = (MIN_TOTAL_ACTIVE_DAYS - signals.totalActiveDays).coerceAtLeast(0)
        val needed = when {
            answersNeeded > 0 && daysNeeded > 0 ->
                "再作答 $answersNeeded 次、再记录 $daysNeeded 天"

            answersNeeded > 0 -> "再作答 $answersNeeded 次左右"
            else -> "再记录 $daysNeeded 天左右"
        }
        return Insight(
            kind = InsightKind.INSUFFICIENT_DATA,
            title = "数据还不够多",
            detail = "目前数据有限，${needed}就能给出更准确的建议。",
            actionLabel = null,
            evidence = mapOf(
                "totalAnswerEvents" to signals.totalAnswerEvents,
                "totalActiveDays" to signals.totalActiveDays,
                "answersNeeded" to answersNeeded,
                "daysNeeded" to daysNeeded,
            ),
            severity = SEVERITY_INSUFFICIENT_DATA,
        )
    }

    // --- R1 ---------------------------------------------------------------------------------

    /**
     * Why 80%: a learner can legitimately spend one session on new words, but once four out of
     * five answers are new for a whole week, due reviews are being starved and the spaced
     * repetition schedule stops paying off. The 10-due-words floor keeps a handful of stragglers
     * from generating a notification.
     */
    private fun newWordsWithoutReview(signals: LearningSignals): Insight? {
        if (signals.answersLast7Days <= 0) return null
        val share = percentOf(signals.newWordAnswersLast7Days, signals.answersLast7Days) ?: return null
        if (share < R1_NEW_WORD_SHARE_PERCENT) return null
        if (signals.dueToday < R1_MIN_DUE_TODAY) return null

        return Insight(
            kind = InsightKind.NEW_WORDS_WITHOUT_REVIEW,
            title = "只学新词，缺少复习",
            detail = "最近 7 天有 $share% 的作答是新词，同时有 ${signals.dueToday} 个词到期，" +
                "先复习它们通常更容易把新词记牢。",
            actionLabel = "去复习这 ${signals.dueToday} 个词",
            evidence = mapOf(
                "newWordAnswersLast7Days" to signals.newWordAnswersLast7Days,
                "answersLast7Days" to signals.answersLast7Days,
                "newWordSharePercent" to share,
                "dueToday" to signals.dueToday,
            ),
            severity = SEVERITY_NEW_WORDS_WITHOUT_REVIEW,
        )
    }

    // --- R2 ---------------------------------------------------------------------------------

    /**
     * Why 20 answers: a weekly percentage computed from fewer answers swings by tens of points
     * on a single mistake. Why 8 points: that is roughly the smallest week-over-week change that
     * also shows up in the following week, so the message is not a one-off bad day.
     */
    private fun accuracyDrop(signals: LearningSignals): Insight? {
        if (signals.answersLast7Days < R2_MIN_ANSWERS_LAST_7_DAYS) return null
        val current = signals.accuracyLast7Days ?: return null
        val previous = signals.accuracyPrevious7Days ?: return null
        val drop = previous - current
        if (drop < R2_MIN_ACCURACY_DROP) return null

        return Insight(
            kind = InsightKind.ACCURACY_DROP,
            title = "正确率有波动",
            detail = "最近 7 天正确率 $current%，比前一周的 $previous% 低了 $drop 个百分点，" +
                "把错题再过一遍通常就能稳住。",
            actionLabel = "去复习错题",
            evidence = mapOf(
                "accuracyLast7Days" to current,
                "accuracyPrevious7Days" to previous,
                "dropPoints" to drop,
                "answersLast7Days" to signals.answersLast7Days,
            ),
            severity = SEVERITY_ACCURACY_DROP,
        )
    }

    // --- R3 ---------------------------------------------------------------------------------

    /**
     * Why 10 attempts: below that a type's percentage is one or two questions. Why 15 points
     * below the mean of the other types: a type is only "the weak one" when it stands clearly
     * apart from the rest, otherwise the learner is just told what they already know. When
     * there is no other type to compare with, the absolute bar (50%) is used instead, because
     * "weakest of one" carries no information.
     */
    private fun weakQuestionType(signals: LearningSignals): Insight? {
        val eligible = signals.examTypesLast30Days.filter { it.attempts >= R3_MIN_ATTEMPTS }
        if (eligible.isEmpty()) return null
        // Ties resolve to the first type the caller supplied, which keeps the rule deterministic.
        val weakestIndex = eligible.indices.minByOrNull { eligible[it].accuracyPercent } ?: return null
        val weakest = eligible[weakestIndex]
        if (weakest.accuracyPercent > R3_MAX_WEAK_ACCURACY) return null

        val others = eligible.filterIndexed { index, _ -> index != weakestIndex }
        val comparedWith: String
        val evidence = LinkedHashMap<String, Int>()
        evidence["attempts"] = weakest.attempts
        evidence["accuracyPercent"] = weakest.accuracyPercent
        if (others.isEmpty()) {
            if (weakest.accuracyPercent > R3_SOLO_MAX_ACCURACY) return null
            comparedWith = "目前只有这一类题型作答记录"
        } else {
            val othersMean = others.sumOf { it.accuracyPercent } / others.size
            if (weakest.accuracyPercent > othersMean - R3_MIN_GAP_BELOW_OTHERS) return null
            comparedWith = "其他题型平均 $othersMean%"
            evidence["othersMeanPercent"] = othersMean
        }

        return Insight(
            kind = InsightKind.WEAK_QUESTION_TYPE,
            title = "有一类题型偏弱",
            detail = "「${weakest.label}」最近 ${weakest.attempts} 题正确率 " +
                "${weakest.accuracyPercent}%，${comparedWith}，专门练几题通常能更快找到规律。",
            actionLabel = "去练${weakest.label}",
            evidence = evidence,
            severity = SEVERITY_WEAK_QUESTION_TYPE,
        )
    }

    // --- R4 ---------------------------------------------------------------------------------

    /**
     * Why 20 mastered words: with a tiny set, one forgotten word can cross any ratio. Why 25%:
     * a few lapses are normal and healthy (they are the signal to review), but when a quarter of
     * everything the learner had mastered slips in two weeks, the intervals were stretched too
     * fast for their memory, not their effort.
     */
    private fun memoryLapse(signals: LearningSignals): Insight? {
        if (signals.masteredCount < R4_MIN_MASTERED) return null
        val lapsedShare = percentOf(signals.masteredLapsedLast14Days, signals.masteredCount) ?: return null
        if (lapsedShare < R4_LAPSE_PERCENT) return null

        return Insight(
            kind = InsightKind.MEMORY_LAPSE,
            title = "熟词有点松动",
            detail = "最近 14 天有 ${signals.masteredLapsedLast14Days} 个已掌握的词又答错或答模糊，" +
                "占已掌握词的 $lapsedShare%（共 ${signals.masteredCount} 个），" +
                "把复习间隔缩短一点会更容易记住。",
            actionLabel = "缩短复习间隔",
            evidence = mapOf(
                "masteredLapsedLast14Days" to signals.masteredLapsedLast14Days,
                "masteredCount" to signals.masteredCount,
                "lapsedPercent" to lapsedShare,
            ),
            severity = SEVERITY_MEMORY_LAPSE,
        )
    }

    // --- R5 ---------------------------------------------------------------------------------

    /**
     * A small, concrete daily target suggested when the rhythm became sparse.
     *
     * Deliberately tiny and independent of the learner's past volume: the point is to make
     * restarting trivial, not to reproduce their previous pace.
     */
    private const val SUGGESTED_DAILY_WORDS = 10

    /**
     * Why compare two windows: a sparse week only matters if it is a change. Requiring 8+ active
     * days across the previous fortnight means the learner has demonstrated they *can* keep a
     * rhythm, so the suggestion is a return to their own baseline rather than an outside standard.
     */
    private fun frequencyDrop(signals: LearningSignals): Insight? {
        if (signals.activeDaysLast7Days > R5_MAX_ACTIVE_DAYS_LAST_7) return null
        if (signals.activeDaysPrevious14Days < R5_MIN_ACTIVE_DAYS_PREVIOUS_14) return null

        return Insight(
            kind = InsightKind.FREQUENCY_DROP,
            title = "节奏慢下来了",
            detail = "最近 7 天有 ${signals.activeDaysLast7Days} 天学习，而之前两周有 " +
                "${signals.activeDaysPrevious14Days} 天，短而固定的节奏比偶尔一次长时间学习更容易坚持。",
            actionLabel = "设个每天 $SUGGESTED_DAILY_WORDS 词的小目标",
            evidence = mapOf(
                "activeDaysLast7Days" to signals.activeDaysLast7Days,
                "activeDaysPrevious14Days" to signals.activeDaysPrevious14Days,
                "suggestedDailyWords" to SUGGESTED_DAILY_WORDS,
            ),
            severity = SEVERITY_FREQUENCY_DROP,
        )
    }

    // --- R6 ---------------------------------------------------------------------------------

    /**
     * Why 45 minutes: this is where self-reported sessions in this app start getting longer
     * without getting more accurate. The rule additionally requires accuracy to be *falling*
     * week over week, because a long session that is going well is not a problem to fix — the
     * insight only fires when the extra time is not buying anything.
     */
    private fun sessionTooLong(signals: LearningSignals): Insight? {
        if (signals.medianMinutesPerDayLast7Days < R6_MEDIAN_MINUTES_THRESHOLD) return null
        val current = signals.accuracyLast7Days ?: return null
        val previous = signals.accuracyPrevious7Days ?: return null
        if (current >= previous) return null

        val drop = previous - current
        val suggestedChunk = R6_MEDIAN_MINUTES_THRESHOLD / 3
        return Insight(
            kind = InsightKind.SESSION_TOO_LONG,
            title = "单次学得有点久",
            detail = "最近 7 天每天中位学习 ${signals.medianMinutesPerDayLast7Days} 分钟，" +
                "而正确率从 $previous% 变成 $current%（低 $drop 个百分点），" +
                "分成每段 $suggestedChunk 分钟左右通常更轻松也更记得住。",
            actionLabel = "改成每段 $suggestedChunk 分钟",
            evidence = mapOf(
                "medianMinutesPerDayLast7Days" to signals.medianMinutesPerDayLast7Days,
                "accuracyLast7Days" to current,
                "accuracyPrevious7Days" to previous,
                "dropPoints" to drop,
                "suggestedChunkMinutes" to suggestedChunk,
            ),
            severity = SEVERITY_SESSION_TOO_LONG,
        )
    }

    // --- R7 ---------------------------------------------------------------------------------

    /**
     * Why 8 words: a couple of wrong words is normal churn, but a group this size that keeps
     * coming back is one shared pattern (similar spelling, same root, same confusion) and is
     * worth a single focused session instead of many scattered retries.
     */
    private fun weakWordGroup(signals: LearningSignals): Insight? {
        val group = signals.weakestMistakeGroup ?: return null
        if (group.wordCount < R7_MIN_GROUP_WORDS) return null

        return Insight(
            kind = InsightKind.WEAK_WORD_GROUP,
            title = "有一组词总答错",
            detail = "「${group.label}」里有 ${group.wordCount} 个词反复答错，" +
                "集中过一遍这组词比零散重做更省时间。",
            actionLabel = "复习这 ${group.wordCount} 个词",
            evidence = mapOf(
                "groupWordCount" to group.wordCount,
                "groupWordIds" to group.wordIds.size,
            ),
            severity = SEVERITY_WEAK_WORD_GROUP,
        )
    }

    // --- Helpers ----------------------------------------------------------------------------

    /**
     * Integer percentage, or null when the denominator cannot support one.
     *
     * Every ratio in this file goes through here, which is what makes a division by zero
     * impossible: a non-positive denominator simply yields null and the rule does not fire.
     * The multiplication is done in [Long] before dividing so a large counter cannot overflow
     * into a negative percentage.
     */
    private fun percentOf(numerator: Int, denominator: Int): Int? =
        if (denominator <= 0) null else ((numerator.toLong() * 100L) / denominator).toInt()
}
