package com.institutionaltrading.mobile

object AlertFormatter {
    fun telegram(alert: SignalAlert): String {
        ClosedBarAlerts.key(alert)
        require(alert.provenance.isNotBlank()) { "Source provenance is required" }
        require(alert.warnings.isNotEmpty()) { "At least one warning is required" }

        val targets = alert.targets.ifEmpty { listOf("N/A") }.joinToString(", ")
        val warnings = alert.warnings.joinToString(" | ")
        return buildString {
            appendLine("Direction: ${alert.direction}")
            appendLine("Symbol: ${alert.symbol.uppercase()}")
            appendLine("Timeframe: ${alert.timeframe}")
            appendLine("Source timestamp: ${alert.sourceTimestamp}")
            appendLine("Entry zone: ${alert.entryZone}")
            appendLine("Invalidation: ${alert.invalidation}")
            appendLine("Stop loss: ${alert.stopLoss}")
            appendLine("Targets: $targets")
            appendLine("Reward-to-risk: ${alert.rewardToRisk}")
            appendLine("Position-risk guidance: ${alert.riskGuidance}")
            appendLine("Confidence diagnostics: ${alert.confidenceDiagnostics}")
            appendLine("Source provenance: ${alert.provenance}")
            append("Warnings: $warnings")
        }.also { require(it.length <= 4096) { "Telegram alert is too long" } }
    }
}
