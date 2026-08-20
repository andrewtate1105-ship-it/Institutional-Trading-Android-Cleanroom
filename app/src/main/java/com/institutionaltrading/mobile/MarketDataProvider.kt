package com.institutionaltrading.mobile

data class MarketDataCapabilities(
    val closedOhlcBars: Boolean,
    val volume: Boolean,
    val derivativesContracts: Boolean,
    val openInterest: Boolean,
    val impliedVolatility: Boolean,
    val greeks: Boolean,
)

interface ClosedBarMarketDataProvider {
    val providerName: String
    val capabilities: MarketDataCapabilities

    fun fetch(
        symbol: String,
        timeframe: String,
        limit: Int = 200,
        onComplete: (Result<List<BacktestRow>>) -> Unit,
    )

    fun destroy()
}

enum class TradingMode(val label: String) {
    STOCKS("Stocks"),
    F_AND_O("F&O"),
}

object TradingModePolicy {
    fun canAnalyze(mode: TradingMode, capabilities: MarketDataCapabilities): Boolean = when (mode) {
        TradingMode.STOCKS -> capabilities.closedOhlcBars
        TradingMode.F_AND_O -> capabilities.closedOhlcBars && capabilities.derivativesContracts
    }

    fun resultNotice(mode: TradingMode, capabilities: MarketDataCapabilities): String = when (mode) {
        TradingMode.STOCKS -> "Underlying equity signal."
        TradingMode.F_AND_O -> if (canAnalyze(mode, capabilities)) {
            "Derivative contract data available. Contract selection must use provider-supplied values only."
        } else {
            "NO TRADE: this feed has no verified F&O contract data. Strike, premium, OI, IV and Greeks are not fabricated."
        }
    }
}
