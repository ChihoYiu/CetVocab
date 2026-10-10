package com.cetvocab.app.domain.study

/** Where the "next step" action button should take the learner. */
enum class NextStepDestination { STUDY, REVIEW, NONE }

/**
 * "What should I do right now?" — the single decision the home screen must answer.
 *
 * This is a pure function of [TodayPlan] so the interesting cases (review is due / only new words
 * are left / the plan is done / there is no vocabulary at all) have deterministic, testable
 * behaviour instead of being re-derived inline in the UI. Getting this wrong is a real product
 * failure: a home screen that says "继续学习" without saying *what* is the exact complaint this
 * iteration addresses.
 */
sealed interface NextStep {

    val actionLabel: String?
    val destination: NextStepDestination

    /** Reviewing is the highest-value action: it is what makes words stick. */
    data class Review(val dueCount: Int, val estimatedMinutes: Int) : NextStep {
        override val actionLabel: String get() = "开始复习"
        override val destination: NextStepDestination get() = NextStepDestination.REVIEW
    }

    /** Nothing is due, but new words are still available inside today's goal. */
    data class LearnNew(val newCount: Int, val estimatedMinutes: Int) : NextStep {
        override val actionLabel: String get() = "开始背新词"
        override val destination: NextStepDestination get() = NextStepDestination.STUDY
    }

    /** Today's plan is finished; the mistake book is the useful thing left. */
    data class PracticeMistakes(val mistakeCount: Int) : NextStep {
        override val actionLabel: String get() = "巩固错题"
        override val destination: NextStepDestination get() = NextStepDestination.REVIEW
    }

    /** Everything for today is done. A calm, finished state — not an error state. */
    data object AllDone : NextStep {
        override val actionLabel: String? get() = null
        override val destination: NextStepDestination get() = NextStepDestination.NONE
    }

    /** Nothing to do because there is no vocabulary at all (e.g. the list never loaded). */
    data class NoVocabulary(val message: String) : NextStep {
        override val actionLabel: String? get() = null
        override val destination: NextStepDestination get() = NextStepDestination.NONE
    }
}

/**
 * Turns today's plan into one concrete next action.
 *
 * Priority order is deliberate and matches the learning science the app already implements:
 *  1. **due reviews** — forgetting is the biggest risk; a due card is worth more than a new one;
 *  2. **new words** — only while inside the daily goal, so the app never pushes an unplanned load;
 *  3. **mistakes** — when the plan is complete, consolidating errors is the best remaining use of
 *     time;
 *  4. **finished** — otherwise say so plainly.
 *
 * The time estimate is shown to the learner ("约 4 分钟") because an unbounded task is a common
 * reason people do not start. It is derived from two stated constants and always labelled "约".
 */
object NextStepPlanner {

    /** A review card is a quick recognition step. */
    const val SECONDS_PER_REVIEW = 20

    /** A brand-new word needs reading, the example and a first recall attempt. */
    const val SECONDS_PER_NEW_WORD = 45

    fun plan(plan: TodayPlan): NextStep {
        val due = plan.dueCount.coerceAtLeast(0)
        if (due > 0) {
            return NextStep.Review(dueCount = due, estimatedMinutes = minutesFor(due, SECONDS_PER_REVIEW))
        }

        val newWords = plan.remainingNew.coerceAtMost(plan.newAvailableCount).coerceAtLeast(0)
        if (newWords > 0) {
            return NextStep.LearnNew(
                newCount = newWords,
                estimatedMinutes = minutesFor(newWords, SECONDS_PER_NEW_WORD),
            )
        }

        val mistakes = plan.activeMistakeCount.coerceAtLeast(0)
        if (mistakes > 0) {
            return NextStep.PracticeMistakes(mistakeCount = mistakes)
        }

        // "No vocabulary at all" is a different situation from "done for today", and the app must
        // not congratulate a learner on finishing a list that was never loaded. The signal is
        // precise: nothing available, nothing due, nothing studied today.
        val studiedToday = plan.studiedToday.coerceAtLeast(0)
        if (plan.newAvailableCount <= 0 && studiedToday == 0) {
            return NextStep.NoVocabulary("当前级别还没有可学习的单词，请检查词库是否已导入。")
        }

        return NextStep.AllDone
    }

    /** Rounds up, with a floor of one minute: "约 0 分钟" would be absurd. */
    private fun minutesFor(count: Int, secondsEach: Int): Int =
        ((count.toLong() * secondsEach + 59) / 60).toInt().coerceAtLeast(1)

    /** Human sentence for the card, kept here so the wording is testable in one place. */
    fun describe(step: NextStep): String = when (step) {
        is NextStep.Review ->
            if (step.dueCount == 1) {
                "还有 1 个单词到期，约 1 分钟"
            } else {
                "还需复习 ${step.dueCount} 个单词，约 ${step.estimatedMinutes} 分钟"
            }

        is NextStep.LearnNew ->
            if (step.newCount == 1) {
                "今天还可以学 1 个新词，约 1 分钟"
            } else {
                "今天还可以学 ${step.newCount} 个新词，约 ${step.estimatedMinutes} 分钟"
            }

        is NextStep.PracticeMistakes -> "今天的计划已完成，可以巩固 ${step.mistakeCount} 个错题"
        NextStep.AllDone -> "今天的计划已完成，明天继续保持"
        is NextStep.NoVocabulary -> step.message
    }
}
