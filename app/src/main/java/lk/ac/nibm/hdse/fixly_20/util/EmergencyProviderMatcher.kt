package lk.ac.nibm.hdse.fixly_20.util

import com.google.firebase.firestore.GeoPoint
import lk.ac.nibm.hdse.fixly_20.model.User
import java.util.Date

data class ProviderMatch(
    val provider: User,
    val distanceKm: Double,
    val minutesAgo: Long
)

object EmergencyProviderMatcher {
    const val MAX_PROVIDER_DISTANCE_KM = 50.0
    const val MAX_LOCATION_AGE_MS = 15L * 60 * 1000

    fun findMatches(
        providers: List<User>,
        customerLocation: GeoPoint,
        category: String,
        radiusKm: Int,
        currentUid: String?,
        currentEmail: String?,
        nowMs: Long = System.currentTimeMillis()
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
                !provider.emergencyAvailable
            ) return@mapNotNull null

            if (normalizedCategory != null &&
                provider.serviceCategories.none { it in normalizedCategory }
            ) return@mapNotNull null

            val location = provider.lastLocation ?: return@mapNotNull null
            val locationTime = provider.lastLocationAt ?: return@mapNotNull null
            val ageMs = nowMs - locationTime.time
            if (ageMs < 0L || ageMs > MAX_LOCATION_AGE_MS) return@mapNotNull null
            if (!location.latitude.isFinite() || location.latitude !in -90.0..90.0 ||
                !location.longitude.isFinite() || location.longitude !in -180.0..180.0
            ) return@mapNotNull null

            val distance = DistanceUtils.distanceKm(
                customerLocation.latitude,
                customerLocation.longitude,
                location.latitude,
                location.longitude
            )
            if (distance > maxDistance) return@mapNotNull null

            ProviderMatch(provider, distance, ageMs / 60_000)
        }.sortedWith(
            compareBy<ProviderMatch> { it.distanceKm }
                .thenByDescending { it.provider.emergencyAvailable }
                .thenByDescending { it.provider.averageRating }
        )
    }
}
