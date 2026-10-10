package com.cetvocab.app.domain.motivation

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Unit tests for the motivation engine.
 *
 * Everything here is pure: no clock, no randomness, no Android framework. The point of the suite is
 * (a) the contract the UI relies on — one short, sincere line per moment, always the same line for
 * the same situation — and (b) the copyright rule, which is enforced by [MotivationEngine.isValid]
 * rather than by a comment.
 */
class MotivationEngineTest {

    /** The surrounded form of a zero day count, i.e. the placeholder the engine must never emit. */
    private val PLACEHOLDER_ZERO_DAYS = " 0 天"

    private fun context(
        moment: MotivationMoment,
        streakDays: Int = 0,
        longestStreakDays: Int = 0,
        daysSinceLastStudy: Int = 0,
        accuracyPercent: Int? = null,
        accuracyDeltaPercent: Int? = null,
        milestoneValue: Int = 0,
        answeredToday: Int = 0,
    ) = MotivationContext(
        moment = moment,
        streakDays = streakDays,
        longestStreakDays = longestStreakDays,
        daysSinceLastStudy = daysSinceLastStudy,
        accuracyPercent = accuracyPercent,
        accuracyDeltaPercent = accuracyDeltaPercent,
        milestoneValue = milestoneValue,
        answeredToday = answeredToday,
    )

    private fun assertUsable(message: MotivationMessage, moment: MotivationMoment) {
        assertThat(message.moment).isEqualTo(moment)
        assertThat(message.text.trim()).isNotEmpty()
        assertThat(message.text.length).isAtMost(MotivationEngine.MAX_TEXT_LENGTH)
        assertThat(message.source).isEqualTo(MotivationSource.ORIGINAL)
        assertThat(MotivationEngine.isValid(message)).isTrue()
    }

    // --- one case per moment ---------------------------------------------------------------

    @Test
    fun `a finished session returns a session line`() {
        val message = MotivationEngine.message(
            context(MotivationMoment.SESSION_COMPLETED, answeredToday = 12, streakDays = 5),
        )

        assertUsable(message, MotivationMoment.SESSION_COMPLETED)
    }

    @Test
    fun `a new streak record returns a record line`() {
        val message = MotivationEngine.message(
            context(MotivationMoment.NEW_STREAK_RECORD, streakDays = 9, longestStreakDays = 9),
        )

        assertUsable(message, MotivationMoment.NEW_STREAK_RECORD)
    }

    @Test
    fun `coming back after a break returns a return line`() {
        val message = MotivationEngine.message(
            context(MotivationMoment.RETURNED_AFTER_BREAK, daysSinceLastStudy = 5),
        )

        assertUsable(message, MotivationMoment.RETURNED_AFTER_BREAK)
    }

    @Test
    fun `a better week returns an accuracy line`() {
        val message = MotivationEngine.message(
            context(MotivationMoment.ACCURACY_IMPROVED, accuracyPercent = 78, accuracyDeltaPercent = 6),
        )

        assertUsable(message, MotivationMoment.ACCURACY_IMPROVED)
    }

    @Test
    fun `a crossed milestone returns a milestone line`() {
        val message = MotivationEngine.message(
            context(MotivationMoment.MILESTONE_REACHED, milestoneValue = 500),
        )

        assertUsable(message, MotivationMoment.MILESTONE_REACHED)
    }

    @Test
    fun `a rough patch returns a struggling line`() {
        val message = MotivationEngine.message(
            context(MotivationMoment.STRUGGLING, accuracyPercent = 41, answeredToday = 20),
        )

        assertUsable(message, MotivationMoment.STRUGGLING)
    }

    @Test
    fun `an ordinary day returns a steady line`() {
        val message = MotivationEngine.message(
            context(MotivationMoment.STEADY, streakDays = 3, answeredToday = 15),
        )

        assertUsable(message, MotivationMoment.STEADY)
    }

    // --- determinism -----------------------------------------------------------------------

    @Test
    fun `the same context always returns the same line`() {
        val contexts = listOf(
            context(MotivationMoment.SESSION_COMPLETED, answeredToday = 12, streakDays = 5),
            context(MotivationMoment.NEW_STREAK_RECORD, streakDays = 9, longestStreakDays = 9),
            context(MotivationMoment.RETURNED_AFTER_BREAK, daysSinceLastStudy = 5),
            context(MotivationMoment.ACCURACY_IMPROVED, accuracyPercent = 78, accuracyDeltaPercent = 6),
            context(MotivationMoment.MILESTONE_REACHED, milestoneValue = 500),
            context(MotivationMoment.STRUGGLING, accuracyPercent = 41),
            context(MotivationMoment.STEADY, streakDays = 3, answeredToday = 15),
            context(MotivationMoment.STEADY),
        )

        for (single in contexts) {
            val first = MotivationEngine.message(single)
            val second = MotivationEngine.message(single.copy())

            assertThat(second).isEqualTo(first)
            assertThat(second.text).isEqualTo(first.text)
        }
    }

    // --- catalogue -------------------------------------------------------------------------

    @Test
    fun `the catalogue offers at least four lines for every moment`() {
        for (moment in MotivationMoment.entries) {
            val lines = MotivationEngine.catalogue.filter { it.moment == moment }

            assertThat(lines.size).isAtLeast(4)
        }
    }

    @Test
    fun `the catalogue ships only original lines`() {
        assertThat(MotivationEngine.catalogue.none { it.source != MotivationSource.ORIGINAL }).isTrue()
        assertThat(MotivationEngine.catalogue.map { it.source }.toSet())
            .containsExactly(MotivationSource.ORIGINAL)
        assertThat(MotivationEngine.catalogue.all { it.attribution == null }).isTrue()
    }

    @Test
    fun `the catalogue has no duplicate lines`() {
        assertThat(MotivationEngine.catalogue.map { it.text }).containsNoDuplicates()
    }

    @Test
    fun `every catalogue line is valid short and free of numbers`() {
        for (message in MotivationEngine.catalogue) {
            assertThat(message.text.trim()).isNotEmpty()
            assertThat(message.text.length).isAtMost(MotivationEngine.MAX_TEXT_LENGTH)
            assertThat(MotivationEngine.isValid(message)).isTrue()
            // Plain lines are shipped verbatim; a number may only arrive from the context.
            assertThat(message.text.any { it.isDigit() }).isFalse()
        }
    }

    // --- isValid ---------------------------------------------------------------------------

    @Test
    fun `isValid rejects a public domain line with no attribution`() {
        val noAttribution = MotivationMessage(
            text = "这是测试里手工构造的一句示例文本。",
            moment = MotivationMoment.STEADY,
            source = MotivationSource.PUBLIC_DOMAIN,
            attribution = null,
        )
        val blankAttribution = noAttribution.copy(attribution = "   ")

        assertThat(MotivationEngine.isValid(noAttribution)).isFalse()
        assertThat(MotivationEngine.isValid(blankAttribution)).isFalse()
    }

    @Test
    fun `isValid accepts a properly attributed public domain line`() {
        val attributed = MotivationMessage(
            text = "这是测试里手工构造的一句示例文本。",
            moment = MotivationMoment.STEADY,
            source = MotivationSource.PUBLIC_DOMAIN,
            attribution = "作者 · 作品（年份）",
        )

        assertThat(MotivationEngine.isValid(attributed)).isTrue()
    }

    @Test
    fun `isValid rejects blank text and text over the length budget`() {
        val blank = MotivationMessage(text = "   ", moment = MotivationMoment.STEADY)
        val tooLong = MotivationMessage(text = "学".repeat(MotivationEngine.MAX_TEXT_LENGTH + 1), moment = MotivationMoment.STEADY)
        val exactlyMax = MotivationMessage(text = "学".repeat(MotivationEngine.MAX_TEXT_LENGTH), moment = MotivationMoment.STEADY)

        assertThat(MotivationEngine.isValid(blank)).isFalse()
        assertThat(MotivationEngine.isValid(tooLong)).isFalse()
        assertThat(MotivationEngine.isValid(exactlyMax)).isTrue()
    }

    @Test
    fun `isValid rejects an original line that carries an attribution`() {
        val borrowed = MotivationMessage(
            text = "这一批词已经变成你的了。",
            moment = MotivationMoment.MILESTONE_REACHED,
            source = MotivationSource.ORIGINAL,
            attribution = "作者 · 作品（年份）",
        )

        assertThat(MotivationEngine.isValid(borrowed)).isFalse()
    }

    // --- fallbacks and variety --------------------------------------------------------------

    @Test
    fun `a context with all default values returns a usable steady message`() {
        val message = MotivationEngine.message(MotivationContext(moment = MotivationMoment.STEADY))

        assertUsable(message, MotivationMoment.STEADY)
        assertThat(message.text.any { it.isDigit() }).isFalse()
    }

    @Test
    fun `different contexts select different lines`() {
        val texts = (1..10).map { answered ->
            MotivationEngine.message(
                context(MotivationMoment.SESSION_COMPLETED, answeredToday = answered),
            ).text
        }

        assertThat(texts.toSet().size).isGreaterThan(1)
    }

    @Test
    fun `numbers from the context are woven into the line`() {
        val milestoneTexts = (0..9).map { streak ->
            MotivationEngine.message(
                context(
                    MotivationMoment.MILESTONE_REACHED,
                    streakDays = streak,
                    milestoneValue = 500,
                ),
            ).text
        }
        val strugglingTexts = (0..9).map { streak ->
            MotivationEngine.message(
                context(MotivationMoment.STRUGGLING, streakDays = streak, accuracyPercent = 42),
            ).text
        }

        assertThat(milestoneTexts.any { it.contains("500") }).isTrue()
        assertThat(strugglingTexts.any { it.contains("42") }).isTrue()
    }

    @Test
    fun `a context without usable numbers never prints a number or a placeholder`() {
        for (moment in MotivationMoment.entries) {
            val text = MotivationEngine.message(MotivationContext(moment = moment)).text

            assertThat(text.trim()).isNotEmpty()
            assertThat(text.any { it.isDigit() }).isFalse()
            assertThat(text.contains(PLACEHOLDER_ZERO_DAYS)).isFalse()
            assertThat(text.contains("N")).isFalse()
        }
    }

    @Test
    fun `every selected line stays inside the length budget and the placeholder rule`() {
        val moments = MotivationMoment.entries
        for (streak in 0..11) {
            for (moment in moments) {
                val message = MotivationEngine.message(
                    context(
                        moment = moment,
                        streakDays = streak,
                        longestStreakDays = streak + 1,
                        daysSinceLastStudy = streak,
                        accuracyPercent = if (streak % 2 == 0) 30 + streak else null,
                        accuracyDeltaPercent = if (streak % 3 == 0) streak else null,
                        milestoneValue = streak * 500,
                        answeredToday = streak,
                    ),
                )

                assertThat(message.moment).isEqualTo(moment)
                assertThat(MotivationEngine.isValid(message)).isTrue()
                assertThat(message.text.length).isAtMost(MotivationEngine.MAX_TEXT_LENGTH)
                // "第 10 天" legitimately contains "0 天"; the bug this guards against is the bare
                // placeholder written as "把 0 天…" style nonsense, so match the surrounded form.
                assertThat(message.text.contains(PLACEHOLDER_ZERO_DAYS)).isFalse()
                assertThat(message.text.contains("N")).isFalse()
            }
        }
    }
}
