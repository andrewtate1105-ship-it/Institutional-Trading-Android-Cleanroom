package com.institutionaltrading.mobile

import kotlin.math.abs
import kotlin.math.floor

enum class PositionRiskStatus {
    READY,
    NO_TRADE,
}

data class PositionRiskGuidance(
    val status: PositionRiskStatus,
    val riskBudget: Double,
    val stopDistance: Double,
    val quantity: Long,
    val estimatedPositionRisk: Double,
    val message: String,
)

/**
 * Conservative signal-only position-risk guidance.
 * This module never places orders and never increases the configured operator risk.
 */
object PositionRiskCalculator {
    fun calculate(
        accountEquity: Double,
        riskPercent: Double,
        entryPrice: Double,
        stopLossPrice: Double,
        lotSize: Long = 1L,
    ): PositionRiskGuidance {
        require(accountEquity.isFinite() && accountEquity > 0.0) { "Account equity must be positive and finite" }
        require(riskPercent.isFinite() && riskPercent > 0.0) { "Risk percent must be positive and finite" }
        require(riskPercent <= Validation.hardRiskCapPercent) { "Risk percent exceeds the 2% hard cap" }
        require(entryPrice.isFinite() && entryPrice > 0.0) { "Entry price must be positive and finite" }
        require(stopLossPrice.isFinite() && stopLossPrice > 0.0) { "Stop-loss price must be positive and finite" }
        require(lotSize > 0L) { "Lot size must be positive" }

        val stopDistance = abs(entryPrice - stopLossPrice)
        if (stopDistance == 0.0 || !stopDistance.isFinite()) {
            return noTrade("Entry and stop loss must define a non-zero finite risk distance")
        }

        val riskBudget = accountEquity * (riskPercent / 100.0)
        if (!riskBudget.isFinite() || riskBudget <= 0.0) {
            return noTrade("Risk budget could not be computed safely")
        }

        val rawUnits = floor(riskBudget / stopDistance).toLong()
        val quantity = (rawUnits / lotSize) * lotSize
        if (quantity <= 0L) {
            return PositionRiskGuidance(
                status = PositionRiskStatus.NO_TRADE,
                riskBudget = riskBudget,
                stopDistance = stopDistance,
                quantity = 0L,
                estimatedPositionRisk = 0.0,
                message = "NO-TRADE: configured risk budget is too small for one valid lot at this stop distance",
            )
        }

        val estimatedRisk = quantity * stopDistance
        require(estimatedRisk.isFinite() && estimatedRisk <= riskBudget + 1e-9) {
            "Calculated position risk exceeds the configured budget"
        }

        return PositionRiskGuidance(
            status = PositionRiskStatus.READY,
            riskBudget = riskBudget,
            stopDistance = stopDistance,
            quantity = quantity,
            estimatedPositionRisk = estimatedRisk,
            message = "Conservative maximum quantity; signal-only guidance, not an execution instruction",
        )
    }

    private fun noTrade(reason: String) = PositionRiskGuidance(
        status = PositionRiskStatus.NO_TRADE,
        riskBudget = 0.0,
        stopDistance = 0.0,
        quantity = 0L,
        estimatedPositionRisk = 0.0,
        message = "NO-TRADE: $reason",
    )
}
