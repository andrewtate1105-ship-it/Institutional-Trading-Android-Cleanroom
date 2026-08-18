package com.institutionaltrading.mobile

data class OperatorProfile(
    val accountEquity: Double,
    val riskPercent: Double,
    val markets: Set<String>,
    val timeframes: Set<String>,
    val watchlist: Set<String>,
    val swingHoldingDays: Int = 5,
)

enum class SignalDirection { LONG, SHORT, NO_TRADE }

data class SignalAlert(
    val direction: SignalDirection,
    val symbol: String,
    val timeframe: String,
    val sourceTimestamp: String,
    val entryZone: String,
    val invalidation: String,
    val stopLoss: String,
    val targets: List<String>,
    val rewardToRisk: String,
    val riskGuidance: String,
    val confidenceDiagnostics: String,
    val provenance: String,
    val warnings: List<String>,
)
