package com.institutionaltrading.mobile

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TradingViewChartHtmlTest {
    @Test fun rendersNseSymbolAndMappedInterval() {
        val html = TradingViewChartHtml.render("ASHOKLEY", "15M")
        assertTrue(html.contains("NSE:ASHOKLEY"))
        assertTrue(html.contains("\"interval\": \"15\""))
        assertTrue(html.contains("embed-widget-advanced-chart.js"))
    }

    @Test fun mapsEverySupportedTimeframeDeterministically() {
        val mappings = mapOf(
            "5M" to "5",
            "15M" to "15",
            "1H" to "60",
            "D" to "D",
            "W" to "W",
        )
        mappings.forEach { (timeframe, expectedInterval) ->
            val html = TradingViewChartHtml.render("RELIANCE", timeframe)
            assertTrue("Expected $timeframe -> $expectedInterval", html.contains("\"interval\": \"$expectedInterval\""))
        }
    }

    @Test fun rejectsInvalidSymbolAndTimeframe() {
        assertTrue(runCatching { TradingViewChartHtml.render("<script>", "15M") }.isFailure)
        assertTrue(runCatching { TradingViewChartHtml.render("ASHOKLEY", "2M") }.isFailure)
    }

    @Test fun doesNotEnableUserControlledHtmlInjection() {
        val html = TradingViewChartHtml.render("SBIN", "1H")
        assertFalse(html.contains("javascript:"))
    }
}
