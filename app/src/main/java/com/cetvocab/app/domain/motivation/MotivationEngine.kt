package com.cetvocab.app.domain.motivation

/**
 * Chooses the one encouragement line that is shown for a [MotivationContext].
 *
 * ### Why every shipped line is original
 *
 * Quoting a person needs a licence while they are alive and for 70 years after their death, and a
 * wrong attribution is worse than no quotation at all. The app therefore ships no borrowed text:
 * every entry of [catalogue] was written for this app and is marked [MotivationSource.ORIGINAL].
 * [MotivationSource.PUBLIC_DOMAIN] exists in the model so that a curated, verifiable public-domain
 * collection can be added later without touching a single caller, but such a line must carry a
 * non-blank [MotivationMessage.attribution] ("作者 · 作品（年份）"). [isValid] enforces that rule and
 * the tests keep the shipped catalogue honest.
 *
 * ### Why selection is deterministic
 *
 * The line is picked by hashing the context, never by a random number and never by the clock.
 * Compose recomposes a screen many times for reasons the learner never sees; if the line changed
 * on every recomposition it would flicker, and two learners in the same situation would read
 * different text. With a pure derivation the same context always yields the same line, so a
 * composable may simply call [message] without remembering anything, and tests can assert the
 * exact result. Variety comes from the inputs actually changing (a longer streak, a new milestone,
 * a better week), which is the variety a learner notices.
 *
 * The derivation is a small polynomial hash over the stable fields of [MotivationContext]:
 * the moment ordinal, then streak days, longest streak, days since the last session, accuracy
 * percent (`-1` when absent), accuracy delta (`-1` when absent), milestone value and today's
 * answered count, each folded in as `hash = hash * 31 + field`. The pool size is applied with
 * `Math.floorMod` so the index stays in range even after an `Int` overflow. Two calls with equal
 * contexts therefore return equal messages.
 *
 * ### Number weaving
 *
 * A moment may own one "numbered" variant that uses a real value from the context (streak length,
 * milestone value, accuracy, ...). It is only offered when that value is present and sensible, so
 * a missing number falls back to a plain line and the app never prints "0 天" or an "N"
 * placeholder. Numbered variants are derived per call, which is why [catalogue] lists only the
 * context-free lines that are shipped verbatim.
 */
object MotivationEngine {

    /** Longest line the app is willing to render, in characters (a CJK glyph counts as one). */
    const val MAX_TEXT_LENGTH: Int = 30

    /**
     * Picks exactly one line for [context]. Deterministic: same context -> same line.
     *
     * The pool is the moment's numbered variant (when the context carries a usable number)
     * followed by that moment's [catalogue] lines; the index is the hash described on this object.
     */
    fun message(context: MotivationContext): MotivationMessage {
        val candidates = pool(context)
        return candidates[stableIndex(context, candidates.size)]
    }

    /**
     * The full original catalogue, for tests and for the "换一句" affordance.
     *
     * Only the context-free lines appear here; the numbered variants are produced by [message]
     * from the context and are original as well. Every moment is represented by at least four
     * lines, every text is at most [MAX_TEXT_LENGTH] characters, and all of them are
     * [MotivationSource.ORIGINAL] because the app deliberately ships no external quotation yet.
     */
    val catalogue: List<MotivationMessage> = listOf(
        original(
            MotivationMoment.SESSION_COMPLETED,
            "这一节学完了，你把它放进了自己的词库。",
            "刚才那些词，已经在脑子里留下痕迹了。",
            "收工之前，你已经把今天该做的做完了。",
            "一节一节地攒，词就是这样变成自己的。",
        ),
        original(
            MotivationMoment.NEW_STREAK_RECORD,
            "今天比之前任何一天都更连续了。",
            "这是你自己的最长记录，今天又长了一天。",
            "记录被你自己打破了，明天不必更快。",
            "新的个人纪录，靠的是每天那一点点。",
        ),
        original(
            MotivationMoment.RETURNED_AFTER_BREAK,
            "回来了就好，词条还在原地等你。",
            "隔了几天再打开，这一步很不容易。",
            "重新开始的第一天，不用补上什么。",
            "中断过再回来，这本身就是坚持。",
        ),
        original(
            MotivationMoment.ACCURACY_IMPROVED,
            "这几天的正确率比之前稳了一些。",
            "错的少了，是你把细节记得更清了。",
            "正确率在慢慢往上走，慢慢来就好。",
            "更准了，说明你的复习方式在起作用。",
        ),
        original(
            MotivationMoment.MILESTONE_REACHED,
            "又过了一个里程碑，词库更熟了一点。",
            "这一批词已经变成你的了。",
            "到站了，看看回头路，再往前走。",
            "词的数目在长，你的耐心也在长。",
        ),
        original(
            MotivationMoment.STRUGGLING,
            "今天错的多了些，那就慢一点。",
            "卡住的词，正是最值得留下的词。",
            "状态不好也来学了，这一点很难得。",
            "错题不是退步，是还没到熟的时候。",
        ),
        original(
            MotivationMoment.STEADY,
            "今天也照常学一会儿，挺好。",
            "没有大事发生，词在一点点变熟。",
            "按自己的节奏来，日拱一卒。",
            "平常的一天，也是记录里的一天。",
        ),
    ).flatten()

    /**
     * Whether [message] is a line the app may ship.
     *
     * Rejects blank text, text longer than [MAX_TEXT_LENGTH], a public-domain line without a
     * non-blank attribution, and an original line that carries an attribution it cannot have.
     */
    fun isValid(message: MotivationMessage): Boolean {
        if (message.text.isBlank()) return false
        if (message.text.length > MAX_TEXT_LENGTH) return false
        return when (message.source) {
            MotivationSource.PUBLIC_DOMAIN -> !message.attribution.isNullOrBlank()
            MotivationSource.ORIGINAL -> message.attribution.isNullOrBlank()
        }
    }

    private val catalogueByMoment: Map<MotivationMoment, List<MotivationMessage>> =
        catalogue.groupBy { it.moment }

    private fun original(moment: MotivationMoment, vararg texts: String): List<MotivationMessage> =
        texts.map { MotivationMessage(text = it, moment = moment) }

    private fun pool(context: MotivationContext): List<MotivationMessage> =
        numberedVariant(context).filter { it.text.length <= MAX_TEXT_LENGTH } +
            catalogueByMoment[context.moment].orEmpty()

    /**
     * The moment's context-dependent line, or nothing when the context has no usable number.
     *
     * Every guard here is also a promise about the text: a value that is absent, zero or out of a
     * sane range simply produces no numbered variant, so the plain catalogue line is used instead.
     */
    private fun numberedVariant(context: MotivationContext): List<MotivationMessage> {
        val text: String? = when (context.moment) {
            MotivationMoment.SESSION_COMPLETED ->
                context.answeredToday.takeIf { it > 0 }
                    ?.let { "刚才答了 $it 个词，一笔一笔都算数。" }

            MotivationMoment.NEW_STREAK_RECORD ->
                context.streakDays.takeIf { it >= 2 }
                    ?.let { "连续 $it 天，是你自己接上的新记录。" }

            MotivationMoment.RETURNED_AFTER_BREAK ->
                context.daysSinceLastStudy.takeIf { it >= 3 }
                    ?.let { "离开 $it 天又回来，进度还在。" }

            MotivationMoment.ACCURACY_IMPROVED -> {
                val percent = context.accuracyPercent?.takeIf { it in 1..100 }
                val delta = context.accuracyDeltaPercent?.takeIf { it in 1..100 }
                if (percent != null && delta != null) {
                    "本周正确率 $percent%，比上周高 $delta 个点。"
                } else {
                    null
                }
            }

            MotivationMoment.MILESTONE_REACHED ->
                context.milestoneValue.takeIf { it > 0 }
                    ?.let { "第 $it 个词到手，台阶是自己垒的。" }

            MotivationMoment.STRUGGLING ->
                context.accuracyPercent?.takeIf { it in 1..100 }
                    ?.let { "今天正确率 $it%，先歇口气再继续。" }

            MotivationMoment.STEADY ->
                context.streakDays.takeIf { it >= 1 }
                    ?.let { "第 $it 天，按自己的速度往前走。" }
        }
        return text
            ?.let { listOf(MotivationMessage(text = it, moment = context.moment)) }
            .orEmpty()
    }

    /**
     * The documented index derivation: a polynomial hash of the stable context fields, folded into
     * `[0, size)` with `Math.floorMod` so it never goes negative.
     */
    private fun stableIndex(context: MotivationContext, size: Int): Int {
        if (size <= 0) return 0
        var hash = context.moment.ordinal + 1
        hash = hash * 31 + context.streakDays
        hash = hash * 31 + context.longestStreakDays
        hash = hash * 31 + context.daysSinceLastStudy
        hash = hash * 31 + (context.accuracyPercent ?: -1)
        hash = hash * 31 + (context.accuracyDeltaPercent ?: -1)
        hash = hash * 31 + context.milestoneValue
        hash = hash * 31 + context.answeredToday
        return Math.floorMod(hash, size)
    }
}
