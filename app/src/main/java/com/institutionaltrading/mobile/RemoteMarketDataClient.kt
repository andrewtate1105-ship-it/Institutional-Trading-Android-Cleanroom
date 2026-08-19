package com.institutionaltrading.mobile

import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

object RemoteMarketDataClient {
    private const val connectTimeoutMs = 5_000
    private const val readTimeoutMs = 10_000
    private const val maxResponseBytes = HistoricalDataIntake.maxBytes

    fun fetch(baseUrl: String, symbol: String, timeframe: String, limit: Int = 200): ImportedMarketData {
        require(baseUrl.isNotBlank()) { "Automatic market-data service is not configured" }
        require(limit in 20..500) { "Market-data limit must be 20-500" }
        val base = URI(baseUrl.trim())
        require(base.scheme == "https") { "Market-data service must use HTTPS" }
        require(base.host?.isNotBlank() == true) { "Market-data service host is invalid" }

        val normalizedSymbol = Validation.normalizeWatchlistItem(symbol)
        require(timeframe in Validation.defaultTimeframes) { "Unsupported timeframe" }
        val query = "symbol=${encode(normalizedSymbol)}&timeframe=${encode(timeframe)}&limit=$limit"
        val endpoint = base.resolve("/v1/ohlc?$query").toURL()
        val connection = (endpoint.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = connectTimeoutMs
            readTimeout = readTimeoutMs
            useCaches = false
            setRequestProperty("Accept", "text/csv")
        }

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

    private fun encode(value: String): String = URLEncoder.encode(value, StandardCharsets.UTF_8.name())
}
