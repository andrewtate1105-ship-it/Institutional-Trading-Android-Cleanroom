package com.institutionaltrading.mobile

import java.time.Duration
import java.time.Instant

/**
 * Freshness guard for user-facing signal analysis.
 *
 * Historical bars may span long periods, so only the latest closed bar is checked
 * for recency. This prevents a valid historical CSV from being mistaken for a
 * current trading signal while preserving older bars needed for structure.
 */
object LatestBarFreshness {
    fun requireCurrent(series: ValidatedBarSeries, now: Instant): ValidatedBarSeries {
        require(series.bars.isNotEmpty()) { "Validated bar series is empty" }
        require(series.bars.all { it.isClosed }) { "Current-signal analysis accepts closed bars only" }

        val latest = series.bars.last()
        val sourceTime = runCatching { Instant.parse(latest.sourceTimestamp) }
            .getOrElse { throw IllegalArgumentException("Latest source timestamp must be ISO-8601 UTC") }
        require(sourceTime.toString() == latest.sourceTimestamp) { "Latest source timestamp must be canonical UTC" }
        require(!sourceTime.isAfter(now)) { "Latest closed bar timestamp is in the future" }

        val age = Duration.between(sourceTime, now)
        val maxAge = maxAgeFor(series.timeframe)
        require(age <= maxAge) {
            "Latest closed bar is stale for ${series.timeframe} analysis (${age.toMinutes()} minutes old; maximum ${maxAge.toMinutes()} minutes). Load current machine-readable closed-bar data."
        }
        return series
    }

    fun maxAgeFor(timeframe: String): Duration = when (timeframe) {
        "5M" -> Duration.ofMinutes(15)
        "15M" -> Duration.ofMinutes(45)
        "1H" -> Duration.ofHours(3)
        "D" -> Duration.ofDays(3)
        "W" -> Duration.ofDays(14)
        else -> throw IllegalArgumentException("Unsupported timeframe")
    }
}
