package com.cetvocab.app.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.cetvocab.app.data.speech.SpeechRateMode
import com.cetvocab.app.domain.model.CetLevel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException

/** DataStore handle for the single preferences file used by the app. */
private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "cet_vocab_settings",
)

/**
 * Settings boundary. The UI only ever sees [UserSettings] and suspending setters, so the
 * storage technology could be swapped without touching a single screen.
 */
interface SettingsRepository {
    val settings: Flow<UserSettings>

    suspend fun current(): UserSettings

    suspend fun setOnboardingCompleted(completed: Boolean)
    suspend fun setLevel(level: CetLevel)
    suspend fun setDailyNewWordGoal(count: Int)
    suspend fun setDailyReviewLimit(limit: Int)
    suspend fun setReminder(enabled: Boolean, hour: Int, minute: Int)
    suspend fun setThemeMode(mode: ThemeMode)
    suspend fun setDynamicColor(enabled: Boolean)
    suspend fun setAnimationsEnabled(enabled: Boolean)
    suspend fun setAutoPlayPronunciation(enabled: Boolean)

    /** Whether the example sentence is read aloud as well as the word. */
    suspend fun setReadExampleSentences(enabled: Boolean)

    /** How fast to read; [SpeechRateMode.SYSTEM] defers to the operating-system setting. */
    suspend fun setSpeechRateMode(mode: SpeechRateMode)

    /** Records that today's motivational line has been shown, so it appears once per day. */
    suspend fun setLastMotivationDayKey(dayKey: String)

    /** Records a celebrated streak / milestone so the same achievement is not announced twice. */
    suspend fun setCelebratedStreak(value: Int)
    suspend fun setCelebratedMilestone(value: Int)

    suspend fun setExamPreferences(questionCount: Int, durationMinutes: Int)

    /** Restores every preference to its factory value (study data is untouched). */
    suspend fun resetToDefaults()
}

/** Preferences DataStore implementation. */
class DataStoreSettingsRepository(private val context: Context) : SettingsRepository {

    override val settings: Flow<UserSettings> = context.settingsDataStore.data
        .catch { throwable ->
            // A corrupted preferences file must never crash the app: fall back to defaults.
            if (throwable is IOException) emit(emptyPreferences()) else throw throwable
        }
        .map { prefs -> prefs.toUserSettings() }

    override suspend fun current(): UserSettings = settings.first()

    override suspend fun setOnboardingCompleted(completed: Boolean) {
        context.settingsDataStore.edit { it[Keys.ONBOARDING_COMPLETED] = completed }
    }

    override suspend fun setLevel(level: CetLevel) {
        context.settingsDataStore.edit { it[Keys.LEVEL] = level.id }
    }

    override suspend fun setDailyNewWordGoal(count: Int) {
        context.settingsDataStore.edit { it[Keys.DAILY_NEW_WORDS] = UserSettings.clampNewWords(count) }
    }

    override suspend fun setDailyReviewLimit(limit: Int) {
        context.settingsDataStore.edit { it[Keys.DAILY_REVIEW_LIMIT] = UserSettings.clampReviewLimit(limit) }
    }

    override suspend fun setReminder(enabled: Boolean, hour: Int, minute: Int) {
        context.settingsDataStore.edit { prefs ->
            prefs[Keys.REMINDER_ENABLED] = enabled
            prefs[Keys.REMINDER_HOUR] = hour.coerceIn(0, 23)
            prefs[Keys.REMINDER_MINUTE] = minute.coerceIn(0, 59)
        }
    }

    override suspend fun setThemeMode(mode: ThemeMode) {
        context.settingsDataStore.edit { it[Keys.THEME_MODE] = mode.id }
    }

    override suspend fun setDynamicColor(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.DYNAMIC_COLOR] = enabled }
    }

    override suspend fun setAnimationsEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.ANIMATIONS] = enabled }
    }

    override suspend fun setAutoPlayPronunciation(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.AUTO_PLAY] = enabled }
    }

    override suspend fun setReadExampleSentences(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.READ_EXAMPLES] = enabled }
    }

    override suspend fun setSpeechRateMode(mode: SpeechRateMode) {
        context.settingsDataStore.edit { it[Keys.SPEECH_RATE_MODE] = mode.id }
    }

    override suspend fun setLastMotivationDayKey(dayKey: String) {
        context.settingsDataStore.edit { it[Keys.LAST_MOTIVATION_DAY] = dayKey }
    }

    override suspend fun setCelebratedStreak(value: Int) {
        context.settingsDataStore.edit { prefs ->
            val current = prefs[Keys.CELEBRATED_STREAK] ?: 0
            // Never move the marker backwards: a streak that was already celebrated must not be
            // re-announced because the streak later dropped and recovered.
            if (value > current) prefs[Keys.CELEBRATED_STREAK] = value
        }
    }

    override suspend fun setCelebratedMilestone(value: Int) {
        context.settingsDataStore.edit { prefs ->
            val current = prefs[Keys.CELEBRATED_MILESTONE] ?: 0
            if (value > current) prefs[Keys.CELEBRATED_MILESTONE] = value
        }
    }

    override suspend fun setExamPreferences(questionCount: Int, durationMinutes: Int) {
        context.settingsDataStore.edit { prefs ->
            prefs[Keys.EXAM_QUESTIONS] = UserSettings.clampExamQuestions(questionCount)
            prefs[Keys.EXAM_DURATION] = UserSettings.clampExamDuration(durationMinutes)
        }
    }

    override suspend fun resetToDefaults() {
        context.settingsDataStore.edit { it.clear() }
    }

    private fun Preferences.toUserSettings(): UserSettings {
        val defaults = UserSettings()
        return UserSettings(
            onboardingCompleted = this[Keys.ONBOARDING_COMPLETED] ?: defaults.onboardingCompleted,
            level = CetLevel.fromId(this[Keys.LEVEL] ?: defaults.level.id),
            dailyNewWordGoal = UserSettings.clampNewWords(
                this[Keys.DAILY_NEW_WORDS] ?: defaults.dailyNewWordGoal,
            ),
            dailyReviewLimit = UserSettings.clampReviewLimit(
                this[Keys.DAILY_REVIEW_LIMIT] ?: defaults.dailyReviewLimit,
            ),
            reminderEnabled = this[Keys.REMINDER_ENABLED] ?: defaults.reminderEnabled,
            reminderHour = (this[Keys.REMINDER_HOUR] ?: defaults.reminderHour).coerceIn(0, 23),
            reminderMinute = (this[Keys.REMINDER_MINUTE] ?: defaults.reminderMinute).coerceIn(0, 59),
            themeMode = ThemeMode.fromId(this[Keys.THEME_MODE] ?: defaults.themeMode.id),
            dynamicColor = this[Keys.DYNAMIC_COLOR] ?: defaults.dynamicColor,
            animationsEnabled = this[Keys.ANIMATIONS] ?: defaults.animationsEnabled,
            autoPlayPronunciation = this[Keys.AUTO_PLAY] ?: defaults.autoPlayPronunciation,
            readExampleSentences = this[Keys.READ_EXAMPLES] ?: defaults.readExampleSentences,
            speechRateMode = SpeechRateMode.fromId(
                this[Keys.SPEECH_RATE_MODE] ?: defaults.speechRateMode.id,
            ),
            lastMotivationDayKey = this[Keys.LAST_MOTIVATION_DAY],
            celebratedStreak = this[Keys.CELEBRATED_STREAK] ?: defaults.celebratedStreak,
            celebratedMilestone = this[Keys.CELEBRATED_MILESTONE] ?: defaults.celebratedMilestone,
            examQuestionCount = UserSettings.clampExamQuestions(
                this[Keys.EXAM_QUESTIONS] ?: defaults.examQuestionCount,
            ),
            examDurationMinutes = UserSettings.clampExamDuration(
                this[Keys.EXAM_DURATION] ?: defaults.examDurationMinutes,
            ),
        )
    }

    private object Keys {
        val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
        val LEVEL = stringPreferencesKey("level")
        val DAILY_NEW_WORDS = intPreferencesKey("daily_new_words")
        val DAILY_REVIEW_LIMIT = intPreferencesKey("daily_review_limit")
        val REMINDER_ENABLED = booleanPreferencesKey("reminder_enabled")
        val REMINDER_HOUR = intPreferencesKey("reminder_hour")
        val REMINDER_MINUTE = intPreferencesKey("reminder_minute")

        /**
         * The old `accent` key is intentionally left unmapped: the accent feature was removed, so a
         * value written by an older version is simply ignored (and dropped on the next reset).
         */
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val ANIMATIONS = booleanPreferencesKey("animations_enabled")
        val AUTO_PLAY = booleanPreferencesKey("auto_play_pronunciation")
        val READ_EXAMPLES = booleanPreferencesKey("read_example_sentences")
        val SPEECH_RATE_MODE = stringPreferencesKey("speech_rate_mode")
        val LAST_MOTIVATION_DAY = stringPreferencesKey("last_motivation_day")
        val CELEBRATED_STREAK = intPreferencesKey("celebrated_streak")
        val CELEBRATED_MILESTONE = intPreferencesKey("celebrated_milestone")
        val EXAM_QUESTIONS = intPreferencesKey("exam_questions")
        val EXAM_DURATION = intPreferencesKey("exam_duration")
    }
}
