package com.institutionaltrading.mobile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ValidationTest {
    private val markets = setOf("NSE_EQUITY")
    private val timeframes = setOf("5M", "15M", "1H", "D", "W")

    private fun validChatId() = buildString {
        append('-')
        repeat(12) { append((it + 1) % 10) }
    }

    private fun phoneLikeValue() = buildString {
        append('9')
        repeat(9) { append('8') }
    }

    private fun profile(watchlist: Set<String>, chatId: String = validChatId()) = OperatorProfile(
        privateChatId = chatId,
        accountEquity = 100000.0,
        riskPercent = 1.0,
        markets = markets,
        timeframes = timeframes,
        watchlist = watchlist,
        swingHoldingDays = 5,
    )

    @Test fun acceptsExactTwelveStockWatchlist() {
        val expected = setOf(
            "RELIANCE", "TCS", "HDFCBANK", "ICICIBANK", "SBIN", "LT",
            "INFY", "BHARTIARTL", "SUNPHARMA", "TATAMOTORS", "CUPID", "AEROFLEX",
        )
        assertEquals(expected, Validation.profile(profile(expected)).watchlist)
    }

    @Test fun normalizesLowercaseAndMixedCaseSymbols() {
        val validated = Validation.profile(profile(setOf(" reliance ", "tCs", "M&M", "ABC-1", "FOO_BAR")))
        assertEquals(setOf("RELIANCE", "TCS", "M&M", "ABC-1", "FOO_BAR"), validated.watchlist)
    }

    @Test fun ignoresBlankLines() {
        val validated = Validation.profile(profile(setOf("INFY\n\n   \nTCS\r\n")))
        assertEquals(setOf("INFY", "TCS"), validated.watchlist)
    }

    @Test fun rejectsSymbolsWithInvalidSpaces() {
        val error = runCatching { Validation.profile(profile(setOf("HDFC BANK"))) }.exceptionOrNull()
        assertEquals("Malformed or unsupported watchlist item", error?.message)
    }

    @Test fun rejectsMalformedUrls() {
        assertFalse(OfficialNseUrl.isWhitelistedQuoteUrl("http://nseindia.com/get-quotes/equity?symbol=INFY"))
        assertFalse(OfficialNseUrl.isWhitelistedQuoteUrl("https://evil.example/get-quotes/equity?symbol=INFY"))
        assertFalse(OfficialNseUrl.isWhitelistedQuoteUrl("https://www.nseindia.com/get-quotes/equity"))
        assertFalse(OfficialNseUrl.isWhitelistedQuoteUrl("https://www.nseindia.com/get-quotes/equity/../equity?symbol=INFY"))
    }

    @Test fun acceptsOfficialNseQuoteUrlsAndNormalizesToSymbols() {
        val validated = Validation.profile(profile(setOf(
            " https://www.nseindia.com/get-quotes/equity?symbol=reliance ",
            "https://nseindia.com/get-quotes/derivatives?symbol=BANKNIFTY",
        )))
        assertEquals(setOf("RELIANCE", "BANKNIFTY"), validated.watchlist)
    }

    @Test fun acceptsAndClassifiesExplicitNonEquityInstruments() {
        val values = setOf("NIFTY", "BANKNIFTY", "SENSEX", "CRUDEOILM")
        assertEquals(values, Validation.profile(profile(values)).watchlist)
        assertEquals(InstrumentType.INDEX, Validation.instrumentType("NIFTY"))
        assertEquals(InstrumentType.INDEX, Validation.instrumentType("BANKNIFTY"))
        assertEquals(InstrumentType.INDEX, Validation.instrumentType("SENSEX"))
        assertEquals(InstrumentType.COMMODITY, Validation.instrumentType("CRUDEOILM"))
        assertEquals(InstrumentType.EQUITY, Validation.instrumentType("RELIANCE"))
    }

    @Test fun rejectsIndianPhoneNumberAsChatIdWithClearMessage() {
        val error = runCatching { Validation.profile(profile(setOf("INFY"), phoneLikeValue())) }.exceptionOrNull()
        assertEquals("Enter your Telegram Chat ID, not your phone number", error?.message)
    }

    @Test fun acceptsValidTelegramChatId() {
        assertEquals(validChatId(), Validation.profile(profile(setOf("INFY"))).privateChatId)
    }

    @Test fun preservesRequiredDefaultsAndFailClosedBehavior() {
        val validated = Validation.profile(profile(setOf("INFY")))
        assertEquals(5, validated.swingHoldingDays)
        assertEquals(setOf("5M", "15M", "1H", "D", "W"), Validation.defaultTimeframes)
        assertEquals(1.0, Validation.defaultRiskPercent, 0.0)
        assertEquals(2.0, Validation.hardRiskCapPercent, 0.0)
        assertEquals(SignalDirection.NO_TRADE, SignalEngine.failClosed("INFY", "D", "none", "missing data").direction)
    }

    @Test fun rejectsRiskAboveCap() {
        val invalid = profile(setOf("INFY")).copy(riskPercent = 2.01)
        assertTrue(runCatching { Validation.profile(invalid) }.isFailure)
    }

    @Test fun rejectsEmptyWatchlist() {
        assertTrue(runCatching { Validation.profile(profile(emptySet())) }.isFailure)
    }
}
