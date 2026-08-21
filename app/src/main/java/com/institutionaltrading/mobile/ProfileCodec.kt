package com.institutionaltrading.mobile

import org.json.JSONArray
import org.json.JSONObject

object ProfileCodec {
    fun encode(profile: OperatorProfile): String = JSONObject()
        .put("schema", 2)
        .put("account_equity", profile.accountEquity)
        .put("risk_percent", profile.riskPercent)
        .put("markets", JSONArray(profile.markets.toList()))
        .put("timeframes", JSONArray(profile.timeframes.toList()))
        .put("watchlist", JSONArray(profile.watchlist.toList()))
        .put("swing_holding_days", profile.swingHoldingDays)
        .put("broker_execution_enabled", false)
        .toString()

    fun decode(value: String): OperatorProfile {
        val json = JSONObject(value)
        require(json.optInt("schema", -1) == 2) { "Unsupported profile schema" }
        require(!json.optBoolean("broker_execution_enabled", true)) { "Broker execution profile rejected" }

        return Validation.profile(
            OperatorProfile(
                accountEquity = json.getDouble("account_equity"),
                riskPercent = json.getDouble("risk_percent"),
                markets = json.getJSONArray("markets").toStringSet(),
                timeframes = json.getJSONArray("timeframes").toStringSet(),
                watchlist = json.getJSONArray("watchlist").toStringSet(),
                swingHoldingDays = json.getInt("swing_holding_days"),
            )
        )
    }

    private fun JSONArray.toStringSet(): Set<String> = buildSet {
        for (index in 0 until length()) add(getString(index))
    }
}
