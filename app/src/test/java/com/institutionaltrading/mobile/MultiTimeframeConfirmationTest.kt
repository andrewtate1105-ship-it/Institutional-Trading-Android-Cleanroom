package com.institutionaltrading.mobile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MultiTimeframeConfirmationTest {
    @Test fun mapsEverySupportedExecutionTimeframe() {
        assertEquals("15M", MultiTimeframeConfirmation.confirmationTimeframe("5M"))
        assertEquals("1H", MultiTimeframeConfirmation.confirmationTimeframe("15M"))
        assertEquals("D", MultiTimeframeConfirmation.confirmationTimeframe("1H"))
        assertEquals("W", MultiTimeframeConfirmation.confirmationTimeframe("D"))
        assertNull(MultiTimeframeConfirmation.confirmationTimeframe("W"))
    }

    @Test fun bullishHigherContextConfirmsLongOnly() {
        val bullish = assessment(InstitutionalBias.BULLISH)
        assertTrue(MultiTimeframeConfirmation.isAligned(SignalDirection.LONG, bullish))
        assertFalse(MultiTimeframeConfirmation.isAligned(SignalDirection.SHORT, bullish))
    }

    @Test fun bearishHigherContextConfirmsShortOnly() {
        val bearish = assessment(InstitutionalBias.BEARISH)
        assertTrue(MultiTimeframeConfirmation.isAligned(SignalDirection.SHORT, bearish))
        assertFalse(MultiTimeframeConfirmation.isAligned(SignalDirection.LONG, bearish))
    }

    @Test fun neutralContextAndNoTradeFailClosed() {
        val neutral = assessment(InstitutionalBias.NEUTRAL)
        assertFalse(MultiTimeframeConfirmation.isAligned(SignalDirection.LONG, neutral))
        assertFalse(MultiTimeframeConfirmation.isAligned(SignalDirection.NO_TRADE, neutral))
    }

    @Test fun unsupportedTimeframeIsRejected() {
        assertTrue(runCatching { MultiTimeframeConfirmation.confirmationTimeframe("30M") }.isFailure)
    }

    private fun assessment(bias: InstitutionalBias) = InstitutionalAssessment(
        bias = bias,
        score = 6,
        ema20 = 100.0,
        ema50 = 99.0,
        ema200 = 95.0,
        rsi14 = 55.0,
        atr14 = 1.0,
        support = 95.0,
        resistance = 105.0,
        momentumPercent = 1.0,
        vwap = null,
        latestVolumeRatio = null,
        reason = "TEST",
    )
}
