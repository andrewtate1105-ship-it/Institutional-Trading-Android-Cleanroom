package com.institutionaltrading.mobile

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class ProfileStore(context: Context) {
    private val file = File(context.filesDir, "operator/profile.json")

    fun save(profile: OperatorProfile) {
        val validated = Validation.profile(profile)
        file.parentFile?.mkdirs()
        val json = JSONObject()
            .put("schema", 1)
            .put("private_chat_id", validated.privateChatId)
            .put("account_equity", validated.accountEquity)
            .put("risk_percent", validated.riskPercent)
            .put("markets", JSONArray(validated.markets.toList()))
            .put("timeframes", JSONArray(validated.timeframes.toList()))
            .put("watchlist", JSONArray(validated.watchlist.toList()))
            .put("swing_holding_days", validated.swingHoldingDays)
            .put("broker_execution_enabled", false)
        val temp = File(file.parentFile, "profile.json.tmp")
        temp.writeText(json.toString())
        require(temp.renameTo(file)) { "Could not atomically save profile" }
    }
}
