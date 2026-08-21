package com.institutionaltrading.mobile

enum class SwingType { HIGH, LOW }
enum class SwingTier { INTERNAL, EXTERNAL }

data class SwingPoint(
    val index: Int,
    val timestamp: String,
    val price: Double,
    val type: SwingType,
    val tier: SwingTier,
)

object MarketStructure {
    const val minorLeft = 2
    const val minorRight = 2
    const val majorLeft = 5
    const val majorRight = 5

    fun confirmedSwings(series: ValidatedBarSeries): List<SwingPoint> {
        val bars = series.bars
        require(bars.all { it.isClosed }) { "Market structure may consume closed bars only" }
        if (bars.size < minorLeft + minorRight + 1) return emptyList()

        val minor = mutableListOf<SwingPoint>()
        for (index in minorLeft until bars.size - minorRight) {
            val bar = bars[index]
            val high = isStrictHigh(bars, index, minorLeft, minorRight)
            val low = isStrictLow(bars, index, minorLeft, minorRight)
            if (high) minor += SwingPoint(index, bar.sourceTimestamp, bar.high, SwingType.HIGH, SwingTier.INTERNAL)
            if (low) minor += SwingPoint(index, bar.sourceTimestamp, bar.low, SwingType.LOW, SwingTier.INTERNAL)
        }

        val major = minor.filter { point ->
            point.index >= majorLeft && point.index < bars.size - majorRight && when (point.type) {
                SwingType.HIGH -> isStrictHigh(bars, point.index, majorLeft, majorRight)
                SwingType.LOW -> isStrictLow(bars, point.index, majorLeft, majorRight)
            }
        }

        val externalKeys = externalSequence(major).map { it.index to it.type }.toSet()
        return minor.map { point ->
            if ((point.index to point.type) in externalKeys) point.copy(tier = SwingTier.EXTERNAL) else point
        }.sortedWith(compareBy<SwingPoint> { it.index }.thenBy { it.type.name })
    }

    fun externalSequence(major: List<SwingPoint>): List<SwingPoint> {
        if (major.isEmpty()) return emptyList()
        val ordered = major.sortedWith(compareBy<SwingPoint> { it.index }.thenBy { it.type.name })
        val result = mutableListOf<SwingPoint>()
        var run = mutableListOf<SwingPoint>()

        fun flush() {
            if (run.isEmpty()) return
            val chosen = when (run.first().type) {
                SwingType.HIGH -> run.maxBy { it.price }
                SwingType.LOW -> run.minBy { it.price }
            }
            result += chosen.copy(tier = SwingTier.EXTERNAL)
            run = mutableListOf()
        }

        for (point in ordered) {
            if (run.isEmpty() || point.type == run.first().type) {
                run += point
            } else {
                flush()
                run += point
            }
        }
        flush()
        return result
    }

    private fun isStrictHigh(bars: List<MarketBar>, index: Int, left: Int, right: Int): Boolean {
        val value = bars[index].high
        return (1..left).all { value > bars[index - it].high } &&
            (1..right).all { value > bars[index + it].high }
    }

    private fun isStrictLow(bars: List<MarketBar>, index: Int, left: Int, right: Int): Boolean {
        val value = bars[index].low
        return (1..left).all { value < bars[index - it].low } &&
            (1..right).all { value < bars[index + it].low }
    }
}
