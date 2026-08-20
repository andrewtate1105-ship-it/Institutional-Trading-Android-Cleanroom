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

    /**
     * A nominal 3R target is not enough. Reject a setup when previously traded structure contains
     * an obvious opposing level between entry and the final target. This uses only completed prior
     * bars and therefore does not add look-ahead.
     */
    fun hasClearStructuralRoom(
        direction: CandidateDirection,
        entry: Double,
        finalTarget: Double,
        priorBars: List<MarketBar>,
    ): Boolean {
        require(direction != CandidateDirection.NONE) { "Directional room check requires LONG or SHORT" }
        require(entry.isFinite() && finalTarget.isFinite() && entry > 0.0 && finalTarget > 0.0) {
            "Entry and final target must be finite and positive"
        }
        require(priorBars.all { it.isClosed }) { "Structural room check accepts closed bars only" }

        return when (direction) {
            CandidateDirection.LONG -> {
                require(finalTarget > entry) { "Long final target must be above entry" }
                priorBars.none { it.high > entry && it.high < finalTarget }
            }
            CandidateDirection.SHORT -> {
                require(finalTarget < entry) { "Short final target must be below entry" }
                priorBars.none { it.low < entry && it.low > finalTarget }
            }
            CandidateDirection.NONE -> false
        }
    }
}
