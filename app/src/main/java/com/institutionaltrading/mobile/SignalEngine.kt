package com.institutionaltrading.mobile

object SignalEngine {
    fun failClosed(symbol: String, timeframe: String, provenance: String, warning: String): SignalAlert = SignalAlert(
        direction = SignalDirection.NO_TRADE,
        symbol = symbol,
        timeframe = timeframe,
        sourceTimestamp = "UNAVAILABLE",
        entryZone = "N/A",
        invalidation = "N/A",
        stopLoss = "N/A",
        targets = emptyList(),
        rewardToRisk = "N/A",
        riskGuidance = "No position while validation or source data is incomplete.",
        confidenceDiagnostics = "Insufficient validated evidence.",
        provenance = provenance,
        warnings = listOf(warning),
    )
}
