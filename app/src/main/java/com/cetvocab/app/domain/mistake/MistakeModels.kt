package com.cetvocab.app.domain.mistake

import com.cetvocab.app.domain.model.CetLevel
import com.cetvocab.app.domain.model.Word
import com.cetvocab.app.domain.srs.WordProgress
import java.time.LocalDate

/**
 * The categories of the review centre (复习中心).
 *
 * Each category maps to exactly one indexed database query, so switching tabs never loads
 * the whole mistake table into memory.
 */
enum class MistakeCategory(val label: String, val description: String) {
    DUE_TODAY("今日待复习", "按复习计划今天该看的词"),
    UNCONSOLIDATED("未巩固词", "答错或标记模糊后还没有稳固的词"),
    HIGH_WRONG("高错误次数", "错得最多、最值得反复看的词"),
    RECENT("最近错题", "最近 7 天产生的错题"),
    HISTORY("历史错题", "包含已标记为已巩固的记录"),
    FAVORITES("收藏单词", "手动收藏、随时想看的词"),
}

/** One row of the mistake book. */
data class MistakeItem(
    val word: Word,
    val progress: WordProgress,
    val wrongCount: Int,
    val fuzzyCount: Int,
    val firstWrongAt: Long,
    val lastWrongAt: Long,
    val lastSource: String,
    val isResolved: Boolean,
) {
    /** Mastery label shown next to the word. */
    val statusLabel: String get() = progress.status.label

    val isFavorite: Boolean get() = progress.isFavorite
}

/**
 * Current filter selection of the review centre. Immutable and used as the key for the
 * reactive query, so every change in the UI simply re-subscribes with a new filter.
 */
data class MistakeFilter(
    val category: MistakeCategory = MistakeCategory.RECENT,
    val search: String = "",
    val minWrongCount: Int = 0,
    val favoritesOnly: Boolean = false,
    val level: CetLevel = CetLevel.CET4,
) {
    /** Rows of a "minimum mistakes" filter: 0 = off, 3 = 3+ mistakes. */
    val hasActiveFilters: Boolean
        get() = search.isNotBlank() || minWrongCount > 0 || favoritesOnly
}

/** Sorts offered by the review centre list. */
enum class MistakeSort(val label: String) {
    RECENT("最近错误"),
    WRONG_COUNT("错误次数"),
}

/** Data needed by the review centre header (counts per category). */
data class MistakeOverview(
    val dueTodayCount: Int,
    val unconsolidatedCount: Int,
    val highWrongCount: Int,
    val recentCount: Int,
    val activeCount: Int,
    val historyCount: Int,
    val favoriteCount: Int,
) {
    companion object {
        val Empty = MistakeOverview(0, 0, 0, 0, 0, 0, 0)
    }
}

/** "最近 7 天" boundary used by the recent-mistakes category. */
fun recentWindowStart(today: LocalDate): LocalDate = today.minusDays(6)
