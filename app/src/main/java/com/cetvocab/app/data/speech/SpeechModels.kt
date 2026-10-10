package com.cetvocab.app.data.speech

/**
 * Speech models shared by every platform implementation.
 *
 * Nothing here depends on Android, so the same models can drive a Web (`window.speechSynthesis`)
 * or desktop implementation later without touching the UI layer.
 */

/**
 * How fast to read.
 *
 * [SYSTEM] deliberately means "whatever the user configured in the operating system" and is the
 * default: the app must not become a second, competing place to configure speech. The explicit
 * modes exist only because a learner may want slower audio for a hard word without changing a
 * device-wide setting.
 */
enum class SpeechRateMode(val id: String, val label: String, val factor: Float?) {
    SYSTEM("SYSTEM", "跟随系统", null),
    SLOW("SLOW", "慢速", 0.8f),
    NORMAL("NORMAL", "常速", 1.0f);

    companion object {
        fun fromId(id: String?): SpeechRateMode = entries.firstOrNull { it.id == id } ?: SYSTEM
    }
}

/** Which text is being read; used for logging/diagnostics and to label the button. */
enum class SpeechContent(val label: String) {
    WORD("单词"),
    EXAMPLE("例句"),
    MEANING("释义"),
}

/** A single reading request. */
data class SpeechRequest(
    val text: String,
    val localeTag: String,
    val content: SpeechContent,
    val rateMode: SpeechRateMode = SpeechRateMode.SYSTEM,
    val utteranceId: String = "cetvocab-${content.name.lowercase()}-${text.hashCode()}",
)

/**
 * Engine state.
 *
 * The failure cases are modelled as data rather than exceptions because every one of them has a
 * user-visible outcome: a message plus a route to fix it (see [SpeechStatus.recovery]).
 */
sealed interface SpeechStatus {
    /** The engine is still starting up; a request made now will be queued. */
    data object Initialising : SpeechStatus

    /** Ready to speak. [rateFromSystem] records whether the system rate/pitch could be read. */
    data class Ready(
        val engine: String?,
        val language: String?,
        val voice: String?,
        val rateFromSystem: Boolean,
        val pitchFromSystem: Boolean,
    ) : SpeechStatus

    /** The engine has no data for the requested language (the voice pack was never installed). */
    data object LanguageMissing : SpeechStatus

    /** This language is not supported by the current engine at all. */
    data object LanguageUnsupported : SpeechStatus

    /** No usable engine: none installed, disabled, or initialisation returned an error. */
    data class EngineUnavailable(val detail: String?) : SpeechStatus

    /** Anything unexpected; kept separate so the UI can still offer a retry. */
    data class Failed(val code: Int) : SpeechStatus

    /** Whether the learner can fix this themselves, and how. */
    val recovery: SpeechRecovery?
        get() = when (this) {
            is Initialising -> null
            is Ready -> null
            LanguageMissing -> SpeechRecovery.INSTALL_VOICE_DATA
            LanguageUnsupported -> SpeechRecovery.OPEN_SYSTEM_SETTINGS
            is EngineUnavailable -> SpeechRecovery.OPEN_SYSTEM_SETTINGS
            is Failed -> SpeechRecovery.RETRY
        }
}

/** What the UI should offer when speech is not working. */
enum class SpeechRecovery {
    /** Open the system text-to-speech settings so the user can pick/install an engine. */
    OPEN_SYSTEM_SETTINGS,

    /** Ask the platform to install the missing voice data for the language. */
    INSTALL_VOICE_DATA,

    /** Nothing to configure — the same request can simply be tried again. */
    RETRY,
}

/** Outcome of a single [SpeechService.speak] call. */
sealed interface SpeechResult {
    data object Spoken : SpeechResult
    data class Unavailable(val status: SpeechStatus) : SpeechResult
}

/** Defaults read from the operating system, all nullable because reading may fail. */
data class SystemSpeechDefaults(
    val rate: Float? = null,
    val pitch: Float? = null,
    val engine: String? = null,
    val localeTag: String? = null,
)

/**
 * Platform-agnostic text-to-speech.
 *
 * Contract:
 *  * [status] is always safe to observe and starts at [SpeechStatus.Initialising];
 *  * [speak] must **never throw** and never block; a request made while initialising is remembered
 *    and spoken once the engine is ready (the previous implementation silently dropped it, so the
 *    first tap appeared to do nothing);
 *  * [release] is idempotent.
 */
interface SpeechService {
    val status: kotlinx.coroutines.flow.StateFlow<SpeechStatus>

    /** Speaks [request], reporting progress through the callbacks. */
    fun speak(
        request: SpeechRequest,
        onStart: () -> Unit = {},
        onDone: () -> Unit = {},
        onError: (status: SpeechStatus) -> Unit = {},
    )

    fun stop()

    fun release()
}
