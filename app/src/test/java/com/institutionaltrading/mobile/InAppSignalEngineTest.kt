package com.institutionaltrading.mobile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InAppSignalEngineTest {
    private fun bar(index: Int, closed: Boolean = true) = MarketBar(
        symbol = "RELIANCE",
        timeframe = "15M",
        sourceTimestamp = java.time.Instant.parse("2026-01-01T00:00:00Z").plusSeconds(index * 900L).toString(),
        fetchedAt = java.time.Instant.parse("2026-01-01T00:00:00Z").plusSeconds(index * 900L + 30L).toString(),
        open = 100.0 + index,
        high = 101.0 + index,
        low = 99.0 + index,
        close = 100.5 + index,
        isClosed = closed,
        provenance = "USER_SUPPLIED_CLOSED_BAR_CSV",
    )

    @Test fun insufficientStructureFailsClosedToNoTrade() {
        val result = InAppSignalEngine.analyze(
            series = ValidatedBarSeries("RELIANCE", "15M", (0..5).map(::bar)),
            accountEquity = 10000.0,
            accountRiskPercent = 1.0,
        )
        assertEquals(SignalDirection.NO_TRADE, result.direction)
        assertEquals(0L, result.quantity)
        assertTrue(result.entry == null)
        assertTrue(result.reason.contains("Insufficient closed-bar history"))
        assertTrue(InAppSignalEngine.format(result).startsWith("SIGNAL: NO_TRADE"))
    }

    @Test fun exactlyTwoHundredClosedBarsPassHistoryGate() {
        val series = ValidatedBarSeries("RELIANCE", "15M", (0 until 200).map(::bar))
        val result = InAppSignalEngine.analyze(series, 100000.0, 1.0)
        assertTrue(!result.reason.contains("Insufficient closed-bar history"))
    }

    @Test fun formingBarIsRejected() {
        val series = ValidatedBarSeries("RELIANCE", "15M", (0..5).map { bar(it, closed = it != 5) })
        assertTrue(runCatching { InAppSignalEngine.analyze(series, 10000.0, 1.0) }.isFailure)
    }

    @Test fun accountRiskBelowRequiredBandIsRejected() {
        val series = ValidatedBarSeries("RELIANCE", "15M", (0..5).map(::bar))
        val failure = runCatching { InAppSignalEngine.analyze(series, 10000.0, 0.99) }
        assertTrue(failure.isFailure)
        assertTrue(failure.exceptionOrNull()?.message.orEmpty().contains("between 1% and 2%"))
    }

    @Test fun accountRiskAboveHardCapIsRejected() {
        val series = ValidatedBarSeries("RELIANCE", "15M", (0..5).map(::bar))
        assertTrue(runCatching { InAppSignalEngine.analyze(series, 10000.0, 2.01) }.isFailure)
    }

    @Test fun nonFiniteAccountRiskIsRejected() {
        val series = ValidatedBarSeries("RELIANCE", "15M", (0..5).map(::bar))
        assertTrue(runCatching { InAppSignalEngine.analyze(series, 10000.0, Double.NaN) }.isFailure)
    }
}
