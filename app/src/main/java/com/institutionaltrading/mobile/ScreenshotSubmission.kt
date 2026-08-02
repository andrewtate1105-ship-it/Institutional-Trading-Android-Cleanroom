package com.institutionaltrading.mobile

import java.time.Duration
import java.time.Instant

data class ScreenshotSubmission(
    val symbol: String,
    val timeframe: String,
    val capturedAt: String,
    val sourceApp: String,
    val imageSha256: String,
    val widthPixels: Int,
    val heightPixels: Int,
    val byteSize: Long,
    val explicitUserSubmission: Boolean,
    val fullChartVisible: Boolean,
    val priceScaleVisible: Boolean,
    val symbolVisible: Boolean,
    val timeframeVisible: Boolean,
    val readable: Boolean,
)

object ScreenshotSubmissionValidator {
    const val minimumDimensionPixels = 720
    const val minimumByteSize = 30_000L
    const val maximumByteSize = 15_000_000L

    fun validate(
        submission: ScreenshotSubmission,
        receivedAt: Instant,
        maximumAge: Duration,
    ): ScreenshotSubmission {
        require(!maximumAge.isNegative && !maximumAge.isZero) { "Screenshot freshness limit must be positive" }
        require(submission.explicitUserSubmission) { "Screenshots are processed only when explicitly submitted" }

        val symbol = runCatching { Validation.normalizeWatchlistItem(submission.symbol) }
            .getOrElse { throw IllegalArgumentException("Screenshot symbol metadata is missing or invalid") }
        require(submission.timeframe in Validation.defaultTimeframes) { "Screenshot timeframe metadata is missing or unsupported" }
        require(submission.sourceApp.isNotBlank()) { "Screenshot source metadata is required" }
        require(submission.imageSha256.matches(Regex("^[a-fA-F0-9]{64}$"))) { "Screenshot digest metadata is invalid" }

        val capturedAt = runCatching { Instant.parse(submission.capturedAt) }
            .getOrElse { throw IllegalArgumentException("Screenshot capture timestamp must be ISO-8601 UTC") }
        require(capturedAt.toString() == submission.capturedAt) { "Screenshot capture timestamp must be canonical UTC" }
        require(!capturedAt.isAfter(receivedAt)) { "Future-dated screenshot rejected" }
        require(Duration.between(capturedAt, receivedAt) <= maximumAge) { "Stale screenshot rejected" }

        require(submission.widthPixels >= minimumDimensionPixels && submission.heightPixels >= minimumDimensionPixels) {
            "Screenshot resolution is too low"
        }
        require(submission.byteSize in minimumByteSize..maximumByteSize) { "Screenshot file size is unsupported" }
        require(submission.readable) { "Unreadable screenshot rejected" }
        require(submission.fullChartVisible) { "Cropped screenshot rejected" }
        require(submission.priceScaleVisible) { "Screenshot must show the price scale" }
        require(submission.symbolVisible) { "Screenshot must visibly show the symbol" }
        require(submission.timeframeVisible) { "Screenshot must visibly show the timeframe" }

        return submission.copy(
            symbol = symbol,
            sourceApp = submission.sourceApp.trim(),
            imageSha256 = submission.imageSha256.lowercase(),
        )
    }
}
