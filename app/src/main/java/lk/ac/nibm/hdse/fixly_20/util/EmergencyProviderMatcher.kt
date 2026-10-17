package lk.ac.nibm.hdse.fixly_20.util

import com.google.firebase.firestore.GeoPoint
import lk.ac.nibm.hdse.fixly_20.model.User

data class ProviderMatch(
    val provider: User,
    val distanceKm: Double
)

object EmergencyProviderMatcher {
    const val MAX_PROVIDER_DISTANCE_KM = 50.0

    fun findMatches(
        providers: List<User>,
        customerLocation: GeoPoint,
        category: String,
        radiusKm: Int,
        currentUid: String?,
        currentEmail: String?
    ): List<ProviderMatch> {
        val normalizedCategory = when (category) {
            "Any", "Any Available Provider" -> null
            "Vehicle Mechanic" -> listOf("Vehicle Mechanic", "Mechanic")
            "Electrician" -> listOf("Electrician", "Electrical")
            "Plumber" -> listOf("Plumber", "Plumbing")
            "AC Technician" -> listOf("AC Technician", "AC Repair")
            "Computer Technician" -> listOf("Computer Technician", "Computer Repair")
            else -> listOf(category)
        }
        val maxDistance = minOf(radiusKm.toDouble(), MAX_PROVIDER_DISTANCE_KM)

        return providers.mapNotNull { provider ->
            if (provider.uid.isBlank() ||
                provider.uid == currentUid ||
                (currentEmail != null && provider.email.equals(currentEmail, ignoreCase = true)) ||
                !provider.roles.contains("provider") ||
                !provider.availableForEmergency ||
                provider.suspended
            ) return@mapNotNull null

            if (normalizedCategory != null &&
                provider.serviceCategories.none { it in normalizedCategory }
            ) return@mapNotNull null

            val latitude = provider.serviceLatitude ?: return@mapNotNull null
            val longitude = provider.serviceLongitude ?: return@mapNotNull null
            if (!latitude.isFinite() || latitude !in -90.0..90.0 ||
                !longitude.isFinite() || longitude !in -180.0..180.0
            ) return@mapNotNull null

            val distance = DistanceUtils.distanceKm(
                customerLocation.latitude,
                customerLocation.longitude,
                latitude,
                longitude
            )
            val providerRadius = provider.serviceRadiusKm.toDouble()
            if (providerRadius <= 0.0 || distance > minOf(maxDistance, providerRadius)) {
                return@mapNotNull null
            }

            ProviderMatch(provider, distance)
        }.sortedWith(
            compareBy<ProviderMatch> { it.distanceKm }
                .thenByDescending { it.provider.averageRating }
                .thenByDescending { it.provider.isVerified }
        )
    }
}
