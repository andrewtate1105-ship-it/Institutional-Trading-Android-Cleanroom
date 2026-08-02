package com.institutionaltrading.mobile

import java.time.Instant

/** Uniquely identifies a signal generated from one completed source bar. */
data class ClosedBarKey(
    val symbol: String,
    val timeframe: String,
    val sourceTimestamp: String,
)

data class AlertDeliveryState(
    val deliveredKeys: Set<ClosedBarKey> = emptySet(),
    val pendingKeys: Set<ClosedBarKey> = emptySet(),
)

object ClosedBarAlerts {
    const val maxRememberedKeys = 2_000

    fun key(alert: SignalAlert): ClosedBarKey {
        val symbol = runCatching { Validation.normalizeWatchlistItem(alert.symbol) }
            .getOrElse { throw IllegalArgumentException("Alert symbol is invalid") }
        require(!symbol.startsWith("HTTP", ignoreCase = true)) { "Alert symbol is invalid" }
        require(alert.timeframe in Validation.defaultTimeframes) { "Alert timeframe is unsupported" }
        require(alert.sourceTimestamp != "UNAVAILABLE") { "Closed-bar timestamp is required" }
        val timestamp = runCatching { Instant.parse(alert.sourceTimestamp) }
            .getOrElse { throw IllegalArgumentException("Closed-bar timestamp must be ISO-8601 UTC") }
        require(timestamp.toString() == alert.sourceTimestamp) { "Closed-bar timestamp must be canonical UTC" }
        return ClosedBarKey(symbol, alert.timeframe, alert.sourceTimestamp)
    }

    fun shouldDeliver(alert: SignalAlert, state: AlertDeliveryState): Boolean {
        val key = key(alert)
        return key !in state.deliveredKeys && key !in state.pendingKeys
    }

    fun reserve(alert: SignalAlert, state: AlertDeliveryState): AlertDeliveryState {
        val key = key(alert)
        require(key !in state.deliveredKeys && key !in state.pendingKeys) { "Alert is already delivered or pending" }
        return state.copy(pendingKeys = bounded(state.pendingKeys + key))
    }

    fun markDelivered(alert: SignalAlert, state: AlertDeliveryState): AlertDeliveryState {
        val key = key(alert)
        require(key in state.pendingKeys) { "Alert must be reserved before delivery completion" }
        return AlertDeliveryState(
            deliveredKeys = bounded(state.deliveredKeys + key),
            pendingKeys = state.pendingKeys - key,
        )
    }

    fun release(alert: SignalAlert, state: AlertDeliveryState): AlertDeliveryState =
        state.copy(pendingKeys = state.pendingKeys - key(alert))

    private fun bounded(keys: Set<ClosedBarKey>): Set<ClosedBarKey> = keys
        .sortedByDescending { it.sourceTimestamp }
        .take(maxRememberedKeys)
        .toSet()
}
