package com.institutionaltrading.mobile

/**
 * Backtest-only research candidate. This module does not dispatch alerts or execute trades.
 * It uses only confirmed closed-bar structure and therefore remains free of forming-bar look-ahead.
 */
enum class CandidateDirection { LONG, SHORT, NONE }

data class StrategyCandidate(
    val direction: CandidateDirection,
    val timestamp: String,
    val entryPrice: Double?,
    val structuralStop: Double?,
    val reason: String,
)

object TrendFollowingCandidate {
    fun evaluate(series: ValidatedBarSeries): StrategyCandidate {
        require(series.bars.all { it.isClosed }) { "Candidate engine accepts closed bars only" }
        val latest = series.bars.last()
        val swings = MarketStructure.confirmedSwings(series)
        val trend = TrendClassifier.classify(swings)

        return when (trend.state) {
            TrendState.UPTREND -> {
                val breakout = trend.latestExternalHigh
                val stop = trend.latestExternalLow
                if (breakout != null && stop != null && latest.close > breakout.price && stop.price < latest.close) {
                    StrategyCandidate(
                        direction = CandidateDirection.LONG,
                        timestamp = latest.sourceTimestamp,
                        entryPrice = latest.close,
                        structuralStop = stop.price,
                        reason = "Confirmed uptrend with closed-bar break above latest external swing high",
                    )
                } else none(latest, "Uptrend present but no confirmed closed-bar continuation break")
            }
            TrendState.DOWNTREND -> {
                val breakout = trend.latestExternalLow
                val stop = trend.latestExternalHigh
                if (breakout != null && stop != null && latest.close < breakout.price && stop.price > latest.close) {
                    StrategyCandidate(
                        direction = CandidateDirection.SHORT,
                        timestamp = latest.sourceTimestamp,
                        entryPrice = latest.close,
                        structuralStop = stop.price,
                        reason = "Confirmed downtrend with closed-bar break below latest external swing low",
                    )
                } else none(latest, "Downtrend present but no confirmed closed-bar continuation break")
            }
            TrendState.RANGE -> none(latest, "Range regime: no trend-following candidate")
            TrendState.UNKNOWN -> none(latest, "Insufficient confirmed external structure")
        }
    }

    private fun none(latest: MarketBar, reason: String) = StrategyCandidate(
        direction = CandidateDirection.NONE,
        timestamp = latest.sourceTimestamp,
        entryPrice = null,
        structuralStop = null,
        reason = reason,
    )
}
