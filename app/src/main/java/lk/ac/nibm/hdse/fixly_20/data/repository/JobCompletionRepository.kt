package lk.ac.nibm.hdse.fixly_20.data.repository

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import lk.ac.nibm.hdse.fixly_20.model.JobPost

/**
 * Everything that happens AFTER a provider marks a job Completed:
 * the customer either confirms it (with a star review) or sends it back for fixing.
 */
class JobCompletionRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) {

    /**
     * Saves the review on the job AND adds the stars to the provider's running rating totals.
     * Both writes go in one batch, so they either both succeed or both fail.
     */
    suspend fun confirmWithReview(job: JobPost, rating: Int, comment: String): Result<Unit> {
        return try {
            val providerId = job.assignedProviderId
                ?: return Result.failure(IllegalStateException("No provider is assigned to this job"))

            val jobRef = firestore.collection("jobPosts").document(job.jobId)
            val providerRef = firestore.collection("users").document(providerId)

            val batch = firestore.batch()
            batch.update(
                jobRef,
                mapOf(
                    "customerConfirmed" to true,
                    "rating" to rating,
                    "reviewComment" to comment
                )
            )
            batch.update(
                providerRef,
                mapOf(
                    "ratingSum" to FieldValue.increment(rating.toDouble()),
                    "ratingCount" to FieldValue.increment(1L),
                    "lastRatingJobId" to job.jobId
                )
            )
            batch.commit().await()

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

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
}
