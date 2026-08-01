package com.institutionaltrading.mobile

import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

object OfficialNseUrl {
    private val hosts = setOf("www.nseindia.com", "nseindia.com", "archives.nseindia.com")
    private val quotePaths = setOf("/get-quotes/equity", "/get-quotes/derivatives")

    fun isWhitelistedQuoteUrl(value: String): Boolean = extractSymbol(value) != null

    fun extractSymbol(value: String): String? {
        return try {
            val uri = URI(value.trim())
            if (uri.scheme?.lowercase() != "https" || uri.host?.lowercase() !in hosts) return null
            if (uri.userInfo != null || uri.port != -1 || uri.fragment != null || uri.path !in quotePaths) return null

            val symbolValues = uri.rawQuery
                ?.split('&')
                ?.mapNotNull { parameter ->
                    val parts = parameter.split('=', limit = 2)
                    if (parts.size != 2 || parts[0].lowercase() != "symbol") null
                    else URLDecoder.decode(parts[1], StandardCharsets.UTF_8.name())
                }
                .orEmpty()

            if (symbolValues.size != 1) null else Validation.normalizeCanonicalSymbol(symbolValues.single())
        } catch (_: Exception) {
            null
        }
    }
}
