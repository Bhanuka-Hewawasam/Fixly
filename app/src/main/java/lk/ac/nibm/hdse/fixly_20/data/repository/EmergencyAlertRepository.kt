package lk.ac.nibm.hdse.fixly_20.data.repository

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.GeoPoint
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import lk.ac.nibm.hdse.fixly_20.BuildConfig
import lk.ac.nibm.hdse.fixly_20.model.EmergencyAlert
import lk.ac.nibm.hdse.fixly_20.model.EmergencyResponse
import lk.ac.nibm.hdse.fixly_20.model.ProviderNotification
import java.util.Date

class EmergencyAlertRepository(
    private val firebaseAuth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) {

    private val alerts = firestore.collection("emergencyAlerts")

    /**
     * Customer sends a broadcast emergency alert.
     */
    suspend fun sendAlert(
        customerName: String,
        customerPhone: String,
        category: String,
        note: String,
        location: GeoPoint
    ): Result<String> {
        return try {
            val customerId = firebaseAuth.currentUser?.uid
                ?: return Result.failure(IllegalStateException("No logged-in user"))

            val existing = alerts
                .whereEqualTo("customerId", customerId)
                .whereEqualTo("status", "ACTIVE")
                .get().await()

            val now = System.currentTimeMillis()
            val hasLiveAlert = existing.documents.any { doc ->
                val expiresAt = doc.getDate("expiresAt")?.time ?: (doc.getDate("createdAt")?.time?.plus(ALERT_LIFETIME_MS) ?: now)
                now < expiresAt
            }
            if (hasLiveAlert) {
                return Result.failure(IllegalStateException("You already have a live emergency alert"))
            }

            existing.documents.forEach { it.reference.update("status", "CANCELLED").await() }

            val ref = alerts.document()
            val createdAtDate = Date()
            val expiresAtDate = Date(createdAtDate.time + ALERT_LIFETIME_MS)

            ref.set(
                EmergencyAlert(
                    alertId = ref.id,
                    customerId = customerId,
                    customerName = customerName,
                    customerPhone = customerPhone,
                    category = category,
                    note = note,
                    location = location,
                    status = "ACTIVE",
                    createdAt = createdAtDate,
                    expiresAt = expiresAtDate
                )
            ).await()

            if (BuildConfig.DEBUG) {
                Log.d(TAG, "DEBUG: request created - alertId: ${ref.id}")
            }

            Result.success(ref.id)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Customer sends a direct emergency request to a selected provider.
     */
    suspend fun sendDirectAlert(
        customerName: String,
        customerPhone: String,
        category: String,
        note: String,
        location: GeoPoint,
        selectedProviderId: String,
        distanceKm: Double
    ): Result<String> {
        return try {
            val customerId = firebaseAuth.currentUser?.uid
                ?: return Result.failure(IllegalStateException("No logged-in user"))

            if (BuildConfig.DEBUG) {
                Log.d(TAG, "DEBUG: sendDirectAlert - current customer UID: $customerId, selected provider UID: $selectedProviderId")
            }

            val ref = alerts.document()
            val alertPath = "emergencyAlerts/${ref.id}"
            val createdAtDate = Date()
            val expiresAtDate = Date(createdAtDate.time + ALERT_LIFETIME_MS)

            val alert = EmergencyAlert(
                alertId = ref.id,
                customerId = customerId,
                customerName = customerName,
                customerPhone = customerPhone,
                category = category,
                note = note,
                location = location,
                status = "ACTIVE",
                createdAt = createdAtDate,
                expiresAt = expiresAtDate,
                selectedProviderId = selectedProviderId
            )

            if (BuildConfig.DEBUG) {
                Log.d(TAG, "DEBUG: request created - alertId: ${ref.id}, document path written: $alertPath with fields: $alert")
            }

            ref.set(alert).await()

            // Create providerNotifications document targeted only to selectedProviderId
            val notifId = "${ref.id}_$selectedProviderId"
            val notifPath = "providerNotifications/$notifId"
            val notifRef = firestore.collection("providerNotifications").document(notifId)
            val notification = ProviderNotification(
                notificationId = notifId,
                providerId = selectedProviderId,
                alertId = ref.id,
                type = "EMERGENCY_ALERT",
                title = "🚨 Direct Request from $customerName",
                body = note.ifBlank { "Customer needs urgent help ($category)." },
                serviceType = category,
                distanceKm = distanceKm,
                isRead = false,
                status = "ACTIVE"
            )

            if (BuildConfig.DEBUG) {
                Log.d(TAG, "DEBUG: provider notification document path written: $notifPath")
            }

            notifRef.set(notification).await()

            Result.success(ref.id)
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) {
                Log.e(TAG, "DEBUG: sendDirectAlert failed", e)
            }
            Result.failure(e)
        }
    }

    /** The logged-in customer's current live alert (ACTIVE or ACCEPTED, not expired). */
    fun observeMyActiveAlert(): Flow<EmergencyAlert?> = callbackFlow {
        val customerId = firebaseAuth.currentUser?.uid
        if (customerId == null) {
            trySend(null)
            close()
            return@callbackFlow
        }

        if (BuildConfig.DEBUG) {
            Log.d(TAG, "DEBUG: waiting observer started for customerId: $customerId")
        }

        val listener = alerts
            .whereEqualTo("customerId", customerId)
            .whereIn("status", listOf("ACTIVE", "ACCEPTED"))
            .addSnapshotListener { snapshot, error ->
                if (error != null) { close(error); return@addSnapshotListener }

                val now = System.currentTimeMillis()
                val live = snapshot?.toObjects(EmergencyAlert::class.java)
                    ?.firstOrNull { alert ->
                        val expiresAt = alert.expiresAt?.time ?: ((alert.createdAt?.time ?: now) + ALERT_LIFETIME_MS)
                        alert.status == "ACCEPTED" || now < expiresAt
                    }
                trySend(live)
            }
        awaitClose { listener.remove() }
    }

    fun observeAlert(alertId: String): Flow<EmergencyAlert?> = callbackFlow {
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "DEBUG: waiting observer started for alertId: $alertId")
        }
        var lastStatus: String? = null
        val listener = alerts.document(alertId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) { close(error); return@addSnapshotListener }
                val alert = snapshot?.toObject(EmergencyAlert::class.java)
                if (alert != null) {
                    if (BuildConfig.DEBUG && lastStatus != alert.status) {
                        Log.d(TAG, "DEBUG: each status change - alertId: $alertId status changed from $lastStatus to ${alert.status}")
                        if (alert.status == "ACCEPTED") {
                            Log.d(TAG, "DEBUG: provider acceptance received - providerName: ${alert.acceptedProviderName}")
                        }
                        lastStatus = alert.status
                    }
                    val now = System.currentTimeMillis()
                    val expiresAt = alert.expiresAt?.time ?: ((alert.createdAt?.time ?: now) + ALERT_LIFETIME_MS)
                    if (alert.status == "ACTIVE" && now >= expiresAt) {
                        if (BuildConfig.DEBUG) {
                            Log.d(TAG, "DEBUG: expiry result - request $alertId expired")
                        }
                    }
                }
                trySend(alert)
            }
        awaitClose { listener.remove() }
    }

    fun observeResponses(alertId: String): Flow<List<EmergencyResponse>> = callbackFlow {
        val listener = alerts.document(alertId).collection("responses")
            .addSnapshotListener { snapshot, error ->
                if (error != null) { close(error); return@addSnapshotListener }
                trySend(snapshot?.toObjects(EmergencyResponse::class.java) ?: emptyList())
            }
        awaitClose { listener.remove() }
    }

    /**
     * ATOMIC FIRST ACCEPTANCE:
     * Uses a Firestore transaction to ensure only the selected provider accepts this request.
     */
    suspend fun acceptAlert(
        alertId: String,
        providerId: String,
        providerName: String,
        providerPhone: String
    ): Result<Unit> {
        return try {
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "DEBUG: acceptAlert starting for alertId: $alertId by providerId: $providerId")
            }

            firestore.runTransaction { transaction ->
                val ref = alerts.document(alertId)
                val snapshot = transaction.get(ref)
                val status = snapshot.getString("status") ?: "ACTIVE"
                val selectedProviderId = snapshot.getString("selectedProviderId")

                if (status != "ACTIVE") {
                    throw IllegalStateException("This request has already been accepted or closed.")
                }
                if (!selectedProviderId.isNullOrBlank() && selectedProviderId != providerId) {
                    throw IllegalStateException("This request was sent to a specific provider.")
                }

                transaction.update(
                    ref,
                    mapOf(
                        "status" to "ACCEPTED",
                        "acceptedProviderId" to providerId,
                        "acceptedProviderName" to providerName,
                        "acceptedProviderPhone" to providerPhone,
                        "acceptedAt" to FieldValue.serverTimestamp()
                    )
                )
            }.await()

            // Also update notification status to ACCEPTED
            val notifId = "${alertId}_$providerId"
            firestore.collection("providerNotifications").document(notifId)
                .update(mapOf("status" to "ACCEPTED", "isRead" to true)).await()

            if (BuildConfig.DEBUG) {
                Log.d(TAG, "DEBUG: Accept/Decline transaction result - acceptAlert SUCCESS for alertId: $alertId")
            }
            Result.success(Unit)
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) {
                Log.e(TAG, "DEBUG: Accept/Decline transaction result - acceptAlert FAILED", e)
            }
            Result.failure(e)
        }
    }

    /**
     * Provider declines the request.
     */
    suspend fun declineAlert(alertId: String, providerId: String, notificationId: String): Result<Unit> {
        return try {
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "DEBUG: declineAlert starting for alertId: $alertId by providerId: $providerId")
            }

            alerts.document(alertId).update("status", "DECLINED").await()
            firestore.collection("providerNotifications").document(notificationId)
                .update(mapOf("status" to "DECLINED", "isRead" to true)).await()

            if (BuildConfig.DEBUG) {
                Log.d(TAG, "DEBUG: Accept/Decline transaction result - declineAlert SUCCESS for alertId: $alertId")
            }
            Result.success(Unit)
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) {
                Log.e(TAG, "DEBUG: Accept/Decline transaction result - declineAlert FAILED", e)
            }
            Result.failure(e)
        }
    }

    /** Customer closes or cancels the alert. */
    suspend fun closeAlert(alertId: String): Result<Unit> {
        return try {
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "DEBUG: cancel result - closeAlert starting for alertId: $alertId")
            }
            val docSnap = alerts.document(alertId).get().await()
            val selectedProviderId = docSnap.getString("selectedProviderId")

            alerts.document(alertId).update("status", "CANCELLED").await()
            if (!selectedProviderId.isNullOrBlank()) {
                val notifId = "${alertId}_$selectedProviderId"
                firestore.collection("providerNotifications").document(notifId)
                    .update(mapOf("status" to "CANCELLED", "isRead" to true)).await()
            }

            if (BuildConfig.DEBUG) {
                Log.d(TAG, "DEBUG: cancel result - closeAlert SUCCESS for alertId: $alertId")
            }
            Result.success(Unit)
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) {
                Log.e(TAG, "DEBUG: cancel result - closeAlert FAILED", e)
            }
            Result.failure(e)
        }
    }

    // --- In-App Provider Notifications Center ---

    fun observeProviderNotifications(providerId: String): Flow<List<ProviderNotification>> = callbackFlow {
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "DEBUG: Provider listener query - observing providerNotifications where providerId == $providerId (current provider UID)")
        }
        val listener = firestore.collection("providerNotifications")
            .whereEqualTo("providerId", providerId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    if (BuildConfig.DEBUG) {
                        Log.e(TAG, "DEBUG: observeProviderNotifications error", error)
                    }
                    close(error)
                    return@addSnapshotListener
                }
                val list = snapshot?.toObjects(ProviderNotification::class.java) ?: emptyList()
                if (BuildConfig.DEBUG) {
                    Log.d(TAG, "DEBUG: number of notifications received: ${list.size} for providerId: $providerId")
                }
                trySend(list)
            }
        awaitClose { listener.remove() }
    }

    suspend fun markNotificationRead(notificationId: String): Result<Unit> {
        return try {
            firestore.collection("providerNotifications").document(notificationId)
                .update("isRead", true).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateNotificationStatus(notificationId: String, newStatus: String): Result<Unit> {
        return try {
            firestore.collection("providerNotifications").document(notificationId)
                .update(mapOf("status" to newStatus, "isRead" to true)).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** DEV-ONLY: Seed demo providers (1 through 6) */
    suspend fun seedDemoProviders(): Result<Unit> {
        return try {
            val batch = firestore.batch()

            val p1 = mapOf(
                "uid" to "demo_sos_provider_1",
                "name" to "Colombo Auto Care",
                "email" to "demo_sos_provider_1@example.com",
                "phoneNumber" to "0771234567",
                "roles" to listOf("provider"),
                "serviceCategories" to listOf("Electrical", "Other"),
                "ratingSum" to 45.0,
                "ratingCount" to 10,
                "emergencyAvailable" to true,
                "lastLocation" to GeoPoint(6.9271, 79.8612),
                "lastLocationAt" to FieldValue.serverTimestamp(),
                "isVerified" to true
            )
            val p2 = mapOf(
                "uid" to "demo_sos_provider_2",
                "name" to "Dehiwala Towing Service",
                "email" to "demo_sos_provider_2@example.com",
                "phoneNumber" to "0772345678",
                "roles" to listOf("provider"),
                "serviceCategories" to listOf("Other", "Electrical"),
                "ratingSum" to 40.0,
                "ratingCount" to 8,
                "emergencyAvailable" to true,
                "lastLocation" to GeoPoint(6.8500, 79.8650),
                "lastLocationAt" to FieldValue.serverTimestamp(),
                "isVerified" to true
            )
            val p3 = mapOf(
                "uid" to "demo_sos_provider_3",
                "name" to "Rajagiriya Emergency Repair",
                "email" to "demo_sos_provider_3@example.com",
                "phoneNumber" to "0773456789",
                "roles" to listOf("provider"),
                "serviceCategories" to listOf("Electrical", "Other"),
                "ratingSum" to 47.5,
                "ratingCount" to 10,
                "emergencyAvailable" to true,
                "lastLocation" to GeoPoint(6.9065, 79.8950),
                "lastLocationAt" to FieldValue.serverTimestamp(),
                "isVerified" to true
            )
            val p4 = mapOf(
                "uid" to "demo_sos_provider_4",
                "name" to "Dambadeniya Roadside Care",
                "email" to "demo_sos_provider_4@example.com",
                "phoneNumber" to "0774567890",
                "roles" to listOf("provider"),
                "serviceCategories" to listOf("Electrical", "Other"),
                "ratingSum" to 42.0,
                "ratingCount" to 9,
                "emergencyAvailable" to true,
                "lastLocation" to GeoPoint(7.3675, 80.1615),
                "lastLocationAt" to FieldValue.serverTimestamp(),
                "isVerified" to true
            )
            val p5 = mapOf(
                "uid" to "demo_sos_provider_5",
                "name" to "Kurunegala Auto Rescue",
                "email" to "demo_sos_provider_5@example.com",
                "phoneNumber" to "0775678901",
                "roles" to listOf("provider"),
                "serviceCategories" to listOf("Electrical", "Other"),
                "ratingSum" to 46.0,
                "ratingCount" to 10,
                "emergencyAvailable" to true,
                "lastLocation" to GeoPoint(7.4863, 80.3623),
                "lastLocationAt" to FieldValue.serverTimestamp(),
                "isVerified" to true
            )
            val p6 = mapOf(
                "uid" to "demo_sos_provider_6",
                "name" to "Kuliyapitiya Vehicle Help",
                "email" to "demo_sos_provider_6@example.com",
                "phoneNumber" to "0776789012",
                "roles" to listOf("provider"),
                "serviceCategories" to listOf("Electrical", "Other"),
                "ratingSum" to 43.5,
                "ratingCount" to 9,
                "emergencyAvailable" to true,
                "lastLocation" to GeoPoint(7.4688, 80.0401),
                "lastLocationAt" to FieldValue.serverTimestamp(),
                "isVerified" to true
            )

            batch.set(firestore.collection("users").document("demo_sos_provider_1"), p1)
            batch.set(firestore.collection("users").document("demo_sos_provider_2"), p2)
            batch.set(firestore.collection("users").document("demo_sos_provider_3"), p3)
            batch.set(firestore.collection("users").document("demo_sos_provider_4"), p4)
            batch.set(firestore.collection("users").document("demo_sos_provider_5"), p5)
            batch.set(firestore.collection("users").document("demo_sos_provider_6"), p6)

            batch.commit().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** DEV-ONLY: Delete demo providers */
    suspend fun deleteDemoProviders(): Result<Unit> {
        return try {
            val snapshot = firestore.collection("users")
                .whereGreaterThanOrEqualTo(FieldPath.documentId(), "demo_sos_provider_")
                .whereLessThanOrEqualTo(FieldPath.documentId(), "demo_sos_provider_\uf8ff")
                .get().await()

            val batch = firestore.batch()
            for (doc in snapshot.documents) {
                if (doc.id.startsWith("demo_sos_provider_")) {
                    batch.delete(doc.reference)
                }
            }
            batch.commit().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    companion object {
        private const val TAG = "EmergencyAlertRepository"
        const val ALERT_LIFETIME_MS = 30L * 60 * 1000
    }
}
