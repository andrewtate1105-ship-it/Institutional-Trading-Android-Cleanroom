package com.institutionaltrading.mobile

import kotlin.math.abs

data class RiskTargets(
    val target1: Double,
    val target2: Double,
    val finalRewardToRisk: Double,
)

object RiskTargetCalculator {
    const val firstTargetR = 1.5
    const val finalTargetR = 3.0

    fun calculate(direction: CandidateDirection, entry: Double, stop: Double): RiskTargets {
        require(direction != CandidateDirection.NONE) { "Directional target requires LONG or SHORT" }
        require(entry.isFinite() && stop.isFinite() && entry > 0.0 && stop > 0.0 && entry != stop) {
            "Entry and stop must be finite, positive and distinct"
        }
        val risk = abs(entry - stop)
        val target1 = if (direction == CandidateDirection.LONG) entry + firstTargetR * risk else entry - firstTargetR * risk
        val target2 = if (direction == CandidateDirection.LONG) entry + finalTargetR * risk else entry - finalTargetR * risk
        require(target1.isFinite() && target2.isFinite() && target1 > 0.0 && target2 > 0.0) { "Calculated exits are invalid" }
        return RiskTargets(target1, target2, finalTargetR)
    }
}
