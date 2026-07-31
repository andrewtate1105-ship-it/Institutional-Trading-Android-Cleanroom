package com.institutionaltrading.mobile

object Validation {
    val defaultMarket = "NSE_EQUITY"
    val defaultTimeframes = setOf("5M", "15M", "1H", "D", "W")
    const val defaultRiskPercent = 1.0
    const val hardRiskCapPercent = 2.0
    private val symbol = Regex("^[A-Z0-9&.-]{1,32}$")
    private val allowedMarkets = setOf("NSE_EQUITY", "NSE_INDEX")

    fun profile(profile: OperatorProfile): OperatorProfile {
        require(profile.privateChatId.matches(Regex("^-?[0-9]{5,20}$"))) { "Invalid private Telegram chat ID" }
        require(profile.accountEquity.isFinite() && profile.accountEquity > 0.0) { "Account equity must be positive" }
        require(profile.riskPercent.isFinite() && profile.riskPercent > 0.0 && profile.riskPercent <= hardRiskCapPercent) { "Risk must be above 0 and at most 2%" }
        require(profile.markets.isNotEmpty() && profile.markets.all { it in allowedMarkets }) { "Unsupported market" }
        require(profile.timeframes.isNotEmpty() && profile.timeframes.all { it in defaultTimeframes }) { "Unsupported timeframe" }
        require(profile.watchlist.isNotEmpty()) { "Watchlist cannot be empty" }
        require(profile.watchlist.all { symbol.matches(it) || OfficialNseUrl.isWhitelistedQuoteUrl(it) }) { "Malformed or unsupported watchlist item" }
        require(profile.swingHoldingDays in 1..5) { "Swing holding guidance must be 1-5 trading days" }
        return profile
    }
}
