package com.institutionaltrading.mobile

import org.junit.Assert.assertEquals
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

    @Test fun nonDirectionalTargetFailsClosed() {
        assertTrue(runCatching { RiskTargetCalculator.calculate(CandidateDirection.NONE, 100.0, 99.0) }.isFailure)
    }
}
