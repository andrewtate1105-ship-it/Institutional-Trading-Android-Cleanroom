package com.institutionaltrading.mobile

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LatestBarFreshnessTest {
    private fun series(latest: String, timeframe: String = "15M") = ValidatedBarSeries(
        symbol = "ASHOKLEY",
        timeframe = timeframe,
        bars = listOf(
            MarketBar(
                symbol = "ASHOKLEY",
                timeframe = timeframe,
                sourceTimestamp = latest,
                fetchedAt = latest,
                open = 100.0,
                high = 102.0,
                low = 99.0,
                close = 101.0,
                isClosed = true,
                provenance = "USER_SUPPLIED_CLOSED_BAR_CSV",
            )
        ),
    )

    @Test fun freshLatestBarIsAccepted() {
        val now = Instant.parse("2026-08-19T04:00:00Z")
        val validated = LatestBarFreshness.requireCurrent(
            series("2026-08-19T03:30:00Z"),
            now,
        )
        assertEquals("ASHOKLEY", validated.symbol)
    }

    @Test fun staleIntradayBarIsRejected() {
        val now = Instant.parse("2026-08-19T04:00:00Z")
        val failure = runCatching {
            LatestBarFreshness.requireCurrent(series("2026-08-19T03:00:00Z"), now)
        }
        assertTrue(failure.isFailure)
        assertTrue(failure.exceptionOrNull()?.message.orEmpty().contains("stale"))
    }

    @Test fun futureBarIsRejected() {
        val now = Instant.parse("2026-08-19T04:00:00Z")
        val failure = runCatching {
            LatestBarFreshness.requireCurrent(series("2026-08-19T04:01:00Z"), now)
        }
        assertTrue(failure.isFailure)
        assertTrue(failure.exceptionOrNull()?.message.orEmpty().contains("future"))
    }

    @Test fun freshnessWindowsMatchSupportedTimeframes() {
        assertEquals(15L, LatestBarFreshness.maxAgeFor("5M").toMinutes())
        assertEquals(45L, LatestBarFreshness.maxAgeFor("15M").toMinutes())
        assertEquals(180L, LatestBarFreshness.maxAgeFor("1H").toMinutes())
        assertEquals(4320L, LatestBarFreshness.maxAgeFor("D").toMinutes())
        assertEquals(20160L, LatestBarFreshness.maxAgeFor("W").toMinutes())
    }
}
