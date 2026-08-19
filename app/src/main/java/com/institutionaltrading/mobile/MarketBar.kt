package com.institutionaltrading.mobile

import java.time.Duration
import java.time.Instant

/**
 * A single source bar. Timestamps must describe the completed source bar in canonical UTC.
 * No signal may be produced from a bar unless [isClosed] is true and validation succeeds.
 */
data class MarketBar(
    val symbol: String,
    val timeframe: String,
    val sourceTimestamp: String,
    val fetchedAt: String,
    val open: Double,
    val high: Double,
    val low: Double,
    val close: Double,
    val isClosed: Boolean,
    val provenance: String,
)

data class ValidatedBarSeries(
    val symbol: String,
    val timeframe: String,
    val bars: List<MarketBar>,
)

object MarketDataQuality {
    fun validateBar(bar: MarketBar, now: Instant, maxAge: Duration): MarketBar {
        val symbol = runCatching { Validation.normalizeWatchlistItem(bar.symbol) }
            .getOrElse { throw IllegalArgumentException("Bar symbol is invalid") }
        require(bar.timeframe in Validation.defaultTimeframes) { "Bar timeframe is unsupported" }
        require(bar.isClosed) { "Open or forming bars are rejected" }
        require(bar.provenance.isNotBlank()) { "Source provenance is required" }
        require(!maxAge.isNegative && !maxAge.isZero) { "Maximum source age must be positive" }

        val sourceTime = parseCanonicalUtc(bar.sourceTimestamp, "Source timestamp")
        val fetchedTime = parseCanonicalUtc(bar.fetchedAt, "Fetch timestamp")
        require(!sourceTime.isAfter(fetchedTime)) { "Fetch timestamp precedes source timestamp" }
        require(!fetchedTime.isAfter(now)) { "Fetch timestamp is in the future" }
        require(!sourceTime.isAfter(now)) { "Source timestamp is in the future" }
        require(Duration.between(sourceTime, now) <= maxAge) { "Source bar is stale" }

        val prices = listOf(bar.open, bar.high, bar.low, bar.close)
        require(prices.all { it.isFinite() && it > 0.0 }) { "OHLC prices must be finite and positive" }
        require(bar.high >= maxOf(bar.open, bar.close, bar.low)) { "High is inconsistent with OHLC values" }
        require(bar.low <= minOf(bar.open, bar.close, bar.high)) { "Low is inconsistent with OHLC values" }

        return bar.copy(symbol = symbol)
    }

    fun validateSeries(
        bars: List<MarketBar>,
        now: Instant,
        maxAge: Duration,
        minimumSamples: Int,
    ): ValidatedBarSeries {
        require(minimumSamples > 0) { "Minimum sample count must be positive" }
        require(bars.size >= minimumSamples) { "Insufficient bar sample" }

        val validated = bars.map { validateBar(it, now, maxAge) }
        val symbol = validated.first().symbol
        val timeframe = validated.first().timeframe
        require(validated.all { it.symbol == symbol }) { "Mixed symbols are rejected" }
        require(validated.all { it.timeframe == timeframe }) { "Mixed timeframes are rejected" }

        val times = validated.map { Instant.parse(it.sourceTimestamp) }
        require(times.zipWithNext().all { (left, right) -> left.isBefore(right) }) {
            "Bar timestamps must be strictly increasing and unique"
        }
        return ValidatedBarSeries(symbol, timeframe, validated)
    }

    private fun parseCanonicalUtc(value: String, label: String): Instant {
        val parsed = runCatching { Instant.parse(value) }
            .getOrElse { throw IllegalArgumentException("$label must be ISO-8601 UTC") }
        require(parsed.toString() == value) { "$label must be canonical UTC" }
        return parsed
    }
}
