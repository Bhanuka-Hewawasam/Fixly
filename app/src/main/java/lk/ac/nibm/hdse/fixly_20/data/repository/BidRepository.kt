package lk.ac.nibm.hdse.fixly_20.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import lk.ac.nibm.hdse.fixly_20.model.Bid
import lk.ac.nibm.hdse.fixly_20.model.User

class BidRepository(
    private val firebaseAuth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) {

    /**
     * Places a bid. The provider's current average rating and review count are copied
     * into the bid, so the customer sees their reputation right on the bid card.
     */
    suspend fun submitBid(
        jobId: String,
        providerName: String,
        price: Double,
        message: String
    ): Result<Unit> {
        return try {
            val providerId = firebaseAuth.currentUser?.uid
                ?: return Result.failure(IllegalStateException("No logged-in user"))

            val providerProfile = firestore.collection("users").document(providerId)
                .get().await().toObject(User::class.java)

            val bidRef = firestore.collection("jobPosts").document(jobId).collection("bids").document()
            val bid = Bid(
                bidId = bidRef.id,
                jobId = jobId,
                providerId = providerId,
                providerName = providerName,
                price = price,
                message = message,
                providerRating = providerProfile?.averageRating ?: 0.0,
                providerReviewCount = providerProfile?.ratingCount ?: 0
            )
            bidRef.set(bid).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun observeBidsForJob(jobId: String): Flow<List<Bid>> = callbackFlow {
        val listener = firestore.collection("jobPosts").document(jobId).collection("bids")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) { close(error); return@addSnapshotListener }
                trySend(snapshot?.toObjects(Bid::class.java) ?: emptyList())
            }
        awaitClose { listener.remove() }
    }

    suspend fun selectBid(jobId: String, bidId: String): Result<Unit> {
        return try {
            val bidSnapshot = firestore.collection("jobPosts").document(jobId)
                .collection("bids").document(bidId).get().await()
            val bid = bidSnapshot.toObject(Bid::class.java)
                ?: return Result.failure(IllegalStateException("Bid not found"))

            firestore.collection("jobPosts").document(jobId)
                .update(
                    mapOf(
                        "selectedBidId" to bidId,
                        "assignedProviderId" to bid.providerId,
                        "status" to "Assigned"
                    )
                ).await()

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
