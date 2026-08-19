package com.institutionaltrading.mobile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InstrumentIdentityResolverTest {
    private val ashokLeylandUrl = "https://www.nseindia.com/get-quote/equity/ASHOKLEY/Ashok-Leyland-Limited"

    @Test fun acceptsHumanCompanyNameAndResolvesCanonicalSymbol() {
        val identity = InstrumentIdentityResolver.resolve("Ashok Leyland Limited", ashokLeylandUrl)
        assertEquals("Ashok Leyland Limited", identity.displayName)
        assertEquals("ASHOKLEY", identity.symbol)
    }

    @Test fun acceptsMatchingSymbol() {
        val identity = InstrumentIdentityResolver.resolve("ashokley", ashokLeylandUrl)
        assertEquals("ASHOKLEY", identity.symbol)
    }

    @Test fun rejectsMismatchedSymbol() {
        val result = runCatching {
            InstrumentIdentityResolver.resolve("RELIANCE", ashokLeylandUrl)
        }
        assertTrue(result.isFailure)
    }

    @Test fun rejectsBlankOrUnsupportedUrl() {
        assertTrue(runCatching { InstrumentIdentityResolver.resolve("", ashokLeylandUrl) }.isFailure)
        assertTrue(
            runCatching {
                InstrumentIdentityResolver.resolve(
                    "Ashok Leyland Limited",
                    "https://example.com/get-quote/equity/ASHOKLEY/Ashok-Leyland-Limited",
                )
            }.isFailure
        )
    }
}
