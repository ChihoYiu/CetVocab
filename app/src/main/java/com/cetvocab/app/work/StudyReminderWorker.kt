package com.cetvocab.app.work

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.cetvocab.app.MainActivity
import com.cetvocab.app.R
import com.cetvocab.app.core.time.StudyDay
import com.cetvocab.app.data.local.CetVocabDatabase
import com.cetvocab.app.data.settings.DataStoreSettingsRepository
import java.time.Duration
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.util.concurrent.TimeUnit

/**
 * Study reminder.
 *
 * WorkManager is used because the reminder must survive process death, a device reboot and
 * Doze: a unique one-time request is scheduled for the next reminder moment and, when it
 * fires, it schedules the following day's request. A daily periodic request acts as a safety
 * net so a missed wake-up can never silence reminders forever.
 *
 * The notification channel is created for every supported version because minSdk is 26
 * (Android 8.0), where channels are mandatory. On Android 13+ the POST_NOTIFICATIONS
 * permission is checked *inside* the worker: without it the work finishes successfully and
 * simply posts nothing, which keeps Android 8-12 behaviour unchanged.
 */
class StudyReminderWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val database = CetVocabDatabase.build(applicationContext)
        val settingsRepository = DataStoreSettingsRepository(applicationContext)
        return try {
            val settings = settingsRepository.current()
            if (settings.reminderEnabled) {
                val now = System.currentTimeMillis()
                val dayKey = StudyDay.dayKey(now, ZoneId.systemDefault())
                val summary = database.dailySummaryDao().get(dayKey, settings.level.id)
                val due = database.progressDao().dueCount(settings.level.id, now)
                postNotification(
                    ReminderContent(
                        studiedToday = summary?.studiedWords ?: 0,
                        dueCount = due,
                        dailyGoal = settings.dailyNewWordGoal,
                    ),
                )
            }
            ReminderScheduler(applicationContext).scheduleNext(settings.reminderHour, settings.reminderMinute)
            Result.success()
        } catch (_: Throwable) {
            // A reminder must never crash the app; retry once with backoff instead.
            Result.retry()
        } finally {
            database.close()
        }
    }

    private fun postNotification(content: ReminderContent) {
        if (!canPostNotifications()) return
        ReminderScheduler(applicationContext).ensureChannel()

        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val text = when {
            content.studiedToday == 0 && content.dueCount > 0 -> "今天还有 ${content.dueCount} 个单词待复习"
            content.studiedToday == 0 -> "今天还没有开始学习，从 ${content.dailyGoal} 个新词开始吧"
            content.dueCount > 0 -> "已完成 ${content.studiedToday} 个词，还有 ${content.dueCount} 个待复习"
            else -> "今天已学习 ${content.studiedToday} 个词，保持节奏"
        }

        val notification = NotificationCompat.Builder(applicationContext, ReminderScheduler.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("该背单词了")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        runCatching {
            NotificationManagerCompat.from(applicationContext).notify(NOTIFICATION_ID, notification)
        }
    }

    private fun canPostNotifications(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            applicationContext,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
    }

    private data class ReminderContent(
        val studiedToday: Int,
        val dueCount: Int,
        val dailyGoal: Int,
    )

    companion object {
        const val NOTIFICATION_ID = 1001
    }
}

/** Schedules and cancels reminders. Safe to call on every app start. */
class ReminderScheduler(private val context: Context) {

    private val workManager: WorkManager get() = WorkManager.getInstance(context)

    /** Creates the notification channel (required from Android 8.0, our minimum). */
    fun ensureChannel() {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "学习提醒",
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = "每天提醒你复习单词，可在个人中心关闭"
            enableVibration(false)
        }
        manager.createNotificationChannel(channel)
    }

    /** Schedules the next reminder and the daily safety net. */
    fun scheduleNext(hour: Int, minute: Int) {
        ensureChannel()
        val delay = millisUntilNext(hour, minute)
        val request = OneTimeWorkRequestBuilder<StudyReminderWorker>()
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .setConstraints(Constraints.NONE)
            .addTag(TAG)
            .build()
        workManager.enqueueUniqueWork(ONE_TIME_WORK, ExistingWorkPolicy.REPLACE, request)

        val daily = PeriodicWorkRequestBuilder<StudyReminderWorker>(1, TimeUnit.DAYS)
            .addTag(TAG)
            .build()
        workManager.enqueueUniquePeriodicWork(
            PERIODIC_WORK,
            ExistingPeriodicWorkPolicy.KEEP,
            daily,
        )
    }

    /** Cancels everything when the user turns reminders off. */
    fun cancel() {
        workManager.cancelUniqueWork(ONE_TIME_WORK)
        workManager.cancelUniqueWork(PERIODIC_WORK)
    }

    /** Re-applies the user's current preference (called on every app start). */
    fun sync(enabled: Boolean, hour: Int, minute: Int) {
        if (enabled) scheduleNext(hour, minute) else cancel()
    }

    private fun millisUntilNext(hour: Int, minute: Int): Long {
        val zone = ZoneId.systemDefault()
        val now = Instant.now().atZone(zone)
        var target = now.with(LocalTime.of(hour, minute))
        if (!target.isAfter(now)) target = target.plusDays(1)
        return Duration.between(now, target).toMillis().coerceAtLeast(0L)
    }

    companion object {
        const val CHANNEL_ID = "study_reminder"
        const val TAG = "study-reminder"
        private const val ONE_TIME_WORK = "study-reminder-next"
        private const val PERIODIC_WORK = "study-reminder-daily"
    }
}
