package com.cetvocab.app.data.settings

import com.cetvocab.app.data.speech.SpeechRateMode
import com.cetvocab.app.domain.model.CetLevel

/** Light / dark / follow system. */
enum class ThemeMode(val id: String, val label: String) {
    SYSTEM("SYSTEM", "跟随系统"),
    LIGHT("LIGHT", "浅色"),
    DARK("DARK", "深色");

    companion object {
        fun fromId(id: String?): ThemeMode = entries.firstOrNull { it.id == id } ?: SYSTEM
    }
}

/**
 * Every user-facing setting. Stored in Preferences DataStore (small, non-relational),
 * while structured study data lives in Room.
 *
 * Note on speech: there is deliberately **no accent option**. Reading is done by the system
 * text-to-speech engine, so the language, voice, rate and pitch come from the operating system's own
 * settings; [speechRateMode] only exists so a learner can slow down a single hard word without
 * changing a device-wide preference.
 */
data class UserSettings(
    val onboardingCompleted: Boolean = false,
    val level: CetLevel = CetLevel.CET4,
    val dailyNewWordGoal: Int = DEFAULT_NEW_WORDS,
    val dailyReviewLimit: Int = DEFAULT_REVIEW_LIMIT,
    val reminderEnabled: Boolean = false,
    val reminderHour: Int = 20,
    val reminderMinute: Int = 0,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = false,
    val animationsEnabled: Boolean = true,
    /** Auto-play the word when a new card appears. */
    val autoPlayPronunciation: Boolean = false,
    /**
     * Shuffle the study queue ("乱序").
     *
     * Applied **once to the whole queue** when a session starts — never per card: a per-card
     * random could show the same word twice and never reach the end of the list. The session
     * persists the resulting order, so resuming continues in the same order instead of
     * re-shuffling on every resume.
     */
    val shuffleWords: Boolean = false,
    /** Read the example sentence too when the card is revealed. */
    val readExampleSentences: Boolean = false,
    /** How fast to read; SYSTEM means "use the operating system's setting". */
    val speechRateMode: SpeechRateMode = SpeechRateMode.SYSTEM,
    val examQuestionCount: Int = 20,
    val examDurationMinutes: Int = 15,
    /** Day key (`yyyy-MM-dd`) of the last motivational line shown on the home screen. */
    val lastMotivationDayKey: String? = null,
    /** Highest streak value already celebrated, so a new record is announced exactly once. */
    val celebratedStreak: Int = 0,
    /** Highest vocabulary milestone already celebrated. */
    val celebratedMilestone: Int = 0,
) {
    companion object {
        const val DEFAULT_NEW_WORDS = 20
        const val DEFAULT_REVIEW_LIMIT = 60

        /** Allowed ranges; the UI clamps to these and the repository validates on write. */
        val NEW_WORD_RANGE = 5..100
        val REVIEW_LIMIT_RANGE = 10..300
        val EXAM_QUESTION_RANGE = 5..50
        val EXAM_DURATION_RANGE = 5..60

        fun clampNewWords(value: Int) = value.coerceIn(NEW_WORD_RANGE)
        fun clampReviewLimit(value: Int) = value.coerceIn(REVIEW_LIMIT_RANGE)
        fun clampExamQuestions(value: Int) = value.coerceIn(EXAM_QUESTION_RANGE)
        fun clampExamDuration(value: Int) = value.coerceIn(EXAM_DURATION_RANGE)
    }

    /** Reminder time formatted as `HH:mm` for display. */
    val reminderLabel: String
        get() = "%02d:%02d".format(reminderHour, reminderMinute)
}
