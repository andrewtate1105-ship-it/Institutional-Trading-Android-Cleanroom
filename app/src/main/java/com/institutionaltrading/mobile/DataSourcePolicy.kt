package com.institutionaltrading.mobile

import java.net.URI
import java.time.Instant

enum class SourceCadence {
    REAL_TIME,
    DELAYED,
    END_OF_DAY,
    MANUAL_CSV,
    SCREENSHOT_AUXILIARY,
}

data class DataSourceDescriptor(
    val sourceId: String,
    val sourceUrl: String?,
    val cadence: SourceCadence,
    val machineReadable: Boolean,
    val lawfulAccessConfirmed: Boolean,
    val antiBotBypassUsed: Boolean,
    val sourceTimestamp: String,
    val fetchedAt: String,
)

data class ValidatedDataSource(
    val sourceId: String,
    val sourceUrl: String?,
    val cadence: SourceCadence,
    val automaticIngestionAllowed: Boolean,
    val provenance: String,
)

object DataSourcePolicy {
    private val officialHosts = setOf(
        "www.nseindia.com",
        "nseindia.com",
        "archives.nseindia.com",
        "nsearchives.nseindia.com",
    )

    fun validate(source: DataSourceDescriptor): ValidatedDataSource {
        val sourceId = source.sourceId.trim()
        require(sourceId.matches(Regex("^[A-Za-z0-9._:-]{3,80}$"))) { "Source identifier is invalid" }
        require(source.lawfulAccessConfirmed) { "Lawful source access is not confirmed" }
        require(!source.antiBotBypassUsed) { "Sources obtained by bypassing access controls are rejected" }

        val sourceTime = canonicalInstant(source.sourceTimestamp, "Source timestamp")
        val fetchedTime = canonicalInstant(source.fetchedAt, "Fetch timestamp")
        require(!fetchedTime.isBefore(sourceTime)) { "Fetch timestamp precedes source timestamp" }

        val normalizedUrl = source.sourceUrl?.trim()?.takeIf { it.isNotEmpty() }?.let(::validateUrl)
        val automatic = source.machineReadable && source.cadence != SourceCadence.SCREENSHOT_AUXILIARY
        if (automatic) require(normalizedUrl != null) { "Automatic ingestion requires a validated source URL" }
        if (!source.machineReadable) {
            require(source.cadence == SourceCadence.MANUAL_CSV || source.cadence == SourceCadence.SCREENSHOT_AUXILIARY) {
                "Non-machine-readable sources may only be manual CSV or screenshot auxiliary inputs"
            }
        }
        if (source.cadence == SourceCadence.SCREENSHOT_AUXILIARY) {
            require(!source.machineReadable) { "Screenshots are not machine-readable market feeds" }
        }

        val provenance = buildString {
            append("source=").append(sourceId)
            append(";cadence=").append(source.cadence.name)
            append(";source_timestamp=").append(sourceTime)
            append(";fetched_at=").append(fetchedTime)
            append(";machine_readable=").append(source.machineReadable)
            normalizedUrl?.let { append(";url=").append(it) }
        }

        return ValidatedDataSource(
            sourceId = sourceId,
            sourceUrl = normalizedUrl,
            cadence = source.cadence,
            automaticIngestionAllowed = automatic,
            provenance = provenance,
        )
    }

    private fun validateUrl(raw: String): String {
        val uri = runCatching { URI(raw) }.getOrElse { throw IllegalArgumentException("Source URL is malformed") }
        require(uri.scheme.equals("https", ignoreCase = true)) { "Source URL must use HTTPS" }
        require(uri.userInfo == null) { "Source URL must not contain credentials" }
        require(uri.port == -1 || uri.port == 443) { "Source URL uses an unsupported port" }
        require(uri.fragment == null) { "Source URL fragments are not allowed" }
        val host = uri.host?.lowercase() ?: throw IllegalArgumentException("Source URL host is missing")
        require(host in officialHosts) { "Source URL host is not whitelisted" }
        val query = uri.rawQuery.orEmpty().lowercase()
        require(listOf("token=", "api_key=", "apikey=", "secret=").none(query::contains)) {
            "Source URL must not contain credentials"
        }
        return URI("https", null, host, if (uri.port == 443) -1 else uri.port, uri.rawPath.ifBlank { "/" }, uri.rawQuery, null).toString()
    }

    private fun canonicalInstant(value: String, label: String): Instant {
        val instant = runCatching { Instant.parse(value) }
            .getOrElse { throw IllegalArgumentException("$label must be ISO-8601 UTC") }
        require(instant.toString() == value) { "$label must be canonical UTC" }
        return instant
    }
}
