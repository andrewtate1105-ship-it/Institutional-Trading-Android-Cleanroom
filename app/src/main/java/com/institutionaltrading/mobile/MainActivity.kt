package com.institutionaltrading.mobile

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.view.ViewGroup
import android.widget.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : Activity() {
    private lateinit var status: TextView
    private lateinit var readiness: TextView
    private lateinit var profileStore: ProfileStore
    private lateinit var nseLinkStore: NseLinkRegistryStore
    private val ioExecutor: ExecutorService = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "institutional-trading-io").apply { isDaemon = true }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        profileStore = ProfileStore(this)
        nseLinkStore = NseLinkRegistryStore(this)

        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(32, 32, 32, 32) }
        val scroll = ScrollView(this).apply { addView(root) }
        root.addView(TextView(this).apply { text = "Institutional Trading System"; textSize = 22f })
        root.addView(TextView(this).apply { text = "Signal-only Android build. No broker login and no automated orders." })
        readiness = TextView(this)
        root.addView(readiness)

        val chatId = field(root, "Numeric Telegram chat ID from Telegram")
        val token = field(root, "Telegram bot token", true)
        val equity = field(root, "Account equity (INR)")
        val risk = field(root, "Risk percent (max 2)").apply { setText("1") }
        val markets = field(root, "Markets").apply { setText("NSE_EQUITY,NSE_INDEX,MCX_COMMODITY") }
        val timeframes = field(root, "Timeframes").apply { setText("5M,15M,1H,D,W") }
        val watchlist = field(root, "Watchlist: comma-separated or one item per line")
        val nseLinks = field(root, "Official NSE quote URLs: one per line")
        val swingDays = field(root, "Swing holding guidance (trading days)").apply { setText("5") }
        status = TextView(this)

        val saveButton = Button(this).apply {
            text = "Save secure setup"
            setOnClickListener {
                val input = runCatching {
                    val profile = OperatorProfile(
                        chatId.text.toString().trim(),
                        equity.text.toString().toDouble(),
                        risk.text.toString().toDouble(),
                        InputNormalizer.parseList(markets.text.toString()),
                        InputNormalizer.parseList(timeframes.text.toString()),
                        InputNormalizer.parseList(watchlist.text.toString()),
                        swingDays.text.toString().toInt()
                    )
                    val urls = nseLinks.text.toString().lineSequence().map(String::trim).filter(String::isNotEmpty).toList()
                    NseLinkRegistryCodec.normalize(urls, profile.watchlist.toSet())
                    Triple(profile, urls, token.text.toString().trim())
                }
                if (input.isFailure) {
                    status.text = "Setup rejected: ${input.exceptionOrNull()?.message}"
                    return@setOnClickListener
                }

                isEnabled = false
                status.text = "Saving secure setup…"
                ioExecutor.execute {
                    val result = runCatching {
                        val (profile, urls, rawToken) = input.getOrThrow()
                        profileStore.save(profile)
                        nseLinkStore.save(urls, profile.watchlist.toSet())
                        SecureTokenStore(this@MainActivity).save(rawToken)
                    }
                    runOnUiThread {
                        if (isFinishing || isDestroyed) return@runOnUiThread
                        isEnabled = true
                        result.onSuccess {
                            token.text.clear()
                            status.text = "Setup and official NSE links saved securely"
                        }.onFailure {
                            status.text = "Setup rejected: ${it.message}"
                        }
                        refreshReadinessAsync()
                    }
                }
            }
        }
        root.addView(saveButton)

        root.addView(Button(this).apply {
            text = "Import historical OHLC CSV"
            setOnClickListener {
                startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "text/*"
                    putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("text/csv", "text/comma-separated-values", "text/plain"))
                }, REQUEST_HISTORICAL_CSV)
            }
        })

        root.addView(Button(this).apply {
            text = "Send Telegram test"
            setOnClickListener {
                isEnabled = false
                status.text = "Checking secure Telegram setup…"
                ioExecutor.execute {
                    val savedToken = runCatching { SecureTokenStore(this@MainActivity).load() }.getOrNull()
                    val savedProfile = runCatching { profileStore.load() }.getOrNull()
                    val gate = RuntimeReadinessChecker.evaluate(savedProfile, savedToken, automaticSourceConfigured = false)
                    val result = if (!gate.telegramReady) {
                        Result.failure(IllegalStateException(gate.reasons.joinToString(" ")))
                    } else {
                        TelegramClient.send(
                            savedToken!!,
                            savedProfile!!.privateChatId,
                            "Institutional Trading System test: secure Telegram connection verified."
                        )
                    }
                    runOnUiThread {
                        if (isFinishing || isDestroyed) return@runOnUiThread
                        isEnabled = true
                        status.text = result.fold({ "Telegram test delivered" }, { "Telegram test failed: ${it.message}" })
                        refreshReadinessAsync()
                    }
                }
            }
        })

        root.addView(status)
        setContentView(scroll)
        restoreSetupAsync(chatId, equity, risk, markets, timeframes, watchlist, nseLinks, swingDays)
        refreshReadinessAsync()
        handleShare(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleShare(intent)
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
            status.text = "Historical CSV rejected: missing document"
            return
        }

        status.text = "Validating historical CSV…"
        ioExecutor.execute {
            val result = runCatching {
                val bytes = contentResolver.openInputStream(uri)?.use(HistoricalDataIntake::readBounded)
                    ?: throw IllegalArgumentException("Could not read selected CSV")
                HistoricalDataIntake.validateCsv(bytes)
            }
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                status.text = result.fold(
                    onSuccess = { summary ->
                        "Historical CSV validated: ${summary.rowCount} rows, ${summary.firstTimestamp} to ${summary.lastTimestamp}. Stored only for user-driven validation; automatic live analysis remains locked."
                    },
                    onFailure = { error -> "Historical CSV rejected: ${error.message}" },
                )
            }
        }
    }

    private fun restoreSetupAsync(
        chatId: EditText,
        equity: EditText,
        risk: EditText,
        markets: EditText,
        timeframes: EditText,
        watchlist: EditText,
        nseLinks: EditText,
        swingDays: EditText,
    ) {
        ioExecutor.execute {
            val saved = runCatching { profileStore.load() }.getOrNull() ?: return@execute
            val links = runCatching { nseLinkStore.load(saved.watchlist.toSet()) }.getOrDefault(emptyList())
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                chatId.setText(saved.privateChatId)
                equity.setText(saved.accountEquity.toString())
                risk.setText(saved.riskPercent.toString())
                markets.setText(saved.markets.joinToString(","))
                timeframes.setText(saved.timeframes.joinToString(","))
                watchlist.setText(saved.watchlist.joinToString("\n"))
                nseLinks.setText(links.joinToString("\n") { it.url })
                swingDays.setText(saved.swingHoldingDays.toString())
                status.text = "Secure setup restored"
            }
        }
    }

    private fun refreshReadinessAsync() {
        ioExecutor.execute {
            val savedProfile = runCatching { profileStore.load() }.getOrNull()
            val savedToken = runCatching { SecureTokenStore(this).load() }.getOrNull()
            val savedLinks = savedProfile?.let { profile ->
                runCatching { nseLinkStore.load(profile.watchlist.toSet()) }.getOrDefault(emptyList())
            } ?: emptyList()
            val gate = RuntimeReadinessChecker.evaluate(
                profile = savedProfile,
                telegramToken = savedToken,
                automaticSourceConfigured = false,
            )
            val text = buildString {
                append("Profile: ").append(if (gate.profileReady) "READY" else "NOT READY")
                append(" | Telegram: ").append(if (gate.telegramReady) "READY" else "NOT READY")
                append(" | NSE links: ").append(savedLinks.size)
                append(" | Automatic analysis: ").append(if (gate.automaticAnalysisReady) "READY" else "LOCKED")
                if (savedLinks.isNotEmpty()) append("\nNSE links identify instruments only until lawful machine-readable price data is verified.")
                if (gate.reasons.isNotEmpty()) append("\n").append(gate.reasons.joinToString(" "))
            }
            runOnUiThread {
                if (!isFinishing && !isDestroyed) readiness.text = text
            }
        }
    }

    private fun handleShare(intent: Intent?) {
        if (intent?.action != Intent.ACTION_SEND || intent.type?.startsWith("image/") != true) return
        val uri = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
        status.text = if (uri == null) {
            "Screenshot rejected: missing image"
        } else {
            "Screenshot received. Add symbol, timeframe and capture time before analysis. Hidden prices or indicators will not be inferred."
        }
    }

    private fun field(parent: LinearLayout, hint: String, password: Boolean = false) = EditText(this).apply {
        this.hint = hint
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        if (password) inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        parent.addView(this)
    }

    companion object {
        private const val REQUEST_HISTORICAL_CSV = 1001
    }
}
