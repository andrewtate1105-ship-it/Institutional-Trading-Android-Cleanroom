package com.institutionaltrading.mobile

import java.time.Duration
import java.time.Instant

/**
 * Fail-closed automatic analysis boundary.
 *
 * This coordinator intentionally emits NO_TRADE until a documented and validated
 * strategy module is implemented. It exists to connect lawful source validation,
 * closed-bar data-quality checks, provenance, and alert generation without
 * inventing trading rules.
 */
object AutomaticAnalysisPipeline {
    fun analyzeWithoutStrategy(
        source: DataSourceDescriptor,
        bars: List<MarketBar>,
        now: Instant,
        maxAge: Duration,
        minimumSamples: Int,
    ): SignalAlert {
        val validatedSource = DataSourcePolicy.validate(source)
        require(validatedSource.automaticIngestionAllowed) {
            "Automatic analysis requires a lawful machine-readable source"
        }

        val series = MarketDataQuality.validateSeries(bars, now, maxAge, minimumSamples)
        val latest = series.bars.last()
        require(source.sourceTimestamp == latest.sourceTimestamp) {
            "Source descriptor timestamp does not match the latest closed bar"
        }
        require(series.bars.all { it.provenance == validatedSource.provenance }) {
            "Bar provenance does not match the validated source"
        }

        val warnings = buildList {
            add("No documented and validated strategy module is enabled; signal forced to NO_TRADE.")
            when (validatedSource.cadence) {
                SourceCadence.REAL_TIME -> Unit
                SourceCadence.DELAYED -> add("Source cadence is DELAYED and must not be described as real-time.")
                SourceCadence.END_OF_DAY -> add("Source cadence is END_OF_DAY and must not be described as real-time.")
                SourceCadence.MANUAL_CSV,
                SourceCadence.SCREENSHOT_AUXILIARY -> error("Non-automatic cadence passed automatic analysis boundary")
            }
        }

        return SignalAlert(
            direction = SignalDirection.NO_TRADE,
            symbol = series.symbol,
            timeframe = series.timeframe,
            sourceTimestamp = latest.sourceTimestamp,
            entryZone = "N/A",
            invalidation = "No validated strategy decision is available.",
            stopLoss = "N/A",
            targets = emptyList(),
            rewardToRisk = "N/A",
            riskGuidance = "No position while strategy evidence is unavailable or unvalidated.",
            confidenceDiagnostics = "Fail-closed: data passed quality checks, but strategy validation is incomplete.",
            provenance = validatedSource.provenance,
            warnings = warnings,
        )
    }
}
