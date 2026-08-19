package com.institutionaltrading.mobile

import android.app.Activity
import android.content.Intent
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
    private lateinit var capitalInput: EditText
    private lateinit var riskInput: EditText
    private lateinit var dataStatus: TextView
    private lateinit var resultView: TextView
    private lateinit var chartView: WebView
    private lateinit var liveDataSource: TradingViewWebSocketDataSource
    private var importedData: ImportedMarketData? = null
    private var pendingImportContext: InstrumentContext? = null
    private var resolvedIdentity: InstrumentIdentity? = null

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
            text = "Type an NSE stock, choose timeframe and risk, then analyze. The app loads a TradingView chart and calculates the signal from validated closed bars. No broker login or automated orders."
        })

        stockInput = field(root, "Stock name or NSE symbol (e.g. Ashok Leyland Limited)")
        timeframeInput = field(root, "Trading timeframe: 5M, 15M, 1H, D or W").apply { setText("15M") }
        capitalInput = field(root, "Trading capital (INR)").apply { setText("10000") }
        riskInput = field(root, "Account risk per trade (1-2%)").apply { setText("1") }

        dataStatus = TextView(this).apply {
            text = "Automatic TradingView market data: READY. The newest potentially forming candle is excluded before analysis."
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
            text = "Analyze + load TradingView chart"
            setOnClickListener {
                val request = runCatching { readRequestWithoutSymbol() }
                if (request.isFailure) {
                    resultView.text = AnalysisFailurePresentation.format(request.exceptionOrNull()!!)
                    return@setOnClickListener
                }

                val typedStock = stockInput.text.toString().trim()
                val input = request.getOrThrow()
                isEnabled = false
                dataStatus.text = "Resolving NSE stock…"
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
                            resolvedIdentity = identity
                            loadTradingViewChart(identity.symbol, input.timeframe)
                            dataStatus.text = "Loading validated closed bars for ${identity.displayName} (${identity.symbol}) ${input.timeframe}…"
                            liveDataSource.fetch(
                                symbol = identity.symbol,
                                timeframe = input.timeframe,
                                limit = 200,
                            ) { rowsResult ->
                                rowsResult.onFailure { directError ->
                                    val fallback = importedData
                                    if (fallback == null) {
                                        isEnabled = true
                                        dataStatus.text = "Market data: UNAVAILABLE — ${directError.message ?: "TradingView feed failed"}"
                                        resultView.text = AnalysisFailurePresentation.format(directError)
                                    } else {
                                        analyzeInBackground(
                                            identity = identity,
                                            loaded = fallback,
                                            input = input,
                                            provenance = "USER_SUPPLIED_CLOSED_BAR_CSV",
                                            button = this,
                                        )
                                    }
                                }.onSuccess { rows ->
                                    val loaded = ImportedMarketData(rows, identity.symbol, input.timeframe)
                                    importedData = loaded
                                    analyzeInBackground(
                                        identity = identity,
                                        loaded = loaded,
                                        input = input,
                                        provenance = "TRADINGVIEW_DIRECT_WEBSOCKET",
                                        button = this,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        })

        root.addView(Button(this).apply {
            text = "Import fallback closed-bar OHLC CSV"
            setOnClickListener {
                val context = runCatching { readFallbackImportContext() }
                if (context.isFailure) {
                    importedData = null
                    pendingImportContext = null
                    dataStatus.text = "Market data: REJECTED — ${context.exceptionOrNull()?.message}"
                    return@setOnClickListener
                }

                pendingImportContext = context.getOrThrow()
                startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "text/*"
                    putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("text/csv", "text/comma-separated-values", "text/plain"))
                }, REQUEST_HISTORICAL_CSV)
            }
        })

        resultView = TextView(this).apply {
            textSize = 18f
            text = "Signal result will appear here."
            setPadding(0, 24, 0, 24)
        }
        root.addView(resultView)

        root.addView(TextView(this).apply {
            text = "Risk rule: directional setups are accepted only when the structural stop is 1-2% from entry and account risk stays at or below the selected 1-2% cap. Forming, stale, future-dated or mismatched bars are rejected; otherwise the app returns NO TRADE."
        })

        setContentView(scroll)
    }

    private fun analyzeInBackground(
        identity: InstrumentIdentity,
        loaded: ImportedMarketData,
        input: AnalysisRequest,
        provenance: String,
        button: Button,
    ) {
        ioExecutor.execute {
            val result = runCatching {
                val rows = loaded.requireMatches(identity.symbol, input.timeframe)
                val series = HistoricalBarAdapter.toValidatedSeries(
                    rows = rows,
                    symbol = identity.symbol,
                    timeframe = input.timeframe,
                    provenance = provenance,
                )
                LatestBarFreshness.requireCurrent(series, Instant.now())
                val signal = InAppSignalEngine.analyze(
                    series = series,
                    accountEquity = input.capital,
                    accountRiskPercent = input.riskPercent,
                )
                AnalysisSuccess(identity, InAppSignalEngine.format(signal))
            }
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                button.isEnabled = true
                result.onSuccess { success ->
                    dataStatus.text = "Market data: READY — ${success.identity.displayName} (${success.identity.symbol}) ${input.timeframe}; validated closed bars loaded."
                    resultView.text = success.formattedSignal
                }.onFailure { error ->
                    dataStatus.text = "Market data: REJECTED — ${error.message ?: "validation failed"}"
                    resultView.text = AnalysisFailurePresentation.format(error)
                }
            }
        }
    }

    override fun onDestroy() {
        liveDataSource.destroy()
        chartView.destroy()
        ioExecutor.shutdownNow()
        super.onDestroy()
    }

    @Deprecated("Deprecated in Android API; retained for minimum-SDK compatibility")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQUEST_HISTORICAL_CSV || resultCode != RESULT_OK) return
        val context = pendingImportContext
        pendingImportContext = null
        if (context == null) {
            importedData = null
            dataStatus.text = "Market data: REJECTED — select a valid NSE symbol and timeframe before importing data"
            return
        }

        val uri = data?.data
        if (uri == null) {
            importedData = null
            dataStatus.text = "Market data: REJECTED — missing document"
            return
        }

        dataStatus.text = "Market data: validating fallback CSV for ${context.symbol} ${context.timeframe}…"
        ioExecutor.execute {
            val result = runCatching {
                val bytes = contentResolver.openInputStream(uri)?.use(HistoricalDataIntake::readBounded)
                    ?: throw IllegalArgumentException("Could not read selected CSV")
                val summary = HistoricalDataIntake.validateCsv(bytes)
                val rows = BacktestCsvImporter.parse(bytes.toString(Charsets.UTF_8))
                ImportedMarketData(rows, context.symbol, context.timeframe) to summary
            }
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                result.onSuccess { (loaded, summary) ->
                    importedData = loaded
                    dataStatus.text = "Market data: ${summary.rowCount} fallback closed bars loaded for ${loaded.symbol} ${loaded.timeframe} (${summary.firstTimestamp} to ${summary.lastTimestamp})."
                }.onFailure { error ->
                    importedData = null
                    dataStatus.text = "Market data: REJECTED — ${error.message}"
                }
            }
        }
    }

    private fun readFallbackImportContext(): InstrumentContext {
        val timeframe = readTimeframe()
        val typed = stockInput.text.toString().trim()
        val cached = resolvedIdentity?.takeIf {
            it.symbol.equals(typed, ignoreCase = true) || it.displayName.equals(typed, ignoreCase = true)
        }
        if (cached != null) return InstrumentContext(cached.symbol, timeframe)

        val symbol = typed.uppercase(Locale.ROOT)
        require(symbol.matches(Regex("^[A-Z0-9&._-]{1,32}$"))) {
            "For fallback CSV import, enter the exact NSE symbol first"
        }
        return InstrumentContext(symbol, timeframe)
    }

    private fun readRequestWithoutSymbol(): AnalysisRequest {
        require(stockInput.text.toString().trim().isNotEmpty()) { "Enter a stock name or NSE symbol" }
        val timeframe = readTimeframe()
        val capital = capitalInput.text.toString().toDoubleOrNull()
            ?: throw IllegalArgumentException("Trading capital must be numeric")
        require(capital.isFinite() && capital > 0.0) { "Trading capital must be positive" }

        val risk = riskInput.text.toString().toDoubleOrNull()
            ?: throw IllegalArgumentException("Risk percent must be numeric")
        require(risk.isFinite() && risk in 1.0..Validation.hardRiskCapPercent) {
            "Risk percent must be between 1% and 2%"
        }
        return AnalysisRequest(timeframe, capital, risk)
    }

    private fun readTimeframe(): String {
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

    private data class InstrumentContext(
        val symbol: String,
        val timeframe: String,
    )

    private data class AnalysisRequest(
        val timeframe: String,
        val capital: Double,
        val riskPercent: Double,
    )

    private data class AnalysisSuccess(
        val identity: InstrumentIdentity,
        val formattedSignal: String,
    )

    companion object {
        private const val REQUEST_HISTORICAL_CSV = 1001
    }
}
