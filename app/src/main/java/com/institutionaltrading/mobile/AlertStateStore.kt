package com.institutionaltrading.mobile

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

interface AlertStateRepository {
    fun load(): AlertDeliveryState
    fun save(state: AlertDeliveryState)
}

object AlertStateCodec {
    fun encode(state: AlertDeliveryState): String = JSONObject()
        .put("schema", 2)
        .put("delivered", encodeKeys(state.deliveredKeys))
        .put("pending", encodeKeys(state.pendingKeys))
        .toString()

    fun decode(raw: String): AlertDeliveryState {
        val root = JSONObject(raw)
        return when (root.getInt("schema")) {
            1 -> AlertDeliveryState(deliveredKeys = decodeKeys(root.getJSONArray("delivered")))
            2 -> AlertDeliveryState(
                deliveredKeys = decodeKeys(root.getJSONArray("delivered")),
                pendingKeys = decodeKeys(root.getJSONArray("pending")),
            ).also { require((it.deliveredKeys intersect it.pendingKeys).isEmpty()) { "Alert state overlaps delivered and pending keys" } }
            else -> throw IllegalArgumentException("Unsupported alert-state schema")
        }
    }

    private fun encodeKeys(keys: Set<ClosedBarKey>): JSONArray = JSONArray().also { rows ->
        keys.sortedWith(compareBy<ClosedBarKey> { it.symbol }.thenBy { it.timeframe }.thenBy { it.sourceTimestamp })
            .forEach { key -> rows.put(JSONObject()
                .put("symbol", key.symbol)
                .put("timeframe", key.timeframe)
                .put("source_timestamp", key.sourceTimestamp)) }
    }

    private fun decodeKeys(rows: JSONArray): Set<ClosedBarKey> {
        require(rows.length() <= ClosedBarAlerts.maxRememberedKeys) { "Alert state exceeds safe limit" }
        val keys = linkedSetOf<ClosedBarKey>()
        for (index in 0 until rows.length()) {
            val row = rows.getJSONObject(index)
            val key = ClosedBarKey(
                runCatching { Validation.normalizeWatchlistItem(row.getString("symbol")) }
                    .getOrElse { throw IllegalArgumentException("Stored alert symbol is invalid") },
                row.getString("timeframe"),
                row.getString("source_timestamp"),
            )
            ClosedBarAlerts.key(SignalAlert(
                direction = SignalDirection.NO_TRADE,
                symbol = key.symbol,
                timeframe = key.timeframe,
                sourceTimestamp = key.sourceTimestamp,
                entryZone = "N/A",
                invalidation = "N/A",
                stopLoss = "N/A",
                targets = emptyList(),
                rewardToRisk = "N/A",
                riskGuidance = "N/A",
                confidenceDiagnostics = "N/A",
                provenance = "stored-state",
                warnings = listOf("stored-state"),
            ))
            require(keys.add(key)) { "Duplicate alert-state key" }
        }
        return keys
    }
}

class AlertStateStore(context: Context) : AlertStateRepository {
    private val file = File(context.filesDir, "operator/alert-state.json")

    override fun load(): AlertDeliveryState = if (!file.exists()) AlertDeliveryState() else AlertStateCodec.decode(file.readText())

    override fun save(state: AlertDeliveryState) {
        file.parentFile?.mkdirs()
        val temp = File(file.parentFile, "alert-state.json.tmp")
        temp.writeText(AlertStateCodec.encode(state))
        require(temp.renameTo(file)) { "Could not atomically save alert state" }
    }
}
