package com.institutionaltrading.mobile

import java.util.Locale

data class ImportedMarketData(
    val rows: List<BacktestRow>,
    val symbol: String,
    val timeframe: String,
) {
    init {
        require(rows.isNotEmpty()) { "Imported market data is empty" }
        require(symbol.isNotBlank()) { "Imported market data symbol is missing" }
        require(timeframe.isNotBlank()) { "Imported market data timeframe is missing" }
    }

    fun requireMatches(requestedSymbol: String, requestedTimeframe: String): List<BacktestRow> {
        val normalizedSymbol = requestedSymbol.trim().uppercase(Locale.ROOT)
        val normalizedTimeframe = requestedTimeframe.trim().uppercase(Locale.ROOT)
        if (normalizedSymbol != symbol.trim().uppercase(Locale.ROOT)) {
            throw MarketDataUnavailableException(
                "Loaded market data belongs to $symbol, not $normalizedSymbol. Re-import data for the selected stock."
            )
        }
        if (normalizedTimeframe != timeframe.trim().uppercase(Locale.ROOT)) {
            throw MarketDataUnavailableException(
                "Loaded market data is bound to $timeframe, not $normalizedTimeframe. Re-import data for the selected timeframe."
            )
        }
        return rows
    }
}
