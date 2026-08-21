package com.institutionaltrading.mobile

object TradingViewChartHtml {
    private val symbolPattern = Regex("^[A-Z0-9&._-]{1,32}$")
    private val intervals = mapOf(
        "5M" to "5",
        "15M" to "15",
        "1H" to "60",
        "D" to "D",
        "W" to "W",
    )

    fun render(symbol: String, timeframe: String): String {
        val normalizedSymbol = symbol.trim().uppercase()
        require(symbolPattern.matches(normalizedSymbol)) { "Invalid TradingView symbol" }
        val interval = intervals[timeframe] ?: throw IllegalArgumentException("Unsupported chart timeframe")
        val tradingViewSymbol = "NSE:$normalizedSymbol"
        return """
            <!doctype html>
            <html>
            <head>
              <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0">
              <style>html,body,.tradingview-widget-container,.tradingview-widget-container__widget{margin:0;width:100%;height:100%;background:#0b0f14;}body{overflow:hidden;}</style>
            </head>
            <body>
              <div class="tradingview-widget-container">
                <div class="tradingview-widget-container__widget"></div>
                <script type="text/javascript" src="https://s3.tradingview.com/external-embedding/embed-widget-advanced-chart.js" async>
                {
                  "autosize": true,
                  "symbol": "$tradingViewSymbol",
                  "interval": "$interval",
                  "timezone": "Asia/Kolkata",
                  "theme": "dark",
                  "style": "1",
                  "locale": "en",
                  "allow_symbol_change": false,
                  "hide_side_toolbar": false,
                  "hide_volume": false,
                  "save_image": false,
                  "support_host": "https://www.tradingview.com"
                }
                </script>
              </div>
            </body>
            </html>
        """.trimIndent()
    }
}
