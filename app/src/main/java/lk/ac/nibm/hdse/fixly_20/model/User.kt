package lk.ac.nibm.hdse.fixly_20.model

import com.google.firebase.firestore.Exclude
import com.google.firebase.firestore.GeoPoint
import com.google.firebase.firestore.PropertyName
import java.util.Date

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

    // --- Emergency search (providers opt in) ---
    val emergencyAvailable: Boolean = false, // provider switched "Available for emergencies" ON
    val lastLocation: GeoPoint? = null,      // where the provider was when they last shared
    val lastLocationAt: Date? = null         // when that location was shared
) {
    // Computed on the fly, never stored in Firestore
    @get:Exclude
    val averageRating: Double
        get() = if (ratingCount > 0) ratingSum / ratingCount else 0.0
}
