package com.cetvocab.app.core.time

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * Unit tests for the study-day boundary rules.
 *
 * The clock is never read: every instant is built from a literal local date time in the zone
 * under test, so the assertions hold in every time zone the build machine happens to have.
 */
class StudyDayTest {

    private val utc = ZoneId.of("UTC")
    private val shanghai = ZoneId.of("Asia/Shanghai")
    private val newYork = ZoneId.of("America/New_York")

    private fun millis(dateTime: LocalDateTime, zone: ZoneId): Long =
        dateTime.atZone(zone).toInstant().toEpochMilli()

    // --- The 04:00 boundary ----------------------------------------------------------------

    @Test
    fun `three fifty nine in the morning still belongs to the previous study day`() {
        val moment = millis(LocalDateTime.of(2026, 1, 15, 3, 59), utc)

        assertThat(StudyDay.localDate(moment, utc)).isEqualTo(LocalDate.of(2026, 1, 14))
        assertThat(StudyDay.dayKey(moment, utc)).isEqualTo("2026-01-14")
    }

    @Test
    fun `four in the morning starts a new study day`() {
        val moment = millis(LocalDateTime.of(2026, 1, 15, 4, 0), utc)

        assertThat(StudyDay.localDate(moment, utc)).isEqualTo(LocalDate.of(2026, 1, 15))
        assertThat(StudyDay.dayKey(moment, utc)).isEqualTo("2026-01-15")
    }

    @Test
    fun `midnight and noon belong to the same study day`() {
        val midnight = millis(LocalDateTime.of(2026, 1, 15, 0, 0), utc)
        val noon = millis(LocalDateTime.of(2026, 1, 15, 12, 0), utc)
        val lateEvening = millis(LocalDateTime.of(2026, 1, 15, 23, 59, 59), utc)

        assertThat(StudyDay.dayKey(midnight, utc)).isEqualTo("2026-01-14")
        assertThat(StudyDay.dayKey(noon, utc)).isEqualTo("2026-01-15")
        assertThat(StudyDay.dayKey(lateEvening, utc)).isEqualTo("2026-01-15")
    }

    @Test
    fun `the boundary holds in a non utc zone`() {
        val before = millis(LocalDateTime.of(2026, 1, 15, 3, 59), shanghai)
        val after = millis(LocalDateTime.of(2026, 1, 15, 4, 0), shanghai)

        assertThat(StudyDay.dayKey(before, shanghai)).isEqualTo("2026-01-14")
        assertThat(StudyDay.dayKey(after, shanghai)).isEqualTo("2026-01-15")
        assertThat(StudyDay.localDate(after, shanghai)).isEqualTo(LocalDate.of(2026, 1, 15))
    }

    @Test
    fun `an evening session is counted for the day it was started on`() {
        // 2026-01-15T01:30+08:00 is still the study day of 2026-01-14.
        val moment = millis(LocalDateTime.of(2026, 1, 15, 1, 30), shanghai)

        assertThat(StudyDay.dayKey(moment, shanghai)).isEqualTo("2026-01-14")
    }

    @Test
    fun `the same instant falls on different study days in different zones`() {
        val moment = millis(LocalDateTime.of(2026, 1, 15, 22, 0), utc)

        // 06:00 in Shanghai the next calendar day, still before 04:00 in New York.
        assertThat(StudyDay.dayKey(moment, utc)).isEqualTo("2026-01-15")
        assertThat(StudyDay.dayKey(moment, shanghai)).isEqualTo("2026-01-16")
        assertThat(StudyDay.dayKey(moment, newYork)).isEqualTo("2026-01-15")
    }

    // --- start / end of day ----------------------------------------------------------------

    @Test
    fun `start and end of day are twenty four hours apart`() {
        val moment = millis(LocalDateTime.of(2026, 1, 15, 10, 30), utc)

        val start = StudyDay.startOfDayMillis(moment, utc)
        val end = StudyDay.endOfDayMillis(moment, utc)

        assertThat(start).isEqualTo(millis(LocalDateTime.of(2026, 1, 15, 4, 0), utc))
        assertThat(end - start).isEqualTo(24L * 60 * 60 * 1000)
        assertThat(end).isEqualTo(millis(LocalDateTime.of(2026, 1, 16, 4, 0), utc))
    }

    @Test
    fun `start of day is consistent with the day key`() {
        val moments = listOf(
            LocalDateTime.of(2026, 1, 15, 4, 0),
            LocalDateTime.of(2026, 1, 15, 9, 15),
            LocalDateTime.of(2026, 1, 16, 3, 59),
        )

        moments.forEach { local ->
            val moment = millis(local, utc)
            val start = StudyDay.startOfDayMillis(moment, utc)
            val end = StudyDay.endOfDayMillis(moment, utc)

            assertThat(StudyDay.dayKey(moment, utc))
                .isEqualTo(StudyDay.dayKey(start, utc))
            assertThat(StudyDay.localDate(start, utc))
                .isEqualTo(LocalDate.of(2026, 1, 15))
            assertThat(end - start).isEqualTo(24L * 60 * 60 * 1000)
            assertThat(moment).isAtLeast(start)
            assertThat(moment).isLessThan(end)
        }
    }

    @Test
    fun `the study day start is the local four o'clock in any zone`() {
        val moment = millis(LocalDateTime.of(2026, 6, 10, 15, 45), shanghai)
        val start = StudyDay.startOfDayMillis(moment, shanghai)

        assertThat(StudyDay.localDate(start, shanghai)).isEqualTo(LocalDate.of(2026, 6, 10))
        assertThat(
            java.time.Instant.ofEpochMilli(start)
                .atZone(shanghai)
                .toLocalTime(),
        ).isEqualTo(LocalTime.of(StudyDay.dayStartHour, 0))
        assertThat(StudyDay.dayStartHour).isEqualTo(4)
    }

    @Test
    fun `millis at start of a date round trips through the day key`() {
        val date = LocalDate.of(2026, 3, 1)
        val start = StudyDay.millisAtStartOf(date, shanghai)

        assertThat(StudyDay.localDate(start, shanghai)).isEqualTo(date)
        assertThat(StudyDay.millisAtStartOf(date, shanghai))
            .isEqualTo(millis(LocalDateTime.of(2026, 3, 1, 4, 0), shanghai))
    }

    // --- DST ---------------------------------------------------------------------------------

    @Test
    fun `the four am boundary survives the spring forward transition`() {
        // Sunday 2026-03-08: 01:59 EST -> 03:00 EDT.
        val before = millis(LocalDateTime.of(2026, 3, 8, 2, 0), newYork)
        val after = millis(LocalDateTime.of(2026, 3, 8, 5, 0), newYork)

        assertThat(StudyDay.localDate(before, newYork)).isEqualTo(LocalDate.of(2026, 3, 7))
        assertThat(StudyDay.localDate(after, newYork)).isEqualTo(LocalDate.of(2026, 3, 8))
    }

    @Test
    fun `a spring forward day is twenty three hours long`() {
        // The study day that starts on 2026-03-07 at 04:00 ends at 04:00 on 2026-03-08,
        // which is only 23 wall-clock hours later.
        val moment = millis(LocalDateTime.of(2026, 3, 7, 12, 0), newYork)
        val start = StudyDay.startOfDayMillis(moment, newYork)
        val end = StudyDay.millisAtStartOf(StudyDay.localDate(moment, newYork).plusDays(1), newYork)

        assertThat(end).isEqualTo(millis(LocalDateTime.of(2026, 3, 8, 4, 0), newYork))
        assertThat(end - start).isEqualTo(23L * 60 * 60 * 1000)
    }

    @Test
    fun `a fall back day is twenty five hours long`() {
        // Sunday 2026-11-01: 02:00 EDT -> 01:00 EST.
        val moment = millis(LocalDateTime.of(2026, 10, 31, 12, 0), newYork)
        val start = StudyDay.startOfDayMillis(moment, newYork)
        val end = StudyDay.millisAtStartOf(StudyDay.localDate(moment, newYork).plusDays(1), newYork)

        assertThat(StudyDay.localDate(moment, newYork)).isEqualTo(LocalDate.of(2026, 10, 31))
        assertThat(end - start).isEqualTo(25L * 60 * 60 * 1000)
    }

    @Test
    fun `end of day uses a fixed twenty four hour span`() {
        val moment = millis(LocalDateTime.of(2026, 10, 31, 12, 0), newYork)

        assertThat(StudyDay.endOfDayMillis(moment, newYork) - StudyDay.startOfDayMillis(moment, newYork))
            .isEqualTo(24L * 60 * 60 * 1000)
    }

    @Test
    fun `an early morning moment on a fall back day keeps the previous day key`() {
        // 01:30 local on 2026-11-01 minus four hours is still 2026-10-31.
        val ambiguous = millis(LocalDateTime.of(2026, 11, 1, 1, 30), newYork)
        val later = millis(LocalDateTime.of(2026, 11, 1, 10, 0), newYork)

        assertThat(StudyDay.dayKey(ambiguous, newYork)).isEqualTo("2026-10-31")
        assertThat(StudyDay.dayKey(later, newYork)).isEqualTo("2026-11-01")
    }

    // --- isDue -------------------------------------------------------------------------------

    @Test
    fun `a due date in the past is due`() {
        val now = millis(LocalDateTime.of(2026, 1, 15, 12, 0), utc)

        assertThat(StudyDay.isDue(now - 1, now, utc)).isTrue()
        assertThat(StudyDay.isDue(now, now, utc)).isTrue()
        assertThat(StudyDay.isDue(now - SpacedRepetitionMillis.DAY, now, utc)).isTrue()
    }

    @Test
    fun `a due date in the near future is not due`() {
        val now = millis(LocalDateTime.of(2026, 1, 15, 12, 0), utc)

        assertThat(StudyDay.isDue(now + 1, now, utc)).isFalse()
        assertThat(StudyDay.isDue(now + SpacedRepetitionMillis.DAY, now, utc)).isFalse()
        assertThat(StudyDay.isDue(now + 399 * SpacedRepetitionMillis.DAY, now, utc)).isFalse()
    }

    @Test
    fun `a broken clock far in the future is treated as due`() {
        val now = millis(LocalDateTime.of(2026, 1, 15, 12, 0), utc)

        assertThat(StudyDay.isDue(now + 401 * SpacedRepetitionMillis.DAY, now, utc)).isTrue()
        assertThat(StudyDay.isDue(now + 4_000 * SpacedRepetitionMillis.DAY, now, utc)).isTrue()
    }

    @Test
    fun `the plausible interval boundary is four hundred days`() {
        val now = millis(LocalDateTime.of(2026, 1, 15, 12, 0), utc)
        val boundary = now + 400 * SpacedRepetitionMillis.DAY

        // Exactly 400 days is the last "plausible" value and is therefore not forced to due.
        assertThat(StudyDay.isDue(boundary, now, utc)).isFalse()
        assertThat(StudyDay.isDue(boundary + 1, now, utc)).isTrue()
    }

    @Test
    fun `days between is floored and never negative`() {
        val from = millis(LocalDateTime.of(2026, 1, 15, 12, 0), utc)

        assertThat(daysBetween(from, from + 23 * 60 * 60 * 1000)).isEqualTo(0)
        assertThat(daysBetween(from, from + 24 * 60 * 60 * 1000)).isEqualTo(1)
        assertThat(daysBetween(from, from + 3 * 24 * 60 * 60 * 1000 + 60_000)).isEqualTo(3)
        assertThat(daysBetween(from, from)).isEqualTo(0)
        assertThat(daysBetween(from, from - 5 * 24 * 60 * 60 * 1000)).isEqualTo(0)
    }

    @Test
    fun `day key is the iso date of the shifted instant`() {
        val moment = millis(LocalDateTime.of(2026, 12, 31, 23, 50), utc)

        assertThat(StudyDay.dayKey(moment, utc)).isEqualTo("2026-12-31")
        assertThat(StudyDay.localDate(moment, utc).toString()).isEqualTo(StudyDay.dayKey(moment, utc))
    }

    /** Local copy of the millisecond constants, so the test does not depend on the SRS package. */
    private object SpacedRepetitionMillis {
        const val DAY: Long = 24L * 60 * 60 * 1000
    }
}
