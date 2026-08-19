package com.institutionaltrading.mobile

import android.app.Activity
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.time.Instant
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : Activity() {
    private lateinit var stockInput: EditText
    private lateinit var timeframeInput: EditText
    private lateinit var dataStatus: TextView
    private lateinit var resultView: TextView
    private lateinit var chartView: WebView
    private lateinit var liveDataSource: TradingViewWebSocketDataSource

    private val ioExecutor: ExecutorService = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "institutional-trading-io").apply { isDaemon = true }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
        }
        val scroll = ScrollView(this).apply { addView(root) }

        root.addView(TextView(this).apply {
            text = "Trading Signal Assistant"
            textSize = 24f
        })
        root.addView(TextView(this).apply {
            text = "Enter an NSE stock and trading timeframe. The app loads validated closed market bars and returns the signal entirely inside the app."
        })

        stockInput = field(root, "Stock name or NSE symbol (e.g. Ashok Leyland Limited)")
        timeframeInput = field(root, "Timeframe: 5M, 15M, 1H, D or W").apply { setText("15M") }

        dataStatus = TextView(this).apply {
            text = "Market data: READY"
            setPadding(0, 16, 0, 16)
        }
        root.addView(dataStatus)

        chartView = WebView(this).apply {
            visibility = View.GONE
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                (480 * resources.displayMetrics.density).toInt(),
            )
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            webViewClient = WebViewClient()
        }
        root.addView(chartView)

        liveDataSource = TradingViewWebSocketDataSource(this)
        root.addView(liveDataSource.view)

        root.addView(Button(this).apply {
            text = "GET SIGNAL"
            setOnClickListener {
                val typedStock = stockInput.text.toString().trim()
                val timeframe = runCatching { readInputs(typedStock) }.getOrElse { error ->
                    resultView.text = AnalysisFailurePresentation.format(error)
                    return@setOnClickListener
                }

                isEnabled = false
                dataStatus.text = "Resolving stock and loading closed bars…"
                resultView.text = "Analyzing…"

                ioExecutor.execute {
                    val identityResult = runCatching { TradingViewDirectSymbolResolver.resolve(typedStock) }
                    runOnUiThread {
                        if (isFinishing || isDestroyed) return@runOnUiThread
                        identityResult.onFailure { error ->
                            isEnabled = true
                            dataStatus.text = "Market data: UNAVAILABLE — ${error.message ?: "stock resolution failed"}"
                            resultView.text = AnalysisFailurePresentation.format(error)
                        }.onSuccess { identity ->
                            loadTradingViewChart(identity.symbol, timeframe)
                            dataStatus.text = "Loading ${identity.displayName} (${identity.symbol}) $timeframe…"
                            liveDataSource.fetch(identity.symbol, timeframe, 200) { rowsResult ->
                                rowsResult.onFailure { error ->
                                    isEnabled = true
                                    dataStatus.text = "Market data: UNAVAILABLE — ${error.message ?: "feed failed"}"
                                    resultView.text = AnalysisFailurePresentation.format(error)
                                }.onSuccess { rows ->
                                    analyzeInBackground(identity, rows, timeframe, this)
                                }
                            }
                        }
                    }
                }
            }
        })

        resultView = TextView(this).apply {
            textSize = 18f
            text = "LONG / SHORT / NO TRADE, entry, stop loss and exits will appear here."
            setPadding(0, 24, 0, 24)
        }
        root.addView(resultView)

        root.addView(TextView(this).apply {
            text = "Risk is handled internally. Directional setups require a structural stop within 1-2% of entry. Stale, forming, future-dated, delayed or invalid data is rejected instead of generating a signal."
        })

        setContentView(scroll)
    }

    private fun analyzeInBackground(
        identity: InstrumentIdentity,
        rows: List<BacktestCsvRow>,
        timeframe: String,
        button: Button,
    ) {
        ioExecutor.execute {
            val result = runCatching {
                val series = HistoricalBarAdapter.toValidatedSeries(
                    rows = rows,
                    symbol = identity.symbol,
                    timeframe = timeframe,
                    provenance = "TRADINGVIEW_DIRECT_WEBSOCKET",
                )
                LatestBarFreshness.requireCurrent(series, Instant.now())
                val signal = InAppSignalEngine.analyze(
                    series = series,
                    accountEquity = DEFAULT_ACCOUNT_EQUITY,
                    accountRiskPercent = DEFAULT_ACCOUNT_RISK_PERCENT,
                )
                InAppSignalEngine.format(signal)
            }
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                button.isEnabled = true
                result.onSuccess { formatted ->
                    dataStatus.text = "Market data: READY — ${identity.displayName} (${identity.symbol}) $timeframe"
                    resultView.text = formatted
                }.onFailure { error ->
                    dataStatus.text = "Market data: REJECTED — ${error.message ?: "validation failed"}"
                    resultView.text = AnalysisFailurePresentation.format(error)
                }
            }
        }
    }

    private fun readInputs(stock: String): String {
        require(stock.isNotEmpty()) { "Enter a stock name or NSE symbol" }
        val timeframe = timeframeInput.text.toString().trim().uppercase(Locale.ROOT)
        require(timeframe in Validation.defaultTimeframes) { "Unsupported timeframe" }
        return timeframe
    }

    private fun loadTradingViewChart(symbol: String, timeframe: String) {
        chartView.visibility = View.VISIBLE
        chartView.loadDataWithBaseURL(
            "https://www.tradingview.com/",
            TradingViewChartHtml.render(symbol, timeframe),
            "text/html",
            "UTF-8",
            null,
        )
    }

    private fun field(parent: LinearLayout, hint: String) = EditText(this).apply {
        this.hint = hint
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        parent.addView(this)
    }

    override fun onDestroy() {
        liveDataSource.destroy()
        chartView.destroy()
        ioExecutor.shutdownNow()
        super.onDestroy()
    }

    companion object {
        // Equity is only used to satisfy the existing position-risk engine; it is not user-facing.
        // Entry, stop and exit prices are independent of this value.
        private const val DEFAULT_ACCOUNT_EQUITY = 100000.0
        private const val DEFAULT_ACCOUNT_RISK_PERCENT = 1.0
    }
}
