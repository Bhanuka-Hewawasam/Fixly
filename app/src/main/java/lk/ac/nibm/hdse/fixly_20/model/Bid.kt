package lk.ac.nibm.hdse.fixly_20.model

import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

/**
 * Firestore document: jobPosts/{jobId}/bids/{bidId}
 * providerRating / providerReviewCount are the category-specific rating snapshot
 * for the job's service at the time this bid was placed.
 */
data class Bid(
    val bidId: String = "",
    val jobId: String = "",
    val providerId: String = "",
    val providerName: String = "",
    val price: Double = 0.0,
    val message: String = "",
    val providerRating: Double = 0.0,
    val providerReviewCount: Int = 0,
    @ServerTimestamp val createdAt: Date? = null
)
