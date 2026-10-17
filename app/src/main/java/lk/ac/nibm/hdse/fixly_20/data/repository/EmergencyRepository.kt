package lk.ac.nibm.hdse.fixly_20.data.repository

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import lk.ac.nibm.hdse.fixly_20.model.User

class EmergencyRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) {
    fun observeAvailableProviders(): Flow<List<User>> = callbackFlow {
        val listener = firestore.collection("emergencyServiceAreas")
            .whereEqualTo("availableForEmergency", true)
            .whereEqualTo("suspended", false)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                trySend(snapshot?.documents.orEmpty().mapNotNull(::toProvider))
            }
        awaitClose { listener.remove() }
    }

    private fun toProvider(document: DocumentSnapshot): User? {
        val providerId = document.getString("providerId") ?: return null
        val latitude = document.getDouble("serviceLatitude") ?: return null
        val longitude = document.getDouble("serviceLongitude") ?: return null
        if (!latitude.isFinite() || latitude !in -90.0..90.0 ||
            !longitude.isFinite() || longitude !in -180.0..180.0
        ) return null
        return User(
            uid = providerId,
            name = document.getString("displayName").orEmpty(),
            roles = listOf("provider"),
            serviceCategories = (document.get("serviceCategories") as? List<*>)
                ?.filterIsInstance<String>()
                ?: emptyList(),
            profilePictureUrl = document.getString("profilePhotoUrl"),
            isVerified = document.getBoolean("isVerified") == true,
            rating = document.getDouble("rating") ?: 0.0,
            ratingSum = (document.getDouble("rating") ?: 0.0) *
                (document.getLong("ratingCount")?.toInt() ?: 0),
            ratingCount = document.getLong("ratingCount")?.toInt() ?: 0,
            serviceAddress = document.getString("serviceAddress").orEmpty(),
            serviceCity = document.getString("serviceCity").orEmpty(),
            serviceLatitude = latitude,
            serviceLongitude = longitude,
            serviceRadiusKm = document.getLong("serviceRadiusKm")?.toInt() ?: 0,
            availableForEmergency = document.getBoolean("availableForEmergency") == true,
            verificationStatus = document.getString("verificationStatus") ?: "PENDING",
            suspended = document.getBoolean("suspended") == true
        )
    }
}
