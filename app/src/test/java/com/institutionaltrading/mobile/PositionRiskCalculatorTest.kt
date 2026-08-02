package com.institutionaltrading.mobile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PositionRiskCalculatorTest {
    @Test fun computesConservativeWholeUnitQuantity() {
        val result = PositionRiskCalculator.calculate(
            accountEquity = 100_000.0,
            riskPercent = 1.0,
            entryPrice = 250.0,
            stopLossPrice = 245.0,
        )
        assertEquals(PositionRiskStatus.READY, result.status)
        assertEquals(1_000.0, result.riskBudget, 0.0)
        assertEquals(5.0, result.stopDistance, 0.0)
        assertEquals(200L, result.quantity)
        assertEquals(1_000.0, result.estimatedPositionRisk, 0.0)
    }

    @Test fun roundsDownToValidLotWithoutExceedingBudget() {
        val result = PositionRiskCalculator.calculate(
            accountEquity = 50_000.0,
            riskPercent = 1.0,
            entryPrice = 100.0,
            stopLossPrice = 93.0,
            lotSize = 25L,
        )
        assertEquals(PositionRiskStatus.READY, result.status)
        assertEquals(50L, result.quantity)
        assertTrue(result.estimatedPositionRisk <= result.riskBudget)
    }

    @Test fun returnsNoTradeWhenOneLotExceedsBudget() {
        val result = PositionRiskCalculator.calculate(
            accountEquity = 10_000.0,
            riskPercent = 1.0,
            entryPrice = 1_000.0,
            stopLossPrice = 900.0,
            lotSize = 2L,
        )
        assertEquals(PositionRiskStatus.NO_TRADE, result.status)
        assertEquals(0L, result.quantity)
        assertTrue(result.message.startsWith("NO-TRADE:"))
    }

    @Test fun returnsNoTradeForZeroStopDistance() {
        val result = PositionRiskCalculator.calculate(
            accountEquity = 100_000.0,
            riskPercent = 1.0,
            entryPrice = 250.0,
            stopLossPrice = 250.0,
        )
        assertEquals(PositionRiskStatus.NO_TRADE, result.status)
        assertEquals(0L, result.quantity)
    }

    @Test fun rejectsRiskAboveHardCapAndInvalidNumbers() {
        assertTrue(runCatching {
            PositionRiskCalculator.calculate(100_000.0, 2.01, 100.0, 95.0)
        }.isFailure)
        assertTrue(runCatching {
            PositionRiskCalculator.calculate(Double.NaN, 1.0, 100.0, 95.0)
        }.isFailure)
        assertTrue(runCatching {
            PositionRiskCalculator.calculate(100_000.0, 1.0, 100.0, 95.0, 0L)
        }.isFailure)
    }
}
