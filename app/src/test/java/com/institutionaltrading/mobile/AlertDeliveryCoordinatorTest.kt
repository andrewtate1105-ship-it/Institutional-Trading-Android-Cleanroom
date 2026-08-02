package com.institutionaltrading.mobile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AlertDeliveryCoordinatorTest {
    private fun alert() = SignalAlert(
        direction = SignalDirection.NO_TRADE,
        symbol = "RELIANCE",
        timeframe = "15M",
        sourceTimestamp = "2026-08-01T09:45:00Z",
        entryZone = "N/A",
        invalidation = "No validated setup",
        stopLoss = "N/A",
        targets = emptyList(),
        rewardToRisk = "N/A",
        riskGuidance = "No position.",
        confidenceDiagnostics = "Insufficient evidence.",
        provenance = "official-public-source:test-fixture",
        warnings = listOf("Test fixture only"),
    )

    private class MemoryRepository(
        var state: AlertDeliveryState = AlertDeliveryState(),
        var failOnSaveNumber: Int? = null,
    ) : AlertStateRepository {
        var saves = 0
        override fun load(): AlertDeliveryState = state
        override fun save(state: AlertDeliveryState) {
            saves += 1
            if (failOnSaveNumber == saves) error("save failed")
            this.state = state
        }
    }

    @Test fun successfulDeliveryIsRecordedAndCannotRepeat() {
        val repository = MemoryRepository()
        var sends = 0
        val coordinator = AlertDeliveryCoordinator(repository) { sends += 1; Result.success(Unit) }
        assertEquals(AlertDeliveryOutcome.DELIVERED, coordinator.deliver(alert()))
        assertEquals(AlertDeliveryOutcome.DUPLICATE_OR_PENDING, coordinator.deliver(alert()))
        assertEquals(1, sends)
        assertTrue(repository.state.deliveredKeys.isNotEmpty())
        assertTrue(repository.state.pendingKeys.isEmpty())
    }

    @Test fun transportFailureReleasesReservationForRetry() {
        val repository = MemoryRepository()
        val coordinator = AlertDeliveryCoordinator(repository) { Result.failure(IllegalStateException("offline")) }
        assertEquals(AlertDeliveryOutcome.DELIVERY_FAILED_RETRYABLE, coordinator.deliver(alert()))
        assertTrue(repository.state.pendingKeys.isEmpty())
        assertTrue(repository.state.deliveredKeys.isEmpty())
        assertTrue(ClosedBarAlerts.shouldDeliver(alert(), repository.state))
    }

    @Test fun reservationSaveFailurePreventsNetworkSend() {
        val repository = MemoryRepository(failOnSaveNumber = 1)
        var sent = false
        val coordinator = AlertDeliveryCoordinator(repository) { sent = true; Result.success(Unit) }
        assertEquals(AlertDeliveryOutcome.STATE_FAILURE, coordinator.deliver(alert()))
        assertFalse(sent)
    }

    @Test fun completionSaveFailureLeavesDurablePendingReservation() {
        val repository = MemoryRepository(failOnSaveNumber = 2)
        val coordinator = AlertDeliveryCoordinator(repository) { Result.success(Unit) }
        assertEquals(AlertDeliveryOutcome.STATE_FAILURE, coordinator.deliver(alert()))
        assertTrue(repository.state.pendingKeys.isNotEmpty())
        assertEquals(AlertDeliveryOutcome.DUPLICATE_OR_PENDING, coordinator.deliver(alert()))
    }
}
