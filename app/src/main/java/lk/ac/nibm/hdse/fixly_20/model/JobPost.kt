package lk.ac.nibm.hdse.fixly_20.model

import com.google.firebase.firestore.GeoPoint
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
    val isEmergency: Boolean = false,
    val location: GeoPoint? = null,
    val address: String = "",
    val status: String = "Open",            // Open -> Assigned -> In Progress -> Completed
    val selectedBidId: String? = null,
    val assignedProviderId: String? = null,
    val providerLocation: GeoPoint? = null,
    val beforePhotoUrl: String? = null,     // optional — provider's photo before starting work
    val afterPhotoUrl: String? = null,      // required to mark the job Completed
    val imageUrls: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Date? = null
)
