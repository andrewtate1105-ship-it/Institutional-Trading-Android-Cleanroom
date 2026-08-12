package com.institutionaltrading.mobile

data class RuntimeReadiness(
    val profileReady: Boolean,
    val telegramReady: Boolean,
    val automaticAnalysisReady: Boolean,
    val reasons: List<String>,
) {
    val fullyReady: Boolean
        get() = profileReady && telegramReady && automaticAnalysisReady
}

object RuntimeReadinessChecker {
    fun evaluate(
        profile: OperatorProfile?,
        telegramToken: String?,
        automaticSourceConfigured: Boolean,
    ): RuntimeReadiness {
        val reasons = mutableListOf<String>()

        val profileReady = runCatching {
            requireNotNull(profile) { "Operator profile is missing" }
            Validation.profile(profile)
        }.isSuccess
        if (!profileReady) reasons += "Complete and save a valid operator profile."

        val token = telegramToken?.trim().orEmpty()
        val telegramReady = token.matches(Regex("^[0-9]{6,12}:[A-Za-z0-9_-]{20,}$")) && profileReady
        if (!telegramReady) reasons += "Store a valid Telegram bot token and private chat ID."

        val automaticAnalysisReady = profileReady && telegramReady && automaticSourceConfigured
        if (!automaticSourceConfigured) reasons += "No lawful automatic market-data source is configured yet."

        return RuntimeReadiness(
            profileReady = profileReady,
            telegramReady = telegramReady,
            automaticAnalysisReady = automaticAnalysisReady,
            reasons = reasons.distinct(),
        )
    }
}
