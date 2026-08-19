package com.institutionaltrading.mobile

object AnalysisFailurePresentation {
    fun format(error: Throwable): String {
        val reason = error.message?.trim().takeUnless { it.isNullOrEmpty() }
            ?: "Analysis could not be completed"
        val status = if (error is MarketDataUnavailableException) {
            "DATA UNAVAILABLE"
        } else {
            "INPUT REJECTED"
        }
        return "SIGNAL: NO TRADE\nSTATUS: $status\nReason: $reason"
    }
}
