package com.cetvocab.app.domain.motivation

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.cetvocab.app.data.motivation.AssetStartupTipSource
import com.cetvocab.app.data.motivation.StartupTipSource
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import kotlin.random.Random

/**
 * Tests for the cold-start lines: the **shipped catalogue** and the choice made from it.
 *
 * The catalogue checks run against the real asset through the real loader, so a merge that drops
 * lines, a line that sneaks in twice, or an edit that reintroduces anxiety wording fails the build
 * instead of shipping. The requirements they encode are the product ones: at least 370 usable
 * lines, no duplicates (exact or near), natural, non-anxious Simplified Chinese.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [26])
class StartupTipTest {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val source: StartupTipSource = AssetStartupTipSource(context)

    /** Wording that would turn encouragement into pressure; must never ship. */
    private val banned = listOf(
        "熬夜", "拼命", "否则你就", "落后", "淘汰", "废物", "不配", "必须", "来不及", "羞耻",
    )

    private val punctuation =
        "，。！？、；：『』「」《》（）…—·,.!?;:()[] \t\n\"'“”‘’"

    private fun core(text: String): String = text.filterNot { it in punctuation }

    private fun finalMarks(text: String): Boolean = text.last() in "。！？"

    @Test
    fun `the shipped catalogue has at least 370 usable lines`() = runTest {
        val catalogue = source.load()

        assertThat(catalogue.tips.size).isAtLeast(370)
        assertThat(catalogue.licence).contains("CC0")
    }

    @Test
    fun `every shipped line is clean, readable and unique`() = runTest {
        val tips = source.load().tips

        assertThat(tips.map { it.id }.toSet()).hasSize(tips.size)
        assertThat(tips.map { it.text }.toSet()).hasSize(tips.size)
        tips.forEach { tip ->
            assertWithMessage("${tip.id} has an empty field").that(
                tip.id.isNotEmpty() && tip.category.isNotEmpty(),
            ).isTrue()
            assertWithMessage("${tip.id} is not trimmed: ${tip.text}")
                .that(tip.text).isEqualTo(tip.text.trim())
            val coreText = core(tip.text)
            assertWithMessage("${tip.id} is too short: ${tip.text}")
                .that(coreText.length).isAtLeast(4)
            assertWithMessage("${tip.id} is too long: ${tip.text}")
                .that(coreText.length).isAtMost(40)
            assertWithMessage("${tip.id} does not end with a full stop: ${tip.text}")
                .that(finalMarks(tip.text)).isTrue()
            // Simplified Chinese only: a stray English word or digit would look like a typo on the
            // first screen the learner sees.
            val hasAscii = tip.text.any { it in 'a'..'z' || it in 'A'..'Z' || it in '0'..'9' }
            assertWithMessage("${tip.id} contains ascii characters: ${tip.text}")
                .that(hasAscii).isFalse()
            banned.forEach { wording ->
                assertWithMessage("${tip.id} contains $wording: ${tip.text}")
                    .that(tip.text).doesNotContain(wording)
            }
        }
    }

    @Test
    fun `the catalogue covers several themes and no two lines are near-duplicates`() = runTest {
        val tips = source.load().tips

        assertThat(tips.map { it.category }.toSet().size).isAtLeast(4)

        // Character-set overlap (Jaccard), ignoring punctuation and word order. The merge script
        // rejects anything above 0.60, so 0.70 here leaves room for a hand edit without letting a
        // real duplicate through.
        val cores = tips.map { core(it.text).toSet() }
        var worst = 0.0
        var worstPair = "" to ""
        for (i in cores.indices) {
            for (j in i + 1 until cores.size) {
                val union = (cores[i] + cores[j]).size
                if (union == 0) continue
                val score = (cores[i] intersect cores[j]).size.toDouble() / union
                if (score > worst) {
                    worst = score
                    worstPair = tips[i].text to tips[j].text
                }
            }
        }
        assertWithMessage("most similar pair: ${worstPair.first} ≈ ${worstPair.second}")
            .that(worst).isLessThan(0.70)
    }

    @Test
    fun `the picker avoids the line shown on the previous cold start`() {
        val tips = listOf(
            StartupTip("a", "分类", "第一句。"),
            StartupTip("b", "分类", "第二句。"),
            StartupTip("c", "分类", "第三句。"),
        )

        repeat(20) { seed ->
            val picked = StartupTipPicker.pick(tips, avoidId = "b", random = Random(seed))
            assertThat(picked?.id).isNotEqualTo("b")
        }
    }

    @Test
    fun `the picker always returns something`() {
        val only = listOf(StartupTip("a", "分类", "唯一一句。"))
        // The avoided line is the only line: showing it again beats showing nothing.
        assertThat(StartupTipPicker.pick(only, avoidId = "a", random = Random(1))).isEqualTo(only.first())
        assertThat(StartupTipPicker.pick(emptyList(), avoidId = null, random = Random(1))).isNull()
        assertThat(StartupTipCatalogue.FALLBACK.text).isNotEmpty()
    }

    @Test
    fun `the parse is lenient and never throws`() {
        assertThat(StartupTipParser.parse("not json at all")).isNull()
        assertThat(StartupTipParser.parse("")).isNull()

        val catalogue = StartupTipParser.parse(
            """
            {
              "version": 2,
              "unknownField": true,
              "tips": [
                {"id": "x", "category": "分类", "text": "  一句。  "},
                {"id": "y", "category": "分类", "text": "一句。"},
                {"id": "z", "category": "", "text": ""},
                {"category": "分类", "text": "没有 id 的一句。"}
              ]
            }
            """.trimIndent(),
        )

        assertThat(catalogue).isNotNull()
        val tips = catalogue!!.tips
        // The duplicate text is dropped, the blank one is skipped, the id-less one gets a stable id.
        assertThat(tips.map { it.text }).containsExactly("一句。", "没有 id 的一句。")
        assertThat(tips.first().category).isEqualTo("分类")
        assertThat(tips.last().id).isNotEmpty()
    }
}
