package lk.ac.nibm.hdse.fixly_20.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.GeoPoint
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import lk.ac.nibm.hdse.fixly_20.model.EmergencyAlert
import lk.ac.nibm.hdse.fixly_20.model.EmergencyResponse

class EmergencyAlertRepository(
    private val firebaseAuth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) {

    private val alerts = firestore.collection("emergencyAlerts")

    /**
     * Customer sends a one-tap alert. A customer can only have ONE live alert at a time;
     * old alerts that timed out without being closed are tidied up automatically.
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
                .whereEqualTo("status", "Active")
                .get().await()

            val now = System.currentTimeMillis()
            val hasLiveAlert = existing.documents.any { doc ->
                val createdAt = doc.getDate("createdAt")?.time ?: now
                now - createdAt < ALERT_LIFETIME_MS
            }
            if (hasLiveAlert) {
                return Result.failure(IllegalStateException("You already have a live emergency alert"))
            }

            existing.documents.forEach { it.reference.update("status", "Closed").await() }

            val ref = alerts.document()
            ref.set(
                EmergencyAlert(
                    alertId = ref.id,
                    customerId = customerId,
                    customerName = customerName,
                    customerPhone = customerPhone,
                    category = category,
                    note = note,
                    location = location,
                    status = "Active"
                )
            ).await()

            Result.success(ref.id)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** The logged-in customer's current live alert (null if none, or if it has timed out). */
    fun observeMyActiveAlert(): Flow<EmergencyAlert?> = callbackFlow {
        val customerId = firebaseAuth.currentUser?.uid
        if (customerId == null) {
            trySend(null)
            close()
            return@callbackFlow
        }

        val listener = alerts
            .whereEqualTo("customerId", customerId)
            .whereEqualTo("status", "Active")
            .addSnapshotListener { snapshot, error ->
                if (error != null) { close(error); return@addSnapshotListener }

                val now = System.currentTimeMillis()
                val live = snapshot?.toObjects(EmergencyAlert::class.java)
                    ?.firstOrNull { alert -> now - (alert.createdAt?.time ?: now) < ALERT_LIFETIME_MS }
                trySend(live)
            }
        awaitClose { listener.remove() }
    }

    /** Every alert that is currently Active — providers' devices filter these by distance and category. */
    fun observeActiveAlerts(): Flow<List<EmergencyAlert>> = callbackFlow {
        val listener = alerts
            .whereEqualTo("status", "Active")
            .addSnapshotListener { snapshot, error ->
                if (error != null) { close(error); return@addSnapshotListener }
                trySend(snapshot?.toObjects(EmergencyAlert::class.java) ?: emptyList())
            }
        awaitClose { listener.remove() }
    }

    fun observeAlert(alertId: String): Flow<EmergencyAlert?> = callbackFlow {
        val listener = alerts.document(alertId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) { close(error); return@addSnapshotListener }
                trySend(snapshot?.toObject(EmergencyAlert::class.java))
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

    /** Provider says "I can help". Document id = providerId, so repeating just overwrites. */
    suspend fun respond(alertId: String, response: EmergencyResponse): Result<Unit> {
        return try {
            alerts.document(alertId).collection("responses")
                .document(response.providerId)
                .set(response).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Customer closes the alert (got help, or no longer needed). */
    suspend fun closeAlert(alertId: String): Result<Unit> {
        return try {
            alerts.document(alertId).update("status", "Closed").await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    companion object {
        /** An alert stops counting as "live" after 30 minutes. */
        const val ALERT_LIFETIME_MS = 30L * 60 * 1000
    }
}
