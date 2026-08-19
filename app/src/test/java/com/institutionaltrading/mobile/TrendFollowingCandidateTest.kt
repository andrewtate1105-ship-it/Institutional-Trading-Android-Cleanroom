package com.institutionaltrading.mobile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TrendFollowingCandidateTest {
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
        provenance = "USER_SUPPLIED_HISTORICAL_CSV",
    )

    @Test fun insufficientStructureProducesNoCandidate() {
        val series = ValidatedBarSeries("RELIANCE", "15M", (0..5).map(::bar))
        val candidate = TrendFollowingCandidate.evaluate(series)
        assertEquals(CandidateDirection.NONE, candidate.direction)
        assertTrue(candidate.entryPrice == null)
        assertTrue(candidate.structuralStop == null)
    }

    @Test fun formingBarIsRejected() {
        val series = ValidatedBarSeries("RELIANCE", "15M", (0..5).map { bar(it, closed = it != 5) })
        assertTrue(runCatching { TrendFollowingCandidate.evaluate(series) }.isFailure)
    }
}
