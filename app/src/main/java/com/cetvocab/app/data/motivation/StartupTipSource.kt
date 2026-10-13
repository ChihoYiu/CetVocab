package com.cetvocab.app.data.motivation

import android.content.Context
import com.cetvocab.app.domain.motivation.StartupTipCatalogue
import com.cetvocab.app.domain.motivation.StartupTipParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Where the start-up lines come from.
 *
 * An interface so the start-up screen can be tested with a deterministic catalogue instead of the
 * shipped asset (the same split the word packs use).
 */
interface StartupTipSource {
    suspend fun load(): StartupTipCatalogue
}

/**
 * Reads `assets/motivation/tips.json`.
 *
 * The file is a few tens of kilobytes, but it is still read off the main thread: the start-up
 * screen must never miss a frame because a JSON document was parsed inside `onCreate`. Any failure
 * (missing asset, broken JSON, empty catalogue) degrades to the built-in fallback line — the
 * feature is a nicety, so it can never be the reason the app fails to open.
 */
class AssetStartupTipSource(private val context: Context) : StartupTipSource {

    override suspend fun load(): StartupTipCatalogue = withContext(Dispatchers.IO) {
        val raw = runCatching {
            context.assets.open(ASSET_PATH).use { stream ->
                stream.bufferedReader(Charsets.UTF_8).readText()
            }
        }.getOrNull()

        val catalogue = raw?.let { StartupTipParser.parse(it) }
        if (catalogue == null || catalogue.tips.isEmpty()) FALLBACK_CATALOGUE else catalogue
    }

    companion object {
        const val ASSET_PATH = "motivation/tips.json"

        private val FALLBACK_CATALOGUE = StartupTipCatalogue(
            version = 0,
            licence = "本项目自撰（CC0-1.0）",
            tips = listOf(StartupTipCatalogue.FALLBACK),
        )
    }
}
