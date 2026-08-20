package com.institutionaltrading.mobile

import org.junit.Assert.assertFalse
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

    @Test fun stocksModeAllowsClosedUnderlyingBars() {
        assertTrue(TradingModePolicy.canAnalyze(TradingMode.STOCKS, underlyingOnly))
        assertTrue(TradingModePolicy.resultNotice(TradingMode.STOCKS, underlyingOnly).contains("equity"))
    }

    @Test fun fnoModeFailsClosedWithoutDerivativeContracts() {
        assertFalse(TradingModePolicy.canAnalyze(TradingMode.F_AND_O, underlyingOnly))
        val notice = TradingModePolicy.resultNotice(TradingMode.F_AND_O, underlyingOnly)
        assertTrue(notice.startsWith("NO TRADE"))
        assertTrue(notice.contains("not fabricated"))
        assertTrue(notice.contains("OI"))
        assertTrue(notice.contains("Greeks"))
    }

    @Test fun fnoModeCanAnalyzeWhenVerifiedContractsExist() {
        val derivatives = underlyingOnly.copy(derivativesContracts = true)
        assertTrue(TradingModePolicy.canAnalyze(TradingMode.F_AND_O, derivatives))
        assertTrue(TradingModePolicy.resultNotice(TradingMode.F_AND_O, derivatives).contains("available"))
    }
}
