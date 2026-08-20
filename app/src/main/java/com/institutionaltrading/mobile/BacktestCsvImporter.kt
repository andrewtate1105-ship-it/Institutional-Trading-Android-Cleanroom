package com.institutionaltrading.mobile

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Validated historical row used only for backtesting/statistical validation. */
data class BacktestRow(
    val timestamp: String,
    val open: Double,
    val high: Double,
    val low: Double,
    val close: Double,
    val previousClose: Double,
    val volume: Double? = null,
)

object BacktestCsvImporter {
    private enum class Field { TIMESTAMP, OPEN, HIGH, LOW, CLOSE, PREVIOUS_CLOSE, VOLUME }

    private val aliases = mapOf(
        "date" to Field.TIMESTAMP,
        "datetime" to Field.TIMESTAMP,
        "timestamp" to Field.TIMESTAMP,
        "time" to Field.TIMESTAMP,
        "open" to Field.OPEN,
        "high" to Field.HIGH,
        "low" to Field.LOW,
        "close" to Field.CLOSE,
        "previousclose" to Field.PREVIOUS_CLOSE,
        "prevclose" to Field.PREVIOUS_CLOSE,
        "previousclosingprice" to Field.PREVIOUS_CLOSE,
        "volume" to Field.VOLUME,
        "vol" to Field.VOLUME,
    )

    fun parse(csv: String): List<BacktestRow> {
        val lines = csv.lineSequence().filter { it.isNotBlank() }.toList()
        require(lines.size >= 2) { "CSV must contain a header and at least one data row" }

        val headers = parseLine(lines.first())
        require(headers.isNotEmpty()) { "CSV header is empty" }
        val mapped = headers.map { header -> aliases[normalizeHeader(header)] }
        val required = setOf(Field.TIMESTAMP, Field.OPEN, Field.HIGH, Field.LOW, Field.CLOSE, Field.PREVIOUS_CLOSE)
        require(mapped.filterNotNull().toSet().containsAll(required)) { "CSV is missing one or more required columns" }
        required.forEach { field ->
            require(mapped.count { it == field } == 1) { "CSV contains ambiguous duplicate required columns" }
        }
        require(mapped.count { it == Field.VOLUME } <= 1) { "CSV contains ambiguous duplicate volume columns" }

        val indexes = required.associateWith { field -> mapped.indexOf(field) }
        val volumeIndex = mapped.indexOf(Field.VOLUME).takeIf { it >= 0 }
        val rows = lines.drop(1).mapIndexed { offset, line ->
            val rowNumber = offset + 2
            val cells = parseLine(line)
            require(cells.size == headers.size) { "CSV row $rowNumber has ${cells.size} columns; expected ${headers.size}" }
            fun value(field: Field): String = cells[indexes.getValue(field)].trim()
            val timestamp = parseTimestamp(value(Field.TIMESTAMP), rowNumber)
            val volume = volumeIndex?.let { index -> parseOptionalVolume(cells[index].trim(), rowNumber) }
            val row = BacktestRow(
                timestamp = timestamp,
                open = parseNumber(value(Field.OPEN), "Open", rowNumber),
                high = parseNumber(value(Field.HIGH), "High", rowNumber),
                low = parseNumber(value(Field.LOW), "Low", rowNumber),
                close = parseNumber(value(Field.CLOSE), "Close", rowNumber),
                previousClose = parseNumber(value(Field.PREVIOUS_CLOSE), "Previous Close", rowNumber),
                volume = volume,
            )
            validateOhlc(row, rowNumber)
            row
        }

        require(rows.map { it.timestamp }.toSet().size == rows.size) { "CSV contains duplicate timestamps" }
        rows.zipWithNext().forEachIndexed { index, (previous, current) ->
            require(Instant.parse(current.timestamp).isAfter(Instant.parse(previous.timestamp))) {
                "CSV timestamps must be strictly chronological at row ${index + 3}"
            }
            require(nearlyEqual(current.previousClose, previous.close)) {
                "Previous Close does not match the prior Close at row ${index + 3}"
            }
        }
        return rows
    }

    private fun normalizeHeader(value: String): String = value
        .trim()
        .lowercase(Locale.ROOT)
        .replace(Regex("[^a-z0-9]"), "")

    private fun parseTimestamp(value: String, rowNumber: Int): String {
        require(value.isNotBlank()) { "Timestamp is empty at row $rowNumber" }
        runCatching { Instant.parse(value) }.getOrNull()?.let { instant ->
            val canonicalMillis = DateTimeFormatter.ISO_INSTANT.format(instant)
            require(value.endsWith("Z")) { "Timestamp must be UTC at row $rowNumber" }
            return canonicalMillis
        }

        val date = runCatching { LocalDate.parse(value) }.getOrElse {
            throw IllegalArgumentException("Timestamp is invalid at row $rowNumber")
        }
        return date.atStartOfDay().toInstant(ZoneOffset.UTC).toString()
    }

    private fun parseNumber(value: String, label: String, rowNumber: Int): Double {
        val number = value.toDoubleOrNull()
            ?: throw IllegalArgumentException("$label is not numeric at row $rowNumber")
        require(number.isFinite() && number > 0.0) { "$label must be finite and positive at row $rowNumber" }
        return number
    }

    private fun parseOptionalVolume(value: String, rowNumber: Int): Double? {
        if (value.isBlank()) return null
        val number = value.toDoubleOrNull()
            ?: throw IllegalArgumentException("Volume is not numeric at row $rowNumber")
        require(number.isFinite() && number >= 0.0) { "Volume must be finite and non-negative at row $rowNumber" }
        return number
    }

    private fun validateOhlc(row: BacktestRow, rowNumber: Int) {
        require(row.high >= maxOf(row.open, row.close, row.low)) { "High is inconsistent at row $rowNumber" }
        require(row.low <= minOf(row.open, row.close, row.high)) { "Low is inconsistent at row $rowNumber" }
    }

    private fun nearlyEqual(a: Double, b: Double): Boolean {
        val scale = maxOf(1.0, kotlin.math.abs(a), kotlin.math.abs(b))
        return kotlin.math.abs(a - b) <= scale * 1e-9
    }

    private fun parseLine(line: String): List<String> {
        val cells = mutableListOf<String>()
        val current = StringBuilder()
        var quoted = false
        var index = 0
        while (index < line.length) {
            val char = line[index]
            when {
                char == '"' && quoted && index + 1 < line.length && line[index + 1] == '"' -> {
                    current.append('"')
                    index++
                }
                char == '"' -> quoted = !quoted
                char == ',' && !quoted -> {
                    cells += current.toString()
                    current.clear()
                }
                else -> current.append(char)
            }
            index++
        }
        require(!quoted) { "CSV contains an unterminated quoted field" }
        cells += current.toString()
        return cells
    }
}
