package com.institutionaltrading.mobile

import java.util.Locale
import kotlin.math.abs
import kotlin.math.max

enum class SetupState { TRADE, WAIT, NO_TRADE }

data class InAppSignalResult(
    val setupState: SetupState,
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
    val score: Int? = null,
    val emaAlignment: String? = null,
    val rsi14: Double? = null,
    val vwap: Double? = null,
    val volumeRatio: Double? = null,
    val support: Double? = null,
    val resistance: Double? = null,
    val invalidation: String? = null,
)

object InAppSignalEngine {
    private const val minAccountRiskPercent = 1.0
    private const val maxStopPercent = 2.0
    private const val atrStopMultiplier = 1.0

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
        val directional = when (assessment.bias) {
            InstitutionalBias.BULLISH -> CandidateDirection.LONG
            InstitutionalBias.BEARISH -> CandidateDirection.SHORT
            InstitutionalBias.NEUTRAL -> CandidateDirection.NONE
        }
        if (directional == CandidateDirection.NONE) {
            return noTrade(series, latest.sourceTimestamp, assessment.reason, assessment = assessment)
        }

        val candidate = TrendFollowingCandidate.evaluate(series)
        val expectedSignal = if (directional == CandidateDirection.LONG) SignalDirection.LONG else SignalDirection.SHORT

        if (candidate.direction == CandidateDirection.NONE || candidate.entryPrice == null || candidate.structuralStop == null) {
            val trigger = if (directional == CandidateDirection.LONG) assessment.resistance else assessment.support
            val stop = adaptiveStop(
                direction = directional,
                entry = trigger,
                structuralStop = if (directional == CandidateDirection.LONG) assessment.support else assessment.resistance,
                atr = assessment.atr14,
            )
            val riskPercent = abs(trigger - stop) / trigger * 100.0
            if (!riskPercent.isFinite() || riskPercent > maxStopPercent) {
                return noTrade(
                    series,
                    latest.sourceTimestamp,
                    "Directional bias exists, but the invalidation required by current structure exceeds the 2% risk guardrail",
                    riskPercent,
                    assessment,
                )
            }
            val targets = RiskTargetCalculator.calculate(directional, trigger, stop)
            return waitResult(
                series = series,
                assessment = assessment,
                direction = expectedSignal,
                entry = trigger,
                stop = stop,
                targets = targets,
                priceRiskPercent = riskPercent,
                reason = "Bias is established but entry confirmation is pending. ${candidate.reason}",
                invalidation = "Do not enter unless a closed bar confirms beyond ${money(trigger)}; invalidate if price breaches ${money(stop)} before confirmation",
            )
        }

        val aligned = candidate.direction == directional
        if (!aligned) {
            return waitResultWithoutLevels(
                series,
                assessment,
                expectedSignal,
                "Institutional bias is directional, but current market-structure trigger points the other way. Wait for structure to realign.",
            )
        }

        val entry = candidate.entryPrice
        val stop = adaptiveStop(candidate.direction, entry, candidate.structuralStop, assessment.atr14)
        val structuralRiskPercent = abs(entry - candidate.structuralStop) / entry * 100.0
        val priceRiskPercent = abs(entry - stop) / entry * 100.0
        if (!priceRiskPercent.isFinite() || priceRiskPercent > maxStopPercent || structuralRiskPercent > maxStopPercent) {
            val cappedStop = cappedStop(candidate.direction, entry, max(assessment.atr14 * atrStopMultiplier, entry * 0.005))
            val cappedRisk = abs(entry - cappedStop) / entry * 100.0
            val targets = RiskTargetCalculator.calculate(candidate.direction, entry, cappedStop)
            return waitResult(
                series,
                assessment,
                expectedSignal,
                entry,
                cappedStop,
                targets,
                cappedRisk,
                "Setup direction is valid, but current structural invalidation is wider than the 2% risk guardrail. Wait for a tighter retest rather than forcing the trade.",
                "Re-evaluate only after structure tightens enough to keep the stop at or below 2%",
            )
        }

        val sizing = PositionRiskCalculator.calculate(accountEquity, accountRiskPercent, entry, stop)
        if (sizing.status != PositionRiskStatus.READY) {
            return noTrade(series, latest.sourceTimestamp, "Risk validation rejected this setup", priceRiskPercent, assessment)
        }

        val targets = runCatching { RiskTargetCalculator.calculate(candidate.direction, entry, stop) }.getOrElse {
            return noTrade(series, latest.sourceTimestamp, "Calculated exit is invalid", priceRiskPercent, assessment)
        }
        val clearTo3R = RiskTargetCalculator.hasClearStructuralRoom(
            direction = candidate.direction,
            entry = entry,
            finalTarget = targets.target2,
            priorBars = series.bars.dropLast(1).takeLast(60),
        )

        if (!clearTo3R && assessment.score < 8) {
            return waitResult(
                series,
                assessment,
                expectedSignal,
                entry,
                stop,
                targets,
                priceRiskPercent,
                "Directional setup is valid, but intermediate opposing structure reduces clean 3R follow-through. Wait for momentum/participation confirmation.",
                "Invalidate below/above the adaptive stop ${money(stop)}; upgrade to TRADE only after momentum confirms through opposing structure",
            )
        }

        return InAppSignalResult(
            setupState = SetupState.TRADE,
            direction = expectedSignal,
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
            reason = "${candidate.reason}; score ${assessment.score}/${InstitutionalSignalAnalysis.maximumDirectionalScore}; ${assessment.reason}${if (!clearTo3R) "; 3R path has prior structure but strong confluence supports managed continuation" else ""}",
            provenance = latest.provenance,
            score = assessment.score,
            emaAlignment = emaAlignment(assessment),
            rsi14 = assessment.rsi14,
            vwap = assessment.vwap,
            volumeRatio = assessment.latestVolumeRatio,
            support = assessment.support,
            resistance = assessment.resistance,
            invalidation = "Closed-bar breach of adaptive stop ${money(stop)} invalidates the setup",
        )
    }

    private fun adaptiveStop(direction: CandidateDirection, entry: Double, structuralStop: Double, atr: Double): Double {
        val atrDistance = max(atr * atrStopMultiplier, entry * 0.005)
        return when (direction) {
            CandidateDirection.LONG -> minOf(structuralStop, entry - atrDistance)
            CandidateDirection.SHORT -> maxOf(structuralStop, entry + atrDistance)
            CandidateDirection.NONE -> error("Adaptive stop requires direction")
        }
    }

    private fun cappedStop(direction: CandidateDirection, entry: Double, desiredDistance: Double): Double {
        val distance = minOf(desiredDistance, entry * maxStopPercent / 100.0)
        return if (direction == CandidateDirection.LONG) entry - distance else entry + distance
    }

    private fun waitResult(
        series: ValidatedBarSeries,
        assessment: InstitutionalAssessment,
        direction: SignalDirection,
        entry: Double,
        stop: Double,
        targets: RiskTargets,
        priceRiskPercent: Double,
        reason: String,
        invalidation: String,
    ) = InAppSignalResult(
        setupState = SetupState.WAIT,
        direction = direction,
        symbol = series.symbol,
        timeframe = series.timeframe,
        sourceTimestamp = series.bars.last().sourceTimestamp,
        entry = entry,
        stopLoss = stop,
        target1 = targets.target1,
        target2 = targets.target2,
        rewardToRisk = targets.finalRewardToRisk,
        riskPercentOfPrice = priceRiskPercent,
        quantity = 0L,
        estimatedAccountRisk = 0.0,
        reason = reason,
        provenance = series.bars.last().provenance,
        score = assessment.score,
        emaAlignment = emaAlignment(assessment),
        rsi14 = assessment.rsi14,
        vwap = assessment.vwap,
        volumeRatio = assessment.latestVolumeRatio,
        support = assessment.support,
        resistance = assessment.resistance,
        invalidation = invalidation,
    )

    private fun waitResultWithoutLevels(
        series: ValidatedBarSeries,
        assessment: InstitutionalAssessment,
        direction: SignalDirection,
        reason: String,
    ) = InAppSignalResult(
        setupState = SetupState.WAIT,
        direction = direction,
        symbol = series.symbol,
        timeframe = series.timeframe,
        sourceTimestamp = series.bars.last().sourceTimestamp,
        entry = null,
        stopLoss = null,
        target1 = null,
        target2 = null,
        rewardToRisk = null,
        riskPercentOfPrice = null,
        quantity = 0L,
        estimatedAccountRisk = 0.0,
        reason = reason,
        provenance = series.bars.last().provenance,
        score = assessment.score,
        emaAlignment = emaAlignment(assessment),
        rsi14 = assessment.rsi14,
        vwap = assessment.vwap,
        volumeRatio = assessment.latestVolumeRatio,
        support = assessment.support,
        resistance = assessment.resistance,
        invalidation = "No entry until directional market structure realigns with institutional bias",
    )

    private fun noTrade(
        series: ValidatedBarSeries,
        timestamp: String,
        reason: String,
        priceRiskPercent: Double? = null,
        assessment: InstitutionalAssessment? = null,
    ) = InAppSignalResult(
        setupState = SetupState.NO_TRADE,
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
        score = assessment?.score,
        emaAlignment = assessment?.let(::emaAlignment),
        rsi14 = assessment?.rsi14,
        vwap = assessment?.vwap,
        volumeRatio = assessment?.latestVolumeRatio,
        support = assessment?.support,
        resistance = assessment?.resistance,
        invalidation = "No position",
    )

    fun format(result: InAppSignalResult): String = buildString {
        append("SETUP: ").append(result.setupState).append('\n')
        append("BIAS: ").append(result.direction).append('\n')
        result.score?.let { append("SCORE: ").append(it).append("/").append(InstitutionalSignalAnalysis.maximumDirectionalScore).append('\n') }
        result.emaAlignment?.let { append("EMA 20/50/200: ").append(it).append('\n') }
        result.rsi14?.let { append("RSI 14: ").append(format(it)).append('\n') }
        if (result.entry != null && result.stopLoss != null && result.target1 != null && result.target2 != null) {
            append(if (result.setupState == SetupState.WAIT) "ENTRY TRIGGER: ₹" else "ENTRY: ₹").append(format(result.entry)).append('\n')
            append("STOP LOSS: ₹").append(format(result.stopLoss)).append('\n')
            append("EXIT 1 (1.5R): ₹").append(format(result.target1)).append('\n')
            append("EXIT 2 / FINAL (3R): ₹").append(format(result.target2)).append('\n')
        }
        if (result.vwap != null) append("VWAP: ₹").append(format(result.vwap)).append('\n')
        if (result.volumeRatio != null) append("VOLUME: ").append(format(result.volumeRatio)).append("x 20-bar avg\n")
        if (result.support != null && result.resistance != null) {
            append("STRUCTURE: support ₹").append(format(result.support)).append(" / resistance ₹").append(format(result.resistance)).append('\n')
        }
        append("WHY: ").append(result.reason).append('\n')
        result.invalidation?.let { append("INVALIDATION: ").append(it) }
    }

    private fun emaAlignment(assessment: InstitutionalAssessment): String = when {
        assessment.ema20 > assessment.ema50 && assessment.ema50 > assessment.ema200 -> "BULLISH ✓"
        assessment.ema20 < assessment.ema50 && assessment.ema50 < assessment.ema200 -> "BEARISH ✓"
        else -> "MIXED"
    }

    private fun money(value: Double): String = "₹${format(value)}"
    private fun format(value: Double): String = String.format(Locale.US, "%.2f", value)
}
