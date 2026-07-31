package com.institutionaltrading.mobile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ValidationTest {
    @Test fun acceptsRequiredDefaults() {
        val profile = Validation.profile(OperatorProfile("123456789",100000.0,1.0,setOf("NSE_EQUITY"),setOf("5M","15M","1H","D","W"),setOf("INFY","TCS"),5))
        assertEquals(5, profile.swingHoldingDays)
        assertEquals(setOf("5M","15M","1H","D","W"), Validation.defaultTimeframes)
    }

    @Test(expected = IllegalArgumentException::class) fun rejectsRiskAboveCap() {
        Validation.profile(OperatorProfile("123456789",100000.0,2.01,setOf("NSE_EQUITY"),setOf("D"),setOf("INFY"),5))
    }

    @Test(expected = IllegalArgumentException::class) fun rejectsEmptyWatchlist() {
        Validation.profile(OperatorProfile("123456789",100000.0,1.0,setOf("NSE_EQUITY"),setOf("D"),emptySet(),5))
    }

    @Test fun rejectsMalformedNseSymbolsAndUrls() {
        assertFalse(OfficialNseUrl.isWhitelistedQuoteUrl("http://nseindia.com/get-quotes/equity?symbol=INFY"))
        assertFalse(OfficialNseUrl.isWhitelistedQuoteUrl("https://evil.example/get-quotes/equity?symbol=INFY"))
        val rejected = runCatching {
            Validation.profile(OperatorProfile("123456789",100000.0,1.0,setOf("NSE_EQUITY"),setOf("D"),setOf("INFY/../../"),5))
        }.isFailure
        assertTrue(rejected)
    }

    @Test fun failClosedProducesNoTrade() {
        assertEquals(SignalDirection.NO_TRADE, SignalEngine.failClosed("INFY", "D", "none", "missing data").direction)
    }
}
