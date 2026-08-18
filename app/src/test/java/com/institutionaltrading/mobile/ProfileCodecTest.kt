package com.institutionaltrading.mobile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertFalse as assertNotPresent
import org.junit.Test

class ProfileCodecTest {
    private val profile = OperatorProfile(
        accountEquity = 250000.0,
        riskPercent = 1.0,
        markets = setOf("NSE_EQUITY", "NSE_INDEX", "MCX_COMMODITY"),
        timeframes = setOf("5M", "15M", "1H", "D", "W"),
        watchlist = setOf("RELIANCE", "CUPID", "AEROFLEX", "NIFTY", "BANKNIFTY", "SENSEX", "CRUDEOILM"),
        swingHoldingDays = 5,
    )

    @Test fun roundTripsValidatedProfile() {
        assertEquals(profile, ProfileCodec.decode(ProfileCodec.encode(profile)))
    }

    @Test fun encodedProfileKeepsBrokerExecutionDisabledAndContainsNoMessagingIdentity() {
        val json = org.json.JSONObject(ProfileCodec.encode(profile))
        assertFalse(json.getBoolean("broker_execution_enabled"))
        assertNotPresent(json.has("private_chat_id"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsExecutionEnabledProfile() {
        val json = org.json.JSONObject(ProfileCodec.encode(profile))
            .put("broker_execution_enabled", true)
        ProfileCodec.decode(json.toString())
    }
}
