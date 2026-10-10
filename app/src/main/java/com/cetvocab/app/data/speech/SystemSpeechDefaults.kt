package com.cetvocab.app.data.speech

import android.content.Context
import android.provider.Settings

/**
 * Reads the user's text-to-speech preferences from the operating system.
 *
 * Why this class exists: Android does **not** hand an app the user's speech rate, pitch, engine or
 * language. A `TextToSpeech` instance starts with the engine's own defaults, so an app that calls
 * `setSpeechRate(1f)` unconditionally silently overrides whatever the learner configured globally.
 *
 * The keys below are the documented `Settings.Secure` entries that the system's own
 * "Text-to-speech output" screen writes (`Settings.Secure.TTS_DEFAULT_SYNTH` /
 * `TTS_DEFAULT_RATE` / `TTS_DEFAULT_PITCH` / `TTS_DEFAULT_LOCALE`).
 *
 * Two deliberate rules:
 *  1. every read is wrapped — an unreadable or missing key yields `null`, never an exception and
 *     never a substituted value (some vendor ROMs restrict these keys, and a wrong guess is worse
 *     than the engine's own default);
 *  2. a value outside a sane range is treated as unreadable, because a corrupted setting must not
 *     make speech unusable.
 *
 * NOTE: readability of these keys on a physical device has **not** been verified by an automated
 * test in this project (Robolectric does not model vendor ROM behaviour). The design therefore
 * degrades safely: [SystemSpeechDefaults] with all-null fields means "use the engine defaults".
 */
interface SpeechDefaultsReader {
    fun read(): SystemSpeechDefaults
}

class AndroidSpeechDefaultsReader(private val context: Context) : SpeechDefaultsReader {

    override fun read(): SystemSpeechDefaults {
        val resolver = context.contentResolver
        return SystemSpeechDefaults(
            rate = readFloat(resolver, KEY_RATE),
            pitch = readFloat(resolver, KEY_PITCH),
            engine = readString(resolver, KEY_ENGINE),
            localeTag = readString(resolver, KEY_LOCALE),
        )
    }

    private fun readString(resolver: android.content.ContentResolver, key: String): String? =
        runCatching { Settings.Secure.getString(resolver, key) }
            .getOrNull()
            ?.takeIf { it.isNotBlank() }

    private fun readFloat(resolver: android.content.ContentResolver, key: String): Float? =
        readString(resolver, key)?.let { raw ->
            val parsed = raw.toFloatOrNull() ?: return@let null
            // Android's own UI allows 0.5x–2.0x; anything else is treated as unreadable.
            parsed.takeIf { it in MIN_RATE..MAX_RATE }
        }

    private companion object {
        /** `Settings.Secure.TTS_DEFAULT_RATE` */
        const val KEY_RATE = "tts_default_rate"

        /** `Settings.Secure.TTS_DEFAULT_PITCH` */
        const val KEY_PITCH = "tts_default_pitch"

        /** `Settings.Secure.TTS_DEFAULT_SYNTH` */
        const val KEY_ENGINE = "tts_default_synth"

        /** `Settings.Secure.TTS_DEFAULT_LOCALE` */
        const val KEY_LOCALE = "tts_default_locale"

        const val MIN_RATE = 0.5f
        const val MAX_RATE = 2.0f
    }
}
