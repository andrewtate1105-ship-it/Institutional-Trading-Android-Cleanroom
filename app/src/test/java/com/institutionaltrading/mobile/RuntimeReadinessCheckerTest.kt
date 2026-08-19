package com.institutionaltrading.mobile

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RuntimeReadinessCheckerTest {
    private fun profile() = OperatorProfile(
        accountEquity = 100000.0,
        riskPercent = 1.0,
        markets = setOf("NSE_EQUITY"),
        timeframes = setOf("15M"),
        watchlist = setOf("RELIANCE"),
    )

    @Test fun staysClosedWithoutAutomaticSource() {
        val readiness = RuntimeReadinessChecker.evaluate(profile(), false)
        assertTrue(readiness.profileReady)
        assertFalse(readiness.automaticAnalysisReady)
        assertFalse(readiness.fullyReady)
    }

    @Test fun rejectsMissingProfile() {
        val missing = RuntimeReadinessChecker.evaluate(null, true)
        assertFalse(missing.profileReady)
        assertFalse(missing.fullyReady)
    }

    @Test fun reportsReadyOnlyWhenProfileAndLawfulSourceExist() {
        val readiness = RuntimeReadinessChecker.evaluate(profile(), true)
        assertTrue(readiness.fullyReady)
        assertTrue(readiness.reasons.isEmpty())
    }
}
