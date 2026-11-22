package lk.ac.nibm.hdse.fixly_20.data.repository

import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import lk.ac.nibm.hdse.fixly_20.model.ProviderRatingAggregate

class ProviderRatingRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) {
    fun observe(providerId: String): Flow<Result<ProviderRatingAggregate?>> = callbackFlow {
        val listener = firestore.collection("providerRatingAggregates")
            .document(providerId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(Result.failure(error))
                    return@addSnapshotListener
                }
                trySend(Result.success(snapshot?.toObject(ProviderRatingAggregate::class.java)))
            }
        awaitClose { listener.remove() }
    }
}
