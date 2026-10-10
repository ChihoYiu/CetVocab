package com.cetvocab.app.core.time

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * Abstraction over the clock so that every time dependent rule can be unit tested
 * deterministically and so that time-zone changes are handled in exactly one place.
 */
interface TimeProvider {
    /** Current wall-clock time as epoch milliseconds (UTC based, zone independent). */
    fun nowMillis(): Long

    /** Zone used to turn an instant into a "study day" (calendar date). */
    fun zone(): ZoneId
}

/** Production implementation backed by the system clock and the device time zone. */
class SystemTimeProvider : TimeProvider {
    override fun nowMillis(): Long = System.currentTimeMillis()
    override fun zone(): ZoneId = ZoneId.systemDefault()
}

/**
 * All "day" arithmetic for the app.
 *
 * A study day starts at [dayStartHour] instead of midnight: a session finished at 01:30 is
 * counted for the previous day, which matches how students actually study and avoids
 * losing a streak to a late-night session.
 *
 * Timestamps are always stored as epoch milliseconds. The calendar date used for daily
 * statistics is derived from the *current* zone at read time, so travelling across time
 * zones or a DST switch cannot corrupt stored data — at worst a boundary day is counted
 * once more or once less, never duplicated inside a single day bucket.
 */
object StudyDay {

    /** Hour (local time) at which a new study day begins. */
    const val dayStartHour: Int = 4

    /** Epoch millis of the start of the study day that contains [epochMillis]. */
    fun startOfDayMillis(epochMillis: Long, zone: ZoneId): Long {
        val date = localDate(epochMillis, zone)
        return date.atTime(LocalTime.of(dayStartHour, 0)).atZone(zone).toInstant().toEpochMilli()
    }

    /** Epoch millis at which the study day containing [epochMillis] ends (exclusive). */
    fun endOfDayMillis(epochMillis: Long, zone: ZoneId): Long =
        startOfDayMillis(epochMillis, zone) + Duration.ofDays(1).toMillis()

    /** The study-date that [epochMillis] belongs to. */
    fun localDate(epochMillis: Long, zone: ZoneId): LocalDate {
        val shifted = Instant.ofEpochMilli(epochMillis)
            .atZone(zone)
            .minusHours(dayStartHour.toLong())
        return shifted.toLocalDate()
    }

    /** Stable `yyyy-MM-dd` key for a study-date, used for daily summary rows. */
    fun dayKey(epochMillis: Long, zone: ZoneId): String = localDate(epochMillis, zone).toString()

    /** Epoch millis of the first moment of the study day identified by [date]. */
    fun millisAtStartOf(date: LocalDate, zone: ZoneId): Long =
        date.atTime(LocalTime.of(dayStartHour, 0)).atZone(zone).toInstant().toEpochMilli()

    /**
     * True when [dueAt] should be shown to the user now.
     *
     * A timestamp far in the future (for example after the user moved the system clock
     * backwards, or after restoring a backup from a device with a wrong clock) is treated
     * as due rather than locking the word out for years.
     */
    fun isDue(dueAt: Long, nowMillis: Long, zone: ZoneId): Boolean =
        dueAt <= nowMillis || dueAt > nowMillis + Duration.ofDays(MAX_PLAUSIBLE_INTERVAL_DAYS).toMillis()

    private const val MAX_PLAUSIBLE_INTERVAL_DAYS = 400L
}

/** Whole days between two instants, floored, never negative. */
fun daysBetween(fromMillis: Long, toMillis: Long): Long {
    val diff = toMillis - fromMillis
    return if (diff <= 0) 0 else Duration.ofMillis(diff).toDays()
}
