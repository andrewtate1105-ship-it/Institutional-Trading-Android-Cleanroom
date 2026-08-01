package com.institutionaltrading.mobile

import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.Locale

object OfficialNseUrl {
    private val hosts = setOf("www.nseindia.com", "nseindia.com", "archives.nseindia.com")
    private val symbol = Regex("^[A-Z0-9&._-]{1,32}$")

    fun isWhitelistedQuoteUrl(value: String): Boolean = normalizeToSymbol(value) != null

    fun normalizeToSymbol(value: String): String? = try {
        val uri = URI(value.trim())
        if (uri.scheme?.lowercase(Locale.ROOT) != "https") return null
        if (uri.host?.lowercase(Locale.ROOT) !in hosts) return null
        if (uri.userInfo != null || uri.fragment != null) return null
        if (uri.path != "/get-quotes/equity" && uri.path != "/get-quotes/derivatives") return null

        val values = uri.rawQuery
            ?.split('&')
            ?.mapNotNull { part ->
                val pieces = part.split('=', limit = 2)
                if (pieces.size != 2 || !pieces[0].equals("symbol", ignoreCase = true)) null
                else URLDecoder.decode(pieces[1], StandardCharsets.UTF_8.name())
            }
            .orEmpty()

        if (values.size != 1) return null
        values.single().trim().uppercase(Locale.ROOT).takeIf(symbol::matches)
    } catch (_: Exception) {
        null
    }
}
