package lk.ac.nibm.hdse.fixly_20.model

import com.google.firebase.firestore.GeoPoint
import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

/**
 * Firestore document: jobPosts/{jobId}
 * Bids for this job live in a sub-collection: jobPosts/{jobId}/bids/{bidId}
 */
data class JobPost(
    val jobId: String = "",
    val customerId: String = "",
    val customerName: String = "",
    val category: String = "",          // "Plumbing", "Electrical", "Cleaning", "Other"
    val description: String = "",
    val preferredDate: String = "",      // simple text for now, e.g. "2026-10-12"
    val preferredTime: String = "",      // e.g. "10:00 AM"
    val isEmergency: Boolean = false,    // sir's requirement — urgent jobs flagged for faster matching
    val location: GeoPoint? = null,
    val address: String = "",
    val status: String = "Open",         // Open -> Assigned -> In Progress -> Completed
    val selectedBidId: String? = null,
    @ServerTimestamp val createdAt: Date? = null
)
