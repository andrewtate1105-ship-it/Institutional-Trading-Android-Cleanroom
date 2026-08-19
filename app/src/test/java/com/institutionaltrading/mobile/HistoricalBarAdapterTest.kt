package com.institutionaltrading.mobile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HistoricalBarAdapterTest {
    private fun rows() = listOf(
        BacktestRow("2026-08-01T00:00:00Z", 100.0, 105.0, 99.0, 104.0, 98.0),
        BacktestRow("2026-08-02T00:00:00Z", 104.0, 108.0, 102.0, 107.0, 104.0),
    )

    @Test fun convertsValidatedRowsToClosedBars() {
        val series = HistoricalBarAdapter.toValidatedSeries(rows(), "reliance", "D")
        assertEquals("RELIANCE", series.symbol)
        assertEquals("D", series.timeframe)
        assertEquals(2, series.bars.size)
        assertTrue(series.bars.all { it.isClosed })
        assertTrue(series.bars.all { it.provenance == "USER_SUPPLIED_HISTORICAL_CSV" })
        assertEquals(series.bars.first().sourceTimestamp, series.bars.first().fetchedAt)
    }

    @Test fun rejectsUnsupportedTimeframe() {
        assertTrue(runCatching { HistoricalBarAdapter.toValidatedSeries(rows(), "RELIANCE", "2M") }.isFailure)
    }

    @Test fun rejectsNonChronologicalRows() {
        val reversed = rows().reversed()
        assertTrue(runCatching { HistoricalBarAdapter.toValidatedSeries(reversed, "RELIANCE", "D") }.isFailure)
    }

    @Test fun rejectsBlankProvenance() {
        assertTrue(runCatching { HistoricalBarAdapter.toValidatedSeries(rows(), "RELIANCE", "D", " ") }.isFailure)
    }
}
