package com.institutionaltrading.mobile

data class RuntimeReadiness(
    val profileReady: Boolean,
    val automaticAnalysisReady: Boolean,
    val reasons: List<String>,
) {
    val fullyReady: Boolean
        get() = profileReady && automaticAnalysisReady
}

object RuntimeReadinessChecker {
    fun evaluate(
        profile: OperatorProfile?,
        automaticSourceConfigured: Boolean,
    ): RuntimeReadiness {
        val reasons = mutableListOf<String>()

        val profileReady = runCatching {
            requireNotNull(profile) { "Operator profile is missing" }
            Validation.profile(profile)
        }.isSuccess
        if (!profileReady) reasons += "Complete and save a valid trading profile."

        val automaticAnalysisReady = profileReady && automaticSourceConfigured
        if (!automaticSourceConfigured) reasons += "No lawful automatic market-data source is configured yet."

        return RuntimeReadiness(
            profileReady = profileReady,
            automaticAnalysisReady = automaticAnalysisReady,
            reasons = reasons.distinct(),
        )
    }
}
