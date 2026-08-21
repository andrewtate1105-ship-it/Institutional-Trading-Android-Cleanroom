package com.institutionaltrading.mobile

import java.time.Duration
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MarketDataQualityTest {
    private val now = Instant.parse("2026-08-01T10:00:00Z")

    private fun bar(
        symbol: String = "reliance",
        timestamp: String = "2026-08-01T09:45:00Z",
        fetchedAt: String = "2026-08-01T09:46:00Z",
        open: Double = 100.0,
        high: Double = 105.0,
        low: Double = 99.0,
        close: Double = 104.0,
        closed: Boolean = true,
    ) = MarketBar(
        symbol = symbol,
        timeframe = "15M",
        sourceTimestamp = timestamp,
        fetchedAt = fetchedAt,
        open = open,
        high = high,
        low = low,
        close = close,
        isClosed = closed,
        provenance = "official-public-source:test-fixture",
    )

    @Test fun acceptsValidClosedBarAndNormalizesSymbol() {
        val validated = MarketDataQuality.validateBar(bar(), now, Duration.ofMinutes(30))
        assertEquals("RELIANCE", validated.symbol)
    }

    @Test fun rejectsFormingStaleFutureAndMalformedTimestamps() {
        assertTrue(runCatching {
            MarketDataQuality.validateBar(bar(closed = false), now, Duration.ofMinutes(30))
        }.isFailure)
        assertTrue(runCatching {
            MarketDataQuality.validateBar(
                bar(timestamp = "2026-08-01T08:00:00Z"), now, Duration.ofMinutes(30)
            )
        }.isFailure)
        assertTrue(runCatching {
            MarketDataQuality.validateBar(
                bar(fetchedAt = "2026-08-01T10:01:00Z"), now, Duration.ofMinutes(30)
            )
        }.isFailure)
        assertTrue(runCatching {
            MarketDataQuality.validateBar(
                bar(timestamp = "2026-08-01 09:45:00"), now, Duration.ofMinutes(30)
            )
        }.isFailure)
    }

    @Test fun rejectsInvalidOhlcAndNumericalValues() {
        assertTrue(runCatching {
            MarketDataQuality.validateBar(bar(high = 103.0, close = 104.0), now, Duration.ofMinutes(30))
        }.isFailure)
        assertTrue(runCatching {
            MarketDataQuality.validateBar(bar(low = 101.0, open = 100.0), now, Duration.ofMinutes(30))
        }.isFailure)
        assertTrue(runCatching {
            MarketDataQuality.validateBar(bar(open = Double.NaN), now, Duration.ofMinutes(30))
        }.isFailure)
        assertTrue(runCatching {
            MarketDataQuality.validateBar(bar(close = 0.0), now, Duration.ofMinutes(30))
        }.isFailure)
    }

    @Test fun rejectsEmptyTinyDuplicateUnorderedAndMixedSeries() {
        assertTrue(runCatching {
            MarketDataQuality.validateSeries(emptyList(), now, Duration.ofHours(1), minimumSamples = 2)
        }.isFailure)
        assertTrue(runCatching {
            MarketDataQuality.validateSeries(listOf(bar()), now, Duration.ofHours(1), minimumSamples = 2)
        }.isFailure)
        val duplicate = listOf(bar(), bar())
        assertTrue(runCatching {
            MarketDataQuality.validateSeries(duplicate, now, Duration.ofHours(1), minimumSamples = 2)
        }.isFailure)
        val unordered = listOf(
            bar(timestamp = "2026-08-01T09:45:00Z"),
            bar(timestamp = "2026-08-01T09:30:00Z", fetchedAt = "2026-08-01T09:46:00Z"),
        )
        assertTrue(runCatching {
            MarketDataQuality.validateSeries(unordered, now, Duration.ofHours(1), minimumSamples = 2)
        }.isFailure)
        val mixed = listOf(
            bar(timestamp = "2026-08-01T09:30:00Z"),
            bar(symbol = "TCS", timestamp = "2026-08-01T09:45:00Z"),
        )
        assertTrue(runCatching {
            MarketDataQuality.validateSeries(mixed, now, Duration.ofHours(1), minimumSamples = 2)
        }.isFailure)
    }

    @Test fun acceptsStrictlyIncreasingValidatedSeries() {
        val series = MarketDataQuality.validateSeries(
            listOf(
                bar(timestamp = "2026-08-01T09:30:00Z", fetchedAt = "2026-08-01T09:31:00Z"),
                bar(timestamp = "2026-08-01T09:45:00Z", fetchedAt = "2026-08-01T09:46:00Z"),
            ),
            now,
            Duration.ofHours(1),
            minimumSamples = 2,
        )
        assertEquals(2, series.bars.size)
        assertEquals("RELIANCE", series.symbol)
        assertEquals("15M", series.timeframe)
    }
}
