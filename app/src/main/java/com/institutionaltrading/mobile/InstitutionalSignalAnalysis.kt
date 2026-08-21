package com.institutionaltrading.mobile

import kotlin.math.abs
import kotlin.math.max

/**
 * Deterministic closed-bar confluence model. It intentionally does not infer unavailable
 * derivatives fields (OI/IV/Greeks), does not invent volume, and never consumes a forming candle.
 */
enum class InstitutionalBias { BULLISH, BEARISH, NEUTRAL }

data class InstitutionalAssessment(
    val bias: InstitutionalBias,
    val score: Int,
    val ema20: Double,
    val ema50: Double,
    val ema200: Double,
    val rsi14: Double,
    val atr14: Double,
    val support: Double,
    val resistance: Double,
    val momentumPercent: Double,
    val vwap: Double?,
    val latestVolumeRatio: Double?,
    val reason: String,
)

object InstitutionalSignalAnalysis {
    const val minimumBars = 200
    const val minimumDirectionalScore = 6
    const val maximumDirectionalScore = 10

    fun assess(series: ValidatedBarSeries): InstitutionalAssessment {
        require(series.bars.size >= minimumBars) { "At least 200 closed bars are required for institutional analysis" }
        require(series.bars.all { it.isClosed }) { "Institutional analysis accepts closed bars only" }

        val bars = series.bars
        val closes = bars.map { it.close }
        val latest = bars.last()
        val ema20 = ema(closes, 20)
        val ema50 = ema(closes, 50)
        val ema200 = ema(closes, 200)
        val rsi = rsi(closes, 14)
        val atr = atr(bars, 14)
        val structureWindow = bars.dropLast(1).takeLast(20)
        val support = structureWindow.minOf { it.low }
        val resistance = structureWindow.maxOf { it.high }
        val priorMomentumClose = closes[closes.lastIndex - 5]
        val momentum = (latest.close / priorMomentumClose - 1.0) * 100.0
        val vwap = vwapOrNull(bars)
        val latestVolumeRatio = latestVolumeRatioOrNull(bars)

        var bullish = 0
        var bearish = 0
        val bullReasons = mutableListOf<String>()
        val bearReasons = mutableListOf<String>()

        if (ema20 > ema50 && ema50 > ema200) {
            bullish += 2
            bullReasons += "EMA 20/50/200 bullish stack"
        }
        if (ema20 < ema50 && ema50 < ema200) {
            bearish += 2
            bearReasons += "EMA 20/50/200 bearish stack"
        }
        if (latest.close > ema200) {
            bullish += 1
            bullReasons += "price above EMA200"
        } else if (latest.close < ema200) {
            bearish += 1
            bearReasons += "price below EMA200"
        }
        if (rsi in 52.0..70.0) {
            bullish += 1
            bullReasons += "RSI confirms positive momentum"
        }
        if (rsi in 30.0..48.0) {
            bearish += 1
            bearReasons += "RSI confirms negative momentum"
        }
        if (momentum > 0.25) {
            bullish += 1
            bullReasons += "five-bar momentum positive"
        } else if (momentum < -0.25) {
            bearish += 1
            bearReasons += "five-bar momentum negative"
        }

        val bullishBreakout = latest.close > resistance
        val bearishBreakdown = latest.close < support
        if (bullishBreakout) {
            bullish += 2
            bullReasons += "closed-bar resistance breakout"
            if (latest.low <= resistance) {
                bullish += 1
                bullReasons += "breakout retest held above prior resistance"
            }
        }
        if (bearishBreakdown) {
            bearish += 2
            bearReasons += "closed-bar support breakdown"
            if (latest.high >= support) {
                bearish += 1
                bearReasons += "breakdown retest rejected below prior support"
            }
        }

        val atrPercent = atr / latest.close * 100.0
        if (atrPercent in 0.35..4.0) {
            if (bullish > bearish) bullish += 1
            if (bearish > bullish) bearish += 1
        }

        if (vwap != null && latestVolumeRatio != null) {
            when {
                latestVolumeRatio < 0.50 -> {
                    return InstitutionalAssessment(
                        bias = InstitutionalBias.NEUTRAL,
                        score = max(bullish, bearish),
                        ema20 = ema20,
                        ema50 = ema50,
                        ema200 = ema200,
                        rsi14 = rsi,
                        atr14 = atr,
                        support = support,
                        resistance = resistance,
                        momentumPercent = momentum,
                        vwap = vwap,
                        latestVolumeRatio = latestVolumeRatio,
                        reason = "Liquidity filter rejected setup: latest volume below 50% of 20-bar average",
                    )
                }
                latest.close > vwap && latestVolumeRatio >= 0.80 -> {
                    bullish += 1
                    bullReasons += "price above verified-volume VWAP with acceptable participation"
                }
                latest.close < vwap && latestVolumeRatio >= 0.80 -> {
                    bearish += 1
                    bearReasons += "price below verified-volume VWAP with acceptable participation"
                }
            }
        }

        val bias = when {
            bullish >= minimumDirectionalScore && bullish >= bearish + 2 -> InstitutionalBias.BULLISH
            bearish >= minimumDirectionalScore && bearish >= bullish + 2 -> InstitutionalBias.BEARISH
            else -> InstitutionalBias.NEUTRAL
        }
        val score = max(bullish, bearish)
        val reason = when (bias) {
            InstitutionalBias.BULLISH -> bullReasons.joinToString(", ")
            InstitutionalBias.BEARISH -> bearReasons.joinToString(", ")
            InstitutionalBias.NEUTRAL -> "Confluence below threshold (bull=$bullish, bear=$bearish)"
        }

        return InstitutionalAssessment(
            bias = bias,
            score = score,
            ema20 = ema20,
            ema50 = ema50,
            ema200 = ema200,
            rsi14 = rsi,
            atr14 = atr,
            support = support,
            resistance = resistance,
            momentumPercent = momentum,
            vwap = vwap,
            latestVolumeRatio = latestVolumeRatio,
            reason = reason,
        )
    }

    internal fun ema(values: List<Double>, period: Int): Double {
        require(period > 0 && values.size >= period) { "Insufficient values for EMA$period" }
        var value = values.take(period).average()
        val alpha = 2.0 / (period + 1.0)
        for (index in period until values.size) {
            value = values[index] * alpha + value * (1.0 - alpha)
        }
        return value
    }

    internal fun rsi(values: List<Double>, period: Int): Double {
        require(period > 0 && values.size > period) { "Insufficient values for RSI$period" }
        var gain = 0.0
        var loss = 0.0
        for (index in 1..period) {
            val delta = values[index] - values[index - 1]
            if (delta >= 0.0) gain += delta else loss -= delta
        }
        var averageGain = gain / period
        var averageLoss = loss / period
        for (index in period + 1 until values.size) {
            val delta = values[index] - values[index - 1]
            val currentGain = max(delta, 0.0)
            val currentLoss = max(-delta, 0.0)
            averageGain = (averageGain * (period - 1) + currentGain) / period
            averageLoss = (averageLoss * (period - 1) + currentLoss) / period
        }
        if (averageLoss == 0.0) return 100.0
        if (averageGain == 0.0) return 0.0
        val rs = averageGain / averageLoss
        return 100.0 - 100.0 / (1.0 + rs)
    }

    internal fun atr(bars: List<MarketBar>, period: Int): Double {
        require(period > 0 && bars.size > period) { "Insufficient bars for ATR$period" }
        val ranges = mutableListOf<Double>()
        for (index in 1 until bars.size) {
            val bar = bars[index]
            val previousClose = bars[index - 1].close
            ranges += max(bar.high - bar.low, max(abs(bar.high - previousClose), abs(bar.low - previousClose)))
        }
        var value = ranges.take(period).average()
        for (index in period until ranges.size) {
            value = (value * (period - 1) + ranges[index]) / period
        }
        return value
    }

    internal fun vwapOrNull(bars: List<MarketBar>): Double? {
        if (bars.isEmpty() || bars.any { it.volume == null }) return null
        var weighted = 0.0
        var volume = 0.0
        for (bar in bars) {
            val barVolume = bar.volume ?: return null
            weighted += ((bar.high + bar.low + bar.close) / 3.0) * barVolume
            volume += barVolume
        }
        return if (volume > 0.0) weighted / volume else null
    }

    internal fun latestVolumeRatioOrNull(bars: List<MarketBar>): Double? {
        if (bars.size < 21 || bars.takeLast(21).any { it.volume == null }) return null
        val prior = bars.dropLast(1).takeLast(20).map { it.volume!! }
        val average = prior.average()
        if (average <= 0.0) return null
        return bars.last().volume!! / average
    }
}
