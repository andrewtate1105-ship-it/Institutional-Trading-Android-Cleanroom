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
    /**
     * Stocks mode can analyze verified closed underlying bars directly. F&O mode is stricter:
     * it remains unavailable until the active provider exposes verified derivative contracts.
     * This prevents an underlying-only directional view from being presented as a tradable
     * futures/options setup and guarantees we never invent strikes, premiums, OI, IV or Greeks.
     */
    fun canAnalyze(mode: TradingMode, capabilities: MarketDataCapabilities): Boolean = when (mode) {
        TradingMode.STOCKS -> capabilities.closedOhlcBars
        TradingMode.F_AND_O -> capabilities.closedOhlcBars && capabilities.derivativesContracts
    }

    fun hasVerifiedDerivativeContracts(capabilities: MarketDataCapabilities): Boolean =
        capabilities.derivativesContracts

    fun resultNotice(mode: TradingMode, capabilities: MarketDataCapabilities): String = when (mode) {
        TradingMode.STOCKS -> "Underlying equity signal."
        TradingMode.F_AND_O -> if (hasVerifiedDerivativeContracts(capabilities)) {
            "Verified derivative contract data available. Contract selection must use provider-supplied values only."
        } else {
            "F&O is unavailable until a verified derivative contract feed is connected. No contract, strike, premium, OI, IV or Greeks are inferred from underlying-only data."
        }
    }
}
