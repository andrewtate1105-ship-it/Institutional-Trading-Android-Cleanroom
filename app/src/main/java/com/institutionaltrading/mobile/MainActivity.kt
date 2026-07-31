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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(32,32,32,32) }
        val scroll = ScrollView(this).apply { addView(root) }
        root.addView(TextView(this).apply { text = "Institutional Trading System"; textSize = 22f })
        root.addView(TextView(this).apply { text = "Signal-only Android build. No Groww login and no automated orders." })
        val chatId = field(root, "Private Telegram chat ID")
        val token = field(root, "Telegram bot token", true)
        val equity = field(root, "Account equity (INR)")
        val risk = field(root, "Risk percent (max 2)").apply { setText("1") }
        val markets = field(root, "Markets").apply { setText("NSE_EQUITY") }
        val timeframes = field(root, "Timeframes").apply { setText("5M,15M,1H,D,W") }
        val watchlist = field(root, "Watchlist symbols or official NSE quote URLs")
        val swingDays = field(root, "Swing holding guidance (trading days)").apply { setText("5") }
        status = TextView(this)
        root.addView(Button(this).apply {
            text = "Save secure setup"
            setOnClickListener {
                runCatching {
                    val profile = OperatorProfile(chatId.text.toString().trim(), equity.text.toString().toDouble(), risk.text.toString().toDouble(), csv(markets.text.toString()), csv(timeframes.text.toString()), csv(watchlist.text.toString()), swingDays.text.toString().toInt())
                    ProfileStore(this@MainActivity).save(profile)
                    SecureTokenStore(this@MainActivity).save(token.text.toString().trim())
                    token.text.clear()
                    "Setup saved securely"
                }.onSuccess { status.text = it }.onFailure { status.text = "Setup rejected: ${it.message}" }
            }
        })
        root.addView(Button(this).apply {
            text = "Send Telegram test"
            setOnClickListener {
                val savedToken = runCatching { SecureTokenStore(this@MainActivity).load() }.getOrNull()
                if (savedToken == null) { status.text = "Complete setup first"; return@setOnClickListener }
                thread {
                    val result = TelegramClient.send(savedToken, chatId.text.toString().trim(), "Institutional Trading System test: signal-only Android connection verified.")
                    runOnUiThread { status.text = result.fold({ "Telegram test delivered" }, { "Telegram test failed: ${it.message}" }) }
                }
            }
        })
        root.addView(status)
        setContentView(scroll)
        handleShare(intent)
    }

    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); setIntent(intent); handleShare(intent) }

    private fun handleShare(intent: Intent?) {
        if (intent?.action != Intent.ACTION_SEND || intent.type?.startsWith("image/") != true) return
        val uri = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
        status.text = if (uri == null) "Screenshot rejected: missing image" else "Screenshot received. Add symbol, timeframe and capture time before analysis. Hidden prices or indicators will not be inferred."
    }

    private fun field(parent: LinearLayout, hint: String, password: Boolean = false) = EditText(this).apply {
        this.hint = hint
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        if (password) inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        parent.addView(this)
    }

    private fun csv(value: String) = value.split(',').map { it.trim() }.filter { it.isNotEmpty() }.toSet()
}
