package lk.ac.nibm.hdse.fixly_20.data.repository

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.GeoPoint
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import lk.ac.nibm.hdse.fixly_20.BuildConfig
import lk.ac.nibm.hdse.fixly_20.model.EmergencyAlert
import lk.ac.nibm.hdse.fixly_20.model.EmergencyAlertPolicy
import lk.ac.nibm.hdse.fixly_20.model.EmergencyResponse
import lk.ac.nibm.hdse.fixly_20.model.ProviderNotification
import lk.ac.nibm.hdse.fixly_20.util.DistanceUtils
import java.util.Date
import java.util.Locale

class EmergencyAlertRepository(
    private val firebaseAuth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) {

    private val alerts = firestore.collection("emergencyAlerts")

    /** Creates one live alert per customer, atomically, so repeated taps cannot create duplicates. */
    suspend fun sendAlert(
        customerName: String,
        customerPhone: String,
        category: String,
        note: String,
        location: GeoPoint
    ): Result<String> {
        return createAlert(customerName, customerPhone, category, note, location, null)
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
        if (selectedProviderId.isBlank() || distanceKm < 0) {
            return Result.failure(IllegalArgumentException("Select a valid nearby provider"))
        }
        return createAlert(customerName, customerPhone, category, note, location, selectedProviderId)
    }

    private suspend fun createAlert(
        customerName: String,
        customerPhone: String,
        category: String,
        note: String,
        location: GeoPoint,
        selectedProviderId: String?
    ): Result<String> = try {
        val customerId = firebaseAuth.currentUser?.uid
            ?: return Result.failure(IllegalStateException("Please sign in to send an emergency request"))
        val alertRef = alerts.document()
        val stateRef = firestore.collection("emergencyCustomerState").document(customerId)
        val expiresAt = Date(System.currentTimeMillis() + ALERT_LIFETIME_MS)

        firestore.runTransaction { transaction ->
            val stateSnapshot = transaction.get(stateRef)
            val activeAlertId = stateSnapshot.getString("activeAlertId")
            if (!activeAlertId.isNullOrBlank()) {
                val existingRef = alerts.document(activeAlertId)
                val existing = transaction.get(existingRef)
                val status = existing.getString("status")
                val existingExpiry = existing.getDate("expiresAt")?.time ?: 0L
                val now = System.currentTimeMillis()
                if (EmergencyAlertPolicy.hasUnexpiredLiveAlert(status.orEmpty(), existingExpiry, now)) {
                    throw IllegalStateException("You already have an active emergency request")
                }
                if (EmergencyAlertPolicy.shouldExpire(status.orEmpty(), existingExpiry, now)) {
                    transaction.update(existingRef, "status", EmergencyAlertPolicy.EXPIRED)
                }
            }

            val alertData = hashMapOf<String, Any?>(
                "alertId" to alertRef.id,
                "customerId" to customerId,
                "customerName" to customerName,
                "customerPhone" to customerPhone,
                "category" to category,
                "note" to note,
                "location" to location,
                "status" to "ACTIVE",
                "createdAt" to FieldValue.serverTimestamp(),
                "expiresAt" to expiresAt,
                "selectedProviderId" to selectedProviderId,
                "acceptedProviderId" to null,
                "acceptedProviderName" to null,
                "acceptedProviderPhone" to null,
                "acceptedAt" to null
            )
            transaction.set(alertRef, alertData)
            transaction.set(
                stateRef,
                mapOf("activeAlertId" to alertRef.id, "updatedAt" to FieldValue.serverTimestamp())
            )
            alertRef.id
        }.await()
        Result.success(alertRef.id)
    } catch (e: Exception) {
        if (BuildConfig.DEBUG) Log.e(TAG, "Failed to create emergency alert", e)
        Result.failure(e)
    }

    /** The latest alert stays visible after terminal states so the customer can see its outcome. */
    fun observeMyActiveAlert(): Flow<EmergencyAlert?> = callbackFlow {
        val customerId = firebaseAuth.currentUser?.uid
        if (customerId == null) {
            trySend(null)
            close()
            return@callbackFlow
        }

        val listener = alerts
            .whereEqualTo("customerId", customerId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) { close(error); return@addSnapshotListener }

                val latest = snapshot?.documents
                    ?.mapNotNull { it.toObject(EmergencyAlert::class.java) }
                    ?.maxByOrNull { it.createdAt?.time ?: 0L }
                trySend(latest)
            }
        awaitClose { listener.remove() }
    }

    fun observeAlert(alertId: String): Flow<EmergencyAlert?> = callbackFlow {
        val listener = alerts.document(alertId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) { close(error); return@addSnapshotListener }
                val alert = snapshot?.toObject(EmergencyAlert::class.java)
                trySend(alert)
            }
        awaitClose { listener.remove() }
    }

    suspend fun getAlert(alertId: String): Result<EmergencyAlert> = try {
        val alert = alerts.document(alertId).get().await().toObject(EmergencyAlert::class.java)
            ?: throw IllegalStateException("This emergency request is no longer available")
        Result.success(alert)
    } catch (e: Exception) {
        Result.failure(e)
    }

    suspend fun getEmergencyProviderPhone(alertId: String, providerId: String): Result<String> = try {
        val customerId = firebaseAuth.currentUser?.uid
            ?: throw IllegalStateException("Please sign in to view provider contact details")
        val alert = alerts.document(alertId).get().await()
        val status = alert.getString("status")
        val expiresAt = alert.getDate("expiresAt")?.time ?: 0L
        if (alert.getString("customerId") != customerId ||
            alert.getString("selectedProviderId") != providerId ||
            status !in listOf("ACTIVE", "ACCEPTED") ||
            (status == "ACTIVE" && expiresAt <= System.currentTimeMillis())
        ) {
            throw IllegalStateException("Provider contact is available only for your current emergency request")
        }
        val phoneNumber = firestore.collection("emergencyProviderContacts")
            .document(providerId)
            .get()
            .await()
            .getString("phoneNumber")
            ?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("This provider hasn't shared a contact number yet")
        Result.success(phoneNumber)
    } catch (e: Exception) {
        Result.failure(e)
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
        providerId: String
    ): Result<Unit> {
        return try {
            val authUid = firebaseAuth.currentUser?.uid
                ?: return Result.failure(IllegalStateException("Please sign in to accept this request"))
            if (authUid != providerId) {
                return Result.failure(IllegalStateException("You can only accept requests for your account"))
            }
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "DEBUG: 1. Current Firebase Auth UID: $authUid")
                Log.d(TAG, "DEBUG: 6. Provider ID passed to acceptAlert(): $providerId")
                Log.d(TAG, "DEBUG: 3. Notification alertId/requestId: $alertId")
                Log.d(TAG, "DEBUG: 4. Firestore request document path: emergencyAlerts/$alertId")
            }

            firestore.runTransaction { transaction ->
                val ref = alerts.document(alertId)
                val notificationRef = firestore.collection("providerNotifications")
                    .document("${alertId}_$providerId")
                val providerRef = firestore.collection("users").document(providerId)
                val snapshot = transaction.get(ref)
                val notification = transaction.get(notificationRef)
                val provider = transaction.get(providerRef)
                val status = snapshot.getString("status") ?: "ACTIVE"
                val selectedProviderId = snapshot.getString("selectedProviderId")
                val expiresAtMs = snapshot.getDate("expiresAt")?.time ?: 0L
                val nowMs = System.currentTimeMillis()

                if (!EmergencyAlertPolicy.canProviderAccept(
                        status,
                        notification.getString("status").orEmpty(),
                        selectedProviderId,
                        providerId,
                        expiresAtMs,
                        nowMs
                    )
                ) {
                    throw IllegalStateException("This request has already been accepted or closed.")
                }
                if (!notification.exists() ||
                    notification.getString("providerId") != providerId ||
                    notification.getString("status") != "ACTIVE"
                ) {
                    throw IllegalStateException("This emergency request is no longer available to you")
                }
                val providerLocation = provider.getGeoPoint("lastLocation")
                    ?: throw IllegalStateException("Your emergency location is unavailable")
                val lastLocationAt = provider.getTimestamp("lastLocationAt")?.toDate()
                    ?: provider.getDate("lastLocationAt")
                    ?: throw IllegalStateException("Update your emergency availability location first")
                val locationAgeMs = System.currentTimeMillis() - lastLocationAt.time
                val providerCategories = provider.get("serviceCategories") as? List<*>
                    ?: emptyList<Any>()
                val providerRoles = provider.get("roles") as? List<*> ?: emptyList<Any>()
                val customerLocation = snapshot.getGeoPoint("location")
                    ?: throw IllegalStateException("The customer's emergency location is unavailable")
                val category = snapshot.getString("category") ?: "Any"
                val categoryAliases = when (category) {
                    "Vehicle Mechanic" -> listOf("Vehicle Mechanic", "Mechanic")
                    "Electrician" -> listOf("Electrician", "Electrical")
                    "Plumber" -> listOf("Plumber", "Plumbing")
                    "AC Technician" -> listOf("AC Technician", "AC Repair")
                    "Computer Technician" -> listOf("Computer Technician", "Computer Repair")
                    "Any Available Provider" -> listOf("Any")
                    else -> listOf(category)
                }
                val distance = DistanceUtils.distanceKm(
                    customerLocation.latitude,
                    customerLocation.longitude,
                    providerLocation.latitude,
                    providerLocation.longitude
                )
                if (!providerRoles.contains("provider") ||
                    provider.getBoolean("emergencyAvailable") != true ||
                    locationAgeMs !in 0..MAX_LOCATION_AGE_MS ||
                    providerLocation.latitude !in -90.0..90.0 ||
                    providerLocation.longitude !in -180.0..180.0 ||
                    distance > MAX_PROVIDER_DISTANCE_KM
                ) {
                    throw IllegalStateException("Turn on emergency availability and refresh your location before accepting")
                }
                if (category != "Any" &&
                    categoryAliases.none { it in providerCategories }
                ) {
                    throw IllegalStateException("This request does not match your service categories")
                }

                val verifiedName = provider.getString("name")?.takeIf { it.isNotBlank() }
                    ?: throw IllegalStateException("Complete your provider profile before accepting")
                val verifiedPhone = provider.getString("phoneNumber")
                    ?: throw IllegalStateException("Add a phone number to your provider profile before accepting")
                transaction.update(
                    ref,
                    mapOf(
                        "status" to "ACCEPTED",
                        "acceptedProviderId" to providerId,
                        "acceptedProviderName" to verifiedName,
                        "acceptedProviderPhone" to verifiedPhone,
                        "acceptedAt" to FieldValue.serverTimestamp()
                    )
                )
            }.await()

            if (BuildConfig.DEBUG) {
                Log.d(TAG, "DEBUG: 7. Transaction success: true for alertId: $alertId")
                Log.d(TAG, "DEBUG: 8. Firestore status after acceptance: ACCEPTED")
            }

            Result.success(Unit)
        } catch (e: Exception) {
            val errorMsg = e.localizedMessage ?: "Unknown error"
            if (BuildConfig.DEBUG) {
                Log.e(TAG, "DEBUG: 7. Transaction success: false", e)
                Log.e(TAG, "DEBUG: 9. Error message if transaction fails: $errorMsg")
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

            val notificationRef = firestore.collection("providerNotifications").document(notificationId)
            val notification = notificationRef.get().await()
            if (notification.getString("providerId") != providerId ||
                notification.getString("alertId") != alertId
            ) {
                return Result.failure(IllegalStateException("This request is not assigned to you"))
            }
            notificationRef
                .update(mapOf("status" to "DECLINED", "isRead" to true)).await()

            if (BuildConfig.DEBUG) {
                Log.d(TAG, "DEBUG: declineAlert SUCCESS for alertId: $alertId")
            }
            Result.success(Unit)
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) {
                Log.e(TAG, "DEBUG: declineAlert FAILED", e)
            }
            Result.failure(e)
        }
    }

    /** Customer closes or cancels the alert. */
    suspend fun closeAlert(alertId: String): Result<Unit> {
        return try {
            val customerId = firebaseAuth.currentUser?.uid
                ?: return Result.failure(IllegalStateException("Please sign in to cancel this request"))
            firestore.runTransaction { transaction ->
                val ref = alerts.document(alertId)
                val snapshot = transaction.get(ref)
                if (snapshot.getString("customerId") != customerId) {
                    throw IllegalStateException("You can only cancel your own request")
                }
                if (!EmergencyAlertPolicy.canCustomerCancel(
                        snapshot.getString("status").orEmpty(),
                        snapshot.getDate("expiresAt")?.time ?: 0L,
                        System.currentTimeMillis()
                    )
                ) {
                    throw IllegalStateException("Only an active request can be cancelled")
                }
                transaction.update(ref, "status", "CANCELLED")
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun finishAcceptedAlert(alertId: String): Result<Unit> {
        return try {
            val customerId = firebaseAuth.currentUser?.uid
                ?: return Result.failure(IllegalStateException("Please sign in to close this request"))
            firestore.runTransaction { transaction ->
                val ref = alerts.document(alertId)
                val snapshot = transaction.get(ref)
                if (snapshot.getString("customerId") != customerId ||
                    !EmergencyAlertPolicy.canCustomerClose(snapshot.getString("status").orEmpty())
                ) {
                    throw IllegalStateException("Only your accepted request can be closed")
                }
                transaction.update(ref, "status", "CLOSED")
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // --- In-App Provider Notifications Center ---

    fun observeProviderNotifications(providerId: String): Flow<List<ProviderNotification>> = callbackFlow {
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "DEBUG: 6. Provider listener query - observing providerNotifications where providerId == $providerId (current provider UID)")
        }
        val listener = firestore.collection("providerNotifications")
            .whereEqualTo("providerId", providerId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                val list = snapshot?.toObjects(ProviderNotification::class.java)
                    ?.filter { notif ->
                        val status = notif.status.uppercase(Locale.getDefault())
                        val isHidden = notif.isCleared || status == "CLOSED" || status == "CANCELLED" || status == "ACCEPTED" || status == "DECLINED" || status == "EXPIRED"
                        !isHidden
                    } ?: emptyList()
                if (BuildConfig.DEBUG) {
                    Log.d(TAG, "DEBUG: 7. Number of notifications received: ${list.size} for providerId: $providerId")
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

    suspend fun clearClearedNotifications(providerId: String): Result<Unit> {
        return try {
            val snapshot = firestore.collection("providerNotifications")
                .whereEqualTo("providerId", providerId)
                .get().await()

            val batch = firestore.batch()
            for (doc in snapshot.documents) {
                val notif = doc.toObject(ProviderNotification::class.java)
                if (notif != null) {
                    val status = notif.status.uppercase(Locale.getDefault())
                    val isEligibleToClear = notif.isRead || status == "ACCEPTED" || status == "CLOSED" || status == "CANCELLED" || status == "DECLINED" || status == "EXPIRED"
                    val isActiveUnread = !notif.isRead && (status == "ACTIVE" || status == "PENDING")

                    if (isEligibleToClear && !isActiveUnread) {
                        batch.update(doc.reference, mapOf("isCleared" to true, "clearedAt" to FieldValue.serverTimestamp()))
                    }
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
        const val MAX_LOCATION_AGE_MS = 15L * 60 * 1000
        const val MAX_PROVIDER_DISTANCE_KM = 50.0
    }
}
