package com.cetvocab.app.data.repository

import androidx.room.withTransaction
import com.cetvocab.app.core.time.TimeProvider
import com.cetvocab.app.data.local.CetVocabDatabase
import com.cetvocab.app.data.local.dao.WrongAnswerRow
import com.cetvocab.app.data.local.dao.WordWithProgressView
import com.cetvocab.app.data.mapper.newProgressFor
import com.cetvocab.app.data.mapper.toEntity
import com.cetvocab.app.data.mapper.toProgress
import com.cetvocab.app.data.mapper.toWord
import com.cetvocab.app.data.mapper.toWordWithProgress
import com.cetvocab.app.domain.mistake.MistakeCategory
import com.cetvocab.app.domain.mistake.MistakeFilter
import com.cetvocab.app.domain.mistake.MistakeItem
import com.cetvocab.app.domain.mistake.MistakeOverview
import com.cetvocab.app.domain.mistake.MistakeSort
import com.cetvocab.app.domain.model.CetLevel
import com.cetvocab.app.domain.model.WordWithProgress
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

/**
 * The review centre (复习中心) and the wrong-answer book (错题本).
 *
 * Every category is a database query, not an in-memory filter, and every destructive action
 * (deleting a record) is exposed as an explicit method that the UI must confirm first.
 */
interface MistakeRepository {

    fun observeOverview(level: CetLevel, nowMillis: Long = 0L): Flow<MistakeOverview>

    /** Reactive list for the current category + filter + sort. */
    fun observeItems(filter: MistakeFilter, sort: MistakeSort): Flow<List<MistakeItem>>

    /** Word ids of the current selection, used by "批量重新练习". */
    suspend fun drillIds(filter: MistakeFilter, sort: MistakeSort): List<Long>

    suspend fun setFavorite(wordId: Long, favorite: Boolean)

    /** Marks words as re-consolidated (记住「已巩固」). */
    suspend fun markResolved(wordIds: List<Long>)

    suspend fun markUnresolved(wordId: Long)

    /** Permanently removes mistake records. The UI asks for confirmation beforehand. */
    suspend fun deleteRecords(wordIds: List<Long>)

    suspend fun detail(wordId: Long): WordWithProgress?
}

class DefaultMistakeRepository(
    private val db: CetVocabDatabase,
    private val time: TimeProvider,
) : MistakeRepository {

    override fun observeOverview(level: CetLevel, nowMillis: Long): Flow<MistakeOverview> {
        val now = if (nowMillis > 0) nowMillis else time.nowMillis()
        val weekAgo = now - 7L * 24L * 60L * 60L * 1000L
        return combine(
            db.progressDao().observeDueCount(level.id, now),
            db.progressDao().observeFragileCount(level.id),
            db.mistakeBookDao().observeHighWrongCount(level.id, HIGH_WRONG_THRESHOLD),
            db.mistakeBookDao().observeRecentlyWrong(level.id, weekAgo),
            db.mistakeBookDao().observeActiveCount(level.id),
        ) { due, fragile, high, recent, active ->
            MistakeOverview(
                dueTodayCount = due,
                unconsolidatedCount = fragile,
                highWrongCount = high,
                recentCount = recent,
                activeCount = active,
                historyCount = active,
                favoriteCount = 0,
            )
        }.combine(db.progressDao().observeFavoriteCount(level.id)) { overview, favorites ->
            overview.copy(favoriteCount = favorites)
        }.combine(db.mistakeBookDao().observeTotalCount(level.id)) { overview, total ->
            overview.copy(historyCount = total)
        }
    }

    override fun observeItems(filter: MistakeFilter, sort: MistakeSort): Flow<List<MistakeItem>> {
        val level = filter.level.id
        val like = likePattern(filter.search)
        return when (filter.category) {
            MistakeCategory.DUE_TODAY -> db.progressDao()
                .observeDueWords(level, time.nowMillis(), LIST_LIMIT)
                .map { rows -> rows.map { it.toDueMistakeItem() } }

            MistakeCategory.UNCONSOLIDATED -> db.progressDao()
                .observeFragileWords(level, LIST_LIMIT)
                .map { rows -> rows.map { it.toDueMistakeItem() } }

            MistakeCategory.FAVORITES -> db.progressDao()
                .observeFavorites(level)
                .map { rows -> rows.map { it.toDueMistakeItem() } }

            MistakeCategory.HIGH_WRONG -> db.mistakeBookDao().observeMistakesByCount(
                level = level,
                includeResolved = 0,
                favoriteOnly = if (filter.favoritesOnly) 1 else 0,
                minWrongCount = filter.minWrongCount,
                sinceMillis = 0L,
                search = filter.search,
                like = like,
            ).map { rows -> rows.map { it.toMistakeItem() } }

            MistakeCategory.RECENT -> db.mistakeBookDao().observeMistakes(
                level = level,
                includeResolved = 0,
                favoriteOnly = if (filter.favoritesOnly) 1 else 0,
                minWrongCount = filter.minWrongCount,
                sinceMillis = time.nowMillis() - RECENT_WINDOW_MILLIS,
                search = filter.search,
                like = like,
            ).map { rows -> rows.map { it.toMistakeItem() } }

            MistakeCategory.HISTORY -> db.mistakeBookDao().observeMistakes(
                level = level,
                includeResolved = 1,
                favoriteOnly = if (filter.favoritesOnly) 1 else 0,
                minWrongCount = filter.minWrongCount,
                sinceMillis = 0L,
                search = filter.search,
                like = like,
            ).map { rows -> rows.map { it.toMistakeItem() } }
        }.let { flow ->
            if (sort == MistakeSort.RECENT || filter.category in orderedCategories) {
                flow
            } else {
                flow.map { items -> items.sortedByDescending { it.wrongCount } }
            }
        }
    }

    override suspend fun drillIds(filter: MistakeFilter, sort: MistakeSort): List<Long> {
        val level = filter.level.id
        return when (filter.category) {
            MistakeCategory.DUE_TODAY -> db.progressDao()
                .dueWords(level, time.nowMillis(), LIST_LIMIT).map { it.wordId }

            MistakeCategory.UNCONSOLIDATED -> db.progressDao()
                .fragileWords(level, time.nowMillis(), LIST_LIMIT).map { it.wordId }

            MistakeCategory.FAVORITES -> db.progressDao().favorites(level).map { it.wordId }

            MistakeCategory.HIGH_WRONG -> db.mistakeBookDao().mistakeIdsForFilter(
                level = level,
                includeResolved = 0,
                favoriteOnly = if (filter.favoritesOnly) 1 else 0,
                minWrongCount = filter.minWrongCount,
                sinceMillis = 0L,
                search = filter.search,
                like = likePattern(filter.search),
            )

            MistakeCategory.RECENT -> db.mistakeBookDao().mistakeIdsForFilter(
                level = level,
                includeResolved = 0,
                favoriteOnly = if (filter.favoritesOnly) 1 else 0,
                minWrongCount = filter.minWrongCount,
                sinceMillis = time.nowMillis() - RECENT_WINDOW_MILLIS,
                search = filter.search,
                like = likePattern(filter.search),
            )

            MistakeCategory.HISTORY -> db.mistakeBookDao().mistakeIdsForFilter(
                level = level,
                includeResolved = 1,
                favoriteOnly = if (filter.favoritesOnly) 1 else 0,
                minWrongCount = filter.minWrongCount,
                sinceMillis = 0L,
                search = filter.search,
                like = likePattern(filter.search),
            )
        }.let { ids ->
            if (sort == MistakeSort.RECENT) ids else db.progressDao().wordsByIds(ids)
                .sortedByDescending { it.wrongCount ?: 0 }
                .map { it.wordId }
        }
    }

    override suspend fun setFavorite(wordId: Long, favorite: Boolean) {
        val now = time.nowMillis()
        db.withTransaction {
            // A favourite must survive even before the word has any progress row, so one is
            // created when necessary.
            if (db.progressDao().byWordId(wordId) == null) {
                db.progressDao().upsert(newProgressFor(wordId, now).toEntity(now))
            }
            db.progressDao().setFavorite(wordId, favorite, now)
            if (db.mistakeBookDao().byWordId(wordId) != null) {
                db.mistakeBookDao().setFavorite(wordId, favorite, now)
            }
        }
    }

    override suspend fun markResolved(wordIds: List<Long>) {
        if (wordIds.isEmpty()) return
        db.mistakeBookDao().markResolvedAll(wordIds, time.nowMillis())
    }

    override suspend fun markUnresolved(wordId: Long) {
        db.mistakeBookDao().markUnresolved(wordId, time.nowMillis())
    }

    override suspend fun deleteRecords(wordIds: List<Long>) {
        if (wordIds.isEmpty()) return
        db.mistakeBookDao().deleteAll(wordIds)
    }

    override suspend fun detail(wordId: Long): WordWithProgress? =
        db.progressDao().wordWithProgress(wordId)?.toWordWithProgress()

    // --- Mapping helpers ------------------------------------------------------------------

    private fun WordWithProgressView.toDueMistakeItem(): MistakeItem {
        val progress = toProgress()
        return MistakeItem(
            word = toWord(),
            progress = progress,
            wrongCount = progress.wrongCount,
            fuzzyCount = progress.fuzzyCount,
            firstWrongAt = progress.introducedAt ?: 0L,
            lastWrongAt = progress.lastReviewedAt ?: 0L,
            lastSource = "",
            isResolved = false,
        )
    }

    private fun WrongAnswerRow.toMistakeItem(): MistakeItem = MistakeItem(
        word = toWord(),
        progress = toProgress(),
        wrongCount = wrongCount,
        fuzzyCount = fuzzyCount,
        firstWrongAt = firstWrongAt,
        lastWrongAt = lastWrongAt,
        lastSource = lastSource,
        isResolved = resolvedAt != null,
    )

    companion object {
        const val HIGH_WRONG_THRESHOLD = 3
        const val LIST_LIMIT = 500
        private const val RECENT_WINDOW_MILLIS = 7L * 24L * 60L * 60L * 1000L

        /** Categories whose SQL already orders rows the way the UI wants them. */
        private val orderedCategories = setOf(
            MistakeCategory.HIGH_WRONG,
            MistakeCategory.RECENT,
            MistakeCategory.HISTORY,
        )

        /**
         * Escapes a user search term for a `LIKE :like ESCAPE '!'` pattern, so `%` and `_`
         * are matched literally instead of acting as wildcards.
         */
        fun likePattern(search: String): String {
            val trimmed = search.trim()
            if (trimmed.isEmpty()) return "%"
            val escaped = trimmed
                .replace("!", "!!")
                .replace("%", "!%")
                .replace("_", "!_")
            return "%$escaped%"
        }
    }
}
