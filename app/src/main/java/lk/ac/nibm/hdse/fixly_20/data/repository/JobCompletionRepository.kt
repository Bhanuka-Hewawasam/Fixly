package lk.ac.nibm.hdse.fixly_20.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import lk.ac.nibm.hdse.fixly_20.model.JobPost
import lk.ac.nibm.hdse.fixly_20.model.BookingPolicy
import lk.ac.nibm.hdse.fixly_20.model.ServiceRating
import lk.ac.nibm.hdse.fixly_20.model.ServiceRatingPolicy
import lk.ac.nibm.hdse.fixly_20.model.ServiceReview
import lk.ac.nibm.hdse.fixly_20.model.ServiceReviewPolicy

/**
 * Everything that happens AFTER a provider marks a job Completed:
 * the customer either confirms it (with a star review) or sends it back for fixing.
 */
class JobCompletionRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val firebaseAuth: FirebaseAuth = FirebaseAuth.getInstance()
) {

    /**
     * Confirms the booking and writes the review and both rating aggregates atomically.
     */
    suspend fun confirmWithReview(job: JobPost, rating: Int, comment: String): Result<Unit> {
        return try {
            val normalizedComment = comment.trim()
            require(rating in 1..5) { "Rating must be between 1 and 5." }
            require(normalizedComment.length <= MAX_REVIEW_LENGTH) {
                "Review comments must be $MAX_REVIEW_LENGTH characters or fewer."
            }
            val customerId = firebaseAuth.currentUser?.uid
                ?: throw IllegalStateException("Sign in to submit a review.")
            val providerId = job.assignedProviderId
                ?: return Result.failure(IllegalStateException("No provider is assigned to this job"))
            if (customerId == providerId) {
                return Result.failure(IllegalStateException("You cannot review or complete your own job."))
            }

            val jobRef = firestore.collection("jobPosts").document(job.jobId)
            val ratingAggregateRef = firestore.collection("providerRatingAggregates").document(providerId)
            val reviewRef = firestore.collection("serviceReviews").document(job.jobId)
            val bookingLockId = job.bookingLockId
                ?: ServiceRatingPolicy.bookingLockId(job.customerId, providerId, job.category)
            val bookingLockRef = firestore.collection("bookingLocks").document(bookingLockId)
            firestore.runTransaction { transaction ->
                val jobSnapshot = transaction.get(jobRef)
                val ratingAggregateSnapshot = transaction.get(ratingAggregateRef)
                val reviewSnapshot = transaction.get(reviewRef)
                val lockSnapshot = transaction.get(bookingLockRef)
                val jobCustomerId = jobSnapshot.getString("customerId")
                val assignedProviderId = jobSnapshot.getString("assignedProviderId")
                if (jobCustomerId == assignedProviderId || customerId == assignedProviderId) {
                    throw IllegalStateException("You cannot review or complete your own job.")
                }
                if (jobSnapshot.getString("assignedProviderId") != providerId) {
                    throw IllegalStateException("The assigned provider for this job has changed.")
                }
                if (!ServiceReviewPolicy.canCreate(
                        customerId = customerId,
                        bookingCustomerId = jobSnapshot.getString("customerId"),
                        providerId = providerId,
                        bookingProviderId = jobSnapshot.getString("assignedProviderId"),
                        jobStatus = jobSnapshot.getString("status"),
                        customerConfirmed = jobSnapshot.getBoolean("customerConfirmed") == true,
                        reviewAlreadyExists = reviewSnapshot.exists(),
                        rating = rating,
                        commentLength = normalizedComment.length
                    )
                ) {
                    throw IllegalStateException("Only the customer who owns an unreviewed completed job can submit one review.")
                }
                val category = jobSnapshot.getString("category")
                    ?: throw IllegalStateException("Job has no service category.")
                require(category.isNotBlank()) { "Job has no valid service category." }
                val updatedSum = (ratingAggregateSnapshot.getDouble("ratingSum") ?: 0.0) + rating
                val updatedCount = (ratingAggregateSnapshot.getLong("ratingCount") ?: 0L) + 1L
                val serviceRatings = readServiceRatings(ratingAggregateSnapshot.get("serviceRatings"))
                val updatedServiceRatings = ServiceRatingPolicy.nextByCategory(
                    serviceRatings,
                    category,
                    rating
                )
                transaction.update(
                    jobRef,
                    mapOf(
                        "customerConfirmed" to true,
                        "rating" to rating,
                        "reviewComment" to normalizedComment
                    )
                )
                transaction.set(
                    reviewRef,
                    ServiceReview(
                        bookingId = job.jobId,
                        customerId = customerId,
                        providerId = providerId,
                        serviceCategory = category,
                        rating = rating,
                        comment = normalizedComment,
                        createdAt = null
                    ).let { review ->
                        mapOf(
                            "bookingId" to review.bookingId,
                            "customerId" to review.customerId,
                            "providerId" to review.providerId,
                            "serviceCategory" to review.serviceCategory,
                            "rating" to review.rating,
                            "comment" to review.comment,
                            "createdAt" to FieldValue.serverTimestamp()
                        )
                    }
                )
                val updatedRatings = updatedServiceRatings.mapValues { (_, value) ->
                    mapOf(
                        "averageRating" to value.averageRating,
                        "reviewCount" to value.reviewCount
                    )
                }
                transaction.set(
                    ratingAggregateRef,
                    mapOf(
                        "providerId" to providerId,
                        "ratingSum" to updatedSum,
                        "ratingCount" to updatedCount,
                        "rating" to updatedSum / updatedCount,
                        "serviceRatings" to updatedRatings,
                        "availableForEmergency" to (
                            ratingAggregateSnapshot.getBoolean("availableForEmergency") == true
                        ),
                        "lastRatingJobId" to job.jobId,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
                if (lockSnapshot.exists() && BookingPolicy.shouldReleaseLock(
                        jobSnapshot.getString("status"),
                        customerConfirmed = true
                    )
                ) {
                    transaction.delete(bookingLockRef)
                }
            }.await()

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun readServiceRatings(value: Any?): Map<String, ServiceRating> =
        (value as? Map<*, *>).orEmpty().mapNotNull { (category, rawRating) ->
            val data = rawRating as? Map<*, *> ?: return@mapNotNull null
            val average = (data["averageRating"] as? Number)?.toDouble() ?: return@mapNotNull null
            val count = (data["reviewCount"] as? Number)?.toInt() ?: return@mapNotNull null
            (category as? String)?.let { it to ServiceRating(average, count) }
        }.toMap()

    /**
     * Customer isn't happy: job goes back to "In Progress" and the old "after" photo is removed,
     * so the provider has to fix the work and upload a fresh photo before completing again.
     */
    suspend fun sendBackToProvider(jobId: String): Result<Unit> {
        return try {
            firestore.collection("jobPosts").document(jobId)
                .update(
                    mapOf(
                        "status" to "In Progress",
                        "afterPhotoUrl" to FieldValue.delete()
                    )
                ).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    companion object {
        const val MAX_REVIEW_LENGTH = 1000
    }
}
