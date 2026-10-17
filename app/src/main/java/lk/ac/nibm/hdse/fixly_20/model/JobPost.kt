package lk.ac.nibm.hdse.fixly_20.model

import com.google.firebase.firestore.GeoPoint
import com.google.firebase.firestore.PropertyName
import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

data class JobPost(
    val jobId: String = "",
    val customerId: String = "",
    val customerName: String = "",
    val category: String = "",
    val description: String = "",
    val preferredDate: String = "",
    val preferredTime: String = "",

    // Pinned name: without this, Firestore saves a Kotlin "isEmergency" property as "emergency",
    // and our queries on "isEmergency" would never match.
    @get:PropertyName("isEmergency")
    val isEmergency: Boolean = false,

    val location: GeoPoint? = null,
    val address: String = "",
    val status: String = "Open",            // Open -> Assigned -> In Progress -> Completed
    val selectedBidId: String? = null,
    val assignedProviderId: String? = null,
    val emergencyAlertId: String? = null,
    val providerLocation: GeoPoint? = null,
    val beforePhotoUrl: String? = null,
    val afterPhotoUrl: String? = null,

    // After the provider marks the job Completed, the customer confirms it and leaves a review
    val customerConfirmed: Boolean = false,
    val rating: Int? = null,                // 1–5 stars
    val reviewComment: String? = null,

    val imageUrls: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Date? = null
)
