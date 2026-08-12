package com.institutionaltrading.mobile

enum class TrendState { UPTREND, DOWNTREND, RANGE, UNKNOWN }

data class TrendClassification(
    val state: TrendState,
    val latestExternalHigh: SwingPoint?,
    val latestExternalLow: SwingPoint?,
    val warning: String?,
)

object TrendClassifier {
    fun classify(swings: List<SwingPoint>): TrendClassification {
        val external = swings.filter { it.tier == SwingTier.EXTERNAL }
            .sortedWith(compareBy<SwingPoint> { it.index }.thenBy { it.type.name })

        val highs = external.filter { it.type == SwingType.HIGH }
        val lows = external.filter { it.type == SwingType.LOW }
        val latestHigh = highs.lastOrNull()
        val latestLow = lows.lastOrNull()

        if (highs.size < 2 || lows.size < 2) {
            return TrendClassification(
                state = TrendState.UNKNOWN,
                latestExternalHigh = latestHigh,
                latestExternalLow = latestLow,
                warning = "LOW_CONFIRMATION_BAR_COUNT",
            )
        }

        val previousHigh = highs[highs.lastIndex - 1]
        val previousLow = lows[lows.lastIndex - 1]

        val state = when {
            latestHigh!!.price > previousHigh.price && latestLow!!.price > previousLow.price -> TrendState.UPTREND
            latestHigh.price < previousHigh.price && latestLow.price < previousLow.price -> TrendState.DOWNTREND
            else -> TrendState.RANGE
        }

        return TrendClassification(
            state = state,
            latestExternalHigh = latestHigh,
            latestExternalLow = latestLow,
            warning = null,
        )
    }
}
