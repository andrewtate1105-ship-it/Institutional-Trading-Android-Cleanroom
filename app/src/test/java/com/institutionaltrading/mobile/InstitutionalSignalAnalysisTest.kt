package com.institutionaltrading.mobile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class InstitutionalSignalAnalysisTest {
    private fun series(step: Double, volume: (Int) -> Double? = { null }): ValidatedBarSeries {
        val origin = Instant.parse("2026-01-01T00:00:00Z")
        val bars = (0 until 205).map { index ->
            val breakout = if (index == 204) if (step > 0) 2.0 else -2.0 else 0.0
            val close = 100.0 + index * step + breakout
            MarketBar(
                symbol = "RELIANCE",
                timeframe = "15M",
                sourceTimestamp = origin.plusSeconds(index * 900L).toString(),
                fetchedAt = origin.plusSeconds(index * 900L + 30L).toString(),
                open = close - step * 0.25,
                high = close + 0.4,
                low = close - 0.4,
                close = close,
                isClosed = true,
                provenance = "TEST",
                volume = volume(index),
            )
        }
        return ValidatedBarSeries("RELIANCE", "15M", bars)
    }

    @Test fun risingClosedBarsProduceBullishConfluence() {
        val assessment = InstitutionalSignalAnalysis.assess(series(0.20))
        assertEquals(InstitutionalBias.BULLISH, assessment.bias)
        assertTrue(assessment.score >= InstitutionalSignalAnalysis.minimumDirectionalScore)
        assertTrue(assessment.ema20 > assessment.ema50)
        assertTrue(assessment.ema50 > assessment.ema200)
        assertTrue(assessment.atr14 > 0.0)
        assertNull(assessment.vwap)
        assertNull(assessment.latestVolumeRatio)
    }

    @Test fun fallingClosedBarsProduceBearishConfluence() {
        val assessment = InstitutionalSignalAnalysis.assess(series(-0.20))
        assertEquals(InstitutionalBias.BEARISH, assessment.bias)
        assertTrue(assessment.score >= InstitutionalSignalAnalysis.minimumDirectionalScore)
        assertTrue(assessment.ema20 < assessment.ema50)
        assertTrue(assessment.ema50 < assessment.ema200)
    }

    @Test fun verifiedVolumeEnablesVwapAndParticipationScore() {
        val assessment = InstitutionalSignalAnalysis.assess(series(0.20) { 1000.0 })
        assertTrue(assessment.vwap != null)
        assertEquals(1.0, assessment.latestVolumeRatio!!, 1e-9)
        assertEquals(InstitutionalBias.BULLISH, assessment.bias)
        assertTrue(assessment.reason.contains("VWAP"))
    }

    @Test fun lowVerifiedVolumeFailsClosed() {
        val assessment = InstitutionalSignalAnalysis.assess(series(0.20) { index ->
            if (index == 204) 400.0 else 1000.0
        })
        assertEquals(InstitutionalBias.NEUTRAL, assessment.bias)
        assertTrue(assessment.reason.contains("Liquidity filter rejected setup"))
    }

    @Test fun emaUsesAllValuesAfterSeed() {
        val ema = InstitutionalSignalAnalysis.ema(listOf(1.0, 2.0, 3.0, 4.0), 3)
        assertEquals(3.0, ema, 1e-9)
    }

    @Test fun rsiOfStrictlyRisingSeriesIsOneHundred() {
        val rsi = InstitutionalSignalAnalysis.rsi((1..20).map(Int::toDouble), 14)
        assertEquals(100.0, rsi, 1e-9)
    }
}
