package com.institutionaltrading.mobile

/**
 * Signals that current, validated machine-readable market data is unavailable
 * for the requested instrument/timeframe. The UI must fail closed rather than
 * infer or fabricate a trading signal.
 */
class MarketDataUnavailableException(message: String) : IllegalArgumentException(message)
