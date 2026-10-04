package lk.ac.nibm.hdse.fixly_20.data.repository

import android.content.Context
import android.net.Uri
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.GeoPoint
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import lk.ac.nibm.hdse.fixly_20.data.remote.CloudinaryUploader
import lk.ac.nibm.hdse.fixly_20.model.JobPost

class JobRepository(
    private val firebaseAuth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) {

    suspend fun postJob(
        context: Context,
        customerName: String,
        category: String,
        description: String,
        preferredDate: String,
        preferredTime: String,
        isEmergency: Boolean,
        location: GeoPoint?,
        address: String,
        imageUris: List<Uri>
    ): Result<String> {
        return try {
            val customerId = firebaseAuth.currentUser?.uid
                ?: return Result.failure(IllegalStateException("No logged-in user"))

            val imageUrls = mutableListOf<String>()
            for (uri in imageUris) {
                val uploadResult = CloudinaryUploader.uploadFile(context, uri)
                uploadResult.getOrNull()?.let { url ->
                    imageUrls.add(url)
                }
            }

            val docRef = firestore.collection("jobPosts").document()

            val job = JobPost(
                jobId = docRef.id,
                customerId = customerId,
                customerName = customerName,
                category = category,
                description = description,
                preferredDate = preferredDate,
                preferredTime = preferredTime,
                isEmergency = isEmergency,
                location = location,
                address = address,
                status = "Open",
                imageUrls = imageUrls
            )

            docRef.set(job).await()
            Result.success(docRef.id)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Real-time stream of all open jobs — used by the Provider's Available Jobs screen. */
    fun observeOpenJobs(): Flow<List<JobPost>> = callbackFlow {
        val listener = firestore.collection("jobPosts")
            .whereEqualTo("status", "Open")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    error.printStackTrace()
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                val jobs = snapshot?.toObjects(JobPost::class.java) ?: emptyList()
                val sortedJobs = jobs.sortedWith(
                    compareByDescending<JobPost> { it.isEmergency }
                        .thenByDescending { it.createdAt }
                )
                trySend(sortedJobs)
            }
        awaitClose { listener.remove() }
    }

    /** Real-time stream of the logged-in CUSTOMER's own jobs — used by My Bookings. */
    fun observeCustomerJobs(): Flow<List<JobPost>> = callbackFlow {
        val customerId = firebaseAuth.currentUser?.uid
        if (customerId == null) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }

        val listener = firestore.collection("jobPosts")
            .whereEqualTo("customerId", customerId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    error.printStackTrace()
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                val jobs = snapshot?.toObjects(JobPost::class.java) ?: emptyList()
                val sortedJobs = jobs.sortedByDescending { it.createdAt }
                trySend(sortedJobs)
            }
        awaitClose { listener.remove() }
    }
}
