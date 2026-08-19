package com.institutionaltrading.mobile

import android.app.Activity
import android.os.Looper
import android.view.View
import android.webkit.JavascriptInterface
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import org.json.JSONObject
import java.util.UUID

class TradingViewWebSocketDataSource(
    private val activity: Activity,
) {
    val view: WebView = WebView(activity).apply {
        visibility = View.INVISIBLE
        layoutParams = android.widget.LinearLayout.LayoutParams(1, 1)
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = false
        settings.allowFileAccess = false
        settings.allowContentAccess = false
        settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
        webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: android.webkit.WebResourceRequest?): Boolean = true
        }
    }

    private var activeToken: String? = null
    private var activeLimit: Int = 0
    private var callback: ((Result<List<BacktestRow>>) -> Unit)? = null

    init {
        view.addJavascriptInterface(Bridge(), "AndroidBridge")
    }

    fun fetch(
        symbol: String,
        timeframe: String,
        limit: Int = 200,
        onComplete: (Result<List<BacktestRow>>) -> Unit,
    ) {
        check(Looper.myLooper() == Looper.getMainLooper()) { "TradingView WebView fetch must start on the main thread" }
        callback?.invoke(Result.failure(MarketDataUnavailableException("Previous TradingView request was replaced")))

        val token = UUID.randomUUID().toString().replace('-', '_')
        activeToken = token
        activeLimit = limit
        callback = onComplete
        val html = TradingViewWebSocketHtml.render(symbol, timeframe, limit, token)
        view.stopLoading()
        view.loadDataWithBaseURL(
            "https://www.tradingview.com/",
            html,
            "text/html",
            "UTF-8",
            null,
        )
    }

    fun destroy() {
        check(Looper.myLooper() == Looper.getMainLooper()) { "TradingView WebView destroy must run on the main thread" }
        callback?.invoke(Result.failure(MarketDataUnavailableException("TradingView data source was destroyed")))
        callback = null
        activeToken = null
        view.removeJavascriptInterface("AndroidBridge")
        view.destroy()
    }

    private inner class Bridge {
        @JavascriptInterface
        fun onSuccess(token: String, payload: String) {
            val result = runCatching { parseRows(payload, activeLimit) }
            activity.runOnUiThread { complete(token, result) }
        }

        @JavascriptInterface
        fun onError(token: String, message: String) {
            val safeMessage = message.take(240).ifBlank { "TradingView data unavailable" }
            activity.runOnUiThread {
                complete(token, Result.failure(MarketDataUnavailableException(safeMessage)))
            }
        }
    }

    private fun complete(token: String, result: Result<List<BacktestRow>>) {
        if (token != activeToken) return
        val current = callback ?: return
        callback = null
        activeToken = null
        activeLimit = 0
        current(result)
    }

    private fun parseRows(payload: String, expectedLimit: Int): List<BacktestRow> {
        require(payload.length <= HistoricalDataIntake.maxBytes) { "TradingView response exceeds safety limit" }
        val array = JSONObject(payload).getJSONArray("rows")
        require(array.length() == expectedLimit) { "TradingView returned incomplete bar history" }

        val csv = buildString {
            appendLine("Timestamp,Open,High,Low,Close,Previous Close")
            for (index in 0 until array.length()) {
                val row = array.getJSONObject(index)
                append(row.getString("timestamp")).append(',')
                append(row.getDouble("open")).append(',')
                append(row.getDouble("high")).append(',')
                append(row.getDouble("low")).append(',')
                append(row.getDouble("close")).append(',')
                append(row.getDouble("previousClose")).append('\n')
            }
        }
        return BacktestCsvImporter.parse(csv).also {
            require(it.size == expectedLimit) { "TradingView validated bar count mismatch" }
        }
    }
}
