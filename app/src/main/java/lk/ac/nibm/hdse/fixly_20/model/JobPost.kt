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
    val location: GeoPoint? = null,       // customer's job address location
    val address: String = "",
    val status: String = "Open",          // Open -> Assigned -> In Progress -> Completed
    val selectedBidId: String? = null,
    val assignedProviderId: String? = null, // set when a bid is selected — who's doing this job
    val providerLocation: GeoPoint? = null, // live GPS location, updated while job is Assigned/In Progress
    val imageUrls: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Date? = null
)
