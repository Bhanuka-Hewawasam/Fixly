package lk.ac.nibm.hdse.fixly_20.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import lk.ac.nibm.hdse.fixly_20.model.Bid

class BidRepository(
    private val firebaseAuth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) {

    suspend fun submitBid(
        jobId: String,
        providerName: String,
        price: Double,
        message: String
    ): Result<Unit> {
        return try {
            val providerId = firebaseAuth.currentUser?.uid
                ?: return Result.failure(IllegalStateException("No logged-in user"))

            val bidRef = firestore.collection("jobPosts")
                .document(jobId)
                .collection("bids")
                .document()

            val bid = Bid(
                bidId = bidRef.id,
                jobId = jobId,
                providerId = providerId,
                providerName = providerName,
                price = price,
                message = message
            )

            bidRef.set(bid).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Real-time stream of bids placed on a specific job, newest first. */
    fun observeBidsForJob(jobId: String): Flow<List<Bid>> = callbackFlow {
        val listener = firestore.collection("jobPosts")
            .document(jobId)
            .collection("bids")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    error.printStackTrace()
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                val bids = snapshot?.toObjects(Bid::class.java) ?: emptyList()
                val sortedBids = bids.sortedByDescending { it.createdAt }
                trySend(sortedBids)
            }
        awaitClose { listener.remove() }
    }

    /**
     * Customer selects a bid: marks the job "Assigned" and records which bid won.
     * Selection is always manual — this just finalises the customer's own choice.
     */
    suspend fun selectBid(jobId: String, bidId: String): Result<Unit> {
        return try {
            firestore.collection("jobPosts").document(jobId)
                .update(mapOf("selectedBidId" to bidId, "status" to "Assigned"))
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
