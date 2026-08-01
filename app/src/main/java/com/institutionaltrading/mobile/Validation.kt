package com.institutionaltrading.mobile

enum class InstrumentType { EQUITY, INDEX, COMMODITY }

object Validation {
    val defaultMarket = "NSE_EQUITY"
    val defaultTimeframes = setOf("5M", "15M", "1H", "D", "W")
    const val defaultRiskPercent = 1.0
    const val hardRiskCapPercent = 2.0

    private val canonicalSymbol = Regex("^[A-Z0-9&_-]{1,32}$")
    private val telegramChatId = Regex("^-?[0-9]{5,20}$")
    private val indianMobileNumber = Regex("^[6-9][0-9]{9}$")
    private val allowedMarkets = setOf("NSE_EQUITY", "NSE_INDEX")
    private val instrumentTypes = mapOf(
        "NIFTY" to InstrumentType.INDEX,
        "BANKNIFTY" to InstrumentType.INDEX,
        "SENSEX" to InstrumentType.INDEX,
        "CRUDEOILM" to InstrumentType.COMMODITY,
    )

    fun profile(profile: OperatorProfile): OperatorProfile {
        val normalizedChatId = validateChatId(profile.privateChatId)
        val normalizedWatchlist = normalizeWatchlist(profile.watchlist)

        require(profile.accountEquity.isFinite() && profile.accountEquity > 0.0) { "Account equity must be positive" }
        require(profile.riskPercent.isFinite() && profile.riskPercent > 0.0 && profile.riskPercent <= hardRiskCapPercent) { "Risk must be above 0 and at most 2%" }
        require(profile.markets.isNotEmpty() && profile.markets.all { it in allowedMarkets }) { "Unsupported market" }
        require(profile.timeframes.isNotEmpty() && profile.timeframes.all { it in defaultTimeframes }) { "Unsupported timeframe" }
        require(normalizedWatchlist.isNotEmpty()) { "Watchlist cannot be empty" }
        require(profile.swingHoldingDays in 1..5) { "Swing holding guidance must be 1-5 trading days" }

        return profile.copy(privateChatId = normalizedChatId, watchlist = normalizedWatchlist)
    }

    fun validateChatId(value: String): String {
        val normalized = value.trim()
        require(normalized.isNotEmpty()) { "Enter your Telegram Chat ID" }
        require(!indianMobileNumber.matches(normalized)) { "Enter your Telegram Chat ID, not your phone number" }
        require(telegramChatId.matches(normalized)) { "Telegram Chat ID must be numeric and may start with -" }
        return normalized
    }

    fun normalizeWatchlist(values: Iterable<String>): Set<String> {
        val normalized = linkedSetOf<String>()
        values
            .flatMap { it.split(',', '\n', '\r') }
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .forEach { item ->
                val symbol = if (item.contains("://")) {
                    OfficialNseUrl.extractSymbol(item)
                } else {
                    normalizeCanonicalSymbol(item)
                }
                require(symbol != null) { "Malformed or unsupported watchlist item" }
                normalized += symbol
            }
        return normalized
    }

    fun normalizeCanonicalSymbol(value: String): String? {
        val normalized = value.trim().uppercase()
        return normalized.takeIf { canonicalSymbol.matches(it) }
    }

    fun instrumentType(symbol: String): InstrumentType {
        val normalized = normalizeCanonicalSymbol(symbol)
            ?: throw IllegalArgumentException("Malformed or unsupported watchlist item")
        return instrumentTypes[normalized] ?: InstrumentType.EQUITY
    }
}
