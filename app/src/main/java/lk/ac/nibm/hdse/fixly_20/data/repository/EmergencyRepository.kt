package lk.ac.nibm.hdse.fixly_20.data.repository

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.QuerySnapshot
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import lk.ac.nibm.hdse.fixly_20.model.User
import lk.ac.nibm.hdse.fixly_20.model.ServiceRating
import lk.ac.nibm.hdse.fixly_20.model.ProviderRatingAggregate

class EmergencyRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) {
    fun observeAvailableProviders(): Flow<List<User>> = callbackFlow {
        var serviceAreas: QuerySnapshot? = null
        var ratingAggregates: Map<String, ProviderRatingAggregate> = emptyMap()

        fun emitProviders() {
            val areas = serviceAreas ?: return
            trySend(
                areas.documents.mapNotNull { document ->
                    val providerId = document.getString("providerId") ?: return@mapNotNull null
                    toProvider(document, ratingAggregates[providerId])
                }
            )
        }

        val serviceAreaListener = firestore.collection("emergencyServiceAreas")
            .whereEqualTo("availableForEmergency", true)
            .whereEqualTo("suspended", false)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                serviceAreas = snapshot
                emitProviders()
            }
        val ratingsListener = firestore.collection("providerRatingAggregates")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                ratingAggregates = snapshot?.documents.orEmpty().mapNotNull { document ->
                    document.toObject(ProviderRatingAggregate::class.java)?.let {
                        document.id to it
                    }
                }.toMap()
                emitProviders()
            }
        awaitClose {
            serviceAreaListener.remove()
            ratingsListener.remove()
        }
    }

    private fun toProvider(
        document: DocumentSnapshot,
        ratingAggregate: ProviderRatingAggregate?
    ): User? {
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
            rating = ratingAggregate?.rating ?: document.getDouble("rating") ?: 0.0,
            ratingSum = ratingAggregate?.ratingSum
                ?: (document.getDouble("rating") ?: 0.0) *
                (document.getLong("ratingCount")?.toInt() ?: 0),
            ratingCount = ratingAggregate?.ratingCount ?: document.getLong("ratingCount")?.toInt() ?: 0,
            serviceRatings = ratingAggregate?.serviceRatings
                ?: readServiceRatings(document.get("serviceRatings")),
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

    private fun readServiceRatings(value: Any?): Map<String, ServiceRating> =
        (value as? Map<*, *>).orEmpty().mapNotNull { (category, rawRating) ->
            val data = rawRating as? Map<*, *> ?: return@mapNotNull null
            val average = (data["averageRating"] as? Number)?.toDouble() ?: return@mapNotNull null
            val count = (data["reviewCount"] as? Number)?.toInt() ?: return@mapNotNull null
            (category as? String)?.let { it to ServiceRating(average, count) }
        }.toMap()
}
