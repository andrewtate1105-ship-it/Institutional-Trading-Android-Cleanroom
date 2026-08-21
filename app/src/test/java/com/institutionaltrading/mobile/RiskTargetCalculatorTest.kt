package com.institutionaltrading.mobile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RiskTargetCalculatorTest {
    @Test fun longTargetsAreOnePointFiveAndThreeR() {
        val targets = RiskTargetCalculator.calculate(CandidateDirection.LONG, entry = 100.0, stop = 98.0)
        assertEquals(103.0, targets.target1, 1e-9)
        assertEquals(106.0, targets.target2, 1e-9)
        assertEquals(3.0, targets.finalRewardToRisk, 1e-9)
    }

    @Test fun shortTargetsAreOnePointFiveAndThreeR() {
        val targets = RiskTargetCalculator.calculate(CandidateDirection.SHORT, entry = 100.0, stop = 102.0)
        assertEquals(97.0, targets.target1, 1e-9)
        assertEquals(94.0, targets.target2, 1e-9)
        assertEquals(3.0, targets.finalRewardToRisk, 1e-9)
    }

    @Test fun longRejectsOlderResistanceBeforeThreeR() {
        val blocked = listOf(bar(high = 104.0, low = 96.0, close = 100.0))
        assertFalse(RiskTargetCalculator.hasClearStructuralRoom(CandidateDirection.LONG, 100.0, 106.0, blocked))
    }

    @Test fun longAcceptsWhenPriorHighIsBeyondThreeR() {
        val clear = listOf(bar(high = 108.0, low = 96.0, close = 100.0))
        assertTrue(RiskTargetCalculator.hasClearStructuralRoom(CandidateDirection.LONG, 100.0, 106.0, clear))
    }

    @Test fun shortRejectsOlderSupportBeforeThreeR() {
        val blocked = listOf(bar(high = 104.0, low = 96.0, close = 100.0))
        assertFalse(RiskTargetCalculator.hasClearStructuralRoom(CandidateDirection.SHORT, 100.0, 94.0, blocked))
    }

    @Test fun shortAcceptsWhenPriorLowIsBeyondThreeR() {
        val clear = listOf(bar(high = 104.0, low = 92.0, close = 100.0))
        assertTrue(RiskTargetCalculator.hasClearStructuralRoom(CandidateDirection.SHORT, 100.0, 94.0, clear))
    }

    @Test fun nonDirectionalTargetFailsClosed() {
        assertTrue(runCatching { RiskTargetCalculator.calculate(CandidateDirection.NONE, 100.0, 99.0) }.isFailure)
    }

    private fun bar(high: Double, low: Double, close: Double) = MarketBar(
        symbol = "TEST",
        timeframe = "15M",
        sourceTimestamp = "2026-08-20T09:15:00Z",
        fetchedAt = "2026-08-20T09:16:00Z",
        open = close,
        high = high,
        low = low,
        close = close,
        isClosed = true,
        provenance = "TEST",
    )
}
