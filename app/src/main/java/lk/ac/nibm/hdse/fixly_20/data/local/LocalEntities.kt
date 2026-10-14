package lk.ac.nibm.hdse.fixly_20.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Local copy of one of the customer's own jobs (table "bookings").
 * Firestore stays the source of truth; this table is what the My Bookings screen actually reads.
 */
@Entity(tableName = "bookings", indices = [Index("customerId")])
data class BookingEntity(
    @PrimaryKey val jobId: String,
    val customerId: String,
    val customerName: String,
    val category: String,
    val description: String,
    val preferredDate: String,
    val preferredTime: String,
    val isEmergency: Boolean,
    val address: String,
    val status: String,
    val selectedBidId: String?,
    val assignedProviderId: String?,
    val beforePhotoUrl: String?,
    val afterPhotoUrl: String?,
    val customerConfirmed: Boolean,
    val rating: Int?,
    val reviewComment: String?,
    val latitude: Double?,        // job location, split into two plain columns
    val longitude: Double?,
    val createdAtMillis: Long?
)

/**
 * Local copy of the logged-in user's profile (table "user_profile").
 * Used as an offline fallback when Firestore can't be reached.
 */
@Entity(tableName = "user_profile")
data class UserProfileEntity(
    @PrimaryKey val uid: String,
    val name: String,
    val email: String,
    val phoneNumber: String,
    val roles: List<String>,               // stored via the TypeConverter in AppDatabase.kt
    val serviceCategories: List<String>,
    val certificateUrl: String?,
    val isVerified: Boolean,
    val ratingSum: Double,
    val ratingCount: Int,
    val emergencyAvailable: Boolean,
    val lastLatitude: Double?,
    val lastLongitude: Double?,
    val lastLocationAtMillis: Long?
)
