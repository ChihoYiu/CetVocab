package com.cetvocab.app.ui.speech

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cetvocab.app.data.speech.SpeechContent
import com.cetvocab.app.data.speech.SpeechRecovery
import com.cetvocab.app.data.speech.SpeechRequest
import com.cetvocab.app.data.speech.SpeechService
import com.cetvocab.app.data.speech.SpeechStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** The four states the speak button can be in. */
enum class SpeechPhase { IDLE, LOADING, PLAYING, FAILED }

/**
 * UI state of one speak button.
 *
 * [message] and [recovery] are only non-null in [SpeechPhase.FAILED]; the UI must always render a
 * message *and* a way out, never a dead button.
 */
@Immutable
data class SpeechUiState(
    val phase: SpeechPhase = SpeechPhase.IDLE,
    val content: SpeechContent? = null,
    val message: String? = null,
    val recovery: SpeechRecovery? = null,
) {
    val isLoading: Boolean get() = phase == SpeechPhase.LOADING
    val isPlaying: Boolean get() = phase == SpeechPhase.PLAYING
    val hasFailed: Boolean get() = phase == SpeechPhase.FAILED
}

/**
 * Turns [SpeechService] into a single UI state.
 *
 * Guarantees that matter for the product requirements:
 *  * **a request always ends in a visible state** — success returns to IDLE, and every failure path
 *    produces FAILED with a readable reason plus a recovery action;
 *  * **a stuck engine cannot spin forever** — readiness is awaited with a timeout
 *    ([timeoutMillis]), after which the learner is told to check the system settings;
 *  * **double taps cannot overlap** — a new request cancels the previous one and stops playback.
 */
class SpeechController(
    private val service: SpeechService,
    private val scope: CoroutineScope,
    private val timeoutMillis: Long = INIT_TIMEOUT_MILLIS,
) {

    private val _state = MutableStateFlow(SpeechUiState())
    val state: StateFlow<SpeechUiState> = _state.asStateFlow()

    private var job: Job? = null
    private var lastRequest: SpeechRequest? = null

    fun speak(request: SpeechRequest) {
        if (request.text.isBlank()) return
        job?.cancel()
        service.stop()
        lastRequest = request
        _state.value = SpeechUiState(phase = SpeechPhase.LOADING, content = request.content)

        job = scope.launch {
            when (val status = awaitUsable()) {
                null -> fail(MESSAGE_TIMEOUT, SpeechRecovery.OPEN_SYSTEM_SETTINGS)
                is SpeechStatus.Ready -> {
                    service.speak(
                        request = request,
                        onStart = {
                            _state.value = SpeechUiState(phase = SpeechPhase.PLAYING, content = request.content)
                        },
                        onDone = {
                            _state.value = SpeechUiState(phase = SpeechPhase.IDLE, content = request.content)
                        },
                        onError = { failure -> failFor(failure) },
                    )
                }

                else -> failFor(status)
            }
        }
    }

    /** Re-runs the last request; used by the "重试" action in the failed state. */
    fun retry() {
        lastRequest?.let { request ->
            // Clear the failed state before re-queuing so the button shows Loading again.
            _state.value = SpeechUiState(phase = SpeechPhase.LOADING, content = request.content)
            speak(request)
        }
    }

    fun stop() {
        job?.cancel()
        service.stop()
        _state.value = SpeechUiState(phase = SpeechPhase.IDLE, content = _state.value.content)
    }

    fun dismissError() {
        _state.value = SpeechUiState(phase = SpeechPhase.IDLE, content = _state.value.content)
    }

    /**
     * Waits for the engine to become usable.
     *
     * Returns null on timeout, the current non-initialising status when it is not usable, or
     * [SpeechStatus.Ready].
     */
    private suspend fun awaitUsable(): SpeechStatus? {
        val current = service.status.value
        if (current !is SpeechStatus.Initialising) return current
        return withTimeoutOrNull(timeoutMillis) {
            service.status.first { it !is SpeechStatus.Initialising }
        }
    }

    private fun failFor(status: SpeechStatus) {
        fail(messageFor(status), status.recovery ?: SpeechRecovery.RETRY)
    }

    private fun fail(message: String, recovery: SpeechRecovery) {
        _state.value = SpeechUiState(
            phase = SpeechPhase.FAILED,
            content = _state.value.content,
            message = message,
            recovery = recovery,
        )
    }

    private fun messageFor(status: SpeechStatus): String = when (status) {
        is SpeechStatus.EngineUnavailable -> "系统语音服务不可用，可能是引擎未安装或被停用。"
        SpeechStatus.LanguageMissing -> "缺少英语语音包，安装后即可朗读。"
        SpeechStatus.LanguageUnsupported -> "当前语音引擎不支持该语言，可以更换引擎后再试。"
        is SpeechStatus.Failed -> "朗读失败，请重试。"
        SpeechStatus.Initialising -> MESSAGE_TIMEOUT
        is SpeechStatus.Ready -> ""
    }

    companion object {
        /** How long to wait for the engine before telling the learner something is wrong. */
        const val INIT_TIMEOUT_MILLIS = 3_000L

        internal const val MESSAGE_TIMEOUT = "系统语音服务没有响应，请检查系统的文字转语音设置。"
    }
}

/**
 * Creates a controller bound to the composition.
 *
 * The service is owned by `AppContainer` (it is expensive to create and outlives a screen), so only
 * the coroutine scope is tied to the composition — leaving the screen cancels an in-flight request
 * but never shuts down the engine behind other screens' backs.
 */
@Composable
fun rememberSpeechController(service: SpeechService): SpeechController {
    val scope = rememberCoroutineScope()
    val currentService by rememberUpdatedState(service)
    return androidx.compose.runtime.remember(currentService, scope) {
        SpeechController(currentService, scope)
    }
}

/** Convenience for screens. */
@Composable
fun SpeechController.uiState(): SpeechUiState {
    val state by state.collectAsStateWithLifecycle()
    return state
}
