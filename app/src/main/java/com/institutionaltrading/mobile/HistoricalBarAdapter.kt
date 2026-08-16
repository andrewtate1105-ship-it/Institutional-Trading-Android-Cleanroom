package com.institutionaltrading.mobile

import java.time.Instant

object HistoricalBarAdapter {
    fun toValidatedSeries(
        rows: List<BacktestRow>,
        symbol: String,
        timeframe: String,
        provenance: String = "USER_SUPPLIED_HISTORICAL_CSV",
    ): ValidatedBarSeries {
        require(rows.isNotEmpty()) { "Historical dataset is empty" }
        val normalizedSymbol = Validation.normalizeWatchlistItem(symbol)
        require(timeframe in Validation.defaultTimeframes) { "Historical timeframe is unsupported" }
        require(provenance.isNotBlank()) { "Historical provenance is required" }

        rows.zipWithNext().forEachIndexed { index, (previous, current) ->
            require(Instant.parse(current.timestamp).isAfter(Instant.parse(previous.timestamp))) {
                "Historical timestamps must be strictly increasing at row ${index + 2}"
            }
        }

        val bars = rows.map { row ->
            MarketBar(
                symbol = normalizedSymbol,
                timeframe = timeframe,
                sourceTimestamp = row.timestamp,
                fetchedAt = row.timestamp,
                open = row.open,
                high = row.high,
                low = row.low,
                close = row.close,
                isClosed = true,
                provenance = provenance,
            )
        }
        return ValidatedBarSeries(normalizedSymbol, timeframe, bars)
    }
}
