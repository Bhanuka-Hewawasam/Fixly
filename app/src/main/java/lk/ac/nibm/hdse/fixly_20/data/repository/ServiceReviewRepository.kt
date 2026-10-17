package lk.ac.nibm.hdse.fixly_20.data.repository

import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import lk.ac.nibm.hdse.fixly_20.model.ServiceReview

class ServiceReviewRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) {
    fun observeProviderReviews(providerId: String): Flow<Result<List<ServiceReview>>> = callbackFlow {
        val listener = firestore.collection("serviceReviews")
            .whereEqualTo("providerId", providerId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(Result.failure(error))
                    return@addSnapshotListener
                }
                val reviews = snapshot?.toObjects(ServiceReview::class.java)
                    .orEmpty()
                    .sortedByDescending { it.createdAt }
                trySend(Result.success(reviews))
            }
        awaitClose { listener.remove() }
    }
}
