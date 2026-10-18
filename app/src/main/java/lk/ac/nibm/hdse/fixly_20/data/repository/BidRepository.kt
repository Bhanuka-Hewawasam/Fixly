package lk.ac.nibm.hdse.fixly_20.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import lk.ac.nibm.hdse.fixly_20.model.Bid
import lk.ac.nibm.hdse.fixly_20.model.BookingPolicy
import lk.ac.nibm.hdse.fixly_20.model.JobPost
import lk.ac.nibm.hdse.fixly_20.model.ProviderRatingAggregate
import lk.ac.nibm.hdse.fixly_20.model.ServiceRatingPolicy
import lk.ac.nibm.hdse.fixly_20.model.User
import lk.ac.nibm.hdse.fixly_20.util.findOverlappingActiveJob
import lk.ac.nibm.hdse.fixly_20.util.jobTimeConflictMessage
import lk.ac.nibm.hdse.fixly_20.util.providerJobSlotKeys

class BidRepository(
    private val firebaseAuth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) {

    /**
     * Places a bid with the provider's rating for the job's service category.
     */
    suspend fun submitBid(
        jobId: String,
        providerName: String,
        price: Double,
        message: String,
        distanceKm: Double,
        estimatedArrivalMinutes: Int?
    ): Result<Unit> {
        return try {
            val providerId = firebaseAuth.currentUser?.uid
                ?: return Result.failure(IllegalStateException("No logged-in user"))
            require(price.isFinite() && price > 0.0) {
                "Enter a price greater than zero."
            }
            require(distanceKm.isFinite() && distanceKm >= 0.0) {
                "A valid distance is required to place a bid."
            }
            require(estimatedArrivalMinutes == null || estimatedArrivalMinutes in 1..1440) {
                "ETA must be between 1 and 1440 minutes."
            }

            val job = firestore.collection("jobPosts").document(jobId).get().await()
                .toObject(JobPost::class.java)
                ?: return Result.failure(IllegalStateException("Job not found"))
            if (job.customerId == providerId) {
                return Result.failure(IllegalStateException("You can't bid on your own job."))
            }
            if (!job.isEmergency) {
                val activeJobs = firestore.collection("jobPosts")
                    .whereEqualTo("assignedProviderId", providerId)
                    .whereIn("status", listOf("Accepted", "In Progress"))
                    .get()
                    .await()
                    .toObjects(JobPost::class.java)
                val timeConflict = findOverlappingActiveJob(job, activeJobs)
                if (timeConflict != null) {
                    return Result.failure(IllegalStateException(jobTimeConflictMessage(timeConflict)))
                }
            }

            val bidRef = firestore.collection("jobPosts").document(jobId).collection("bids").document(providerId)
            val userRef = firestore.collection("users").document(providerId)
            val aggregateRef = firestore.collection("providerRatingAggregates").document(providerId)
            val providerMetadata = firestore.runTransaction { transaction ->
                val existingBid = transaction.get(bidRef)
                if (existingBid.exists()) {
                    throw IllegalStateException("You already placed a bid on this job.")
                }
                val userSnapshot = transaction.get(userRef)
                val aggregateSnapshot = transaction.get(aggregateRef)
                if (!aggregateSnapshot.exists()) {
                    val profile = userSnapshot.toObject(User::class.java)
                        ?: throw IllegalStateException("Provider profile not found")
                    val ratingCount = userSnapshot.getLong("ratingCount")?.toInt() ?: 0
                    val ratingSum = userSnapshot.getDouble("ratingSum") ?: 0.0
                    transaction.set(
                        aggregateRef,
                        mapOf(
                            "providerId" to providerId,
                            "ratingSum" to ratingSum,
                            "ratingCount" to ratingCount,
                            "rating" to (userSnapshot.getDouble("rating")
                                ?: if (ratingCount > 0) ratingSum / ratingCount else 0.0),
                            "serviceRatings" to (
                                userSnapshot.get("serviceRatings") ?: profile.serviceRatings
                            ),
                            "availableForEmergency" to profile.availableForEmergency,
                            "lastRatingJobId" to userSnapshot.getString("lastRatingJobId"),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                }
                Pair(
                    userSnapshot.getBoolean("isVerified") == true,
                    userSnapshot.getString("certificateUrl")?.takeIf { it.isNotBlank() }
                )
            }.await()
            val providerRatings = aggregateRef.get().await()
                .toObject(ProviderRatingAggregate::class.java)
            val categoryRating = providerRatings?.serviceRatings?.get(job.category)

            val bid = Bid(
                bidId = bidRef.id,
                jobId = jobId,
                providerId = providerId,
                providerName = providerName,
                price = price,
                message = message,
                providerRating = categoryRating?.averageRating ?: 0.0,
                providerReviewCount = categoryRating?.reviewCount ?: 0,
                ratingAvg = categoryRating?.averageRating ?: 0.0,
                ratingCount = categoryRating?.reviewCount ?: 0,
                isCertified = providerMetadata.first,
                certificateUrl = providerMetadata.second,
                distanceKm = distanceKm,
                estimatedArrivalMinutes = estimatedArrivalMinutes
            )
            val bidBatch = firestore.batch()
            bidBatch.set(bidRef, bid)
            bidBatch.set(
                firestore.collection("providerProfileAccess")
                    .document(providerId)
                    .collection("customers")
                    .document(job.customerId),
                mapOf(
                    "providerId" to providerId,
                    "customerId" to job.customerId,
                    "jobId" to jobId,
                    "bidId" to bidRef.id
                )
            )
            bidBatch.commit().await()
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
            val customerId = firebaseAuth.currentUser?.uid
                ?: return Result.failure(IllegalStateException("No logged-in user"))
            val jobRef = firestore.collection("jobPosts").document(jobId)
            val bidRef = jobRef.collection("bids").document(bidId)
            firestore.runTransaction { transaction ->
                val jobSnapshot = transaction.get(jobRef)
                val bidSnapshot = transaction.get(bidRef)
                val bid = bidSnapshot.toObject(Bid::class.java)
                    ?: throw IllegalStateException("Bid not found")
                val providerRef = firestore.collection("users").document(bid.providerId)
                val providerSnapshot = transaction.get(providerRef)
                val jobCustomerId = jobSnapshot.getString("customerId")
                    ?: throw IllegalStateException("Job has no customer")
                val category = jobSnapshot.getString("category")
                    ?: throw IllegalStateException("Job has no service category")
                val providerPhone = providerSnapshot.getString("phoneNumber")
                    ?.takeIf { it.isNotBlank() }
                    ?: providerSnapshot.getString("phone")?.takeIf { it.isNotBlank() }
                    ?: providerSnapshot.getString("phone_number")?.takeIf { it.isNotBlank() }
                    ?: throw IllegalStateException("Provider has no phone number on their profile.")
                if (jobCustomerId != customerId) {
                    throw IllegalStateException("You can only select a bid for your own job.")
                }
                if (bid.providerId == customerId) {
                    throw IllegalStateException("You cannot accept your own bid.")
                }
                val lockId = ServiceRatingPolicy.bookingLockId(customerId, bid.providerId, category)
                val lockRef = firestore.collection("bookingLocks").document(lockId)
                val lockSnapshot = transaction.get(lockRef)
                val slotRefs = if (jobSnapshot.getBoolean("isEmergency") == true) {
                    emptyList()
                } else {
                    providerJobSlotKeys(
                        bid.providerId,
                        jobSnapshot.getString("preferredDate").orEmpty(),
                        jobSnapshot.getString("preferredTime").orEmpty()
                    ).map { slotKey ->
                        firestore.collection("providerSlots").document(slotKey)
                    }
                }
                val slotSnapshots = slotRefs.map { slotRef -> transaction.get(slotRef) }
                if (slotSnapshots.any { slotSnapshot ->
                        slotSnapshot.exists() && slotSnapshot.getString("jobId") != jobId
                    }
                ) {
                    throw IllegalStateException(
                        "This provider is no longer available at that time. Please choose another bid."
                    )
                }
                if (!BookingPolicy.canAssign(
                        jobSnapshot.getString("status"),
                        activeLockExists = lockSnapshot.exists()
                    )
                ) {
                    throw IllegalStateException(
                        if (lockSnapshot.exists()) {
                            "You already have an active booking with this provider for this service."
                        } else {
                            "This job is no longer open for bid selection."
                        }
                    )
                }
                transaction.update(
                    jobRef,
                    mapOf(
                        "selectedBidId" to bidId,
                        "assignedProviderId" to bid.providerId,
                        "assignedProviderName" to bid.providerName,
                        "providerPhone" to providerPhone,
                        "providerCertificateUrl" to providerSnapshot.getString("certificateUrl"),
                        "acceptedBidPrice" to bid.price,
                        "acceptedProviderRating" to (
                            if (bid.ratingCount == 0 && bid.providerReviewCount > 0) {
                                bid.providerRating
                            } else {
                                bid.ratingAvg
                            }
                        ),
                        "acceptedProviderReviewCount" to (
                            if (bid.ratingCount == 0) bid.providerReviewCount else bid.ratingCount
                        ),
                        "bookingLockId" to lockId,
                        "status" to "Accepted"
                    )
                )
                transaction.set(
                    lockRef,
                    mapOf(
                        "customerId" to customerId,
                        "providerId" to bid.providerId,
                        "serviceCategory" to category,
                        "jobId" to jobId
                    )
                )
                slotRefs.zip(slotSnapshots)
                    .filter { (_, slotSnapshot) -> !slotSnapshot.exists() }
                    .forEach { (slotRef, _) ->
                        transaction.set(
                            slotRef,
                            mapOf(
                                "providerId" to bid.providerId,
                                "jobId" to jobId,
                                "customerId" to customerId,
                                "createdAt" to FieldValue.serverTimestamp()
                            )
                        )
                    }
            }.await()

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
