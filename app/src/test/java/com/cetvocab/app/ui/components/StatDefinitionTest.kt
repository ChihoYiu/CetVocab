package com.cetvocab.app.ui.components

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Tests for the statistics definitions.
 *
 * The product requirement is that every number on the dashboard can be explained, and that the
 * explanation is accurate. Those definitions are data, so they can be tested directly — which is
 * also why they live in an enum instead of in prose inside a screen.
 */
class StatDefinitionTest {

    @Test
    fun `every definition has a title and an explanation`() {
        StatDefinition.entries.forEach { definition ->
            assertThat(definition.title.isNotBlank()).isTrue()
            assertThat(definition.body.isNotBlank()).isTrue()
            assertThat(definition.body.length).isAtLeast(10)
        }
    }

    @Test
    fun `titles are unique so the sheet cannot show the same row twice`() {
        val titles = StatDefinition.entries.map { it.title }
        assertThat(titles.toSet().size).isEqualTo(titles.size)
    }

    @Test
    fun `the metrics shown on the dashboard all have a definition`() {
        // If a card starts showing a new number, this fails until the explanation is written.
        val titles = StatDefinition.entries.map { it.title }.toSet()
        assertThat(titles).containsAtLeast(
            "今日已学",
            "今日正确率",
            "今日时长",
            "已掌握",
            "学习中",
            "未学习",
            "连续学习天数",
            "阶段目标",
            "下一步建议",
        )
    }

    @Test
    fun `the explanations state the traps that make a number look wrong`() {
        // "今日已学" counts distinct words (not answers) — the single most misread number.
        assertThat(StatDefinition.STUDIED_TODAY.body).contains("不同单词")
        // The study day starts at 04:00, which explains "I studied yesterday but the streak moved".
        assertThat(StatDefinition.STREAK.body).contains("凌晨 4 点")
        // "No data" must never be shown as 0%.
        assertThat(StatDefinition.ACCURACY_TODAY.body).contains("暂无数据")
        // The duration cap is what stops a backgrounded app from inflating study time.
        assertThat(StatDefinition.DURATION_TODAY.body).contains("2 分钟")
    }

    @Test
    fun `no definition uses markdown emphasis that would render as asterisks`() {
        StatDefinition.entries.forEach { definition ->
            assertThat(definition.body).doesNotContain("**")
            assertThat(definition.title).doesNotContain("**")
        }
    }
}
