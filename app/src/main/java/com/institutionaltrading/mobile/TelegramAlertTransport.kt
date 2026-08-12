package com.institutionaltrading.mobile

class TelegramAlertTransport(
    private val tokenProvider: () -> String?,
    private val chatIdProvider: () -> String?,
    private val sender: (String, String, String) -> Result<Unit> = TelegramClient::send,
) : AlertTransport {
    override fun send(text: String): Result<Unit> = runCatching {
        require(text.isNotBlank()) { "Telegram alert text is required" }

        val token = tokenProvider()?.trim().orEmpty()
        require(token.matches(Regex("^[0-9]{6,12}:[A-Za-z0-9_-]{20,}$"))) {
            "Stored Telegram bot token is missing or invalid"
        }

        val chatId = chatIdProvider()?.trim().orEmpty()
        require(chatId.matches(Regex("^-?[0-9]{5,20}$"))) {
            "Stored Telegram private chat ID is missing or invalid"
        }

        sender(token, chatId, text).getOrThrow()
    }
}
