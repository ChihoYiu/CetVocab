package com.cetvocab.app.domain.motivation

import com.cetvocab.app.core.json.JsonCodec
import kotlinx.serialization.Serializable
import kotlin.random.Random

/**
 * One encouragement line shown while the app starts ("开屏提示语").
 *
 * This is deliberately a different thing from [MotivationMessage]: the lines here depend on
 * nothing the learner did — they are read once, on a cold start, before any screen exists, and
 * they carry a stable [id] so the app can guarantee that two consecutive cold starts do not show
 * the same line. The catalogue ships inside the APK (`assets/motivation/tips.json`), so it works
 * with no network, and the whole set is validated by `StartupTipCatalogueTest` and
 * `tools/motivation/validate_tips.py`.
 */
data class StartupTip(
    val id: String,
    val category: String,
    val text: String,
)

/** The parsed asset plus the facts needed to describe and audit it. */
data class StartupTipCatalogue(
    val version: Int = 1,
    /** Licence of the text itself; the shipped lines are written for this project (CC0-1.0). */
    val licence: String = "",
    val tips: List<StartupTip> = emptyList(),
) {
    companion object {
        /**
         * Last-resort line.
         *
         * Used when the asset is missing, unreadable or empty — the start-up screen must never be
         * blank, and an encouragement feature must never be the reason the app fails to open.
         */
        val FALLBACK = StartupTip(
            id = "fallback",
            category = "开始",
            text = "今天也从一个单词开始。",
        )
    }
}

/**
 * Parses `assets/motivation/tips.json`.
 *
 * Lenient by design, like every other file this app reads: unknown keys are ignored, blank or
 * duplicated lines are dropped instead of failing the whole catalogue, and a malformed document
 * returns `null` so the caller can degrade to [StartupTipCatalogue.FALLBACK]. Nothing here touches
 * Android APIs, so the validation runs as a plain unit test.
 */
object StartupTipParser {

    @Serializable
    private data class CatalogueDto(
        val version: Int = 1,
        val licence: String = "",
        val tips: List<TipDto> = emptyList(),
    )

    @Serializable
    private data class TipDto(
        val id: String? = null,
        val category: String? = null,
        val text: String? = null,
    )

    fun parse(raw: String): StartupTipCatalogue? {
        if (raw.isBlank()) return null
        val dto = runCatching { JsonCodec.json.decodeFromString<CatalogueDto>(raw) }.getOrNull()
            ?: return null

        val seen = HashSet<String>()
        val tips = ArrayList<StartupTip>(dto.tips.size)
        dto.tips.forEachIndexed { index, entry ->
            val text = entry.text.orEmpty().trim()
            if (text.isEmpty()) return@forEachIndexed
            // The same line twice would break "two cold starts in a row never repeat" in a subtle
            // way (picking the twin looks like a repeat), so de-duplication happens at load time.
            if (!seen.add(text)) return@forEachIndexed
            tips += StartupTip(
                id = entry.id?.trim().orEmpty().ifEmpty { "tip-${index + 1}" },
                category = entry.category?.trim().orEmpty().ifEmpty { "未分类" },
                text = text,
            )
        }
        return StartupTipCatalogue(version = dto.version, licence = dto.licence, tips = tips)
    }
}

/**
 * Chooses which line to show, avoiding the one shown on the previous cold start.
 *
 * [random] is a parameter rather than a global so the behaviour is reproducible in tests. When the
 * only line available is the avoided one it is returned anyway — "avoid a repeat" must never turn
 * into "show nothing".
 */
object StartupTipPicker {

    fun pick(tips: List<StartupTip>, avoidId: String?, random: Random = Random.Default): StartupTip? {
        if (tips.isEmpty()) return null
        if (tips.size == 1) return tips.first()
        val pool = tips.filter { it.id != avoidId }.ifEmpty { tips }
        return pool[random.nextInt(pool.size)]
    }
}
