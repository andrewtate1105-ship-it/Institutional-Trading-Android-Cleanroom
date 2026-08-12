package com.institutionaltrading.mobile

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.view.ViewGroup
import android.widget.*
import kotlin.concurrent.thread

class MainActivity : Activity() {
    private lateinit var status: TextView
    private lateinit var readiness: TextView
    private lateinit var profileStore: ProfileStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        profileStore = ProfileStore(this)

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
        val swingDays = field(root, "Swing holding guidance (trading days)").apply { setText("5") }
        status = TextView(this)

        profileStore.load()?.let { saved ->
            chatId.setText(saved.privateChatId)
            equity.setText(saved.accountEquity.toString())
            risk.setText(saved.riskPercent.toString())
            markets.setText(saved.markets.joinToString(","))
            timeframes.setText(saved.timeframes.joinToString(","))
            watchlist.setText(saved.watchlist.joinToString("\n"))
            swingDays.setText(saved.swingHoldingDays.toString())
            status.text = "Secure setup restored"
        }

        root.addView(Button(this).apply {
            text = "Save secure setup"
            setOnClickListener {
                runCatching {
                    val profile = OperatorProfile(
                        chatId.text.toString().trim(),
                        equity.text.toString().toDouble(),
                        risk.text.toString().toDouble(),
                        InputNormalizer.parseList(markets.text.toString()),
                        InputNormalizer.parseList(timeframes.text.toString()),
                        InputNormalizer.parseList(watchlist.text.toString()),
                        swingDays.text.toString().toInt()
                    )
                    profileStore.save(profile)
                    SecureTokenStore(this@MainActivity).save(token.text.toString().trim())
                    token.text.clear()
                    "Setup saved securely"
                }.onSuccess {
                    status.text = it
                    refreshReadiness()
                }.onFailure {
                    status.text = "Setup rejected: ${it.message}"
                    refreshReadiness()
                }
            }
        })

        root.addView(Button(this).apply {
            text = "Send Telegram test"
            setOnClickListener {
                val savedToken = runCatching { SecureTokenStore(this@MainActivity).load() }.getOrNull()
                val savedProfile = profileStore.load()
                val gate = RuntimeReadinessChecker.evaluate(savedProfile, savedToken, automaticSourceConfigured = false)
                if (!gate.telegramReady) {
                    status.text = "Telegram test locked: ${gate.reasons.joinToString(" ")}"
                    refreshReadiness()
                    return@setOnClickListener
                }
                thread {
                    val result = TelegramClient.send(
                        savedToken!!,
                        savedProfile!!.privateChatId,
                        "Institutional Trading System test: secure Telegram connection verified."
                    )
                    runOnUiThread {
                        status.text = result.fold({ "Telegram test delivered" }, { "Telegram test failed: ${it.message}" })
                        refreshReadiness()
                    }
                }
            }
        })

        root.addView(status)
        setContentView(scroll)
        refreshReadiness()
        handleShare(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleShare(intent)
    }

    private fun refreshReadiness() {
        val savedProfile = runCatching { profileStore.load() }.getOrNull()
        val savedToken = runCatching { SecureTokenStore(this).load() }.getOrNull()
        val gate = RuntimeReadinessChecker.evaluate(
            profile = savedProfile,
            telegramToken = savedToken,
            automaticSourceConfigured = false,
        )
        readiness.text = buildString {
            append("Profile: ").append(if (gate.profileReady) "READY" else "NOT READY")
            append(" | Telegram: ").append(if (gate.telegramReady) "READY" else "NOT READY")
            append(" | Automatic analysis: ").append(if (gate.automaticAnalysisReady) "READY" else "LOCKED")
            if (gate.reasons.isNotEmpty()) append("\n").append(gate.reasons.joinToString(" "))
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
}
