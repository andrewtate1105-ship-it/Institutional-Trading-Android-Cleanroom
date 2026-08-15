package com.institutionaltrading.mobile

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class NseInstrumentLink(
    val symbol: String,
    val url: String,
)

object NseLinkRegistryCodec {
    fun normalize(urls: Collection<String>, allowedSymbols: Set<String>): List<NseInstrumentLink> {
        val normalizedAllowed = allowedSymbols.map(Validation::normalizeWatchlistItem).toSet()
        val bySymbol = linkedMapOf<String, NseInstrumentLink>()

        urls.map(String::trim).filter(String::isNotEmpty).forEach { url ->
            val symbol = OfficialNseUrl.normalizeToSymbol(url)
                ?: throw IllegalArgumentException("Only supported official NSE quote URLs are allowed")
            require(symbol in normalizedAllowed) { "NSE URL symbol $symbol is not in the saved watchlist" }
            require(bySymbol.putIfAbsent(symbol, NseInstrumentLink(symbol, url)) == null) {
                "Duplicate NSE URL for $symbol"
            }
        }
        return bySymbol.values.toList()
    }

    fun encode(links: List<NseInstrumentLink>): String = JSONObject()
        .put("schema", 1)
        .put("links", JSONArray().also { rows ->
            links.sortedBy { it.symbol }.forEach { link ->
                rows.put(JSONObject().put("symbol", link.symbol).put("url", link.url))
            }
        })
        .toString()

    fun decode(raw: String, allowedSymbols: Set<String>): List<NseInstrumentLink> {
        val root = JSONObject(raw)
        require(root.getInt("schema") == 1) { "Unsupported NSE-link registry schema" }
        val rows = root.getJSONArray("links")
        val urls = buildList {
            for (index in 0 until rows.length()) {
                val row = rows.getJSONObject(index)
                val url = row.getString("url")
                val expected = row.getString("symbol")
                val actual = OfficialNseUrl.normalizeToSymbol(url)
                    ?: throw IllegalArgumentException("Stored NSE URL is invalid")
                require(actual == expected) { "Stored NSE URL symbol mismatch" }
                add(url)
            }
        }
        return normalize(urls, allowedSymbols)
    }
}

class NseLinkRegistryStore(context: Context) {
    private val file = File(context.filesDir, "operator/nse-links.json")

    fun load(allowedSymbols: Set<String>): List<NseInstrumentLink> =
        if (!file.exists()) emptyList() else NseLinkRegistryCodec.decode(file.readText(), allowedSymbols)

    fun save(urls: Collection<String>, allowedSymbols: Set<String>) {
        val links = NseLinkRegistryCodec.normalize(urls, allowedSymbols)
        file.parentFile?.mkdirs()
        val temp = File(file.parentFile, "nse-links.json.tmp")
        temp.writeText(NseLinkRegistryCodec.encode(links))
        require(temp.renameTo(file)) { "Could not atomically save NSE link registry" }
    }
}
