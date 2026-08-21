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

enum class TradingDataTier {
    UNAVAILABLE,
    UNDERLYING_ONLY,
    VERIFIED_DERIVATIVES,
}

object TradingModePolicy {
    /**
     * Both modes may analyze verified closed underlying bars. F&O is intentionally split into two
     * data tiers: underlying-only analysis can provide directional levels on the cash/index
     * instrument, while an actual futures/options contract recommendation is allowed only when the
     * active provider supplies verified derivative contracts. This keeps the app useful with the
     * temporary TradingView feed without ever inventing strike, premium, OI, IV or Greeks.
     */
    fun dataTier(mode: TradingMode, capabilities: MarketDataCapabilities): TradingDataTier {
        if (!capabilities.closedOhlcBars) return TradingDataTier.UNAVAILABLE
        return when (mode) {
            TradingMode.STOCKS -> TradingDataTier.UNDERLYING_ONLY
            TradingMode.F_AND_O -> if (capabilities.derivativesContracts) {
                TradingDataTier.VERIFIED_DERIVATIVES
            } else {
                TradingDataTier.UNDERLYING_ONLY
            }
        }
    }

    fun canAnalyze(mode: TradingMode, capabilities: MarketDataCapabilities): Boolean =
        dataTier(mode, capabilities) != TradingDataTier.UNAVAILABLE

    fun hasVerifiedDerivativeContracts(capabilities: MarketDataCapabilities): Boolean =
        capabilities.derivativesContracts

    fun resultNotice(mode: TradingMode, capabilities: MarketDataCapabilities): String = when (mode) {
        TradingMode.STOCKS -> "Underlying equity signal."
        TradingMode.F_AND_O -> when (dataTier(mode, capabilities)) {
            TradingDataTier.VERIFIED_DERIVATIVES ->
                "Verified derivative contract data available. Contract selection must use provider-supplied values only."
            TradingDataTier.UNDERLYING_ONLY ->
                "F&O directional view uses verified underlying bars only. Entry, stop and targets refer to the underlying instrument; no futures/options contract, strike, premium, OI, IV or Greeks are inferred."
            TradingDataTier.UNAVAILABLE ->
                "F&O analysis unavailable because verified closed underlying bars are unavailable."
        }
    }
}
