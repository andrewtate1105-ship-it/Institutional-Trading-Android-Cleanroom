package com.institutionaltrading.mobile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MarketStructureTest {
    private fun bar(index: Int, high: Double, low: Double, closed: Boolean = true) = MarketBar(
        symbol = "RELIANCE",
        timeframe = "15M",
        sourceTimestamp = java.time.Instant.parse("2026-01-01T00:00:00Z").plusSeconds(index * 900L).toString(),
        fetchedAt = java.time.Instant.parse("2026-01-01T00:00:00Z").plusSeconds(index * 900L + 30L).toString(),
        open = (high + low) / 2.0,
        high = high,
        low = low,
        close = (high + low) / 2.0,
        isClosed = closed,
        provenance = "test",
    )

    @Test fun detectsOnlyConfirmedFiveBarMinorSwing() {
        val highs = listOf(10.0, 11.0, 15.0, 12.0, 11.0, 10.0)
        val lows = listOf(5.0, 6.0, 7.0, 6.5, 6.0, 5.5)
        val series = ValidatedBarSeries("RELIANCE", "15M", highs.indices.map { bar(it, highs[it], lows[it]) })
        val swings = MarketStructure.confirmedSwings(series)
        assertTrue(swings.any { it.index == 2 && it.type == SwingType.HIGH && it.price == 15.0 })
        assertTrue(swings.none { it.index >= highs.size - MarketStructure.minorRight })
    }

    @Test fun strictComparisonRejectsEqualHighPlateau() {
        val highs = listOf(10.0, 12.0, 15.0, 15.0, 12.0, 10.0)
        val lows = listOf(5.0, 6.0, 7.0, 7.0, 6.0, 5.0)
        val series = ValidatedBarSeries("RELIANCE", "15M", highs.indices.map { bar(it, highs[it], lows[it]) })
        assertTrue(MarketStructure.confirmedSwings(series).none { it.type == SwingType.HIGH })
    }

    @Test fun rejectsAnySeriesContainingFormingBar() {
        val bars = (0..5).map { index -> bar(index, 10.0 + index, 5.0 + index, closed = index != 5) }
        val series = ValidatedBarSeries("RELIANCE", "15M", bars)
        assertTrue(runCatching { MarketStructure.confirmedSwings(series) }.isFailure)
    }

    @Test fun zigzagKeepsMostExtremeConsecutiveSameTypeMajorSwing() {
        val major = listOf(
            SwingPoint(5, "2026-01-01T01:15:00Z", 100.0, SwingType.HIGH, SwingTier.INTERNAL),
            SwingPoint(8, "2026-01-01T02:00:00Z", 105.0, SwingType.HIGH, SwingTier.INTERNAL),
            SwingPoint(12, "2026-01-01T03:00:00Z", 90.0, SwingType.LOW, SwingTier.INTERNAL),
            SwingPoint(15, "2026-01-01T03:45:00Z", 92.0, SwingType.LOW, SwingTier.INTERNAL),
        )
        val external = MarketStructure.externalSequence(major)
        assertEquals(2, external.size)
        assertEquals(105.0, external[0].price, 0.0)
        assertEquals(SwingType.HIGH, external[0].type)
        assertEquals(90.0, external[1].price, 0.0)
        assertEquals(SwingType.LOW, external[1].type)
        assertTrue(external.all { it.tier == SwingTier.EXTERNAL })
    }
}
