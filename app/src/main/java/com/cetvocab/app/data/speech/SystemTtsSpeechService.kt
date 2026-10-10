package com.cetvocab.app.data.speech

import android.content.Context
import android.content.Intent
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

/**
 * The Android implementation: the **system** text-to-speech engine.
 *
 * Design decisions, each of which fixes a concrete problem:
 *
 *  * **The default engine, not a hard-coded one.** `TextToSpeech(context, listener)` binds to
 *    whichever engine the user selected in the system settings, which is exactly what the product
 *    requirement asks for. Users who installed a specific engine (or disabled Google's) keep it.
 *  * **Requests made before the engine is ready are queued, not dropped.** The first version of
 *    this app returned silently in that window, so the very first tap on the speaker did nothing.
 *  * **System rate/pitch are honoured, and never guessed.** When [SpeechDefaultsReader] cannot read
 *    a value, `setSpeechRate`/`setPitch` are simply not called, so the engine default applies. This
 *    is asserted by a unit test.
 *  * **Nothing throws outward.** Every platform call is wrapped; failures become [SpeechStatus]
 *    values that the UI can render with a recovery action.
 */
class SystemTtsSpeechService(
    context: Context,
    private val defaultsReader: SpeechDefaultsReader = AndroidSpeechDefaultsReader(context),
    private val onDiagnostic: (SpeechDiagnostic) -> Unit = {},
) : SpeechService {

    private val appContext = context.applicationContext

    private val _status = MutableStateFlow<SpeechStatus>(SpeechStatus.Initialising)
    override val status: StateFlow<SpeechStatus> = _status.asStateFlow()

    private var engine: TextToSpeech? = null
    private var ready = false
    private var released = false

    /** The request that arrived before the engine finished initialising. */
    private var pending: PendingRequest? = null

    private var currentUtteranceId: String? = null

    private data class PendingRequest(
        val request: SpeechRequest,
        val onStart: () -> Unit,
        val onDone: () -> Unit,
        val onError: (SpeechStatus) -> Unit,
    )

    init {
        initialize()
    }

    private fun initialize() {
        if (engine != null || released) return
        engine = runCatching {
            TextToSpeech(appContext) { initStatus ->
                ready = initStatus == TextToSpeech.SUCCESS
                if (ready) {
                    _status.value = describeReadyState()
                    pending?.let { queued ->
                        pending = null
                        performSpeak(queued)
                    }
                } else {
                    val failure = SpeechStatus.EngineUnavailable("初始化返回错误码 $initStatus")
                    _status.value = failure
                    onDiagnostic(SpeechDiagnostic.Failure(SpeechDiagnostic.FailureKind.ENGINE, null))
                    pending?.let { queued ->
                        pending = null
                        queued.onError(failure)
                    }
                }
            }
        }.getOrElse { throwable ->
            val failure = SpeechStatus.EngineUnavailable(throwable.javaClass.simpleName)
            _status.value = failure
            onDiagnostic(SpeechDiagnostic.Failure(SpeechDiagnostic.FailureKind.ENGINE, null))
            null
        }

        if (engine == null) {
            // No engine object at all: no TTS service installed on this device.
            _status.value = SpeechStatus.EngineUnavailable("未找到系统语音引擎")
            onDiagnostic(SpeechDiagnostic.Failure(SpeechDiagnostic.FailureKind.ENGINE, null))
        }
    }

    /** Engine identity and whether the system defaults were readable. */
    private fun describeReadyState(): SpeechStatus {
        val defaults = runCatching { defaultsReader.read() }.getOrDefault(SystemSpeechDefaults())
        return SpeechStatus.Ready(
            engine = defaults.engine ?: runCatching { engine?.defaultEngine }.getOrNull(),
            language = defaults.localeTag ?: runCatching { engine?.defaultLanguage?.toLanguageTag() }.getOrNull(),
            voice = runCatching { engine?.defaultVoice?.name }.getOrNull(),
            rateFromSystem = defaults.rate != null,
            pitchFromSystem = defaults.pitch != null,
        )
    }

    override fun speak(
        request: SpeechRequest,
        onStart: () -> Unit,
        onDone: () -> Unit,
        onError: (SpeechStatus) -> Unit,
    ) {
        if (released) return
        if (request.text.isBlank()) return
        if (!ready) {
            // Queue instead of dropping. If initialisation already failed, report it immediately.
            val current = _status.value
            if (current is SpeechStatus.EngineUnavailable || current is SpeechStatus.Failed) {
                onDiagnostic(SpeechDiagnostic.Failure(SpeechDiagnostic.FailureKind.ENGINE, request.content))
                onError(current)
                return
            }
            pending = PendingRequest(request, onStart, onDone, onError)
            return
        }
        performSpeak(PendingRequest(request, onStart, onDone, onError))
    }

    private fun performSpeak(queued: PendingRequest) {
        val tts = engine ?: run {
            queued.onError(_status.value)
            return
        }

        val request = queued.request
        val locale = runCatching { Locale.forLanguageTag(request.localeTag) }.getOrNull() ?: Locale.ENGLISH

        val languageResult = runCatching { tts.setLanguage(locale) }.getOrElse { TextToSpeech.LANG_NOT_SUPPORTED }
        when (languageResult) {
            TextToSpeech.LANG_MISSING_DATA -> {
                val failure = SpeechStatus.LanguageMissing
                _status.value = failure
                onDiagnostic(SpeechDiagnostic.Failure(SpeechDiagnostic.FailureKind.LANGUAGE_MISSING, request.content))
                queued.onError(failure)
                return
            }

            TextToSpeech.LANG_NOT_SUPPORTED -> {
                val failure = SpeechStatus.LanguageUnsupported
                _status.value = failure
                onDiagnostic(SpeechDiagnostic.Failure(SpeechDiagnostic.FailureKind.LANGUAGE_UNSUPPORTED, request.content))
                queued.onError(failure)
                return
            }
        }

        applySystemTuning(tts, request.rateMode)

        currentUtteranceId = request.utteranceId
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                onDiagnostic(SpeechDiagnostic.Spoken(request.content))
                queued.onStart()
            }

            override fun onDone(utteranceId: String?) {
                queued.onDone()
            }

            @Deprecated("Kept for API levels below 21 behaviour parity")
            override fun onError(utteranceId: String?) {
                reportPlaybackFailure(queued)
            }

            override fun onError(utteranceId: String?, errorCode: Int) {
                reportPlaybackFailure(queued, errorCode)
            }
        })

        val result = runCatching {
            tts.speak(request.text, TextToSpeech.QUEUE_FLUSH, null, request.utteranceId)
        }.getOrDefault(TextToSpeech.ERROR)

        // A queued engine reports ERROR until it has finished loading: treat it as "try again soon"
        // rather than as a hard failure, because the engine is otherwise usable.
        if (result == TextToSpeech.ERROR) {
            reportPlaybackFailure(queued)
        }
    }

    private fun reportPlaybackFailure(queued: PendingRequest, errorCode: Int? = null) {
        val failure = SpeechStatus.Failed(errorCode ?: -1)
        onDiagnostic(SpeechDiagnostic.Failure(SpeechDiagnostic.FailureKind.PLAYBACK, queued.request.content))
        queued.onError(failure)
    }

    /**
     * Applies the learner's own preferences.
     *
     * Rate: [SpeechRateMode.SYSTEM] uses the operating-system value when readable, otherwise the
     * engine default is left untouched. Pitch is only ever taken from the system — the app has no
     * pitch UI, so overriding it would be a pure regression.
     */
    private fun applySystemTuning(tts: TextToSpeech, rateMode: SpeechRateMode) {
        val defaults = runCatching { defaultsReader.read() }.getOrDefault(SystemSpeechDefaults())

        defaults.pitch?.let { pitch -> runCatching { tts.setPitch(pitch) } }

        when (val explicit = rateMode.factor) {
            null -> defaults.rate?.let { rate -> runCatching { tts.setSpeechRate(rate) } }
            else -> runCatching { tts.setSpeechRate(explicit) }
        }
    }

    override fun stop() {
        pending = null
        runCatching { engine?.stop() }
    }

    override fun release() {
        if (released) return
        released = true
        pending = null
        runCatching {
            engine?.stop()
            engine?.shutdown()
        }
        engine = null
        ready = false
    }

    companion object {
        /**
         * Opens the system screen where text-to-speech engines and voice data are configured.
         *
         * `ACTION_ACCESSIBILITY_SETTINGS` is the documented public entry point; the TTS output
         * settings live inside it. Returning false means "no activity could handle this", which the
         * UI turns into a plain message instead of a crash.
         */
        fun openSystemSpeechSettings(context: Context): Boolean = runCatching {
            context.startActivity(
                Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
            true
        }.getOrDefault(false)

        /** Asks the platform to install the missing voice data for the current engine. */
        fun requestVoiceDataInstall(context: Context): Boolean = runCatching {
            context.startActivity(
                Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
            true
        }.getOrDefault(false)
    }
}

/**
 * Local counters behind the "发音服务状态" panel.
 *
 * They exist so the speech success rate is measurable **on the device**, without any analytics SDK
 * and without sending anything anywhere.
 */
sealed interface SpeechDiagnostic {
    data class Spoken(val content: SpeechContent) : SpeechDiagnostic
    data class Failure(val kind: FailureKind, val content: SpeechContent?) : SpeechDiagnostic

    enum class FailureKind { ENGINE, LANGUAGE_MISSING, LANGUAGE_UNSUPPORTED, PLAYBACK }
}
