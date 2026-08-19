package com.institutionaltrading.mobile

import org.junit.Assert.assertEquals
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
}
