package com.institutionaltrading.mobile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Test

class TrendClassificationTest {
    private fun swing(index: Int, price: Double, type: SwingType) = SwingPoint(
        index = index,
        timestamp = "2026-01-01T00:00:00Z",
        price = price,
        type = type,
        tier = SwingTier.EXTERNAL,
    )

    @Test
    fun classifiesUptrendFromHigherHighAndHigherLow() {
        val result = TrendClassifier.classify(listOf(
            swing(1, 100.0, SwingType.HIGH),
            swing(2, 90.0, SwingType.LOW),
            swing(3, 110.0, SwingType.HIGH),
            swing(4, 95.0, SwingType.LOW),
        ))
        assertEquals(TrendState.UPTREND, result.state)
        assertNull(result.warning)
    }

    @Test
    fun classifiesDowntrendFromLowerHighAndLowerLow() {
        val result = TrendClassifier.classify(listOf(
            swing(1, 110.0, SwingType.HIGH),
            swing(2, 100.0, SwingType.LOW),
            swing(3, 105.0, SwingType.HIGH),
            swing(4, 90.0, SwingType.LOW),
        ))
        assertEquals(TrendState.DOWNTREND, result.state)
    }

    @Test
    fun classifiesRangeWhenGeometryIsMixed() {
        val result = TrendClassifier.classify(listOf(
            swing(1, 100.0, SwingType.HIGH),
            swing(2, 90.0, SwingType.LOW),
            swing(3, 110.0, SwingType.HIGH),
            swing(4, 85.0, SwingType.LOW),
        ))
        assertEquals(TrendState.RANGE, result.state)
    }

    @Test
    fun failsClosedToUnknownWithInsufficientExternalSwings() {
        val result = TrendClassifier.classify(listOf(
            swing(1, 100.0, SwingType.HIGH),
            swing(2, 90.0, SwingType.LOW),
        ))
        assertEquals(TrendState.UNKNOWN, result.state)
        assertEquals("LOW_CONFIRMATION_BAR_COUNT", result.warning)
        assertNotNull(result.latestExternalHigh)
        assertNotNull(result.latestExternalLow)
    }

    @Test
    fun ignoresInternalSwings() {
        val internal = SwingPoint(
            index = 99,
            timestamp = "2026-01-01T00:00:00Z",
            price = 1_000.0,
            type = SwingType.HIGH,
            tier = SwingTier.INTERNAL,
        )
        val result = TrendClassifier.classify(listOf(
            swing(1, 100.0, SwingType.HIGH),
            swing(2, 90.0, SwingType.LOW),
            internal,
            swing(3, 110.0, SwingType.HIGH),
            swing(4, 95.0, SwingType.LOW),
        ))
        assertEquals(TrendState.UPTREND, result.state)
    }
}
