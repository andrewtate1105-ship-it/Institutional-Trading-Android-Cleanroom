package com.institutionaltrading.mobile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HistoricalDataIntakeTest {
    private val validCsv = """
        Date,Open,High,Low,Close,Previous Close
        2026-08-01,100,110,95,105,99
        2026-08-02,105,112,101,108,105
    """.trimIndent()

    @Test fun acceptsValidatedHistoricalCsv() {
        val summary = HistoricalDataIntake.validateCsv(validCsv.toByteArray())
        assertEquals(2, summary.rowCount)
        assertEquals("2026-08-01T00:00:00Z", summary.firstTimestamp)
        assertEquals("2026-08-02T00:00:00Z", summary.lastTimestamp)
    }

    @Test fun rejectsEmptyAndOversizedFiles() {
        assertTrue(runCatching { HistoricalDataIntake.validateCsv(byteArrayOf()) }.isFailure)
        val oversized = ByteArray(HistoricalDataIntake.maxBytes + 1) { '1'.code.toByte() }
        assertTrue(runCatching { HistoricalDataIntake.validateCsv(oversized) }.isFailure)
    }

    @Test fun rejectsBrokenPreviousCloseContinuity() {
        val broken = validCsv.replace("2026-08-02,105,112,101,108,105", "2026-08-02,105,112,101,108,104")
        assertTrue(runCatching { HistoricalDataIntake.validateCsv(broken.toByteArray()) }.isFailure)
    }
}
