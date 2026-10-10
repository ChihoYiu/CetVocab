package com.cetvocab.app

import android.content.Context
import com.cetvocab.app.core.time.SystemTimeProvider
import com.cetvocab.app.core.time.TimeProvider
import com.cetvocab.app.data.io.DocumentIo
import com.cetvocab.app.data.local.CetVocabDatabase
import com.cetvocab.app.data.repository.DefaultInsightRepository
import com.cetvocab.app.data.repository.InsightRepository
import com.cetvocab.app.data.speech.SpeechDiagnostic
import com.cetvocab.app.data.speech.SpeechDiagnosticsStore
import com.cetvocab.app.data.speech.SpeechService
import com.cetvocab.app.data.speech.SystemTtsSpeechService
import com.cetvocab.app.data.repository.BackupRepository
import com.cetvocab.app.data.repository.DefaultBackupRepository
import com.cetvocab.app.data.repository.DefaultExamRepository
import com.cetvocab.app.data.repository.DefaultMistakeRepository
import com.cetvocab.app.data.repository.DefaultStatsRepository
import com.cetvocab.app.data.repository.DefaultStudyRepository
import com.cetvocab.app.data.repository.DefaultWordRepository
import com.cetvocab.app.data.repository.ExamRepository
import com.cetvocab.app.data.repository.MistakeRepository
import com.cetvocab.app.data.repository.ProgressRecorder
import com.cetvocab.app.data.repository.SeedProgress
import com.cetvocab.app.data.repository.StatsRepository
import com.cetvocab.app.data.repository.StudyRepository
import com.cetvocab.app.data.repository.WordRepository
import com.cetvocab.app.data.seed.BuiltInPackLoader
import com.cetvocab.app.data.settings.DataStoreSettingsRepository
import com.cetvocab.app.data.settings.SettingsRepository
import com.cetvocab.app.work.ReminderScheduler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

/**
 * The application's object graph.
 *
 * Constructor injection by hand instead of a DI framework: the graph is small and explicit,
 * it keeps the build fast (one annotation processor instead of three), and every ViewModel
 * receives exactly the interfaces it needs through a factory, so nothing in the UI can reach
 * a DAO or DataStore directly.
 */
class AppContainer(
    private val context: Context,
    /**
     * Injection seam for tests: unit and Compose-UI tests pass an in-memory Room database so a
     * test run never touches the user's real data and can be asserted on directly.
     */
    databaseOverride: CetVocabDatabase? = null,
) {

    val timeProvider: TimeProvider = SystemTimeProvider()

    val database: CetVocabDatabase = databaseOverride ?: CetVocabDatabase.build(context)

    val settingsRepository: SettingsRepository = DataStoreSettingsRepository(context)

    val progressRecorder: ProgressRecorder = ProgressRecorder(database, timeProvider)

    /**
     * Progress of the built-in word-pack seeding.
     *
     * The complete CET-4/CET-6 lists hold thousands of entries, so the cold-start screen shows
     * real numbers instead of an indefinite spinner.
     */
    private val _seedProgress = MutableStateFlow<SeedProgress?>(null)
    val seedProgress: StateFlow<SeedProgress?> = _seedProgress.asStateFlow()

    private val packLoader = BuiltInPackLoader(context)

    val wordRepository: WordRepository = DefaultWordRepository(
        db = database,
        time = timeProvider,
        packLoader = packLoader,
        onSeedProgress = { progress -> _seedProgress.value = progress },
    )

    val studyRepository: StudyRepository =
        DefaultStudyRepository(database, timeProvider, progressRecorder)

    val mistakeRepository: MistakeRepository = DefaultMistakeRepository(database, timeProvider)

    val examRepository: ExamRepository = DefaultExamRepository(database, timeProvider, progressRecorder)

    val statsRepository: StatsRepository = DefaultStatsRepository(database, timeProvider)

    val backupRepository: BackupRepository = DefaultBackupRepository(database, timeProvider)

    val documentIo: DocumentIo = DocumentIo(context)

    val reminderScheduler: ReminderScheduler = ReminderScheduler(context)

    /** Version name shown in the profile / about screen. */
    val appVersionName: String = runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()
    }.getOrDefault("")

    /**
     * Reads the inputs the learning-analysis rules need.
     *
     * Kept as its own repository because it is a read-only projection over existing tables: the
     * analysis adds no schema, so an upgrade cannot invalidate a learner's data.
     */
    val insightRepository: InsightRepository = DefaultInsightRepository(database, timeProvider)

    // --- Speech ----------------------------------------------------------------------------

    /**
     * Local counters for the speech diagnostics panel.
     *
     * They live in memory only: the app ships no analytics SDK, and these numbers exist to let the
     * learner see whether reading works on their device. Nothing is uploaded, and there is no
     * per-word history — just two counters and a failure breakdown.
     */
    val speechDiagnostics = SpeechDiagnosticsStore()

    /**
     * The system text-to-speech engine.
     *
     * Created once for the whole app: binding a `TextToSpeech` service is expensive, and one shared
     * instance keeps the engine state (and therefore the UI state) consistent between screens.
     * Nothing here requires a network or a permission.
     */
    val speechService: SpeechService = SystemTtsSpeechService(
        context = context,
        onDiagnostic = speechDiagnostics::record,
    )

    /**
     * One-time preparation performed on app start: make sure the built-in packs are present and up
     * to date, then re-apply the reminder schedule (WorkManager persists it, but a settings change
     * or a restore can make the stored schedule stale).
     */
    suspend fun prepare() {
        wordRepository.ensureBuiltInPacks()
        _seedProgress.value = null
        val settings = settingsRepository.current()
        reminderScheduler.ensureChannel()
        reminderScheduler.sync(settings.reminderEnabled, settings.reminderHour, settings.reminderMinute)
    }
}
