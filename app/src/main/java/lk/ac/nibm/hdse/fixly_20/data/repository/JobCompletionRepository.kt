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
            val emergencyAreaRef = firestore.collection("emergencyServiceAreas").document(providerId)
            firestore.runTransaction { transaction ->
                val jobSnapshot = transaction.get(jobRef)
                val providerSnapshot = transaction.get(providerRef)
                val emergencyAreaSnapshot = transaction.get(emergencyAreaRef)
                if (jobSnapshot.getBoolean("customerConfirmed") == true) {
                    throw IllegalStateException("This job has already been reviewed.")
                }
                val updatedSum = (providerSnapshot.getDouble("ratingSum") ?: 0.0) + rating
                val updatedCount = (providerSnapshot.getLong("ratingCount") ?: 0L) + 1L
                transaction.update(
                    jobRef,
                    mapOf(
                        "customerConfirmed" to true,
                        "rating" to rating,
                        "reviewComment" to comment
                    )
                )
                transaction.update(
                    providerRef,
                    mapOf(
                        "ratingSum" to updatedSum,
                        "ratingCount" to updatedCount,
                        "rating" to updatedSum / updatedCount,
                        "lastRatingJobId" to job.jobId
                    )
                )
                if (emergencyAreaSnapshot.exists()) {
                    transaction.update(
                        emergencyAreaRef,
                        mapOf(
                            "rating" to updatedSum / updatedCount,
                            "ratingCount" to updatedCount,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                }
            }.await()

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
