package com.institutionaltrading.mobile

import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Computes expected NSE cash-session bar closes without claiming that a date is an exchange holiday.
 * Callers must supply a lawful session calendar. Missing calendar confirmation fails closed.
 */
fun interface TradingSessionCalendar {
    fun isTradingSession(date: LocalDate): Boolean?
}

data class ClosedBarSlot(
    val timeframe: String,
    val closeTimestampUtc: String,
)

object NseClosedBarSchedule {
    val zone: ZoneId = ZoneId.of("Asia/Kolkata")
    val sessionOpen: LocalTime = LocalTime.of(9, 15)
    val sessionClose: LocalTime = LocalTime.of(15, 30)

    fun latestCompleted(
        timeframe: String,
        now: Instant,
        calendar: TradingSessionCalendar,
    ): ClosedBarSlot? {
        require(timeframe in Validation.defaultTimeframes) { "Unsupported timeframe" }
        val localNow = now.atZone(zone)
        return when (timeframe) {
            "5M" -> latestIntraday(localNow, Duration.ofMinutes(5), calendar)
            "15M" -> latestIntraday(localNow, Duration.ofMinutes(15), calendar)
            "1H" -> latestIntraday(localNow, Duration.ofHours(1), calendar)
            "D" -> latestDaily(localNow, calendar)
            "W" -> latestWeekly(localNow, calendar)
            else -> null
        }?.let { ClosedBarSlot(timeframe, it.toInstant().toString()) }
    }

    private fun latestIntraday(
        now: ZonedDateTime,
        interval: Duration,
        calendar: TradingSessionCalendar,
    ): ZonedDateTime? {
        val date = now.toLocalDate()
        if (calendar.isTradingSession(date) != true) return null
        val open = date.atTime(sessionOpen).atZone(zone)
        val close = date.atTime(sessionClose).atZone(zone)
        if (now.isBefore(open.plus(interval))) return null
        val capped = if (now.isAfter(close)) close else now
        val completed = Duration.between(open, capped).seconds / interval.seconds
        if (completed <= 0L) return null
        val candidate = open.plusSeconds(completed * interval.seconds)
        return candidate.takeUnless { it.isAfter(close) }
    }

    private fun latestDaily(now: ZonedDateTime, calendar: TradingSessionCalendar): ZonedDateTime? {
        var date = now.toLocalDate()
        when (calendar.isTradingSession(date)) {
            null -> return null
            true -> {
                val close = date.atTime(sessionClose).atZone(zone)
                if (!now.isBefore(close)) return close
            }
            false -> Unit
        }
        date = date.minusDays(1)
        repeat(10) {
            when (calendar.isTradingSession(date)) {
                true -> return date.atTime(sessionClose).atZone(zone)
                null -> return null
                false -> date = date.minusDays(1)
            }
        }
        return null
    }

    private fun latestWeekly(now: ZonedDateTime, calendar: TradingSessionCalendar): ZonedDateTime? {
        var friday = now.toLocalDate().with(DayOfWeek.FRIDAY)
        repeat(3) {
            val finalSession = finalConfirmedSessionOnOrBefore(friday, calendar) ?: return null
            val close = finalSession.atTime(sessionClose).atZone(zone)
            if (!now.isBefore(close)) return close
            friday = friday.minusWeeks(1)
        }
        return null
    }

    private fun finalConfirmedSessionOnOrBefore(
        friday: LocalDate,
        calendar: TradingSessionCalendar,
    ): LocalDate? {
        var date = friday
        repeat(5) {
            when (calendar.isTradingSession(date)) {
                true -> return date
                null -> return null
                false -> date = date.minusDays(1)
            }
        }
        return null
    }
}
