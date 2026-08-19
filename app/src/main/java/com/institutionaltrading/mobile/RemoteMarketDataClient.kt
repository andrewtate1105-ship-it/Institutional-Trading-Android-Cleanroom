package com.institutionaltrading.mobile

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

object RemoteMarketDataClient {
    private const val connectTimeoutMs = 5_000
    private const val readTimeoutMs = 10_000
    private const val maxResponseBytes = HistoricalDataIntake.maxBytes

    fun resolveStock(baseUrl: String, stockNameOrSymbol: String): InstrumentIdentity {
        val base = validateBase(baseUrl)
        val typed = stockNameOrSymbol.trim().replace(Regex("\\s+"), " ")
        require(typed.isNotEmpty()) { "Enter a stock name or NSE symbol" }
        require(typed.length <= 100) { "Stock name is too long" }

        val endpoint = base.resolve("/v1/resolve?q=${encode(typed)}").toURL()
        val connection = open(endpoint, "application/json")
        try {
            val status = connection.responseCode
            if (status !in 200..299) {
                throw MarketDataUnavailableException("Could not resolve NSE stock (HTTP $status)")
            }
            val bytes = connection.inputStream.use(HistoricalDataIntake::readBounded)
            val json = JSONObject(bytes.toString(Charsets.UTF_8))
            val symbol = Validation.normalizeWatchlistItem(json.getString("symbol"))
            val displayName = json.getString("displayName").trim()
            require(displayName.isNotBlank()) { "Resolved stock name is missing" }
            return InstrumentIdentity(displayName, symbol)
        } catch (error: MarketDataUnavailableException) {
            throw error
        } catch (error: Exception) {
            throw MarketDataUnavailableException("Could not resolve NSE stock: ${error.message ?: error.javaClass.simpleName}")
        } finally {
            connection.disconnect()
        }
    }

    fun fetch(baseUrl: String, symbol: String, timeframe: String, limit: Int = 200): ImportedMarketData {
        val base = validateBase(baseUrl)
        require(limit in 20..500) { "Market-data limit must be 20-500" }
        val normalizedSymbol = Validation.normalizeWatchlistItem(symbol)
        require(timeframe in Validation.defaultTimeframes) { "Unsupported timeframe" }
        val query = "symbol=${encode(normalizedSymbol)}&timeframe=${encode(timeframe)}&limit=$limit"
        val endpoint = base.resolve("/v1/ohlc?$query").toURL()
        val connection = open(endpoint, "text/csv")

        try {
            val status = connection.responseCode
            if (status !in 200..299) {
                throw MarketDataUnavailableException("Automatic market data unavailable (HTTP $status)")
            }
            val bytes = connection.inputStream.use(HistoricalDataIntake::readBounded)
            require(bytes.size <= maxResponseBytes) { "Market-data response exceeds safety limit" }
            val rows = BacktestCsvImporter.parse(bytes.toString(Charsets.UTF_8))
            require(rows.size >= 20) { "Automatic market data returned too few validated bars" }
            return ImportedMarketData(rows, normalizedSymbol, timeframe)
        } catch (error: MarketDataUnavailableException) {
            throw error
        } catch (error: Exception) {
            throw MarketDataUnavailableException("Automatic market data unavailable: ${error.message ?: error.javaClass.simpleName}")
        } finally {
            connection.disconnect()
        }
    }

    private fun validateBase(baseUrl: String): URI {
        require(baseUrl.isNotBlank()) { "Automatic market-data service is not configured" }
        val base = URI(baseUrl.trim().trimEnd('/') + "/")
        require(base.scheme == "https") { "Market-data service must use HTTPS" }
        require(base.host?.isNotBlank() == true) { "Market-data service host is invalid" }
        return base
    }

    private fun open(url: java.net.URL, accept: String) = (url.openConnection() as HttpURLConnection).apply {
        requestMethod = "GET"
        connectTimeout = connectTimeoutMs
        readTimeout = readTimeoutMs
        useCaches = false
        setRequestProperty("Accept", accept)
    }

    private fun encode(value: String): String = URLEncoder.encode(value, StandardCharsets.UTF_8.name())
}
