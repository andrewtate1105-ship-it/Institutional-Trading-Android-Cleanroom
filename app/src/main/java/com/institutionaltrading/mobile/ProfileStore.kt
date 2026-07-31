package com.institutionaltrading.mobile

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class ProfileStore(context: Context) {
    private val file = File(context.filesDir, "operator/profile.json")

    fun save(profile: OperatorProfile) {
        Validation.profile(profile)
        file.parentFile?.mkdirs()
        val json = JSONObject()
            .put("schema", 1)
            .put("private_chat_id", profile.privateChatId)
            .put("account_equity", profile.accountEquity)
            .put("risk_percent", profile.riskPercent)
            .put("markets", JSONArray(profile.markets.toList()))
            .put("timeframes", JSONArray(profile.timeframes.toList()))
            .put("watchlist", JSONArray(profile.watchlist.toList()))
            .put("swing_holding_days", profile.swingHoldingDays)
            .put("broker_execution_enabled", false)
        val temp = File(file.parentFile, "profile.json.tmp")
        temp.writeText(json.toString())
        require(temp.renameTo(file)) { "Could not atomically save profile" }
    }
}
