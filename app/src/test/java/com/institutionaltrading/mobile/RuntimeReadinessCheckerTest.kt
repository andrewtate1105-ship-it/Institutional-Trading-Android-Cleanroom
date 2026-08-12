package com.institutionaltrading.mobile

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RuntimeReadinessCheckerTest {
    private fun profile() = OperatorProfile(
        privateChatId = "123456789",
        accountEquity = 100000.0,
        riskPercent = 1.0,
        markets = setOf("NSE_EQUITY"),
        timeframes = setOf("15M"),
        watchlist = setOf("RELIANCE"),
    )

    private fun syntheticToken(): String = "123456789:" + "A".repeat(24)

    @Test fun staysClosedWithoutAutomaticSource() {
        val readiness = RuntimeReadinessChecker.evaluate(profile(), syntheticToken(), false)
        assertTrue(readiness.profileReady)
        assertTrue(readiness.telegramReady)
        assertFalse(readiness.automaticAnalysisReady)
        assertFalse(readiness.fullyReady)
    }

    @Test fun rejectsMissingOrInvalidCredentials() {
        val missing = RuntimeReadinessChecker.evaluate(null, null, true)
        assertFalse(missing.profileReady)
        assertFalse(missing.telegramReady)
        assertFalse(missing.fullyReady)

        val invalidToken = RuntimeReadinessChecker.evaluate(profile(), "invalid", true)
        assertTrue(invalidToken.profileReady)
        assertFalse(invalidToken.telegramReady)
        assertFalse(invalidToken.fullyReady)
    }

    @Test fun reportsReadyOnlyWhenAllPrerequisitesExist() {
        val readiness = RuntimeReadinessChecker.evaluate(profile(), syntheticToken(), true)
        assertTrue(readiness.fullyReady)
        assertTrue(readiness.reasons.isEmpty())
    }
}
