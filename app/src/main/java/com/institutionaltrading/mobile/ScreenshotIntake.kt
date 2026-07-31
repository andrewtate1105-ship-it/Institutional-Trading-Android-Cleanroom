package com.institutionaltrading.mobile

import android.content.ContentResolver
import android.net.Uri

object ScreenshotIntake {
    data class Metadata(val symbol: String, val timeframe: String, val capturedAtIso: String)

    fun validate(resolver: ContentResolver, uri: Uri, metadata: Metadata?): Result<Unit> = runCatching {
        require(metadata != null) { "Screenshot metadata is required" }
        require(metadata.symbol.isNotBlank()) { "Screenshot symbol is required" }
        require(metadata.timeframe in setOf("5M", "15M", "1H", "D", "W")) { "Unsupported screenshot timeframe" }
        val type = resolver.getType(uri)
        require(type == "image/png" || type == "image/jpeg") { "Only PNG or JPEG screenshots are accepted" }
        val size = resolver.openAssetFileDescriptor(uri, "r")?.use { it.length } ?: -1
        require(size in 20_000..15_000_000) { "Screenshot is unreadable, tiny, or too large" }
    }
}
