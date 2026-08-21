package com.institutionaltrading.mobile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdaptiveStopCalculatorTest {
    @Test fun longStopUsesWiderOfAtrAndStructure() {
        val result = AdaptiveStopCalculator.calculate(
            direction = CandidateDirection.LONG,
            entry = 100.0,
            structuralStop = 99.0,
            atr = 1.5,
        )
        assertEquals(98.5, result.stop, 1e-9)
        assertEquals(1.5, result.riskPercent, 1e-9)
        assertTrue(result.withinRiskGuardrail)
    }

    @Test fun structuralStopBeyondTwoPercentIsNotTradeReady() {
        val result = AdaptiveStopCalculator.calculate(
            direction = CandidateDirection.LONG,
            entry = 100.0,
            structuralStop = 96.0,
            atr = 1.0,
        )
        assertEquals(96.0, result.stop, 1e-9)
        assertEquals(4.0, result.riskPercent, 1e-9)
        assertFalse(result.withinRiskGuardrail)
    }

    @Test fun waitStopNeverExceedsTwoPercent() {
        val result = AdaptiveStopCalculator.cappedForWait(
            direction = CandidateDirection.SHORT,
            entry = 100.0,
            atr = 4.0,
        )
        assertEquals(102.0, result.stop, 1e-9)
        assertEquals(2.0, result.riskPercent, 1e-9)
        assertTrue(result.withinRiskGuardrail)
    }
}
