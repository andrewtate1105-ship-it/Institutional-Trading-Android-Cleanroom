package com.institutionaltrading.mobile

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TradingViewWebSocketHtmlTest {
    @Test fun rendersReadOnlyNseChartSessionWithDelayedFeedGuard() {
        val html = TradingViewWebSocketHtml.render(
            symbol = "ASHOKLEY",
            timeframe = "15M",
            limit = 200,
            requestToken = "request_12345678",
        )
        assertTrue(html.contains("NSE:ASHOKLEY"))
        assertTrue(html.contains("unauthorized_user_token"))
        assertTrue(html.contains("chart_create_session"))
        assertTrue(html.contains("resolve_symbol"))
        assertTrue(html.contains("create_series"))
        assertTrue(html.contains("symbol_resolved"))
        assertTrue(html.contains("isExplicitlyDelayed"))
        assertTrue(html.contains("is_delayed"))
        assertTrue(html.contains("data_status"))
        assertTrue(html.contains("delayed market data"))
        assertTrue(html.contains("'15'"))
        assertFalse(html.contains("place_order", ignoreCase = true))
    }

    @Test fun rejectsUnsafeInputs() {
        assertTrue(runCatching {
            TradingViewWebSocketHtml.render("<script>", "15M", 200, "request_12345678")
        }.isFailure)
        assertTrue(runCatching {
            TradingViewWebSocketHtml.render("ASHOKLEY", "2M", 200, "request_12345678")
        }.isFailure)
        assertTrue(runCatching {
            TradingViewWebSocketHtml.render("ASHOKLEY", "15M", 10, "request_12345678")
        }.isFailure)
    }
}
