package com.institutionaltrading.mobile

import org.junit.Assert.assertEquals
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
        assertEquals(TradingDataTier.UNDERLYING_ONLY, TradingModePolicy.dataTier(TradingMode.STOCKS, underlyingOnly))
        assertTrue(TradingModePolicy.resultNotice(TradingMode.STOCKS, underlyingOnly).contains("equity"))
    }

    @Test fun fnoModeCanProvideUnderlyingDirectionalViewWithoutInventingContracts() {
        assertTrue(TradingModePolicy.canAnalyze(TradingMode.F_AND_O, underlyingOnly))
        assertFalse(TradingModePolicy.hasVerifiedDerivativeContracts(underlyingOnly))
        assertEquals(TradingDataTier.UNDERLYING_ONLY, TradingModePolicy.dataTier(TradingMode.F_AND_O, underlyingOnly))
        val notice = TradingModePolicy.resultNotice(TradingMode.F_AND_O, underlyingOnly)
        assertTrue(notice.contains("directional view"))
        assertTrue(notice.contains("underlying"))
        assertTrue(notice.contains("no futures/options contract"))
        assertTrue(notice.contains("OI"))
        assertTrue(notice.contains("Greeks"))
    }

    @Test fun fnoModeCanUseVerifiedContractsWhenProviderSupportsThem() {
        val derivatives = underlyingOnly.copy(derivativesContracts = true)
        assertTrue(TradingModePolicy.canAnalyze(TradingMode.F_AND_O, derivatives))
        assertTrue(TradingModePolicy.hasVerifiedDerivativeContracts(derivatives))
        assertEquals(TradingDataTier.VERIFIED_DERIVATIVES, TradingModePolicy.dataTier(TradingMode.F_AND_O, derivatives))
        assertTrue(TradingModePolicy.resultNotice(TradingMode.F_AND_O, derivatives).contains("available"))
    }

    @Test fun bothModesFailClosedWithoutClosedBars() {
        val unavailable = underlyingOnly.copy(closedOhlcBars = false, derivativesContracts = true)
        assertFalse(TradingModePolicy.canAnalyze(TradingMode.STOCKS, unavailable))
        assertFalse(TradingModePolicy.canAnalyze(TradingMode.F_AND_O, unavailable))
        assertEquals(TradingDataTier.UNAVAILABLE, TradingModePolicy.dataTier(TradingMode.STOCKS, unavailable))
        assertEquals(TradingDataTier.UNAVAILABLE, TradingModePolicy.dataTier(TradingMode.F_AND_O, unavailable))
    }
}
