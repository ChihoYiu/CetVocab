package com.cetvocab.app.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.cetvocab.app.AppContainer
import com.cetvocab.app.ui.components.LoadingState
import com.cetvocab.app.ui.navigation.CetVocabNavHost
import com.cetvocab.app.ui.theme.CetVocabTheme

/**
 * Root composable: applies the user's theme preferences and then the navigation graph.
 *
 * The theme is driven by DataStore, so changing "浅色 / 深色 / 跟随系统", the dynamic-colour
 * switch or the animation switch in settings re-composes the whole app immediately — including
 * the window background of the empty first frame, which is why the XML theme uses the same
 * warm-white value.
 */
@Composable
fun CetVocabApp(
    container: AppContainer,
    state: MainUiState,
) {
    val settings = state.settings

    CetVocabTheme(
        themeMode = settings.themeMode,
        dynamicColor = settings.dynamicColor,
        animations = settings.animationsEnabled,
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            if (state.ready) {
                CetVocabNavHost(
                    container = container,
                    onboardingCompleted = settings.onboardingCompleted,
                )
            } else {
                // Cold start: the built-in CET-4/CET-6 packs are being written to the database.
                // The screen reports real progress instead of an indefinite spinner, because the
                // complete lists are thousands of rows on a first launch.
                LoadingState(text = state.seedProgress?.let { progress ->
                    "正在准备词库 ${progress.packName}（${progress.processed} / ${progress.total}）"
                } ?: "正在准备词库…")
            }
        }
    }
}
