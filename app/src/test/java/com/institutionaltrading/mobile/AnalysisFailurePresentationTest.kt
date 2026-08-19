package com.institutionaltrading.mobile

import org.junit.Assert.assertTrue
import org.junit.Test

class AnalysisFailurePresentationTest {
    @Test fun unavailableMarketDataIsExplicitlyShownAsNoTrade() {
        val output = AnalysisFailurePresentation.format(
            MarketDataUnavailableException("Latest closed bar is stale")
        )

        assertTrue(output.startsWith("SIGNAL: NO TRADE"))
        assertTrue(output.contains("STATUS: DATA UNAVAILABLE"))
        assertTrue(output.contains("Latest closed bar is stale"))
    }

    @Test fun invalidUserInputIsDistinguishedFromMissingData() {
        val output = AnalysisFailurePresentation.format(
            IllegalArgumentException("Risk percent must be between 1% and 2%")
        )

        assertTrue(output.startsWith("SIGNAL: NO TRADE"))
        assertTrue(output.contains("STATUS: INPUT REJECTED"))
        assertTrue(output.contains("Risk percent must be between 1% and 2%"))
    }
}
