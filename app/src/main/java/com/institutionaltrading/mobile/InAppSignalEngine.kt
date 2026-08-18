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
    val target: Double?,
    val rewardToRisk: Double?,
    val riskPercentOfPrice: Double?,
    val quantity: Long,
    val estimatedAccountRisk: Double,
    val reason: String,
    val provenance: String,
)

/**
 * Signal-only presentation engine for validated, closed-bar data.
 *
 * It never places orders. A directional setup is accepted only when the existing
 * structural candidate has an entry and stop, the stop is 1-2% from entry, and
 * position sizing stays inside the configured account-risk cap.
 */
object InAppSignalEngine {
    private const val minStopPercent = 1.0
    private const val maxStopPercent = 2.0
    private const val targetR = 2.0

    fun analyze(
        series: ValidatedBarSeries,
        accountEquity: Double,
        accountRiskPercent: Double,
    ): InAppSignalResult {
        require(series.bars.isNotEmpty()) { "Validated bar series is empty" }
        require(series.bars.all { it.isClosed }) { "In-app signal engine accepts closed bars only" }
        require(accountRiskPercent > 0.0 && accountRiskPercent <= Validation.hardRiskCapPercent) {
            "Account risk must be above 0 and at most 2%"
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
            return noTrade(
                series,
                latest.sourceTimestamp,
                "Structural stop is ${format(priceRiskPercent)}% from entry; required band is 1-2%. ${candidate.reason}",
                priceRiskPercent,
            )
        }

        val sizing = PositionRiskCalculator.calculate(
            accountEquity = accountEquity,
            riskPercent = accountRiskPercent,
            entryPrice = entry,
            stopLossPrice = stop,
        )
        if (sizing.status != PositionRiskStatus.READY) {
            return noTrade(series, latest.sourceTimestamp, sizing.message, priceRiskPercent)
        }

        val stopDistance = abs(entry - stop)
        val target = when (candidate.direction) {
            CandidateDirection.LONG -> entry + targetR * stopDistance
            CandidateDirection.SHORT -> entry - targetR * stopDistance
            CandidateDirection.NONE -> error("NONE candidate passed directional branch")
        }
        if (!target.isFinite() || target <= 0.0) {
            return noTrade(series, latest.sourceTimestamp, "Calculated target is invalid", priceRiskPercent)
        }

        return InAppSignalResult(
            direction = when (candidate.direction) {
                CandidateDirection.LONG -> SignalDirection.LONG
                CandidateDirection.SHORT -> SignalDirection.SHORT
                CandidateDirection.NONE -> SignalDirection.NO_TRADE
            },
            symbol = series.symbol,
            timeframe = series.timeframe,
            sourceTimestamp = latest.sourceTimestamp,
            entry = entry,
            stopLoss = stop,
            target = target,
            rewardToRisk = targetR,
            riskPercentOfPrice = priceRiskPercent,
            quantity = sizing.quantity,
            estimatedAccountRisk = sizing.estimatedPositionRisk,
            reason = candidate.reason,
            provenance = latest.provenance,
        )
    }

    private fun noTrade(
        series: ValidatedBarSeries,
        timestamp: String,
        reason: String,
        priceRiskPercent: Double? = null,
    ) = InAppSignalResult(
        direction = SignalDirection.NO_TRADE,
        symbol = series.symbol,
        timeframe = series.timeframe,
        sourceTimestamp = timestamp,
        entry = null,
        stopLoss = null,
        target = null,
        rewardToRisk = null,
        riskPercentOfPrice = priceRiskPercent,
        quantity = 0L,
        estimatedAccountRisk = 0.0,
        reason = reason,
        provenance = series.bars.last().provenance,
    )

    fun format(result: InAppSignalResult): String = buildString {
        append("SIGNAL: ").append(result.direction).append('\n')
        append("Symbol: ").append(result.symbol).append(" | Timeframe: ").append(result.timeframe).append('\n')
        append("Closed-bar timestamp: ").append(result.sourceTimestamp).append('\n')
        if (result.direction != SignalDirection.NO_TRADE) {
            append("Entry: ₹").append(format(result.entry!!)).append('\n')
            append("Stop loss: ₹").append(format(result.stopLoss!!))
                .append(" (").append(format(result.riskPercentOfPrice!!)).append("%)\n")
            append("Target: ₹").append(format(result.target!!)).append('\n')
            append("Reward/Risk: 1:").append(format(result.rewardToRisk!!)).append('\n')
            append("Max quantity: ").append(result.quantity).append('\n')
            append("Estimated account risk: ₹").append(format(result.estimatedAccountRisk)).append('\n')
        }
        append("Reason: ").append(result.reason).append('\n')
        append("Data: ").append(result.provenance)
    }

    private fun format(value: Double): String = String.format(Locale.US, "%.2f", value)
}
