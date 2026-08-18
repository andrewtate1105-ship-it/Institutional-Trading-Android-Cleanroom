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

/**
 * Signal-only presentation engine for validated, closed-bar data.
 *
 * It never places orders. A directional setup is accepted only when the existing
 * structural candidate has an entry and stop, the stop is 1-2% from entry, and
 * position sizing stays inside the configured 1-2% account-risk band.
 */
object InAppSignalEngine {
    private const val minAccountRiskPercent = 1.0
    private const val minStopPercent = 1.0
    private const val maxStopPercent = 2.0
    private const val target1R = 1.0
    private const val target2R = 2.0

    fun analyze(
        series: ValidatedBarSeries,
        accountEquity: Double,
        accountRiskPercent: Double,
    ): InAppSignalResult {
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
        val target1 = when (candidate.direction) {
            CandidateDirection.LONG -> entry + target1R * stopDistance
            CandidateDirection.SHORT -> entry - target1R * stopDistance
            CandidateDirection.NONE -> error("NONE candidate passed directional branch")
        }
        val target2 = when (candidate.direction) {
            CandidateDirection.LONG -> entry + target2R * stopDistance
            CandidateDirection.SHORT -> entry - target2R * stopDistance
            CandidateDirection.NONE -> error("NONE candidate passed directional branch")
        }
        if (!target1.isFinite() || !target2.isFinite() || target1 <= 0.0 || target2 <= 0.0) {
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
        append("Symbol: ").append(result.symbol).append(" | Timeframe: ").append(result.timeframe).append('\n')
        append("Closed-bar timestamp: ").append(result.sourceTimestamp).append('\n')
        if (result.direction != SignalDirection.NO_TRADE) {
            append("Entry zone: ₹").append(format(result.entry!!)).append(" (validated closed-bar trigger)\n")
            append("Stop loss / invalidation: ₹").append(format(result.stopLoss!!))
                .append(" (").append(format(result.riskPercentOfPrice!!)).append("%)\n")
            append("Target 1 (1R): ₹").append(format(result.target1!!)).append('\n')
            append("Target 2 (2R): ₹").append(format(result.target2!!)).append('\n')
            append("Exit guidance: take risk off at Target 1; final planned exit at Target 2 or structural stop, whichever occurs first.\n")
            append("Reward/Risk to Target 2: 1:").append(format(result.rewardToRisk!!)).append('\n')
            append("Max quantity: ").append(result.quantity).append('\n')
            append("Estimated account risk: ₹").append(format(result.estimatedAccountRisk)).append('\n')
        }
        append("Reason: ").append(result.reason).append('\n')
        append("Data: ").append(result.provenance)
    }

    private fun format(value: Double): String = String.format(Locale.US, "%.2f", value)
}
