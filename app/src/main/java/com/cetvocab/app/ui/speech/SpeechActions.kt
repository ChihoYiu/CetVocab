package com.cetvocab.app.ui.speech

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.cetvocab.app.data.speech.SpeechContent
import com.cetvocab.app.data.speech.SpeechRateMode
import com.cetvocab.app.data.speech.SpeechRequest
import com.cetvocab.app.data.speech.SystemTtsSpeechService
import com.cetvocab.app.domain.model.Word

/**
 * The language the app reads English content in.
 *
 * `"en"` rather than `"en-US"`: the accent is chosen by the operating system (that is the whole
 * point of delegating speech to it), so requesting a specific regional variant here would silently
 * override the learner's own engine/voice preference.
 */
const val SPEECH_LOCALE_ENGLISH = "en"

/** Chinese for the meaning read-aloud; the engine picks the voice. */
const val SPEECH_LOCALE_CHINESE = "zh-CN"

/** Reading request for the headword. */
fun Word.asSpeechRequest(rateMode: SpeechRateMode): SpeechRequest = SpeechRequest(
    text = word,
    localeTag = SPEECH_LOCALE_ENGLISH,
    content = SpeechContent.WORD,
    rateMode = rateMode,
    utteranceId = "word-$id",
)

/** Reading request for the example sentence; only meaningful when one exists. */
fun Word.exampleAsSpeechRequest(rateMode: SpeechRateMode): SpeechRequest = SpeechRequest(
    text = exampleSentence,
    localeTag = SPEECH_LOCALE_ENGLISH,
    content = SpeechContent.EXAMPLE,
    rateMode = rateMode,
    utteranceId = "example-$id",
)

/**
 * Reading request for the meaning.
 *
 * The meaning list can hold several senses; they are joined with a pause so a multi-sense word does
 * not read as one run-on sentence.
 */
fun Word.meaningAsSpeechRequest(rateMode: SpeechRateMode): SpeechRequest = SpeechRequest(
    text = meanings.joinToString("，"),
    localeTag = SPEECH_LOCALE_CHINESE,
    content = SpeechContent.MEANING,
    rateMode = rateMode,
    utteranceId = "meaning-$id",
)

/**
 * One place that turns a [Word] into a reading request and routes the failure actions.
 *
 * Screens hold one of these instead of wiring three callbacks each; that is what keeps the failure
 * behaviour (settings entry, voice-data install, retry) identical everywhere.
 */
class SpeechActions(
    private val controller: SpeechController,
    private val context: Context,
    private val rateMode: () -> SpeechRateMode,
) {
    val state: kotlinx.coroutines.flow.StateFlow<SpeechUiState> get() = controller.state

    fun speakWord(word: Word) = controller.speak(word.asSpeechRequest(rateMode()))

    fun speakExample(word: Word) {
        if (word.exampleSentence.isBlank()) return
        controller.speak(word.exampleAsSpeechRequest(rateMode()))
    }

    fun speakMeaning(word: Word) {
        if (word.meanings.isEmpty()) return
        controller.speak(word.meaningAsSpeechRequest(rateMode()))
    }

    fun retry() = controller.retry()

    /** Reads a fixed bilingual sample so the learner can check their engine before studying. */
    fun speakSample() = controller.speak(
        SpeechRequest(
            text = "The quick brown fox jumps over the lazy dog.",
            localeTag = SPEECH_LOCALE_ENGLISH,
            content = SpeechContent.EXAMPLE,
            rateMode = rateMode(),
            utteranceId = "speech-sample-en",
        ),
    )

    /** The Chinese half of the sample, for learners whose engine lacks an English voice. */
    fun speakSampleChinese() = controller.speak(
        SpeechRequest(
            text = "这是中文语音示例。",
            localeTag = SPEECH_LOCALE_CHINESE,
            content = SpeechContent.MEANING,
            rateMode = rateMode(),
            utteranceId = "speech-sample-zh",
        ),
    )

    fun stop() = controller.stop()

    fun openSystemSpeechSettings() {
        SystemTtsSpeechService.openSystemSpeechSettings(context)
    }

    fun installVoiceData() {
        SystemTtsSpeechService.requestVoiceDataInstall(context)
    }
}

/**
 * Creates the actions bound to the composition, using [rateModeProvider] so a screen can follow the
 * live setting without recreating the controller.
 */
@Composable
fun rememberSpeechActions(
    service: com.cetvocab.app.data.speech.SpeechService,
    rateModeProvider: () -> SpeechRateMode,
): SpeechActions {
    val controller = rememberSpeechController(service)
    val context = LocalContext.current
    return remember(controller, context) {
        SpeechActions(controller, context.applicationContext, rateModeProvider)
    }
}
