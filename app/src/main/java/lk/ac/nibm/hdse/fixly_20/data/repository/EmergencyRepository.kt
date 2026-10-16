package lk.ac.nibm.hdse.fixly_20.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.GeoPoint
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import lk.ac.nibm.hdse.fixly_20.model.User

class EmergencyRepository(
    private val firebaseAuth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) {

    /**
     * Provider opts in/out of emergency searches. Private location data remains in their user profile;
     * a trusted Cloud Function mirrors only the approved emergency-search fields.
     */
    suspend fun setAvailability(available: Boolean, location: GeoPoint?): Result<Unit> {
        return try {
            val uid = firebaseAuth.currentUser?.uid
                ?: return Result.failure(IllegalStateException("No logged-in user"))

            val updates = mutableMapOf<String, Any>("emergencyAvailable" to available)
            if (available && location != null) {
                updates["lastLocation"] = location
                updates["lastLocationAt"] = FieldValue.serverTimestamp()
            } else {
                updates["lastLocation"] = FieldValue.delete()
                updates["lastLocationAt"] = FieldValue.delete()
            }

            firestore.collection("users").document(uid).update(updates).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Real-time stream of the restricted emergency-provider projection, never full user documents.
     */
    fun observeAvailableProviders(): Flow<List<User>> = callbackFlow {
        val listener = firestore.collection("emergencyProviderProfiles")
            .whereEqualTo("emergencyAvailable", true)
            .addSnapshotListener { snapshot, error ->
                if (error != null) { close(error); return@addSnapshotListener }
                val providers = snapshot?.documents.orEmpty().mapNotNull { document ->
                    val providerId = document.getString("providerId") ?: return@mapNotNull null
                    val latitude = document.getDouble("latitude") ?: return@mapNotNull null
                    val longitude = document.getDouble("longitude") ?: return@mapNotNull null
                    if (latitude !in -90.0..90.0 || longitude !in -180.0..180.0) {
                        return@mapNotNull null
                    }
                    val locationAt = document.getTimestamp("lastLocationAt")?.toDate()
                        ?: return@mapNotNull null
                    val rating = document.getDouble("rating")
                        ?: document.getLong("rating")?.toDouble()
                        ?: 0.0
                    val ratingCount = document.getLong("ratingCount")?.toInt() ?: 0

                    User(
                        uid = providerId,
                        name = document.getString("displayName").orEmpty(),
                        roles = listOf("provider"),
                        serviceCategories = (document.get("serviceCategories") as? List<*>)
                            ?.filterIsInstance<String>()
                            ?: emptyList(),
                        profilePictureUrl = document.getString("profilePhotoUrl"),
                        isVerified = document.getBoolean("isVerified") == true,
                        ratingSum = rating * ratingCount,
                        ratingCount = ratingCount,
                        emergencyAvailable = document.getBoolean("emergencyAvailable") == true,
                        lastLocation = GeoPoint(latitude, longitude),
                        lastLocationAt = locationAt
                    )
                }
                trySend(providers)
            }
        awaitClose { listener.remove() }
    }
}
