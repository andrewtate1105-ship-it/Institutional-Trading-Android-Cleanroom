package com.institutionaltrading.mobile

import java.util.Locale

object Validation {
    val defaultMarket = "NSE_EQUITY"
    val defaultMarkets = setOf("NSE_EQUITY", "NSE_INDEX", "MCX_COMMODITY")
    val defaultTimeframes = setOf("5M", "15M", "1H", "D", "W")
    const val defaultRiskPercent = 1.0
    const val hardRiskCapPercent = 2.0

    private val symbol = Regex("^[A-Z0-9&._-]{1,32}$")
    private val specialInstrumentMarkets = mapOf(
        "NIFTY" to "NSE_INDEX",
        "BANKNIFTY" to "NSE_INDEX",
        "SENSEX" to "NSE_INDEX",
        "CRUDEOILM" to "MCX_COMMODITY",
    )

    fun profile(profile: OperatorProfile): OperatorProfile {
        val normalized = profile.copy(
            privateChatId = profile.privateChatId.trim(),
            markets = profile.markets.map { it.trim().uppercase(Locale.ROOT) }.filter { it.isNotEmpty() }.toSet(),
            timeframes = profile.timeframes.map { it.trim().uppercase(Locale.ROOT) }.filter { it.isNotEmpty() }.toSet(),
            watchlist = profile.watchlist.map(::normalizeWatchlistItem).toCollection(linkedSetOf()),
        )

        require(normalized.privateChatId.matches(Regex("^-?[0-9]{5,20}$"))) {
            "Enter the numeric Telegram chat ID shown by Telegram, not a username"
        }
        require(normalized.accountEquity.isFinite() && normalized.accountEquity > 0.0) {
            "Account equity must be positive"
        }
        require(
            normalized.riskPercent.isFinite() &&
                normalized.riskPercent > 0.0 &&
                normalized.riskPercent <= hardRiskCapPercent
        ) { "Risk must be above 0 and at most 2%" }
        require(normalized.markets.isNotEmpty() && normalized.markets.all { it in defaultMarkets }) {
            "Unsupported market"
        }
        require(normalized.timeframes.isNotEmpty() && normalized.timeframes.all { it in defaultTimeframes }) {
            "Unsupported timeframe"
        }
        require(normalized.watchlist.isNotEmpty()) { "Watchlist cannot be empty" }
        normalized.watchlist.forEach { item ->
            specialInstrumentMarkets[item]?.let { requiredMarket ->
                require(requiredMarket in normalized.markets) {
                    "$item requires allowed market $requiredMarket"
                }
            }
        }
        require(normalized.swingHoldingDays in 1..5) {
            "Swing holding guidance must be 1-5 trading days"
        }
        return normalized
    }

    fun normalizeWatchlistItem(value: String): String {
        val trimmed = value.trim()
        require(trimmed.isNotEmpty()) { "Malformed or unsupported watchlist item" }
        if (trimmed.startsWith("http://", ignoreCase = true) || trimmed.startsWith("https://", ignoreCase = true)) {
            return OfficialNseUrl.normalizeToSymbol(trimmed)
                ?: throw IllegalArgumentException("Malformed or unsupported watchlist item")
        }
        val normalized = trimmed.uppercase(Locale.ROOT)
        require(symbol.matches(normalized)) { "Malformed or unsupported watchlist item" }
        return normalized
    }
}
