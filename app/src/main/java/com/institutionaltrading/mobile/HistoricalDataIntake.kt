package com.institutionaltrading.mobile

import java.io.InputStream

data class HistoricalDataSummary(
    val rowCount: Int,
    val firstTimestamp: String,
    val lastTimestamp: String,
)

object HistoricalDataIntake {
    const val maxBytes: Int = 5 * 1024 * 1024

    fun readBounded(input: InputStream): ByteArray {
        val buffer = ByteArray(32 * 1024)
        val output = java.io.ByteArrayOutputStream(minOf(maxBytes, buffer.size))
        var total = 0
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            total += read
            require(total <= maxBytes) { "CSV file exceeds 5 MB safety limit" }
            output.write(buffer, 0, read)
        }
        return output.toByteArray()
    }

    fun validateCsv(bytes: ByteArray): HistoricalDataSummary {
        require(bytes.isNotEmpty()) { "CSV file is empty" }
        require(bytes.size <= maxBytes) { "CSV file exceeds 5 MB safety limit" }
        val text = bytes.toString(Charsets.UTF_8)
        require(!text.contains('\u0000')) { "CSV file is not valid UTF-8 text" }
        val rows = BacktestCsvImporter.parse(text)
        require(rows.isNotEmpty()) { "CSV contains no validated rows" }
        return HistoricalDataSummary(
            rowCount = rows.size,
            firstTimestamp = rows.first().timestamp,
            lastTimestamp = rows.last().timestamp,
        )
    }
}
