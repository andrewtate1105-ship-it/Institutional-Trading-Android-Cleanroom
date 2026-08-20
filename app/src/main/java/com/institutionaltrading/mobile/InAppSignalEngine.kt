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

    fun analyze(series: ValidatedBarSeries, accountEquity: Double, accountRiskPercent: Double): InAppSignalResult {
        require(series.bars.isNotEmpty()) { "Validated bar series is empty" }
        require(series.bars.all { it.isClosed }) { "In-app signal engine accepts closed bars only" }
        require(accountRiskPercent.isFinite() && accountRiskPercent in minAccountRiskPercent..Validation.hardRiskCapPercent) {
            "Account risk must be between 1% and 2%"
        }

        val latest = series.bars.last()
        if (series.bars.size < InstitutionalSignalAnalysis.minimumBars) {
            return noTrade(series, latest.sourceTimestamp, "Insufficient closed-bar history for EMA200 institutional analysis")
        }

        val assessment = InstitutionalSignalAnalysis.assess(series)
        if (assessment.bias == InstitutionalBias.NEUTRAL) {
            return noTrade(series, latest.sourceTimestamp, assessment.reason)
        }

        val candidate = TrendFollowingCandidate.evaluate(series)
        if (candidate.direction == CandidateDirection.NONE || candidate.entryPrice == null || candidate.structuralStop == null) {
            return noTrade(series, latest.sourceTimestamp, candidate.reason)
        }

        val aligned = when (candidate.direction) {
            CandidateDirection.LONG -> assessment.bias == InstitutionalBias.BULLISH
            CandidateDirection.SHORT -> assessment.bias == InstitutionalBias.BEARISH
            CandidateDirection.NONE -> false
        }
        if (!aligned) {
            return noTrade(series, latest.sourceTimestamp, "Market structure and institutional confluence disagree")
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

        val targets = runCatching { RiskTargetCalculator.calculate(candidate.direction, entry, stop) }.getOrElse {
            return noTrade(series, latest.sourceTimestamp, "Calculated exit is invalid", priceRiskPercent)
        }

        return InAppSignalResult(
            direction = if (candidate.direction == CandidateDirection.LONG) SignalDirection.LONG else SignalDirection.SHORT,
            symbol = series.symbol,
            timeframe = series.timeframe,
            sourceTimestamp = latest.sourceTimestamp,
            entry = entry,
            stopLoss = stop,
            target1 = targets.target1,
            target2 = targets.target2,
            rewardToRisk = targets.finalRewardToRisk,
            riskPercentOfPrice = priceRiskPercent,
            quantity = sizing.quantity,
            estimatedAccountRisk = sizing.estimatedPositionRisk,
            reason = "${candidate.reason}; score ${assessment.score}/8; ${assessment.reason}",
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
            append("EXIT 1 (1.5R): ₹").append(format(result.target1!!)).append('\n')
            append("EXIT 2 (3R): ₹").append(format(result.target2!!)).append('\n')
            append("R:R: 1:").append(format(result.rewardToRisk!!)).append('\n')
            append("SETUP: ").append(result.reason)
        } else {
            append("NO TRADE\n")
            append("Reason: ").append(result.reason)
        }
    }

    private fun format(value: Double): String = String.format(Locale.US, "%.2f", value)
}
