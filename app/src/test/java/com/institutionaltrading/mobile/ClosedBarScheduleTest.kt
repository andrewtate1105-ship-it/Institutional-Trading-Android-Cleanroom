package com.institutionaltrading.mobile

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ClosedBarScheduleTest {
    private val weekdays = TradingSessionCalendar { date ->
        date.dayOfWeek !in setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)
    }

    @Test fun fiveMinuteBarsUseAsiaKolkataSessionAnchoring() {
        assertNull(NseClosedBarSchedule.latestCompleted("5M", Instant.parse("2026-08-03T03:49:59Z"), weekdays))
        assertEquals(
            "2026-08-03T03:50:00Z",
            NseClosedBarSchedule.latestCompleted("5M", Instant.parse("2026-08-03T03:50:00Z"), weekdays)?.closeTimestampUtc,
        )
    }

    @Test fun hourlyBarsDoNotInventPartialSessionCloseBar() {
        assertEquals(
            "2026-08-03T09:45:00Z",
            NseClosedBarSchedule.latestCompleted("1H", Instant.parse("2026-08-03T10:30:00Z"), weekdays)?.closeTimestampUtc,
        )
    }

    @Test fun dailyBarWaitsForConfirmedSessionClose() {
        assertEquals(
            "2026-07-31T10:00:00Z",
            NseClosedBarSchedule.latestCompleted("D", Instant.parse("2026-08-03T09:59:59Z"), weekdays)?.closeTimestampUtc,
        )
        assertEquals(
            "2026-08-03T10:00:00Z",
            NseClosedBarSchedule.latestCompleted("D", Instant.parse("2026-08-03T10:00:00Z"), weekdays)?.closeTimestampUtc,
        )
    }

    @Test fun weeklyBarUsesHolidayShortenedFinalSessionOnlyAfterClose() {
        val fridayHoliday = TradingSessionCalendar { date ->
            when {
                date == LocalDate.parse("2026-08-07") -> false
                date.dayOfWeek in setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY) -> false
                else -> true
            }
        }
        assertEquals(
            "2026-07-31T10:00:00Z",
            NseClosedBarSchedule.latestCompleted("W", Instant.parse("2026-08-06T09:59:59Z"), fridayHoliday)?.closeTimestampUtc,
        )
        assertEquals(
            "2026-08-06T10:00:00Z",
            NseClosedBarSchedule.latestCompleted("W", Instant.parse("2026-08-06T10:00:00Z"), fridayHoliday)?.closeTimestampUtc,
        )
    }

    @Test fun unknownCalendarAndUnsupportedTimeframeFailClosed() {
        val unknown = TradingSessionCalendar { null }
        assertNull(NseClosedBarSchedule.latestCompleted("15M", Instant.parse("2026-08-03T05:00:00Z"), unknown))
        assertTrue(runCatching {
            NseClosedBarSchedule.latestCompleted("2H", Instant.parse("2026-08-03T05:00:00Z"), weekdays)
        }.isFailure)
    }
}
