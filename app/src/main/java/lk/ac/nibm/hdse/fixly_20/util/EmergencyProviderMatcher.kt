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
            else -> {
                val canonical = ServiceCategories.normalize(category)
                when (canonical) {
                    "Vehicle Mechanic" -> listOf("Vehicle Mechanic", "Mechanic")
                    "Electrical" -> listOf("Electrician", "Electrical")
                    "Plumbing" -> listOf("Plumber", "Plumbing")
                    "AC Repair" -> listOf("AC Technician", "AC Repair")
                    "Computer Technician" -> listOf("Computer Technician", "Computer Repair")
                    else -> listOf(canonical)
                }
            }
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

            val normalizedProviderCategories = provider.serviceCategories.map { ServiceCategories.normalize(it) }
            if (normalizedCategory != null &&
                normalizedProviderCategories.none { it in normalizedCategory.map { c -> ServiceCategories.normalize(c) } }
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
                .thenByDescending {
                    if (category == "Any" || category == "Any Available Provider") {
                        it.provider.averageRating
                    }
                    else it.provider.ratingForCategory(ServiceCategories.normalize(category)).averageRating
                }
                .thenByDescending { it.provider.isVerified }
        )
    }
}
