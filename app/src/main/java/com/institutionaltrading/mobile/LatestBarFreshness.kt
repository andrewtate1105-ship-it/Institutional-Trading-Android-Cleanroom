package com.institutionaltrading.mobile

import java.time.Duration
import java.time.Instant

/**
 * Freshness guard for user-facing signal analysis.
 *
 * Historical bars may span long periods, so only the latest closed bar is checked
 * for recency. Intraday windows are intentionally tight enough to reject a
 * typical 15-minute delayed quote feed instead of presenting it as a live setup.
 */
object LatestBarFreshness {
    fun requireCurrent(series: ValidatedBarSeries, now: Instant): ValidatedBarSeries {
        require(series.bars.isNotEmpty()) { "Validated bar series is empty" }
        require(series.bars.all { it.isClosed }) { "Current-signal analysis accepts closed bars only" }

        val latest = series.bars.last()
        val sourceTime = runCatching { Instant.parse(latest.sourceTimestamp) }
            .getOrElse { throw MarketDataUnavailableException("Latest source timestamp must be ISO-8601 UTC") }
        if (sourceTime.toString() != latest.sourceTimestamp) {
            throw MarketDataUnavailableException("Latest source timestamp must be canonical UTC")
        }
        if (sourceTime.isAfter(now)) {
            throw MarketDataUnavailableException("Latest closed bar timestamp is in the future")
        }

        val age = Duration.between(sourceTime, now)
        val maxAge = maxAgeFor(series.timeframe)
        if (age > maxAge) {
            throw MarketDataUnavailableException(
                "Latest closed bar is stale for ${series.timeframe} analysis (${age.toMinutes()} minutes old; maximum ${maxAge.toMinutes()} minutes). A delayed feed is not accepted for a current signal."
            )
        }
        return series
    }

    fun maxAgeFor(timeframe: String): Duration = when (timeframe) {
        "5M" -> Duration.ofMinutes(10)
        "15M" -> Duration.ofMinutes(25)
        "1H" -> Duration.ofMinutes(70)
        "D" -> Duration.ofDays(3)
        "W" -> Duration.ofDays(14)
        else -> throw IllegalArgumentException("Unsupported timeframe")
    }
}
