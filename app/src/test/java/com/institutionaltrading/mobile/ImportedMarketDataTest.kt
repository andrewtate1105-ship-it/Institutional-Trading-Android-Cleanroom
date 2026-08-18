package com.institutionaltrading.mobile

import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ImportedMarketDataTest {
    private val rows = listOf(
        BacktestRow(
            timestamp = "2026-08-18T09:15:00Z",
            open = 100.0,
            high = 101.0,
            low = 99.0,
            close = 100.5,
            previousClose = 100.0,
        )
    )

    @Test fun matchingInstrumentAndTimeframeReturnsOriginalRows() {
        val data = ImportedMarketData(rows, "ASHOKLEY", "15M")
        assertSame(rows, data.requireMatches("ashokley", "15m"))
    }

    @Test fun symbolMismatchFailsClosed() {
        val data = ImportedMarketData(rows, "ASHOKLEY", "15M")
        val error = runCatching { data.requireMatches("RELIANCE", "15M") }.exceptionOrNull()
        assertTrue(error is IllegalArgumentException)
        assertTrue(error?.message?.contains("Re-import data for the selected stock") == true)
    }

    @Test fun timeframeMismatchFailsClosed() {
        val data = ImportedMarketData(rows, "ASHOKLEY", "15M")
        val error = runCatching { data.requireMatches("ASHOKLEY", "1H") }.exceptionOrNull()
        assertTrue(error is IllegalArgumentException)
        assertTrue(error?.message?.contains("Re-import data for the selected timeframe") == true)
    }
}
