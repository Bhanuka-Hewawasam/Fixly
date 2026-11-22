package lk.ac.nibm.hdse.fixly_20.model

import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

data class ServiceReview(
    val bookingId: String = "",
    val customerId: String = "",
    val providerId: String = "",
    val serviceCategory: String = "",
    val rating: Int = 0,
    val comment: String = "",
    @ServerTimestamp val createdAt: Date? = null
)

object ServiceReviewPolicy {
    fun canCreate(
        customerId: String,
        bookingCustomerId: String?,
        providerId: String,
        bookingProviderId: String?,
        jobStatus: String?,
        customerConfirmed: Boolean,
        reviewAlreadyExists: Boolean,
        rating: Int,
        commentLength: Int
    ): Boolean =
        customerId.isNotBlank() &&
            customerId == bookingCustomerId &&
            customerId != providerId &&
            providerId == bookingProviderId &&
            jobStatus == "Completed" &&
            !customerConfirmed &&
            !reviewAlreadyExists &&
            rating in 1..5 &&
            commentLength in 0..1000
}
