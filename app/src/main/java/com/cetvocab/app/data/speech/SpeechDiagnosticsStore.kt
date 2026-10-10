package com.cetvocab.app.data.speech

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * A snapshot of how reading is behaving **on this device**.
 *
 * This is deliberately tiny and entirely local: the product asked for a measurable TTS success rate,
 * and the app ships no analytics SDK, so the counters are kept in memory and shown to the learner in
 * 「设置 → 发音」. Nothing is uploaded, and there is no per-word or per-session history — just a
 * success/failure tally with a failure breakdown.
 */
data class SpeechDiagnostics(
    val spokenCount: Int = 0,
    val engineFailures: Int = 0,
    val languageMissingFailures: Int = 0,
    val languageUnsupportedFailures: Int = 0,
    val playbackFailures: Int = 0,
) {
    val attempts: Int get() = spokenCount + failures

    val failures: Int
        get() = engineFailures + languageMissingFailures + languageUnsupportedFailures + playbackFailures

    /**
     * Percentage of attempts that produced audio, or null when nothing was attempted.
     *
     * Null (not 0) because "never tried" and "always fails" are very different messages.
     */
    val successPercent: Int?
        get() = if (attempts == 0) null else (spokenCount * 100) / attempts

    val hasData: Boolean get() = attempts > 0

    /** The most useful single sentence describing the current state. */
    val summary: String
        get() = when {
            !hasData -> "还没有朗读记录。"
            failures == 0 -> "已成功朗读 $spokenCount 次，没有失败。"
            else -> "已成功朗读 $spokenCount 次，失败 $failures 次" + when {
                engineFailures > 0 -> "（其中 $engineFailures 次是语音引擎不可用）"
                languageMissingFailures > 0 -> "（其中 $languageMissingFailures 次缺少语音包）"
                languageUnsupportedFailures > 0 -> "（其中 $languageUnsupportedFailures 次不支持该语言）"
                else -> "（均为播放异常）"
            } + "。"
        }
}

/** Thread-safe sink for [SpeechDiagnostic] events. */
class SpeechDiagnosticsStore {

    private val _state = MutableStateFlow(SpeechDiagnostics())
    val state: StateFlow<SpeechDiagnostics> = _state.asStateFlow()

    fun record(diagnostic: SpeechDiagnostic) {
        _state.update { current ->
            when (diagnostic) {
                is SpeechDiagnostic.Spoken -> current.copy(spokenCount = current.spokenCount + 1)
                is SpeechDiagnostic.Failure -> when (diagnostic.kind) {
                    SpeechDiagnostic.FailureKind.ENGINE ->
                        current.copy(engineFailures = current.engineFailures + 1)

                    SpeechDiagnostic.FailureKind.LANGUAGE_MISSING ->
                        current.copy(languageMissingFailures = current.languageMissingFailures + 1)

                    SpeechDiagnostic.FailureKind.LANGUAGE_UNSUPPORTED ->
                        current.copy(
                            languageUnsupportedFailures = current.languageUnsupportedFailures + 1,
                        )

                    SpeechDiagnostic.FailureKind.PLAYBACK ->
                        current.copy(playbackFailures = current.playbackFailures + 1)
                }
            }
        }
    }

    fun reset() {
        _state.value = SpeechDiagnostics()
    }
}
