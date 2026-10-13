package com.cetvocab.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.cetvocab.app.AppContainer
import com.cetvocab.app.ui.components.LoadingState
import com.cetvocab.app.ui.navigation.CetVocabNavHost
import com.cetvocab.app.ui.screens.StartupTipScreen
import com.cetvocab.app.ui.theme.AppTheme
import com.cetvocab.app.ui.theme.CetVocabTheme

/**
 * Root composable: applies the user's theme preferences, the cold-start line, then the navigation
 * graph.
 *
 * The theme is driven by DataStore, so changing "浅色 / 深色 / 跟随系统", the dynamic-colour
 * switch or the animation switch in settings re-composes the whole app immediately — including
 * the window background of the empty first frame, which is why the XML theme uses the same
 * warm-white value.
 *
 * The start-up line is an **overlay owned by [MainViewModel]**, not a navigation destination:
 * * it appears once per process start, so switching tabs can never bring it back;
 * * the navigation graph is composed underneath it as soon as the vocabulary is ready, so the
 *   first home frame is already drawn while the line fades out — the encouragement costs no
 *   extra launch time;
 * * while the vocabulary is still being written (first launch, or a pack upgrade), the same
 *   screen shows the real seeding progress it always showed.
 */
@Composable
fun CetVocabApp(
    container: AppContainer,
    state: MainUiState,
    onDismissStartupTip: () -> Unit = {},
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
            Box(modifier = Modifier.fillMaxSize()) {
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

                AnimatedVisibility(
                    visible = state.startupTipVisible,
                    enter = fadeIn(tween(AppTheme.motion.durationMillis(STARTUP_TIP_FADE_MILLIS))),
                    exit = fadeOut(tween(AppTheme.motion.durationMillis(STARTUP_TIP_FADE_MILLIS))),
                ) {
                    StartupTipScreen(
                        tip = state.startupTip,
                        seedProgress = state.seedProgress,
                        onContinue = onDismissStartupTip,
                    )
                }
            }
        }
    }
}

/**
 * Fade of the start-up line.
 *
 * Short on purpose: the line sits on top of a screen that is already drawn, so the transition only
 * has to look deliberate, not to cover any loading. The in-app animation switch collapses it to
 * zero.
 */
private const val STARTUP_TIP_FADE_MILLIS = 220
