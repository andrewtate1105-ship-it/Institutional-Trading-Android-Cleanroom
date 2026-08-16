package com.institutionaltrading.mobile

data class HistoricalDataSummary(
    val rowCount: Int,
    val firstTimestamp: String,
    val lastTimestamp: String,
)

object HistoricalDataIntake {
    const val maxBytes: Int = 5 * 1024 * 1024

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
