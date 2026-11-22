package lk.ac.nibm.hdse.fixly_20.model

object EmergencyAlertPolicy {
    const val ACTIVE = "ACTIVE"
    const val ACCEPTED = "ACCEPTED"
    const val CANCELLED = "CANCELLED"
    const val EXPIRED = "EXPIRED"
    const val CLOSED = "CLOSED"

    fun isLiveForCustomer(status: String): Boolean = status == ACTIVE || status == ACCEPTED

    fun canCustomerCancel(status: String, expiresAtMs: Long, nowMs: Long): Boolean =
        status == ACTIVE && expiresAtMs > nowMs

    fun canCustomerClose(status: String): Boolean = status == ACCEPTED

    fun hasUnexpiredLiveAlert(status: String, expiresAtMs: Long, nowMs: Long): Boolean =
        isLiveForCustomer(status) && expiresAtMs > nowMs

    fun canProviderAccept(
        alertStatus: String,
        notificationStatus: String,
        selectedProviderId: String?,
        providerId: String,
        expiresAtMs: Long,
        nowMs: Long
    ): Boolean = alertStatus == ACTIVE &&
        notificationStatus == ACTIVE &&
        expiresAtMs > nowMs &&
        (selectedProviderId.isNullOrBlank() || selectedProviderId == providerId)

    fun shouldExpire(status: String, expiresAtMs: Long, nowMs: Long): Boolean =
        (status == ACTIVE || status == ACCEPTED) && expiresAtMs <= nowMs
}
