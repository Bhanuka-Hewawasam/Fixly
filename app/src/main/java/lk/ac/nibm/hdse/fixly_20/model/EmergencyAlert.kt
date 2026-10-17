package lk.ac.nibm.hdse.fixly_20.model

import com.google.firebase.firestore.GeoPoint
import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

/**
 * Firestore document: emergencyAlerts/{alertId}
 * A customer's emergency request (direct or broadcast).
 */
data class EmergencyAlert(
    val alertId: String = "",
    val customerId: String = "",
    val customerName: String = "",
    val customerPhone: String = "",
    val category: String = "Any",           // serviceType / category
    val note: String = "",
    val location: GeoPoint? = null,         // customerLocation
    val status: String = "ACTIVE",          // ACTIVE, ACCEPTED, CANCELLED, EXPIRED, CLOSED
    @ServerTimestamp val createdAt: Date? = null,
    val expiresAt: Date? = null,
    val selectedProviderId: String? = null, // target provider for direct request
    val acceptedProviderId: String? = null,
    val acceptedProviderName: String? = null,
    val acceptedProviderPhone: String? = null,
    @ServerTimestamp val acceptedAt: Date? = null
)

/**
 * Firestore document: emergencyAlerts/{alertId}/responses/{providerId}
 * A provider saying "I can help".
 */
data class EmergencyResponse(
    val providerId: String = "",
    val providerName: String = "",
    val providerPhone: String = "",
    val providerRating: Double = 0.0,
    val providerReviewCount: Int = 0,
    val distanceKm: Double? = null,
    @ServerTimestamp val createdAt: Date? = null
)

/**
 * Firestore document: providerNotifications/{notificationId}
 * In-app notification center entry for a provider.
 */
data class ProviderNotification(
    val notificationId: String = "",
    val providerId: String = "",
    val alertId: String = "",
    val type: String = "EMERGENCY_ALERT",
    val title: String = "",
    val body: String = "",
    val serviceType: String = "",
    val distanceKm: Double = 0.0,
    val isRead: Boolean = false,
    val status: String = "ACTIVE",          // ACTIVE, ACCEPTED, CLOSED, CANCELLED, DECLINED, EXPIRED
    val isCleared: Boolean = false,
    @ServerTimestamp val createdAt: Date? = null,
    val expiresAt: Date? = null,
    @ServerTimestamp val clearedAt: Date? = null
)
