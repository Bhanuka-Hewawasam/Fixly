package lk.ac.nibm.hdse.fixly_20.model

import com.google.firebase.firestore.Exclude
import com.google.firebase.firestore.PropertyName
import lk.ac.nibm.hdse.fixly_20.util.ServiceCategories

enum class AccountType { CUSTOMER, PROVIDER }

data class User(
    val uid: String = "",
    val name: String = "",
    val email: String = "",
    val phoneNumber: String = "",
    val roles: List<String> = listOf("customer"),
    val serviceCategories: List<String> = emptyList(),
    val certificateUrl: String? = null,
    val profilePictureUrl: String? = null,

    // Kotlin "isXxx" Boolean properties are saved by Firestore WITHOUT the "is" prefix
    // (isVerified -> "verified") unless the name is pinned like this.
    @get:PropertyName("isVerified")
    val isVerified: Boolean = false,

    val ratingSum: Double = 0.0,   // total of all star ratings received as a provider
    val ratingCount: Int = 0,      // how many reviews that total came from
    val rating: Double = 0.0,
    val serviceRatings: Map<String, ServiceRating> = emptyMap(),

    val serviceAddress: String = "",
    val serviceCity: String = "",
    val serviceLatitude: Double? = null,
    val serviceLongitude: Double? = null,
    val serviceRadiusKm: Int = 20,
    val availableForEmergency: Boolean = false,
    val verificationStatus: String = "PENDING",
    val suspended: Boolean = false
) {
    // Computed on the fly, never stored in Firestore
    @get:Exclude
    val averageRating: Double
        get() = if (ratingCount > 0) ratingSum / ratingCount else 0.0

    fun ratingForCategory(category: String): ServiceRating {
        val normalized = ServiceCategories.normalize(category)
        val entry = serviceRatings.entries.firstOrNull {
            it.key.equals(category, ignoreCase = true) ||
            it.key.equals(normalized, ignoreCase = true) ||
            ServiceCategories.normalize(it.key).equals(normalized, ignoreCase = true)
        }
        return entry?.value ?: ServiceRating()
    }
}
