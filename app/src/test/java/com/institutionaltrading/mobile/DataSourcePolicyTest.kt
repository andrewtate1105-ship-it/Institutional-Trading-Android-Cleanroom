package com.institutionaltrading.mobile

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DataSourcePolicyTest {
    private fun descriptor(
        url: String? = "https://www.nseindia.com/api/example",
        cadence: SourceCadence = SourceCadence.DELAYED,
        machineReadable: Boolean = true,
        lawful: Boolean = true,
        bypass: Boolean = false,
        sourceTimestamp: String = "2026-08-04T09:45:00Z",
        fetchedAt: String = "2026-08-04T09:46:00Z",
    ) = DataSourceDescriptor(
        sourceId = "official-nse-public",
        sourceUrl = url,
        cadence = cadence,
        machineReadable = machineReadable,
        lawfulAccessConfirmed = lawful,
        antiBotBypassUsed = bypass,
        sourceTimestamp = sourceTimestamp,
        fetchedAt = fetchedAt,
    )

    @Test fun allowsAutomaticIngestionOnlyForValidatedMachineReadableSource() {
        val validated = DataSourcePolicy.validate(descriptor())
        assertTrue(validated.automaticIngestionAllowed)
        assertTrue(validated.provenance.contains("cadence=DELAYED"))
        assertTrue(validated.provenance.contains("source_timestamp=2026-08-04T09:45:00Z"))
    }

    @Test fun rejectsUnlawfulOrBypassedSources() {
        assertTrue(runCatching { DataSourcePolicy.validate(descriptor(lawful = false)) }.isFailure)
        assertTrue(runCatching { DataSourcePolicy.validate(descriptor(bypass = true)) }.isFailure)
    }

    @Test fun rejectsUnapprovedHostsAndCredentialBearingUrls() {
        assertTrue(runCatching {
            DataSourcePolicy.validate(descriptor(url = "https://example.com/market.csv"))
        }.isFailure)
        assertTrue(runCatching {
            DataSourcePolicy.validate(descriptor(url = "https://www.nseindia.com/api/data?token=secret"))
        }.isFailure)
        assertTrue(runCatching {
            DataSourcePolicy.validate(descriptor(url = "http://www.nseindia.com/api/data"))
        }.isFailure)
    }

    @Test fun screenshotIsAuxiliaryAndNeverAutomatic() {
        val validated = DataSourcePolicy.validate(descriptor(
            url = null,
            cadence = SourceCadence.SCREENSHOT_AUXILIARY,
            machineReadable = false,
        ))
        assertFalse(validated.automaticIngestionAllowed)
    }

    @Test fun nonMachineReadableFeedClaimFailsClosed() {
        assertTrue(runCatching {
            DataSourcePolicy.validate(descriptor(machineReadable = false, cadence = SourceCadence.REAL_TIME))
        }.isFailure)
    }

    @Test fun rejectsNonCanonicalOrImpossibleTimestamps() {
        assertTrue(runCatching {
            DataSourcePolicy.validate(descriptor(sourceTimestamp = "2026-08-04T15:15:00+05:30"))
        }.isFailure)
        assertTrue(runCatching {
            DataSourcePolicy.validate(descriptor(
                sourceTimestamp = "2026-08-04T09:47:00Z",
                fetchedAt = "2026-08-04T09:46:00Z",
            ))
        }.isFailure)
    }
}
