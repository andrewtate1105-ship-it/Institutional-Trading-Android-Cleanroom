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
    private val supportedTypes = setOf("stock", "index")

    fun resolve(stockNameOrSymbol: String): InstrumentIdentity {
        val typed = stockNameOrSymbol.trim().replace(Regex("\\s+"), " ")
        require(typed.isNotEmpty()) { "Enter a stock/index name or NSE symbol" }
        require(typed.length <= 100) { "Instrument name is too long" }

        // search_type=undefined intentionally asks TradingView for all matching instrument types.
        // We then fail closed to NSE stocks and indices only, which lets inputs such as NIFTY
        // resolve without broadening the app into unsupported funds, futures or other products.
        val url = buildString {
            append(endpoint)
            append("?exchange=NSE&search_type=undefined&start=0&text=")
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
                throw MarketDataUnavailableException("TradingView instrument search unavailable (HTTP $status)")
            }
            val bytes = connection.inputStream.use(HistoricalDataIntake::readBounded)
            val symbols = JSONObject(bytes.toString(Charsets.UTF_8)).getJSONArray("symbols")
            val candidates = buildList {
                for (index in 0 until symbols.length()) {
                    val item = symbols.getJSONObject(index)
                    val symbol = item.optString("symbol").trim().uppercase(Locale.ROOT)
                    val exchange = item.optString("exchange").trim().uppercase(Locale.ROOT)
                    val prefix = item.optString("prefix").trim().uppercase(Locale.ROOT)
                    val type = item.optString("type").trim().lowercase(Locale.ROOT)
                    if (!symbolPattern.matches(symbol)) continue
                    if (!isSupportedNseInstrument(exchange, prefix, type)) continue
                    val description = item.optString("description").trim().ifBlank { symbol }
                    add(InstrumentIdentity(description, symbol))
                }
            }
            if (candidates.isEmpty()) throw MarketDataUnavailableException("No matching NSE stock or index found")

            val upper = typed.uppercase(Locale.ROOT)
            candidates.firstOrNull { it.symbol == upper }?.let { return it }

            val normalized = normalizeName(typed)
            candidates.firstOrNull { normalizeName(it.displayName) == normalized }?.let { return it }

            if (candidates.size == 1) return candidates.single()
            throw MarketDataUnavailableException("Instrument name is ambiguous; enter the NSE symbol")
        } catch (error: MarketDataUnavailableException) {
            throw error
        } catch (error: Exception) {
            throw MarketDataUnavailableException("TradingView instrument search unavailable: ${error.message ?: error.javaClass.simpleName}")
        } finally {
            connection.disconnect()
        }
    }

    internal fun isSupportedNseInstrument(exchange: String, prefix: String, type: String): Boolean {
        val isNse = exchange.equals("NSE", ignoreCase = true) || prefix.equals("NSE", ignoreCase = true)
        return isNse && type.lowercase(Locale.ROOT) in supportedTypes
    }

    internal fun normalizeName(value: String): String = value
        .uppercase(Locale.ROOT)
        .replace(Regex("\\b(LIMITED|LTD)\\b"), "")
        .replace(Regex("[^A-Z0-9&]+"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()
}
