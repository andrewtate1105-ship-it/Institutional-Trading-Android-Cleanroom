package com.institutionaltrading.mobile

import java.time.Duration
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenshotSubmissionValidatorTest {
    private val receivedAt = Instant.parse("2026-08-02T08:00:00Z")

    private fun valid() = ScreenshotSubmission(
        symbol = "reliance",
        timeframe = "15M",
        capturedAt = "2026-08-02T07:55:00Z",
        sourceApp = "Groww",
        imageSha256 = "A".repeat(64),
        widthPixels = 1080,
        heightPixels = 1920,
        byteSize = 250_000,
        explicitUserSubmission = true,
        fullChartVisible = true,
        priceScaleVisible = true,
        symbolVisible = true,
        timeframeVisible = true,
        readable = true,
    )

    @Test fun acceptsAndNormalizesCompleteFreshSubmission() {
        val normalized = ScreenshotSubmissionValidator.validate(valid(), receivedAt, Duration.ofMinutes(15))
        assertEquals("RELIANCE", normalized.symbol)
        assertEquals("a".repeat(64), normalized.imageSha256)
    }

    @Test fun rejectsMetadataFreeStaleFutureAndImplicitSubmissions() {
        val cases = listOf(
            valid().copy(symbol = ""),
            valid().copy(sourceApp = ""),
            valid().copy(imageSha256 = "bad"),
            valid().copy(capturedAt = "2026-08-02T07:00:00Z"),
            valid().copy(capturedAt = "2026-08-02T08:01:00Z"),
            valid().copy(explicitUserSubmission = false),
        )
        cases.forEach { assertTrue(runCatching { ScreenshotSubmissionValidator.validate(it, receivedAt, Duration.ofMinutes(15)) }.isFailure) }
    }

    @Test fun rejectsCroppedUnreadableLowResolutionAndHiddenMetadata() {
        val cases = listOf(
            valid().copy(fullChartVisible = false),
            valid().copy(readable = false),
            valid().copy(priceScaleVisible = false),
            valid().copy(symbolVisible = false),
            valid().copy(timeframeVisible = false),
            valid().copy(widthPixels = 640),
            valid().copy(heightPixels = 640),
            valid().copy(byteSize = 10_000),
            valid().copy(byteSize = 20_000_000),
        )
        cases.forEach { assertTrue(runCatching { ScreenshotSubmissionValidator.validate(it, receivedAt, Duration.ofMinutes(15)) }.isFailure) }
    }

    @Test fun rejectsUnsupportedTimeframeAndNonCanonicalTimestamp() {
        assertTrue(runCatching {
            ScreenshotSubmissionValidator.validate(valid().copy(timeframe = "30M"), receivedAt, Duration.ofMinutes(15))
        }.isFailure)
        assertTrue(runCatching {
            ScreenshotSubmissionValidator.validate(valid().copy(capturedAt = "2026-08-02T13:25:00+05:30"), receivedAt, Duration.ofMinutes(15))
        }.isFailure)
    }

    @Test fun rejectsInvalidFreshnessPolicy() {
        assertTrue(runCatching {
            ScreenshotSubmissionValidator.validate(valid(), receivedAt, Duration.ZERO)
        }.isFailure)
    }
}
