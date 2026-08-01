package com.institutionaltrading.mobile

import java.time.Instant

/** Uniquely identifies a signal generated from one completed source bar. */
data class ClosedBarKey(
    val symbol: String,
    val timeframe: String,
    val sourceTimestamp: String,
)

data class AlertDeliveryState(val deliveredKeys: Set<ClosedBarKey> = emptySet())

object ClosedBarAlerts {
    const val maxRememberedKeys = 2_000

    fun key(alert: SignalAlert): ClosedBarKey {
        val symbol = Validation.normalizeCanonicalSymbol(alert.symbol)
            ?: throw IllegalArgumentException("Alert symbol is invalid")
        require(alert.timeframe in Validation.defaultTimeframes) { "Alert timeframe is unsupported" }
        require(alert.sourceTimestamp != "UNAVAILABLE") { "Closed-bar timestamp is required" }
        val timestamp = runCatching { Instant.parse(alert.sourceTimestamp) }
            .getOrElse { throw IllegalArgumentException("Closed-bar timestamp must be ISO-8601 UTC") }
        require(timestamp.toString() == alert.sourceTimestamp) { "Closed-bar timestamp must be canonical UTC" }
        return ClosedBarKey(symbol, alert.timeframe, alert.sourceTimestamp)
    }

    fun shouldDeliver(alert: SignalAlert, state: AlertDeliveryState): Boolean = key(alert) !in state.deliveredKeys

    fun markDelivered(alert: SignalAlert, state: AlertDeliveryState): AlertDeliveryState {
        val key = key(alert)
        val retained = (state.deliveredKeys + key)
            .sortedByDescending { it.sourceTimestamp }
            .take(maxRememberedKeys)
            .toSet()
        return AlertDeliveryState(retained)
    }
}
