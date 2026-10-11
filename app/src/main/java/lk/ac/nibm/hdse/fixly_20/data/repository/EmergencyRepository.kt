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
     * Provider opts in/out of emergency searches.
     * When switching ON (or refreshing), their current location + timestamp are saved on their profile.
     */
    suspend fun setAvailability(available: Boolean, location: GeoPoint?): Result<Unit> {
        return try {
            val uid = firebaseAuth.currentUser?.uid
                ?: return Result.failure(IllegalStateException("No logged-in user"))

            val updates = mutableMapOf<String, Any>("emergencyAvailable" to available)
            if (available && location != null) {
                updates["lastLocation"] = location
                updates["lastLocationAt"] = FieldValue.serverTimestamp()
            }

            firestore.collection("users").document(uid).update(updates).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Real-time stream of every provider who currently has "Available for emergencies" ON.
     * Category / distance / freshness filtering is done by the ViewModel, so this stays a
     * single-field query (no composite index needed).
     */
    fun observeAvailableProviders(): Flow<List<User>> = callbackFlow {
        val listener = firestore.collection("users")
            .whereEqualTo("emergencyAvailable", true)
            .addSnapshotListener { snapshot, error ->
                if (error != null) { close(error); return@addSnapshotListener }
                trySend(snapshot?.toObjects(User::class.java) ?: emptyList())
            }
        awaitClose { listener.remove() }
    }
}
