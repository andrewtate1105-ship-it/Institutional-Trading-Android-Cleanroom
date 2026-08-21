package com.institutionaltrading.mobile

import kotlin.math.abs
import kotlin.math.max

data class AdaptiveStop(
    val stop: Double,
    val riskPercent: Double,
    val withinRiskGuardrail: Boolean,
)

object AdaptiveStopCalculator {
    const val maximumRiskPercent = 2.0
    private const val minimumAtrFloorPercent = 0.5

    fun calculate(
        direction: CandidateDirection,
        entry: Double,
        structuralStop: Double,
        atr: Double,
    ): AdaptiveStop {
        require(direction != CandidateDirection.NONE) { "Adaptive stop requires LONG or SHORT" }
        require(entry.isFinite() && structuralStop.isFinite() && atr.isFinite()) { "Adaptive stop inputs must be finite" }
        require(entry > 0.0 && structuralStop > 0.0 && atr > 0.0) { "Adaptive stop inputs must be positive" }
        require(direction != CandidateDirection.LONG || structuralStop < entry) { "Long structural stop must be below entry" }
        require(direction != CandidateDirection.SHORT || structuralStop > entry) { "Short structural stop must be above entry" }

        val atrDistance = max(atr, entry * minimumAtrFloorPercent / 100.0)
        val stop = when (direction) {
            CandidateDirection.LONG -> minOf(structuralStop, entry - atrDistance)
            CandidateDirection.SHORT -> maxOf(structuralStop, entry + atrDistance)
            CandidateDirection.NONE -> error("unreachable")
        }
        val riskPercent = abs(entry - stop) / entry * 100.0
        return AdaptiveStop(stop, riskPercent, riskPercent <= maximumRiskPercent)
    }

    fun cappedForWait(direction: CandidateDirection, entry: Double, atr: Double): AdaptiveStop {
        require(direction != CandidateDirection.NONE) { "Adaptive stop requires LONG or SHORT" }
        require(entry.isFinite() && atr.isFinite() && entry > 0.0 && atr > 0.0) { "Adaptive stop inputs must be finite and positive" }
        val desired = max(atr, entry * minimumAtrFloorPercent / 100.0)
        val distance = minOf(desired, entry * maximumRiskPercent / 100.0)
        val stop = if (direction == CandidateDirection.LONG) entry - distance else entry + distance
        val riskPercent = abs(entry - stop) / entry * 100.0
        return AdaptiveStop(stop, riskPercent, true)
    }
}
