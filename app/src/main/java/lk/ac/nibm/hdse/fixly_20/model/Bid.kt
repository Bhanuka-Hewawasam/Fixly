package lk.ac.nibm.hdse.fixly_20.model

import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

/**
 * Firestore document: jobPosts/{jobId}/bids/{bidId}
 */
data class Bid(
    val bidId: String = "",
    val jobId: String = "",
    val providerId: String = "",
    val providerName: String = "",
    val price: Double = 0.0,
    val message: String = "",
    val providerRating: Double = 0.0, // placeholder until ratings/reviews feature exists
    @ServerTimestamp val createdAt: Date? = null
)
