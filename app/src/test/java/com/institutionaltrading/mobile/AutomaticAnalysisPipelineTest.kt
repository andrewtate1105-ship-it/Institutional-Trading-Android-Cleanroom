package com.institutionaltrading.mobile

import java.time.Duration
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AutomaticAnalysisPipelineTest {
    private fun source(
        cadence: SourceCadence = SourceCadence.DELAYED,
        machineReadable: Boolean = true,
        timestamp: String = "2026-08-07T09:45:00Z",
    ) = DataSourceDescriptor(
        sourceId = "official-nse-fixture",
        sourceUrl = if (machineReadable) "https://www.nseindia.com/market-data" else null,
        cadence = cadence,
        machineReadable = machineReadable,
        lawfulAccessConfirmed = true,
        antiBotBypassUsed = false,
        sourceTimestamp = timestamp,
        fetchedAt = "2026-08-07T09:46:00Z",
    )

    private fun bars(source: DataSourceDescriptor, provenanceOverride: String? = null): List<MarketBar> {
        val provenance = provenanceOverride ?: DataSourcePolicy.validate(source).provenance
        return listOf("09:15:00Z", "09:30:00Z", "09:45:00Z").mapIndexed { index, time ->
            val price = 100.0 + index
            MarketBar(
                symbol = "reliance",
                timeframe = "15M",
                sourceTimestamp = "2026-08-07T$time",
                fetchedAt = "2026-08-07T09:46:00Z",
                open = price,
                high = price + 1.0,
                low = price - 1.0,
                close = price + 0.5,
                isClosed = true,
                provenance = provenance,
            )
        }
    }

    @Test fun validatedAutomaticDataStillForcesNoTradeWithoutStrategy() {
        val source = source()
        val alert = AutomaticAnalysisPipeline.analyzeWithoutStrategy(
            source = source,
            bars = bars(source),
            now = Instant.parse("2026-08-07T09:47:00Z"),
            maxAge = Duration.ofHours(1),
            minimumSamples = 3,
        )

        assertEquals(SignalDirection.NO_TRADE, alert.direction)
        assertEquals("RELIANCE", alert.symbol)
        assertEquals("2026-08-07T09:45:00Z", alert.sourceTimestamp)
        assertTrue(alert.warnings.any { it.contains("DELAYED") && it.contains("not be described as real-time") })
        assertTrue(AlertFormatter.inApp(alert).contains("Direction: NO_TRADE"))
    }

    @Test fun rejectsManualOrScreenshotSourcesAtAutomaticBoundary() {
        val source = source(cadence = SourceCadence.SCREENSHOT_AUXILIARY, machineReadable = false)
        assertTrue(runCatching {
            AutomaticAnalysisPipeline.analyzeWithoutStrategy(
                source, emptyList(), Instant.parse("2026-08-07T09:47:00Z"), Duration.ofHours(1), 1
            )
        }.isFailure)
    }

    @Test fun rejectsProvenanceMismatch() {
        val source = source()
        assertTrue(runCatching {
            AutomaticAnalysisPipeline.analyzeWithoutStrategy(
                source,
                bars(source, provenanceOverride = "unverified-source"),
                Instant.parse("2026-08-07T09:47:00Z"),
                Duration.ofHours(1),
                3,
            )
        }.isFailure)
    }

    @Test fun rejectsDescriptorTimestampThatDoesNotMatchLatestClosedBar() {
        val source = source(timestamp = "2026-08-07T09:30:00Z")
        assertTrue(runCatching {
            AutomaticAnalysisPipeline.analyzeWithoutStrategy(
                source,
                bars(source),
                Instant.parse("2026-08-07T09:47:00Z"),
                Duration.ofHours(1),
                3,
            )
        }.isFailure)
    }
}
