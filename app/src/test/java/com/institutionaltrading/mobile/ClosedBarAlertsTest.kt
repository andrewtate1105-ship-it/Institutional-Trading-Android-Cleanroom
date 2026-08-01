package com.institutionaltrading.mobile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ClosedBarAlertsTest {
    private fun alert(timestamp: String = "2026-08-01T09:45:00Z") = SignalAlert(
        direction = SignalDirection.NO_TRADE,
        symbol = "reliance",
        timeframe = "15M",
        sourceTimestamp = timestamp,
        entryZone = "N/A",
        invalidation = "No validated setup",
        stopLoss = "N/A",
        targets = emptyList(),
        rewardToRisk = "N/A",
        riskGuidance = "No position while evidence is incomplete.",
        confidenceDiagnostics = "Fail-closed diagnostic.",
        provenance = "official-public-source:test-fixture",
        warnings = listOf("Test fixture only"),
    )

    @Test fun deliversOnlyNewClosedBarKey() {
        val signal = alert()
        val empty = AlertDeliveryState()
        assertTrue(ClosedBarAlerts.shouldDeliver(signal, empty))
        val delivered = ClosedBarAlerts.markDelivered(signal, empty)
        assertFalse(ClosedBarAlerts.shouldDeliver(signal, delivered))
    }

    @Test fun rejectsUnavailableAndNonCanonicalTimestamps() {
        assertTrue(runCatching { ClosedBarAlerts.key(alert("UNAVAILABLE")) }.isFailure)
        assertTrue(runCatching { ClosedBarAlerts.key(alert("2026-08-01T15:15:00+05:30")) }.isFailure)
        assertTrue(runCatching { ClosedBarAlerts.key(alert("not-a-time")) }.isFailure)
    }

    @Test fun formatterIncludesEveryRequiredAlertField() {
        val text = AlertFormatter.telegram(alert())
        listOf(
            "Direction:", "Symbol:", "Timeframe:", "Source timestamp:", "Entry zone:",
            "Invalidation:", "Stop loss:", "Targets:", "Reward-to-risk:",
            "Position-risk guidance:", "Confidence diagnostics:", "Source provenance:", "Warnings:",
        ).forEach { assertTrue("Missing $it", text.contains(it)) }
        assertTrue(text.length <= 4096)
    }

    @Test fun alertStateRoundTripsAndRejectsDuplicates() {
        val state = ClosedBarAlerts.markDelivered(alert(), AlertDeliveryState())
        assertEquals(state, AlertStateCodec.decode(AlertStateCodec.encode(state)))
        val duplicateJson = """{"schema":1,"delivered":[{"symbol":"RELIANCE","timeframe":"15M","source_timestamp":"2026-08-01T09:45:00Z"},{"symbol":"RELIANCE","timeframe":"15M","source_timestamp":"2026-08-01T09:45:00Z"}]}"""
        assertTrue(runCatching { AlertStateCodec.decode(duplicateJson) }.isFailure)
    }

    @Test fun deliveryStateIsBounded() {
        var state = AlertDeliveryState()
        repeat(ClosedBarAlerts.maxRememberedKeys + 5) { index ->
            val timestamp = java.time.Instant.parse("2026-01-01T00:00:00Z").plusSeconds(index.toLong()).toString()
            state = ClosedBarAlerts.markDelivered(alert(timestamp), state)
        }
        assertEquals(ClosedBarAlerts.maxRememberedKeys, state.deliveredKeys.size)
    }
}
