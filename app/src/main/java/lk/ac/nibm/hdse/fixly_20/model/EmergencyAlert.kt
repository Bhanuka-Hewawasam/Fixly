package lk.ac.nibm.hdse.fixly_20.model

import com.google.firebase.firestore.GeoPoint
import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

/**
 * Firestore document: emergencyAlerts/{alertId}
 * A customer's one-tap "I need help right now" request.
 */
data class EmergencyAlert(
    val alertId: String = "",
    val customerId: String = "",
    val customerName: String = "",
    val customerPhone: String = "",
    val category: String = "Any",       // "Any" = every nearby provider is alerted
    val note: String = "",              // optional: "Flat tyre on the Dambulla road"
    val location: GeoPoint? = null,     // where the emergency is
    val status: String = "Active",      // Active -> Closed
    @ServerTimestamp val createdAt: Date? = null
)

/**
 * Firestore document: emergencyAlerts/{alertId}/responses/{providerId}
 * A provider saying "I can help, I'm on my way". The document id is the providerId,
 * so one provider can only respond once per alert.
 */
data class EmergencyResponse(
    val providerId: String = "",
    val providerName: String = "",
    val providerPhone: String = "",
    val providerRating: Double = 0.0,
    val providerReviewCount: Int = 0,
    val distanceKm: Double? = null,     // how far the provider was when they responded
    @ServerTimestamp val createdAt: Date? = null
)
