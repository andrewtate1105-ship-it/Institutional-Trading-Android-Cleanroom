package com.institutionaltrading.mobile

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object TelegramClient {
    fun send(token: String, chatId: String, text: String): Result<Unit> = runCatching {
        require(token.isNotBlank()) { "Telegram token is required" }
        require(text.length <= 4096) { "Telegram message is too long" }
        val connection = URL("https://api.telegram.org/bot$token/sendMessage").openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.connectTimeout = 10_000
        connection.readTimeout = 15_000
        connection.doOutput = true
        connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
        val payload = JSONObject().put("chat_id", chatId).put("text", text).put("disable_web_page_preview", true)
        connection.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }
        val code = connection.responseCode
        require(code in 200..299) { "Telegram delivery failed with HTTP $code" }
    }
}
