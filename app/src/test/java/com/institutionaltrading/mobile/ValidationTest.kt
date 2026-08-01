package com.institutionaltrading.mobile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ValidationTest {
    private val allMarkets = setOf("NSE_EQUITY", "NSE_INDEX", "MCX_COMMODITY")

    @Test fun acceptsRequiredDefaults() {
        val profile = Validation.profile(
            OperatorProfile(
                "123456789",
                100000.0,
                1.0,
                allMarkets,
                setOf("5M", "15M", "1H", "D", "W"),
                setOf("INFY", "TCS"),
                5,
            )
        )
        assertEquals(5, profile.swingHoldingDays)
        assertEquals(setOf("5M", "15M", "1H", "D", "W"), Validation.defaultTimeframes)
    }

    @Test fun acceptsExactRequestedMultilineWatchlist() {
        val raw = """
            RELIANCE
            TCS
            HDFCBANK
            ICICIBANK
            SBIN
            LT
            INFY
            BHARTIARTL
            SUNPHARMA
            TATAMOTORS
            CUPID
            AEROFLEX
            NIFTY
            BANKNIFTY
            SENSEX
            CRUDEOILM
        """.trimIndent()
        val parsed = InputNormalizer.parseList(raw)
        val profile = Validation.profile(
            OperatorProfile("8850805173", 100000.0, 1.0, allMarkets, Validation.defaultTimeframes, parsed, 5)
        )
        assertEquals(16, profile.watchlist.size)
        assertTrue("CUPID" in profile.watchlist)
        assertTrue("AEROFLEX" in profile.watchlist)
        assertTrue("CRUDEOILM" in profile.watchlist)
    }

    @Test fun normalizesLowercaseAndIgnoresBlankLines() {
        val parsed = InputNormalizer.parseList("reliance\n\n tcs, cupid")
        val profile = Validation.profile(
            OperatorProfile("8850805173", 100000.0, 1.0, allMarkets, setOf("d"), parsed, 5)
        )
        assertEquals(setOf("RELIANCE", "TCS", "CUPID"), profile.watchlist)
        assertEquals(setOf("D"), profile.timeframes)
    }

    @Test fun officialNseQuoteUrlIsNormalizedToSymbol() {
        val profile = Validation.profile(
            OperatorProfile(
                "8850805173",
                100000.0,
                1.0,
                allMarkets,
                setOf("D"),
                setOf("https://www.nseindia.com/get-quotes/equity?symbol=infy"),
                5,
            )
        )
        assertEquals(setOf("INFY"), profile.watchlist)
    }

    @Test fun rejectsMalformedSymbolsAndUrls() {
        assertFalse(OfficialNseUrl.isWhitelistedQuoteUrl("http://nseindia.com/get-quotes/equity?symbol=INFY"))
        assertFalse(OfficialNseUrl.isWhitelistedQuoteUrl("https://evil.example/get-quotes/equity?symbol=INFY"))
        assertTrue(runCatching { Validation.normalizeWatchlistItem("BAD SYMBOL") }.isFailure)
        assertTrue(runCatching { Validation.normalizeWatchlistItem("INFY/../../") }.isFailure)
    }

    @Test fun acceptsUserTelegramNumericChatId() {
        val profile = Validation.profile(
            OperatorProfile("8850805173", 100000.0, 1.0, allMarkets, setOf("D"), setOf("INFY"), 5)
        )
        assertEquals("8850805173", profile.privateChatId)
    }

    @Test(expected = IllegalArgumentException::class) fun rejectsRiskAboveCap() {
        Validation.profile(
            OperatorProfile("123456789", 100000.0, 2.01, allMarkets, setOf("D"), setOf("INFY"), 5)
        )
    }

    @Test(expected = IllegalArgumentException::class) fun rejectsEmptyWatchlist() {
        Validation.profile(
            OperatorProfile("123456789", 100000.0, 1.0, allMarkets, setOf("D"), emptySet(), 5)
        )
    }

    @Test fun failClosedProducesNoTrade() {
        assertEquals(
            SignalDirection.NO_TRADE,
            SignalEngine.failClosed("INFY", "D", "none", "missing data").direction,
        )
    }
}
