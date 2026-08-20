package com.institutionaltrading.mobile

import org.junit.Assert.assertTrue
import org.junit.Test

class TradingModePolicyTest {
    private val underlyingOnly = MarketDataCapabilities(
        closedOhlcBars = true,
        volume = false,
        derivativesContracts = false,
        openInterest = false,
        impliedVolatility = false,
        greeks = false,
    )

    @Test fun stocksModeIdentifiesUnderlyingSignal() {
        assertTrue(TradingModePolicy.resultNotice(TradingMode.STOCKS, underlyingOnly).contains("equity"))
    }

    @Test fun fnoModeNeverClaimsUnavailableDerivativeFields() {
        val notice = TradingModePolicy.resultNotice(TradingMode.F_AND_O, underlyingOnly)
        assertTrue(notice.contains("underlying"))
        assertTrue(notice.contains("not fabricated"))
        assertTrue(notice.contains("OI"))
        assertTrue(notice.contains("Greeks"))
    }
}
