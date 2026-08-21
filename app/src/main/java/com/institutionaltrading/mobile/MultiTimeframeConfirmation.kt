package com.institutionaltrading.mobile

/**
 * Maps the operator-selected execution timeframe to one higher closed-bar context timeframe.
 * Weekly is the terminal context and therefore has no additional confirmation timeframe.
 */
object MultiTimeframeConfirmation {
    private val confirmationMap = mapOf(
        "5M" to "15M",
        "15M" to "1H",
        "1H" to "D",
        "D" to "W",
        "W" to null,
    )

    fun confirmationTimeframe(timeframe: String): String? {
        require(timeframe in Validation.defaultTimeframes) { "Unsupported timeframe" }
        return confirmationMap.getValue(timeframe)
    }

    fun isAligned(direction: SignalDirection, higherAssessment: InstitutionalAssessment): Boolean = when (direction) {
        SignalDirection.LONG -> higherAssessment.bias == InstitutionalBias.BULLISH
        SignalDirection.SHORT -> higherAssessment.bias == InstitutionalBias.BEARISH
        SignalDirection.NO_TRADE -> false
    }

    fun mismatchReason(timeframe: String, higherTimeframe: String): String =
        "NO TRADE: $timeframe setup is not aligned with $higherTimeframe institutional context"
}
