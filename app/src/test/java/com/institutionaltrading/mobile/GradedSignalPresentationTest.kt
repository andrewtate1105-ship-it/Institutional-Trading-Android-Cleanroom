package com.institutionaltrading.mobile

import org.junit.Assert.assertTrue
import org.junit.Test

class GradedSignalPresentationTest {
    @Test fun waitStateShowsTriggerDiagnosticsAndThreeRObjective() {
        val result = InAppSignalResult(
            setupState = SetupState.WAIT,
            direction = SignalDirection.LONG,
            symbol = "RELIANCE",
            timeframe = "15M",
            sourceTimestamp = "2026-08-21T06:00:00Z",
            entry = 1400.0,
            stopLoss = 1386.0,
            target1 = 1421.0,
            target2 = 1442.0,
            rewardToRisk = 3.0,
            riskPercentOfPrice = 1.0,
            quantity = 0,
            estimatedAccountRisk = 0.0,
            reason = "Bias established; breakout confirmation pending",
            provenance = "TEST",
            score = 7,
            emaAlignment = "BULLISH ✓",
            rsi14 = 61.0,
            support = 1388.0,
            resistance = 1400.0,
            invalidation = "No entry until breakout confirms",
        )

        val formatted = InAppSignalEngine.format(result)
        assertTrue(formatted.contains("SETUP: WAIT"))
        assertTrue(formatted.contains("BIAS: LONG"))
        assertTrue(formatted.contains("SCORE: 7/10"))
        assertTrue(formatted.contains("ENTRY TRIGGER: ₹1400.00"))
        assertTrue(formatted.contains("EXIT 2 / FINAL (3R): ₹1442.00"))
        assertTrue(formatted.contains("INVALIDATION:"))
    }
}
