package com.institutionaltrading.mobile

class ValidatedAlertDispatcher(
    private val deliveryCoordinator: AlertDeliveryCoordinator,
) {
    fun deliver(alert: SignalAlert): AlertDeliveryOutcome {
        require(alert.direction == SignalDirection.NO_TRADE) {
            "Only fail-closed NO_TRADE alerts may be dispatched until strategy validation is complete"
        }
        return deliveryCoordinator.deliver(alert)
    }
}
