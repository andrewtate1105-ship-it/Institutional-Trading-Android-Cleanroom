package com.institutionaltrading.mobile

import android.app.Activity
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
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
    private lateinit var stocksButton: Button
    private lateinit var fnoButton: Button
    private var tradingMode: TradingMode = TradingMode.STOCKS

    private val ioExecutor: ExecutorService = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "institutional-trading-io").apply { isDaemon = true }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(24), dp(20), dp(32))
            setBackgroundColor(BACKGROUND)
        }
        val scroll = ScrollView(this).apply {
            isFillViewport = true
            setBackgroundColor(BACKGROUND)
            addView(root)
        }

        root.addView(label("INSTITUTIONAL EDGE", 12f, ACCENT).apply { letterSpacing = 0.16f })
        root.addView(label("Indian Market Signal Desk", 28f, TEXT_PRIMARY).apply {
            setPadding(0, dp(6), 0, dp(6))
        })
        root.addView(label(
            "Closed-bar market structure + EMA 20/50/200 + RSI + ATR + momentum + breakout confirmation. Weak setups are rejected.",
            14f,
            TEXT_SECONDARY,
        ))

        val modeCard = card()
        modeCard.addView(label("MARKET MODE", 12f, TEXT_SECONDARY))
        val modeRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(10), 0, 0)
        }
        stocksButton = modeButton("STOCKS", TradingMode.STOCKS)
        fnoButton = modeButton("F&O", TradingMode.F_AND_O)
        modeRow.addView(stocksButton, LinearLayout.LayoutParams(0, dp(48), 1f).apply { marginEnd = dp(6) })
        modeRow.addView(fnoButton, LinearLayout.LayoutParams(0, dp(48), 1f).apply { marginStart = dp(6) })
        modeCard.addView(modeRow)
        root.addView(modeCard, cardParams())
        updateModeButtons()

        val inputCard = card()
        inputCard.addView(label("ANALYZE", 12f, TEXT_SECONDARY))
        stockInput = field(inputCard, "Stock / index name or NSE symbol", "RELIANCE")
        timeframeInput = field(inputCard, "Timeframe: 5M, 15M, 1H, D or W", "15M")
        root.addView(inputCard, cardParams())

        dataStatus = label("Market data: READY", 13f, ACCENT).apply {
            setPadding(dp(2), dp(4), dp(2), dp(12))
        }
        root.addView(dataStatus)

        chartView = WebView(this).apply {
            visibility = View.GONE
            setBackgroundColor(CARD)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(440),
            )
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            webViewClient = WebViewClient()
        }
        root.addView(chartView, cardParams())

        liveDataSource = TradingViewWebSocketDataSource(this)
        root.addView(liveDataSource.view)

        val signalButton = Button(this).apply {
            text = "RUN INSTITUTIONAL ANALYSIS"
            textSize = 14f
            setTextColor(Color.BLACK)
            isAllCaps = false
            background = rounded(ACCENT, 14f)
            setOnClickListener { runAnalysis(this) }
        }
        root.addView(signalButton, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(54)).apply {
            topMargin = dp(8)
            bottomMargin = dp(16)
        })

        val resultCard = card()
        resultCard.addView(label("SIGNAL OUTPUT", 12f, TEXT_SECONDARY))
        resultView = label(
            "LONG / SHORT / NO TRADE will appear here with entry, structural stop and 1.5R / 3R exits.",
            17f,
            TEXT_PRIMARY,
        ).apply { setPadding(0, dp(12), 0, dp(4)) }
        resultCard.addView(resultView)
        root.addView(resultCard, cardParams())

        root.addView(label(
            "Risk gate: structural stop must be 1–2% from entry. Stale, forming, future-dated, delayed, invalid or low-confluence data fails closed. F&O never fabricates strike, premium, OI, IV or Greeks.",
            12f,
            TEXT_MUTED,
        ).apply { setPadding(dp(2), dp(4), dp(2), 0) })

        setContentView(scroll)
    }

    private fun runAnalysis(button: Button) {
        val typedStock = stockInput.text.toString().trim()
        val timeframe = runCatching { readInputs(typedStock) }.getOrElse { error ->
            resultView.text = AnalysisFailurePresentation.format(error)
            return
        }

        button.isEnabled = false
        dataStatus.text = "Resolving instrument and loading confirmed bars…"
        resultView.text = "Analyzing market structure and confluence…"
        val selectedMode = tradingMode

        ioExecutor.execute {
            val identityResult = runCatching { TradingViewDirectSymbolResolver.resolve(typedStock) }
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                identityResult.onFailure { error ->
                    button.isEnabled = true
                    dataStatus.text = "Market data: UNAVAILABLE — ${error.message ?: "instrument resolution failed"}"
                    resultView.text = AnalysisFailurePresentation.format(error)
                }.onSuccess { identity ->
                    loadTradingViewChart(identity.symbol, timeframe)
                    dataStatus.text = "Loading ${identity.displayName} (${identity.symbol}) $timeframe…"
                    liveDataSource.fetch(identity.symbol, timeframe, 200) { rowsResult ->
                        rowsResult.onFailure { error ->
                            button.isEnabled = true
                            dataStatus.text = "Market data: UNAVAILABLE — ${error.message ?: "feed failed"}"
                            resultView.text = AnalysisFailurePresentation.format(error)
                        }.onSuccess { rows ->
                            analyzeInBackground(identity, rows, timeframe, selectedMode, button)
                        }
                    }
                }
            }
        }
    }

    private fun analyzeInBackground(
        identity: InstrumentIdentity,
        rows: List<BacktestRow>,
        timeframe: String,
        mode: TradingMode,
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
                buildString {
                    append(InAppSignalEngine.format(signal))
                    append("\n\nMODE: ").append(mode.label)
                    append("\n").append(TradingModePolicy.resultNotice(mode, liveDataSource.capabilities))
                }
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
        require(stock.isNotEmpty()) { "Enter a stock/index name or NSE symbol" }
        val timeframe = timeframeInput.text.toString().trim().uppercase(Locale.ROOT)
        require(timeframe in Validation.defaultTimeframes) { "Unsupported timeframe. Use 5M, 15M, 1H, D or W" }
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

    private fun modeButton(text: String, mode: TradingMode) = Button(this).apply {
        this.text = text
        textSize = 14f
        isAllCaps = false
        setOnClickListener {
            tradingMode = mode
            updateModeButtons()
        }
    }

    private fun updateModeButtons() {
        val buttons = listOf(stocksButton to TradingMode.STOCKS, fnoButton to TradingMode.F_AND_O)
        buttons.forEach { (button, mode) ->
            val selected = tradingMode == mode
            button.setTextColor(if (selected) Color.BLACK else TEXT_SECONDARY)
            button.background = rounded(if (selected) ACCENT else CARD_ELEVATED, 12f)
        }
    }

    private fun field(parent: LinearLayout, hint: String, defaultValue: String) = EditText(this).apply {
        this.hint = hint
        hintTextColors = android.content.res.ColorStateList.valueOf(TEXT_MUTED)
        setTextColor(TEXT_PRIMARY)
        textSize = 16f
        setText(defaultValue)
        setPadding(dp(14), 0, dp(14), 0)
        background = rounded(INPUT, 12f)
        parent.addView(this, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52)).apply {
            topMargin = dp(10)
        })
    }

    private fun label(text: String, size: Float, color: Int) = TextView(this).apply {
        this.text = text
        textSize = size
        setTextColor(color)
        setLineSpacing(0f, 1.12f)
    }

    private fun card() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(16), dp(16), dp(16), dp(16))
        background = rounded(CARD, 18f)
    }

    private fun cardParams() = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
        topMargin = dp(16)
        bottomMargin = dp(4)
    }

    private fun rounded(color: Int, radiusDp: Float) = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        setColor(color)
        cornerRadius = dp(radiusDp.toInt()).toFloat()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    override fun onDestroy() {
        liveDataSource.destroy()
        chartView.destroy()
        ioExecutor.shutdownNow()
        super.onDestroy()
    }

    companion object {
        private const val DEFAULT_ACCOUNT_EQUITY = 100000.0
        private const val DEFAULT_ACCOUNT_RISK_PERCENT = 1.0
        private val BACKGROUND = Color.rgb(8, 12, 18)
        private val CARD = Color.rgb(18, 25, 35)
        private val CARD_ELEVATED = Color.rgb(29, 38, 50)
        private val INPUT = Color.rgb(12, 18, 27)
        private val ACCENT = Color.rgb(72, 224, 172)
        private val TEXT_PRIMARY = Color.rgb(242, 247, 250)
        private val TEXT_SECONDARY = Color.rgb(173, 186, 200)
        private val TEXT_MUTED = Color.rgb(116, 132, 148)
    }
}
