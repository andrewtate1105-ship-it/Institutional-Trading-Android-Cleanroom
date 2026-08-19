package com.institutionaltrading.mobile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BacktestCsvImporterTest {
    @Test fun acceptsRequiredColumnsInAnyOrderAndAliases() {
        val csv = """
            Close,Prev Close,Date,Low,Open,High
            101.0,99.0,2026-01-01,98.0,100.0,102.0
            103.0,101.0,2026-01-02,100.0,101.0,104.0
        """.trimIndent()

        val rows = BacktestCsvImporter.parse(csv)
        assertEquals(2, rows.size)
        assertEquals("2026-01-01T00:00:00Z", rows.first().timestamp)
        assertEquals(103.0, rows.last().close, 0.0)
    }

    @Test fun acceptsCanonicalUtcTimestampsAndQuotedHeaders() {
        val csv = """
            "Timestamp","Open","High","Low","Close","Previous Close"
            "2026-01-01T09:15:00Z",100,102,99,101,98
            "2026-01-01T09:20:00Z",101,103,100,102,101
        """.trimIndent()
        assertEquals(2, BacktestCsvImporter.parse(csv).size)
    }

    @Test fun rejectsMissingOrAmbiguousHeaders() {
        val missing = "Date,Open,High,Low,Close\n2026-01-01,1,1,1,1"
        val duplicate = "Date,Timestamp,Open,High,Low,Close,PrevClose\n2026-01-01,2026-01-01,1,1,1,1,1"
        assertTrue(runCatching { BacktestCsvImporter.parse(missing) }.isFailure)
        assertTrue(runCatching { BacktestCsvImporter.parse(duplicate) }.isFailure)
    }

    @Test fun rejectsBrokenPreviousCloseDuplicateAndUnorderedRows() {
        val broken = """
            Date,Open,High,Low,Close,Previous Close
            2026-01-01,100,102,99,101,98
            2026-01-02,101,103,100,102,100
        """.trimIndent()
        val duplicate = """
            Date,Open,High,Low,Close,Previous Close
            2026-01-01,100,102,99,101,98
            2026-01-01,101,103,100,102,101
        """.trimIndent()
        val unordered = """
            Date,Open,High,Low,Close,Previous Close
            2026-01-02,100,102,99,101,98
            2026-01-01,101,103,100,102,101
        """.trimIndent()
        assertTrue(runCatching { BacktestCsvImporter.parse(broken) }.isFailure)
        assertTrue(runCatching { BacktestCsvImporter.parse(duplicate) }.isFailure)
        assertTrue(runCatching { BacktestCsvImporter.parse(unordered) }.isFailure)
    }

    @Test fun rejectsMalformedRowsAndImpossibleOhlc() {
        val wrongWidth = "Date,Open,High,Low,Close,Previous Close\n2026-01-01,100,102,99,101"
        val impossible = "Date,Open,High,Low,Close,Previous Close\n2026-01-01,100,99,98,101,97"
        val nonNumeric = "Date,Open,High,Low,Close,Previous Close\n2026-01-01,x,102,99,101,97"
        assertTrue(runCatching { BacktestCsvImporter.parse(wrongWidth) }.isFailure)
        assertTrue(runCatching { BacktestCsvImporter.parse(impossible) }.isFailure)
        assertTrue(runCatching { BacktestCsvImporter.parse(nonNumeric) }.isFailure)
    }
}
