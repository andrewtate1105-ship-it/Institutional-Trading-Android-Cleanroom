package com.institutionaltrading.mobile

import kotlin.math.abs
import java.util.Locale

data class InAppSignalResult(
    val direction: SignalDirection,
    val symbol: String,
    val timeframe: String,
    val sourceTimestamp: String,
    val entry: Double?,
    val stopLoss: Double?,
    val target1: Double?,
    val target2: Double?,
    val rewardToRisk: Double?,
    val riskPercentOfPrice: Double?,
    val quantity: Long,
    val estimatedAccountRisk: Double,
    val reason: String,
    val provenance: String,
)

object InAppSignalEngine {
    private const val minAccountRiskPercent = 1.0
    private const val minStopPercent = 1.0
    private const val maxStopPercent = 2.0
    private const val target1R = 1.0
    private const val target2R = 2.0

    fun analyze(series: ValidatedBarSeries, accountEquity: Double, accountRiskPercent: Double): InAppSignalResult {
        require(series.bars.isNotEmpty()) { "Validated bar series is empty" }
        require(series.bars.all { it.isClosed }) { "In-app signal engine accepts closed bars only" }
        require(accountRiskPercent.isFinite() && accountRiskPercent in minAccountRiskPercent..Validation.hardRiskCapPercent) {
            "Account risk must be between 1% and 2%"
        }

        val candidate = TrendFollowingCandidate.evaluate(series)
        val latest = series.bars.last()
        if (candidate.direction == CandidateDirection.NONE || candidate.entryPrice == null || candidate.structuralStop == null) {
            return noTrade(series, latest.sourceTimestamp, candidate.reason)
        }

        val entry = candidate.entryPrice
        val stop = candidate.structuralStop
        val priceRiskPercent = abs(entry - stop) / entry * 100.0
        if (!priceRiskPercent.isFinite() || priceRiskPercent !in minStopPercent..maxStopPercent) {
            return noTrade(series, latest.sourceTimestamp, "No valid 1-2% structural stop is available", priceRiskPercent)
        }

        val sizing = PositionRiskCalculator.calculate(accountEquity, accountRiskPercent, entry, stop)
        if (sizing.status != PositionRiskStatus.READY) {
            return noTrade(series, latest.sourceTimestamp, "Risk validation rejected this setup", priceRiskPercent)
        }

        val distance = abs(entry - stop)
        val target1 = if (candidate.direction == CandidateDirection.LONG) entry + distance else entry - distance
        val target2 = if (candidate.direction == CandidateDirection.LONG) entry + target2R * distance else entry - target2R * distance
        if (!target1.isFinite() || !target2.isFinite() || target1 <= 0.0 || target2 <= 0.0) {
            return noTrade(series, latest.sourceTimestamp, "Calculated exit is invalid", priceRiskPercent)
        }

        return InAppSignalResult(
            direction = if (candidate.direction == CandidateDirection.LONG) SignalDirection.LONG else SignalDirection.SHORT,
            symbol = series.symbol,
            timeframe = series.timeframe,
            sourceTimestamp = latest.sourceTimestamp,
            entry = entry,
            stopLoss = stop,
            target1 = target1,
            target2 = target2,
            rewardToRisk = target2R,
            riskPercentOfPrice = priceRiskPercent,
            quantity = sizing.quantity,
            estimatedAccountRisk = sizing.estimatedPositionRisk,
            reason = candidate.reason,
            provenance = latest.provenance,
        )
    }

    private fun noTrade(series: ValidatedBarSeries, timestamp: String, reason: String, priceRiskPercent: Double? = null) = InAppSignalResult(
        direction = SignalDirection.NO_TRADE,
        symbol = series.symbol,
        timeframe = series.timeframe,
        sourceTimestamp = timestamp,
        entry = null,
        stopLoss = null,
        target1 = null,
        target2 = null,
        rewardToRisk = null,
        riskPercentOfPrice = priceRiskPercent,
        quantity = 0L,
        estimatedAccountRisk = 0.0,
        reason = reason,
        provenance = series.bars.last().provenance,
    )

    fun format(result: InAppSignalResult): String = buildString {
        append("SIGNAL: ").append(result.direction).append('\n')
        if (result.direction != SignalDirection.NO_TRADE) {
            append("ENTRY: ₹").append(format(result.entry!!)).append('\n')
            append("STOP LOSS: ₹").append(format(result.stopLoss!!)).append('\n')
            append("EXIT 1: ₹").append(format(result.target1!!)).append('\n')
            append("EXIT 2: ₹").append(format(result.target2!!)).append('\n')
        } else {
            append("NO TRADE\n")
            append("Reason: ").append(result.reason)
        }
    }

    private fun format(value: Double): String = String.format(Locale.US, "%.2f", value)
}
