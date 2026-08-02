package com.institutionaltrading.mobile

enum class AlertDeliveryOutcome {
    DELIVERED,
    DUPLICATE_OR_PENDING,
    DELIVERY_FAILED_RETRYABLE,
    STATE_FAILURE,
}

fun interface AlertTransport {
    fun send(text: String): Result<Unit>
}

class AlertDeliveryCoordinator(
    private val stateRepository: AlertStateRepository,
    private val transport: AlertTransport,
) {
    @Synchronized
    fun deliver(alert: SignalAlert): AlertDeliveryOutcome {
        val text = runCatching { AlertFormatter.telegram(alert) }.getOrElse { return AlertDeliveryOutcome.STATE_FAILURE }
        val initial = runCatching { stateRepository.load() }.getOrElse { return AlertDeliveryOutcome.STATE_FAILURE }
        if (!ClosedBarAlerts.shouldDeliver(alert, initial)) return AlertDeliveryOutcome.DUPLICATE_OR_PENDING

        val reserved = runCatching { ClosedBarAlerts.reserve(alert, initial) }.getOrElse { return AlertDeliveryOutcome.STATE_FAILURE }
        if (runCatching { stateRepository.save(reserved) }.isFailure) return AlertDeliveryOutcome.STATE_FAILURE

        val delivery = transport.send(text)
        if (delivery.isFailure) {
            val released = ClosedBarAlerts.release(alert, reserved)
            return if (runCatching { stateRepository.save(released) }.isSuccess) {
                AlertDeliveryOutcome.DELIVERY_FAILED_RETRYABLE
            } else {
                AlertDeliveryOutcome.STATE_FAILURE
            }
        }

        val delivered = runCatching { ClosedBarAlerts.markDelivered(alert, reserved) }
            .getOrElse { return AlertDeliveryOutcome.STATE_FAILURE }
        return if (runCatching { stateRepository.save(delivered) }.isSuccess) {
            AlertDeliveryOutcome.DELIVERED
        } else {
            // Reservation remains durable, so a retry cannot duplicate a message whose final state is uncertain.
            AlertDeliveryOutcome.STATE_FAILURE
        }
    }
}
