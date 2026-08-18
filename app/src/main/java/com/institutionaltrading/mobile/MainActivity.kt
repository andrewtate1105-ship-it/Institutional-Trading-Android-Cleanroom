package com.institutionaltrading.mobile

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : Activity() {
    private lateinit var stockInput: EditText
    private lateinit var nseLinkInput: EditText
    private lateinit var timeframeInput: EditText
    private lateinit var capitalInput: EditText
    private lateinit var riskInput: EditText
    private lateinit var dataStatus: TextView
    private lateinit var resultView: TextView
    private var importedRows: List<BacktestRow>? = null

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
            text = "Signals stay inside the app. No broker login and no automated orders."
        })

        stockInput = field(root, "Stock name or NSE symbol (e.g. ASHOKLEY)")
        nseLinkInput = field(root, "Official NSE quote/chart link")
        timeframeInput = field(root, "Trading timeframe: 5M, 15M, 1H, D or W").apply { setText("15M") }
        capitalInput = field(root, "Trading capital (INR)").apply { setText("10000") }
        riskInput = field(root, "Account risk per trade (1-2%)").apply { setText("1") }

        dataStatus = TextView(this).apply {
            text = "Market data: NOT LOADED. The NSE link identifies the instrument; it is not treated as a live price feed."
        }
        root.addView(dataStatus)

        root.addView(Button(this).apply {
            text = "Import closed-bar OHLC CSV"
            setOnClickListener {
                startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "text/*"
                    putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("text/csv", "text/comma-separated-values", "text/plain"))
                }, REQUEST_HISTORICAL_CSV)
            }
        })

        root.addView(Button(this).apply {
            text = "Analyze"
            setOnClickListener {
                val request = runCatching { readRequest() }
                if (request.isFailure) {
                    resultView.text = "NO TRADE\n${request.exceptionOrNull()?.message}"
                    return@setOnClickListener
                }

                isEnabled = false
                resultView.text = "Analyzing validated closed-bar data…"
                ioExecutor.execute {
                    val output = runCatching {
                        val input = request.getOrThrow()
                        val rows = importedRows
                            ?: throw IllegalStateException("No validated machine-readable market bars are loaded. Import closed-bar OHLC CSV or configure a lawful data feed; no signal will be invented from the NSE webpage.")
                        val series = HistoricalBarAdapter.toValidatedSeries(
                            rows = rows,
                            symbol = input.symbol,
                            timeframe = input.timeframe,
                            provenance = "USER_SUPPLIED_CLOSED_BAR_CSV",
                        )
                        InAppSignalEngine.format(
                            InAppSignalEngine.analyze(
                                series = series,
                                accountEquity = input.capital,
                                accountRiskPercent = input.riskPercent,
                            )
                        )
                    }.getOrElse { error -> "NO TRADE\n${error.message}" }

                    runOnUiThread {
                        if (isFinishing || isDestroyed) return@runOnUiThread
                        isEnabled = true
                        resultView.text = output
                    }
                }
            }
        })

        resultView = TextView(this).apply {
            textSize = 18f
            text = "Signal result will appear here."
            setPadding(0, 24, 0, 24)
        }
        root.addView(resultView)

        root.addView(TextView(this).apply {
            text = "Risk rule: directional setups are accepted only when the structural stop is 1-2% from entry and account risk stays at or below the selected 1-2% cap. Otherwise the result is NO TRADE."
        })

        setContentView(scroll)
    }

    override fun onDestroy() {
        ioExecutor.shutdownNow()
        super.onDestroy()
    }

    @Deprecated("Deprecated in Android API; retained for minimum-SDK compatibility")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQUEST_HISTORICAL_CSV || resultCode != RESULT_OK) return
        val uri = data?.data
        if (uri == null) {
            dataStatus.text = "Market data: REJECTED — missing document"
            return
        }

        dataStatus.text = "Market data: validating CSV…"
        ioExecutor.execute {
            val result = runCatching {
                val bytes = contentResolver.openInputStream(uri)?.use(HistoricalDataIntake::readBounded)
                    ?: throw IllegalArgumentException("Could not read selected CSV")
                val summary = HistoricalDataIntake.validateCsv(bytes)
                val rows = BacktestCsvImporter.parse(bytes.toString(Charsets.UTF_8))
                rows to summary
            }
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                result.onSuccess { (rows, summary) ->
                    importedRows = rows
                    dataStatus.text = "Market data: ${summary.rowCount} validated closed bars loaded (${summary.firstTimestamp} to ${summary.lastTimestamp})."
                }.onFailure { error ->
                    importedRows = null
                    dataStatus.text = "Market data: REJECTED — ${error.message}"
                }
            }
        }
    }

    private fun readRequest(): AnalysisRequest {
        val link = nseLinkInput.text.toString().trim()
        val symbolFromUrl = OfficialNseUrl.normalizeToSymbol(link)
            ?: throw IllegalArgumentException("Enter a supported official NSE quote/chart URL")

        val typed = stockInput.text.toString().trim()
        require(typed.matches(Regex("^[A-Za-z0-9&._-]{1,32}$"))) {
            "Enter a valid NSE stock symbol"
        }
        val typedSymbol = typed.uppercase(Locale.ROOT)
        require(typedSymbol == symbolFromUrl) {
            "Stock symbol and NSE link identify different instruments"
        }

        val timeframe = timeframeInput.text.toString().trim().uppercase(Locale.ROOT)
        require(timeframe in Validation.defaultTimeframes) { "Unsupported timeframe" }

        val capital = capitalInput.text.toString().toDoubleOrNull()
            ?: throw IllegalArgumentException("Trading capital must be numeric")
        require(capital.isFinite() && capital > 0.0) { "Trading capital must be positive" }

        val risk = riskInput.text.toString().toDoubleOrNull()
            ?: throw IllegalArgumentException("Risk percent must be numeric")
        require(risk.isFinite() && risk in 1.0..Validation.hardRiskCapPercent) {
            "Risk percent must be between 1% and 2%"
        }

        return AnalysisRequest(symbolFromUrl, timeframe, capital, risk)
    }

    private fun field(parent: LinearLayout, hint: String) = EditText(this).apply {
        this.hint = hint
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        parent.addView(this)
    }

    private data class AnalysisRequest(
        val symbol: String,
        val timeframe: String,
        val capital: Double,
        val riskPercent: Double,
    )

    companion object {
        private const val REQUEST_HISTORICAL_CSV = 1001
    }
}
