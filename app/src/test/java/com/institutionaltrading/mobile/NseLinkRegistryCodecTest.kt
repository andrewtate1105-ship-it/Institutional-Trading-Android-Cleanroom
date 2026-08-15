package com.institutionaltrading.mobile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NseLinkRegistryCodecTest {
    private val allowed = setOf("RELIANCE", "TCS")

    @Test fun normalizesSupportedOfficialUrls() {
        val links = NseLinkRegistryCodec.normalize(
            listOf(
                "https://www.nseindia.com/get-quotes/equity?symbol=RELIANCE",
                "https://www.nseindia.com/get-quotes/equity?symbol=TCS",
            ),
            allowed,
        )
        assertEquals(listOf("RELIANCE", "TCS"), links.map { it.symbol })
    }

    @Test fun rejectsUrlsOutsideSavedWatchlist() {
        val result = runCatching {
            NseLinkRegistryCodec.normalize(
                listOf("https://www.nseindia.com/get-quotes/equity?symbol=INFY"),
                allowed,
            )
        }
        assertTrue(result.isFailure)
    }

    @Test fun rejectsDuplicateSymbolLinks() {
        val result = runCatching {
            NseLinkRegistryCodec.normalize(
                listOf(
                    "https://www.nseindia.com/get-quotes/equity?symbol=RELIANCE",
                    "https://nseindia.com/get-quotes/equity?symbol=RELIANCE",
                ),
                allowed,
            )
        }
        assertTrue(result.isFailure)
    }

    @Test fun roundTripsRegistry() {
        val original = NseLinkRegistryCodec.normalize(
            listOf("https://www.nseindia.com/get-quotes/equity?symbol=RELIANCE"),
            allowed,
        )
        val decoded = NseLinkRegistryCodec.decode(NseLinkRegistryCodec.encode(original), allowed)
        assertEquals(original, decoded)
    }
}
