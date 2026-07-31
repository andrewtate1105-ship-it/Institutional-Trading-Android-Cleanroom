package com.institutionaltrading.mobile

import java.net.URI

object OfficialNseUrl {
    private val hosts = setOf("www.nseindia.com", "nseindia.com", "archives.nseindia.com")

    fun isWhitelistedQuoteUrl(value: String): Boolean = try {
        val uri = URI(value)
        uri.scheme == "https" && uri.host?.lowercase() in hosts &&
            (uri.path.startsWith("/get-quotes/equity") || uri.path.startsWith("/get-quotes/derivatives"))
    } catch (_: Exception) { false }
}
