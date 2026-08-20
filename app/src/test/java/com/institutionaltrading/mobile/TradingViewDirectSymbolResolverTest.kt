package com.institutionaltrading.mobile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TradingViewDirectSymbolResolverTest {
    @Test fun normalizesLimitedSuffixAndSpacing() {
        assertEquals(
            "ASHOK LEYLAND",
            TradingViewDirectSymbolResolver.normalizeName("  Ashok   Leyland Limited "),
        )
        assertEquals(
            "HDFC BANK",
            TradingViewDirectSymbolResolver.normalizeName("HDFC Bank Ltd."),
        )
    }

    @Test fun acceptsNseStocksAndIndices() {
        assertTrue(TradingViewDirectSymbolResolver.isSupportedNseInstrument("NSE", "NSE", "stock"))
        assertTrue(TradingViewDirectSymbolResolver.isSupportedNseInstrument("NSE", "NSE", "index"))
        assertTrue(TradingViewDirectSymbolResolver.isSupportedNseInstrument("", "NSE", "index"))
    }

    @Test fun rejectsUnsupportedOrNonNseResults() {
        assertFalse(TradingViewDirectSymbolResolver.isSupportedNseInstrument("NSE", "NSE", "futures"))
        assertFalse(TradingViewDirectSymbolResolver.isSupportedNseInstrument("NSE", "NSE", "fund"))
        assertFalse(TradingViewDirectSymbolResolver.isSupportedNseInstrument("BSE", "BSE", "stock"))
    }
}
