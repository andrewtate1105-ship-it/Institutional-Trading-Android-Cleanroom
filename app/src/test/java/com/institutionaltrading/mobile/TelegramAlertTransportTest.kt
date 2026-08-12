package com.institutionaltrading.mobile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TelegramAlertTransportTest {
    @Test
    fun sendsUsingProvidedSecureValuesWithoutPersistingThemInTransport() {
        var capturedToken = ""
        var capturedChatId = ""
        var capturedText = ""
        val transport = TelegramAlertTransport(
            tokenProvider = { "123456789:ABCDEFGHIJKLMNOPQRSTUVWXYZ_ab" },
            chatIdProvider = { "123456789" },
            sender = { token, chatId, text ->
                capturedToken = token
                capturedChatId = chatId
                capturedText = text
                Result.success(Unit)
            },
        )

        assertTrue(transport.send("signal").isSuccess)
        assertEquals("123456789:ABCDEFGHIJKLMNOPQRSTUVWXYZ_ab", capturedToken)
        assertEquals("123456789", capturedChatId)
        assertEquals("signal", capturedText)
    }

    @Test
    fun failsClosedWhenTokenIsMissing() {
        var called = false
        val transport = TelegramAlertTransport(
            tokenProvider = { null },
            chatIdProvider = { "123456789" },
            sender = { _, _, _ -> called = true; Result.success(Unit) },
        )

        assertTrue(transport.send("signal").isFailure)
        assertTrue(!called)
    }

    @Test
    fun failsClosedWhenChatIdIsInvalid() {
        var called = false
        val transport = TelegramAlertTransport(
            tokenProvider = { "123456789:ABCDEFGHIJKLMNOPQRSTUVWXYZ_ab" },
            chatIdProvider = { "@username" },
            sender = { _, _, _ -> called = true; Result.success(Unit) },
        )

        assertTrue(transport.send("signal").isFailure)
        assertTrue(!called)
    }

    @Test
    fun propagatesTelegramDeliveryFailureForCoordinatorRetryHandling() {
        val transport = TelegramAlertTransport(
            tokenProvider = { "123456789:ABCDEFGHIJKLMNOPQRSTUVWXYZ_ab" },
            chatIdProvider = { "123456789" },
            sender = { _, _, _ -> Result.failure(IllegalStateException("network")) },
        )

        assertTrue(transport.send("signal").isFailure)
    }
}
