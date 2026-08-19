package com.institutionaltrading.mobile

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.Locale

object TradingViewDirectSymbolResolver {
    private const val endpoint = "https://symbol-search.tradingview.com/symbol_search/v3"
    private const val timeoutMs = 8_000
    private val symbolPattern = Regex("^[A-Z0-9&._-]{1,32}$")

    fun resolve(stockNameOrSymbol: String): InstrumentIdentity {
        val typed = stockNameOrSymbol.trim().replace(Regex("\\s+"), " ")
        require(typed.isNotEmpty()) { "Enter a stock name or NSE symbol" }
        require(typed.length <= 100) { "Stock name is too long" }

        val url = buildString {
            append(endpoint)
            append("?exchange=NSE&search_type=stock&start=0&text=")
            append(URLEncoder.encode(typed, StandardCharsets.UTF_8.name()))
        }
        val connection = (java.net.URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = timeoutMs
            readTimeout = timeoutMs
            useCaches = false
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Origin", "https://www.tradingview.com")
            setRequestProperty("User-Agent", "Mozilla/5.0 Android TradingSignalAssistant")
        }

        try {
            val status = connection.responseCode
            if (status !in 200..299) {
                throw MarketDataUnavailableException("TradingView stock search unavailable (HTTP $status)")
            }
            val bytes = connection.inputStream.use(HistoricalDataIntake::readBounded)
            val symbols = JSONObject(bytes.toString(Charsets.UTF_8)).getJSONArray("symbols")
            val candidates = buildList {
                for (index in 0 until symbols.length()) {
                    val item = symbols.getJSONObject(index)
                    val symbol = item.optString("symbol").trim().uppercase(Locale.ROOT)
                    val exchange = item.optString("exchange").trim().uppercase(Locale.ROOT)
                    val prefix = item.optString("prefix").trim().uppercase(Locale.ROOT)
                    if (!symbolPattern.matches(symbol)) continue
                    if (exchange != "NSE" && prefix != "NSE") continue
                    val description = item.optString("description").trim().ifBlank { symbol }
                    add(InstrumentIdentity(description, symbol))
                }
            }
            if (candidates.isEmpty()) throw MarketDataUnavailableException("No matching NSE equity found")

            val upper = typed.uppercase(Locale.ROOT)
            candidates.firstOrNull { it.symbol == upper }?.let { return it }

            val normalized = normalizeName(typed)
            candidates.firstOrNull { normalizeName(it.displayName) == normalized }?.let { return it }

            if (candidates.size == 1) return candidates.single()
            throw MarketDataUnavailableException("Stock name is ambiguous; enter the NSE symbol")
        } catch (error: MarketDataUnavailableException) {
            throw error
        } catch (error: Exception) {
            throw MarketDataUnavailableException("TradingView stock search unavailable: ${error.message ?: error.javaClass.simpleName}")
        } finally {
            connection.disconnect()
        }
    }

    internal fun normalizeName(value: String): String = value
        .uppercase(Locale.ROOT)
        .replace(Regex("\\b(LIMITED|LTD)\\b"), "")
        .replace(Regex("[^A-Z0-9&]+"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()
}
