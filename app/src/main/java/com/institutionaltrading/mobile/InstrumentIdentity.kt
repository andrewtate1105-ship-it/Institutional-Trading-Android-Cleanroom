package com.institutionaltrading.mobile

import java.util.Locale

data class InstrumentIdentity(
    val displayName: String,
    val symbol: String,
)

object InstrumentIdentityResolver {
    private val symbolLike = Regex("^[A-Za-z0-9&._-]{1,32}$")
    private val humanName = Regex("^[\\p{L}0-9&().,'+/_ -]{1,100}$")

    fun resolve(stockNameOrSymbol: String, officialNseUrl: String): InstrumentIdentity {
        val symbolFromUrl = OfficialNseUrl.normalizeToSymbol(officialNseUrl)
            ?: throw IllegalArgumentException("Enter a supported official NSE quote/chart URL")

        val typed = stockNameOrSymbol.trim().replace(Regex("\\s+"), " ")
        require(typed.isNotEmpty()) { "Enter a stock name or NSE symbol" }
        require(humanName.matches(typed)) { "Enter a valid stock name or NSE symbol" }

        if (symbolLike.matches(typed)) {
            require(typed.uppercase(Locale.ROOT) == symbolFromUrl) {
                "Stock symbol and NSE link identify different instruments"
            }
        }

        return InstrumentIdentity(
            displayName = typed,
            symbol = symbolFromUrl,
        )
    }
}
